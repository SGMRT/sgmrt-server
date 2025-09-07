package soma.ghostrunner.domain.auth.event;

public record MemberLoggedInEvent(
   Long memberId,
   String pushToken
) {}