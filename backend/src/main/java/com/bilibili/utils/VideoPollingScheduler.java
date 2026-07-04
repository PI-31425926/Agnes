package com.bilibili.utils;

import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesVideoStatusResponse;
import com.bilibili.pojo.dto.VideoTaskInfo;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesVideoService;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
public class VideoPollingScheduler {

    @Autowired
    private AgnesVideoService agnesVideoService;

    @Autowired
    private VideoTaskManager taskManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Scheduled(fixedDelay = 20000)
    public void pollVideoStatuses() {
        List<VideoTaskInfo> pendingTasks = taskManager.getPendingTasks();
        for (VideoTaskInfo task : pendingTasks) {
            try {
                String apiKey = decryptApiKey(task.getUserId());
                AgnesVideoStatusResponse statusResp = agnesVideoService.queryVideoStatusWithApiKey(
                        task.getVideoId(), apiKey);
                String status = statusResp.getStatus();
                int progress = statusResp.getProgress();
                String url = statusResp.getDownloadUrl();
                String error = statusResp.getError();
                taskManager.updateTask(task.getVideoId(), status, progress, url, error);
            } catch (Exception e) {
                System.err.println("轮询视频 " + task.getVideoId() + " 失败：" + e.getMessage());
            }
        }
    }

    private String decryptApiKey(String phone) throws Exception {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + phone));
        return aesUtil.decrypt(user.getApiKey());
    }
}
