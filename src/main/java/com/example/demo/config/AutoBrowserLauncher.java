package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Component
@Order(2) // 1번이 끝나면 실행됨
public class AutoBrowserLauncher implements ApplicationRunner {

    @Value("${server.port}")
    private String serverPort;

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== [2단계] 브라우저 실행 시도 ==========");

        String url = "http://localhost:" + serverPort + "/game/";

        if (!GraphicsEnvironment.isHeadless()) {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec("xdg-open " + url);
                } else {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(url));
                    }
                }
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }
}