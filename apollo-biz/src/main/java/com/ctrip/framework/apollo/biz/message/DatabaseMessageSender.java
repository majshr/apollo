package com.ctrip.framework.apollo.biz.message;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.repository.ReleaseMessageRepository;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.collect.Queues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据库实现消息发送
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class DatabaseMessageSender implements MessageSender {
	private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageSender.class);
	/**
	 * 清理 Message 队列 最大容量
	 */
	private static final int CLEAN_QUEUE_MAX_SIZE = 100;
	
	/**
	 * 清理 Message 队列(队列保存的是ReleaseMessage消息的id)<br>
	 * 在调用sendMessage方法后, 会在队列中加入消息ID, 也就是发送消息的进程负责清理消息.<br>
	 * 每次发送新消息后, 原来的消息才会变成旧消息
	 */
	private BlockingQueue<Long> toClean = Queues.newLinkedBlockingQueue(CLEAN_QUEUE_MAX_SIZE);
	
	/**
	 * 清理消息队列中的旧消息, 异步任务
	 */
	private final ExecutorService cleanExecutorService;
	
	/**
	 * 是否停止清理 Message 标识
	 */
	private final AtomicBoolean cleanStopped;

	private final ReleaseMessageRepository releaseMessageRepository;

	public DatabaseMessageSender(final ReleaseMessageRepository releaseMessageRepository) {
		cleanExecutorService = Executors
				.newSingleThreadExecutor(ApolloThreadFactory.create("DatabaseMessageSender", true));
		cleanStopped = new AtomicBoolean(false);
		this.releaseMessageRepository = releaseMessageRepository;
	}

	@Override
	@Transactional
	public void sendMessage(String message, String channel) {
		logger.info("Sending message {} to channel {}", message, channel);
		// 仅允许发送 APOLLO_RELEASE_TOPIC
		if (!Objects.equals(channel, Topics.APOLLO_RELEASE_TOPIC)) {
			logger.warn("Channel {} not supported by DatabaseMessageSender!");
			return;
		}

		Tracer.logEvent("Apollo.AdminService.ReleaseMessage", message);
		Transaction transaction = Tracer.newTransaction("Apollo.AdminService", "sendMessage");
		try {
			// 保存 ReleaseMessage 对象
			ReleaseMessage newMessage = releaseMessageRepository.save(new ReleaseMessage(message));
			// 添加到清理 Message 队列。若队列已满，添加失败，不阻塞等待。
			toClean.offer(newMessage.getId());
			transaction.setStatus(Transaction.SUCCESS);
		} catch (Throwable ex) {
			logger.error("Sending message to database failed", ex);
			transaction.setStatus(ex);
			throw ex;
		} finally {
			transaction.complete();
		}
	}

	
	/**
	 * spring调用, 启动定时任务<br>
	 * 不断清理旧的 ReleaseMessage 记录的后台任务。
	 */
	@PostConstruct
	private void initialize() {
		cleanExecutorService.submit(() -> {
			while (!cleanStopped.get() && !Thread.currentThread().isInterrupted()) {
				try {
					// 拉取
					Long rm = toClean.poll(1, TimeUnit.SECONDS);
					// 队列非空，处理拉取到的消息
					if (rm != null) {
						cleanMessage(rm);
					} else {
						// 队列为空，sleep ，避免空跑，占用 CPU
						TimeUnit.SECONDS.sleep(5);
					}
				} catch (Throwable ex) {
					Tracer.logError(ex);
				}
			}
		});
	}

	/**
	 * 清理消息(清理id对应的消息, 的"AppId+Cluster+Namespace"相同的旧消息)
	 * @param id
	 */
	private void cleanMessage(Long id) {
		boolean hasMore = true;
		// double check in case the release message is rolled back
		// 查询对应的 ReleaseMessage 对象，避免已经删除。因为，DatabaseMessageSender 会在多进程中执行。
		// 例如：1）Config Service + Admin Service ；2）N * Config Service ；3）N * Admin Service
		ReleaseMessage releaseMessage = releaseMessageRepository.findById(id).orElse(null);
		if (releaseMessage == null) {
			return;
		}
		
		// 循环删除相同消息内容( `message` )的老消息, 每次批量删除100条
		while (hasMore && !Thread.currentThread().isInterrupted()) {
			List<ReleaseMessage> messages = releaseMessageRepository.findFirst100ByMessageAndIdLessThanOrderByIdAsc(
					releaseMessage.getMessage(), releaseMessage.getId());

			releaseMessageRepository.deleteAll(messages);
			// 如果当前查得的数量等于100, 说明还有老数据
			hasMore = messages.size() == 100;

			// 【TODO 6001】Tracer 日志
			messages.forEach(toRemove -> Tracer.logEvent(
					String.format("ReleaseMessage.Clean.%s", toRemove.getMessage()), String.valueOf(toRemove.getId())));
		}
	}

	void stopClean() {
		cleanStopped.set(true);
	}
}
