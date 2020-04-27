package com.ctrip.framework.foundation.spi;

import com.ctrip.framework.foundation.spi.provider.Provider;

/**
 * provider管理器
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月27日 上午9:59:33
 */
public interface ProviderManager {
    public String getProperty(String name, String defaultValue);

    public <T extends Provider> T provider(Class<T> clazz);
}
