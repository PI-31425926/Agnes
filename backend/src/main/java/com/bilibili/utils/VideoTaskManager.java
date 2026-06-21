package com.bilibili.utils;

import com.bilibili.pojo.dto.VideoTaskInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class VideoTaskManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final long VIDEO_TTL_MINUTES = 30;
    private static final int MAX_VIDEOS_PER_USER = 5;
    private static final String VIDEO_PREFIX = "video:";
    private static final String USER_VIDEOS_PREFIX = "user:";
    private static final String USER_VIDEOS_SUFFIX = ":videos";
    private static final String PENDING_SET_KEY = "pending:videos";

    /**
     * 添加新任务
     */
    public void addTask(VideoTaskInfo task) {
        String userId = task.getUserId();
        String videoId = task.getVideoId();
        String userVideosKey = USER_VIDEOS_PREFIX + userId + USER_VIDEOS_SUFFIX;

        // 检查用户已有视频数量
        Long count = redisTemplate.opsForSet().size(userVideosKey);
        if (count != null && count >= MAX_VIDEOS_PER_USER) {
            throw new RuntimeException("每个用户最多同时拥有" + MAX_VIDEOS_PER_USER + "个视频任务，请等待已有任务完成或手动删除");
        }

        // 存入任务数据
        redisTemplate.opsForValue().set(VIDEO_PREFIX + videoId, task);
        redisTemplate.expire(VIDEO_PREFIX + videoId, Duration.ofMinutes(VIDEO_TTL_MINUTES));

        // 关联到用户
        redisTemplate.opsForSet().add(userVideosKey, videoId);
        // 用户集合也设置 TTL，以便无任务时自动清理（不强制）
        redisTemplate.expire(userVideosKey, Duration.ofMinutes(VIDEO_TTL_MINUTES * 2));

        // 加入待处理集合
        redisTemplate.opsForSet().add(PENDING_SET_KEY, videoId);
    }

    /**
     * 更新任务状态（进度、状态、结果等）
     */
    public void updateTask(String videoId, String status, int progress, String url, String error) {
        String key = VIDEO_PREFIX + videoId;
        VideoTaskInfo task = (VideoTaskInfo) redisTemplate.opsForValue().get(key);
        if (task == null) return;

        task.setStatus(status);
        task.setProgress(progress);
        if (url != null) task.setUrl(url);
        if (error != null) task.setError(error);

        // 更新并重置 TTL（可选，保持过期时间延续）
        redisTemplate.opsForValue().set(key, task);
        redisTemplate.expire(key, Duration.ofMinutes(VIDEO_TTL_MINUTES));

        // 如果任务完成或失败，从待处理集合中移除
        if ("completed".equals(status) || "failed".equals(status)) {
            redisTemplate.opsForSet().remove(PENDING_SET_KEY, videoId);
        }
    }

    /**
     * 获取所有未完成的任务（用于轮询）
     */
    public List<VideoTaskInfo> getPendingTasks() {
        Set<Object> pendingIds = redisTemplate.opsForSet().members(PENDING_SET_KEY);
        if (pendingIds == null || pendingIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<VideoTaskInfo> tasks = new ArrayList<>();
        for (Object obj : pendingIds) {
            String videoId = obj.toString();
            VideoTaskInfo task = (VideoTaskInfo) redisTemplate.opsForValue().get(VIDEO_PREFIX + videoId);
            if (task != null) {
                if (!"completed".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
                    tasks.add(task);
                } else {
                    // 状态已变更但未从集合移除，清理
                    redisTemplate.opsForSet().remove(PENDING_SET_KEY, videoId);
                }
            } else {
                // 任务 Key 已过期，从集合中移除
                redisTemplate.opsForSet().remove(PENDING_SET_KEY, videoId);
            }
        }
        return tasks;
    }

    /**
     * 获取指定用户的所有任务（含已完成和失败）
     */
    public List<VideoTaskInfo> getTasksByUser(String userId) {
        String userVideosKey = USER_VIDEOS_PREFIX + userId + USER_VIDEOS_SUFFIX;
        Set<Object> videoIds = redisTemplate.opsForSet().members(userVideosKey);
        if (videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<VideoTaskInfo> tasks = new ArrayList<>();
        for (Object obj : videoIds) {
            String videoId = obj.toString();
            VideoTaskInfo task = (VideoTaskInfo) redisTemplate.opsForValue().get(VIDEO_PREFIX + videoId);
            if (task != null) {
                tasks.add(task);
            } else {
                // 任务已过期，清理用户集合中的引用
                redisTemplate.opsForSet().remove(userVideosKey, videoId);
            }
        }
        return tasks;
    }

    /**
     * 删除指定任务（需确认归属）
     */
    public boolean removeTask(String videoId, String userId) {
        String key = VIDEO_PREFIX + videoId;
        VideoTaskInfo task = (VideoTaskInfo) redisTemplate.opsForValue().get(key);
        if (task == null || !userId.equals(task.getUserId())) {
            return false;
        }

        // 删除任务数据
        redisTemplate.delete(key);

        // 从用户集合和待处理集合中移除
        String userVideosKey = USER_VIDEOS_PREFIX + userId + USER_VIDEOS_SUFFIX;
        redisTemplate.opsForSet().remove(userVideosKey, videoId);
        redisTemplate.opsForSet().remove(PENDING_SET_KEY, videoId);

        return true;
    }
}