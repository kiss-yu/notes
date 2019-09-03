### spring 接口支持 application/json和表单
### @requestbody 多个参数
### spring application/json 多参数解析
```java
ApiJsonParam //注标识解
ApiJsonParamResolver // 自定义装载器
SpringMvcConfig // 配置
```
spring支持在application/json模式下使用@ModelAttribute @RequestParam @Valid @PathVariable 等形式接收json串里的值，这样直接实现了application/json下接口方法多值参数，免去了@RequestBody需要定义DTO对象。支持复杂json。
