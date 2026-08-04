package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TOP 순위 한 자리(멤버 + 기록)를 나타내는 값 객체.
 * 컬럼명은 이 VO를 embed 하는 엔티티의 @AttributeOverride 가 지정한다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class RankSlot {

    private Long memberId;

    private Integer timeSeconds;

    public RankSlot(Long memberId, Integer timeSeconds) {
        this.memberId = memberId;
        this.timeSeconds = timeSeconds;
    }

}
