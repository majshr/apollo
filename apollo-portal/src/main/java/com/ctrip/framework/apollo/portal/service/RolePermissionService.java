package com.ctrip.framework.apollo.portal.service;

import java.util.List;
import java.util.Set;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public interface RolePermissionService {

    /**
     * Create role with permissions, note that role name should be unique
     */
    public Role createRoleWithPermissions(Role role, Set<Long> permissionIds);

    /**
     * Assign role to users<br>
     * 授权给用户
     * 
     * @param roleName
     * @param userIds
     *            需要授权的用户
     * @param operatorUserId
     *            操作者
     * @return Set<String>
     * @date: 2020年4月24日 上午11:13:09
     */
    public Set<String> assignRoleToUsers(String roleName, Set<String> userIds, String operatorUserId);

    /**
     * Remove role from users
     */
    public void removeRoleFromUsers(String roleName, Set<String> userIds, String operatorUserId);

    /**
     * Query users with role
     */
    public Set<UserInfo> queryUsersWithRole(String roleName);

    /**
     * Find role by role name, note that roleName should be unique
     */
    public Role findRoleByRoleName(String roleName);

    /**
     * Check whether user has the permission
     */
    public boolean userHasPermission(String userId, String permissionType, String targetId);

    /**
     * Find the user's roles
     */
    public List<Role> findUserRoles(String userId);

    public boolean isSuperAdmin(String userId);

    /**
     * Create permission, note that permissionType + targetId should be unique
     */
    public Permission createPermission(Permission permission);

    /**
     * Create permissions, note that permissionType + targetId should be unique
     */
    public Set<Permission> createPermissions(Set<Permission> permissions);

    /**
     * delete permissions when delete app.
     */
    public void deleteRolePermissionsByAppId(String appId, String operator);

    /**
     * delete permissions when delete app namespace.
     */
    public void deleteRolePermissionsByAppIdAndNamespace(String appId, String namespaceName, String operator);
}
