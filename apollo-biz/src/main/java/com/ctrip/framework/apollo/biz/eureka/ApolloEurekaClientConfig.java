package com.ctrip.framework.apollo.biz.eureka;

import java.util.List;

import org.springframework.cloud.netflix.eureka.EurekaClientConfigBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.biz.config.BizConfig;

/**
 * 声明eureka配置
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月26日 下午5:30:46
 */
@Component
@Primary // @Primary 注解，保证优先级
public class ApolloEurekaClientConfig extends EurekaClientConfigBean {

    private final BizConfig bizConfig;

    public ApolloEurekaClientConfig(final BizConfig bizConfig) {
        this.bizConfig = bizConfig;
    }

    /**
     * Eureka Server 共享该配置，从而形成 Eureka Server 集群。<br>
     * Assert only one zone: defaultZone, but multiple environments.
     */
    @Override
    public List<String> getEurekaServerServiceUrls(String myZone) {
        // 从 ServerConfig 的 "eureka.service.url" 配置项，获得 Eureka Server 地址
        List<String> urls = bizConfig.eurekaServiceUrls();
        // 数据库配置中没有, 使用配置文件的
        return CollectionUtils.isEmpty(urls) ? super.getEurekaServerServiceUrls(myZone) : urls;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }
}
