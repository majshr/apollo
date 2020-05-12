package com.ctrip.framework.apollo.internals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ClassLoaderUtil;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

/**
 * 本地文件配置 Repository 实现类
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class LocalFileConfigRepository extends AbstractConfigRepository implements RepositoryChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(LocalFileConfigRepository.class);
    /**
     * 配置文件目录
     */
    private static final String CONFIG_DIR = "/config-cache";
    /**
     * Namespace 名字
     */
    private final String m_namespace;
    /**
     * 本地缓存配置文件目录
     */
    private File m_baseDir;

    private final ConfigUtil m_configUtil;
    /**
     * 配置文件 Properties
     */
    private volatile Properties m_fileProperties;
    /**
     * 上游的 ConfigRepository 对象。一般情况下，使用 RemoteConfigRepository 对象，读取远程 Config
     * Service 的配置
     */
    private volatile ConfigRepository m_upstream;

    private volatile ConfigSourceType m_sourceType = ConfigSourceType.LOCAL;

    /**
     * Constructor.
     *
     * @param namespace
     *            the namespace
     */
    public LocalFileConfigRepository(String namespace) {
        this(namespace, null);
    }

    public LocalFileConfigRepository(String namespace, ConfigRepository upstream) {
        m_namespace = namespace;
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        this.setLocalCacheDir(findLocalCacheDir(), false);
        // 初始拉取 Config Service 的配置，并监听配置变化
        this.setUpstreamRepository(upstream);
        this.trySync();
    }

    /**
     * 设置 m_baseDir 字段
     * 
     * @param baseDir
     * @param syncImmediately
     * @date: 2020年4月28日 下午4:49:17
     */
    void setLocalCacheDir(File baseDir, boolean syncImmediately) {
        m_baseDir = baseDir;
        // 获得本地缓存配置文件的目录
        this.checkLocalConfigCacheDir(m_baseDir);
        if (syncImmediately) {
            // 若需要立即同步，则进行同步
            this.trySync();
        }
    }

    /**
     * 获得本地缓存目录
     * 
     * @return File
     * @date: 2020年4月28日 下午4:38:12
     */
    private File findLocalCacheDir() {
        try {
            // 获得默认缓存配置目录
            String defaultCacheDir = m_configUtil.getDefaultLocalCacheDir();
            Path path = Paths.get(defaultCacheDir);
            // 有权限，使用 /opt/data/${appId}/ + config-cache 目录。
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // 返回该目录下的 CONFIG_DIR 目录
            if (Files.exists(path) && Files.isWritable(path)) {
                return new File(defaultCacheDir, CONFIG_DIR);
            }
        } catch (Throwable ex) {
            // ignore
        }

        // 无权限，使用 ClassPath/ + config-cache 目录。这个目录，应用程序下，肯定是有权限的
        // 若失败，使用 ClassPath 下的 CONFIG_DIR 目录
        return new File(ClassLoaderUtil.getClassPath(), CONFIG_DIR);
    }

    @Override
    public Properties getConfig() {
        if (m_fileProperties == null) {
            sync();
        }
        Properties result = propertiesFactory.getPropertiesInstance();
        result.putAll(m_fileProperties);
        return result;
    }

    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
        if (upstreamConfigRepository == null) {
            return;
        }
        // clear previous listener
        // 从老的 `m_upstream` 移除自己
        if (m_upstream != null) {
            m_upstream.removeChangeListener(this);
        }
        // 设置新的 `m_upstream`
        m_upstream = upstreamConfigRepository;
        // 从 `m_upstream` 拉取配置
        trySyncFromUpstream();
        // 向新的 `m_upstream` 注册自己
        upstreamConfigRepository.addChangeListener(this);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return m_sourceType;
    }

    @Override
    public void onRepositoryChange(String namespace, Properties newProperties) {
        // 忽略，若未变更
        if (newProperties.equals(m_fileProperties)) {
            return;
        }
        // 读取新的 Properties 对象
        Properties newFileProperties = propertiesFactory.getPropertiesInstance();
        newFileProperties.putAll(newProperties);
        // 更新到 `m_fileProperties` 中
        updateFileProperties(newFileProperties, m_upstream.getSourceType());
        // 发布 Repository 的配置发生变化，触发对应的监听器们
        this.fireRepositoryChange(namespace, newProperties);
    }

    /**
     * 非本地模式的情况下，LocalFileConfigRepository 在初始化时，会首先从远程 Config Service 同步( 加载
     * )配置。若同步(加载)失败，则读取本地缓存的配置文件。<br>
     * 
     * 在本地模式的情况下，则只读取本地缓存的配置文件。当然，严格来说，也不一定是缓存，可以是开发者，手动创建的配置文件。
     */
    @Override
    protected void sync() {
        // sync with upstream immediately
        // 从 `m_upstream` 上游同步配置
        boolean syncFromUpstreamResultSuccess = trySyncFromUpstream();
        // 若成功，则直接返回
        if (syncFromUpstreamResultSuccess) {
            return;
        }

        // 若失败，读取本地缓存的配置文件
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncLocalConfig");
        Throwable exception = null;
        try {
            transaction.addData("Basedir", m_baseDir.getAbsolutePath());
            // 加载本地缓存的配置文件
            m_fileProperties = this.loadFromLocalCacheFile(m_baseDir, m_namespace);
            m_sourceType = ConfigSourceType.LOCAL;
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
            transaction.setStatus(ex);
            exception = ex;
            // ignore
        } finally {
            transaction.complete();
        }

        // 若未读取到缓存的配置文件，抛出异常
        if (m_fileProperties == null) {
            m_sourceType = ConfigSourceType.NONE;
            throw new ApolloConfigException("Load config from local config failed!", exception);
        }
    }

    /**
     * 从 m_upstream 拉取初始配置，并返回是否拉取成功。
     * 
     * @return boolean
     * @date: 2020年4月28日 下午5:18:11
     */
    private boolean trySyncFromUpstream() {
        if (m_upstream == null) {
            return false;
        }
        try {
            // 从 `m_upstream` 拉取配置 Properties, 更新到 `m_fileProperties` 中
            updateFileProperties(m_upstream.getConfig(), m_upstream.getSourceType());
            // 返回同步成功
            return true;
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger.warn("Sync config from upstream repository {} failed, reason: {}", m_upstream.getClass(),
                    ExceptionUtil.getDetailMessage(ex));
        }
        // 返回同步失败
        return false;
    }

    /**
     * 若 Properties 发生变化，向缓存配置文件，写入 Properties
     * 
     * @param newProperties
     * @param sourceType
     *            void
     * @date: 2020年4月28日 下午5:13:28
     */
    private synchronized void updateFileProperties(Properties newProperties, ConfigSourceType sourceType) {
        this.m_sourceType = sourceType;
        // 忽略，若未变更
        if (newProperties.equals(m_fileProperties)) {
            return;
        }
        // 设置新的 Properties 到 `m_fileProperties` 中。
        this.m_fileProperties = newProperties;
        // 持久化到本地缓存配置文件
        persistLocalCacheFile(m_baseDir, m_namespace);
    }

    /**
     * 从缓存配置文件
     * 
     * @param baseDir
     * @param namespace
     * @return
     * @throws IOException
     *             Properties
     * @date: 2020年4月28日 下午4:55:27
     */
    private Properties loadFromLocalCacheFile(File baseDir, String namespace) throws IOException {
        Preconditions.checkNotNull(baseDir, "Basedir cannot be null");
        // 拼接本地缓存的配置文件 File 对象
        File file = assembleLocalCacheFile(baseDir, namespace);
        Properties properties = null;

        if (file.isFile() && file.canRead()) {
            InputStream in = null;

            try {
                in = new FileInputStream(file);
                properties = propertiesFactory.getPropertiesInstance();
                properties.load(in);
                logger.debug("Loading local config file {} successfully!", file.getAbsolutePath());
            } catch (IOException ex) {
                Tracer.logError(ex);
                throw new ApolloConfigException(
                        String.format("Loading config from local cache file %s failed", file.getAbsolutePath()), ex);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                    // ignore
                }
            }
        } else {
            throw new ApolloConfigException(
                    String.format("Cannot read from local cache file %s", file.getAbsolutePath()));
        }

        return properties;
    }

    /**
     * 向缓存文件写入配置信息
     * 
     * @param baseDir
     * @param namespace
     * @date: 2020年4月28日 下午5:00:09
     */
    void persistLocalCacheFile(File baseDir, String namespace) {
        if (baseDir == null) {
            return;
        }
        File file = assembleLocalCacheFile(baseDir, namespace);

        OutputStream out = null;

        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "persistLocalConfigFile");
        transaction.addData("LocalConfigFile", file.getAbsolutePath());
        try {
            out = new FileOutputStream(file);
            m_fileProperties.store(out, "Persisted by DefaultConfig");
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception = new ApolloConfigException(
                    String.format("Persist local cache file %s failed", file.getAbsolutePath()), ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn("Persist local cache file {} failed, reason: {}.", file.getAbsolutePath(),
                    ExceptionUtil.getDetailMessage(ex));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
            transaction.complete();
        }
    }

    /**
     * 校验本地缓存配置目录是否存在。若不存在，则进行创建。
     * 
     * @param baseDir
     * @date: 2020年4月28日 下午4:50:35
     */
    private void checkLocalConfigCacheDir(File baseDir) {
        if (baseDir.exists()) {
            return;
        }
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "createLocalConfigDir");
        transaction.addData("BaseDir", baseDir.getAbsolutePath());
        try {
            Files.createDirectory(baseDir.toPath());
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception = new ApolloConfigException(
                    String.format("Create local config directory %s failed", baseDir.getAbsolutePath()), ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn(
                    "Unable to create local config cache directory {}, reason: {}. Will not able to cache config file.",
                    baseDir.getAbsolutePath(), ExceptionUtil.getDetailMessage(ex));
        } finally {
            transaction.complete();
        }
    }

    /**
     * 生成完整的缓存配置文件到底路径<br>
     * ${baseDir}/config-cache/ + ${appId}+${cluster} + ${namespace}.properties
     * 
     * @param baseDir
     * @param namespace
     * @return File
     * @date: 2020年4月28日 下午4:52:40
     */
    File assembleLocalCacheFile(File baseDir, String namespace) {
        String fileName = String.format("%s.properties", Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
                .join(m_configUtil.getAppId(), m_configUtil.getCluster(), namespace));
        return new File(baseDir, fileName);
    }
}
