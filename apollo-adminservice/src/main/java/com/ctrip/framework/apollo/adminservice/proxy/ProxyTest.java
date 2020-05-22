package com.ctrip.framework.apollo.adminservice.proxy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ProxyTest {

    public static void main(String[] args) {

    }

    public static void main1(String[] args) throws NoSuchMethodException, SecurityException, IOException {
        UserService us = new UserServiceImpl();
        
        UserService usProxy = (UserService) Proxy.newProxyInstance(us.getClass().getClassLoader(),
                us.getClass().getInterfaces(),
                new UserServiceHandler(us));

        // 在运行的方法中要配置这个属性
        System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        // 将生成的代理类写入文件，名字就是¥Proxy0，那后filePath自己随便
        byte[] classFile = sun.misc.ProxyGenerator.generateProxyClass("$Proxy0", UserServiceImpl.class.getInterfaces());
        FileOutputStream out = new FileOutputStream(
                "E:\\github\\apollo\\apollo-adminservice\\target\\classes\\com\\ctrip\\framework\\apollo\\adminservice\\proxy\\UserService"
                        + "$Proxy0.class");
        out.write(classFile);
        out.close();


        System.out.println(usProxy.getUser());
    }

    static class UserServiceHandler implements InvocationHandler {

        private UserService target;

        public UserServiceHandler(UserService target) {
            super();
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("代理了方法");

            // method为的接口Method对象
            UserAnnotation anno = method.getAnnotation(UserAnnotation.class);
            if (anno != null) {
                System.out.println("method存在UserAnnotation注解: " + anno.name());
            }

            // 可以根据目标对象获取实现类的Method对象
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            anno = targetMethod.getAnnotation(UserAnnotation.class);
            if (anno != null) {
                System.out.println("targetMethod存在UserAnnotation注解: " + anno.name());
        }

            return method.invoke(target, args);
        }

}
}

