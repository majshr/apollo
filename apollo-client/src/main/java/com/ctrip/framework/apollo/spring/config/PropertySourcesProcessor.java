package com.ctrip.framework.apollo.spring.config;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.property.AutoUpdateConfigChangeListener;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Apollo Property Sources processor for Spring Annotation Based Application.
 * <br />
 * <br />
 * 实现 BeanFactoryPostProcessor、EnvironmentAware、PriorityOrdered 接口，PropertySource 处理器。
 *
 * The reason why PropertySourcesProcessor implements
 * {@link BeanFactoryPostProcessor} instead of
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}
 * is that lower versions of Spring (e.g. 3.1.1) doesn't support registering
 * BeanDefinitionRegistryPostProcessor in ImportBeanDefinitionRegistrar -
 * {@link com.ctrip.framework.apollo.spring.annotation.ApolloConfigRegistrar}
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class PropertySourcesProcessor implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {
	/**
	 * Namespace 名字集合<br>
	 *
	 * KEY：优先级     VALUE：Namespace 名字集合<br>
	 * Apollo 在解析到的 XML 或注解配置的 Apollo Namespace 时，会调用 #addNamespaces(namespaces,
	 * order) 方法，添加到其中。
	 */
	private static final Multimap<Integer, String> NAMESPACE_NAMES = LinkedHashMultimap.create();
	private static final Set<BeanFactory> AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES = Sets.newConcurrentHashSet();

	/**
	 * ConfigPropertySource 工厂。在 NAMESPACE_NAMES 中的每一个 Namespace，都会创建成对应的
	 * ConfigPropertySource 对象( 基于 Apollo Config 的 PropertySource 实现类 )，并添加到
	 * environment 中。<br>
	 * 
	 * 通过这样的方式，Spring 的 <property name="" value="" /> 和 @Value 注解，就可以从 environment
	 * 中，直接读取到对应的属性值。
	 */
	private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector
			.getInstance(ConfigPropertySourceFactory.class);

	private final ConfigUtil configUtil = ApolloInjector.getInstance(ConfigUtil.class);

	/**
	 * Spring ConfigurableEnvironment 对象, 通过它，可以获取到应用实例中，所有的配置属性信息。
	 */
	private ConfigurableEnvironment environment;

	/**
	 * 添加信息到NAMESPACE_NAMES
	 * 
	 * @param namespaces
	 * @param order
	 * @return boolean
	 * @date: 2020年4月22日 上午10:09:05
	 */
	public static boolean addNamespaces(Collection<String> namespaces, int order) {
		return NAMESPACE_NAMES.putAll(order, namespaces);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// 初始化 PropertySource 们
		initializePropertySources();
		// 初始化 AutoUpdateConfigChangeListener 对象，实现属性的自动更新
		initializeAutoUpdatePropertiesFeature(beanFactory);
	}

	/**
	 * 初始化 PropertySource 们
	 * 
	 * @date: 2020年4月22日 上午10:15:53
	 */
	private void initializePropertySources() {
		// 若 `environment` 已经有 APOLLO_PROPERTY_SOURCE_NAME 属性源，说明已经初始化，直接返回。
		if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_PROPERTY_SOURCE_NAME)) {
			// already initialized
			return;
		}
		
		// 创建 CompositePropertySource 对象，组合多个 Namespace 的 ConfigPropertySource 。
		CompositePropertySource composite = new CompositePropertySource(
				PropertySourcesConstants.APOLLO_PROPERTY_SOURCE_NAME);

		// 按照优先级，顺序遍历 Namespace
		// sort by order asc
		ImmutableSortedSet<Integer> orders = ImmutableSortedSet.copyOf(NAMESPACE_NAMES.keySet());
		Iterator<Integer> iterator = orders.iterator();

		while (iterator.hasNext()) {
			int order = iterator.next();
			for (String namespace : NAMESPACE_NAMES.get(order)) {
				// 创建 Apollo Config 对象
				Config config = ConfigService.getConfig(namespace);

				// 创建 Namespace 对应的 ConfigPropertySource 对象
				// 添加到 `composite` 中。
				composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
			}
		}

		// clean up
		NAMESPACE_NAMES.clear();

		// add after the bootstrap property source or to the first
		// 若有 APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME 属性源，添加到其后
		if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {

			// ensure ApolloBootstrapPropertySources is still the first
			ensureBootstrapPropertyPrecedence(environment);

			environment.getPropertySources().addAfter(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME,
					composite);
		} else {
			// 若没 APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME 属性源，添加到首个
			environment.getPropertySources().addFirst(composite);
		}
	}

	private void ensureBootstrapPropertyPrecedence(ConfigurableEnvironment environment) {
		MutablePropertySources propertySources = environment.getPropertySources();

		PropertySource<?> bootstrapPropertySource = propertySources
				.get(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);

		// not exists or already in the first place
		if (bootstrapPropertySource == null || propertySources.precedenceOf(bootstrapPropertySource) == 0) {
			return;
		}

		propertySources.remove(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
		propertySources.addFirst(bootstrapPropertySource);
	}

	/**
	 * 初始化 AutoUpdateConfigChangeListener 对象，实现属性的自动更新
	 * 
	 * @param beanFactory void
	 * @date: 2020年4月22日 上午10:16:02
	 */
	private void initializeAutoUpdatePropertiesFeature(ConfigurableListableBeanFactory beanFactory) {
		// 若未开启属性的自动更新功能，直接返回
		if (!configUtil.isAutoUpdateInjectedSpringPropertiesEnabled()
				|| !AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES.add(beanFactory)) {
			return;
		}

		// 创建 AutoUpdateConfigChangeListener 对象
		AutoUpdateConfigChangeListener autoUpdateConfigChangeListener = new AutoUpdateConfigChangeListener(environment,
				beanFactory);

		// 循环，向 ConfigPropertySource 注册配置变更器, 从而实现 Apollo Config 配置变更的监听。
		List<ConfigPropertySource> configPropertySources = configPropertySourceFactory.getAllConfigPropertySources();
		for (ConfigPropertySource configPropertySource : configPropertySources) {
			configPropertySource.addChangeListener(autoUpdateConfigChangeListener);
		}
	}

	/**
	 * 注入spring环境变量信息
	 */
	@Override
	public void setEnvironment(Environment environment) {
		// it is safe enough to cast as all known environment is derived from
		// ConfigurableEnvironment
		this.environment = (ConfigurableEnvironment) environment;
	}

	@Override
	public int getOrder() {
		// make it as early as possible
		return Ordered.HIGHEST_PRECEDENCE;
	}

	// for test only
	static void reset() {
		NAMESPACE_NAMES.clear();
		AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES.clear();
	}
}
