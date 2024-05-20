package com.heima.search.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.search.dtos.HistorySearchDto;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-20 15:13:20
 */
public interface ApUserSearchService
{

    /**
     * 保存用户搜索历史记录
     *
     * @param keyword
     * @param userId
     */
    void insert(String keyword, Integer userId);

    /**
     * 查询搜索历史
     *
     * @return
     */
    ResponseResult findUserSearch();

    /**
     * 删除搜索历史
     *
     * @param historySearchDto
     * @return
     */
    ResponseResult delUserSearch(HistorySearchDto historySearchDto);
}
