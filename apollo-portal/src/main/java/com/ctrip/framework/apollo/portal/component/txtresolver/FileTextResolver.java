package com.ctrip.framework.apollo.portal.component.txtresolver;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.StringUtils;

/**
 * 文件文本解析`(对文本来说, key为"content", 值为文本信息)<br>
 * 适用于 yaml、yml、json、xml 格式
 * 
 * @author mengaijun
 * @Description: TODO
 * @date: 2020年4月15日 下午2:36:07
 */
@Component("fileTextResolver")
public class FileTextResolver implements ConfigTextResolver {

    @Override
    public ItemChangeSets resolve(long namespaceId, String configText, List<ItemDTO> baseItems) {
        ItemChangeSets changeSets = new ItemChangeSets();
        
        // 配置文本为空，不进行修改
        if (CollectionUtils.isEmpty(baseItems) && StringUtils.isEmpty(configText)) {
            return changeSets;
        }
        
        // 不存在已有配置，创建 ItemDTO 到 ItemChangeSets 新增项
        if (CollectionUtils.isEmpty(baseItems)) {
            changeSets.addCreateItem(createItem(namespaceId, 0, configText));
        } else {
            // 已存在配置，创建 ItemDTO 到 ItemChangeSets 修改项
            ItemDTO beforeItem = baseItems.get(0);
            if (!configText.equals(beforeItem.getValue())) {// update
                changeSets.addUpdateItem(createItem(namespaceId, beforeItem.getId(), configText));
            }
        }

        return changeSets;
    }

    /**
     * 根据文本信息创建配置项Item
     * @param namespaceId
     * @param itemId
     * @param value
     * @return
     */
    private ItemDTO createItem(long namespaceId, long itemId, String value) {
        ItemDTO item = new ItemDTO();
        item.setId(itemId);
        item.setNamespaceId(namespaceId);
        item.setValue(value);
        item.setLineNum(1);
        item.setKey(ConfigConsts.CONFIG_FILE_CONTENT_KEY);
        return item;
    }
}
