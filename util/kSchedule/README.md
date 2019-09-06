### 功能 任意时间提交任意延迟的任务，任务支持多机器部署，服务重启
### 基于数据库的schedule，支持重启，多机器部署。仅限于spring项目
```
kz表达式
+ 默认
- 
+y1 表示时间为1年后
+y1 -M2 表示1年后的2个月前 和+M10 结果一致
y 年
M 月
d 日
H 时
m 分
s 秒
S 毫秒
```
```java
public @interface KSchedule {
    // 延迟表达式
    String kz() default ""; 
    // 暂时未实现
    @Deprecated 
    String cron() default "";
    // 执行beanName 必须制定 请属性spring beanName机制
    String beanName();
    // 任务执行失败最大重试次数和
    int maxRetry() default 0;

}
```
```java
    // 使用：
    // 表示在调用方法后2分钟才执行改方法
    // 方法的参数化必须实现Serializable接口
    @KSchedule(beanName = "v3LabelService",kz = "m2") 
    public void sayHello(Integer h) {
        System.out.println(LocalDateTime.now());
        System.out.println("hello world" + h);
    }
    // 指定动态延迟
    // 动态指定延迟优先级高于kz延迟表达式，动态延迟只需要在参数钱添加KScheduleDelay注解
    // 注解的参数只能为 Integer Long int long四种类型
    @KSchedule(beanName = "v3LabelService", kz = "m2")
    public void sayHello(@KScheduleDelay Integer delay, Integer h) {
        System.out.println(LocalDateTime.now());
        System.out.println("hello world" + h);
    }
```
