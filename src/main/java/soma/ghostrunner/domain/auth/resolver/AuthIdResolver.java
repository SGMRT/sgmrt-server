package soma.ghostrunner.domain.auth.resolver;

public interface AuthIdResolver {

    String resolveAuthId(String externalAuthToken);

}
