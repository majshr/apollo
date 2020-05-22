package com.ctrip.framework.apollo.adminservice.proxy;

public class UserServiceImpl extends UserClass implements UserService {

    // @UserAnnotation(name = "maj")
    @Override
    public String getUser() {
        return "aaa";
    }

}
