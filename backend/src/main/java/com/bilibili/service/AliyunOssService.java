package com.bilibili.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.bilibili.config.OssProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class AliyunOssService {

    @Autowired
    private OssProperties ossProperties;

    private OSS createOssClient() {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }

    /**
     * 上传图片到阿里云 OSS，返回 CNAME 公网 URL
     */
    public String uploadImage(MultipartFile file, String userId) throws IOException {
        String objectName = generateObjectName(userId, file.getOriginalFilename());

        OSS ossClient = createOssClient();
        try {
            com.aliyun.oss.model.ObjectMetadata metadata = new com.aliyun.oss.model.ObjectMetadata();
            metadata.setContentType(file.getContentType());
            ossClient.putObject(
                    ossProperties.getBucketName(),
                    objectName,
                    file.getInputStream(),
                    metadata
            );
        } finally {
            ossClient.shutdown();
        }

        // 使用 CNAME 域名作为公网 URL
        return ossProperties.getCnameUrl() + "/" + objectName;
    }

    /**
     * 生成唯一的 objectName
     * 格式: chat-images/{userId}/{timestamp}-{uuid}.ext
     */
    private String generateObjectName(String userId, String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "chat-images/" + userId + "/" + timestamp + "-" + uuid + ext;
    }
}
