package soma.ghostrunner.domain.running.infra.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.domain.formula.RunningTipsProvider;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JsonlRunningTipsProvider implements RunningTipsProvider {

    private final ObjectMapper objectMapper;
    private List<String> cache;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("static/running-tips.jsonl");
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                this.cache = br.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                        .map(this::parseLine)
                        .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
            }

            if (cache.isEmpty()) {
                throw new IllegalStateException("러닝 팁이 비어 있습니다. running-tips.jsonl 내용을 확인하세요.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("러닝 팁 JSONL 로딩에 실패했습니다.", ex);
        }
    }

    @Override
    public String getRandomTip() {
        Random random = new Random();
        return cache.get(random.nextInt(cache.size()));
    }

    private String parseLine(String line) {
        try {
            TipRecord rec = objectMapper.readValue(line, TipRecord.class);
            if (rec.getText() == null || rec.getText().isBlank()) {
                throw new IllegalArgumentException("text 필드가 비어 있습니다: " + line);
            }
            return rec.getText().trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("JSONL 파싱 실패: " + line, e);
        }
    }

    @Getter
    private static class TipRecord {
        private String text;
    }
}
