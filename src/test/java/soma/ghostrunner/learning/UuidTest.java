package soma.ghostrunner.learning;

import org.junit.jupiter.api.Test;

import java.util.UUID;

public class UuidTest {

    @Test
    public void testUuid() {
        UUID uuid = UUID.randomUUID();
        System.out.println(uuid);
    }

}
