package com.ctrip.framework.apollo.spring.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.core.Ordered;
import org.w3c.dom.Element;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

/**
 * 自定义标签处理器<br>
 * Apollo 的 XML Namespace 的处理器<br>
 * <config namespaces = "" order = ""></config>
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class NamespaceHandler extends NamespaceHandlerSupport {
    /**
     * "," 分隔字符串
     * 
     */
    private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

    @Override
    public void init() {
        registerBeanDefinitionParser("config", new BeanParser());
    }

    /**
     * bean定义解析
     * 
     * @author mengaijun
     * @Description: TODO
     * @date: 2020年5月7日 上午9:38:57
     */
    static class BeanParser extends AbstractSingleBeanDefinitionParser {
        @Override
        protected Class<?> getBeanClass(Element element) {
            return ConfigPropertySourcesProcessor.class;
        }

        @Override
        protected boolean shouldGenerateId() {
            return true;
        }

        // 解析 XML 配置
        @Override
        protected void doParse(Element element, BeanDefinitionBuilder builder) {
            // 解析 `namespaces` 属性，默认为 `"application"`
            String namespaces = element.getAttribute("namespaces");
            // default to application
            if (Strings.isNullOrEmpty(namespaces)) {
                namespaces = ConfigConsts.NAMESPACE_APPLICATION;
            }

            // 解析 `order` 属性，默认为 Ordered.LOWEST_PRECEDENCE;
            int order = Ordered.LOWEST_PRECEDENCE;
            String orderAttribute = element.getAttribute("order");

            if (!Strings.isNullOrEmpty(orderAttribute)) {
                try {
                    order = Integer.parseInt(orderAttribute);
                } catch (Throwable ex) {
                    throw new IllegalArgumentException(
                            String.format("Invalid order: %s for namespaces: %s", orderAttribute, namespaces));
                }
            }

            // 添加到 PropertySourcesProcessor
            PropertySourcesProcessor.addNamespaces(NAMESPACE_SPLITTER.splitToList(namespaces), order);
        }
    }
}
