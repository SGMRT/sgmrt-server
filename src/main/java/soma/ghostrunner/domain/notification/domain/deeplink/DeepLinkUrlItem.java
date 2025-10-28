package soma.ghostrunner.domain.notification.domain.deeplink;

public record DeepLinkUrlItem(
        String version, // 클라이언트 버전 (예: "1.0.0")
        String url // 딥링크 URL (예: "/course/123/detail")
) {
    public static final String ALL_VERSIONS = null;
    public static final String EMPTY_URL = null;
}
