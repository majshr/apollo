package com.ctrip.framework.apollo.common.config;

import java.util.Map;

import org.springframework.core.env.MapPropertySource;

/**
 * 可刷新的配置抽象类
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月22日 下午3:00:43
 */
public abstract class RefreshablePropertySource extends MapPropertySource {

    public RefreshablePropertySource(String name, Map<String, Object> source) {
        super(name, source);
    }

    @Override
    public Object getProperty(String name) {
        return this.source.get(name);
    }

    /**
     * 刷新属性配置信息<br>
     * refresh property
     */
    protected abstract void refresh();

}
