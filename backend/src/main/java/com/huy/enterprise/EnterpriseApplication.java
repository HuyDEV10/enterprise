package com.huy.enterprise;

import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnterpriseApplication {

    private static final String APPLICATION_TIME_ZONE = "Asia/Ho_Chi_Minh";

    static {
        System.setProperty("user.timezone", APPLICATION_TIME_ZONE);
        TimeZone.setDefault(
                TimeZone.getTimeZone(ZoneId.of(APPLICATION_TIME_ZONE)));
    }

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseApplication.class, args);
    }
}