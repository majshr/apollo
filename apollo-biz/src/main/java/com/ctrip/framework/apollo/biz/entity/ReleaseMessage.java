package com.ctrip.framework.apollo.biz.entity;

import com.google.common.base.MoreObjects;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 * 发布消息(中间件中的消息, 实现为数据库实现)<br>
 * ReleaseMessage 设计的意图是作为配置发生变化的通知，所以对于同一个 Namespace ，仅需要保留其最新的 ReleaseMessage 记录即可。
 * 所以，在 「DatabaseMessageSender」 中，我们会看到，有后台任务不断清理旧的 ReleaseMessage 记录。
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "ReleaseMessage")
public class ReleaseMessage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "Id")
	private long id;

	/**
	 * AppId+Cluster+Namespace(数据库实现的消息队列, 存这个)<br>
	 * 消息内容，通过 {@link com.ctrip.framework.apollo.biz.utils.ReleaseMessageKeyGenerator#generate(String, String, String)} 方法生成。<br>
	 * 
	 * 对于同一个 Namespace ，生成的消息内容是相同的。
	 * 通过这样的方式，我们可以使用最新的 ReleaseMessage 的 id 属性，作为 Namespace 是否发生变更的标识。
	 * 而 Apollo 确实是通过这样的方式实现，Client 通过不断使用获得到 ReleaseMessage 的 id 属性作为版本号，
	 * 请求 Config Service 判断是否配置发生了变化。
	 */
	@Column(name = "Message", nullable = false)
	private String message;

	/**
     * 最后更新时间
     */
	@Column(name = "DataChange_LastTime")
	private Date dataChangeLastModifiedTime;

	@PrePersist
	protected void prePersist() {
		if (this.dataChangeLastModifiedTime == null) {
			dataChangeLastModifiedTime = new Date();
		}
	}

	public ReleaseMessage() {
	}

	public ReleaseMessage(String message) {
		this.message = message;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).omitNullValues().add("id", id).add("message", message)
				.add("dataChangeLastModifiedTime", dataChangeLastModifiedTime).toString();
	}
}
