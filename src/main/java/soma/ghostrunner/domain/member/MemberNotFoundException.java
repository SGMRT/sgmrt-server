package soma.ghostrunner.domain.member;

import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.EntityNotFoundException;

public class MemberNotFoundException extends EntityNotFoundException {
    public MemberNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MemberNotFoundException(ErrorCode errorCode, long id) {
        super(errorCode, id);
    }
}
