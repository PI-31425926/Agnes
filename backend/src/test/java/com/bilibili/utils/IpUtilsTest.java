package com.bilibili.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpUtilsTest {

    @Test
    void getClientIpDirectConnection() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        assertEquals("192.168.1.100", IpUtils.getClientIp(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1, 10.0.0.2, 192.168.1.1", "10.0.0.1"})
    void getClientIpXForwardedFor(String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        assertEquals("10.0.0.1", IpUtils.getClientIp(request));
    }

    @Test
    void getClientIpFallsBackToRealIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.50");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        assertEquals("10.0.0.50", IpUtils.getClientIp(request));
    }

    @Test
    void getClientIpTreatsUnknownAsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.99");

        assertEquals("10.0.0.99", IpUtils.getClientIp(request));
    }
}
