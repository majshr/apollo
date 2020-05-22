package com.ctrip.framework.apollo.demo.testself.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;

@Controller
public class TestController {

    @RequestMapping("test")
    @ResponseBody
    public String test() {
        ConfigUtil m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        m_configUtil.getApolloEnv();
        m_configUtil.getMetaServerDomainName();
        return "hello";
    }
}
