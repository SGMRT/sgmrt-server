package soma.ghostrunner.global.config;

import com.niamedtech.expo.exposerversdk.ExpoPushNotificationClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExpoPushConfig {

    @Bean
    public ExpoPushNotificationClient expoPushNotificationClient(CloseableHttpClient httpClient) {
        return ExpoPushNotificationClient.builder()
                .setHttpClient(httpClient)
                .build();
    }

}
