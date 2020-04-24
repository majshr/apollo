package com.ctrip.framework.apollo.portal.entity.po;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

/**
 * Users 表，对应实体
 * 
 * @author lepdou 2017-04-08
 */
@Entity
@Table(name = "Users")
public class UserPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private long id;
    @Column(name = "Username", nullable = false)
    private String username;
    @Column(name = "Password", nullable = false)
    private String password;
    @Column(name = "Email", nullable = false)
    private String email;
    @Column(name = "Enabled", nullable = false)
    private int enabled;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getEnabled() {
        return enabled;
    }

    public void setEnabled(int enabled) {
        this.enabled = enabled;
    }

    /**
     * userPO转为userBO
     * 
     * @return UserInfo
     * @date: 2020年4月24日 上午10:19:09
     */
    public UserInfo toUserInfo() {
        UserInfo userInfo = new UserInfo();
        userInfo.setName(this.getUsername());
        userInfo.setUserId(this.getUsername());
        userInfo.setEmail(this.getEmail());
        return userInfo;
    }
}
