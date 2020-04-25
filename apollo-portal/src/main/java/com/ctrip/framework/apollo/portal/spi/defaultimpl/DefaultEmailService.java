package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.portal.entity.bo.Email;
import com.ctrip.framework.apollo.portal.spi.EmailService;

/**
 * 默认邮件服务, 未做任何操作
 * @author maj
 *
 */
public class DefaultEmailService implements EmailService {

	@Override
	public void send(Email email) {
		// do nothing
	}
}
