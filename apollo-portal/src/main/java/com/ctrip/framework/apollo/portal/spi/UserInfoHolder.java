package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

/**
 * 获取当前登录用户信息，SSO 一般都是把当前登录用户信息放在线程 ThreadLocal 上。<br>
 * Get access to the user's information, different companies should have a
 * different implementation
 */
public interface UserInfoHolder {

	/**
	 * 获得用户
	 * @return
	 */
	UserInfo getUser();

}
