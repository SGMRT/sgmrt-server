package soma.ghostrunner.domain.member.exception;

import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.BusinessException;

public class MemberSettingsNotFoundException extends BusinessException {

  public MemberSettingsNotFoundException() { super(ErrorCode.ENTITY_NOT_FOUND, "MemberSettings not found"); }


}
