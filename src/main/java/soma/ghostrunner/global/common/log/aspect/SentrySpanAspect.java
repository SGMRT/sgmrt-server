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
 * course와 running 패키지의 컨트롤러, 서비스, 리포지토리에 적용한다.
 **/
@Aspect
@Component
public class SentrySpanAspect {

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Api.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Api.*(..))")
    public void apiPackagePointCut() {}

    @Pointcut("execution(* soma.ghostrunner.domain.course..*Service.*(..)) || " +
            "execution(* soma.ghostrunner.domain.course..*Facade.*(..)) || " +
            "execution(* soma.ghostrunner.domain.running..*Service.*(..))")
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
