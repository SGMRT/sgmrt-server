package soma.ghostrunner.domain.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    // 로그인 -> fb id token 기반
    @PostMapping("/firebase-sign-in")
    public Object firebaseSignIn(
            @RequestHeader("Authorization") String authorizationHeader) {
        return authService.signIn(authorizationHeader);
    }


    // 회원가입 -> 입력한 정보를 받아서 회원 삽입
    @PostMapping("/firebase-sign-up")
    public Object firebaseSignUp(
            @RequestHeader("Authorization") String authorizationHeader) {
        // todo
        // 헤더에 firebase id token을 입력으로 받음
        // 추가적으로 약관 동의 여부, 닉네임, 프로필 사진 url (presigned url), 성별, 그리고 nullable한 키 몸무게를 입력으로 받음
        // - firebase uuid가 존재하는 경우 예외 반환
        // - 회원 생성 후 필요하다고 판단되는 데이터 반환
        return null;
    }


    // 토큰 갱신


    // (선택) 로그아웃


}
