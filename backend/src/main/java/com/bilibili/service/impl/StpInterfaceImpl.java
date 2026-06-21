package com.bilibili.service.impl;

import cn.dev33.satoken.stp.StpInterface;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 暂不使用权限，返回空列表
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String phone = (String) loginId;
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user != null) {
            return Collections.singletonList(user.getRole());
        }
        return Collections.emptyList();
    }
}