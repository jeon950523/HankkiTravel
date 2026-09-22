package kr.hankkitravel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class LocalEnvironment {
    private LocalEnvironment() {}
    static String get(String name) throws IOException {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) return value;
        var path = Files.isRegularFile(Path.of(".env.local")) ? Path.of(".env.local") : Path.of("../.env.local");
        if (!Files.isRegularFile(path)) return "";
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties.getProperty(name, "");
    }
}
