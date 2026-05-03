package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class OutboundHttpConfig {

    @Bean("outboundHttpClient")
    public HttpClient outboundHttpClient(
            @Value("${outbound-http.connect-timeout-ms:5000}") int connectTimeoutMs
    ) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
