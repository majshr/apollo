package com.ctrip.framework.apollo.configservice.controller;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import com.ctrip.framework.apollo.biz.message.ReleaseMessageListener;
import com.ctrip.framework.apollo.biz.message.Topics;
import com.ctrip.framework.apollo.biz.utils.EntityManagerUtil;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.configservice.service.ReleaseMessageServiceWithCache;
import com.ctrip.framework.apollo.configservice.util.NamespaceUtil;
import com.ctrip.framework.apollo.configservice.util.WatchKeysUtil;
import com.ctrip.framework.apollo.configservice.wrapper.DeferredResultWrapper;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfigNotification;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@RestController
@RequestMapping("/notifications/v2")
public class NotificationControllerV2 implements ReleaseMessageListener {
	private static final Logger logger = LoggerFactory.getLogger(NotificationControllerV2.class);

	/**
     * Watch Key 与 DeferredResultWrapper 的 Multimap; 维护所有请求<br>
     * key: 监听的key(ReleaseMessage.message)
     * Value：DeferredResultWrapper(每个key对应多个等待通知的客户端) <br>
     * 目前 Apollo 的实现上，Watch Key 等价于 ReleaseMessage 的通知内容 message 字段<br>
     * 只要一个namespace对应的配置发生改变, 就需要通知监听的请求 当请求的 Namespace 暂无新通知时，会将该 Namespace
     * 对应的 Watch Key 们，注册到 deferredResults 中。<br>
     * 等到 Namespace 配置发生变更时，在 #handleMessage(...) 中，进行通知。
     */
	private final Multimap<String, DeferredResultWrapper> deferredResults = Multimaps
			.synchronizedSetMultimap(TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Ordering.natural()));

	/**
	 * "+" 拼接符
	 */
	private static final Splitter STRING_SPLITTER = Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
			.omitEmptyStrings();

	private static final Type notificationsTypeReference = new TypeToken<List<ApolloConfigNotification>>() {
	}.getType();

	/**
	 * 大量通知分批执行 ExecutorService
	 */
	private final ExecutorService largeNotificationBatchExecutorService;

	private final WatchKeysUtil watchKeysUtil;
	private final ReleaseMessageServiceWithCache releaseMessageService;
	private final EntityManagerUtil entityManagerUtil;
	private final NamespaceUtil namespaceUtil;
	private final Gson gson;
	private final BizConfig bizConfig;

	@Autowired
	public NotificationControllerV2(final WatchKeysUtil watchKeysUtil,
			final ReleaseMessageServiceWithCache releaseMessageService, final EntityManagerUtil entityManagerUtil,
			final NamespaceUtil namespaceUtil, final Gson gson, final BizConfig bizConfig) {
		largeNotificationBatchExecutorService = Executors
				.newSingleThreadExecutor(ApolloThreadFactory.create("NotificationControllerV2", true));
		this.watchKeysUtil = watchKeysUtil;
		this.releaseMessageService = releaseMessageService;
		this.entityManagerUtil = entityManagerUtil;
		this.namespaceUtil = namespaceUtil;
		this.gson = gson;
		this.bizConfig = bizConfig;
	}

	/**
	 * 拉取更新配置, 仅返回配置发生变化的Namespace对应的ApolloConfigNotification<br>
	 * 有几个 配置发生变化的 Namespace ，返回几个对应的 ApolloConfigNotification 。
	 * 另外，客户端接收到返回后，会增量合并到本地的配置通知信息。客户端下次请求时，使用合并后的配置通知信息。
	 * @param appId
	 * @param cluster
	 * @param notificationsAsString
	 * @param dataCenter
	 * @param clientIp
	 * @return
	 */
	@GetMapping
	public DeferredResult<ResponseEntity<List<ApolloConfigNotification>>> pollNotification(
			@RequestParam(value = "appId") String appId, @RequestParam(value = "cluster") String cluster,
			@RequestParam(value = "notifications") String notificationsAsString,
			@RequestParam(value = "dataCenter", required = false) String dataCenter,
			@RequestParam(value = "ip", required = false) String clientIp) {

		// 客户端监听的namespace
		// 解析 notificationsAsString 参数，创建 ApolloConfigNotification 数组。
		List<ApolloConfigNotification> notifications = null;
		try {
            // 因为一个客户端可以订阅多个 Namespace ，所以该参数是 List 。
            // 参数是{客户端需要使用的namespace-客户端最新更新编号}集合
			/*
			 * 接口真正返回的结果也是 List<ApolloConfigNotification> ，
			 * 仅返回配置发生变化的 Namespace 对应的 ApolloConfigNotification 。
			 * 也就说，当有几个 配置发生变化的 Namespace ，返回几个对应的 ApolloConfigNotification 。
			 * 另外，客户端接收到返回后，会增量合并到本地的配置通知信息。客户端下次请求时，使用合并后的配置通知信息。
			 */
			// 客户端请求时，只传递 ApolloConfigNotification 的 namespaceName + notificationId ，不传递 messages 。
			notifications = gson.fromJson(notificationsAsString, notificationsTypeReference);
		} catch (Throwable ex) {
			Tracer.logError(ex);
		}
		if (CollectionUtils.isEmpty(notifications)) {
			throw new BadRequestException("Invalid format of notifications: " + notificationsAsString);
		}

        // 创建 DeferredResultWrapper 对象(对应单个请求, 请求可能监听多个namespace)
		DeferredResultWrapper deferredResultWrapper = new DeferredResultWrapper(bizConfig.longPollingTimeoutInMilli());
		
        // Namespace 集合(归一化后namespace)
		Set<String> namespaces = Sets.newHashSet();
        // 客户端发布信息 Map 。key 为 Namespace 名，value 为通知编号。
		Map<String, Long> clientSideNotifications = Maps.newHashMap();
		// 过滤并创建 ApolloConfigNotification Map(名字归一化处理)
		Map<String, ApolloConfigNotification> filteredNotifications = filterNotifications(appId, notifications);
		// 循环 ApolloConfigNotification Map ，初始化上述变量。
		for (Map.Entry<String, ApolloConfigNotification> notificationEntry : filteredNotifications.entrySet()) {
			String normalizedNamespace = notificationEntry.getKey();
			ApolloConfigNotification notification = notificationEntry.getValue();
			// 添加到 `namespaces` 中。
			namespaces.add(normalizedNamespace);
			// 添加到 `clientSideNotifications` 中。
			clientSideNotifications.put(normalizedNamespace, notification.getNotificationId());
			// 记录名字被归一化的 Namespace 。因为，最终返回给客户端，使用原始的 Namespace 名字，否则客户端无法识别。
			if (!Objects.equals(notification.getNamespaceName(), normalizedNamespace)) {
				deferredResultWrapper.recordNamespaceNameNormalizedResult(notification.getNamespaceName(),
						normalizedNamespace);
			}
		}

		if (CollectionUtils.isEmpty(namespaces)) {
			throw new BadRequestException("Invalid format of notifications: " + notificationsAsString);
		}

		// 组装 Watch Key Multimap(Key 为 Namespace 的名字，Value 为 Watch Key)
		Multimap<String, String> watchedKeysMap = watchKeysUtil.assembleAllWatchKeys(appId, cluster, namespaces,
				dataCenter);
		// 生成 Watch Key 集合
		Set<String> watchedKeys = Sets.newHashSet(watchedKeysMap.values());

		/**
		 * 1、set deferredResult before the check, for avoid more waiting If the check
		 * before setting deferredResult,it may receive a notification the next time
		 * when method handleMessage is executed between check and set deferredResult.
		 */
		// 注册超时事件
		deferredResultWrapper.onTimeout(
				() -> logWatchedKeys(watchedKeys, "Apollo.LongPoll.TimeOutKeys"));

		// 注册监听结束事件
		deferredResultWrapper.onCompletion(() -> {
			// unregister all keys
			// 移除 Watch Key + DeferredResultWrapper 出 `deferredResults`
			for (String key : watchedKeys) {
				deferredResults.remove(key, deferredResultWrapper);
			}
			// 记录日志
			logWatchedKeys(watchedKeys, "Apollo.LongPoll.CompletedKeys");
		});

		// 注册 Watch Key + DeferredResultWrapper 到 `deferredResults` 中，等待配置发生变化后通知。
		// 详见 `#handleMessage(...)` 方法。
		// register all keys
		for (String key : watchedKeys) {
			// 注册监听
			this.deferredResults.put(key, deferredResultWrapper);
		}

		logWatchedKeys(watchedKeys, "Apollo.LongPoll.RegisteredKeys");
		logger.debug("Listening {} from appId: {}, cluster: {}, namespace: {}, datacenter: {}", watchedKeys, appId,
				cluster, namespaces, dataCenter);

		/**
		 * 2、check new release<br> 
		 * 获得 Watch Key 集合中，每个 Watch Key 对应的 最新的ReleaseMessage 记录。
		 */
		List<ReleaseMessage> latestReleaseMessages = releaseMessageService
				.findLatestReleaseMessagesGroupByMessages(watchedKeys);

		/**
		 * Manually close the entity manager. Since for async request, Spring won't do
		 * so until the request is finished, which is unacceptable since we are doing
		 * long polling - means the db connection would be hold for a very long time
		 */
		// 手动关闭 EntityManager
		// 因为对于 async 请求，SpringMVC 在请求完成之前不会这样做
		// 这是不可接受的，因为我们正在做长轮询——意味着 db 连接将被保留很长时间。
		// 实际上，下面的过程，我们已经不需要 db 连接，因此进行关闭。
		entityManagerUtil.closeEntityManager();

        // 获得 ApolloConfigNotification 通知数组(发布信息比client现在存储的发布信息新)
		List<ApolloConfigNotification> newNotifications = getApolloConfigNotifications(namespaces,
				clientSideNotifications, watchedKeysMap, latestReleaseMessages);

		// 若有新的通知，调用 DeferredResultWrapper#setResult(List<ApolloConfigNotification>) 方法
		// 直接设置 DeferredResult 的结果，从而结束长轮询。
		if (!CollectionUtils.isEmpty(newNotifications)) {
			deferredResultWrapper.setResult(newNotifications);
		}

		return deferredResultWrapper.getResult();
	}

	/**
     * 过滤并创建 ApolloConfigNotification Map 。其中，KEY 为 Namespace归一化后 的名字<br>
     * 目的是客户端传递的 Namespace 的名字不是正确的，例如大小写不对，需要做下归一化( normalized )处理。
     * 
     * @param appId
     * @param notifications
     * @return
     */
	private Map<String, ApolloConfigNotification> filterNotifications(String appId,
			List<ApolloConfigNotification> notifications) {
        // 返回结果信息, key为namespace从数据库查询归一化之后的名字
		Map<String, ApolloConfigNotification> filteredNotifications = Maps.newHashMap();
		
		for (ApolloConfigNotification notification : notifications) {
			if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
				continue;
			}
			
			// 若 Namespace 名以 .properties 结尾，移除该结尾，并设置到 ApolloConfigNotification 中。
			// 例如 application.properties => application 。
			// strip out .properties suffix
			String originalNamespace = namespaceUtil.filterNamespaceName(notification.getNamespaceName());
			notification.setNamespaceName(originalNamespace);
			
			// 获得归一化的 Namespace 名字。因为，客户端 Namespace 会填写错大小写。
			// 例如，数据库中已存在数据的 Namespace 名为 Fx.Apollo ，而客户端 Namespace 名为 fx.Apollo
			// fix the character case issue, such as FX.apollo <-> fx.apollo
			String normalizedNamespace = namespaceUtil.normalizeNamespace(appId, originalNamespace);

			// in case client side namespace name has character case issue and has
			// difference notification ids
			// such as FX.apollo = 1 but fx.apollo = 2, we should let FX.apollo have the
			// chance to update its notification id
			// which means we should record FX.apollo = 1 here and ignore fx.apollo = 2
			// 如果客户端 Namespace 的名字有大小写的问题，并且恰好有不同的通知编号。
			// 例如 Namespace 名字为 FX.apollo 的通知编号是 1 ，但是 fx.apollo 的通知编号为 2 。
			// 我们应该让 FX.apollo 可以更新它的通知编号，
			// 所以，我们使用 FX.apollo 的 ApolloConfigNotification 对象，添加到结果，而忽略 fx.apollo 。
			// 通过这样的方式，若此时服务器的通知编号为 3 ，那么 "FX.apollo" 的通知编号先更新成 3 ，再下一次长轮询时，"fx.apollo" 的通知编号再更新成 3 。
			if (filteredNotifications.containsKey(normalizedNamespace) && filteredNotifications.get(normalizedNamespace)
					.getNotificationId() < notification.getNotificationId()) {
				continue;
			}

			filteredNotifications.put(normalizedNamespace, notification);
		}
		return filteredNotifications;
	}

	/**
     * 获得新的 ApolloConfigNotification 通知数组
     * 
     * @param namespaces
     *            等待通知的namespace数组
     * @param clientSideNotifications
     *            客户端通知编号
     * @param watchedKeysMap
     *            {namespace: watchKey}map
     * @param latestReleaseMessages
     *            最新的ReleaseMessage集合
     * @return
     */
	private List<ApolloConfigNotification> getApolloConfigNotifications(Set<String> namespaces,
			Map<String, Long> clientSideNotifications, Multimap<String, String> watchedKeysMap,
			List<ReleaseMessage> latestReleaseMessages) {
		// 创建 ApolloConfigNotification 数组
		List<ApolloConfigNotification> newNotifications = Lists.newArrayList();
		
		if (!CollectionUtils.isEmpty(latestReleaseMessages)) {
			// 创建最新通知的 Map 。其中 Key 为 Watch Key; value为ReleaseMessage.id 。
			Map<String, Long> latestNotifications = Maps.newHashMap();
            // 最新ReleaseMessage消息map
			for (ReleaseMessage releaseMessage : latestReleaseMessages) {
				latestNotifications.put(releaseMessage.getMessage(), releaseMessage.getId());
			}

			// 循环 Namespace 的名字的集合，判断是否有配置更新
			for (String namespace : namespaces) {
				// 客户端编号
				long clientSideId = clientSideNotifications.get(namespace);
                // 记录当前最新通知编号
				long latestId = ConfigConsts.NOTIFICATION_ID_PLACEHOLDER;
				// 获得 Namespace 对应的 Watch Key 集合
				Collection<String> namespaceWatchedKeys = watchedKeysMap.get(namespace);
				
				// 获得最大的通知编号
				for (String namespaceWatchedKey : namespaceWatchedKeys) {
					long namespaceNotificationId = latestNotifications.getOrDefault(namespaceWatchedKey,
							ConfigConsts.NOTIFICATION_ID_PLACEHOLDER);
					if (namespaceNotificationId > latestId) {
						latestId = namespaceNotificationId;
					}
				}
				
				// 若服务器的通知编号大于客户端的通知编号，意味着有配置更新
				if (latestId > clientSideId) {
					// 创建 ApolloConfigNotification 对象
					ApolloConfigNotification notification = new ApolloConfigNotification(namespace, latestId);
					// 循环添加通知编号到 ApolloConfigNotification 中。
					namespaceWatchedKeys.stream().filter(latestNotifications::containsKey)
							.forEach(namespaceWatchedKey -> notification.addMessage(namespaceWatchedKey,
									latestNotifications.get(namespaceWatchedKey)));
					// 添加 ApolloConfigNotification 对象到结果
					newNotifications.add(notification);
				}
			}
		}
		return newNotifications;
	}

    // 当有新的 ReleaseMessage 时，通知其对应的 Namespace 的(设置deferredResults)，响应正在等待的请求。
	@Override
	public void handleMessage(ReleaseMessage message, String channel) {
		logger.info("message received - channel: {}, message: {}", channel, message);

		// 【TODO 6001】Tracer 日志
		String content = message.getMessage();
		Tracer.logEvent("Apollo.LongPoll.Messages", content);
		
		// 仅处理 APOLLO_RELEASE_TOPIC
		if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(content)) {
			return;
		}

		// 获得对应的 Namespace 的名字
		String changedNamespace = retrieveNamespaceFromReleaseMessage.apply(content);

		if (Strings.isNullOrEmpty(changedNamespace)) {
			logger.error("message format invalid - {}", content);
			return;
		}

		// `deferredResults` 存在对应的 Watch Key
		if (!deferredResults.containsKey(content)) {
			return;
		}

		// create a new list to avoid ConcurrentModificationException
		// 从 deferredResults 中读取并创建 DeferredResultWrapper 数组，避免并发问题。
		List<DeferredResultWrapper> results = Lists.newArrayList(deferredResults.get(content));

		// 创建 ApolloConfigNotification 对象
		ApolloConfigNotification configNotification = new ApolloConfigNotification(changedNamespace, message.getId());
		configNotification.addMessage(content, message.getId());

		// do async notification if too many clients
		// 若需要通知的客户端过多，使用 ExecutorService 异步通知，避免“惊群效应”
		if (results.size() > bizConfig.releaseMessageNotificationBatch()) {
			largeNotificationBatchExecutorService.submit(() -> {
				logger.debug("Async notify {} clients for key {} with batch {}", results.size(), content,
						bizConfig.releaseMessageNotificationBatch());
				// 假设一个公共 Namespace 有10W 台机器使用，如果该公共 Namespace 发布时直接下发配置更新消息的话，
				// 就会导致这 10W 台机器一下子都来请求配置，这动静就有点大了，而且对 Config Service 的压力也会比较大。
				
				// 数量可通过 ServerConfig "apollo.release-message.notification.batch" 配置，默认 100 。
				// 每通知 "apollo.release-message.notification.batch" 个客户端，sleep 一段时间。
				//可通过 ServerConfig "apollo.release-message.notification.batch.interval" 配置，默认 100 毫秒。
				for (int i = 0; i < results.size(); i++) {
					// 每 N 个客户端，sleep 一段时间。
					if (i > 0 && i % bizConfig.releaseMessageNotificationBatch() == 0) {
						try {
							TimeUnit.MILLISECONDS.sleep(bizConfig.releaseMessageNotificationBatchIntervalInMilli());
						} catch (InterruptedException e) {
							// ignore
						}
					}
					logger.debug("Async notify {}", results.get(i));
					// 设置结果, 从而结束长轮询
					results.get(i).setResult(configNotification);
				}
			});
			return;
		}

		logger.debug("Notify {} clients for key {}", results.size(), content);

		// 设置结果
		for (DeferredResultWrapper result : results) {
			result.setResult(configNotification);
		}
		logger.debug("Notification completed");
	}

	/**
	 * 通过 ReleaseMessage 的消息内容，获得对应 Namespace 的名字<br>
	 * Function<T, R>, 方法参数类型为T, 方法返回值类型为R<br>
	 * 实现是参数通过"+"分割, 获取截取到的字符串
	 */
	private static final Function<String, String> retrieveNamespaceFromReleaseMessage = releaseMessage -> {
		if (Strings.isNullOrEmpty(releaseMessage)) {
			return null;
		}
		List<String> keys = STRING_SPLITTER.splitToList(releaseMessage);
		// message should be appId+cluster+namespace
		if (keys.size() != 3) {
			logger.error("message format invalid - {}", releaseMessage);
			return null;
		}
		return keys.get(2);
	};

	/**
	 * 记录超时事件(超时会执行)
	 * @param watchedKeys
	 * @param eventName
	 */
	private void logWatchedKeys(Set<String> watchedKeys, String eventName) {
		for (String watchedKey : watchedKeys) {
			Tracer.logEvent(eventName, watchedKey);
		}
	}
}
