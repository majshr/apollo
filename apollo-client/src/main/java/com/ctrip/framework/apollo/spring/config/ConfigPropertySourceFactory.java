package com.ctrip.framework.apollo.spring.config;

import java.util.List;

import com.ctrip.framework.apollo.Config;
import com.google.common.collect.Lists;

/**
 * ConfigPropertySource工厂类
 * @author maj
 *
 */
public class ConfigPropertySourceFactory {

	/**
     * ConfigPropertySource 数组
     */
	private final List<ConfigPropertySource> configPropertySources = Lists.newLinkedList();

	/**
	 *  创建 ConfigPropertySource 对象
	 * @param name
	 * @param source
	 * @return
	 */
	public ConfigPropertySource getConfigPropertySource(String name, Config source) {
		// 创建 ConfigPropertySource 对象
		ConfigPropertySource configPropertySource = new ConfigPropertySource(name, source);

		// 添加到数组
		configPropertySources.add(configPropertySource);

		return configPropertySource;
	}

	public List<ConfigPropertySource> getAllConfigPropertySources() {
		return Lists.newLinkedList(configPropertySources);
	}
}
