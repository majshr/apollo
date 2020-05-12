package com.ctrip.framework.apollo.spi;

/**
 * 配置工厂管理类
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigFactoryManager {
    /**
     * Get the config factory for the namespace.
     *
     * @param namespace
     *            the namespace
     * @return the config factory for this namespace
     */
    public ConfigFactory getFactory(String namespace);
}
