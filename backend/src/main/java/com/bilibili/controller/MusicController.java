package com.bilibili.controller;

import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.dto.MusicRequest;
import com.bilibili.pojo.dto.MusicResponse;
import com.bilibili.service.MusicService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicService musicService;

    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }

    @PostMapping("/generate")
    public ApiResponse<MusicResponse> generate(@RequestBody MusicRequest request) throws Exception {
        MusicResponse response = musicService.generate(request);
        return ApiResponse.success(response);
    }
}
