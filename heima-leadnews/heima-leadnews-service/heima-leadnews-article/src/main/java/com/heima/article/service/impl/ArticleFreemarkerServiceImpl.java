package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import freemarker.template.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Transactional
public class ArticleFreemarkerServiceImpl implements ArticleFreemarkerService
{

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ApArticleService apArticleService;

    /**
     * 生成静态文件上传到minIO中
     *
     * @param apArticle
     * @param content
     */
    // 异步方法
    @Async
    @Override
    public void buildArticleToMinIO(ApArticle apArticle, String content) {
        // 已知文章的id
        // 4.1 获取文章内容
        if (StringUtils.isBlank(content)) {
            log.error("文章内容为空");
            return;
        }

        // 4.2 文章内容通过freemarker生成html文件
        StringWriter out = new StringWriter();
        try {
            // 数据模型
            Map<String, Object> contentDataModel = new HashMap<>();
            contentDataModel.put("content", JSONArray.parseArray(content));
            // 合成
            configuration
                    .getTemplate("article.ftl")
                    .process(contentDataModel, out);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // 4.3 把html文件上传到minio中
        InputStream in = new ByteArrayInputStream(out
                .toString()
                .getBytes());
        String path = fileStorageService.uploadHtmlFile("", apArticle.getId() + ".html", in);

        // 4.4 修改ap_article表，保存static_url字段
        apArticleService.update(Wrappers
                .<ApArticle>lambdaUpdate()
                .eq(ApArticle::getId, apArticle.getId())
                .set(ApArticle::getStaticUrl, path));
    }

}