package soma.ghostrunner.domain.pacemaker.application.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.pacemaker.application.PacemakerRecoveryService;

/**
 * Pacemaker 복구 워커
 * - 1분마다 실행
 * - ShedLock으로 다중 서버 환경에서 중복 실행 방지
 * - INIT/PROCEEDING 상태로 15분 이상 경과한 Pacemaker를 복구
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PacemakerRecoveryWorker {

    private final PacemakerRecoveryService recoveryService;

    @Scheduled(fixedDelay = 60000)  // 1분마다 실행
    @SchedulerLock(
            name = "pacemakerRecovery",
            lockAtLeastFor = "PT50S",   // 최소 50초 락 유지 (중복 실행 방지)
            lockAtMostFor = "PT55S"     // 최대 55초 락 유지 (데드락 방지)
    )
    public void recoverStalePacemakers() {
        log.debug("Pacemaker 복구 워커 시작");

        try {
            recoveryService.recoverStalePacemakers();
        } catch (Exception e) {
            log.error("Pacemaker 복구 워커 실행 중 오류 발생", e);
        }

        log.debug("Pacemaker 복구 워커 종료");
    }

}
