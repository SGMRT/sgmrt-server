package soma.ghostrunner.global.common.log;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingPatternLayout extends PatternLayout {

    private static final String MASK_TEXT = "*****";

    private Pattern multilinePattern;
    private final List<String> maskPatterns = new ArrayList<>();

    public void addMaskPattern(String maskPattern) {
        maskPatterns.add(maskPattern);
        multilinePattern = Pattern.compile(String.join("|", maskPatterns), Pattern.MULTILINE);
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        return maskMessage(super.doLayout(event));
    }

    private String maskMessage(String message) {
        if (multilinePattern == null || message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder(message);
        Matcher matcher = multilinePattern.matcher(sb);

        List<int[]> replacementPoints = new ArrayList<>();

        while (matcher.find()) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                if (matcher.group(group) != null) {
                    replacementPoints.add(new int[]{matcher.start(group), matcher.end(group)});
                }
            }
        }

        for (int i = replacementPoints.size() - 1; i >= 0; i--) {
            int[] points = replacementPoints.get(i);
            sb.replace(points[0], points[1], MASK_TEXT);
        }

        return sb.toString();
    }
}
