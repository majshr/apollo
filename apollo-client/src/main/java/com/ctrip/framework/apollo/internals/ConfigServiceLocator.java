package com.ctrip.framework.apollo.internals;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ServiceNameConsts;
import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.ctrip.framework.apollo.util.http.HttpRequest;
import com.ctrip.framework.apollo.util.http.HttpResponse;
import com.ctrip.framework.apollo.util.http.HttpUtil;
import com.ctrip.framework.foundation.Foundation;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.reflect.TypeToken;

/**
 * ConfigServer信息加载器<br>
 * 初始时，从 Meta Service 获取 Config Service 集群地址进行缓存。<br>
 * 定时任务，每 5 分钟，从 Meta Service 获取 Config Service 集群地址刷新缓存。<br>
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月26日 下午4:44:54
 */
public class ConfigServiceLocator {
    private static final Logger logger = LoggerFactory.getLogger(ConfigServiceLocator.class);
    private HttpUtil m_httpUtil;
    private ConfigUtil m_configUtil;
    private AtomicReference<List<ServiceDTO>> m_configServices;
    private Type m_responseType;
    private ScheduledExecutorService m_executorService;
    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");
    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

    /**
     * Create a config service locator.
     */
    public ConfigServiceLocator() {
        List<ServiceDTO> initial = Lists.newArrayList();
        m_configServices = new AtomicReference<>(initial);
        m_responseType = new TypeToken<List<ServiceDTO>>() {
        }.getType();
        m_httpUtil = ApolloInjector.getInstance(HttpUtil.class);
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        this.m_executorService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("ConfigServiceLocator", true));

        // 初始化查询配置
        initConfigServices();
    }

    /**
     * 构造方法执行时初始化
     * 
     * @date: 2020年4月27日 上午9:51:53
     */
    private void initConfigServices() {
        // get from run time configurations
        List<ServiceDTO> customizedConfigServices = getCustomizedConfigService();

        if (customizedConfigServices != null) {
            setConfigServices(customizedConfigServices);
            return;
        }

        // update from meta service
        this.tryUpdateConfigServices();
        this.schedulePeriodicRefresh();
    }

    /**
     * 获取定制的config service地址
     * 
     * @return List<ServiceDTO>
     * @date: 2020年4月27日 上午10:25:47
     */
    private List<ServiceDTO> getCustomizedConfigService() {
        // 1. Get from System Property
        String configServices = System.getProperty("apollo.configService");
        if (Strings.isNullOrEmpty(configServices)) {
            // 2. Get from OS environment variable
            configServices = System.getenv("APOLLO_CONFIGSERVICE");
        }
        if (Strings.isNullOrEmpty(configServices)) {
            // 3. Get from server.properties
            configServices = Foundation.server().getProperty("apollo.configService", null);
        }

        if (Strings.isNullOrEmpty(configServices)) {
            return null;
        }

        logger.warn(
                "Located config services from apollo.configService configuration: {}, will not refresh config services from remote meta service!",
                configServices);

        // mock service dto list
        String[] configServiceUrls = configServices.split(",");
        List<ServiceDTO> serviceDTOS = Lists.newArrayList();

        for (String configServiceUrl : configServiceUrls) {
            configServiceUrl = configServiceUrl.trim();
            ServiceDTO serviceDTO = new ServiceDTO();
            serviceDTO.setHomepageUrl(configServiceUrl);
            serviceDTO.setAppName(ServiceNameConsts.APOLLO_CONFIGSERVICE);
            serviceDTO.setInstanceId(configServiceUrl);
            serviceDTOS.add(serviceDTO);
        }

        return serviceDTOS;
    }

    /**
     * Get the config service info from remote meta server.
     *
     * @return the services dto
     */
    public List<ServiceDTO> getConfigServices() {
        if (m_configServices.get().isEmpty()) {
            updateConfigServices();
        }

        return m_configServices.get();
    }

    /**
     * 更新服务(从meta service更新)
     * 
     * @return boolean
     * @date: 2020年4月27日 上午9:53:50
     */
    private boolean tryUpdateConfigServices() {
        try {
            updateConfigServices();
            return true;
        } catch (Throwable ex) {
            // ignore
        }
        return false;
    }

    /**
     * 刷新定时任务(定时周期从meta service查询服务)
     * 
     * void
     * 
     * @date: 2020年4月27日 上午9:53:38
     */
    private void schedulePeriodicRefresh() {
        this.m_executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                logger.debug("refresh config services");
                Tracer.logEvent("Apollo.MetaService", "periodicRefresh");
                tryUpdateConfigServices();
            }
        }, m_configUtil.getRefreshInterval(), m_configUtil.getRefreshInterval(),
                m_configUtil.getRefreshIntervalTimeUnit());
    }

    /**
     * 更新服务(从meta service更新)
     * 
     * @date: 2020年4月27日 上午9:54:15
     */
    private synchronized void updateConfigServices() {
        String url = assembleMetaServiceUrl();

        HttpRequest request = new HttpRequest(url);
        int maxRetries = 2;
        Throwable exception = null;

        for (int i = 0; i < maxRetries; i++) {
            Transaction transaction = Tracer.newTransaction("Apollo.MetaService", "getConfigService");
            transaction.addData("Url", url);
            try {
                HttpResponse<List<ServiceDTO>> response = m_httpUtil.doGet(request, m_responseType);
                transaction.setStatus(Transaction.SUCCESS);
                List<ServiceDTO> services = response.getBody();
                // 没获取到服务, 记录日志
                if (services == null || services.isEmpty()) {
                    logConfigService("Empty response!");
                    continue;
                }
                setConfigServices(services);
                return;
            } catch (Throwable ex) {
                Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                transaction.setStatus(ex);
                exception = ex;
            } finally {
                transaction.complete();
            }

            try {
                m_configUtil.getOnErrorRetryIntervalTimeUnit().sleep(m_configUtil.getOnErrorRetryInterval());
            } catch (InterruptedException ex) {
                // ignore
            }
        }

        throw new ApolloConfigException(String.format("Get config services failed from %s", url), exception);
    }

    /**
     * 设置config service服务
     * 
     * @param services
     * @date: 2020年4月27日 上午10:27:38
     */
    private void setConfigServices(List<ServiceDTO> services) {
        // 设置服务信息
        m_configServices.set(services);
        // 记录日志
        logConfigServices(services);
    }

    /**
     * 生成meta service url
     * 
     * @return String
     * @date: 2020年4月27日 上午10:29:39
     */
    private String assembleMetaServiceUrl() {
        String domainName = m_configUtil.getMetaServerDomainName();
        String appId = m_configUtil.getAppId();
        String localIp = m_configUtil.getLocalIp();

        Map<String, String> queryParams = Maps.newHashMap();
        queryParams.put("appId", queryParamEscaper.escape(appId));
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        return domainName + "/services/config?" + MAP_JOINER.join(queryParams);
    }

    private void logConfigServices(List<ServiceDTO> serviceDtos) {
        for (ServiceDTO serviceDto : serviceDtos) {
            logConfigService(serviceDto.getHomepageUrl());
        }
    }

    private void logConfigService(String serviceUrl) {
        Tracer.logEvent("Apollo.Config.Services", serviceUrl);
    }
}
