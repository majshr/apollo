package com.ctrip.framework.apollo.spring.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

/**
 * Apollo 处理器抽象类，封装了在 Spring Bean 初始化之前，处理属性和方法。<br>
 * Create by zhangzheng on 2018/2/6
 */
public abstract class ApolloProcessor implements BeanPostProcessor, PriorityOrdered {

    // bean初始化方法之前执行(此时bean构造方法和依赖注入执行完, 初始化方法没执行, 还没有生成代理对象)
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class clazz = bean.getClass();
        for (Field field : findAllField(clazz)) {
            processField(bean, beanName, field);
        }
        for (Method method : findAllMethod(clazz)) {
            processMethod(bean, beanName, method);
        }
        return bean;
    }

    // 如果有代理, 也是此处实现的代理(bean初始化完成之后)
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 子类需要实现, 来处理字段
     * 
     * @param bean
     * @param beanName
     * @param field
     * @date: 2020年5月7日 下午2:44:01
     */
    protected abstract void processField(Object bean, String beanName, Field field);

    /**
     * 子类需要实现来处理方法
     * 
     * @param bean
     * @param beanName
     * @param method
     * @date: 2020年5月7日 下午2:44:20
     */
    protected abstract void processMethod(Object bean, String beanName, Method method);

    @Override
    public int getOrder() {
        // make it as late as possible
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 查找类的所有字段
     * 
     * @param clazz
     * @return List<Field>
     * @date: 2020年5月7日 下午2:46:27
     */
    private List<Field> findAllField(Class clazz) {
        final List<Field> res = new LinkedList<>();
        ReflectionUtils.doWithFields(clazz, new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                res.add(field);
            }
        });
        return res;
    }

    /**
     * 获取类的所有方法
     * 
     * @param clazz
     * @return List<Method>
     * @date: 2020年5月7日 下午2:47:17
     */
    private List<Method> findAllMethod(Class clazz) {
        final List<Method> res = new LinkedList<>();
        ReflectionUtils.doWithMethods(clazz, new ReflectionUtils.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                res.add(method);
            }
        });
        return res;
    }
}
