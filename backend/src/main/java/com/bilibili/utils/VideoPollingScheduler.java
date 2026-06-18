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

    // 每20秒执行一次
    /*@Scheduled(fixedDelay = 20000)
    public void pollVideoStatuses() {
        List<VideoTaskInfo> pendingTasks = taskManager.getPendingTasks();
        for (VideoTaskInfo task : pendingTasks) {
            try {
                AgnesVideoStatusResponse statusResp = agnesVideoService.queryVideoStatus(task.getVideoId());
                String status = statusResp.getStatus();
                int progress = statusResp.getProgress();
                String url = statusResp.getUrl();
                String error = statusResp.getError();
                taskManager.updateTask(task.getVideoId(), status, progress, url, error);
            } catch (Exception e) {
                // 记录日志，跳过该任务，避免一个失败影响其他
                System.err.println("轮询视频 " + task.getVideoId() + " 失败：" + e.getMessage());
            }
        }
        // 可选：定期清理已完成超过一定时间的任务，否则会一直堆积
        // taskManager.removeCompleted();
    }*/
}
