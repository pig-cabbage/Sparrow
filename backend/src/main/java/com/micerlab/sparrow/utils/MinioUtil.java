package com.micerlab.sparrow.utils;

import com.micerlab.sparrow.domain.ErrorCode;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class MinioUtil {
    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;
    @Value("${minio.bucketName}")
    private String bucketName;

    private MinioClient minioClient;

    @Autowired
    private FileUtil fileUtil;

    private static Logger logger = LoggerFactory.getLogger(FileUtil.class);

    public String getPutUrl(String objectName) {
        String url = null;
        try {
            minioClient = new MinioClient("http://" + endpoint, accessKey, secretKey);
            url = minioClient.presignedPutObject(bucketName, objectName);
        } catch (Exception e) {
            logger.error("minio获取上传url失败: "+ e.getMessage());
            throw new BusinessException(ErrorCode.SERVER_ERR_Minio);
        }
        return url;
    }

    public void deleteFile(String objectName){
        try {
            minioClient = new MinioClient("http://" + endpoint, accessKey, secretKey);
            minioClient.removeObject(bucketName, objectName);
        } catch (Exception e) {
            logger.error("minio删除文件" + objectName + "失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.SERVER_ERR_Minio);
        }
    }

    public void downloadFile(String fileName, String objectName, HttpServletResponse httpServletResponse){
        BufferedInputStream bufferedInputStream = null;
        OutputStream responseOutput = null;
        try {
            minioClient = new MinioClient("http://" + endpoint, accessKey, secretKey);
            bufferedInputStream = new BufferedInputStream(minioClient.getObject(bucketName, objectName));

            httpServletResponse.setCharacterEncoding("UTF-8");
            httpServletResponse.setContentType("application/octet-stream");
            String contentDisposition = "attachment; filename=" + fileName;
            httpServletResponse.setHeader("Content-Disposition", contentDisposition);
            responseOutput = httpServletResponse.getOutputStream();
            byte[] buff = new byte[1024];
            int length;
            while ((length = bufferedInputStream.read(buff)) != -1){
                responseOutput.write(buff,0,length);
                responseOutput.flush();
            }
        }catch (Exception e){
            logger.error("minio下载文件" + objectName + "失败: " + e.getMessage());
        }finally {
            if (bufferedInputStream != null){
                try {
                    bufferedInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (responseOutput != null){
                try {
                    responseOutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<String, Object> uploadThumbnail(File file){
        Map<String, Object> thumbnailInfo = new HashMap<>(2);
        try{
            minioClient = new MinioClient("http://" + endpoint, accessKey, secretKey);
            String path = fileUtil.getFileStorePath("jpg");
            String finalPath = path + "thumbnail/";
            String objectName = finalPath + file.getName().trim();
            InputStream inputStream = new FileInputStream(file);
            minioClient.putObject(bucketName, objectName, inputStream, "application/octet-stream");
            // url最大生效时间是7天
            String url = minioClient.presignedGetObject(bucketName, objectName, 60*60*24*7);

            thumbnailInfo.put("thumbnail_path", objectName);
            thumbnailInfo.put("thumbnail_url", url);

        }catch(Exception e){
            logger.error("上传缩略图失败: " + e.getMessage());
        }
        return thumbnailInfo;
    }

    public String updateGetThumbnaliUrl(String objectName){
        String url = null;
        try {
            minioClient = new MinioClient("http://" + endpoint, accessKey, secretKey);
            url = minioClient.presignedGetObject(bucketName, objectName, 60*60*24*7);
        }catch (Exception e){
            logger.error("更新缩略图url失败: " + e.getMessage());
        }
        return url;
    }
}
