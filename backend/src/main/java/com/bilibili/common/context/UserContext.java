package com.bilibili.common.context;

public class UserContext {
    public static ThreadLocal<String> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(String phone) {
        threadLocal.set(phone);
    }

    public static String getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }
}
