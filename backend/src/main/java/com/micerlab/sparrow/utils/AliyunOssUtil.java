package com.micerlab.sparrow.utils;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AliyunOssUtil {

    @Value("${oss.endpoint}")
    private String endpoint;
    @Value("${oss.accessKeyId}")
    private String accessKeyId;
    @Value("${oss.accessKeySecret}")
    private String accessKeySecret;
    @Value("${oss.bucketName}")
    private String bucketName;

    @Autowired
    private FileUtil fileUtil;

    public Map<String, String> getPolicy(Map<String, Object> params){
        Map<String, String> respMap = new LinkedHashMap<>();

        // 文件扩展名
        String suffix = (String) params.get("ext");
        // OSS存储路径
        String dir = fileUtil.getFileStorePath(suffix);

        String host = "http://" + bucketName + "." + endpoint;
        OSSClient client = new OSSClient(endpoint, accessKeyId, accessKeySecret);
        try {
            long expireTime = 30000;
            long expireEndTime = System.currentTimeMillis() + expireTime * 1000;
            Date expiration = new Date(expireEndTime);
            PolicyConditions policyConds = new PolicyConditions();
            policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, 1048576000);
            policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

            String postPolicy = client.generatePostPolicy(expiration, policyConds);
            byte[] binaryData = postPolicy.getBytes("utf-8");
            String encodedPolicy = BinaryUtil.toBase64String(binaryData);
            String postSignature = client.calculatePostSignature(postPolicy);

            respMap.put("accessKey", accessKeyId);
            respMap.put("policy", encodedPolicy);
            respMap.put("signature", postSignature);
            respMap.put("dir", dir);
            respMap.put("host", host);
            respMap.put("expire", String.valueOf(expireEndTime / 1000));
            // respMap.put("expire", formatISO8601Date(expiration));
        } catch (Exception e) {
            e.printStackTrace();
        }
        client.shutdown();
        return respMap;
    }

    public String getPutUrl(String objectName1){
        // Endpoint以杭州为例，其它Region请按实际情况填写。
        String endpoint = "http://oss-cn-beijing.aliyuncs.com";
        // 阿里云主账号AccessKey拥有所有API的访问权限，风险很高。强烈建议您创建并使用RAM账号进行API访问或日常运维，请登录 https://ram.console.aliyun.com 创建RAM账号。
        String accessKeyId = "LTAInhMpeCqDsM4c";
        String accessKeySecret = "RXxpqMZIlo7bwTIbX3MiAGgxdKX4v7";
        String bucketName = "douban-test";
        String objectName = "image/a.jpg";

        // 创建OSSClient实例。
        OSSClient ossClient = new OSSClient(endpoint, accessKeyId, accessKeySecret);

        try {
            // 生成签名URL。
            Date expiration = new Date(new Date().getTime() + 3600 * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
            // 设置过期时间。
            request.setExpiration(expiration);
            // 设置Content-Type。
            request.setContentType("application/octet-stream");
            // 添加用户自定义元信息。
            request.addUserMetadata("author", "aliy");
            // 生成签名URL（HTTP PUT请求）。
            URL signedUrl = ossClient.generatePresignedUrl(request);
            System.out.println("signed url for putObject: " + signedUrl);
            return signedUrl.toString();
        }catch (Exception e){
            e.printStackTrace();
        }

        // 关闭OSSClient。
        ossClient.shutdown();
        return null;
    }

    public void deleteFile(String objectName){
        OSSClient ossClient = new OSSClient(endpoint,accessKeyId,accessKeySecret);
        ossClient.deleteObject(bucketName,objectName);
        ossClient.shutdown();
    }

    public void downloadFile(String fileName, String objectName, HttpServletResponse httpServletResponse){
        OSSClient ossClient = new OSSClient(endpoint,accessKeyId,accessKeySecret);
        OSSObject ossObject = ossClient.getObject(bucketName,objectName);

        BufferedInputStream bufferedInputStream = null;
        OutputStream responseOutput = null;
        try {
            httpServletResponse.setCharacterEncoding("UTF-8");
            httpServletResponse.setContentType("application/octet-stream");
            String contentDisposition = "attachment; filename=" + fileName;
            httpServletResponse.setHeader("Content-Disposition", contentDisposition);

            responseOutput = httpServletResponse.getOutputStream();
            byte[] buff = new byte[1024];
            bufferedInputStream = new BufferedInputStream(ossObject.getObjectContent());

            int length;
            while ((length = bufferedInputStream.read(buff)) != -1){
                responseOutput.write(buff,0,length);
                responseOutput.flush();
            }

        } catch (UnsupportedEncodingException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ossClient != null){
                ossClient.shutdown();
            }

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

    public Map<String, Object> uploadThumbnail(File file) {
        Map<String, Object> thumbnailInfo = new HashMap<>(2);
        try {
            OSSClient ossClient = new OSSClient(endpoint, accessKeyId, accessKeySecret);

            String path = fileUtil.getFileStorePath("jpg");
            String finalPath = "";
            String[] strings = path.split("/");
            for (int i = 0; i < strings.length - 1; i++) {
                finalPath += strings[i] + "/";
            }
            finalPath += "thumbnail/";

            //        String objectName = "admin/allen/thumbnail/" + file.getName().trim();
            String objectName = finalPath + file.getName().trim();

            InputStream inputStream = new FileInputStream(file);
            ossClient.putObject(bucketName, objectName, inputStream);

            String url = "";
            Date expiration = new Date(System.currentTimeMillis() + 3600L * 1000 * 24 * 365 * 100);
            url = ossClient.generatePresignedUrl(bucketName, objectName, expiration).toString();

            ossClient.shutdown();

            thumbnailInfo.put("thumbnail_path", objectName);
            thumbnailInfo.put("thumbnail_url", url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return thumbnailInfo;
    }
}
