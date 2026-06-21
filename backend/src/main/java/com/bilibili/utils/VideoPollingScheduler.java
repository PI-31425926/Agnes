package com.bilibili.utils;

import com.bilibili.pojo.dto.AgnesVideoStatusResponse;
import com.bilibili.pojo.dto.VideoTaskInfo;
import com.bilibili.service.AgnesVideoService;
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

    @Scheduled(fixedDelay = 20000)
    public void pollVideoStatuses() {
        List<VideoTaskInfo> pendingTasks = taskManager.getPendingTasks();
        for (VideoTaskInfo task : pendingTasks) {
            try {
                // 使用任务中保存的明文 API 密钥，不依赖登录状态
                AgnesVideoStatusResponse statusResp = agnesVideoService.queryVideoStatusWithApiKey(
                        task.getVideoId(), task.getApiKey());
                String status = statusResp.getStatus();
                int progress = statusResp.getProgress();
                String url = statusResp.getUrl();
                String error = statusResp.getError();
                taskManager.updateTask(task.getVideoId(), status, progress, url, error);
            } catch (Exception e) {
                System.err.println("轮询视频 " + task.getVideoId() + " 失败：" + e.getMessage());
            }
        }
    }
}
