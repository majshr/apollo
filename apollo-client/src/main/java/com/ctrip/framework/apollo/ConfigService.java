package com.ctrip.framework.apollo;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.internals.ConfigManager;
import com.ctrip.framework.apollo.spi.ConfigFactory;
import com.ctrip.framework.apollo.spi.ConfigRegistry;

/**
 * Entry point for client config use
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigService {
    private static final ConfigService s_instance = new ConfigService();

    /**
     * config管理器
     */
    private volatile ConfigManager m_configManager;
    private volatile ConfigRegistry m_configRegistry;

    private ConfigManager getManager() {
        // 若 ConfigManager 未初始化，进行获得
        if (m_configManager == null) {
            synchronized (this) {
                if (m_configManager == null) {
                    m_configManager = ApolloInjector.getInstance(ConfigManager.class);
                }
            }
        }

        return m_configManager;
    }

    /**
     * 
     * @return ConfigRegistry
     * @date: 2020年4月27日 下午4:27:49
     */
    private ConfigRegistry getRegistry() {
        if (m_configRegistry == null) {
            synchronized (this) {
                if (m_configRegistry == null) {
                    // DefaultConfigRegistry
                    m_configRegistry = ApolloInjector.getInstance(ConfigRegistry.class);
                }
            }
        }

        return m_configRegistry;
    }

    /**
     * Get Application's config instance.
     *
     * @return config instance
     */
    public static Config getAppConfig() {
        return getConfig(ConfigConsts.NAMESPACE_APPLICATION);
    }

    /**
     * Get the config instance for the namespace.<br>
     * 获取namespace对应的config对象
     * 
     * @param namespace
     *            the namespace of the config
     * @return config instance
     */
    public static Config getConfig(String namespace) {
        return s_instance.getManager().getConfig(namespace);
    }

    /**
     * 获得 Namespace 对应的 ConfigFile 对象。
     * 
     * @param namespace
     * @param configFileFormat
     * @return ConfigFile
     * @date: 2020年4月27日 下午4:23:26
     */
    public static ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        return s_instance.getManager().getConfigFile(namespace, configFileFormat);
    }

    /**
     * 设置 Config 对象
     * 
     * @param config
     * @date: 2020年4月27日 下午4:26:06
     */
    static void setConfig(Config config) {
        setConfig(ConfigConsts.NAMESPACE_APPLICATION, config);
    }

    /**
     * Manually set the config for the namespace specified, use with
     * caution.<br>
     * 设置 Config 对象
     * 
     * @param namespace
     *            the namespace
     * @param config
     *            the config instance
     */
    static void setConfig(String namespace, final Config config) {
        s_instance.getRegistry().register(namespace, new ConfigFactory() {
            @Override
            public Config create(String namespace) {
                return config;
            }

            @Override
            public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
                return null;
            }

        });
    }

    /**
     * 设置 ConfigFactory 对象
     * 
     * @param factory
     * @date: 2020年4月27日 下午4:29:33
     */
    static void setConfigFactory(ConfigFactory factory) {
        setConfigFactory(ConfigConsts.NAMESPACE_APPLICATION, factory);
    }

    /**
     * Manually set the config factory for the namespace specified, use with
     * caution.
     *
     * @param namespace
     *            the namespace
     * @param factory
     *            the factory instance
     */
    static void setConfigFactory(String namespace, ConfigFactory factory) {
        s_instance.getRegistry().register(namespace, factory);
    }

    // for test only
    static void reset() {
        synchronized (s_instance) {
            s_instance.m_configManager = null;
            s_instance.m_configRegistry = null;
        }
    }
}
