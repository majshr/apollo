package com.ctrip.framework.apollo.portal.spi;

import com.ctrip.framework.apollo.portal.entity.bo.Email;
/**
 * email服务
 * @author maj
 *
 */
public interface EmailService {

	/**
	 * 发送邮件
	 * @param email
	 */
	void send(Email email);

}
