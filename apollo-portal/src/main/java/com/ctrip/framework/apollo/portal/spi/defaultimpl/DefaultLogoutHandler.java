package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ctrip.framework.apollo.portal.spi.LogoutHandler;

/**
 * 默认登出实现
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午10:34:11
 */
public class DefaultLogoutHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 登出后，跳转到 / 地址。
            response.sendRedirect("/");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
