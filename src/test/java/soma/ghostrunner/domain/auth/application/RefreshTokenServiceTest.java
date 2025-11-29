package soma.ghostrunner.domain.auth.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;

import java.util.Optional;
import java.util.UUID;

class RefreshTokenServiceTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @DisplayName("리프레쉬 토큰을 저장하면, 해당 사용자의 ID로 조회가 가능하다.")
    @Test
    void testSaveAndFindToken() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        String refreshToken = "my-refresh-token";

        // when
        refreshTokenService.saveToken(memberUuid, refreshToken);

        // then
        Optional<String> foundToken = refreshTokenService.findTokenByMemberUuid(memberUuid);
        Assertions.assertThat(foundToken).isPresent();
        Assertions.assertThat(foundToken.get()).isEqualTo(refreshToken);
    }

    @DisplayName("리프레쉬 토큰을 삭제하면, 더 이상 조회되지 않는다.")
    @Test
    void testDeleteToken() {
        // given
        String memberUuid = UUID.randomUUID().toString();
        String refreshToken = "my-refresh-token";
        // 먼저 데이터를 저장
        refreshTokenService.saveToken(memberUuid, refreshToken);

        // when
        refreshTokenService.deleteTokenByMemberUuid(memberUuid);

        // then
        Optional<String> foundToken = refreshTokenService.findTokenByMemberUuid(memberUuid);
        Assertions.assertThat(foundToken).isNotPresent();
    }

    @DisplayName("저장된 토큰이 없는 사용자를 조회하면, 빈 Optional 객체를 반환한다.")
    @Test
    void testFindToken_NotFound() {
        // given
        String memberUuid = UUID.randomUUID().toString();

        // when
        Optional<String> foundToken = refreshTokenService.findTokenByMemberUuid(memberUuid);

        // then
        Assertions.assertThat(foundToken).isEmpty();
    }

}
