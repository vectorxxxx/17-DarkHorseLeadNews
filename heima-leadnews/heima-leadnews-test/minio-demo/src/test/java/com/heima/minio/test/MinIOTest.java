package com.heima.minio.test;

import com.heima.file.service.FileStorageService;
import com.heima.minio.MinIOApplication;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@SpringBootTest(classes = MinIOApplication.class)
@RunWith(SpringRunner.class)
public class MinIOTest
{

    public static void main(String[] args) {
        final String file = "D:/workspace-mine/17-DarkHorseLeadNews/heima-leadnews/heima-leadnews-test/freemarker-demo/src/main/resources/templates/list.html";
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            // 1.创建minio链接客户端
            MinioClient minioClient = MinioClient
                    // 构建器
                    .builder()
                    // 账号密码
                    .credentials("minio", "minio123")
                    // 链接地址
                    .endpoint("http://192.168.56.17:9000")
                    .build();

            // 2.上传
            PutObjectArgs putObjectArgs = PutObjectArgs
                    // 构建器
                    .builder()
                    // 桶名
                    .bucket("leadnews")
                    // 文件名
                    .object("list.html")
                    // 文件类型
                    .contentType("text/html")
                    // 文件流
                    .stream(fileInputStream, fileInputStream.available(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);

            System.out.println("http://192.168.56.17:9000/leadnews/ak47.jpg");

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    public void testUpdateImgFile() {
        try {
            FileInputStream fileInputStream = new FileInputStream(
                    "D:/workspace-mine/17-DarkHorseLeadNews/heima-leadnews/heima-leadnews-test/minio-demo/src/main/resources/static/ak47.jpg");
            String filePath = fileStorageService.uploadImgFile("", "ak47.jpg", fileInputStream);
            System.out.println(filePath);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
