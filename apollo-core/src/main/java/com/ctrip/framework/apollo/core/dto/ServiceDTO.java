package com.ctrip.framework.apollo.core.dto;

/**
 * Config Service 服务信息, 查询namespace配置信息时需要
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年5月8日 下午2:30:24
 */
public class ServiceDTO {

    private String appName;

    private String instanceId;

    private String homepageUrl;

    public String getAppName() {
        return appName;
    }

    public String getHomepageUrl() {
        return homepageUrl;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setHomepageUrl(String homepageUrl) {
        this.homepageUrl = homepageUrl;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ServiceDTO{");
        sb.append("appName='").append(appName).append('\'');
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", homepageUrl='").append(homepageUrl).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
