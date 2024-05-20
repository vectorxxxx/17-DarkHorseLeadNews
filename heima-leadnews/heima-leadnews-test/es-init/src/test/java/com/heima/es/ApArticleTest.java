package com.heima.es;

import com.alibaba.fastjson.JSONObject;
import com.heima.es.mapper.ApArticleMapper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ApArticleTest
{
    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 注意：数据量的导入，如果数据量过大，需要分页导入
     *
     * @throws Exception
     */
    @Test
    public void init() throws Exception {
        final BulkRequest bulkRequest = new BulkRequest("app_info_article");
        apArticleMapper
                // 1.查询所有符合条件的文章数据
                .loadArticleList()
                // 2.批量导入到es索引库
                .forEach(searchArticleVo -> {
                    final IndexRequest request = new IndexRequest()
                            .id(searchArticleVo
                                    .getId()
                                    .toString())
                            .source(JSONObject.toJSON(searchArticleVo), XContentType.JSON);
                    bulkRequest.add(request);
                });
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    }
}
