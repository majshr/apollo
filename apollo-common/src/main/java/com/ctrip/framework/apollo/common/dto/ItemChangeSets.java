package com.ctrip.framework.apollo.common.dto;

import java.util.LinkedList;
import java.util.List;

/**
 * 配置信息改变封装类, 记录了新建, 更新, 删除的配置信息<br>
 *  storage cud result
 */
public class ItemChangeSets extends BaseDTO {

	private List<ItemDTO> createItems = new LinkedList<>();
	private List<ItemDTO> updateItems = new LinkedList<>();
	private List<ItemDTO> deleteItems = new LinkedList<>();

	public void addCreateItem(ItemDTO item) {
		createItems.add(item);
	}

	public void addUpdateItem(ItemDTO item) {
		updateItems.add(item);
	}

	public void addDeleteItem(ItemDTO item) {
		deleteItems.add(item);
	}

	public boolean isEmpty() {
		return createItems.isEmpty() && updateItems.isEmpty() && deleteItems.isEmpty();
	}

	public List<ItemDTO> getCreateItems() {
		return createItems;
	}

	public List<ItemDTO> getUpdateItems() {
		return updateItems;
	}

	public List<ItemDTO> getDeleteItems() {
		return deleteItems;
	}

	public void setCreateItems(List<ItemDTO> createItems) {
		this.createItems = createItems;
	}

	public void setUpdateItems(List<ItemDTO> updateItems) {
		this.updateItems = updateItems;
	}

	public void setDeleteItems(List<ItemDTO> deleteItems) {
		this.deleteItems = deleteItems;
	}

}
