package com.example.brdagent;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class DocumentExtractor {
    private static final int MAX_CHARS_PER_FILE = 120_000;

    public SourceDocument extract(File file) throws IOException {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        String text;
        if (lower.endsWith(".pdf")) text = pdf(file);
        else if (lower.endsWith(".docx")) text = docx(file);
        else if (lower.endsWith(".xlsx")) text = xlsx(file);
        else text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        text = text == null ? "" : text.replace("\r", "").trim();
        if (text.length() > MAX_CHARS_PER_FILE) {
            text = text.substring(0, MAX_CHARS_PER_FILE) + "\n\n[TRUNCATED]";
        }
        return new SourceDocument(file.getName(), text);
    }

    private String pdf(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String docx(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String xlsx(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                sb.append("\n## Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    for (Cell cell : row) sb.append(formatter.formatCellValue(cell)).append(" | ");
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }
}
