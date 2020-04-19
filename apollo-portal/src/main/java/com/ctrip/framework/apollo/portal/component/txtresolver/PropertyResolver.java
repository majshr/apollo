package com.ctrip.framework.apollo.portal.component.txtresolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;
import com.ctrip.framework.apollo.common.exception.BadRequestException;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.google.common.base.Strings;

/**
对于注释和空行配置项，基于行数做比较。当发生变化时，使用删除 + 创建的方式。
笔者的理解是，注释和空行配置项，是没有 Key ，每次变化都认为是新的。
另外，这样也可以和注释和空行配置项被改成普通配置项，保持一致。
例如，第一行原先是注释配置项，改成了普通配置项，从数据上也是删除 + 创建的方式。

对于普通配置项，基于 Key 做比较。
例如，第一行原先是普通配置项，结果我们在敲了回车，在第一行添加了注释，那么认为是普通配置项修改了行数。
*/
/**
 * normal property file resolver. update comment and blank item implement by
 * create new item and delete old item. update normal key/value item implement
 * by update.<br>
 * properties 配置解析器
 */
@Component("propertyResolver")
public class PropertyResolver implements ConfigTextResolver {

    private static final String KV_SEPARATOR = "=";
    private static final String ITEM_SEPARATOR = "\n";

    @Override
    public ItemChangeSets resolve(long namespaceId, String configText, List<ItemDTO> baseItems) {

    	// 创建 Item Map ，以 lineNum 为 键
        Map<Integer, ItemDTO> oldLineNumMapItem = BeanUtils.mapByKey("lineNum", baseItems);
        // 创建 Item Map ，以 key 为 键
        Map<String, ItemDTO> oldKeyMapItem = BeanUtils.mapByKey("key", baseItems);

        // remove comment and blank item map.
        oldKeyMapItem.remove("");

        // 按照拆分 Property 配置(按行切分为数组)
        String[] newItems = configText.split(ITEM_SEPARATOR);

        // 校验是否存在重复配置 Key 。若是，抛出 BadRequestException 异常
        if (isHasRepeatKey(newItems)) {
            throw new BadRequestException("config text has repeat key please check.");
        }

        // 创建 ItemChangeSets 对象，并解析配置文件到 ItemChangeSets 中。
        ItemChangeSets changeSets = new ItemChangeSets();
        Map<Integer, String> newLineNumMapItem = new HashMap<>();// use for
                                                                 // delete blank
                                                                 // and comment
                                                                 // item
        int lineCounter = 1;
        for (String newItem : newItems) {
            newItem = newItem.trim();
            newLineNumMapItem.put(lineCounter, newItem);
            // 使用行号，获得已存在的 ItemDTO
            ItemDTO oldItemByLine = oldLineNumMapItem.get(lineCounter);

            // comment item  注释 Item
            if (isCommentItem(newItem)) {
                handleCommentLine(namespaceId, oldItemByLine, newItem, lineCounter, changeSets);

                // blank item  空白 Item
            } else if (isBlankItem(newItem)) {

                handleBlankLine(namespaceId, oldItemByLine, lineCounter, changeSets);

                // normal item  普通 Item
            } else {
                handleNormalLine(namespaceId, oldKeyMapItem, newItem, lineCounter, changeSets);
            }

            lineCounter++;
        }

        // 删除注释和空行配置项
        deleteCommentAndBlankItem(oldLineNumMapItem, newLineNumMapItem, changeSets);
        // 删除普通配置项
        deleteNormalKVItem(oldKeyMapItem, changeSets);

        return changeSets;
    }

    /**
     * 验证数组是否重复
     * @param newItems
     * @return
     */
    private boolean isHasRepeatKey(String[] newItems) {
        Set<String> keys = new HashSet<>();
        int lineCounter = 1;
        int keyCount = 0;
        for (String item : newItems) {
        	// 排除注释和空行的配置项
            if (!isCommentItem(item) && !isBlankItem(item)) {
                keyCount++;
                String[] kv = parseKeyValueFromItem(item);
                if (kv != null) {
                    keys.add(kv[0].toLowerCase());
                } else {
                    throw new BadRequestException("line:" + lineCounter + " key value must separate by '='");
                }
            }
            lineCounter++;
        }

        return keyCount > keys.size();
    }

    private String[] parseKeyValueFromItem(String item) {
        int kvSeparator = item.indexOf(KV_SEPARATOR);
        if (kvSeparator == -1) {
            return null;
        }

        String[] kv = new String[2];
        kv[0] = item.substring(0, kvSeparator).trim();
        kv[1] = item.substring(kvSeparator + 1, item.length()).trim();
        return kv;
    }

    private void handleCommentLine(Long namespaceId, ItemDTO oldItemByLine, String newItem, int lineCounter,
            ItemChangeSets changeSets) {
        String oldComment = oldItemByLine == null ? "" : oldItemByLine.getComment();
        // create comment. implement update comment by delete old comment and
        // create new comment
        // 创建注释 ItemDTO 到 ItemChangeSets 的新增项，若老的配置项不是注释或者不相等。另外，更新注释配置，通过删除 + 添加的方式。
        if (!(isCommentItem(oldItemByLine) && newItem.equals(oldComment))) {
            changeSets.addCreateItem(buildCommentItem(0l, namespaceId, newItem, lineCounter));
        }
    }

