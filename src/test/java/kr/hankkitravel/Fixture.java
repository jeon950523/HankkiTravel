package kr.hankkitravel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Fixture {
    private Fixture() {}
    public static String read(String name) throws IOException {
        try (var input = Fixture.class.getResourceAsStream("/fixtures/" + name)) {
            if (input == null) throw new IOException("Missing fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
