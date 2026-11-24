package br.com.fynavero.report;

import br.com.fynavero.report.ReportDtos.AnnualReport;
import br.com.fynavero.report.ReportDtos.ReportGroup;
import br.com.fynavero.report.ReportDtos.ReportRow;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static br.com.fynavero.common.Formats.brl;

/** Exporta o relatório anual já calculado pelo {@link ReportService} para Excel e PDF (nenhum cálculo novo aqui). */
@Service
public class ReportExporter {
    // Cores da identidade Fynavero usadas nos arquivos exportados.
    private static final Color ORANGE = new Color(0xE8, 0x50, 0x02);
    private static final Color DARK = new Color(0x33, 0x33, 0x33);
    private static final Color LIGHT = new Color(0xF2, 0xF2, 0xF2);

    private enum Style { TITLE, SECTION, NORMAL, SUBTOTAL, TOTAL }

    private record Line(Style style, String label, ReportRow row) {}

    private static List<Line> lines(AnnualReport r) {
        List<Line> out = new ArrayList<>();
        out.add(new Line(Style.SECTION, "Receitas", null));
        r.receitas().forEach(x -> out.add(new Line(Style.NORMAL, x.label(), x)));
        if (!r.emprestimos().isEmpty()) {
            out.add(new Line(Style.SECTION, "Empréstimos recebidos (entram no saldo)", null));
            r.emprestimos().forEach(x -> out.add(new Line(Style.NORMAL, x.label(), x)));
        }
        out.add(new Line(Style.SECTION, "Despesas", null));
        for (ReportGroup g : r.despesas()) {
            g.rows().forEach(x -> out.add(new Line(Style.NORMAL, x.label(), x)));
            out.add(new Line(Style.SUBTOTAL, g.subtotal().label(), g.subtotal()));
        }
        out.add(new Line(Style.TOTAL, r.totalDespesas().label(), r.totalDespesas()));
        out.add(new Line(Style.SECTION, "Investimentos", null));
        r.investimentos().forEach(x -> out.add(new Line(Style.NORMAL, x.label(), x)));
        out.add(new Line(Style.TOTAL, r.totalInvestido().label(), r.totalInvestido()));
        out.add(new Line(Style.SECTION, "Resultado", null));
        r.resultado().forEach(x -> out.add(new Line(Style.TOTAL, x.label(), x)));
        return out;
    }

    public byte[] xlsx(AnnualReport r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Relatório " + r.year());
            DataFormat df = wb.createDataFormat();
            short money = df.getFormat("[$R$-416] #,##0.00;-[$R$-416] #,##0.00");

            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            org.apache.poi.ss.usermodel.Font title = wb.createFont();
            title.setBold(true);
            title.setFontHeightInPoints((short) 14);

            XSSFCellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(new XSSFColor(ORANGE, null));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(title);
            CellStyle section = wb.createCellStyle();
            section.setFont(bold);
            CellStyle num = wb.createCellStyle();
            num.setDataFormat(money);
            CellStyle numBold = wb.createCellStyle();
            numBold.setDataFormat(money);
            numBold.setFont(bold);
            XSSFCellStyle totalStyle = wb.createCellStyle();
            totalStyle.setDataFormat(money);
            totalStyle.setFont(bold);
            totalStyle.setBorderTop(BorderStyle.THIN);
            CellStyle labelBold = wb.createCellStyle();
            labelBold.setFont(bold);

            int rowIdx = 0;
            Row t = sh.createRow(rowIdx++);
            t.createCell(0).setCellValue("Fynavero — Relatório anual " + r.year());
            t.getCell(0).setCellStyle(titleStyle);
            rowIdx++;

            Row h = sh.createRow(rowIdx++);
            List<String> heads = new ArrayList<>(List.of("Categoria"));
            heads.addAll(r.monthLabels());
            heads.add("Total");
            heads.add("Média mensal");
            for (int i = 0; i < heads.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(heads.get(i));
                c.setCellStyle(header);
            }

            for (Line l : lines(r)) {
                Row row = sh.createRow(rowIdx++);
                Cell label = row.createCell(0);
                label.setCellValue(l.label());
                if (l.style() == Style.SECTION) {
                    label.setCellStyle(section);
                    sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 2));
                    continue;
                }
                boolean strong = l.style() != Style.NORMAL;
                if (strong) label.setCellStyle(labelBold);
                CellStyle s = l.style() == Style.TOTAL ? totalStyle : strong ? numBold : num;
                for (int i = 0; i < 12; i++) setMoney(row.createCell(i + 1), l.row().months()[i], s);
                setMoney(row.createCell(13), l.row().total(), totalStyle);
                setMoney(row.createCell(14), l.row().average(), s);
            }
            sh.setColumnWidth(0, 34 * 256);
            for (int i = 1; i <= 14; i++) sh.setColumnWidth(i, 14 * 256);
            sh.createFreezePane(1, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setMoney(Cell c, long cents, CellStyle s) {
        c.setCellValue(cents / 100.0);
        c.setCellStyle(s);
    }

    public byte[] pdf(AnnualReport r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titleF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, DARK);
        Font headF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.5f, Color.BLACK);
        Font cellF = FontFactory.getFont(FontFactory.HELVETICA, 6.2f, DARK);
        Font boldF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.2f, Color.BLACK);
        Font brandF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, ORANGE);

        Paragraph brand = new Paragraph("FYNAVERO", brandF);
        doc.add(brand);
        Paragraph title = new Paragraph("Relatório anual " + r.year(), titleF);
        title.setSpacingAfter(10);
        doc.add(title);

        PdfPTable table = new PdfPTable(15);
        table.setWidthPercentage(100);
        float[] widths = new float[15];
        widths[0] = 3.2f;
        for (int i = 1; i < 15; i++) widths[i] = 1.25f;
        table.setWidths(widths);
        table.setHeaderRows(1);

        List<String> heads = new ArrayList<>(List.of("Categoria"));
        heads.addAll(r.monthLabels());
        heads.add("Total");
        heads.add("Média");
        for (String hd : heads) {
            PdfPCell c = new PdfPCell(new Phrase(hd, headF));
            c.setBackgroundColor(ORANGE);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(3);
            c.setBorderColor(ORANGE);
            table.addCell(c);
        }
        for (Line l : lines(r)) {
            if (l.style() == Style.SECTION) {
                PdfPCell c = new PdfPCell(new Phrase(l.label(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, DARK)));
                c.setColspan(15);
                c.setBackgroundColor(LIGHT);
                c.setPadding(4);
                c.setBorder(Rectangle.NO_BORDER);
                table.addCell(c);
                continue;
            }
            Font f = l.style() == Style.NORMAL ? cellF : boldF;
            table.addCell(cell(l.label(), f, Element.ALIGN_LEFT, l.style()));
            for (int i = 0; i < 12; i++) table.addCell(cell(brl(l.row().months()[i]), f, Element.ALIGN_RIGHT, l.style()));
            table.addCell(cell(brl(l.row().total()), boldF, Element.ALIGN_RIGHT, l.style()));
            table.addCell(cell(brl(l.row().average()), f, Element.ALIGN_RIGHT, l.style()));
        }
        doc.add(table);
        Paragraph foot = new Paragraph("Média mensal considera apenas os meses com valor. Valores previstos incluídos.", cellF);
        foot.setSpacingBefore(8);
        doc.add(foot);
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(String text, Font f, int align, Style style) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(2.5f);
        c.setBorder(style == Style.TOTAL ? Rectangle.TOP : Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
        return c;
    }
}
