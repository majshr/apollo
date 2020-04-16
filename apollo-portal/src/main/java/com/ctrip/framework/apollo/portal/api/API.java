package com.ctrip.framework.apollo.portal.api;

import org.springframework.beans.factory.annotation.Autowired;

import com.ctrip.framework.apollo.portal.component.RetryableRestTemplate;

/**
 * api抽象, 提供一个restTemplate
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月14日 下午5:11:24
 */
public abstract class API {

    @Autowired
    protected RetryableRestTemplate restTemplate;

}
