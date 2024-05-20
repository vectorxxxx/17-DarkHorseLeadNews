package com.heima.search.listener;

import com.alibaba.fastjson.JSONObject;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.search.vos.SearchArticleVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SyncArticleListener
{

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @KafkaListener(topics = ArticleConstants.ARTICLE_ES_SYNC_TOPIC)
    public void onMessage(String message) {
        if (StringUtils.isNotBlank(message)) {

            log.info("SyncArticleListener,message={}", message);

            final SearchArticleVo searchArticleVo = JSONObject.parseObject(message, SearchArticleVo.class);
            final IndexRequest indexRequest = new IndexRequest("app_info_article")
                    .id(searchArticleVo
                            .getId()
                            .toString())
                    .source(message, XContentType.JSON);

            try {
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            }
            catch (IOException e) {
                log.error("sync es error={}", e.getMessage(), e);
            }
        }

    }
}
