package com.ctrip.framework.apollo.internals;

import java.util.Properties;

import com.ctrip.framework.apollo.enums.ConfigSourceType;

/**
 * 作为 Client 的 Repository ( 类似 DAO ) ，读取配置。
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigRepository {
    /**
     * 读取配置<br>
     * Get the config from this repository.
     * 
     * @return config
     */
    public Properties getConfig();

    /**
     * 设置上游的 Repository 。主要用于 LocalFileConfigRepository ，从 Config Service 读取配置，缓存在本地文件。<br>
     * Set the fallback repo for this repository.
     * 
     * @param upstreamConfigRepository
     *            the upstream repo
     */
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository);

    /**
     * 监听 Repository 的配置的变化。<br>
     * Add change listener.
     * 
     * @param listener
     *            the listener to observe the changes
     */
    public void addChangeListener(RepositoryChangeListener listener);

    /**
     * Remove change listener.
     * 
     * @param listener
     *            the listener to remove
     */
    public void removeChangeListener(RepositoryChangeListener listener);

    /**
     * Return the config's source type, i.e. where is the config loaded from
     *
     * @return the config's source type
     */
    public ConfigSourceType getSourceType();
}
