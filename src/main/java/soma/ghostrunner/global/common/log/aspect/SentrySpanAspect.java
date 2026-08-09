package soma.ghostrunner.global.common.log.aspect;

import io.sentry.ISpan;
import io.sentry.Sentry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;


/**
 * 메서드 별 실행 시간 캡처를 위해 메서드 앞뒤로 Sentry Span을 덧씌운다.
 * Span은 Sentry 성능 측정 대시보드에서 확인할 수 있다.
 * course와 running 패키지의 컨트롤러(Api), 애플리케이션 계층(Service·Facade·Reader·Writer·Resolver),
 * 리포지토리에 적용한다.
 *
 * <p><b>⚠️ 이 포인트컷은 클래스 이름 접미사에 의존한다 — 클래스 개명 시 반드시 이 파일을 함께 확인할 것.</b>
 * 컴파일러도 테스트도 이 유실을 잡지 못하고, 알게 되는 시점은 장애 대응 중 트레이스를 열었을 때다.
 * (사례: docs/design/reader-writer-layering.md 의 Reader/Writer 개명으로 {@code *Service} 접미사를 잃은
 * 클래스들의 스팬이 조용히 유실됐다.)
 **/
@Aspect
@Component
public class SentrySpanAspect {

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Api.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Api.*(..))")
    public void apiPackagePointCut() {}

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Service.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Facade.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Reader.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Writer.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Resolver.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Service.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Reader.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Writer.*(..))")
    public void servicePackagePointCut() {}

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Repository.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Repository.*(..))")
    public void repositoryPackagePointCut() {}

    @Around("apiPackagePointCut() || " +
            "servicePackagePointCut() || " +
            "repositoryPackagePointCut()")
    public Object sentrySpanLogging(ProceedingJoinPoint joinPoint) throws Throwable {
        String spanName = joinPoint.getSignature().getName();
        ISpan parent = Sentry.getSpan();
        ISpan span = parent != null ? parent.startChild("method", spanName) : null;
        try {
            return joinPoint.proceed();
        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }
}
