package com.heima.article.listener;

import com.alibaba.fastjson.JSON;
import com.heima.article.service.ApArticleService;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-22 16:55:09
 */
@Component
@Slf4j
public class ArticleIncrHandleListener
{
    @Autowired
    private ApArticleService apArticleService;

    @KafkaListener(topics = HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC)
    public void onMessage(String message) {
        if (StringUtils.isNotBlank(message)) {
            log.info("收到消息：{}", message);
            apArticleService.updateScore(JSON.parseObject(message, ArticleVisitStreamMess.class));
        }
    }
}
