package com.ctrip.framework.apollo.internals;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.Apollo;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfig;
import com.ctrip.framework.apollo.core.dto.ApolloNotificationMessages;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.schedule.ExponentialSchedulePolicy;
import com.ctrip.framework.apollo.core.schedule.SchedulePolicy;
import com.ctrip.framework.apollo.core.signature.Signature;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.exceptions.ApolloConfigStatusCodeException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;

/**
 * 实现 AbstractConfigRepository 抽象类，远程配置 Repository 。<br>
 * 实现从 Config Service 拉取配置，并缓存在内存中。并且，定时 + 实时刷新缓存。<br>
 * 每个namespace对应一个RemoteConfigRepository对象
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class RemoteConfigRepository extends AbstractConfigRepository {
	private static final Logger logger = LoggerFactory.getLogger(RemoteConfigRepository.class);
	private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
	private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");

	private static final Escaper pathEscaper = UrlEscapers.urlPathSegmentEscaper();
	private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

	private final ConfigServiceLocator m_serviceLocator;
	private final HttpUtil m_httpUtil;
	private final ConfigUtil m_configUtil;
	
	//***************************基础属性******************************
	/**
	 * 指向 ApolloConfig 的 AtomicReference ，缓存配置
	 */
	private volatile AtomicReference<ApolloConfig> m_configCache;
	/**
	 * Namespace 名字, 一个 RemoteConfigRepository 对应一个 Namespace 。
	 */
	private final String m_namespace;
	
	// *************************轮询属性***************************************
	/**
	 * ScheduledExecutorService 对象
	 */
	private final static ScheduledExecutorService m_executorService;
	
	/**
	 * 指向 ApolloNotificationMessages 的 AtomicReference
	 */
	private final AtomicReference<ApolloNotificationMessages> m_remoteMessages;
	
	/**
	 * 失败定时重试策略，使用 {@link ExponentialSchedulePolicy}
	 */
	private final SchedulePolicy m_loadConfigFailSchedulePolicy;
	
	/**
     * 加载配置的 RateLimiter(限流器, qps配置设置的)
     */
	private final RateLimiter m_loadConfigRateLimiter;

	// ***********************通知属性*************************8
	/**
     * 指向 ServiceDTO(长轮询通知变化的Config Service的信息) 的 AtomicReference
     */
	private final AtomicReference<ServiceDTO> m_longPollServiceDto;
	
	/**
	 * 远程配置长轮询服务
	 */
	private final RemoteConfigLongPollService remoteConfigLongPollService;
	
	/**
     * 是否强制拉取缓存的标记<br>
     *
     * 若为 true ，则多一轮从 Config Service 拉取配置<br>
     * 为 true 的原因，RemoteConfigRepository 知道 Config Service 有配置刷新,
     * 例如有新的通知的情况下。<br>
     */
	private final AtomicBoolean m_configNeedForceRefresh;
	
	// ========================================================

	private final Gson gson;

	static {
		// 单线程
		m_executorService = Executors.newScheduledThreadPool(1,
				ApolloThreadFactory.create("RemoteConfigRepository", true));
	}

	/**
	 * Constructor.
	 *
	 * @param namespace the namespace
	 */
	public RemoteConfigRepository(String namespace) {
		m_namespace = namespace;
		m_configCache = new AtomicReference<>();
		m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
		m_httpUtil = ApolloInjector.getInstance(HttpUtil.class);
		m_serviceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
		remoteConfigLongPollService = ApolloInjector.getInstance(RemoteConfigLongPollService.class);
		m_longPollServiceDto = new AtomicReference<>();
		m_remoteMessages = new AtomicReference<>();
		m_loadConfigRateLimiter = RateLimiter.create(m_configUtil.getLoadConfigQPS());
		m_configNeedForceRefresh = new AtomicBoolean(true);
		m_loadConfigFailSchedulePolicy = new ExponentialSchedulePolicy(m_configUtil.getOnErrorRetryInterval(),
				m_configUtil.getOnErrorRetryInterval() * 8);
		gson = new Gson();
		
		// 尝试同步配置, 作为初次的配置缓存初始化。
		this.trySync();
		// 初始化定时刷新配置的任务
		this.schedulePeriodicRefresh();
		// 注册自己到 RemoteConfigLongPollService 中，实现配置更新的实时通知
		this.scheduleLongPollingRefresh();
	}

	@Override
	public Properties getConfig() {
        // 如果缓存为空，强制从 Config Service 拉取配置
		if (m_configCache.get() == null) {
            // 强制从 Config Service 拉取配置。
			this.sync();
		}
        // 转换成 Properties 对象，并返回
		return transformApolloConfigToProperties(m_configCache.get());
	}

	@Override
	public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
		// remote config doesn't need upstream
	}

	@Override
	public ConfigSourceType getSourceType() {
		return ConfigSourceType.REMOTE;
	}

	/**
     * 初始化定时刷新配置的任务(每隔配置时间调用trySync()方法更新)
     */
	private void schedulePeriodicRefresh() {
		logger.debug("Schedule periodic refresh with interval: {} {}", m_configUtil.getRefreshInterval(),
				m_configUtil.getRefreshIntervalTimeUnit());
		
        // 创建定时任务，定时刷新配置(每隔5分钟, 调用trySync同步配置)
		m_executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				Tracer.logEvent("Apollo.ConfigService", String.format("periodicRefresh: %s", m_namespace));
				logger.debug("refresh config for namespace: {}", m_namespace);
				
				// 尝试同步配置
				trySync();
				
				Tracer.logEvent("Apollo.Client.Version", Apollo.VERSION);
			}
		}, m_configUtil.getRefreshInterval(), m_configUtil.getRefreshInterval(),
				m_configUtil.getRefreshIntervalTimeUnit());
	}

	@Override
	protected synchronized void sync() {
		Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncRemoteConfig");

		try {
			// 获得缓存的 ApolloConfig 对象
			ApolloConfig previous = m_configCache.get();
			// 从 Config Service 加载 ApolloConfig 对象
			ApolloConfig current = loadApolloConfig();

			// reference equals means HTTP 304
			// 缓存的和加载的ApolloConfig若不相等，说明更新了，设置到缓存中
			if (previous != current) {
				logger.debug("Remote Config refreshed!");
				// 设置到缓存
				m_configCache.set(current);
				// 发布 Repository 的配置发生变化，触发对应的监听器们
				this.fireRepositoryChange(m_namespace, this.getConfig());
			}

			if (current != null) {
				Tracer.logEvent(String.format("Apollo.Client.Configs.%s", current.getNamespaceName()),
						current.getReleaseKey());
			}

			transaction.setStatus(Transaction.SUCCESS);
		} catch (Throwable ex) {
			transaction.setStatus(ex);
			throw ex;
		} finally {
			transaction.complete();
		}
	}

    /**
     * 配置转换为properties格式
     * 
     * @param apolloConfig
     * @return Properties
     * @date: 2020年5月8日 下午5:33:38
     */
	private Properties transformApolloConfigToProperties(ApolloConfig apolloConfig) {
		Properties result = propertiesFactory.getPropertiesInstance();
		result.putAll(apolloConfig.getConfigurations());
		return result;
	}

	/**
	 * 从 Config Service 加载 ApolloConfig 对象
	 * @return
	 */
	private ApolloConfig loadApolloConfig() {
		// 限流
		if (!m_loadConfigRateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
			// wait at most 5 seconds
			// 判断是否被限流, 如果被限流, sleep五秒, 避免对Config service请求过于频繁
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException e) {
			}
		}
		
		// 获得 appId cluster dataCenter 配置信息
		String appId = m_configUtil.getAppId();
		String cluster = m_configUtil.getCluster();
		String dataCenter = m_configUtil.getDataCenter();
		String secret = m_configUtil.getAccessKeySecret();
		Tracer.logEvent("Apollo.Client.ConfigMeta", STRING_JOINER.join(appId, cluster, m_namespace));
        // 计算重试次数, 若 m_configNeedForceRefresh 为 true ，代表强制刷新配置，会多重试一次。
		int maxRetries = m_configNeedForceRefresh.get() ? 2 : 1;
		long onErrorSleepTime = 0; // 0 means no sleep
		Throwable exception = null;

		// 获得所有的 Config Service 的地址
		List<ServiceDTO> configServices = getConfigServices();
		String url = null;
		// 循环读取配置重试次数直到成功。每一次，都会循环所有的 ServiceDTO 数组。
		for (int i = 0; i < maxRetries; i++) {
			// 随机所有的 Config Service 的地址
			List<ServiceDTO> randomConfigServices = Lists.newLinkedList(configServices);
			Collections.shuffle(randomConfigServices);
			// 优先访问通知配置变更的 Config Service 的地址。并且，获取到时，需要置空，避免重复优先访问。
			// Access the server which notifies the client first
			if (m_longPollServiceDto.get() != null) {
				randomConfigServices.add(0, m_longPollServiceDto.getAndSet(null));
			}

            // 循环所有的 Config Service 的地址, 读取配置重试次数直到成功。
			for (ServiceDTO configService : randomConfigServices) {
				// sleep 等待，下次从 Config Service 拉取配置
				if (onErrorSleepTime > 0) {
					logger.warn("Load config failed, will retry in {} {}. appId: {}, cluster: {}, namespaces: {}",
							onErrorSleepTime, m_configUtil.getOnErrorRetryIntervalTimeUnit(), appId, cluster,
							m_namespace);

					try {
						m_configUtil.getOnErrorRetryIntervalTimeUnit().sleep(onErrorSleepTime);
					} catch (InterruptedException e) {
						// ignore
					}
				}

                // 组装查询配置的地址(http://172.16.91.3:8080/configs/app-m/default/application?ip=172.16.91.3)
				url = assembleQueryConfigUrl(configService.getHomepageUrl(), appId, cluster, m_namespace, dataCenter,
						m_remoteMessages.get(), m_configCache.get());

				logger.debug("Loading config from {}", url);

				// 创建 HttpRequest 对象
				HttpRequest request = new HttpRequest(url);
				if (!StringUtils.isBlank(secret)) {
					Map<String, String> headers = Signature.buildHttpHeaders(url, appId, secret);
					request.setHeaders(headers);
				}

				Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "queryConfig");
				transaction.addData("Url", url);
				try {

					// 发起请求，返回 HttpResponse 对象
					HttpResponse<ApolloConfig> response = m_httpUtil.doGet(request, ApolloConfig.class);
					// 设置 m_configNeedForceRefresh = false
					m_configNeedForceRefresh.set(false);
					// 标记成功
					m_loadConfigFailSchedulePolicy.success();

					transaction.addData("StatusCode", response.getStatusCode());
					transaction.setStatus(Transaction.SUCCESS);

					// 无新的配置，直接返回缓存的 ApolloConfig 对象
					if (response.getStatusCode() == 304) {
						logger.debug("Config server responds with 304 HTTP status code.");
						return m_configCache.get();
					}

					// 有新的配置，进行返回新的 ApolloConfig 对象
					ApolloConfig result = response.getBody();

					logger.debug("Loaded config for {}: {}", m_namespace, result);

					return result;
				} catch (ApolloConfigStatusCodeException ex) {
					ApolloConfigStatusCodeException statusCodeException = ex;
					// config not found
					// 若返回的状态码是 404 ，说明查询配置的 Config Service 不存在该 Namespace 。
					if (ex.getStatusCode() == 404) {
						String message = String.format(
								"Could not find config for namespace - appId: %s, cluster: %s, namespace: %s, "
										+ "please check whether the configs are released in Apollo!",
								appId, cluster, m_namespace);
						statusCodeException = new ApolloConfigStatusCodeException(ex.getStatusCode(), message);
					}
					Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(statusCodeException));
					transaction.setStatus(statusCodeException);
					exception = statusCodeException;
				} catch (Throwable ex) {
					Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
					transaction.setStatus(ex);
					exception = ex;
				} finally {
					transaction.complete();
				}

				// if force refresh, do normal sleep, if normal config load, do exponential
				// sleep
				onErrorSleepTime = m_configNeedForceRefresh.get() ? m_configUtil.getOnErrorRetryInterval()
						: m_loadConfigFailSchedulePolicy.fail();
			}

		}
		// 若查询配置失败，抛出 ApolloConfigException 异常
		String message = String.format("Load Apollo Config failed - appId: %s, cluster: %s, namespace: %s, url: %s",
				appId, cluster, m_namespace, url);
		throw new ApolloConfigException(message, exception);
	}

	/**
	 * 组装轮询 Config Service 的配置读取 (/configs/{appId}/{clusterName}/{namespace:.+} 接口的 URL )
	 * @param uri
	 * @param appId
	 * @param cluster
	 * @param namespace
	 * @param dataCenter
	 * @param remoteMessages
	 * @param previousConfig
	 * @return
	 */
	String assembleQueryConfigUrl(String uri, String appId, String cluster, String namespace, String dataCenter,
			ApolloNotificationMessages remoteMessages, ApolloConfig previousConfig) {

		String path = "configs/%s/%s/%s";
		List<String> pathParams = Lists.newArrayList(pathEscaper.escape(appId), pathEscaper.escape(cluster),
				pathEscaper.escape(namespace));
		Map<String, String> queryParams = Maps.newHashMap();

		// releaseKey
		if (previousConfig != null) {
			queryParams.put("releaseKey", queryParamEscaper.escape(previousConfig.getReleaseKey()));
		}

		// dataCenter
		if (!Strings.isNullOrEmpty(dataCenter)) {
			queryParams.put("dataCenter", queryParamEscaper.escape(dataCenter));
		}

		// ip
		String localIp = m_configUtil.getLocalIp();
		if (!Strings.isNullOrEmpty(localIp)) {
			queryParams.put("ip", queryParamEscaper.escape(localIp));
		}

		// messages
		if (remoteMessages != null) {
			queryParams.put("messages", queryParamEscaper.escape(gson.toJson(remoteMessages)));
		}

		// 格式化 URL
		String pathExpanded = String.format(path, pathParams.toArray());

		// 拼接 Query String
		if (!queryParams.isEmpty()) {
			pathExpanded += "?" + MAP_JOINER.join(queryParams);
		}
		// 拼接最终的请求 URL
		if (!uri.endsWith("/")) {
			uri += "/";
		}
		return uri + pathExpanded;
	}

	/**
	 * 注册自己到 RemoteConfigLongPollService 中，实现配置更新的实时通知<br>
	 * 当 RemoteConfigLongPollService 长轮询到该 RemoteConfigRepository 的 Namespace 下的配置更新时，会回调 
	 * onLongPollNotified(ServiceDTO, ApolloNotificationMessages)
	 */
	private void scheduleLongPollingRefresh() {
		remoteConfigLongPollService.submit(m_namespace, this);
	}

	/**
     * RemoteConfigLongPollService 长轮询到RemoteConfigRepository 的 Namespace
     * 下的配置更新时, 会回调此方法; 同步配置
     * 
     * @param longPollNotifiedServiceDto
     * @param remoteMessages
     */
	public void onLongPollNotified(ServiceDTO longPollNotifiedServiceDto, ApolloNotificationMessages remoteMessages) {
		// 设置长轮询到配置更新的 Config Service 。下次同步配置时，优先读取该服务
		m_longPollServiceDto.set(longPollNotifiedServiceDto);
		// 设置 m_remoteMessages
		m_remoteMessages.set(remoteMessages);
		// 提交同步任务
		m_executorService.submit(new Runnable() {
			@Override
			public void run() {
				// 设置 m_configNeedForceRefresh 为 true
				m_configNeedForceRefresh.set(true);
				// 尝试同步配置
				trySync();
			}
		});
	}

	/**
     * 获得所有 Config Service 信息(Config Service集群地址们)
     * 
     * @return
     */
	private List<ServiceDTO> getConfigServices() {
		List<ServiceDTO> services = m_serviceLocator.getConfigServices();
		if (services.size() == 0) {
			throw new ApolloConfigException("No available config service");
		}

		return services;
	}
}
