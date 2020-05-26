package com.ctrip.framework.apollo.spring.property;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;

/**
 * SpringValue, 分为field和method <br>
 * 
 * @Value method info <br>
 *
 * @author github.com/zhegexiaohuozi seimimaster@gmail.com
 * @since 2018/2/6.
 */
public class SpringValue {

    /**
     * 方法参数
     */
    private MethodParameter methodParameter;

    private Field field;

    /**
     * Bean 对象
     */
    private WeakReference<Object> beanRef;

    /**
     * Bean 名字
     */
    private String beanName;

    /**
     * KEY
     *
     * 即在 Config 中的属性 KEY 。
     */
    private String key;

    /**
     * 占位符
     */
    private String placeholder;

    /**
     * 值类型
     */
    private Class<?> targetType;

    /**
     * 泛型。当是 JSON 类型时，使用
     */
    private Type genericType;

    /**
     * 是否 JSON
     */
    private boolean isJson;

    public SpringValue(String key, String placeholder, Object bean, String beanName, Field field, boolean isJson) {
        this.beanRef = new WeakReference<>(bean);
        this.beanName = beanName;
        this.field = field;
        this.key = key;
        this.placeholder = placeholder;
        this.targetType = field.getType();
        this.isJson = isJson;
        if (isJson) {
            this.genericType = field.getGenericType();
        }
    }

    public SpringValue(String key, String placeholder, Object bean, String beanName, Method method, boolean isJson) {
        this.beanRef = new WeakReference<>(bean);
        this.beanName = beanName;
        this.methodParameter = new MethodParameter(method, 0);
        this.key = key;
        this.placeholder = placeholder;
        Class<?>[] paramTps = method.getParameterTypes();
        this.targetType = paramTps[0];
        this.isJson = isJson;
        if (isJson) {
            this.genericType = method.getGenericParameterTypes()[0];
        }
    }

    /**
     * 更新属性值
     * 
     * @param newVal
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @date: 2020年5月7日 下午4:34:59
     */
    public void update(Object newVal) throws IllegalAccessException, InvocationTargetException {
        if (isField()) {
            injectField(newVal);
        } else {
            injectMethod(newVal);
        }
    }

    /**
     * 设置对象的某个字段的值
     * 
     * @param newVal
     * @throws IllegalAccessException
     * @date: 2020年5月26日 上午10:42:08
     */
    private void injectField(Object newVal) throws IllegalAccessException {
        Object bean = beanRef.get();
        if (bean == null) {
            return;
        }
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        field.set(bean, newVal);
        field.setAccessible(accessible);
    }

    /**
     * 根据调用set方法设置对象某个字段的值
     * 
     * @param newVal
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @date: 2020年5月26日 上午10:42:31
     */
    private void injectMethod(Object newVal) throws InvocationTargetException, IllegalAccessException {
        Object bean = beanRef.get();
        if (bean == null) {
            return;
        }
        methodParameter.getMethod().invoke(bean, newVal);
    }

    public String getBeanName() {
        return beanName;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public String getPlaceholder() {
        return this.placeholder;
    }

    public MethodParameter getMethodParameter() {
        return methodParameter;
    }

    public boolean isField() {
        return this.field != null;
    }

    public Field getField() {
        return field;
    }

    public Type getGenericType() {
        return genericType;
    }

    public boolean isJson() {
        return isJson;
    }

    boolean isTargetBeanValid() {
        return beanRef.get() != null;
    }

    @Override
    public String toString() {
        Object bean = beanRef.get();
        if (bean == null) {
            return "";
        }
        if (isField()) {
            return String.format("key: %s, beanName: %s, field: %s.%s", key, beanName, bean.getClass().getName(),
                    field.getName());
        }
        return String.format("key: %s, beanName: %s, method: %s.%s", key, beanName, bean.getClass().getName(),
                methodParameter.getMethod().getName());
    }
}
