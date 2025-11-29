package soma.ghostrunner.domain.running.infra.openai;

import java.util.List;

public class OpenAiResponse {
    public String id;
    public String object;

    // 편의 필드: OpenAI 서버가 만들어주는 전체 텍스트
    public String output_text;

    public List<OutputItem> output;

    public static class OutputItem {
        public List<Content> content;
    }

    public static class Content {
        public String type;  // "output_text", "refusal" 등
        public String text;  // 실제 텍스트 값
    }
}
