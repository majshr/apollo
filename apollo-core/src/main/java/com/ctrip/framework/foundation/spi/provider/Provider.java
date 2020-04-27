package com.ctrip.framework.foundation.spi.provider;

/**
 * provider接口
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月27日 上午10:04:02
 */
public interface Provider {
    /**
     * 获取当前provider的类型
     * 
     * @return the current provider's type
     */
    public Class<? extends Provider> getType();

    /**
     * Return the property value with the given name, or {@code defaultValue} if
     * the name doesn't exist.
     *
     * @param name
     *            the property name
     * @param defaultValue
     *            the default value when name is not found or any error occurred
     * @return the property value
     */
    public String getProperty(String name, String defaultValue);

    /**
     * Initialize the provider
     */
    public void initialize();
}
