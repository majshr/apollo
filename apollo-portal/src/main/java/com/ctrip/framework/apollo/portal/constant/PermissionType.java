package com.ctrip.framework.apollo.portal.constant;

/**
 * 权限类型, 分成 App 和 Namespace 两种级别的权限类型。
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午11:00:37
 */
public interface PermissionType {

    /**
     * system level permission
     */
    String CREATE_APPLICATION = "CreateApplication";
    String MANAGE_APP_MASTER = "ManageAppMaster";

    /**
     * APP level permission
     */

    String CREATE_NAMESPACE = "CreateNamespace";

    String CREATE_CLUSTER = "CreateCluster";

    /**
     * 分配用户权限的权限
     */
    String ASSIGN_ROLE = "AssignRole";

    /**
     * namespace level permission
     */

    String MODIFY_NAMESPACE = "ModifyNamespace";

    String RELEASE_NAMESPACE = "ReleaseNamespace";

}
