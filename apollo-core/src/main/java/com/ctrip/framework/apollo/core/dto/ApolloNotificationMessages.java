package com.ctrip.framework.apollo.core.dto;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;


/**
 * 通知明细信息, 通过一个map维护<br>
 * 按道理说，对于一个 Namespace 的通知，使用 ApolloConfigNotification 的 namespaceName + notificationId 已经足够了。
 * 但是，在 namespaceName对应的 Namespace 是关联类型时，
 * 会同时查询当前 Namespace + 关联的 Namespace 这两个 Namespace，所以会是多个，使用 Map 数据结构。 <br>
 * 
 * 当然，对于 /notifications/v2 接口，仅有【直接】获得到配置变化才可能出现 ApolloNotificationMessages.details 为多个的情况。
 * 为啥？在 #handleMessage(...) 方法中，一次只处理一条 ReleaseMessage ，
 * 因此只会有 ApolloNotificationMessages.details 只会有一个。
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class ApolloNotificationMessages {
	/**
     * 明细 Map
     *
     * KEY ：{appId} "+" {clusterName} "+" {namespace} ，例如：100004458+default+application
     * VALUE ：通知编号
     */
	private Map<String, Long> details;

	public ApolloNotificationMessages() {
		this(Maps.<String, Long>newHashMap());
	}

	private ApolloNotificationMessages(Map<String, Long> details) {
		this.details = details;
	}

	public void put(String key, long notificationId) {
		details.put(key, notificationId);
	}

	public Long get(String key) {
		return this.details.get(key);
	}

	public boolean has(String key) {
		return this.details.containsKey(key);
	}

	public boolean isEmpty() {
		return this.details.isEmpty();
	}

	public Map<String, Long> getDetails() {
		return details;
	}

	public void setDetails(Map<String, Long> details) {
		this.details = details;
	}

	/**
	 * 在客户端中使用，将 Config Service 返回的结果，合并到本地的通知信息中。
	 * @param source
	 */
	public void mergeFrom(ApolloNotificationMessages source) {
		if (source == null) {
			return;
		}

		for (Map.Entry<String, Long> entry : source.getDetails().entrySet()) {
			// to make sure the notification id always grows bigger
			// 只合并新的通知编号大于的情况
			if (this.has(entry.getKey()) && this.get(entry.getKey()) >= entry.getValue()) {
				continue;
			}
			this.put(entry.getKey(), entry.getValue());
		}
	}

	public ApolloNotificationMessages clone() {
		return new ApolloNotificationMessages(ImmutableMap.copyOf(this.details));
	}
}
