package com.ctrip.framework.apollo.spring.property;

/**
 * SpringValue 定义
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年5月7日 下午4:39:59
 */
public class SpringValueDefinition {

    /**
     * KEY
     *
     * 即在 Config 中的属性 KEY 。
     */
    private final String key;

    /**
     * 占位符
     */
    private final String placeholder;

    /**
     * 属性名
     */
    private final String propertyName;

    public SpringValueDefinition(String key, String placeholder, String propertyName) {
        this.key = key;
        this.placeholder = placeholder;
        this.propertyName = propertyName;
    }

    public String getKey() {
        return key;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
