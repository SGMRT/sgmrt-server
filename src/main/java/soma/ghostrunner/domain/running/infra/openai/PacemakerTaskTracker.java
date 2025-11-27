package soma.ghostrunner.domain.running.infra.openai;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PacemakerTaskTracker implements SmartLifecycle {

    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private volatile boolean running = true;

    public void startTask() {
        activeTasks.incrementAndGet();
    }

    public void endTask() {
        activeTasks.decrementAndGet();
    }

    @Override
    public void start() {
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("🛑 SmartLifecycle stop() 호출: AI Ghost 작업 대기 시작");
        this.running = false;
        awaitCompletion();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;       // 가장 늦게 생성, 가장 먼저 종료
    }

    public void awaitCompletion() {

        int remaining = activeTasks.get();
        if (remaining == 0) {
            log.info("AI 고스트(페이스메이커) 작업이 없습니다. 즉시 종료합니다.");
            return;
        }

        log.info("Graceful Shutdown 시작. 진행 중인 AI 고스트(페이스메이커) 작업 {}개를 대기합니다.", remaining);

        long maxWaitMillis = 180_000; // 3분
        long startTime = System.currentTimeMillis();

        // 0.5s 마다 루프 돌며 남은 작업이 있는지 확인
        while (activeTasks.get() > 0) {

            if (System.currentTimeMillis() - startTime > maxWaitMillis) {
                log.warn("시간 초과! {}개의 작업이 완료되지 않았지만 강제 종료합니다.", activeTasks.get());
                break;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

        }

        log.info("모든 AI Ghost 작업 완료. 종료 프로세스를 진행합니다.");
    }

}
