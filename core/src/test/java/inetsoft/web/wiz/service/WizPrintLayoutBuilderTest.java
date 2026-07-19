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
      // 1 title/recap block + 2 per-chart captions = 3
      assertEquals(3, editableTextBlocks);

      List<VSEditableAssemblyLayout> texts = all.stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout)
         .map(l -> (VSEditableAssemblyLayout) l)
         .collect(Collectors.toList());
      boolean hasTitleRecap = texts.stream().anyMatch(t ->
         ((TextVSAssemblyInfo) t.getInfo()).getText().contains("Q39 Board") &&
         ((TextVSAssemblyInfo) t.getInfo()).getText().contains("Premium drives revenue."));
      assertTrue(hasTitleRecap, "one editable block carries both the title and the recap");
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
      // title/recap + caption + insights = 3
      assertEquals(3, texts.size());

      VSEditableAssemblyLayout insights = texts.stream()
         .filter(t -> ((TextVSAssemblyInfo) t.getInfo()).getText().contains("Bold finding"))
         .findFirst().orElseThrow();
      String insightsText = ((TextVSAssemblyInfo) insights.getInfo()).getText();
      assertTrue(insightsText.contains("Bold finding"), "markdown bold stripped: " + insightsText);
      assertTrue(insightsText.contains("• point one"), "markdown bullet normalized: " + insightsText);
      assertFalse(insightsText.contains("**"), "no raw markdown syntax survives: " + insightsText);
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
      // title/recap + caption only = 2 (same as the baseline before this feature)
      assertEquals(2, editableTextBlocks);
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
      assertEquals(2, editableTextBlocks, "the 3-arg compatibility constructor omits insights");
   }
}
