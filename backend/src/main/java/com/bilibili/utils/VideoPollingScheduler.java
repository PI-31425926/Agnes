package com.bilibili.utils;

import com.bilibili.handler.VideoWebSocketHandler;
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
import java.util.Map;

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

    @Autowired
    private VideoWebSocketHandler webSocketHandler;

    // 记录上次轮询时的状态，用于检测变化
    private final Map<String, String> lastKnownStatus = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> lastKnownProgress = new java.util.concurrent.ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 20000)
    public void pollVideoStatuses() {
        List<VideoTaskInfo> pendingTasks = taskManager.getPendingTasks();
        for (VideoTaskInfo task : pendingTasks) {
            try {
                if (task.getUserId() == null || task.getUserId().isBlank()) {
                    // 旧任务没有 userId，无法解密 apiKey，跳过并清理
                    taskManager.forceRemoveTask(task.getVideoId());
                    continue;
                }
                String apiKey = decryptApiKey(task.getUserId());
                AgnesVideoStatusResponse statusResp = agnesVideoService.queryVideoStatusWithApiKey(
                        task.getVideoId(), apiKey);
                String status = statusResp.getStatus();
                int progress = statusResp.getProgress();
                // 优先使用 download_url，其次 url，最后 remixed_from_video_id
                String url = statusResp.getDownloadUrl();
                if (url == null || url.isBlank()) url = statusResp.getUrl();
                if (url == null || url.isBlank()) url = statusResp.getRemixedFromVideoId();
                String error = statusResp.getError();
                taskManager.updateTask(task.getVideoId(), status, progress, url, error);

                // 检测状态变化并推送
                String prevStatus = lastKnownStatus.put(task.getVideoId(), status);
                Integer prevProgress = lastKnownProgress.put(task.getVideoId(), progress);

                if (prevStatus == null || !prevStatus.equals(status)) {
                    String eventType = "completed".equals(status) ? "video_completed" : "video_status";
                    VideoTaskInfo updatedTask = taskManager.getTaskById(task.getVideoId());
                    if (updatedTask != null) {
                        webSocketHandler.pushToUser(task.getUserId(), updatedTask, eventType);
                    }
                }
            } catch (Exception e) {
                System.err.println("轮询视频 " + task.getVideoId() + " 失败：" + e.getMessage());
            }
        }
    }

    private String decryptApiKey(String phone) throws Exception {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + phone));
        return aesUtil.decryptLegacy(user.getApiKey());
    }
}
