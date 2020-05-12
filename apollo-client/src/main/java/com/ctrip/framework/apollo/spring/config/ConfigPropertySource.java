package com.ctrip.framework.apollo.spring.config;

import java.util.Set;

import org.springframework.core.env.EnumerablePropertySource;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;

/**
 * 基于 Apollo Config 的 PropertySource 实现类。<br>
 * Property source wrapper for Config<br>
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigPropertySource extends EnumerablePropertySource<Config> {
	private static final String[] EMPTY_ARRAY = new String[0];

	/**
	 * 构造方法, 此处的 Apollo Config 作为 `source`
	 * @param name
	 * @param source
	 */
	ConfigPropertySource(String name, Config source) {
		super(name, source);
	}

    // 获取所有配置的属性名称
	@Override
	public String[] getPropertyNames() {
		// 从 Config 中，获得属性名集合
		Set<String> propertyNames = this.source.getPropertyNames();
		if (propertyNames.isEmpty()) {
			return EMPTY_ARRAY;
		}
		return propertyNames.toArray(new String[propertyNames.size()]);
	}

    // 根据名称获取属性值
	@Override
	public Object getProperty(String name) {
        // source就是config
		return this.source.getProperty(name, null);
	}

	/**
     * 添加 ConfigChangeListener 到 Config 中
     *
     * @param listener 监听器
     */
	public void addChangeListener(ConfigChangeListener listener) {
		this.source.addChangeListener(listener);
	}
}
