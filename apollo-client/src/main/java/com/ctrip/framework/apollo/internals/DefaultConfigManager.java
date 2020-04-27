package com.ctrip.framework.apollo.internals;

import java.util.Map;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.spi.ConfigFactory;
import com.ctrip.framework.apollo.spi.ConfigFactoryManager;
import com.google.common.collect.Maps;

/**
 * ConfigFile 的管理器, 也是Config的管理器<br>
 * ConfigManager 不允许设置 Namespace 对应的 Config 对象，而是通过 ConfigFactory
 * 统一创建，虽然此时的创建是假的，直接返回了 config 方法参数。
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigManager implements ConfigManager {

    private ConfigFactoryManager m_factoryManager;

    /**
     * 当需要获得的 Config 或 ConfigFile 对象不在缓存中时，通过 ConfigFactoryManager ，获得对应的
     * ConfigFactory 对象，从而创建 Config 或 ConfigFile 对象。
     */
    private Map<String, Config> m_configs = Maps.newConcurrentMap();
    private Map<String, ConfigFile> m_configFiles = Maps.newConcurrentMap();

    public DefaultConfigManager() {
        m_factoryManager = ApolloInjector.getInstance(ConfigFactoryManager.class);
    }

    @Override
    public Config getConfig(String namespace) {
        Config config = m_configs.get(namespace);

        if (config == null) {
            synchronized (this) {
                config = m_configs.get(namespace);

                // 不存在, 进行创建
                if (config == null) {
                    ConfigFactory factory = m_factoryManager.getFactory(namespace);

                    config = factory.create(namespace);
                    m_configs.put(namespace, config);
                }
            }
        }

        return config;
    }

    @Override
    public ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        String namespaceFileName = String.format("%s.%s", namespace, configFileFormat.getValue());
        ConfigFile configFile = m_configFiles.get(namespaceFileName);

        if (configFile == null) {
            synchronized (this) {
                configFile = m_configFiles.get(namespaceFileName);

                if (configFile == null) {
                    ConfigFactory factory = m_factoryManager.getFactory(namespaceFileName);

                    configFile = factory.createConfigFile(namespaceFileName, configFileFormat);
                    m_configFiles.put(namespaceFileName, configFile);
                }
            }
        }

        return configFile;
    }
}
