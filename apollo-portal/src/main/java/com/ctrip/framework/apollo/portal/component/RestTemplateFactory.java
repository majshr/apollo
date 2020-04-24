package com.ctrip.framework.apollo.portal.component;

import java.io.UnsupportedEncodingException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ctrip.framework.apollo.portal.component.config.PortalConfig;

/**
 * 自定义RestTemplate工厂, 实现了FactoryBean, 用于生成一个RestTemplate对象
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月23日 上午9:50:16
 */
@Component
public class RestTemplateFactory implements FactoryBean<RestTemplate>, InitializingBean {

    @Autowired
    private HttpMessageConverters httpMessageConverters;
    @Autowired
    private PortalConfig portalConfig;

    private RestTemplate restTemplate;

    @Override
    public RestTemplate getObject() {
        return restTemplate;
    }

    @Override
    public Class<RestTemplate> getObjectType() {
        return RestTemplate.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws UnsupportedEncodingException {
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        restTemplate = new RestTemplate(httpMessageConverters.getConverters());
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(portalConfig.connectTimeout());
        requestFactory.setReadTimeout(portalConfig.readTimeout());

        restTemplate.setRequestFactory(requestFactory);
    }

}
