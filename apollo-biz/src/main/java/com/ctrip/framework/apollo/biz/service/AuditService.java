package com.ctrip.framework.apollo.biz.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.repository.AuditRepository;

/**
 * audit日志信息service
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 下午5:04:03
 */
@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(final AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    List<Audit> findByOwner(String owner) {
        return auditRepository.findByOwner(owner);
    }

    List<Audit> find(String owner, String entity, String op) {
        return auditRepository.findAudits(owner, entity, op);
    }

    @Transactional
    void audit(String entityName, Long entityId, Audit.OP op, String owner) {
        Audit audit = new Audit();
        audit.setEntityName(entityName);
        audit.setEntityId(entityId);
        audit.setOpName(op.name());
        audit.setDataChangeCreatedBy(owner);
        auditRepository.save(audit);
    }

    @Transactional
    void audit(Audit audit) {
        auditRepository.save(audit);
    }
}
