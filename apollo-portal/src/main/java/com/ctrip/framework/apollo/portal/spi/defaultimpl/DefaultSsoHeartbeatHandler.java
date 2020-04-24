package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ctrip.framework.apollo.portal.spi.SsoHeartbeatHandler;

/**
 * 默认实现
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultSsoHeartbeatHandler implements SsoHeartbeatHandler {

    @Override
    public void doHeartbeat(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 跳转到 default_sso_heartbeat.html
            response.sendRedirect("default_sso_heartbeat.html");
        } catch (IOException e) {
        }
    }

}
// 每60s刷新一次页面
/*
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>SSO Heartbeat</title>
    <script type="text/javascript">
        var reloading = false;
        setInterval(function () {
            if (reloading) {
                return;
            }
            reloading = true;
            location.reload(true);
        }, 60000);
    </script>
</head>
<body>
</body>
</html>
*/