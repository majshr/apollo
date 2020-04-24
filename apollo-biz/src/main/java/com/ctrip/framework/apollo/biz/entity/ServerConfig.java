package com.ctrip.framework.apollo.biz.entity;

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
@Table(name = "ServerConfig")
@SQLDelete(sql = "Update ServerConfig set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class ServerConfig extends BaseEntity {
    @Column(name = "Key", nullable = false)
    private String key;

    @Column(name = "Cluster", nullable = false)
    private String cluster;

    @Column(name = "Value", nullable = false)
    private String value;

    @Column(name = "Comment", nullable = false)
    private String comment;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String toString() {
        return toStringHelper().add("key", key).add("value", value).add("comment", comment).toString();
    }
}
