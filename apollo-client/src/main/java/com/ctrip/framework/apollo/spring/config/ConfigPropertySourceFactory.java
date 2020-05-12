package com.ctrip.framework.apollo.spring.config;

import java.util.List;

import com.ctrip.framework.apollo.Config;
import com.google.common.collect.Lists;

/**
 * ConfigPropertySource(apollo实现的属性源)工厂类
 * 
 * @author maj
 *
 */
public class ConfigPropertySourceFactory {

	/**
     * ConfigPropertySource 数组(每一个namespace, 对应一个ConfigPropertySource对象)
     */
	private final List<ConfigPropertySource> configPropertySources = Lists.newLinkedList();

	/**
     * 创建 ConfigPropertySource 对象
     * 
     * @param name
     *            属性源名称
     * @param source
     *            存储配置信息的容器
     * @return
     */
	public ConfigPropertySource getConfigPropertySource(String name, Config source) {
		// 创建 ConfigPropertySource 对象
		ConfigPropertySource configPropertySource = new ConfigPropertySource(name, source);

		// 添加到数组
		configPropertySources.add(configPropertySource);

		return configPropertySource;
	}

    /**
     * 获取所有属性源
     * 
     * @return List<ConfigPropertySource>
     * @date: 2020年4月29日 下午2:33:27
     */
	public List<ConfigPropertySource> getAllConfigPropertySources() {
		return Lists.newLinkedList(configPropertySources);
	}
}
