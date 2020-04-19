package com.ctrip.framework.apollo.biz.message;

/**
 * 消息发送接口定义
 * @author Jason Song(song_s@ctrip.com)
 */
public interface MessageSender {
	/**
	 * 消息发送
	 * @param message 消息
	 * @param channel 通道(主题)
	 */
	void sendMessage(String message, String channel);
}
