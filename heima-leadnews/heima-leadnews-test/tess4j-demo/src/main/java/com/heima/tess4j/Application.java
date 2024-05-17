package com.heima.tess4j;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import java.io.File;

public class Application
{

    public static void main(String[] args) {
        try {
            // 获取本地图片
            File file = new File("D:\\workspace-mine\\17-DarkHorseLeadNews\\heima-leadnews\\heima-leadnews-test\\tess4j-demo\\src\\main\\resources\\static\\test.png");

            // 创建Tesseract对象
            ITesseract tesseract = new Tesseract();
            // 设置字体库路径
            tesseract.setDatapath("D:\\workspace-mine\\17-DarkHorseLeadNews\\tessdata");
            // 中文识别
            tesseract.setLanguage("chi_sim");
            // 执行ocr识别
            String result = tesseract.doOCR(file);

            // 替换回车和tal键  使结果为一行
            result = result
                    .replaceAll("\\r|\\n", "-")
                    .replaceAll(" ", "");
            System.out.println("识别的结果为：" + result);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
