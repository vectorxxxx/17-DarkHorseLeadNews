package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.dtos.ArticleInfoDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService
{

    // 单页最大加载的数字
    private final static short MAX_PAGE_SIZE = 50;

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    /**
     * 根据参数加载文章列表
     *
     * @param loadtype 1为加载更多  2为加载最新
     * @param dto
     * @return
     */
    @Override
    public ResponseResult load(Short loadtype, ArticleHomeDto dto) {
        // 1.校验参数
        Integer size = dto.getSize();
        if (size == null || size == 0) {
            size = 10;
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        dto.setSize(size);

        // 类型参数检验
        if (!loadtype.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !loadtype.equals(ArticleConstants.LOADTYPE_LOAD_NEW)) {
            loadtype = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        // 文章频道校验
        if (StringUtils.isEmpty(dto.getTag())) {
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        // 时间校验
        if (dto.getMaxBehotTime() == null) {
            dto.setMaxBehotTime(new Date());
        }
        if (dto.getMinBehotTime() == null) {
            dto.setMinBehotTime(new Date());
        }

        // 2.查询数据
        List<ApArticle> apArticles = apArticleMapper.loadArticleList(dto, loadtype);

        // 3.结果封装
        return ResponseResult.okResult(apArticles);
    }

    @Override
    public ResponseResult load2(ArticleHomeDto dto, Short type, boolean firstPage) {
        if (firstPage) {
            final String jsonStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + dto.getTag());
            if (StringUtils.isNotBlank(jsonStr)) {
                final List<ApArticle> apArticleList = JSONObject.parseArray(jsonStr, ApArticle.class);
                return ResponseResult.okResult(apArticleList);
            }
        }

        return load(type, dto);
    }

    /**
     * 保存app端相关文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {
        // try {
        //     Thread.sleep(3000);
        // }
        // catch (InterruptedException e) {
        //     e.printStackTrace();
        // }

        // 1.检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);

        // 2.判断是否存在id
        if (dto.getId() == null) {
            // 2.1 不存在id  保存  文章  文章配置  文章内容

            // 保存文章
            save(apArticle);

            // 保存配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            // 保存 文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);

        }
        else {
            // 2.2 存在id   修改  文章  文章内容

            // 修改  文章
            updateById(apArticle);

            // 修改文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers
                    .<ApArticleContent>lambdaQuery()
                    .eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        // 异步调用 生成静态文件上传到minio中
        articleFreemarkerService.buildArticleToMinIO(apArticle, dto.getContent());

        // 3.结果返回  文章的id
        return ResponseResult.okResult(apArticle.getId());
    }

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult loadArticleBehavior(ArticleInfoDto dto) {

        // 0.检查参数
        if (dto == null || dto.getArticleId() == null || dto.getAuthorId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        // { "isfollow": true, "islike": true,"isunlike": false,"iscollection": true }
        boolean isfollow = false, islike = false, isunlike = false, iscollection = false;

        ApUser user = AppThreadLocalUtils.getUser();
        if (user != null) {
            // 喜欢行为
            String likeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + dto
                    .getArticleId()
                    .toString(), user
                    .getId()
                    .toString());
            if (StringUtils.isNotBlank(likeBehaviorJson)) {
                islike = true;
            }
            // 不喜欢的行为
            String unLikeBehaviorJson = (String) cacheService.hGet(BehaviorConstants.UN_LIKE_BEHAVIOR + dto
                    .getArticleId()
                    .toString(), user
                    .getId()
                    .toString());
            if (StringUtils.isNotBlank(unLikeBehaviorJson)) {
                isunlike = true;
            }
            // 是否收藏
            String collctionJson = (String) cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto
                    .getArticleId()
                    .toString());
            if (StringUtils.isNotBlank(collctionJson)) {
                iscollection = true;
            }

            // 是否关注
            Double score = cacheService.zScore(BehaviorConstants.APUSER_FOLLOW_RELATION + user.getId(), dto
                    .getAuthorId()
                    .toString());
            System.out.println(score);
            if (score != null) {
                isfollow = true;
            }

        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("isfollow", isfollow);
        resultMap.put("islike", islike);
        resultMap.put("isunlike", isunlike);
        resultMap.put("iscollection", iscollection);

        return ResponseResult.okResult(resultMap);
    }

    @Override
    public void updateScore(ArticleVisitStreamMess mess) {
        // 1.更新文章的阅读、点赞、收藏、评论的数量
        ApArticle apArticle = updateArticle(mess);

        // 2.计算文章的分值
        Integer score = computeScore(apArticle);
        score *= 3;

        // 3.替换当前文章对应频道的热点数据
        replaceDataToRedis(apArticle, score, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId());

        // 4.替换推荐对应的热点数据
        replaceDataToRedis(apArticle, score, ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
    }

    /**
     * 更新文章行为数量
     *
     * @param mess
     */
    private ApArticle updateArticle(ArticleVisitStreamMess mess) {
        log.info("articleId: {}", mess.getArticleId());
        final ApArticle apArticle = getById(mess.getArticleId());
        apArticle.setCollection(apArticle.getCollection() == null ?
                                0 :
                                apArticle.getCollection() + mess.getCollect());
        apArticle.setComment(apArticle.getComment() == null ?
                             0 :
                             apArticle.getComment() + mess.getComment());
        apArticle.setLikes(apArticle.getLikes() == null ?
                           0 :
                           apArticle.getLikes() + mess.getLike());
        apArticle.setViews(apArticle.getViews() == null ?
                           0 :
                           apArticle.getViews() + mess.getView());
        updateById(apArticle);
        return apArticle;
    }

    /**
     * 计算文章的具体分值
     *
     * @param apArticle
     * @return
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
     * 替换数据并且存入到redis
     *
     * @param apArticle
     * @param score
     * @param s
     */
    private void replaceDataToRedis(ApArticle apArticle, Integer score, String s) {
        final String articleListStr = cacheService.get(s);
        if (StringUtils.isEmpty(articleListStr)) {
            return;
        }

        List<HotArticleVo> hotArticleVoList = JSON.parseArray(articleListStr, HotArticleVo.class);

        //如果缓存中存在该文章，只更新分值
        boolean flag = true;
        for (HotArticleVo hotArticleVo : hotArticleVoList) {
            if (hotArticleVo
                    .getId()
                    .equals(apArticle.getId())) {
                hotArticleVo.setScore(score);
                flag = false;
                break;
            }
        }

        //如果缓存中不存在，查询缓存中分值最小的一条数据，进行分值的比较，如果当前文章的分值大于缓存中的数据，就替换
        if (flag) {
            if (hotArticleVoList.size() >= 30) {
                hotArticleVoList = hotArticleVoList
                        .stream()
                        .sorted(Comparator
                                .comparing(HotArticleVo::getScore)
                                .reversed())
                        .collect(Collectors.toList());
                final HotArticleVo lastHot = hotArticleVoList.get(hotArticleVoList.size() - 1);
                if (score > lastHot.getScore()) {
                    hotArticleVoList.remove(lastHot);
                    final HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(apArticle, hotArticleVo);
                    hotArticleVo.setScore(score);
                    hotArticleVoList.add(hotArticleVo);
                }
            }
            else {
                final HotArticleVo hotArticleVo = new HotArticleVo();
                BeanUtils.copyProperties(apArticle, hotArticleVo);
                hotArticleVo.setScore(score);
                hotArticleVoList.add(hotArticleVo);
            }
        }

        // 缓存
        hotArticleVoList = hotArticleVoList
                .stream()
                .sorted(Comparator
                        .comparing(HotArticleVo::getScore)
                        .reversed())
                .collect(Collectors.toList());
        cacheService.set(s, JSON.toJSONString(hotArticleVoList));
    }
}
