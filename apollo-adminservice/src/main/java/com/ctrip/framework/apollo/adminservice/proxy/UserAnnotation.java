package com.ctrip.framework.apollo.adminservice.proxy;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Inherited
// @Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface UserAnnotation {
    String name() default "aaa";
}
