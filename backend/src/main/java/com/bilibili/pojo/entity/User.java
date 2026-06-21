package com.bilibili.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String phone;          // 手机号 = 用户名

    @Column(nullable = false)
    private String apiKey;         // 加密后的 API 密钥

    @Column(nullable = false)
    private String role = "USER";   // 角色：USER / ADMIN
}
