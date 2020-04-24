package com.ctrip.framework.apollo.portal.repository;

import org.springframework.data.repository.PagingAndSortingRepository;

import com.ctrip.framework.apollo.portal.entity.po.ServerConfig;

public interface ServerConfigRepository extends PagingAndSortingRepository<ServerConfig, Long> {
    ServerConfig findByKey(String key);
}
