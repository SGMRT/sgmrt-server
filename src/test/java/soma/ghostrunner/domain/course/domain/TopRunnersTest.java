package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TopRunners 순수 단위 테스트 (Spring 컨텍스트/DB 없음)
 *
 * 불변식: 기록 오름차순 정렬 · 최대 4개(MAX_RANK) · 멤버 중복 없음(멤버당 최고 기록 1개만 유지)
 * 설계 문서: docs/refactoring/course-read-model/core/04-detailed-design.md §1-3, §1-4, §1-7
 */
@DisplayName("TopRunners 단위 테스트")
class TopRunnersTest {

    @Nested
    @DisplayName("with() - 삽입 성공")
    class InsertSuccess {

        @Test
        @DisplayName("빈 TopRunners에 기록을 반영하면 슬롯 1개짜리 새 인스턴스가 된다")
        void insertIntoEmpty() {
            // given
            TopRunners empty = topRunners();

            // when
            TopRunners result = empty.with(1L, 1800);

            // then
            assertThat(result).isNotSameAs(empty);
            assertThat(empty.slots()).isEmpty();
            assertThat(result.slots()).hasSize(1);
            assertThat(result.slots().get(0).getMemberId()).isEqualTo(1L);
            assertThat(result.slots().get(0).getTimeSeconds()).isEqualTo(1800);
        }

