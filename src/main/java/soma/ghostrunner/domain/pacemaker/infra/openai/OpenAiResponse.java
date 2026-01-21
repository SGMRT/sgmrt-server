package soma.ghostrunner.domain.pacemaker.infra.openai;

import java.util.List;

public class OpenAiResponse {
    public String id;
    public String object;

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
