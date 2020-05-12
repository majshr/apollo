package com.ctrip.framework.apollo;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;

/**
 * 配置改变监听器
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigChangeListener {
    /**
     * Invoked when there is any config change for the namespace.
     * 
     * @param changeEvent
     *            the event for this change
     */
    public void onChange(ConfigChangeEvent changeEvent);
}