        @Test
        @DisplayName("슬롯이 MAX_RANK 미만이면 신규 멤버는 기존 기록보다 느려도 삽입된다")
        void insertWhenNotFull() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200));

            // when
            TopRunners result = current.with(4L, 9999);

            // then
            assertThat(result.slots()).hasSize(4);
            assertThat(result.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(1L, 1000),
                            tuple(2L, 1100),
                            tuple(3L, 1200),
                            tuple(4L, 9999));
        }

        @Test
        @DisplayName("중간 순위로 삽입되면 하위 슬롯이 밀려나고 5번째는 잘려 최대 4개를 유지한다")
        void insertInMiddleTruncatesFifth() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(5L, 1150);

            // then
            assertThat(result.slots()).hasSize(TopRunners.MAX_RANK);
            assertThat(result.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(1L, 1000),
                            tuple(2L, 1100),
                            tuple(5L, 1150),
                            tuple(3L, 1200));
            assertThat(result.slots()).extracting(RankSlot::getMemberId).doesNotContain(4L);
        }

        @Test
        @DisplayName("이미 포함된 멤버가 더 빠른 기록을 내면 기존 슬롯이 제거되고 재삽입되어 중복이 없다")
        void existingMemberFasterRecordHasNoDuplicate() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200));

            // when
            TopRunners result = current.with(3L, 900);

            // then
            assertThat(result.slots()).hasSize(3);
            assertThat(result.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(3L, 900),
                            tuple(1L, 1000),
                            tuple(2L, 1100));
            assertThat(result.slots()).extracting(RankSlot::getMemberId)
                    .containsOnlyOnce(3L);
        }
    }

    @Nested
    @DisplayName("with() - 삽입 탈락")
    class InsertRejected {

        @Test
        @DisplayName("4명이 찬 상태에서 4위보다 느린 기록은 동일 인스턴스를 그대로 반환한다")
        void slowerThanLastSlotReturnsThis() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(5L, 1400);

            // then
            assertThat(result).isSameAs(current);
        }

        @Test
        @DisplayName("이미 포함된 멤버가 더 느린 기록을 내면 변화 없이 동일 인스턴스를 반환한다")
        void existingMemberSlowerRecordReturnsThis() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(2L, 1250);

            // then
            assertThat(result).isSameAs(current);
            assertThat(current.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(1L, 1000),
                            tuple(2L, 1100),
                            tuple(3L, 1200),
                            tuple(4L, 1300));
        }

        @Test
        @DisplayName("슬롯이 MAX_RANK 미만이어도 이미 포함된 멤버가 더 느린 기록을 내면 기존 최고 기록이 유지된다")
        void existingMemberSlowerRecordWhenNotFullKeepsBestRecord() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100));

            // when
            TopRunners result = current.with(2L, 1500);

            // then
            assertThat(result).isSameAs(current);
            assertThat(current.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(1L, 1000),
                            tuple(2L, 1100));
        }
    }

    @Nested
    @DisplayName("with() - 엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("4위와 동률인 신규 기록은 기존 순위를 유지한다 (strict < 판정)")
        void tieWithLastSlotKeepsExistingRank() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(5L, 1300);

            // then
            assertThat(result).isSameAs(current);
        }

        @Test
        @DisplayName("이미 포함된 멤버가 자신과 동률인 기록을 내면 기존 순위를 유지한다 (strict < 판정)")
        void tieWithOwnRecordKeepsExistingRank() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(2L, 1100);

            // then
            assertThat(result).isSameAs(current);
        }

        @Test
        @DisplayName("4명이 찬 상태에서 4위 멤버 본인이 1위 기록을 내면 정렬이 재배치된다")
        void lastSlotMemberBecomesFirst() {
            // given
            TopRunners current = topRunners(slot(1L, 1000), slot(2L, 1100), slot(3L, 1200), slot(4L, 1300));

            // when
            TopRunners result = current.with(4L, 900);

            // then
            assertThat(result.slots()).hasSize(TopRunners.MAX_RANK);
            assertThat(result.slots())
                    .extracting(RankSlot::getMemberId, RankSlot::getTimeSeconds)
                    .containsExactly(
                            tuple(4L, 900),
                            tuple(1L, 1000),
                            tuple(2L, 1100),
                            tuple(3L, 1200));
        }
    }

    @Nested
    @DisplayName("불변성")
    class Immutability {

        @Test
        @DisplayName("생성자에 넘긴 List를 외부에서 변경해도 내부 슬롯은 영향을 받지 않는다 (방어적 복사)")
        void defensiveCopyOnConstruction() {
            // given
            List<RankSlot> source = new ArrayList<>();
            source.add(new RankSlot(1L, 1000));
            TopRunners topRunners = new TopRunners(source);

            // when
            source.add(new RankSlot(2L, 1100));
            source.clear();

            // then
            assertThat(topRunners.slots()).hasSize(1);
            assertThat(topRunners.slots().get(0).getMemberId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("slots()가 반환한 리스트에 원소를 추가하면 UnsupportedOperationException이 발생한다")
        void slotsIsUnmodifiable() {
            // given
            TopRunners topRunners = topRunners(slot(1L, 1000));

            // when & then
            assertThatThrownBy(() -> topRunners.slots().add(new RankSlot(2L, 1100)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("동등성")
    class Equality {

        @Test
        @DisplayName("같은 순서로 같은 값을 가진 TopRunners는 동등하다 (엔티티 변경 감지의 전제)")
        void sameOrderSameValueIsEqual() {
            // given
            TopRunners left = topRunners(slot(1L, 1000), slot(2L, 1100));
            TopRunners right = topRunners(slot(1L, 1000), slot(2L, 1100));

            // when & then
            assertThat(left).isEqualTo(right);
            assertThat(left).hasSameHashCodeAs(right);
        }

        @Test
        @DisplayName("순서 또는 값이 다른 TopRunners는 동등하지 않다")
        void differentOrderOrValueIsNotEqual() {
            // given
            TopRunners base = topRunners(slot(1L, 1000), slot(2L, 1100));
            TopRunners differentOrder = topRunners(slot(2L, 1100), slot(1L, 1000));
            TopRunners differentValue = topRunners(slot(1L, 1000), slot(2L, 1200));

            // when & then
            assertThat(base).isNotEqualTo(differentOrder);
            assertThat(base).isNotEqualTo(differentValue);
        }

        @Test
        @DisplayName("RankSlot은 memberId와 timeSeconds가 같으면 동등하고 해시코드도 같다")
        void rankSlotEqualsAndHashCode() {
            // given
            RankSlot slot = new RankSlot(1L, 1000);
            RankSlot same = new RankSlot(1L, 1000);
            RankSlot differentMember = new RankSlot(2L, 1000);
            RankSlot differentTime = new RankSlot(1L, 1100);

            // when & then
            assertThat(slot).isEqualTo(same);
            assertThat(slot).hasSameHashCodeAs(same);
            assertThat(slot).isNotEqualTo(differentMember);
            assertThat(slot).isNotEqualTo(differentTime);
        }
    }

    // ========== Helper Methods ==========

    /**
     * 나열한 순서 그대로 슬롯을 가진 TopRunners를 만든다.
     */
    private static TopRunners topRunners(RankSlot... slots) {
        return new TopRunners(List.of(slots));
    }

    private static RankSlot slot(long memberId, int timeSeconds) {
        return new RankSlot(memberId, timeSeconds);
    }
}
