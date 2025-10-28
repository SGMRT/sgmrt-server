package soma.ghostrunner.domain.notification.domain.deeplink;

import java.util.List;
import static soma.ghostrunner.domain.notification.domain.deeplink.DeepLinkUrlItem.*;

/** 푸시 알림 별 딥링크 URL 데이터 모음 */
public class DeepLinkUrlItems {

    /**
     * 사용법
     * 클라이언트 버전 업데이트로 딥링크 URL이 변경되는 경우 반환값에 새로운 버전의 DeepLinkUrlItem을 추가한다.
     *
     * 예시) 기존에는 {ALL_VERSIONS: "/course/{courseId}"} 에서 v1.0.3부터 /course/{courseId}/detail로 변경된 경우
     * - 기존: return List.of(new DeepLinkUrlItem(ALL_VERSIONS, "/course/" + courseId));
     * - 변경: return List.of(new DeepLunkUrlItem(ALL_VERSIONS, "/course/" + courseId),
     *                 new DeepLinkUrlItem("1.0.3", "/course/" + courseId + "/detail"));
     * - 위처럼 반환하면 클라이언트에서 URL 리스트를 받아서 클라이언트 버전에 따라 라우팅한다.
     *
     * 버전에 상관 없이 항상 동일한 URL을 사용하는 경우에는 DeepLinkUrlItem.ALL_VERSIONS를 사용한다.
     * - 클라이언트는 자신에 맞는 버전의 URL을 우선적으로 찾고, 없다면 ALL_VERSIONS의 URL을 사용한다.
     *
     * */

    /** 타 러너가 본인 코스 완주한 경우 */
    public static List<DeepLinkUrlItem> courseRunUrlItems(Long courseId) {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, "/profile/" + courseId + "/detail") // 코스 상세 페이지
        );
    }

    /** 본인 최고 기록 갱신한 경우 */
    public static List<DeepLinkUrlItem> topRecordUpdateUrlItems(Long courseId) {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, EMPTY_URL) // 딥링크 없음 (추후 개발)
        );
    }

    /** 페이스메이커가 생성된 경우 */
    public static List<DeepLinkUrlItem> pacemakerCreationUrlItems(Long courseId) {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, "/profile/" + courseId + "/ghosty") // 고스티 상세 페이지
        );
    }

    /** 공지사항이 공개된 경우 */
    public static List<DeepLinkUrlItem> generalNoticeUrlItems(Long noticeId) {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, "/profile/notice" + noticeId) // 공지사항 상세 페이지
        );
    }

    /** 이벤트 공지사항이 공개된 경우 */
    public static List<DeepLinkUrlItem> eventNoticeUrlItems(Long noticeId) {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, "/profile/notice/" + noticeId) // 공지사항 상세 페이지
        );
    }

    /** 다수의 공지사항이 공개된 경우 */
    public static List<DeepLinkUrlItem> multiNoticeUrlItems() {
        return List.of(
                new DeepLinkUrlItem(ALL_VERSIONS, "/profile/notice") // 공지사항 목록 페이지
        );
    }

}
