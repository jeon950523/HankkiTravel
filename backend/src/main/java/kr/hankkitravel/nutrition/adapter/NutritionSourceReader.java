package kr.hankkitravel.nutrition.adapter;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.hankkitravel.nutrition.model.NutritionSourceData;
import kr.hankkitravel.nutrition.model.NutritionSourceRow;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Component;

/** Reads a supplied xlsx only for an explicit import command; paths are never persisted or returned. */
@Component
public class NutritionSourceReader {
    private static final List<String> REQUIRED = List.of("식품코드", "식품명", "식품대분류명", "영양성분함량기준량",
            "에너지(kcal)", "탄수화물(g)", "당류(g)", "단백질(g)", "지방(g)", "나트륨(mg)", "출처명",
            "데이터생성일자", "데이터기준일자");

    private static final int MAX_EXCEL_PART_BYTES = 150_000_000;
    public NutritionSourceData read(Path path) {
        IOUtils.setByteArrayMaxOverride(MAX_EXCEL_PART_BYTES);
        if (path == null || !Files.isRegularFile(path)) throw new IllegalArgumentException("영양 원본 파일을 찾을 수 없습니다.");
        try (InputStream input = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() < 1) throw new IllegalArgumentException("영양 원본 시트가 없습니다.");
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.KOREA);
            var header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new IllegalArgumentException("영양 원본 헤더가 없습니다.");
            var columns = headerMap(header, formatter);
            validateRequired(columns);
            var rows = new ArrayList<NutritionSourceRow>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || blank(row, columns, formatter)) continue;
                rows.add(new NutritionSourceRow(index + 1, value(row, columns, "식품코드", formatter),
                        value(row, columns, "식품명", formatter), value(row, columns, "식품대분류명", formatter),
                        value(row, columns, "영양성분함량기준량", formatter), value(row, columns, "에너지(kcal)", formatter),
                        value(row, columns, "탄수화물(g)", formatter), value(row, columns, "당류(g)", formatter),
                        value(row, columns, "단백질(g)", formatter), value(row, columns, "지방(g)", formatter),
                        value(row, columns, "나트륨(mg)", formatter), value(row, columns, "출처명", formatter),
                        value(row, columns, "데이터생성일자", formatter), value(row, columns, "데이터기준일자", formatter)));
            }
            return new NutritionSourceData(path.getFileName().toString(), sheet.getSheetName(), columns.size(), rows);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("영양 원본을 읽을 수 없습니다.", exception);
        }
    }

    private Map<String, Integer> headerMap(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (var cell : row) {
            String value = formatter.formatCellValue(cell).trim();
            if (!value.isBlank()) columns.putIfAbsent(value, cell.getColumnIndex());
        }
        return columns;
    }

    private void validateRequired(Map<String, Integer> columns) {
        var missing = REQUIRED.stream().filter(required -> !columns.containsKey(required)).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("필수 영양 원본 열이 없습니다: " + String.join(",", missing));
    }

    private String value(Row row, Map<String, Integer> columns, String name, DataFormatter formatter) {
        var cell = row.getCell(columns.get(name), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? null : formatter.formatCellValue(cell).trim();
    }

    private boolean blank(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        return value(row, columns, "식품코드", formatter) == null && value(row, columns, "식품명", formatter) == null;
    }
}
