package com.ctrip.framework.apollo.portal.listener;

import org.springframework.context.ApplicationEvent;

import com.ctrip.framework.apollo.common.entity.App;
import com.google.common.base.Preconditions;

public class AppCreationEvent extends ApplicationEvent {

    public AppCreationEvent(Object source) {
        super(source);
    }

    public App getApp() {
        Preconditions.checkState(source != null);
        return (App) this.source;
    }
}
