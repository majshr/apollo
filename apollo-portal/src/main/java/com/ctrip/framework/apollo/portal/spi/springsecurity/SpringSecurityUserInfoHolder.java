package com.ctrip.framework.apollo.portal.spi.springsecurity;

import java.security.Principal;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;

/**
 * SpringSecurity实现方式
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午10:12:24
 */
public class SpringSecurityUserInfoHolder implements UserInfoHolder {

    @Override
    public UserInfo getUser() {
        // 创建 UserInfo 对象，设置 `username` 到 `UserInfo.userId` 中。
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(getCurrentUsername());
        return userInfo;
    }

    /**
     * 获得当前用户名
     * 
     * @return String
     * @date: 2020年4月24日 上午10:12:58
     */
    private String getCurrentUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        if (principal instanceof Principal) {
            return ((Principal) principal).getName();
        }
        return String.valueOf(principal);
    }

}
