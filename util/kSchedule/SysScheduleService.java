package com.caishi.v3;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.caishi.BaseEntityV3;
import com.caishi.BaseMapperV3;
import com.caishi.SpringContextHolder;
import com.caishi.util.KZEngine;
import com.caishi.v3.sys.mapper.SysScheduleMaper;
import com.caishi.v3.sys.model.SysScheduleModel;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author by keray
 * date:2019/9/5 16:43
 */
@Aspect
@Service(value = "sysScheduleService")
@Slf4j
public class SysScheduleService extends BaseServiceImpl<SysScheduleModel> {
    private final ThreadLocal<Boolean> scheduleExecFlag = new ThreadLocal<>();
    private final String driverId = RandomUtil.randomString(64);
    private final ScheduledExecutorService schedulingException = new ScheduledThreadPoolExecutor(10, r -> {
        Thread t = new Thread(r);
        t.setName("kSchedule-pool");
        return t;
    });
    @Resource
    private SysScheduleMaper sysScheduleMaper;

    @Resource(name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
    private ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Override
    public BaseMapperV3<SysScheduleModel> getMapper() {
        return sysScheduleMaper;
    }

    public SysScheduleModel updateScheduleStatus(String id, String status) {
        log.info("sysSchedule 状态更新:id={} -> {}", id, status);
        SysScheduleModel model = new SysScheduleModel();
        model.setId(id);
        model.setStatus(status);
        sysScheduleMaper.updateById(model);
        return model;
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 18:24</h3>
     * 项目启动任务初始化
     * </p>
     *
     * @param
     * @return <p> {@link } </p>
     * @throws
     */
    @EventListener
    public void scheduleInit(ApplicationStartedEvent startedEvent) {
        List<SysScheduleModel> sysScheduleModels = selectList(
                Wrappers.lambdaQuery(new SysScheduleModel())
                        .ne(SysScheduleModel::getStatus, SysScheduleModel.SUCCESS)
                        .ne(SysScheduleModel::getStatus, SysScheduleModel.FAIL)
                        // 不取执行中的任务，exec任务保证有机器正在执行
                        .ne(SysScheduleModel::getStatus, SysScheduleModel.EXEC)
        );
        sysScheduleModels.stream()
                .parallel()
                .forEach(this::dataScheduleSubmit);
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/6 14:05</h3>
     * 每天凌晨2点扫描任务
     * 凌晨扫描的任务只获取等待提交的任务
     * </p>
     *
     * @param
     * @return <p> {@link} </p>
     * @throws
     */
    @Scheduled(cron = "0 0 2 * * ? ")
    public void dayScanSchedule() {
        List<SysScheduleModel> sysScheduleModels = selectList(
                Wrappers.lambdaQuery(new SysScheduleModel())
                        .eq(SysScheduleModel::getStatus, SysScheduleModel.WAIT_SUBMIT)
        );
        log.info("扫描的等待提交的任务：tasks={}", JSON.toJSON(sysScheduleModels));
        sysScheduleModels.stream()
                .parallel()
                .forEach(this::dataScheduleSubmit);
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/6 10:41</h3>
     * 提交SysScheduleModel对象任务
     * </p>
     *
     * @param model
     * @return <p> {@link } </p>
     * @throws
     */
    public void dataScheduleSubmit(SysScheduleModel model) {
        SysScheduleModel newModel = SysScheduleModel.builder()
                .driverId(driverId)
                .status(SysScheduleModel.WAIT_SUBMIT)
                .build();
        if (sysScheduleMaper.update(newModel,
                Wrappers.lambdaUpdate(new SysScheduleModel())
                        .eq(BaseEntityV3::getId, model.getId())
                        .eq(SysScheduleModel::getDriverId, model.getDriverId())
        ) == 1) {
            try {
                // 更新成功 提交任务
                log.info("开始发序列化任务：{}", model);
                JSONObject kzCron = JSON.parseObject(model.getKzCron());
                JSONObject methodDetail = JSON.parseObject(model.getMethodDetail());
                String methodName = methodDetail.getString("name");
                JSONArray args = methodDetail.getJSONArray("args");
                Object[] argsValue = null;
                if (CollUtil.isNotEmpty(args)) {
                    argsValue = args.stream().map(v -> {
                        Map<String, String> detail = (Map<String, String>) v;
                        try {
                            if (detail.get("value") == null) {
                                return null;
                            }
                            // 先基本类型转换
                            try {
                                return Convert.convert(Class.forName(detail.get("clazz")), detail.get("value"));
                            } catch (Exception e) {
                                log.warn("value转换基本类型失败:value={},clazz={}", detail.get("value"), detail.get("value").getClass());
                            }
                            return JSON.parseObject(detail.get("value"), Class.forName(detail.get("clazz")));
                        } catch (ClassNotFoundException e) {
                            log.error("任务方法反序列失败：", e);
                            throw new RuntimeException(e);
                        }
                    }).toArray();
                }
                log.info("参数反序列化完成:{}", argsValue == null ? null : JSON.toJSON(argsValue));
                Object bean = SpringContextHolder.getBean(model.getBeanName());
                if (bean == null) {
                    log.error("任务执行的bean不存在:name={}", model.getBeanName());
                    updateScheduleStatus(model.getId(), SysScheduleModel.FAIL);
                    return;
                }
                Method method = null;
                // 如果是spring aop代理类 拿到真实对象的method
                if (bean instanceof SpringProxy) {
                    method = bean.getClass().getSuperclass().getMethod(methodName, argsValue == null ? null :
                            Stream.of(argsValue).map(Object::getClass).toArray(Class[]::new));
                } else {
                    method = bean.getClass().getMethod(methodName, argsValue == null ? null :
                            Stream.of(argsValue).map(Object::getClass).toArray(Class[]::new));
                }
                submit(model.getId(), model.getCreateTime(), 0, driverId,
                        kzCron.getString("kz"), kzCron.getString("cron"), model.getBeanName(), model.getRetryCount(), model.getRetryMillis(),
                        method, argsValue
                );
            } catch (Exception e) {
                log.error("任务反序列失败:", e);
            }
        }
        // 失败不做任何事，只有在分布式下被其他节点抢先提交了才会失败
    }


    @Pointcut("@annotation(com.caishi.v3.KSchedule)")
    public void setSysSchedule() {
    }

    @Around("setSysSchedule()")
    public Object setSysSchedule(ProceedingJoinPoint pjp) {
        try {
            MethodInvocationProceedingJoinPoint methodPoint = (MethodInvocationProceedingJoinPoint) pjp;
            Field proxy = methodPoint.getClass().getDeclaredField("methodInvocation");
            proxy.setAccessible(true);
            ReflectiveMethodInvocation j = (ReflectiveMethodInvocation) proxy.get(methodPoint);
            Method method = j.getMethod();
            KSchedule kSchedule = method.getAnnotation(KSchedule.class);

            System.out.println("pjp.getArgs():" + Arrays.toString(pjp.getArgs()));

            // 检查任务是否可直接执行 可执行直接返回
            if (scheduleExecFlag.get() != null && scheduleExecFlag.get()) {
                return pjp.proceed();
            }
            if (saveSchedule(kSchedule, method, pjp.getArgs())) {
                return pjp.proceed();
            }
        } catch (Throwable e) {
            log.error("任务提交失败", e);
        }
        return null;
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 21:45</h3>
     * 持久化任务
     * </p>
     *
     * @param kSchedule
     * @param method
     * @param args
     * @return <p> {@link boolean} </p>
     * @throws
     */
    private boolean saveSchedule(KSchedule kSchedule, Method method, Object[] args) {
        LocalDateTime submitNow = LocalDateTime.now();
        // 没有kz和cron表达式的任务直接直接执行 并且关闭了动态延迟
        if (StrUtil.isBlank(kSchedule.kz()) && StrUtil.isBlank(kSchedule.cron()) && !kSchedule.dynamicDelay()) {
            return true;
        }
        // 校验参数是否全部实现Serialization接口
        if (args != null) {
            for (Object obj : args) {
                if (obj != null && !(obj instanceof Serializable)) {
                    throw new IllegalStateException("提交任务的方法参数必须全部实现Serializable接口");
                }
            }
        }
        // 校验kz
        if (StrUtil.isNotBlank(kSchedule.kz()) && !KZEngine.checkKZ(kSchedule.kz())) {
            throw new IllegalStateException("kz表达式错误：kz" + kSchedule.kz());
        }
        // 校验cron

        // 设备id
        String driverId = this.driverId;
        // 保存任务到数据库
        SysScheduleModel sysScheduleModel = SysScheduleModel.builder()
                .beanName(kSchedule.beanName())
                .retryCount(kSchedule.maxRetry())
                .status(SysScheduleModel.WAIT_SUBMIT)
                .retryMillis(kSchedule.retryMillis())
                .driverId(driverId)
                .kzCron(JSON.toJSONString(
                        MapUtil.builder()
                                .put("kz", kSchedule.kz())
                                .put("cron", kSchedule.cron())
                                .build()
                ))
                .methodDetail(JSON.toJSONString(
                        MapUtil.builder()
                                .put("name", method.getName())
                                .put("args", args == null ? null : Stream.of(args)
                                        .map(value -> MapUtil.builder()
                                                .put("clazz", value.getClass().getName())
                                                .put("value", JSON.toJSON(value))
                                                .build())
                                        .collect(Collectors.toList()))
                                .build()
                ))
                .build();
        insert(sysScheduleModel);
        // 提交任务
        submit(sysScheduleModel.getId(), submitNow, 0, driverId,
                kSchedule.kz(), kSchedule.cron(), kSchedule.beanName(), kSchedule.maxRetry(), kSchedule.retryMillis(),
                method, args
        );
        return false;
    }


    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 16:54</h3>
     * 提交任务
     * </p>
     *
     * @param id        任务id
     * @param startTime 任务提交时间
     * @param kz
     * @param cron
     * @param beanName
     * @param method    执行方法
     * @param args      方法参数
     */
    private void submit(String id, LocalDateTime startTime, int retryCount, String driverId,
                        String kz, String cron, String beanName, int retryMaxCount, int retryMS,
                        Method method, Object[] args) {
        log.info("提交任务:id={},startTime={},retryCount={},driverId={},kz={},cron={},beanName={},retryMaxCount={},retryMS={},method={},args={}",
                id, startTime, retryCount, driverId, kz, cron, beanName, retryMaxCount, retryMS, method, args
        );
        if (StrUtil.isNotBlank(cron) && StrUtil.isBlank(kz)) {
            log.error("暂时不支持cron方式");
            throw new RuntimeException("no support");
        }
        if (retryCount > retryMaxCount) {
            log.warn("任务重试次数达到最大值，直接失败任务");
            updateScheduleStatus(id, SysScheduleModel.FAIL);
            return;
        }
        long delay = computeDelay(startTime, kz, method, args);
        if (delay < 0) {
            log.warn("定义执行时间已超时，立即执行任务");
            delay = 0;
        }
        // 如果任务执行时间在一天后退出
        if (delay > 24 * 60 * 60 * 1000) {
            log.info("任务延迟超过1天，暂时不提交任务，等待下次轮询提交 delay={}", delay);
            return;
        }
        // 保存任务提交为wait_exec的定格数据，用于执行前的定格数据检查
        SysScheduleModel model = updateScheduleStatus(id, SysScheduleModel.WAIT_EXEC);
        log.info("提交执行任务:id={},delay={}", id, delay);
        // 任务runnable
        Runnable run = () -> {
            try {
                String owner = this.driverId;
                if (!driverId.equals(owner)) {
                    log.warn("任务设定执行设备不是本设备，放弃执行 driverId={},owner={}", driverId, owner);
                    return;
                }
                scheduleExecFlag.set(true);
                if (exec(id, beanName, method, args, model)) {
                    updateScheduleStatus(id, SysScheduleModel.SUCCESS);
                }
            } catch (Exception e) {
                log.error("任务执行异常", e);
                if (retryMaxCount > 0) {
                    updateScheduleStatus(id, SysScheduleModel.WAIT_RETRY);
                    submit(id, startTime.minus(-retryMS, ChronoUnit.MILLIS), retryMaxCount, driverId, kz, cron, beanName, retryCount + 1, retryMS, method, args);
                } else {
                    updateScheduleStatus(id, SysScheduleModel.FAIL);
                }
            } finally {
                scheduleExecFlag.remove();
            }
        };
        // 动态时间

        // kz表达式任务提 & cron
        if (StrUtil.isNotBlank(kz) && StrUtil.isNotBlank(cron)) {
            // 提交kz 占时忽略cron
            schedulingException.schedule(run, delay, TimeUnit.MILLISECONDS);
        }
        // cron 表达式任务提交到spring schedule
        else if (StrUtil.isNotBlank(cron)) {
            // 在KSchedule设定的延迟时间执行cron
            log.error("暂时不支持cron方式");
            throw new RuntimeException("no support");
        }
        // 提交纯延迟任务
        else {
            schedulingException.schedule(run, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/6 15:27</h3>
     * 计算任务延迟时间
     * </p>
     *
     * @param startTime 创建开始时间
     * @param kz
     * @param method
     * @param args
     * @return <p> {@link long} </p>
     * @throws
     */
    private long computeDelay(LocalDateTime startTime, String kz, Method method, Object[] args) {
        // 传递的动态延迟 优先级高于kz
        Annotation[][] annotations = method.getParameterAnnotations();
        all:
        for (int i = 0; annotations != null && i < annotations.length; i++) {
            if (annotations[i] != null && annotations[i].length > 0) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation.annotationType() == KScheduleDelay.class) {
                        Object o = args[i];
                        if (o == null) {
                            log.warn("schedule执行传入的delay为null");
                        } else if (o instanceof Integer || o instanceof Long) {
                            return Long.parseLong(o.toString());
                        } else if (o instanceof String) {
                            kz = (String) o;
                            break all;
                        } else {
                            log.warn("schedule执行传入的delay类型错误或者为null，仅支持 Integer,Long,int,long,String");
                        }
                        break all;
                    }
                }
            }
        }
        // 校验kz
        if (!KZEngine.checkKZ(kz)) {
            throw new IllegalStateException("kz表达式错误：kz" + kz);
        }

        // kz计算延迟
        if (StrUtil.isNotBlank(kz)) {
            return LocalDateTime.now().until(KZEngine.computeTime(kz, startTime), ChronoUnit.MILLIS);
        }
        return 0;
    }


    /**
     * <p>
     * <h3>作者 keray</h3>
     * <h3>时间： 2019/9/5 18:04</h3>
     * </p>
     *
     * @param id       任务id
     * @param beanName
     * @param method
     * @param args
     * @return <p> {@link } </p>
     * @throws
     */
    private boolean exec(String id, String beanName, Method method, Object[] args, SysScheduleModel oldModel) throws InvocationTargetException, IllegalAccessException {
        // check 失败，表明在提交任务到这里这段时间任务被修改
        // 设置任务的定格状态
        LocalDateTime modifyTime = oldModel.getModifyTime();
        // mysql不支持毫秒问题
        oldModel.setModifyTime(null);
        oldModel.setDriverId(driverId);
        if (sysScheduleMaper.selectOne(Wrappers.lambdaQuery(oldModel)
                // mysql 与java时间戳转换精度问题
                .apply("REPLACE(unix_timestamp(modify_time),'.','') > {0}", modifyTime.toEpochSecond(ZoneOffset.of("+8")) - 2)
                .apply("REPLACE(unix_timestamp(modify_time),'.','') < {0}", modifyTime.toEpochSecond(ZoneOffset.of("+8")) + 2)

        ) == null) {
            log.warn("任务在提交后到执行期间任务被打断，退出执行:data={}", oldModel);
            return false;
        }
        // check 过后将状态改为exec
        oldModel = updateScheduleStatus(id, SysScheduleModel.EXEC);
        // 再一次check 保证任务改为执行状态时任务未被修改，任务处于exec必然不会被其他机器exec，exec后必然会改成fail，success，wait_retry状态（非kill模式关闭系统）
        // 再一次check避免的情况是第一次check成功为改为exec状态时期间，另外一台机器直接执行了获取任务->提交任务->第一次check成功这个过程，如果不加第二次check就会多台机器执行，
        // 第二次check会使得多台机器最终只有一个定格数据，保证任务的单台执行
        // 这里并发下会存在的问题，多机器间的并发ok，存在的问题在意单机同时在这里，无法根据driverId区分开来，这是依靠的是modifyTime，但是modifyTime的
        // 精度只能到毫秒级，也就是上诉的获取任务->提交任务->第一次check成功这个过程在另一个任务修改为exec的毫秒级相同会出现单机同时执行多个相同任务
        LocalDateTime modifyTime1 = oldModel.getModifyTime();
        oldModel.setModifyTime(null);
        oldModel.setDriverId(driverId);
        if (sysScheduleMaper.selectOne(Wrappers.lambdaQuery(oldModel)
                .apply("REPLACE(unix_timestamp(modify_time),'.','') > {0}", modifyTime1.toEpochSecond(ZoneOffset.of("+8")) - 2)
                .apply("REPLACE(unix_timestamp(modify_time),'.','') < {0}", modifyTime1.toEpochSecond(ZoneOffset.of("+8")) + 2)
        ) == null) {
            log.warn("任务第二次check失败，退出执行:data={}", oldModel);
            return false;
        }
        Object execObj = SpringContextHolder.getBean(beanName);
        if (execObj == null) {
            log.error("任务执行的bean不存在:name={}", beanName);
            updateScheduleStatus(id, SysScheduleModel.FAIL);
            return false;
        }
        Object result = method.invoke(execObj, args);
        if (result instanceof Boolean) {
            return (boolean) result;
        }
        return true;
    }

    public static void main(String[] args) {

    }
}
