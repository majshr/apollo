package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;

/**
 * 发布消息监听器
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseMessageListener {
	/**
	 * 处理发布消息
	 * @param message
	 * @param channel
	 */
	void handleMessage(ReleaseMessage message, String channel);
}
