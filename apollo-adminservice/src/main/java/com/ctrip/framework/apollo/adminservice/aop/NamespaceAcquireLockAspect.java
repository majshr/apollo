package com.ctrip.framework.apollo.adminservice.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.biz.config.BizConfig;
import com.ctrip.framework.apollo.biz.entity.Item;
import com.ctrip.framework.apollo.biz.entity.Namespace;
import com.ctrip.framework.apollo.biz.entity.NamespaceLock;
import com.ctrip.framework.apollo.biz.service.ItemService;
import com.ctrip.framework.apollo.biz.service.NamespaceLockService;
import com.ctrip.framework.apollo.biz.service.NamespaceService;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.exception.ServiceException;

/**
 * 一个namespace在一次发布中只能允许一个人修改配置 通过数据库lock表来实现
 */
@Aspect
@Component
public class NamespaceAcquireLockAspect {
    private static final Logger logger = LoggerFactory.getLogger(NamespaceAcquireLockAspect.class);

    private final NamespaceLockService namespaceLockService;
    private final NamespaceService namespaceService;
    private final ItemService itemService;
    private final BizConfig bizConfig;

    public NamespaceAcquireLockAspect(final NamespaceLockService namespaceLockService,
            final NamespaceService namespaceService, final ItemService itemService, final BizConfig bizConfig) {
        this.namespaceLockService = namespaceLockService;
        this.namespaceService = namespaceService;
        this.itemService = itemService;
        this.bizConfig = bizConfig;
    }

    // create item
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, item, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName, ItemDTO item) {
        acquireLock(appId, clusterName, namespaceName, item.getDataChangeLastModifiedBy());
    }

    // update item
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, itemId, item, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName, long itemId, ItemDTO item) {
        acquireLock(appId, clusterName, namespaceName, item.getDataChangeLastModifiedBy());
    }

    // update by change set
    @Before("@annotation(PreAcquireNamespaceLock) && args(appId, clusterName, namespaceName, changeSet, ..)")
    public void requireLockAdvice(String appId, String clusterName, String namespaceName, ItemChangeSets changeSet) {
        acquireLock(appId, clusterName, namespaceName, changeSet.getDataChangeLastModifiedBy());
    }

    // delete item
    @Before("@annotation(PreAcquireNamespaceLock) && args(itemId, operator, ..)")
    public void requireLockAdvice(long itemId, String operator) {
        Item item = itemService.findOne(itemId);
        if (item == null) {
            throw new BadRequestException("item not exist.");
        }
        acquireLock(item.getNamespaceId(), operator);
    }

    void acquireLock(String appId, String clusterName, String namespaceName, String currentUser) {
        // 当关闭锁定 Namespace 开关时，直接返回
        if (bizConfig.isNamespaceLockSwitchOff()) {
            return;
        }
        // 获得 Namespace 对象
        Namespace namespace = namespaceService.findOne(appId, clusterName, namespaceName);
        // 尝试锁定
        acquireLock(namespace, currentUser);
    }

    void acquireLock(long namespaceId, String currentUser) {
        if (bizConfig.isNamespaceLockSwitchOff()) {
            return;
        }

        Namespace namespace = namespaceService.findOne(namespaceId);

        acquireLock(namespace, currentUser);

    }

    /**
     * 尝试锁定
     * 
     * @param namespace
     * @param currentUser
     *            void
     * @date: 2020年4月16日 下午5:10:30
     */
    private void acquireLock(Namespace namespace, String currentUser) {
        if (namespace == null) {
            throw new BadRequestException("namespace not exist.");
        }

        long namespaceId = namespace.getId();

        NamespaceLock namespaceLock = namespaceLockService.findLock(namespaceId);
        if (namespaceLock == null) {
            try {
                tryLock(namespaceId, currentUser);
                // lock success
            } catch (DataIntegrityViolationException e) {
                // lock fail
                // 唯一索引重复, 重新获取锁对象
                namespaceLock = namespaceLockService.findLock(namespaceId);
                // 校验获得锁的是不是自己
                checkLock(namespace, namespaceLock, currentUser);
            } catch (Exception e) {
                logger.error("try lock error", e);
                throw e;
            }
        } else {
            // check lock owner is current user
            // 校验获得锁的是不是自己
            checkLock(namespace, namespaceLock, currentUser);
        }
    }

    /**
     * 尝试锁定(也就是加一天记录, 有唯一主键, 多个人添加的话只有一个成功, 其他会有报错)
     * 
     * @param namespaceId
     * @param user
     * @date: 2020年4月16日 下午5:11:17
     */
    private void tryLock(long namespaceId, String user) {
        NamespaceLock lock = new NamespaceLock();
        lock.setNamespaceId(namespaceId);
        lock.setDataChangeCreatedBy(user);
        lock.setDataChangeLastModifiedBy(user);
        namespaceLockService.tryLock(lock);
    }

    private void checkLock(Namespace namespace, NamespaceLock namespaceLock, String currentUser) {
        if (namespaceLock == null) {
            throw new ServiceException(
                    String.format("Check lock for %s failed, please retry.", namespace.getNamespaceName()));
        }

        String lockOwner = namespaceLock.getDataChangeCreatedBy();
        if (!lockOwner.equals(currentUser)) {
            throw new BadRequestException("namespace:" + namespace.getNamespaceName() + " is modified by " + lockOwner);
        }
    }

}
