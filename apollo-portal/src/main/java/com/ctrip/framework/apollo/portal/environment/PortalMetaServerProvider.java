package com.ctrip.framework.apollo.portal.environment;

/**
 * MetaServer信息提供者接口(有多种实现, 从数据库查询配置, 从环境变量, 配置文件查询配置)<br>
 * For the supporting of multiple meta server address providers. From
 * configuration file, from OS environment, From database, ... Just implement
 * this interface
 * 
 * @author wxq
 */
public interface PortalMetaServerProvider {

    /**
     * 获取MetaServer地址
     * 
     * @param targetEnv
     *            environment
     * @return meta server address matched environment
     */
  String getMetaServerAddress(Env targetEnv);

    /**
     * 判断是否存在
     * 
     * @param targetEnv
     *            environment
     * @return environment's meta server address exists or not
     */
  boolean exists(Env targetEnv);

    /**
     * 加载地址<br>
     * reload the meta server address in runtime
     */
  void reload();

}
