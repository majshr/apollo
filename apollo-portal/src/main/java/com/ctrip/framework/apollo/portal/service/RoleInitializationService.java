package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.entity.App;

/**
 * 角色初始化相关操作
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午11:23:54
 */
public interface RoleInitializationService {

    /**
     * 初始化 App 级的 Role
     * 
     * @param app
     *            void
     * @date: 2020年4月23日 下午5:51:59
     */
    public void initAppRoles(App app);

    /**
     * 初始化 Namespace 级的 Role
     * 
     * @param appId
     * @param namespaceName
     * @param operator
     *            void
     * @date: 2020年4月24日 上午11:24:39
     */
    public void initNamespaceRoles(String appId, String namespaceName, String operator);

    public void initNamespaceEnvRoles(String appId, String namespaceName, String operator);

    public void initNamespaceSpecificEnvRoles(String appId, String namespaceName, String env, String operator);

    public void initCreateAppRole();

    public void initManageAppMasterRole(String appId, String operator);

}
