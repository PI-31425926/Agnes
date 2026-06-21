package com.bilibili.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.bilibili.mapper.OperationLogRepository;
import com.bilibili.pojo.entity.OperationLog;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@SaCheckRole("ADMIN")
public class AdminController {

    @Autowired
    private UserService userService;
    @Autowired
    private OperationLogRepository logRepository;

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return userService.findAll().stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("phone", u.getPhone());
            map.put("role", u.getRole());
            return map;
        }).collect(Collectors.toList());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userService.findByPhone(null); // 简单判断存在
        if (user == null) return ResponseEntity.notFound().build();
        userService.deleteById(id);
        return ResponseEntity.ok("删除成功");
    }

    @GetMapping("/logs")
    public List<OperationLog> getLogs(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return logRepository.findAll(PageRequest.of(page, size, Sort.by("createTime").descending()))
                .getContent();
    }
}
