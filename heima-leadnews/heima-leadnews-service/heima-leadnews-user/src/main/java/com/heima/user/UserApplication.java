package com.heima.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author VectorX
 * @version V1.0
 * @description
 * @date 2024-05-11 14:57:21
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.heima.user.mapper")
@EnableSwagger2
public class UserApplication
{
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
