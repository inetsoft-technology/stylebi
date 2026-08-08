package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
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

   /**
    * LIVE BUG. A composed dashboard is no longer charts-only: since the dashboard-filter feature it
    * also carries interactive filter CONTROLS plus the bar's decoration (caption text, band and
    * divider rectangles). resolveTopLevelAssemblies counted all of them, so the guard rejected
    * every board that had any filter — "Dashboard has 13 top-level assemblies but 5 charts were
    * requested" — and because that 400 carries a JSON body while the caller asks for
    * application/pdf, it reached the user as an unexplained "Couldn't export PDF".
    */
   /**
    * LIVE BUG, seen in an exported PDF: the first chart's insights prose collided with the SECOND
    * chart's caption and plot.
    *
    * <p>Each chart was pinned at {@code page * pageStride} with {@code page++} after it, which
    * assumes a chart's block always fits inside one stride. It does not: page 1 also carries the
    * report header, and an insights block is as tall as its prose. addMarkdownBlock already
    * measured and RETURNED that bottom — the caller discarded it.
    */
   @Test
   void aTallInsightsBlockPushesTheNextChartPastItInsteadOfUnderneathIt() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      textAssembly(vs, "Chart2", 420);

      String longInsights = ("**Finding.** " + "Lorem ipsum dolor sit amet consectetur. ".repeat(60));
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0, longInsights),
         new WizPrintLayoutBuilder.ChartCaption("Second", "cap two", 1)
      );

      PrintLayout layout = builder.build(vs, "letter", "Board", "a recap line", charts);

      int insightsBottom = bottomOf(layout, "wizMarkdownInsights_0");
      int secondCaptionTop = topOf(layout, "wizExportCaption_1");
      int secondChartTop = topOf(layout, "Chart2");

      assertTrue(secondCaptionTop >= insightsBottom,
         "the second chart's caption (y=" + secondCaptionTop + ") must start at or below the first " +
         "chart's insights block (bottom=" + insightsBottom + ") — otherwise they overprint");
      assertTrue(secondChartTop > insightsBottom,
         "the second chart itself must clear the first chart's insights");
   }

   @Test
   void everyChartStillGetsItsOwnPageWhenTheContentIsShort() {
      // The overflow fix must not collapse two short charts onto one page.
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      textAssembly(vs, "Chart2", 420);
      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "c1", 0),
         new WizPrintLayoutBuilder.ChartCaption("Second", "c2", 1)
      );

      PrintLayout layout = builder.build(vs, "letter", "Board", null, charts);

      // Letter stride = (11 - 0.55 - 0.55) * 72, TRUNCATED — the same arithmetic as
      // VsToReportConverter.getPageContentSize(), which is what the converter paginates against.
      // Rounding up to 713 instead put each page's content one point past the converter's own page
      // boundary, so getPageNumber() read it as belonging to the next page.
      int stride = (int) ((11.0 - 0.55 - 0.55) * 72.0);
      assertEquals(712, stride, "guards the truncation, not just the expression");
      assertEquals(stride, topOf(layout, "wizExportCaption_1"),
         "with short content the second chart still starts exactly one page down");
   }

   private static int topOf(PrintLayout layout, String name) {
      return layout.getVSAssemblyLayouts().stream()
         .filter(l -> name.equals(l.getName()))
         .findFirst().orElseThrow(() -> new AssertionError("no layout named " + name))
         .getPosition().y;
   }

   private static int bottomOf(PrintLayout layout, String name) {
      VSAssemblyLayout l = layout.getVSAssemblyLayouts().stream()
         .filter(x -> name.equals(x.getName()))
         .findFirst().orElseThrow(() -> new AssertionError("no layout named " + name));
      return l.getPosition().y + l.getSize().height;
   }

   @Test
   void ignoresFilterControlsAndFilterBarDecorationWhenCountingTopLevelAssemblies() {
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      textAssembly(vs, "Chart2", 420);

      // A shared-bar control and a per-chart control: both are SelectionVSAssembly.
      SelectionListVSAssembly shared = new SelectionListVSAssembly(vs, "sharedFilter_name");
      shared.getVSAssemblyInfo().setPixelOffset(new Point(0, 900));
      shared.getVSAssemblyInfo().setPixelSize(new Dimension(200, 60));
      vs.addAssembly(shared);

      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "perChartFilter_due_date");
      slider.getVSAssemblyInfo().setPixelOffset(new Point(220, 900));
      slider.getVSAssemblyInfo().setPixelSize(new Dimension(200, 60));
      vs.addAssembly(slider);

      // The bar's own decoration — a Text, which is ALSO how a KPI tile renders, so it can only be
      // told apart by the builder's name prefix.
      textAssembly(vs, WizDashboardFilterBuilder.DECORATION_NAME_PREFIX + "Label_sharedFilter_name", 960);
      textAssembly(vs, WizDashboardFilterBuilder.DECORATION_NAME_PREFIX + "BarBand", 980);
      textAssembly(vs, WizDashboardFilterBuilder.DECORATION_NAME_PREFIX + "BarDivider", 1000);

      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0),
         new WizPrintLayoutBuilder.ChartCaption("Second", "cap two", 1)
      );

      // 7 top-level assemblies, 2 charts requested — must build, not throw.
      PrintLayout layout = builder.build(vs, "a4", "Board", null, charts);

      List<String> laidOut = layout.getVSAssemblyLayouts().stream()
         .filter(l -> !(l instanceof VSEditableAssemblyLayout))
         .map(VSAssemblyLayout::getName)
         .collect(Collectors.toList());
      assertEquals(List.of("Chart1", "Chart2"), laidOut,
         "only the real board tiles get a page — no filter control and no bar decoration");
   }

   @Test
   void stillFailsLoudWhenTheREALTileCountDisagrees() {
      // The guard must keep its teeth: excluding filters must not turn a genuine desync into a
      // silently mis-captioned export.
      Viewsheet vs = new Viewsheet();
      textAssembly(vs, "Chart1", 0);
      SelectionListVSAssembly f = new SelectionListVSAssembly(vs, "sharedFilter_name");
      f.getVSAssemblyInfo().setPixelOffset(new Point(0, 900));
      f.getVSAssemblyInfo().setPixelSize(new Dimension(200, 60));
      vs.addAssembly(f);

      List<WizPrintLayoutBuilder.ChartCaption> charts = List.of(
         new WizPrintLayoutBuilder.ChartCaption("First", "cap one", 0),
         new WizPrintLayoutBuilder.ChartCaption("Second", "cap two", 1)
      );
      assertThrows(IllegalStateException.class, () -> builder.build(vs, "a4", "Board", null, charts));
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
   void addsMarkdownInsightsBoxBelowChartWithoutResizingTheChart() {
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
      // width = A4's printable content width (paper minus left/right margins, the width the
      // converter paints into); height = CHART_HEIGHT_PT (a private constant, hardcoded here since
      // this test lives in the same package but not the same class, so private members aren't visible).
      assertEquals(new Dimension(printableContentWidthPt(layout), 400), chartLayout.getSize(),
         "chart box is not resized when insights are present");

      List<VSEditableAssemblyLayout> texts = all.stream()
         .filter(l -> l instanceof VSEditableAssemblyLayout)
         .map(l -> (VSEditableAssemblyLayout) l)
         .collect(Collectors.toList());
      // report header (title + date + summary) + caption + insights = 5 (insights is one markdown box)
      assertEquals(5, texts.size());

      // The insights box is named "wizMarkdown*" (so the converter renders it via MarkdownPresenter)
      // and carries the RAW markdown as its value; stripping/styling happens at paint time.
      VSEditableAssemblyLayout insights = texts.stream()
         .filter(t -> t.getName().startsWith("wizMarkdownInsights"))
         .findFirst().orElseThrow();
      String raw = ((TextVSAssemblyInfo) insights.getInfo()).getText();
      assertTrue(raw.contains("**Bold**"), "raw markdown preserved for the presenter: " + raw);
      assertTrue(raw.contains("- point one"), "raw bullet preserved for the presenter: " + raw);
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
   void reportHeaderSplitsTitleDateAndMarkdownSummaryWithStyledFonts() {
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

      // The summary is a markdown box (rendered richly by MarkdownPresenter at paint time); it
      // carries the RAW recap markdown as its value.
      VSEditableAssemblyLayout summary = texts.stream()
         .filter(t -> t.getName().startsWith("wizMarkdownSummary"))
         .findFirst().orElseThrow();
      String summaryText = ((TextVSAssemblyInfo) summary.getInfo()).getText();
      assertTrue(summaryText.contains("Premium units run the business"), "recap kept: " + summaryText);
      assertTrue(summaryText.contains("**"), "raw markdown preserved for the presenter: " + summaryText);
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

   /**
    * LIVE BUG. An exported board's insights prose was cut off mid-sentence at the bottom of a page —
    * the tail of the paragraph simply never appeared, and the next page began with the next chart.
    *
    * The block is a markdown box whose height the builder reserves from
    * MarkdownPresenter.getPreferredSize(md, width). It passed a hardcoded 8in (576pt) as that width,
    * but the box is PAINTED at the page's real printable width — VsToReportConverter's
    * getPageContentSize() is (paper - left - right) * 72, i.e. 544pt on Letter and 527pt on A4 at
    * this layout's margins. Narrower paint width means MORE wrapped lines than were measured, and
    * PainterElementDef.getPainterPreferredSize() returns the element's EXPLICIT size when one is
    * set, so isEnd() stops the painter dead at the reserved height: every line past it is dropped,
    * silently. ~6% under-measurement is one or two lines on a full-page block — exactly the observed
    * "cut off at the bottom of page three".
    *
    * The measure width must equal the paint width, for both page sizes.
    */
   @Test
   void insightsBoxReservesTheHeightItsTextNeedsAtThePageWidthItIsPaintedAt() {
      String insights = """
         **The warning on the counted view did not survive contact with the data — weighting by \
         effort barely changes anything.**

         The expectation going in was that a count would mislead, because it treats a 160-hour Epic \
         and a 2-hour Bug alike. It doesn't here. Re-weighting by hours leaves the ranking identical \
         and each tier's share of the whole within about a point of where it was, and the spread even \
         narrows slightly. Whatever you concluded from the counted chart still stands.

         **The reason is the more useful finding: priority and size are close to independent.** \
         Urgent items are only slightly larger on average than low-priority ones — a gradient exists, \
         but a weak one. That matters because effort on this dataset scales *strongly* with \
         work-package type, Epics running an order of magnitude above Bugs.

         **So what:** priority is safe to analyse by row count, which is the cheaper and more robust \
         cut — you are not hiding an effort story behind it. The corollary is the caution: do not use \
         priority to forecast capacity.

         **Better cuts:** priority against type, to confirm directly that the two are independent \
         rather than inferring it from the flatness here; or priority against status, which asks the \
         question this chart cannot — whether urgent work is actually being cleared or just piling up.
         """;

      for(String pageSize : List.of("letter", "a4")) {
         Viewsheet vs = new Viewsheet();
         textAssembly(vs, "Chart1", 0);
         PrintLayout layout = builder.build(vs, pageSize, "Project maturity", null,
            List.of(new WizPrintLayoutBuilder.ChartCaption("Priority vs effort", null, 0, insights)));

         VSEditableAssemblyLayout box = layout.getVSAssemblyLayouts().stream()
            .filter(l -> l instanceof VSEditableAssemblyLayout)
            .map(l -> (VSEditableAssemblyLayout) l)
            .filter(t -> t.getName().startsWith("wizMarkdownInsights"))
            .findFirst().orElseThrow();

         int paintWidth = printableContentWidthPt(layout);

         assertEquals(paintWidth, box.getSize().width, pageSize +
            ": the markdown box must be as wide as the page's printable area, since that is the " +
            "width VsToReportConverter paints it at");

         MarkdownPresenter presenter = new MarkdownPresenter();
         presenter.setFont(inetsoft.uql.viewsheet.internal.VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11));
         int needed = presenter.getPreferredSize(insights, paintWidth).height;

         assertTrue(box.getSize().height >= needed, pageSize +
            ": reserved " + box.getSize().height + "pt for a block that needs " + needed +
            "pt when wrapped at the " + paintWidth + "pt paint width — the overflow is clipped, " +
            "not flowed, so the tail of the prose is lost");
      }
   }

   /** VsToReportConverter.getPageContentSize().width, recomputed from the layout's own print info. */
   private static int printableContentWidthPt(PrintLayout layout) {
      inetsoft.uql.viewsheet.vslayout.PrintInfo info = layout.getPrintInfo();
      inetsoft.report.Margin margin = info.getMargin();
      return (int) ((info.getSize().getWidth() - margin.left - margin.right) * 72.0);
   }
}
