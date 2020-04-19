package com.ctrip.framework.apollo.biz.entity;

import com.ctrip.framework.apollo.common.entity.BaseEntity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@Entity
@Table(name = "ReleaseHistory")
@SQLDelete(sql = "Update ReleaseHistory set isDeleted = 1 where id = ?")
@Where(clause = "isDeleted = 0")
public class ReleaseHistory extends BaseEntity {
	/**
	 * App 编号
	 */
	@Column(name = "AppId", nullable = false)
	private String appId;

	/**
	 * Cluster 名字
	 */
	@Column(name = "ClusterName", nullable = false)
	private String clusterName;

	/**
	 * Namespace 名字
	 */
	@Column(name = "NamespaceName", nullable = false)
	private String namespaceName;

	/**
	 * Branch 名
	 *
	 * 主干，使用 Cluster 名字 分支，使用子 Cluster 名字
	 */
	@Column(name = "BranchName", nullable = false)
	private String branchName;

	/**
	 * Release 编号
	 */
	@Column(name = "ReleaseId")
	private long releaseId;

	/**
	 * 上一次 Release 编号
	 */
	@Column(name = "PreviousReleaseId")
	private long previousReleaseId;

	/**
	 * 操作类型 {@link com.ctrip.framework.apollo.common.constants.ReleaseOperation}
	 */
	@Column(name = "Operation")
	private int operation;

	/**
	 * 操作 Context
	 */
	@Column(name = "OperationContext", nullable = false)
	private String operationContext;

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getNamespaceName() {
		return namespaceName;
	}

	public void setNamespaceName(String namespaceName) {
		this.namespaceName = namespaceName;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public long getReleaseId() {
		return releaseId;
	}

	public void setReleaseId(long releaseId) {
		this.releaseId = releaseId;
	}

	public long getPreviousReleaseId() {
		return previousReleaseId;
	}

	public void setPreviousReleaseId(long previousReleaseId) {
		this.previousReleaseId = previousReleaseId;
	}

	public int getOperation() {
		return operation;
	}

	public void setOperation(int operation) {
		this.operation = operation;
	}

	public String getOperationContext() {
		return operationContext;
	}

	public void setOperationContext(String operationContext) {
		this.operationContext = operationContext;
	}

	public String toString() {
		return toStringHelper().add("appId", appId).add("clusterName", clusterName).add("namespaceName", namespaceName)
				.add("branchName", branchName).add("releaseId", releaseId).add("previousReleaseId", previousReleaseId)
				.add("operation", operation).toString();
	}
}
