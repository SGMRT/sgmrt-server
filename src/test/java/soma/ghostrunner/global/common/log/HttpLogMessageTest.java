package soma.ghostrunner.global.common.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class HttpLogMessageTest {

    ObjectMapper mapper = new ObjectMapper();

    @DisplayName("객체를 .toString()을 사용했을 때 콘솔에 Json 형태로 출력하는지 검증한다.")
    @Test
    void objectToJsonPrettyPrinterTest() {
        String singleLineJson = "{\"key\":\"value\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Object json = objectMapper.readValue(singleLineJson, Object.class);
            String test = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            System.out.println("Before : " + json + "\n");
            System.out.println("After : \n" + test);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @DisplayName("Map을 .toString()을 사용했을 때 콘솔에 Json 형태로 출력하는지 검증한다.")
    @Test
    void mapToJsonPrettyPrinterTest() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        map.put("key2", "value2");
        map.put("key3", "value3");

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            System.out.println("Pretty Map JSON: \n" + prettyJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
