package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.VSEditableAssemblyLayout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizPrintLayoutBuilderTest {
   private final WizPrintLayoutBuilder builder = new WizPrintLayoutBuilder();

   @Test
   void buildsA4PrintInfoInPortraitInches() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "a4", "Q39 Board", "Premium drives revenue.", List.of());
      assertEquals("A4 [210x297 mm]", layout.getPrintInfo().getPaperType());
      assertEquals("inches", layout.getPrintInfo().getUnit());
      assertFalse(layout.isHorizontalScreen()); // portrait
   }

   @Test
   void buildsLetterPrintInfo() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "LETTER", "Board", null, List.of());
      assertEquals("Letter [8.5x11 in]", layout.getPrintInfo().getPaperType());
   }

   @Test
   void rejectsUnknownPageSize() {
      Viewsheet vs = mock(Viewsheet.class);
      assertThrows(IllegalArgumentException.class,
         () -> builder.build(vs, "tabloid", "Board", null, List.of()));
   }

   @Test
   void headerAndFooterLayoutsAreEmpty() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "a4", "Board", null, List.of());
      assertTrue(layout.getHeaderLayouts().isEmpty());
      assertTrue(layout.getFooterLayouts().isEmpty());
   }

   private static TextVSAssembly textAssembly(Viewsheet vs, String name, int y) {
      TextVSAssembly a = new TextVSAssembly(vs, name);
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) a.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, y));
      info.setPixelSize(new Dimension(800, 400));
      vs.addAssembly(a);
      return a;
   }

   @Test
   void oneVSAssemblyLayoutPerTopLevelChartPlusOneEditableTextPerCaptionAndTitle() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      textAssembly(vs, "Chart1_2", 420);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0),
         new WizPrintLayoutBuilder.ChartCaption("Second", "cap two", 1)
      );

      PrintLayout layout = builder.build(vs, "a4", "Q39 Board", "Premium drives revenue.", charts);

      List<VSAssemblyLayout> all = layout.getVSAssemblyLayouts();
      long chartRefs = all.stream()
         .filter(l -> !(l instanceof VSEditableAssemblyLayout))
         .filter(l -> l.getName().equals("Chart1") || l.getName().equals("Chart1_2"))
         .count();
      assertEquals(2, chartRefs, "one plain VSAssemblyLayout per existing chart assembly");

      long editableTextBlocks = all.stream().filter(l -> l instanceof VSEditableAssemblyLayout).count();
      // report header (title + generated-date + summary) + 2 per-chart captions = 5
      assertEquals(5, editableTextBlocks);

      List<VSEditableAssemblyLayout> texts = all.stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout)
         .map(l -> (VSEditableAssemblyLayout) l)
         .collect(Collectors.toList());
      assertTrue(texts.stream().anyMatch(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().equals("Q39 Board")),
         "the title box carries the board name on its own");
      assertTrue(texts.stream().anyMatch(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().startsWith("Generated ")),
         "a generated-date line is present");
      assertTrue(texts.stream().anyMatch(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().contains("Premium drives revenue.")),
         "the recap becomes the summary box");
      assertTrue(texts.stream().anyMatch(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().equals("First — cap one")));
      assertTrue(texts.stream().anyMatch(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().equals("Second — cap two")));
   }

   @Test
   void skipsContainerChildrenAndAnnotationsWhenCountingTopLevelAssemblies() {
      // A dashboard with exactly 1 top-level chart but a mismatched charts list (size 2)
      // must fail loud rather than silently misattribute a caption.
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0),
         new WizPrintLayoutBuilder.ChartCaption("Second", "cap two", 1)
      );
      assertThrows(IllegalStateException.class,
         () -> builder.build(vs, "a4", "Board", null, charts));
   }

   @Test
   void addsStrippedInsightsBlockBelowChartWithoutResizingTheChart() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0, "**Bold** finding\n- point one")
      );

      PrintLayout layout = builder.build(vs, "a4", "Q39 Board", "Premium drives revenue.", charts);

      List<VSAssemblyLayout> all = layout.getVSAssemblyLayouts();

      VSAssemblyLayout chartLayout = all.stream()
         .filter(l -> !(l instanceof VSEditableAssemblyLayout))
         .filter(l -> l.getName().equals("Chart1"))
         .findFirst().orElseThrow();
      // 576x400 = PAGE_CONTENT_WIDTH_PT x CHART_HEIGHT_PT (private constants; hardcoded here since
      // this test lives in the same package but not the same class, so private members aren't visible).
      assertEquals(new Dimension(576, 400), chartLayout.getSize(),
         "chart box is not resized when insights are present");

      List<VSEditableAssemblyLayout> texts = all.stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout)
         .map(l -> (VSEditableAssemblyLayout) l)
         .collect(Collectors.toList());
      // report header (title + date + summary) + caption + insights (paragraph + bullet = 2) = 6
      assertEquals(6, texts.size());

      // The bold paragraph and the bullet become separate structured boxes (one font per box);
      // inline bold is flattened, bullet marker rendered.
      VSEditableAssemblyLayout para = texts.stream()
         .filter(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().contains("Bold finding"))
         .findFirst().orElseThrow();
      assertFalse(((TextVSAssemblyInfo) para.getInfo()).getText().contains("**"),
         "no raw markdown syntax survives: " + ((TextVSAssemblyInfo) para.getInfo()).getText());

      VSEditableAssemblyLayout bullet = texts.stream()
         .filter(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().contains("point one"))
         .findFirst().orElseThrow();
      String bulletText = ((TextVSAssemblyInfo) bullet.getInfo()).getText();
      assertTrue(bulletText.startsWith("•"), "bullet marker rendered: " + bulletText);
      assertFalse(bulletText.contains("- point"), "raw bullet dash stripped: " + bulletText);
   }

   @Test
   void omitsInsightsBlockWhenBlank() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0, "   ")
      );

      PrintLayout layout = builder.build(vs, "a4", "Q39 Board", "Premium drives revenue.", charts);

      long editableTextBlocks = layout.getVSAssemblyLayouts().stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout).count();
      // report header (title + date + summary) + caption, no insights block = 4
      assertEquals(4, editableTextBlocks);
   }

   @Test
   void reportHeaderSplitsTitleDateAndStrippedSummaryWithStyledFonts() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0)
      );

      PrintLayout layout = builder.build(vs, "letter", "Odoo — Category Revenue (Q39)",
         "**Premium units run the business** — the $1,500+ band is ~69% of revenue.", charts);

      List<VSEditableAssemblyLayout> texts = layout.getVSAssemblyLayouts().stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout)
         .map(l -> (VSEditableAssemblyLayout) l)
         .collect(Collectors.toList());

      VSEditableAssemblyLayout title = texts.stream().filter(t -> t.getName().equals("wizExportTitle"))
         .findFirst().orElseThrow();
      assertEquals("Odoo — Category Revenue (Q39)", ((TextVSAssemblyInfo) title.getInfo()).getText());
      // title is rendered larger than the 11pt body default
      assertTrue(((TextVSAssemblyInfo) title.getInfo()).getFormat().getFont().getSize() > 11,
         "title uses a larger-than-body font");
      assertTrue(((TextVSAssemblyInfo) title.getInfo()).getFormat().getFont().isBold(), "title is bold");

      VSEditableAssemblyLayout date = texts.stream().filter(t -> t.getName().equals("wizExportDate"))
         .findFirst().orElseThrow();
      assertTrue(((TextVSAssemblyInfo) date.getInfo()).getText().startsWith("Generated "),
         "date line: " + ((TextVSAssemblyInfo) date.getInfo()).getText());

      VSEditableAssemblyLayout summary = texts.stream()
         .filter(t -> t.getName().startsWith("wizExportSummary"))
         .findFirst().orElseThrow();
      String summaryText = ((TextVSAssemblyInfo) summary.getInfo()).getText();
      assertTrue(summaryText.contains("Premium units run the business"), "recap kept: " + summaryText);
      assertFalse(summaryText.contains("**"), "recap markdown stripped: " + summaryText);
   }

   @Test
   void generatedDateLineIsHumanReadable() {
      assertTrue(new WizPrintLayoutBuilder().generatedDateLine().matches("Generated \\w+ \\d{1,2}, \\d{4}"),
         "expected e.g. 'Generated July 21, 2026'");
   }

   @Test
   void setsScaleFontToOneSoTableCellFontsAreNotZeroSized() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "letter", "Board", null, List.of());
      // A bare PrintLayout leaves scaleFont at its 0f default; AbstractLayout.apply() then stamps
      // RScaleFont=0 onto every assembly's cell formats, and VSCompositeFormat.getFont() multiplies
      // the font size by rscaleFont — so crosstab/table cells render at font size 0 (invisible text
      // and zero-width auto columns) while charts, painted by the graph engine, are unaffected.
      // 1f == "no font scaling", matching an interactively-created print layout.
      assertEquals(1f, layout.getScaleFont(),
         "crosstab/table cell fonts must not be scaled to zero in the board-export print layout");
   }

   @Test
   void threeArgChartCaptionStillCompilesAndOmitsInsights() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0)
      );

      PrintLayout layout = builder.build(vs, "a4", "Board", null, charts);

      long editableTextBlocks = layout.getVSAssemblyLayouts().stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout).count();
      // null recap -> report header is title + date only (no summary box), + caption = 3
      assertEquals(3, editableTextBlocks, "the 3-arg compatibility constructor omits insights");
   }
}
