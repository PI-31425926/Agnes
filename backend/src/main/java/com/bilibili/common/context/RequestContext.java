package com.bilibili.common.context;

public class RequestContext {
    private static final InheritableThreadLocal<String> currentUser = new InheritableThreadLocal<>();

    public static void setCurrentUser(String phone) {
        currentUser.set(phone);
    }

    public static String getCurrentUser() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();
    }
}
