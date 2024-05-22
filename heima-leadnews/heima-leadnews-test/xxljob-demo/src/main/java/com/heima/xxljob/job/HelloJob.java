package com.heima.xxljob.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-22 10:42:24
 */
@Component
public class HelloJob
{

    @Value("${server.port}")
    private String port;

    @XxlJob("demoJobHandler")
    public void helloJob() {
        System.out.println("简单任务执行了。。。。" + port);

    }

    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() {
        // 分片的参数
        final int shardIndex = XxlJobHelper.getShardIndex();
        final int shardTotal = XxlJobHelper.getShardTotal();

        // 业务逻辑
        final List<Integer> list = getList();
        list.forEach(item -> {
            if (item % shardTotal == shardIndex) {
                System.out.println("我是" + shardIndex + "，我负责处理" + item);
            }
        });
    }

    public List<Integer> getList() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add(i);
        }
        return list;
    }
}
