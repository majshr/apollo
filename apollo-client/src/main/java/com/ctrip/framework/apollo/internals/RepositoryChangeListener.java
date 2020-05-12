package com.ctrip.framework.apollo.internals;

import java.util.Properties;

/**
 * 监听配置Repository变化
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RepositoryChangeListener {
    /**
     * Invoked when config repository changes.
     * 
     * @param namespace
     *            the namespace of this repository change(改变的namespace, 改变的文件)
     * @param newProperties
     *            the properties after change(改变后的配置)
     */
    public void onRepositoryChange(String namespace, Properties newProperties);
}
