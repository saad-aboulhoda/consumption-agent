package ma.aboulhoda.consumptionagent.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import ma.aboulhoda.consumptionagent.config.ReportProperties;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionPeriod;
import ma.aboulhoda.consumptionagent.service.dto.internal.ConsumptionReport;
import ma.aboulhoda.consumptionagent.service.dto.internal.ReportRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportProperties properties;

    private static final Color INK    = new Color(0x14, 0x1B, 0x2E);
    private static final Color MUTED  = new Color(0x6B, 0x72, 0x80);
    private static final Color ACCENT = new Color(0xF5, 0x9E, 0x0B);
    private static final Color BAR    = new Color(0xF5, 0x9E, 0x0B);
    private static final Color TRACK  = new Color(0xEE, 0xF0, 0xF4);
    private static final Color ROW_ALT = new Color(0xF7, 0xF8, 0xFA);

    private static final byte[] LOGO = loadLogo();

    private record ReportSpec(String period, int year, Integer month, String periodLabel,
                              String fileName, String breakdownTitle, String firstColumn,
                              String breakdownMetric, String emptyMessage) {}

    public ConsumptionReport generateMonthly(int year, int month,
                                             ConsumptionPeriod summary, List<ReportRow> rows) {
        YearMonth ym = YearMonth.of(year, month);
        String label = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        ReportSpec spec = new ReportSpec("month", year, month, label,
                "consumption-report-%04d-%02d.pdf".formatted(year, month),
                "Daily Breakdown", "Date", "Days w/ Data",
                "No consumption data was recorded for this month.");
        return generate(spec, summary, rows);
    }

    public ConsumptionReport generateYearly(int year, ConsumptionPeriod summary, List<ReportRow> rows) {
        ReportSpec spec = new ReportSpec("year", year, null, String.valueOf(year),
                "consumption-report-%04d.pdf".formatted(year),
                "Monthly Breakdown", "Month", "Months w/ Data",
                "No consumption data was recorded for this year.");
        return generate(spec, summary, rows);
    }

    private ConsumptionReport generate(ReportSpec spec, ConsumptionPeriod summary, List<ReportRow> rows) {
        byte[] pdf = render(spec, summary, rows);

        Path target = outputDir().resolve(spec.fileName());
        try {
            Files.write(target, pdf);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report PDF: " + e.getMessage(), e);
        }

        String url = properties.getPublicBasePath() + "/" + spec.fileName();
        return new ConsumptionReport(spec.period(), spec.year(), spec.month(), spec.periodLabel(),
                summary.energyKwh(), summary.avgWatts(), summary.peakWatts(), summary.samples(),
                rows.size(), spec.fileName(), url);
    }

    private Path outputDir() {
        Path dir = Path.of(properties.getOutputDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create report directory: " + e.getMessage(), e);
        }
        return dir;
    }

    private byte[] render(ReportSpec spec, ConsumptionPeriod summary, List<ReportRow> rows) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addHeader(doc, spec.periodLabel());
            addSummary(doc, summary, spec.breakdownMetric(), rows.size());
            addBreakdownTable(doc, spec, rows);
            addFooter(doc);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render report PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private void addHeader(Document doc, String periodLabel) throws DocumentException {
        PdfPTable header = new PdfPTable(new float[]{1, 9});
        header.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (LOGO != null) {
            try {
                Image logo = Image.getInstance(LOGO);
                logo.scaleToFit(36, 36);
                logoCell.addElement(logo);
            } catch (Exception ignored) {
            }
        }
        header.addCell(logoCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.addElement(new Paragraph("Electricity Consumption Report", font(18, Font.BOLD, INK)));
        titleCell.addElement(new Paragraph(periodLabel, font(12, Font.NORMAL, ACCENT)));
        header.addCell(titleCell);

        doc.add(header);

        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(10);
        rule.setSpacingAfter(4);
        PdfPCell line = new PdfPCell();
        line.setFixedHeight(2.5f);
        line.setBorder(Rectangle.NO_BORDER);
        line.setBackgroundColor(ACCENT);
        rule.addCell(line);
        doc.add(rule);
    }

    private void addSummary(Document doc, ConsumptionPeriod s, String breakdownMetric, int breakdownCount)
            throws DocumentException {
        PdfPTable cards = new PdfPTable(4);
        cards.setWidthPercentage(100);
        cards.setSpacingBefore(10);
        cards.setSpacingAfter(18);
        cards.addCell(metricCard("Total Energy", fmt(s.energyKwh()) + " kWh"));
        cards.addCell(metricCard("Avg Power", s.avgWatts() == null ? "—" : fmt(s.avgWatts()) + " W"));
        cards.addCell(metricCard("Peak Power", s.peakWatts() == null ? "—" : fmt(s.peakWatts()) + " W"));
        cards.addCell(metricCard(breakdownMetric, String.valueOf(breakdownCount)));
        doc.add(cards);
    }

    private PdfPCell metricCard(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(TRACK);
        cell.setBorderWidth(1f);
        cell.setPadding(10);
        cell.setBackgroundColor(ROW_ALT);
        Paragraph l = new Paragraph(label.toUpperCase(Locale.ENGLISH), font(7.5f, Font.BOLD, MUTED));
        l.setSpacingAfter(5);
        cell.addElement(l);
        cell.addElement(new Paragraph(value, font(13, Font.BOLD, INK)));
        return cell;
    }

    private void addBreakdownTable(Document doc, ReportSpec spec, List<ReportRow> rows) throws DocumentException {
        Paragraph heading = new Paragraph(spec.breakdownTitle(), font(13, Font.BOLD, INK));
        heading.setSpacingAfter(8);
        doc.add(heading);

        if (rows.isEmpty()) {
            doc.add(new Paragraph(spec.emptyMessage(), font(10, Font.ITALIC, MUTED)));
            return;
        }

        double maxKwh = rows.stream().mapToDouble(ReportRow::energyKwh).max().orElse(0);

        PdfPTable table = new PdfPTable(new float[]{2.2f, 1.6f, 3f, 1.5f, 1.5f, 1.3f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        String[] heads = {spec.firstColumn(), "Energy (kWh)", "Usage", "Avg (W)", "Peak (W)", "Samples"};
        for (int col = 0; col < heads.length; col++) {
            PdfPCell hc = new PdfPCell(new Phrase(heads[col], font(8.5f, Font.BOLD, Color.WHITE)));
            hc.setBackgroundColor(INK);
            hc.setPadding(6);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.setHorizontalAlignment((col == 0 || col == 2) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(hc);
        }

        int i = 0;
        for (ReportRow r : rows) {
            Color bg = (i++ % 2 == 0) ? Color.WHITE : ROW_ALT;
            table.addCell(bodyCell(r.label(), Element.ALIGN_LEFT, bg));
            table.addCell(bodyCell(fmt(r.energyKwh()), Element.ALIGN_RIGHT, bg));
            table.addCell(barCell(maxKwh == 0 ? 0 : r.energyKwh() / maxKwh, bg));
            table.addCell(bodyCell(fmt(r.avgWatts()), Element.ALIGN_RIGHT, bg));
            table.addCell(bodyCell(fmt(r.peakWatts()), Element.ALIGN_RIGHT, bg));
            table.addCell(bodyCell(String.valueOf(r.samples()), Element.ALIGN_RIGHT, bg));
        }
        doc.add(table);
    }

    private PdfPCell bodyCell(String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(9, Font.NORMAL, INK)));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(bg);
        return c;
    }

    private PdfPCell barCell(double ratio, Color bg) {
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(18);
        c.setBorder(Rectangle.NO_BORDER);
        c.setBackgroundColor(bg);
        c.setCellEvent(new DataBar(ratio));
        return c;
    }

    private void addFooter(Document doc) throws DocumentException {
        Paragraph f = new Paragraph(
                "Generated by Consumption Agent · "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)),
                font(8, Font.NORMAL, MUTED));
        f.setSpacingBefore(20);
        doc.add(f);
    }

    private static byte[] loadLogo() {
        try (InputStream in = new ClassPathResource("static/images/logo.png").getInputStream()) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) return null;
            int targetH = 96;
            int targetW = Math.max(1, src.getWidth() * targetH / src.getHeight());
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, targetW, targetH, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private static String fmt(Number n) {
        if (n == null) return "—";
        double v = n.doubleValue();
        return (v == Math.floor(v))
                ? String.format(Locale.US, "%,.0f", v)
                : String.format(Locale.US, "%,.2f", v);
    }

    private static final class DataBar implements PdfPCellEvent {
        private final double ratio;

        DataBar(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle pos, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float pad = 3f;
            float h = 8f;
            float x = pos.getLeft() + pad;
            float y = pos.getBottom() + (pos.getHeight() - h) / 2f;
            float wMax = pos.getWidth() - 2 * pad;

            cb.setColorFill(TRACK);
            cb.roundRectangle(x, y, wMax, h, 2.5f);
            cb.fill();

            float w = (float) Math.max(0, Math.min(1, ratio)) * wMax;
            if (w > 1f) {
                cb.setColorFill(BAR);
                cb.roundRectangle(x, y, w, h, 2.5f);
                cb.fill();
            }
        }
    }
}
