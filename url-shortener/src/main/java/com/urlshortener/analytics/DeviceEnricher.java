package com.urlshortener.analytics;

import org.springframework.stereotype.Component;

@Component
public class DeviceEnricher {

    public DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceInfo("UNKNOWN", "Unknown", "Unknown");
        }

        String ua = userAgent.toLowerCase();

        String deviceType = detectDevice(ua);
        String browser    = detectBrowser(ua);
        String os         = detectOs(ua);

        return new DeviceInfo(deviceType, browser, os);
    }

    private String detectDevice(String ua) {
        if (ua.contains("mobile") || ua.contains("android") && !ua.contains("tablet")) return "MOBILE";
        if (ua.contains("tablet") || ua.contains("ipad")) return "TABLET";
        return "DESKTOP";
    }

    private String detectBrowser(String ua) {
        if (ua.contains("edg/"))     return "Edge";
        if (ua.contains("chrome"))   return "Chrome";
        if (ua.contains("firefox"))  return "Firefox";
        if (ua.contains("safari"))   return "Safari";
        if (ua.contains("opera"))    return "Opera";
        return "Other";
    }

    private String detectOs(String ua) {
        if (ua.contains("windows nt")) return "Windows";
        if (ua.contains("mac os x"))   return "macOS";
        if (ua.contains("android"))    return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("linux"))      return "Linux";
        return "Other";
    }

    public record DeviceInfo(String deviceType, String browser, String os) {}
}
