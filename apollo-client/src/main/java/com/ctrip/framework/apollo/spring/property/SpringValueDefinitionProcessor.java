package com.ctrip.framework.apollo.spring.property;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * 解析xml属性(对应 @Value 注解 )<br>
 * 处理 Spring XML PlaceHolder ，解析成 StringValueDefinition 集合。<br>
 * 
 * To process xml config placeholders, e.g.
 *
 * 每个 <property /> 都会被解析成一个 StringValueDefinition 对象。<br>
 *
 * <pre>
 *  &lt;bean class=&quot;com.ctrip.framework.apollo.demo.spring.xmlConfigDemo.bean.XmlBean&quot;&gt;
 *    &lt;property name=&quot;timeout&quot; value=&quot;${timeout:200}&quot;/&gt;
 *    &lt;property name=&quot;batch&quot; value=&quot;${batch:100}&quot;/&gt;
 *  &lt;/bean&gt;
 * </pre>
 */
public class SpringValueDefinitionProcessor implements BeanDefinitionRegistryPostProcessor {

    /**
     * SpringValueDefinition 集合<br>
     *
     * KEY：beanName<br>
     * VALUE：SpringValueDefinition 集合(每个属性一个)
     */
    private static final Map<BeanDefinitionRegistry, Multimap<String, SpringValueDefinition>> beanName2SpringValueDefinitions = Maps
            .newConcurrentMap();
    private static final Set<BeanDefinitionRegistry> PROPERTY_VALUES_PROCESSED_BEAN_FACTORIES = Sets
            .newConcurrentHashSet();

    private final ConfigUtil configUtil;
    private final PlaceholderHelper placeholderHelper;

    public SpringValueDefinitionProcessor() {
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 是否开启自动更新功能，因为 SpringValueDefinitionProcessor 就是为了这个功能编写的
        if (configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()) {
            processPropertyValues(registry);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    /**
     * beanName->SpringValueDefinition定义
     * 
     * @param registry
     * @return Multimap<String,SpringValueDefinition>
     * @date: 2020年5月7日 下午4:45:02
     */
    public static Multimap<String, SpringValueDefinition> getBeanName2SpringValueDefinitions(
            BeanDefinitionRegistry registry) {
        Multimap<String, SpringValueDefinition> springValueDefinitions = beanName2SpringValueDefinitions.get(registry);
        if (springValueDefinitions == null) {
            springValueDefinitions = LinkedListMultimap.create();
        }

        return springValueDefinitions;
    }

    /**
     * 自动更新实现
     * 
     * @param beanRegistry
     * @date: 2020年5月7日 下午5:36:39
     */
    private void processPropertyValues(BeanDefinitionRegistry beanRegistry) {
        if (!PROPERTY_VALUES_PROCESSED_BEAN_FACTORIES.add(beanRegistry)) {
            // already initialized
            return;
        }

        if (!beanName2SpringValueDefinitions.containsKey(beanRegistry)) {
            beanName2SpringValueDefinitions.put(beanRegistry,
                    LinkedListMultimap.<String, SpringValueDefinition> create());
        }

        Multimap<String, SpringValueDefinition> springValueDefinitions = beanName2SpringValueDefinitions
                .get(beanRegistry);

        // 循环 BeanDefinition 集合
        String[] beanNames = beanRegistry.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanRegistry.getBeanDefinition(beanName);
            // 循环 BeanDefinition 的 PropertyValue 数组
            MutablePropertyValues mutablePropertyValues = beanDefinition.getPropertyValues();
            List<PropertyValue> propertyValues = mutablePropertyValues.getPropertyValueList();
            for (PropertyValue propertyValue : propertyValues) {
                // 获得 `value` 属性。
                Object value = propertyValue.getValue();
                // 忽略非 Spring PlaceHolder 的 `value` 属性。
                if (!(value instanceof TypedStringValue)) {
                    continue;
                }
                // 获得 `placeholder` 属性。(${xxx})
                String placeholder = ((TypedStringValue) value).getValue();
                // 提取 `keys` 属性们。
                Set<String> keys = placeholderHelper.extractPlaceholderKeys(placeholder);

                if (keys.isEmpty()) {
                    continue;
                }

                // 循环 `keys` ，创建对应的 SpringValueDefinition 对象，并添加到
                // `beanName2SpringValueDefinitions` 中。
                for (String key : keys) {
                    springValueDefinitions.put(beanName,
                            new SpringValueDefinition(key, placeholder, propertyValue.getName()));
                }
            }
        }
    }
}
