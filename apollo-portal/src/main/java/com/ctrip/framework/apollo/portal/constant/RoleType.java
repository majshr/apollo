package com.ctrip.framework.apollo.portal.constant;

/**
 * 规则类型
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 上午11:27:04
 */
public class RoleType {

    public static final String MASTER = "Master";

    public static final String MODIFY_NAMESPACE = "ModifyNamespace";

    public static final String RELEASE_NAMESPACE = "ReleaseNamespace";

    public static boolean isValidRoleType(String roleType) {
        return MASTER.equals(roleType) || MODIFY_NAMESPACE.equals(roleType) || RELEASE_NAMESPACE.equals(roleType);
    }

}
