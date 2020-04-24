package com.ctrip.framework.apollo.portal.spi;

import java.util.List;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;

/**
 * User 服务接口，用来给 Portal 提供用户搜索相关功能。
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface UserService {
    /**
     * 查找用户
     * 
     * @param keyword
     * @param offset
     * @param limit
     * @return List<UserInfo>
     * @date: 2020年4月24日 上午10:00:08
     */
    List<UserInfo> searchUsers(String keyword, int offset, int limit);

    UserInfo findByUserId(String userId);

    List<UserInfo> findByUserIds(List<String> userIds);

}
