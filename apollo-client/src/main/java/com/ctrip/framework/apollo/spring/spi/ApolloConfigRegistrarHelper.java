package com.ctrip.framework.apollo.spring.spi;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.type.AnnotationMetadata;

import com.ctrip.framework.apollo.core.spi.Ordered;

/**
 * 配置注册接口类
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年5月8日 上午10:17:28
 */
public interface ApolloConfigRegistrarHelper extends Ordered {

    /**
     * 注册BeanDefinition到spring容器
     * 
     * @param importingClassMetadata
     *            注解元数据信息
     * @param registry
     *            BeanDefinition缓存管理
     * @date: 2020年5月8日 下午2:37:07
     */
    void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry);
}
