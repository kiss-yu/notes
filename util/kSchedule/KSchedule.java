package com.caishi.v3;

import java.lang.annotation.*;

/**
 * @author by keray
 * date:2019/9/5 16:44
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KSchedule {
    String kz() default "";

    @Deprecated
    String cron() default "";

    String beanName();

    int maxRetry() default 0;

}
