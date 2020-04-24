package com.ctrip.framework.apollo.common.config;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.core.utils.ApolloThreadFactory;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * 可刷新配置
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月22日 下午2:46:54
 */
public abstract class RefreshableConfig {

    private static final Logger logger = LoggerFactory.getLogger(RefreshableConfig.class);

    /**
     * 分隔符: ","
     */
    private static final String LIST_SEPARATOR = ",";

    /**
     * TimeUnit: second, 定时读取配置时间间隔
     */
    private static final int CONFIG_REFRESH_INTERVAL = 60;

    /**
     * "," 的
     */
    protected Splitter splitter = Splitter.on(LIST_SEPARATOR).omitEmptyStrings().trimResults();

    /**
     * spring管理的ConfigurableEnvironment
     */
    @Autowired
    private ConfigurableEnvironment environment;

    /**
     * PortalDBPropertySource(没有注入, setup方法设置的)
     */
    private List<RefreshablePropertySource> propertySources;

    /**
     * register refreshable property source. Notice: The front property source
     * has higher priority.<br>
     * 
     * 可刷新的配置资源信息
     */
    protected abstract List<RefreshablePropertySource> getRefreshablePropertySources();

    @PostConstruct
    public void setup() {
        // 获取所有可刷新资源
        propertySources = getRefreshablePropertySources();
        if (CollectionUtils.isEmpty(propertySources)) {
            throw new IllegalStateException("Property sources can not be empty.");
        }

        // add property source to environment
        for (RefreshablePropertySource propertySource : propertySources) {
            // 刷新配置, 加到环境变量配置中
            propertySource.refresh();
            // 将配置加入到spring管理(addLast方法, 如果存在, 先移除, 再添加)
            environment.getPropertySources().addLast(propertySource);
        }

        // task to update configs
        // 定时任务定时刷新配置, 刷新的值就是已经加入到spring环境管理environment中的propertySource了
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("ConfigRefresher", true));
        executorService.scheduleWithFixedDelay(() -> {
            try {
                propertySources.forEach(RefreshablePropertySource::refresh);
            } catch (Throwable t) {
                logger.error("Refresh configs failed.", t);
                Tracer.logError("Refresh configs failed.", t);
            }
        }, CONFIG_REFRESH_INTERVAL, CONFIG_REFRESH_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * 获取int类型配置信息
     * 
     * @param key
     * @param defaultValue
     * @return int
     * @date: 2020年4月22日 下午3:22:59
     */
    public int getIntProperty(String key, int defaultValue) {
        try {
            String value = getValue(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (Throwable e) {
            Tracer.logError("Get int property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 获取boolean类型配置信息
     * 
     * @param key
     * @param defaultValue
     * @return boolean
     * @date: 2020年4月22日 下午3:22:47
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        try {
            String value = getValue(key);
            return value == null ? defaultValue : "true".equals(value);
        } catch (Throwable e) {
            Tracer.logError("Get boolean property failed.", e);
            return defaultValue;
        }
    }

    /**
     * 获取配置信息, 然后配置信息按"," 分隔
     * 
     * @param key
     * @param defaultValue
     * @return String[]
     * @date: 2020年4月22日 下午3:22:21
     */
    public String[] getArrayProperty(String key, String[] defaultValue) {
        try {
            String value = getValue(key);
            return Strings.isNullOrEmpty(value) ? defaultValue : value.split(LIST_SEPARATOR);
        } catch (Throwable e) {
            Tracer.logError("Get array property failed.", e);
            return defaultValue;
        }
    }

    /**
     * environment获取配置, 没有返回defaultValue
     * 
     * @param key
     * @param defaultValue
     * @return String
     * @date: 2020年4月22日 下午3:17:29
     */
    public String getValue(String key, String defaultValue) {
        try {
            return environment.getProperty(key, defaultValue);
        } catch (Throwable e) {
            Tracer.logError("Get value failed.", e);
            return defaultValue;
        }
    }

    /**
     * environment中获取配置
     * 
     * @param key
     * @return String
     * @date: 2020年4月22日 下午3:17:11
     */
    public String getValue(String key) {
        return environment.getProperty(key);
    }

}
