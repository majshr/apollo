package com.ctrip.framework.apollo.spring.spi;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import com.ctrip.framework.apollo.core.spi.Ordered;

/**
 * 配置属性执行器
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月29日 上午10:29:19
 */
public interface ConfigPropertySourcesProcessorHelper extends Ordered {

    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;
}
