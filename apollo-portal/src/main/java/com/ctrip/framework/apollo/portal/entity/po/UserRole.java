package com.ctrip.framework.apollo.portal.entity.po;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.ctrip.framework.apollo.common.entity.BaseEntity;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "UserRole")
@SQLDelete(sql = "Update UserRole set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class UserRole extends BaseEntity {
    /**
     * 账号 {@link UserPO#username}, 我们自己的业务系统里，推荐使用 UserPO.id 。
     */
    @Column(name = "UserId", nullable = false)
    private String userId;

    /**
     * 角色编号 {@link Role#id}
     */
    @Column(name = "RoleId", nullable = false)
    private long roleId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getRoleId() {
        return roleId;
    }

    public void setRoleId(long roleId) {
        this.roleId = roleId;
    }
}