    private void handleBlankLine(Long namespaceId, ItemDTO oldItem, int lineCounter, ItemChangeSets changeSets) {
    	// 创建空行 ItemDTO 到 ItemChangeSets 的新增项，若老的不是空行。另外，更新空行配置，通过删除 + 添加的方式
        if (!isBlankItem(oldItem)) {
            changeSets.addCreateItem(buildBlankItem(0l, namespaceId, lineCounter));
        }
    }

    /**
     * 添加新加的配置到ItemChangeSets, 或添加更新的配置到ItemChangeSets; 删除keyMapOldItem的信息
     * @param namespaceId
     * @param keyMapOldItem
     * @param newItem
     * @param lineCounter
     * @param changeSets
     */
    private void handleNormalLine(Long namespaceId, Map<String, ItemDTO> keyMapOldItem, String newItem, int lineCounter,
            ItemChangeSets changeSets) {
    	// 解析一行，生成 [key, value]
        String[] kv = parseKeyValueFromItem(newItem);

        if (kv == null) {
            throw new BadRequestException("line:" + lineCounter + " key value must separate by '='");
        }

        String newKey = kv[0];
        String newValue = kv[1].replace("\\n", "\n"); // handle user input \n

        // 获得老的 ItemDTO 对象
        ItemDTO oldItem = keyMapOldItem.get(newKey);
        // 不存在，则创建 ItemDTO 到 ItemChangeSets 的添加项
        if (oldItem == null) {// new item
            changeSets.addCreateItem(buildNormalItem(0l, namespaceId, newKey, newValue, "", lineCounter));
        } else if (!newValue.equals(oldItem.getValue()) || lineCounter != oldItem.getLineNum()) {// update
            // 属性value值改变或行号改变, 则创建 ItemDTO 到 ItemChangeSets 的修改项                                                                                  // item
            changeSets.addUpdateItem(
                    buildNormalItem(oldItem.getId(), namespaceId, newKey, newValue, oldItem.getComment(), lineCounter));
        }
        // 移除老的 ItemDTO 对象(当所有修改属性操作完成后, 这个集合里剩下的就是需要删除的普通配置项)
        keyMapOldItem.remove(newKey);
    }
 
    /**
     * key为空串, 且comment以"#"或"!"开头
     * @param item
     * @return
     */
    private boolean isCommentItem(ItemDTO item) {
        return item != null && "".equals(item.getKey())
                && (item.getComment().startsWith("#") || item.getComment().startsWith("!"));
    }

    /**
     * 判断是否是注释("#"或"!"开头)
     * @param line
     * @return
     */
    private boolean isCommentItem(String line) {
        return line != null && (line.startsWith("#") || line.startsWith("!"));
    }

    /**
     * 配置项为空白(key为空串且comment为空串)
     * @param item
     * @return
     */
    private boolean isBlankItem(ItemDTO item) {
        return item != null && "".equals(item.getKey()) && "".equals(item.getComment());
    }

    /**
     * 字符串 为空
     * @param line
     * @return
     */
    private boolean isBlankItem(String line) {
        return Strings.nullToEmpty(line).trim().isEmpty();
    }

    private void deleteNormalKVItem(Map<String, ItemDTO> baseKeyMapItem, ItemChangeSets changeSets) {
    	// 将剩余的配置项，添加到 ItemChangeSets 的删除项
        // surplus item is to be deleted
        for (Map.Entry<String, ItemDTO> entry : baseKeyMapItem.entrySet()) {
            changeSets.addDeleteItem(entry.getValue());
        }
    }

    /**
     * 删除注释和 空行配置项
     * @param oldLineNumMapItem
     * @param newLineNumMapItem
     * @param changeSets
     */
    private void deleteCommentAndBlankItem(Map<Integer, ItemDTO> oldLineNumMapItem,
            Map<Integer, String> newLineNumMapItem, ItemChangeSets changeSets) {

        for (Map.Entry<Integer, ItemDTO> entry : oldLineNumMapItem.entrySet()) {
            int lineNum = entry.getKey();
            ItemDTO oldItem = entry.getValue();
            String newItem = newLineNumMapItem.get(lineNum);

            // 添加到 ItemChangeSets 的删除项
            // 1. old is blank by now is not
            // 2.old is comment by now is not exist or modified
            if ((isBlankItem(oldItem) && !isBlankItem(newItem))  // 老的是空行配置项，新的不是空行配置项
            		// 老的是注释配置项，新的不相等
                    || isCommentItem(oldItem) && (newItem == null || !newItem.equals(oldItem.getComment()))) {
                changeSets.addDeleteItem(oldItem);
            }
        }
    }

    private ItemDTO buildCommentItem(Long id, Long namespaceId, String comment, int lineNum) {
        return buildNormalItem(id, namespaceId, "", "", comment, lineNum);
    }

    private ItemDTO buildBlankItem(Long id, Long namespaceId, int lineNum) {
        return buildNormalItem(id, namespaceId, "", "", "", lineNum);
    }

    /**
     * 构建Item信息
     * @param id
     * @param namespaceId
     * @param key
     * @param value
     * @param comment
     * @param lineNum
     * @return
     */
    private ItemDTO buildNormalItem(Long id, Long namespaceId, String key, String value, String comment, int lineNum) {
        ItemDTO item = new ItemDTO(key, value, comment, lineNum);
        item.setId(id);
        item.setNamespaceId(namespaceId);
        return item;
    }
}
