package com.ctrip.framework.apollo.configservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.repository.ReleaseMessageRepository;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 缓存 ReleaseMessage 的 Service 实现类。通过将 ReleaseMessage 缓存在内存中，提高查询性能。<br>
 * 1, 启动时，初始化 ReleaseMessage 到缓存。<br>
 * 2, 新增时，基于 ReleaseMessageListener ，通知有新的 ReleaseMessage ，根据是否有消息间隙，直接使用该
 * ReleaseMessage 或从数据库读取。<br>
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
@Service
public class ReleaseMessageServiceWithCache implements ReleaseMessageListener, InitializingBean {
	private static final Logger logger = LoggerFactory.getLogger(ReleaseMessageServiceWithCache.class);
	private final ReleaseMessageRepository releaseMessageRepository;
	private final BizConfig bizConfig;

	/**
	 * 扫描周期
	 */
	private int scanInterval;
	/**
	 * 扫描周期单位
	 */
	private TimeUnit scanIntervalTimeUnit;

	/**
	 * 最后扫描到的 ReleaseMessage 的编号
	 */
	private volatile long maxIdScanned;

	/**
	 * ReleaseMessage 缓存
	 *
	 * KEY：`ReleaseMessage.message` VALUE：对应的最新的 ReleaseMessage 记录
	 */
	private ConcurrentMap<String, ReleaseMessage> releaseMessageCache;

	/**
	 * 是否执行扫描任务
	 */
	private AtomicBoolean doScan;
	
	/**
	 * ExecutorService 对象
	 */
	private ExecutorService executorService;

	public ReleaseMessageServiceWithCache(final ReleaseMessageRepository releaseMessageRepository,
			final BizConfig bizConfig) {
		this.releaseMessageRepository = releaseMessageRepository;
		this.bizConfig = bizConfig;
		initialize();
	}

	/**
	 * 初始化方法
	 */
	private void initialize() {
		releaseMessageCache = Maps.newConcurrentMap();
		doScan = new AtomicBoolean(true);
		executorService = Executors
				.newSingleThreadExecutor(ApolloThreadFactory.create("ReleaseMessageServiceWithCache", true));
	}

	public ReleaseMessage findLatestReleaseMessageForMessages(Set<String> messages) {
		if (CollectionUtils.isEmpty(messages)) {
			return null;
		}

		long maxReleaseMessageId = 0;
		ReleaseMessage result = null;
		for (String message : messages) {
			ReleaseMessage releaseMessage = releaseMessageCache.get(message);
			if (releaseMessage != null && releaseMessage.getId() > maxReleaseMessageId) {
				maxReleaseMessageId = releaseMessage.getId();
				result = releaseMessage;
			}
		}

		return result;
	}

	/**
	 * 每个 Watch Key 对应的最新的 ReleaseMessage 记录(从缓存中获取)。
	 * @param messages
	 * @return
	 */
	public List<ReleaseMessage> findLatestReleaseMessagesGroupByMessages(Set<String> messages) {
		if (CollectionUtils.isEmpty(messages)) {
			return Collections.emptyList();
		}
		List<ReleaseMessage> releaseMessages = Lists.newArrayList();

		for (String message : messages) {
			ReleaseMessage releaseMessage = releaseMessageCache.get(message);
			if (releaseMessage != null) {
				releaseMessages.add(releaseMessage);
			}
		}

		return releaseMessages;
	}

    // 监听器处理消息, 关闭定时拉取
	@Override
	public void handleMessage(ReleaseMessage message, String channel) {
		// Could stop once the ReleaseMessageScanner starts to work
		// 关闭增量拉取定时任务的执行, 后续通过 ReleaseMessageScanner 通知即可。
		doScan.set(false);
		// 是不是也该关闭线程池
		logger.info("message received - channel: {}, message: {}", channel, message);

		// 仅处理 APOLLO_RELEASE_TOPIC
		String content = message.getMessage();
		Tracer.logEvent("Apollo.ReleaseMessageService.UpdateCache", String.valueOf(message.getId()));
		if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(content)) {
			return;
		}

