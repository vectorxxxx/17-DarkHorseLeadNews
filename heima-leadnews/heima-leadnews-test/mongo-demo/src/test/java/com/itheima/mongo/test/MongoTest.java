package com.itheima.mongo.test;

import com.itheima.mongo.MongoApplication;
import com.itheima.mongo.pojo.ApAssociateWords;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-20 14:18:56
 */
@SpringBootTest(classes = MongoApplication.class)
@RunWith(SpringRunner.class)
public class MongoTest
{
    @Autowired
    private MongoTemplate mongoTemplate;

    //保存
    @Test
    public void saveTest() {
        final ApAssociateWords apAssociateWords = new ApAssociateWords();
        apAssociateWords.setAssociateWords("黑马直播");
        apAssociateWords.setCreatedTime(new Date());
        mongoTemplate.save(apAssociateWords);
    }

    //查询一个
    @Test
    public void selectById() {
        ApAssociateWords apAssociateWords = mongoTemplate.findById("664af598de0e9a20c2ed1bb1", ApAssociateWords.class);
        System.out.println(apAssociateWords);
    }

    //条件查询
    @Test
    public void testQuery() {
        final List<ApAssociateWords> apAssociateWords = mongoTemplate.find(Query
                .query(Criteria
                        .where("associateWords")
                        .is("黑马头条"))
                .with(Sort.by(Sort.Direction.DESC, "createdTime")), ApAssociateWords.class);
        System.out.println(apAssociateWords);
    }

    @Test
    public void testDel() {
        mongoTemplate.remove(Query.query(Criteria
                .where("associateWords")
                .is("黑马头条")), ApAssociateWords.class);
    }
}
