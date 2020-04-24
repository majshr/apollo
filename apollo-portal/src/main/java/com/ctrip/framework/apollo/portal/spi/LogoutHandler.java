package com.ctrip.framework.apollo.portal.spi;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登出接口
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午10:33:37
 */
public interface LogoutHandler {

    /**
     * 登出
     * 
     * @param request
     * @param response
     * @date: 2020年4月24日 上午10:33:48
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

}
