package com.ctrip.framework.apollo.portal.entity.po;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.ctrip.framework.apollo.common.entity.BaseEntity;

/**
 * 对应role表
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "Role")
@SQLDelete(sql = "Update Role set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class Role extends BaseEntity {

    /**
     * roleName 字段，角色名，通过系统自动生成。目前有三种类型( 不是三个 )角色：<br>
     * App 管理员，格式为 "Master + AppId" ，例如："Master+100004458" 。 <br>
     * 
     * Namespace 修改管理员，格式为 "ModifyNamespace + AppId + NamespaceName"
     * ，例如："ModifyNamespace+100004458+application" 。<br>
     * 
     * Namespace 发布管理员，格式为 "ReleaseNamespace + AppId + NamespaceName"
     * ，例如："ReleaseNamespace+100004458+application" 。
     */
    @Column(name = "RoleName", nullable = false)
    private String roleName;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
