package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.global.common.BaseTimeEntity;

/**
 * 코스-회원 간 관계를 추적하는 중간 테이블
 * <p>
 *     생명주기
 * <ul>
 *   <li>코스 생성자가 코스를 공개 전환 → 생성자 구독 객체 생성 or 복원</li>
 *   <li>코스 생성자가 코스를 비공개 전환 → 생성자 구독만 soft delete, 타인 구독은 유지됨</li>
 *   <li>타인이 공개 코스를 따라 달림 → 타인의 구독 객체 자동 생성</li>
 * </ul>
 *
 * <p>
 * 소유자가 비공개 전환하면 소유자 본인은 주변 코스 조회에서 해당 코스가 보이지 않지만,
 * 달린 적 있는 타인은 구독 객체가 남아있어 계속 조회할 수 있다.<br>
 */
@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "course_subscription",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_course_member",
                        columnNames = {"course_id", "member_id"}
                )
        },
        indexes = {
                @Index(name = "idx_course_member_deleted", columnList = "course_id, member_id, deleted")
        }
)
public class CourseSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어떤 코스에 대한 구독/관계인지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * 어떤 사용자의 구독/관계인지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 등록 해제 여부 (Soft Delete)
     * false : 활성
     * true  : 등록 해제
     */
    @Column(nullable = false)
    private boolean deleted = false;

    private CourseSubscription(Course course, Member member) {
        this.course = course;
        this.member = member;
        this.deleted = false;
    }

    /* =====================
       생성 메서드
       ===================== */

    public static CourseSubscription create(Course course, Member member) {
        return new CourseSubscription(course, member);
    }

    /* =====================
       도메인 로직
       ===================== */

    /**
     * 코스 등록 해제 (Soft Delete)
     */
    public void unregister() {
        if (this.deleted) {
            return; // 멱등성 보장
        }
        this.deleted = true;
    }

    /**
     * 재등록 (확장 가능)
     */
    public void restore() {
        this.deleted = false;
    }

    public boolean isActive() {
        return !deleted;
    }
}
