package com.ctrip.framework.apollo.core.enums;

import com.ctrip.framework.apollo.core.utils.StringUtils;

/**
 * 配置文件类型枚举
 * @author Jason Song(song_s@ctrip.com)
 */
public enum ConfigFileFormat {
    Properties("properties"), XML("xml"), JSON("json"), YML("yml"), YAML("yaml"), TXT("txt");

    private String value;

    ConfigFileFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConfigFileFormat fromString(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("value can not be empty");
        }
        switch (value.toLowerCase()) {
        case "properties":
            return Properties;
        case "xml":
            return XML;
        case "json":
            return JSON;
        case "yml":
            return YML;
        case "yaml":
            return YAML;
        case "txt":
            return TXT;
        }
        throw new IllegalArgumentException(value + " can not map enum");
    }

    /**
     * value是否为符合的文件类型
     * 
     * @param value
     * @return boolean
     * @date: 2020年5月8日 下午5:10:10
     */
    public static boolean isValidFormat(String value) {
        try {
            fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 类型为YAML, YML 返回true; 否则返回false
     * 
     * @param format
     * @return boolean
     * @date: 2020年5月8日 下午5:09:23
     */
    public static boolean isPropertiesCompatible(ConfigFileFormat format) {
        return format == YAML || format == YML;
    }
}
