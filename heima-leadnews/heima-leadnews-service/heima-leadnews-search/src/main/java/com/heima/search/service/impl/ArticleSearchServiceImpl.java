package com.heima.search.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.service.ApUserSearchService;
import com.heima.search.service.ArticleSearchService;
import com.heima.utils.thread.AppThreadLocalUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleSearchServiceImpl implements ArticleSearchService
{

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ApUserSearchService apUserSearchService;

    /**
     * es文章分页检索
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult search(UserSearchDto dto) throws IOException {

        // 1.检查参数
        if (StringUtils.isEmpty(dto.getSearchWords())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        final ApUser user = AppThreadLocalUtils.getUser();
        if (user != null && dto.getFromIndex() == 0) {
            apUserSearchService.insert(dto.getSearchWords(), user.getId());
        }

        // 2.设置查询条件
        final SearchRequest searchRequest = new SearchRequest("app_info_article").source(new SearchSourceBuilder()
                // 条件查询
                .query(QueryBuilders
                        // 布尔查询
                        .boolQuery()
                        // 关键字的分词之后查询
                        .must(QueryBuilders
                                .queryStringQuery(dto.getSearchWords())
                                .field("title")
                                .field("content")
                                .defaultOperator(Operator.OR))
                        // 查询小于mindate的数据
                        .filter(QueryBuilders
                                .rangeQuery("publishTime")
                                .lt(dto
                                        .getMinBehotTime()
                                        .getTime())))
                // 分页查询
                .from(0)
                .size(dto.getPageSize())
                // 按照发布时间倒序查询
                .sort("publishTime", SortOrder.DESC)
                // 设置高亮 Title
                .highlighter(new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color: red; font-size: inherit;'>")
                        .postTags("</font>")));
        final SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        // 3.结果封装返回
        final List<Map> list = Arrays
                .stream(searchResponse
                        .getHits()
                        .getHits())
                .map(hit -> {
                    final Map map = JSONObject.parseObject(hit.getSourceAsString(), Map.class);
                    final Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                    if (highlightFields != null && !highlightFields.isEmpty()) {
                        map.put("h_title", StringUtils.join(highlightFields
                                .get("title")
                                .getFragments()));
                    }
                    else {
                        map.put("h_title", map.get("title"));
                    }
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseResult.okResult(list);

    }
}
