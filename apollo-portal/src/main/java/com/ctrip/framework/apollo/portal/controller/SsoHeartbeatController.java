package com.ctrip.framework.apollo.portal.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ctrip.framework.apollo.portal.spi.SsoHeartbeatHandler;

/**
 * 心跳发送controller Since sso auth information has a limited expiry time, so we
 * need to do sso heartbeat to keep the information refreshed when unavailable
 *
 * @author Jason Song(song_s@ctrip.com)
 */
@Controller
@RequestMapping("/sso_heartbeat")
public class SsoHeartbeatController {
    private final SsoHeartbeatHandler handler;

    public SsoHeartbeatController(final SsoHeartbeatHandler handler) {
        this.handler = handler;
    }

    /**
     * 通过打开一个新的窗口，访问 http://ip:prot/sso_hearbeat 地址，每 60 秒刷新一次页面，从而避免 SSO 登陆过期。
     * 
     * @param request
     * @param response
     *            void
     * @date: 2020年4月24日 上午10:27:04
     */
    @GetMapping
    public void heartbeat(HttpServletRequest request, HttpServletResponse response) {
        handler.doHeartbeat(request, response);
    }
}
