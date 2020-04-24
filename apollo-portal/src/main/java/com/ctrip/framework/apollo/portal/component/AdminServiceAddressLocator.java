package com.ctrip.framework.apollo.portal.component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.ctrip.framework.apollo.core.dto.ServiceDTO;
import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.environment.PortalMetaDomainService;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.collect.Lists;

/**
 * admin service服务地址加载器
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月23日 上午9:57:40
 */
@Component
public class AdminServiceAddressLocator {

    /**
     * 刷新正常, 下次执行周期
     */
    private static final long NORMAL_REFRESH_INTERVAL = 5 * 60 * 1000;
    /**
     * 刷新异常, 下次执行周期
     */
    private static final long OFFLINE_REFRESH_INTERVAL = 10 * 1000;
    /**
     * 刷新重试次数
     */
    private static final int RETRY_TIMES = 3;
    private static final String ADMIN_SERVICE_URL_PATH = "/services/admin";
    private static final Logger logger = LoggerFactory.getLogger(AdminServiceAddressLocator.class);

    private ScheduledExecutorService refreshServiceAddressService;
    private RestTemplate restTemplate;
    private List<Env> allEnvs;

    /**
     * 环境对应的服务列表缓存(env对应的AdminService地址列表)
     */
    private Map<Env, List<ServiceDTO>> cache = new ConcurrentHashMap<>();

    private final PortalSettings portalSettings;
    private final RestTemplateFactory restTemplateFactory;
    private final PortalMetaDomainService portalMetaDomainService;

    public AdminServiceAddressLocator(final HttpMessageConverters httpMessageConverters,
            final PortalSettings portalSettings, final RestTemplateFactory restTemplateFactory,
            final PortalMetaDomainService portalMetaDomainService) {
        this.portalSettings = portalSettings;
        this.restTemplateFactory = restTemplateFactory;
        this.portalMetaDomainService = portalMetaDomainService;
    }

    // 依赖注入之后执行
    @PostConstruct
    public void init() {
        // 获取所有ENV
        allEnvs = portalSettings.getAllEnvs();

        // init restTemplate
        restTemplate = restTemplateFactory.getObject();

        refreshServiceAddressService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("ServiceLocator", true));

        refreshServiceAddressService.schedule(new RefreshAdminServerAddressTask(), 1, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取ENV对应AdminService服务列表(列表会随机换下顺序)
     * 
     * @param env
     * @return List<ServiceDTO>
     * @date: 2020年4月23日 上午10:48:41
     */
    public List<ServiceDTO> getServiceList(Env env) {
        List<ServiceDTO> services = cache.get(env);
        if (CollectionUtils.isEmpty(services)) {
            return Collections.emptyList();
        }
        List<ServiceDTO> randomConfigServices = Lists.newArrayList(services);
        // 默认随机源对列表进行置换，所有置换发生的可能性都是大致相等的
        Collections.shuffle(randomConfigServices);
        return randomConfigServices;
    }

    /**
     * 定时刷新AdminServerAddress任务
     * 
     * @author mengaijun
     * @Description: TODO
     * @date: 2020年4月23日 上午10:13:35
     */
    private class RefreshAdminServerAddressTask implements Runnable {

        @Override
        public void run() {
            boolean refreshSuccess = true;
            // refresh fail if get any env address fail
            for (Env env : allEnvs) {
                boolean currentEnvRefreshResult = refreshServerAddressCache(env);
                refreshSuccess = refreshSuccess && currentEnvRefreshResult;
            }

            if (refreshSuccess) {
                refreshServiceAddressService.schedule(new RefreshAdminServerAddressTask(), NORMAL_REFRESH_INTERVAL,
                        TimeUnit.MILLISECONDS);
            } else {
                refreshServiceAddressService.schedule(new RefreshAdminServerAddressTask(), OFFLINE_REFRESH_INTERVAL,
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 刷新环境服务地址
     * 
     * @param env
     * @return boolean
     * @date: 2020年4月23日 上午10:14:10
     */
    private boolean refreshServerAddressCache(Env env) {

        for (int i = 0; i < RETRY_TIMES; i++) {

            try {
                ServiceDTO[] services = getAdminServerAddress(env);
                if (services == null || services.length == 0) {
                    continue;
                }
                cache.put(env, Arrays.asList(services));
                return true;
            } catch (Throwable e) {
                logger.error(String.format(
                        "Get admin server address from meta server failed. env: %s, meta server address:%s", env,
                        portalMetaDomainService.getDomain(env)), e);
                Tracer.logError(String.format(
                        "Get admin server address from meta server failed. env: %s, meta server address:%s", env,
                        portalMetaDomainService.getDomain(env)), e);
            }
        }
        return false;
    }

    /**
     * 获取admin服务地址(调eureka接口, 从eureka获取)
     * 
     * @param env
     * @return ServiceDTO[]
     * @date: 2020年4月23日 上午10:16:28
     */
    private ServiceDTO[] getAdminServerAddress(Env env) {
        String domainName = portalMetaDomainService.getDomain(env);
        // 这是查的eureka的接口
        if (Objects.equals(domainName, PortalMetaDomainService.DEFAULT_META_URL)) {
            return null;
        }
        String url = domainName + ADMIN_SERVICE_URL_PATH;
        return restTemplate.getForObject(url, ServiceDTO[].class);
    }

}
