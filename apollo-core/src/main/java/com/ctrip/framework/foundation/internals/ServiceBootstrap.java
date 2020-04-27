package com.ctrip.framework.foundation.internals;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import com.ctrip.framework.apollo.core.spi.Ordered;
import com.google.common.collect.Lists;

/**
 * 启动加载服务(spi加载)
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月24日 下午3:07:46
 */
public class ServiceBootstrap {
    /**
     * 加载实例
     * 
     * @param clazz
     * @return S
     * @date: 2020年4月24日 下午3:08:51
     */
    public static <S> S loadFirst(Class<S> clazz) {
        Iterator<S> iterator = loadAll(clazz);
        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format(
                    "No implementation defined in /META-INF/services/%s, please check whether the file exists and has the right implementation class!",
                    clazz.getName()));
        }
        return iterator.next();
    }

    /**
     * SPI加载对象
     * 
     * @param clazz
     * @return Iterator<S>
     * @date: 2020年4月24日 下午3:16:11
     */
    public static <S> Iterator<S> loadAll(Class<S> clazz) {
        ServiceLoader<S> loader = ServiceLoader.load(clazz);

        return loader.iterator();
    }

    public static <S extends Ordered> List<S> loadAllOrdered(Class<S> clazz) {
        Iterator<S> iterator = loadAll(clazz);

        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format(
                    "No implementation defined in /META-INF/services/%s, please check whether the file exists and has the right implementation class!",
                    clazz.getName()));
        }

        List<S> candidates = Lists.newArrayList(iterator);
        Collections.sort(candidates, new Comparator<S>() {
            @Override
            public int compare(S o1, S o2) {
                // the smaller order has higher priority
                return Integer.compare(o1.getOrder(), o2.getOrder());
            }
        });

        return candidates;
    }

    public static <S extends Ordered> S loadPrimary(Class<S> clazz) {
        List<S> candidates = loadAllOrdered(clazz);

        return candidates.get(0);
    }
}
