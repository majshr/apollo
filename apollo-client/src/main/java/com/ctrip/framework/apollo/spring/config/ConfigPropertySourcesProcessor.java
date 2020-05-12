package com.ctrip.framework.apollo.spring.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import com.ctrip.framework.apollo.spring.spi.ConfigPropertySourcesProcessorHelper;
import com.ctrip.framework.foundation.internals.ServiceBootstrap;

/**
 * 用于处理 Spring XML 的配置。<br>
 * Apollo PropertySource 处理器。<br>
 * Apollo Property Sources processor for Spring XML Based Application
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigPropertySourcesProcessor extends PropertySourcesProcessor
        implements BeanDefinitionRegistryPostProcessor {

    private ConfigPropertySourcesProcessorHelper helper = ServiceBootstrap
            .loadPrimary(ConfigPropertySourcesProcessorHelper.class);

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        helper.postProcessBeanDefinitionRegistry(registry);
    }
}
