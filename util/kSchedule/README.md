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
// 使用：
// 表示在调用方法后2分钟才执行改方法
// 方法的参数化必须实现Serializable接口
@KSchedule(beanName = "v3LabelService",kz = "m2") 
public void sayHello(Integer h) {
    System.out.println(LocalDateTime.now());
    System.out.println("hello world" + h);
}
```
