package com.ctrip.framework.apollo.configservice.wrapper;

import java.util.Map;

/**
 * map包装器, key转为小写, 进行操作
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class CaseInsensitiveMapWrapper<T> {
	private final Map<String, T> delegate;

	public CaseInsensitiveMapWrapper(Map<String, T> delegate) {
		this.delegate = delegate;
	}

	public T get(String key) {
		return delegate.get(key.toLowerCase());
	}

	public T put(String key, T value) {
		return delegate.put(key.toLowerCase(), value);
	}

	public T remove(String key) {
		return delegate.remove(key.toLowerCase());
	}
}
