package com.ctrip.framework.apollo.common.constants;

/**
 * release操作类型
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseOperation {
	int NORMAL_RELEASE = 0;  // 主干发布
	int ROLLBACK = 1;  // 回滚
	int GRAY_RELEASE = 2;  // 灰度发布
	int APPLY_GRAY_RULES = 3;
	int GRAY_RELEASE_MERGE_TO_MASTER = 4;
	int MASTER_NORMAL_RELEASE_MERGE_TO_GRAY = 5;
	int MATER_ROLLBACK_MERGE_TO_GRAY = 6;
	int ABANDON_GRAY_RELEASE = 7;
	int GRAY_RELEASE_DELETED_AFTER_MERGE = 8;
}
