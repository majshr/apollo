package com.ctrip.framework.apollo.tracer.spi;

/**
 * 消息生产者管理器
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public interface MessageProducerManager {
    /**
     * 获取消息生产者
     * 
     * @return the message producer
     */
    MessageProducer getProducer();
}
