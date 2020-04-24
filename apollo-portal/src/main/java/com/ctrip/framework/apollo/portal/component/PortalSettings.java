package com.ctrip.framework.apollo.portal.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.environment.PortalMetaDomainService;

/**
 * 检查env健康状态
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月23日 下午2:55:17
 */
@Component
public class PortalSettings {

    private static final Logger logger = LoggerFactory.getLogger(PortalSettings.class);
    /**
     * 环境健康检查周期
     */
    private static final int HEALTH_CHECK_INTERVAL = 10 * 1000;

    private final ApplicationContext applicationContext;
    private final PortalConfig portalConfig;
    private final PortalMetaDomainService portalMetaDomainService;

    private List<Env> allEnvs = new ArrayList<>();

    // mark env up or down
    /**
     * 标记支持环境是否可用
     */
    private Map<Env, Boolean> envStatusMark = new ConcurrentHashMap<>();

    public PortalSettings(final ApplicationContext applicationContext, final PortalConfig portalConfig,
            final PortalMetaDomainService portalMetaDomainService) {
        this.applicationContext = applicationContext;
        this.portalConfig = portalConfig;
        this.portalMetaDomainService = portalMetaDomainService;
    }

    // admin service健康检查
    @PostConstruct
    private void postConstruct() {
        // 支持的环境
        allEnvs = portalConfig.portalSupportedEnvs();

        for (Env env : allEnvs) {
            envStatusMark.put(env, true);
        }

        // 环境健康状态检查线程
        ScheduledExecutorService healthCheckService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("EnvHealthChecker", true));

        healthCheckService.scheduleWithFixedDelay(new HealthCheckTask(applicationContext), 1000, HEALTH_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS);

    }

    public List<Env> getAllEnvs() {
        return allEnvs;
    }

    public List<Env> getActiveEnvs() {
        List<Env> activeEnvs = new LinkedList<>();
        for (Env env : allEnvs) {
            if (envStatusMark.get(env)) {
                activeEnvs.add(env);
            }
        }
        return activeEnvs;
    }

    public boolean isEnvActive(Env env) {
        Boolean mark = envStatusMark.get(env);
        return mark == null ? false : mark;
    }

    /**
     * 环境健康检查任务
     * 
     * @author mengaijun
     * @Description: TODO
     * @date: 2020年4月23日 上午9:46:26
     */
    private class HealthCheckTask implements Runnable {

        /**
         * 环境下线阈值
         */
        private static final int ENV_DOWN_THRESHOLD = 2;

        /**
         * 健康检查失败数量
         */
        private Map<Env, Integer> healthCheckFailedCounter = new HashMap<>();

        private AdminServiceAPI.HealthAPI healthAPI;

        public HealthCheckTask(ApplicationContext context) {
            healthAPI = context.getBean(AdminServiceAPI.HealthAPI.class);
            for (Env env : allEnvs) {
                // 每次检查新建任务, 失败次数置零
                healthCheckFailedCounter.put(env, 0);
            }
        }

        @Override
        public void run() {
            for (Env env : allEnvs) {
                try {
                    // 环境可用
                    if (isUp(env)) {
                        // revive
                        if (!envStatusMark.get(env)) {
                            envStatusMark.put(env, true);
                            healthCheckFailedCounter.put(env, 0);
                            logger.info("Env revived because env health check success. env: {}", env);
                        }
                    } else {
                        // 环境不可用
                        logger.error(
                                "Env health check failed, maybe because of admin server down. env: {}, meta server address: {}",
                                env, portalMetaDomainService.getDomain(env));
                        handleEnvDown(env);
                    }

                } catch (Exception e) {
                    logger.error(
                            "Env health check failed, maybe because of meta server down "
                                    + "or configure wrong meta server address. env: {}, meta server address: {}",
                            env, portalMetaDomainService.getDomain(env), e);
                    handleEnvDown(env);
                }
            }

        }

        /**
         * 是否启动状态
         * 
         * @param env
         * @return boolean
         * @date: 2020年4月23日 上午9:48:12
         */
        private boolean isUp(Env env) {
            Health health = healthAPI.health(env);
            return "UP".equals(health.getStatus().getCode());
        }

        /**
         * 环境不可用操作
         * 
         * @param env
         * @date: 2020年4月23日 上午11:10:02
         */
        private void handleEnvDown(Env env) {
            // 增加健康检查失败次数
            int failedTimes = healthCheckFailedCounter.get(env);
            healthCheckFailedCounter.put(env, ++failedTimes);

            if (!envStatusMark.get(env)) {
                logger.error("Env is down. env: {}, failed times: {}, meta server address: {}", env, failedTimes,
                        portalMetaDomainService.getDomain(env));
            } else {
                // 失败次数超过下线阈值, 设置环境状态为下线
                if (failedTimes >= ENV_DOWN_THRESHOLD) {
                    envStatusMark.put(env, false);
                    logger.error(
                            "Env is down because health check failed for {} times, "
                                    + "which equals to down threshold. env: {}, meta server address: {}",
                            ENV_DOWN_THRESHOLD, env, portalMetaDomainService.getDomain(env));
                } else {
                    logger.error(
                            "Env health check failed for {} times which less than down threshold. down threshold:{}, env: {}, meta server address: {}",
                            failedTimes, ENV_DOWN_THRESHOLD, env, portalMetaDomainService.getDomain(env));
                }
            }

        }

    }
}
