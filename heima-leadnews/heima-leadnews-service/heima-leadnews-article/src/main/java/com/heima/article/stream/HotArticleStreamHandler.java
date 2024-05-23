package com.heima.article.stream;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.mess.UpdateArticleMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-22 16:12:49
 */
@Configuration
@Slf4j
public class HotArticleStreamHandler
{
    @Bean
    public KStream<String, String> kStream(StreamsBuilder streamsBuilder) {
        // 接收消息
        KStream<String, String> stream = streamsBuilder.stream(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC);

        // 聚合流式处理
        stream.map((key, value) -> {
            final UpdateArticleMess mess = JSONObject.parseObject(value, UpdateArticleMess.class);
            return new KeyValue<>(mess
                    .getArticleId()
                    .toString(), mess
                    .getType()
                    .name()
                    .concat(":")
                    .concat(String.valueOf(mess.getAdd())));
        })
              // 按照文章ID进行聚合
              .groupBy((key, value) -> key)
              // 时间窗口
              .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))
              // 聚合计算
              .aggregate(() -> "COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0", (key, value, aggValue) -> {
                  if (StringUtils.isBlank(value)) {
                      return aggValue;
                  }

                  BigDecimal col = BigDecimal.ZERO;
                  BigDecimal com = BigDecimal.ZERO;
                  BigDecimal lik = BigDecimal.ZERO;
                  BigDecimal vie = BigDecimal.ZERO;
                  for (String agg : aggValue.split(",")) {
                      String[] split = agg.split(":");
                      /**
                       * 获得初始值，也是时间窗口内计算之后的值
                       */
                      switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])) {
                          case COLLECTION:
                              col = BigDecimal.valueOf(Integer.parseInt(split[1]));
                              break;
                          case COMMENT:
                              com = BigDecimal.valueOf(Integer.parseInt(split[1]));
                              break;
                          case LIKES:
                              lik = BigDecimal.valueOf(Integer.parseInt(split[1]));
                              break;
                          case VIEWS:
                              vie = BigDecimal.valueOf(Integer.parseInt(split[1]));
                              break;
                      }
                  }
                  /**
                   * 累加操作
                   */
                  String[] valAry = value.split(":");
                  final String val = StringUtils.isBlank(valAry[1]) ?
                                     "0" :
                                     "null".equalsIgnoreCase(valAry[1]) ?
                                     "0" :
                                     valAry[1];
                  switch (UpdateArticleMess.UpdateArticleType.valueOf(valAry[0])) {
                      case COLLECTION:
                          col = col.add(BigDecimal.valueOf(Integer.parseInt(val)));
                          break;
                      case COMMENT:
                          com = com.add(BigDecimal.valueOf(Integer.parseInt(val)));
                          break;
                      case LIKES:
                          lik = lik.add(BigDecimal.valueOf(Integer.parseInt(val)));
                          break;
                      case VIEWS:
                          vie = vie.add(BigDecimal.valueOf(Integer.parseInt(val)));
                          break;
                  }
                  String formatStr = String.format("COLLECTION:%d,COMMENT:%d,LIKES:%d,VIEWS:%d", col.intValue(), com.intValue(), lik.intValue(), vie.intValue());
                  System.out.println("文章的id:" + key);
                  System.out.println("当前时间窗口内的消息处理结果：" + formatStr);
                  return formatStr;
              }, Materialized.as("hot-atricle-stream-count-001"))
              .toStream()
              .map((key, value) -> new KeyValue<>(key.key(), formatObj(key.key(), value)))
              // 发送消息
              .to(HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC);

        return stream;
    }

    /**
     * 格式化消息的value数据
     *
     * @param articleId
     * @param value
     * @return
     */
    private String formatObj(String articleId, String value) {
        final ArticleVisitStreamMess mess = new ArticleVisitStreamMess();
        mess.setArticleId(Long.valueOf(articleId));
        // COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0
        for (String val : value.split(",")) {
            final String[] split = val.split(":");
            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])) {
                case COLLECTION:
                    mess.setCollect(Integer.parseInt(split[1]));
                    break;
                case COMMENT:
                    mess.setComment(Integer.parseInt(split[1]));
                    break;
                case LIKES:
                    mess.setLike(Integer.parseInt(split[1]));
                    break;
                case VIEWS:
                    mess.setView(Integer.parseInt(split[1]));
                    break;
            }
        }
        log.info("聚合消息处理之后的结果为:{}", JSON.toJSONString(mess));
        return JSON.toJSONString(mess);
    }
}
