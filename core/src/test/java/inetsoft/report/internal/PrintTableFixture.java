package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.uql.viewsheet.BorderColors;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.Reference;
import java.util.List;
import java.util.*;

/**
 * Prints one table the way print layout's converter lays it out: a TableElementDef in a section
 * band, on a US Letter page with 0.5in margins. The band starts at (36, 36), so a table at band
 * (20, 10) has its card at x = 56, y = 46, and its box, where the cells start, at x = 57 + L.
 */
final class PrintTableFixture {
   PrintTableFixture rows(int rows) {
      this.rows = rows;
      return this;
   }

   PrintTableFixture rowHeight(int rowHeight) {
      this.rowHeight = rowHeight;
      return this;
   }

   /** Give every cell these insets, the channel print layout's cell padding reaches cells by. */
   PrintTableFixture cellInsets(Insets cellInsets) {
      this.cellInsets = cellInsets;
      return this;
   }

   PrintTableFixture keepRowsWhole() {
      this.keepRowsWhole = true;
      return this;
   }

   /** Put wrapped text in the row's first cell and let its height follow the text. */
   PrintTableFixture wrappedRow(int row, String text) {
      this.wrappedRow = row;
      this.wrappedText = text;
      return this;
   }

   PrintTableFixture widths(int... widths) {
      this.widths = widths;
      return this;
   }

   PrintTableFixture layout(int layout) {
      this.layout = layout;
      return this;
   }

   PrintTableFixture headerCols(int headerCols) {
      this.headerCols = headerCols;
      return this;
   }

   PrintTableFixture noCellBorders() {
      this.cellBorders = false;
      return this;
   }

   PrintTableFixture background(Color background) {
      this.background = background;
      return this;
   }

   PrintTableFixture inset(int left, int bottom, int right) {
      this.inset = new Insets(0, left, bottom, right);
      return this;
   }

   /** Put a one-line text element in the band at y = 120, below the table's 100pt design box. */
   PrintTableFixture textBelow() {
      this.textBelow = true;
      return this;
   }

   /** The table element in its band, before anything prints. */
   TableElementDef element() {
      if(element == null) {
         build();
      }

      return element;
   }

   /** Every page the report engine produces. */
   List<StylePage> print() {
      element();
      List<StylePage> pages = new ArrayList<>();
      Enumeration<?> generated = ReportGenerator.generate(report);

      while(generated.hasMoreElements()) {
         pages.add((StylePage) generated.nextElement());
      }

      return pages;
   }

   /** Every table region, in print order. */
   List<TablePaintable> regions() {
      List<TablePaintable> regions = new ArrayList<>();

      for(StylePage page : print()) {
         regions.addAll(paintables(page, TablePaintable.class));
      }

      return regions;
   }

   static <T extends Paintable> List<T> paintables(StylePage page, Class<T> type) {
      List<T> found = new ArrayList<>();

      for(int i = 0; i < page.getPaintableCount(); i++) {
         if(type.isInstance(page.getPaintable(i))) {
            found.add(type.cast(page.getPaintable(i)));
         }
      }

      return found;
   }

   /**
    * One of this fixture's pages painted on white at one pixel per point. An element holds its
    * report weakly and painting reads it, so the fixture keeps its report alive until the paint
    * ends.
    */
   BufferedImage render(StylePage page) {
      try {
         Dimension size = page.getPageDimension();
         BufferedImage image =
            new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = image.createGraphics();
         g.setColor(Color.WHITE);
         g.fillRect(0, 0, size.width, size.height);
         page.print(g);
         g.dispose();
         return image;
      }
      finally {
         Reference.reachabilityFence(this);
      }
   }

   /** The first page, printed and painted. */
   BufferedImage renderFirstPage() {
      return render(print().get(0));
   }

   private void build() {
      report = new TabularSheet(null, null);
      report.setPageSize(new Size(8.5, 11));
      report.setMargin(new Margin(0.5, 0.5, 0.5, 0.5));

      // the section shape VsToReportConverter.createReportSection builds
      SectionElementDef section = new SectionElementDef(report);
      section.setSpacing(-1);
      section.getSection().getSectionHeader()[0].setVisible(false);
      section.getSection().getSectionFooter()[0].setVisible(false);
      SectionBand band = section.getSection().getSectionContent()[0];
      band.setHeight(300 / 72f);

      Object[][] data = new Object[rows + 1][widths.length];

      for(int c = 0; c < widths.length; c++) {
         data[0][c] = "H" + c;

         for(int r = 1; r <= rows; r++) {
            data[r][c] = "r" + r + "c" + c;
         }
      }

      if(wrappedText != null) {
         data[wrappedRow][0] = wrappedText;
      }

      DefaultTableLens lens = new DefaultTableLens(data);
      lens.setHeaderRowCount(1);
      lens.setHeaderColCount(headerCols);

      for(int r = 0; r <= rows; r++) {
         for(int c = 0; c < widths.length; c++) {
            if(cellInsets != null) {
               lens.setInsets(r, c, cellInsets);
            }
         }
      }

      if(wrappedText != null) {
         lens.setLineWrap(wrappedRow, 0, true);
      }

      if(!cellBorders) {
         lens.setRowBorder(StyleConstants.NO_BORDER);
         lens.setColBorder(StyleConstants.NO_BORDER);
      }

      element = new TableElementDef(report, lens);
      element.setEmbedWidth(true);
      element.setLayout(layout);
      element.setFixedWidths(widths);
      int[] heights = new int[rows + 1];
      Arrays.fill(heights, rowHeight);

      if(wrappedText != null) {
         heights[wrappedRow] = -1;
      }

      element.setFixedHeights(heights);
      element.setKeepRowsWhole(keepRowsWhole);
      element.setBorders(new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                    StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
      element.setBorderColors(new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED));

      if(background != null) {
         element.setBackground(background);
      }

      element.setCardInset(inset);
      band.addElement(element, new Rectangle(20, 10, 400, 100));

      if(textBelow) {
         band.addElement(new TextElementDef(report, new DefaultTextLens("below")),
                         new Rectangle(20, 120, 400, 20));
      }

      report.addElement(0, 0, section);
   }

   static final int ROW_H = 20;
   private int rows = 10;
   private int rowHeight = ROW_H;
   private Insets cellInsets;
   private boolean keepRowsWhole;
   private int wrappedRow;
   private String wrappedText;
   private int headerCols = 0;
   private int[] widths = { 100, 100, 100 };
   private int layout = ReportSheet.TABLE_FIT_PAGE;
   private boolean cellBorders = true;
   private Color background;
   private Insets inset;
   private boolean textBelow;
   private TabularSheet report;
   private TableElementDef element;
}
