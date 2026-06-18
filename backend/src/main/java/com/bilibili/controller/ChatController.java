package com.bilibili.controller;

import com.bilibili.pojo.dto.ChatRequest;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.service.AgnesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgnesService agnesService;

    public ChatController(AgnesService agnesService) {
        this.agnesService = agnesService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = agnesService.chat(request.getMessage());
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return response;
    }
}
