package soma.ghostrunner.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class FirebaseConfig {

  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(new ClassPathResource("firebase-private-key.json").getInputStream()))
        .build();
    if (FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.initializeApp(options);
    } else {
      return FirebaseApp.getInstance();
    }
  }

  @Bean
  public FirebaseAuth getFirebaseAuth(FirebaseApp app) {
    return FirebaseAuth.getInstance(app);
  }

}
