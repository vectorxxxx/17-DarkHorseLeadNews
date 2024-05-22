package com.heima.article.service.impl.impl;

import com.alibaba.fastjson.JSONObject;
import com.heima.apis.wemedia.IWemediaClient;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.HotArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-22 11:25:30
 */
@Service
@Slf4j
@Transactional
public class HotArticleServiceImpl implements HotArticleService
{

    @Autowired
    private ApArticleMapper apArticleMapper;

    /**
     * 计算热点文章
     */
    @Override
    public void computeHotArticle() {
        // 1.查询前5天的文章数据
        final Date dayParam = DateTime
                .now()
                .minusDays(2000)
                .toDate();
        final List<ApArticle> apArticleList = apArticleMapper.findArticleListByLast5days(dayParam);
        log.info("查询前5天的文章数据:{}", apArticleList);

        // 2.计算文章的分值
        List<HotArticleVo> hotArticleVoList = computeHotArticle(apArticleList);
        log.info("计算文章的分值:{}", hotArticleVoList);

        // 3.为每个频道缓存30条分值较高的文章
        cacheTagToRedis(hotArticleVoList);
        log.info("为每个频道缓存30条分值较高的文章");
    }

    @Autowired
    private IWemediaClient wemediaClient;

    @Autowired
    private CacheService cacheService;

    /**
     * 计算热点文章
     *
     * @param apArticleList 文章列表
     * @return {@link List }<{@link HotArticleVo }>
     */
    private List<HotArticleVo> computeHotArticle(List<ApArticle> apArticleList) {
        return apArticleList
                .stream()
                .map(apArticle -> {
                    final HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(apArticle, hotArticleVo);
                    hotArticleVo.setScore(computeScore(apArticle));
                    return hotArticleVo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算分数
     *
     * @param apArticle 文章
     * @return {@link Integer }
     */
    private Integer computeScore(ApArticle apArticle) {
        BigDecimal score = BigDecimal.ZERO;
        if (apArticle.getLikes() != null) {
            score = score.add(BigDecimal
                    .valueOf(apArticle.getLikes())
                    .multiply(BigDecimal.valueOf(ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT)));
        }
        if (apArticle.getViews() != null) {
            score = score.add(BigDecimal.valueOf(apArticle.getViews()));
        }
        if (apArticle.getCollection() != null) {
            score = score.add(BigDecimal
                    .valueOf(apArticle.getCollection())
                    .multiply(BigDecimal.valueOf(ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT)));
        }
        if (apArticle.getComment() != null) {
            score = score.add(BigDecimal
                    .valueOf(apArticle.getComment())
                    .multiply(BigDecimal.valueOf(ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT)));
        }
        return score.intValue();
    }

    /**
     * 缓存文章到 Redis
     *
     * @param hotArticleVoList 热门文章列表
     */
    private void cacheTagToRedis(List<HotArticleVo> hotArticleVoList) {
        // 每个频道缓存30条分值较高的文章
        final ResponseResult responseResult = wemediaClient.getChannels();
        if (responseResult
                .getCode()
                .equals(200)) {
            final List<WmChannel> channelList = JSONObject.parseArray(JSONObject.toJSONString(responseResult.getData()), WmChannel.class);
            channelList.forEach(wmChannel -> {
                final List<HotArticleVo> articleVoList = hotArticleVoList
                        .stream()
                        .filter(hotArticleVo -> hotArticleVo
                                .getChannelId()
                                .equals(wmChannel.getId()))
                        .collect(Collectors.toList());
                // 给文章进行排序，取30条分值较高的文章存入redis  key：频道id   value：30条分值较高的文章
                sortAndCache(articleVoList, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + wmChannel.getId());
            });
        }

        // 设置推荐数据
        // 给文章进行排序，取30条分值较高的文章存入redis
        // key：频道id   value：30条分值较高的文章
        sortAndCache(hotArticleVoList, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
    }

    /**
     * 排序并且缓存数据
     *
     * @param articleVoList articleVoList
     * @param key           key
     */
    private void sortAndCache(List<HotArticleVo> articleVoList, String key) {
        articleVoList = articleVoList
                .stream()
                .sorted(Comparator
                        .comparing(HotArticleVo::getScore)
                        .reversed())
                .limit(30)
                .collect(Collectors.toList());
        log.info("缓存文章到 Redis:{}", articleVoList);
        cacheService.set(key, JSONObject.toJSONString(articleVoList));
    }

}
