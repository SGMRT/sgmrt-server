package soma.ghostrunner.domain.course.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 코스의 상위 기록 슬롯을 담는 불변 VO.
 *
 * 불변식: 기록 오름차순 정렬 · 최대 {@link #MAX_RANK}개 · 멤버 중복 없음(멤버당 최고 기록 1개만 유지)
 */
public record TopRunners(List<RankSlot> slots) {

    public static final int MAX_RANK = 4;

    public TopRunners {
        slots = List.copyOf(slots);
    }

    /**
     * 새 기록을 반영한 새 인스턴스를 반환한다. 변화가 없으면 this 를 그대로 반환한다.
     */
    public TopRunners with(Long memberId, int timeSeconds) {
        if (!qualifies(memberId, timeSeconds)) {
            return this;
        }
        List<RankSlot> updatedSlots = new ArrayList<>(slots);
        updatedSlots.removeIf(slot -> Objects.equals(slot.getMemberId(), memberId));
        updatedSlots.add(new RankSlot(memberId, timeSeconds));
        updatedSlots.sort(Comparator.comparing(RankSlot::getTimeSeconds));
        return new TopRunners(updatedSlots.subList(0, Math.min(updatedSlots.size(), MAX_RANK)));
    }

    /**
     * 대부분의 러닝은 여기서 끝난다 — 리스트 조작 없이 탈락 판정.
     *
     * 이미 슬롯을 가진 멤버는 슬롯 수와 무관하게 자신의 최고 기록을 앞당길 때만 통과한다.
     * 동률은 기존 순위를 유지한다(strict {@code <} 판정).
     */
    private boolean qualifies(Long memberId, int timeSeconds) {
        RankSlot myBestSlot = findByMember(memberId);
        if (myBestSlot != null) {
            return timeSeconds < myBestSlot.getTimeSeconds();
        }
        if (slots.size() < MAX_RANK) {
            return true;
        }
        RankSlot slowestSlot = slots.get(slots.size() - 1);
        return timeSeconds < slowestSlot.getTimeSeconds();
    }

    private RankSlot findByMember(Long memberId) {
        for (RankSlot slot : slots) {
            if (Objects.equals(slot.getMemberId(), memberId)) {
                return slot;
            }
        }
        return null;
    }

}
