package com.ctrip.framework.apollo.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.PropertiesCompatibleConfigFile;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.ctrip.framework.apollo.internals.ConfigRepository;
import com.ctrip.framework.apollo.internals.DefaultConfig;
import com.ctrip.framework.apollo.internals.JsonConfigFile;
import com.ctrip.framework.apollo.internals.LocalFileConfigRepository;
import com.ctrip.framework.apollo.internals.PropertiesCompatibleFileConfigRepository;
import com.ctrip.framework.apollo.internals.PropertiesConfigFile;
import com.ctrip.framework.apollo.internals.RemoteConfigRepository;
import com.ctrip.framework.apollo.internals.TxtConfigFile;
import com.ctrip.framework.apollo.internals.XmlConfigFile;
import com.ctrip.framework.apollo.internals.YamlConfigFile;
import com.ctrip.framework.apollo.internals.YmlConfigFile;
import com.ctrip.framework.apollo.util.ConfigUtil;

/**
 * 默认配置工厂实现
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactory implements ConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigFactory.class);
    private ConfigUtil m_configUtil;

    public DefaultConfigFactory() {
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    }

    @Override
    public Config create(String namespace) {
        ConfigFileFormat format = determineFileFormat(namespace);
        if (ConfigFileFormat.isPropertiesCompatible(format)) {
            return new DefaultConfig(namespace, createPropertiesCompatibleFileConfigRepository(namespace, format));
        }
        // 创建 ConfigRepository 对象
        // 创建 DefaultConfig 对象
        return new DefaultConfig(namespace, createLocalConfigRepository(namespace));
    }

    @Override
    public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        // 创建 ConfigRepository 对象
        ConfigRepository configRepository = createLocalConfigRepository(namespace);
        // 创建对应的 ConfigFile 对象
        switch (configFileFormat) {
        case Properties:
            return new PropertiesConfigFile(namespace, configRepository);
        case XML:
            return new XmlConfigFile(namespace, configRepository);
        case JSON:
            return new JsonConfigFile(namespace, configRepository);
        case YAML:
            return new YamlConfigFile(namespace, configRepository);
        case YML:
            return new YmlConfigFile(namespace, configRepository);
        case TXT:
            return new TxtConfigFile(namespace, configRepository);
        }

        return null;
    }

    /**
     * 创建 LocalConfigRepository 对象
     * 
     * @param namespace
     * @return LocalFileConfigRepository
     * @date: 2020年4月27日 下午5:01:06
     */
    LocalFileConfigRepository createLocalConfigRepository(String namespace) {
        // 本地模式，使用 LocalFileConfigRepository 对象
        if (m_configUtil.isInLocalMode()) {
            logger.warn("==== Apollo is in local mode! Won't pull configs from remote server for namespace {} ! ====",
                    namespace);
            return new LocalFileConfigRepository(namespace);
        }

        // 非本地模式，使用 LocalFileConfigRepository + RemoteConfigRepository 对象
        return new LocalFileConfigRepository(namespace, createRemoteConfigRepository(namespace));
    }

    /**
     * 创建namespace对应的RemoteConfigRepository对象
     * 
     * @param namespace
     * @return RemoteConfigRepository
     * @date: 2020年5月8日 下午5:14:18
     */
    RemoteConfigRepository createRemoteConfigRepository(String namespace) {
        return new RemoteConfigRepository(namespace);
    }

    PropertiesCompatibleFileConfigRepository createPropertiesCompatibleFileConfigRepository(String namespace,
            ConfigFileFormat format) {
        String actualNamespaceName = trimNamespaceFormat(namespace, format);
        PropertiesCompatibleConfigFile configFile = (PropertiesCompatibleConfigFile) ConfigService
                .getConfigFile(actualNamespaceName, format);

        return new PropertiesCompatibleFileConfigRepository(configFile);
    }

    /**
     * 根据名称判断配置文件类型 <br>
     * for namespaces whose format are not properties, the file extension must
     * be present, e.g. application.yaml
     * 
     * @param namespaceName
     * @return ConfigFileFormat
     * @date: 2020年5月8日 下午5:08:07
     */

    ConfigFileFormat determineFileFormat(String namespaceName) {
        String lowerCase = namespaceName.toLowerCase();
        for (ConfigFileFormat format : ConfigFileFormat.values()) {
            if (lowerCase.endsWith("." + format.getValue())) {
                return format;
            }
        }

        return ConfigFileFormat.Properties;
    }

    String trimNamespaceFormat(String namespaceName, ConfigFileFormat format) {
        String extension = "." + format.getValue();
        if (!namespaceName.toLowerCase().endsWith(extension)) {
            return namespaceName;
        }

        return namespaceName.substring(0, namespaceName.length() - extension.length());
    }

}
