package com.caishi.v3.sys.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.caishi.BaseEntityV3;
import io.swagger.annotations.ApiModel;
import lombok.*;

/**
 * @author by keray
 * date:2019/9/5 16:32
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "系统定时任务", description = "系统定时任务")
@TableName("sys_schedule")
public class SysScheduleModel extends BaseEntityV3 {
    public final static String WAIT_SUBMIT = "waitSubmit";
    public final static String WAIT_EXEC = "waitExec";
    public final static String EXEC = "exec";
    public final static String SUCCESS = "success";
    public final static String FAIL = "fail";
    public final static String WAIT_RETRY = "waitRetry";
    /**
     * 设备id
     */
    private String driverId;
    /**
     * beanName
     */
    private String beanName;
    /**
     * kz_cron表达式{"kz":"","cron":""}
     */
    private String kzCron;

    /**
     * 等待提交 {@link SysScheduleModel#WAIT_SUBMIT}
     * 等待执行{@link SysScheduleModel#WAIT_EXEC}
     * 执行中{@link SysScheduleModel#EXEC}
     * 执行成功{@link SysScheduleModel#SUCCESS}
     * 执行失败{@link SysScheduleModel#FAIL}
     * 等待重试{@link SysScheduleModel#WAIT_RETRY}
     */
    private String status;


    /**
     * {
     * "name":"methodName",
     * "args":[
     * {
     * "clazz":""java.lang.String"",
     * "value":"123"
     * },
     * {
     * "clazz":""java.lang.Integer"",
     * "value":"123"
     * }
     * ]
     * }
     */
    private String methodDetail;

    /**
     * 重试次数 0 不重试
     */
    private Integer retryCount;
}