		// 计算 gap
		long gap = message.getId() - maxIdScanned;
		// 若无空缺 gap ，直接合并
		if (gap == 1) {
            // 直接合并当前发布的消息
			mergeReleaseMessage(message);
		} else if (gap > 1) {
			// gap found!
			// 如有空缺 gap ，增量拉取
			// 例如，上述的第 3 步，定时任务还来不及拉取( 即未执行 )，ReleaseMessageScanner 就已经通知，此处会产生空缺的 gap 。
			loadReleaseMessages(maxIdScanned);
		}
	}

    // Spring 调用，初始化定时任务
	@Override
	public void afterPropertiesSet() throws Exception {
		// 从 ServerConfig 中，读取任务的周期配置
		populateDataBaseInterval();

        // 初始, 第一次拉取 ReleaseMessage 到缓存
		// block the startup process until load finished
		// this should happen before ReleaseMessageScanner due to autowire
		loadReleaseMessages(0);

		// 创建定时任务，增量拉取 ReleaseMessage 到缓存，用以处理初始化期间，产生的 ReleaseMessage 遗漏的问题。
		// 20:00:00 程序启动过程中，当前 release message 有 5 条
		// 20:00:01 loadReleaseMessages(0); 执行完成，获取到 5 条记录
		// 20:00:02 有一条 release message 新产生，但是因为程序还没启动完，所以不会触发 handle message 操作
		// 20:00:05 程序启动完成，但是第三步的这条新的 release message 漏了
		// 20:10:00 假设这时又有一条 release message 产生，这次会触发 handle message ，同时会把第三步的那条 release message 加载到
		executorService.submit(() -> {
			while (doScan.get() && !Thread.currentThread().isInterrupted()) {
				// 【TODO 6001】Tracer 日志
				Transaction transaction = Tracer.newTransaction("Apollo.ReleaseMessageServiceWithCache",
						"scanNewReleaseMessages");
				try {
					// 增量拉取 ReleaseMessage 到缓存
					loadReleaseMessages(maxIdScanned);
					transaction.setStatus(Transaction.SUCCESS);
				} catch (Throwable ex) {
					transaction.setStatus(ex);
					logger.error("Scan new release messages failed", ex);
				} finally {
					transaction.complete();
				}
				try {
					scanIntervalTimeUnit.sleep(scanInterval);
				} catch (InterruptedException e) {
					// ignore
				}
			}
		});
	}

	/**
     * 合并到 ReleaseMessage 缓存(合并操作时, 会更新最新数据(根据id大小, 大的为新数据))
     * 
     * @param releaseMessage
     */
	private synchronized void mergeReleaseMessage(ReleaseMessage releaseMessage) {
		// 获得对应的 ReleaseMessage 对象
		ReleaseMessage old = releaseMessageCache.get(releaseMessage.getMessage());

		// 若不存在, 或新查得的新的编号更大，进行更新缓存
		if (old == null || releaseMessage.getId() > old.getId()) {
			releaseMessageCache.put(releaseMessage.getMessage(), releaseMessage);
			maxIdScanned = releaseMessage.getId();
		}
	}

	/**
     * 拉取ReleaseMessage到缓存(每次批量处理500条记录)
     * 
     * @param startId
     */
	private void loadReleaseMessages(long startId) {
		boolean hasMore = true;
		while (hasMore && !Thread.currentThread().isInterrupted()) {
			// 查询500条
			// current batch is 500
			List<ReleaseMessage> releaseMessages = releaseMessageRepository
					.findFirst500ByIdGreaterThanOrderByIdAsc(startId);
			if (CollectionUtils.isEmpty(releaseMessages)) {
				break;
			}

			// 合并到 ReleaseMessage 缓存
			releaseMessages.forEach(this::mergeReleaseMessage);

			// 扫描到的条数, 如果为500, 说明还有记录, 需要继续操作
			int scanned = releaseMessages.size();
			hasMore = scanned == 500;
			// 下次查询起始id
			startId = releaseMessages.get(scanned - 1).getId();
			logger.info("Loaded {} release messages with startId {}", scanned, startId);
		}
	}

	/**
     * 从 ServerConfig 中，读取任务的周期配置信息
     */
	private void populateDataBaseInterval() {
		scanInterval = bizConfig.releaseMessageCacheScanInterval();
		scanIntervalTimeUnit = bizConfig.releaseMessageCacheScanIntervalTimeUnit();
	}

	// only for test use
	private void reset() throws Exception {
		executorService.shutdownNow();
		initialize();
		afterPropertiesSet();
	}
}
