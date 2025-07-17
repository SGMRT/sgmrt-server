package soma.ghostrunner.domain.auth.resolver.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.auth.resolver.AuthIdResolver;

@Component
@RequiredArgsConstructor
public class FirebaseUidResolver implements AuthIdResolver {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String resolveAuthId(String firebaseToken) {
        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(firebaseToken);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("Firebase ID 토큰 해석 실패 : " + firebaseToken);
        }
    }
}
