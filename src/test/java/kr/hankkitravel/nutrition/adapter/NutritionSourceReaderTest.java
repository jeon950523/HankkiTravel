package kr.hankkitravel.nutrition.adapter;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NutritionSourceReaderTest {
    private static final List<String> HEADERS = List.of("식품코드", "식품명", "식품대분류명", "영양성분함량기준량",
            "에너지(kcal)", "탄수화물(g)", "당류(g)", "단백질(g)", "지방(g)", "나트륨(mg)", "출처명",
            "데이터생성일자", "데이터기준일자");
    private final NutritionSourceReader reader = new NutritionSourceReader();

    @TempDir Path temporaryDirectory;

    @Test void readsRequiredKoreanHeadersAndKeepsMissingNutrientsNull() throws Exception {
        Path path = writeWorkbook("nutrition.xlsx", HEADERS,
                java.util.Arrays.asList("F-1", "테스트 비빔밥", "밥류", "100g", "150", null, "3", "5", "2", "400", "식약처", "2026-08-28", "2026-08-28"));

        var source = reader.read(path);

        assertThat(source.sheetName()).isEqualTo("음식");
        assertThat(source.rows()).hasSize(1);
        assertThat(source.rows().getFirst()).extracting(row -> row.sourceFoodId(), row -> row.carbohydrateGRaw(), row -> row.sourceDatasetVersionRaw())
                .containsExactly("F-1", null, "2026-08-28");
    }

    @Test void rejectsWorkbookWithoutEveryRequiredHeader() throws Exception {
        Path path = writeWorkbook("missing-column.xlsx", HEADERS.subList(0, HEADERS.size() - 1),
                List.of("F-1", "테스트", "밥류", "100g", "1", "1", "1", "1", "1", "1", "식약처", "2026-08-28"));

        assertThatThrownBy(() -> reader.read(path)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수 영양 원본 열").hasMessageContaining("데이터기준일자");
    }

    private Path writeWorkbook(String fileName, List<String> headers, List<String> values) throws Exception {
        Path path = temporaryDirectory.resolve(fileName);
        try (var workbook = new XSSFWorkbook(); var output = java.nio.file.Files.newOutputStream(path)) {
            var sheet = workbook.createSheet("음식");
            var header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) header.createCell(index).setCellValue(headers.get(index));
            var row = sheet.createRow(1);
            for (int index = 0; index < values.size(); index++) if (values.get(index) != null) row.createCell(index).setCellValue(values.get(index));
            workbook.write(output);
        }
        return path;
    }
}
