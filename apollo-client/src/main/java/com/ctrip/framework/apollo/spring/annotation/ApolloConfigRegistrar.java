package com.ctrip.framework.apollo.spring.annotation;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import com.ctrip.framework.apollo.spring.spi.ApolloConfigRegistrarHelper;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;

/**
 * 实现 ImportBeanDefinitionRegistrar 接口，Apollo Spring Java Config 注册器。
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloConfigRegistrar implements ImportBeanDefinitionRegistrar {

    private ApolloConfigRegistrarHelper helper = ServiceBootstrap.loadPrimary(ApolloConfigRegistrarHelper.class);

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        helper.registerBeanDefinitions(importingClassMetadata, registry);
    }
}
