/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.service;

import inetsoft.report.StyleConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds one selection control per {@link FilterRequest}, binds it to each merged chart's own
 * final bound worksheet table that exposes the requested column, and positions the controls as
 * a top filter bar on an already-composed dashboard {@link Viewsheet}.
 *
 * <h2>Binds to each chart's own table, not the shared root table</h2>
 *
 * An earlier version bound filters to every root/physical worksheet table exposing the column
 * ({@link AddFilterService#findColumnMatchingRootTables}) — correct for a simple chart, but a
 * selection filter is applied as a pre-condition on the named table, which then cascades
 * through every join/mirror built on it. For a chart whose worksheet computes a global
 * aggregate downstream of that same root table (e.g. cross-category min/max feeding a
 * radar-chart normalization), filtering the root table collapses the aggregate to the filtered
 * subset too — confirmed live: selecting one category made a radar chart's own normalization
 * divide by zero (min==max after the collapse) and the chart went blank ("No data is
 * available"), even though every other chart correctly filtered. {@link #build} now matches
 * against each merged chart's own final bound table ({@link AddFilterService#findColumnMatchingChartTables})
 * instead — for a normalization pipeline that table sits downstream of the already-computed
 * aggregate, so filtering it only narrows the display rows. A chart whose own final table
 * doesn't expose the column (e.g. one already permanently scoped to a single category, so the
 * column was dropped before its last group-by) simply isn't bound to that filter, which is
 * correct: it was never meaningfully controllable by it in the first place.
 *
 * <h2>Reuse seam (decided in Step 1, after reading {@code AddFilterService} in full)</h2>
 *
 * {@code AddFilterService} (the reference implementation of adding a single selection
 * filter from the chat/composer flow) already contains three pieces this builder needs:
 * <ul>
 *   <li>{@code createFilterAssembly(Viewsheet, String, ColumnRef)} — the data-type →
 *       control-type branch (numeric/date → {@link TimeSliderVSAssembly} with a
 *       {@link SingleTimeInfo}; everything else → {@link SelectionListVSAssembly} via
 *       {@code setDataRef}). Was {@code private}; changed to package-visible {@code static}
 *       (it touches no instance state). {@link #createControlForType} below is a <b>thin
 *       wrapper</b> around it (reuse decision (b) from the task brief) — no second copy of
 *       the type-branch logic exists.</li>
 *   <li>{@code buildColumnRef(String, String)} — the {@code AttributeRef}+{@code ColumnRef}
 *       construction (was inlined at the old lines 75-78). Extracted to a package-visible
 *       {@code static} helper (reuse decision (a)) that both {@code AddFilterService} and
 *       this builder call.</li>
 *   <li>{@code findColumnMatchingRootTables(Worksheet, String)} — the root-table/column-name
 *       matching loop, extracted from {@code findTablesWithColumn}'s body (reuse decision
 *       (a)). {@code AddFilterService.findTablesWithColumn} does its own
 *       {@code AssetRepository}/{@code Principal} reload first (needed there because
 *       {@code AddVisualizationService} saves the merged worksheet to the repository and
 *       repoints the runtime {@code Viewsheet}'s base entry via {@code setBaseEntry(...)}, but
 *       never refreshes the runtime {@code Viewsheet}'s own transient {@code ws} field — so
 *       {@code vs.getBaseWorksheet()} would otherwise return the stale/empty pre-merge
 *       worksheet), loading with {@code permission=false} — deliberately, since the merged base
 *       worksheet is a system-generated, already-gated internal/ephemeral entry, and a
 *       {@code permission=true} load can fail the ACL check (or return a stripped sheet) for a
 *       principal with no explicit grant on that ephemeral entry, reproducing the exact
 *       every-filter-skipped symptom this reuse is meant to avoid — then delegates to this
 *       shared loop. <b>This builder has the identical staleness problem</b> and is deliberately
 *       <b>not</b> handed a {@code Viewsheet} to read {@code getBaseWorksheet()} from for
 *       matching: {@link #build} instead takes the already-loaded {@code Worksheet} directly as
 *       a parameter, so its caller ({@link WizDashboardService#composeDashboard}) is forced to
 *       load it itself — with {@code permission=false} — exactly as
 *       {@code AddFilterService.findTablesWithColumn} does inline, rather than risk a second
 *       copy of the load drifting to a different (incorrect) permission flag over time.</li>
 * </ul>
 *
 * <p><b>Confirmed signatures</b> (against the real {@code AddFilterService}, not the task
 * brief's sketch, which diverged on one point): {@code ColumnRef} lives in
 * {@code inetsoft.uql.asset} (not {@code inetsoft.uql.erm} as the brief assumed) and is
 * built as {@code new ColumnRef(AttributeRef)} + {@code setDataType(String)}.
 * {@code AbstractSelectionVSAssembly} does not itself declare {@code setTitleValue} — both
 * {@code SelectionListVSAssembly} (via {@code CompositeVSAssembly extends TitledVSAssembly})
 * and {@code TimeSliderVSAssembly} (implements {@code TitledVSAssembly} directly) do, so the
 * title is set through a cast to the common {@link TitledVSAssembly} interface.</p>
 *
 * <p><b>Deferred to manual E2E</b> (per the brief; no synthetic worksheet fixture is
 * fabricated here): column-matching across merged root tables ({@link #build}'s table-lookup
 * branch) and actual runtime filtering of charts both require a live composed dashboard with a
 * real, permission=false-loaded worksheet. The automated coverage in
 * {@code WizDashboardFilterBuilderTest} is limited to the pure data-type → control-type
 * branch via the package-visible {@link #createControlForType}.</p>
 */
@Component
public class WizDashboardFilterBuilder {
   public record FilterRequest(String field, String dataType, String label) {}

   public record FilterResult(List<String> applied, List<String> skipped,
                               List<FilterControlPlacement> placements) {
      /** Compatibility constructor for existing callers/tests that only care about
       *  applied/skipped (e.g. {@code WizDashboardServiceGridTest}'s
       *  {@code applyFiltersMapsSpecsToFilterRequestsAndReturnsBuilderResult}, which constructs
       *  a {@code FilterResult} directly as its mocked return value) -- defaults
       *  {@code placements} to empty rather than requiring every such call site to be touched. */
      public FilterResult(List<String> applied, List<String> skipped) {
         this(applied, skipped, List.of());
      }
   }

   public record FilterControlPlacement(String assemblyName, java.awt.Point position, java.awt.Dimension size) {}

   /**
    * Builds one selection control per request, binds it to each merged chart's own final bound
    * table that exposes the column (see the class Javadoc for why this is NOT every root table
    * exposing the column), positions the controls as a top filter bar, and adds them to
    * {@code vs}.
    *
    * <p><b>Caller contract:</b> {@code baseWorksheet} must be the dashboard's merged base
    * worksheet, loaded directly from the repository with {@code permission=false} — i.e.
    * {@code assetRepository.getSheet(vs.getBaseEntry(), principal, false, AssetContent.ALL)},
    * the exact mirror of {@code AddFilterService.findTablesWithColumn}. Do <b>not</b> pass
    * {@code vs.getBaseWorksheet()}: {@code AddVisualizationService}'s merge never refreshes the
    * runtime {@code Viewsheet}'s own transient {@code ws} field, so it would be stale/empty; and
    * do not load with {@code permission=true} ({@link Viewsheet#reloadBaseWorksheet}'s flag) —
    * see the class Javadoc reuse-seam note for why that can fail the ACL check on this
    * system-generated ephemeral entry. Passing a stale or wrongly-permissioned worksheet here
    * does not fail loud: every field simply lands in {@link FilterResult#skipped()}, since this
    * method has no way to distinguish "genuinely no matching table" from "caller loaded the
    * wrong worksheet" — getting the load right in the caller matters.</p>
    *
    * <p>{@code startX} is the x-pixel the first control is placed at (subsequent controls tile to
    * its right). Callers pass the merged charts' actual left edge so the bar lines up with the
    * columns below it, rather than the raw canvas margin — the chart merge offsets each chart by
    * +CANVAS_MARGIN, so the two would otherwise be misaligned (same offset the per-chart filters
    * correct for).
    *
    * @return which fields bound to &gt;=1 table (applied) vs none (skipped).
    */
   public FilterResult build(Viewsheet vs, Worksheet baseWorksheet, List<FilterRequest> requests, int startX) {
      List<String> applied = new ArrayList<>();
      List<String> skipped = new ArrayList<>();
      List<FilterControlPlacement> placements = new ArrayList<>();
      int x = startX;
      int y = FILTER_BAR_Y;
      List<String> chartTableNames = mergedChartTableNames(vs);

      for(FilterRequest req : requests) {
         List<String> tables =
            AddFilterService.findColumnMatchingChartTables(baseWorksheet, chartTableNames, req.field());

         if(tables.isEmpty()) {
            skipped.add(req.field());
            continue;
         }

         ColumnRef colRef = AddFilterService.buildColumnRef(req.field(), req.dataType());
         AbstractSelectionVSAssembly control = createControlForType(vs, req.dataType(), colRef);

         if(req.label() != null && control instanceof TitledVSAssembly titled) {
            titled.setTitleValue(req.label());
         }

         Point pos = new Point(x, y);
         java.awt.Dimension size = new java.awt.Dimension(FILTER_CONTROL_WIDTH, FILTER_CONTROL_HEIGHT);
         control.setTableNames(tables);
         control.setPixelOffset(pos);
         // Explicit compact size: SelectionList/TimeSlider's own default pixel size is not
         // reliably short, and WizDashboardService reserves only FILTER_BAR_ROW_HEIGHT (120px)
         // above the charts -- an oversized control here would visually collide with the first
         // chart row instead of leaving the intended small gap.
         control.setPixelSize(size);
         vs.addAssembly(control);
         applied.add(req.field());
         placements.add(new FilterControlPlacement(control.getName(), pos, size));
         x += FILTER_CONTROL_WIDTH;
      }

      return new FilterResult(applied, skipped, placements);
   }

   /**
    * Adds a full-width "filter toolbar" band behind the shared filter bar so its controls read as
    * a distinct toolbar region rather than looking attached to the first chart below: a subtly
    * tinted background rectangle spanning the chart area's width ({@code x}..{@code x+width}), and
    * a thin gray divider bar along its bottom edge. Both are plain {@link RectangleVSAssembly}s
    * styled purely via a background fill — the same VSCompositeFormat render path already proven to
    * render for the selection controls — and MUST be added before the controls (this method is
    * called first) so the controls layer on top of the band.
    *
    * <p>Returns the two rectangles' placements so the caller can carry them into the adaptive
    * layout tiers, exactly as it already carries the filter controls' placements (otherwise an
    * assembly with no per-tier layout entry is hidden when that tier is selected).
    */
   public List<FilterControlPlacement> buildFilterBarBand(Viewsheet vs, int x, int y, int width, int height) {
      List<FilterControlPlacement> placements = new ArrayList<>();
      placements.add(addFillRectangle(vs, "wizFilterBarBand", x, y, width, height, BAND_BACKGROUND));
      placements.add(addFillRectangle(vs, "wizFilterBarDivider",
         x, y + height - DIVIDER_THICKNESS, width, DIVIDER_THICKNESS, DIVIDER_COLOR));
      return placements;
   }

   /**
    * Creates a borderless {@link RectangleVSAssembly} rendered as a solid fill of {@code fill} at
    * the given pixel bounds and adds it to {@code vs}. Uses setBackgroundValue (the persist-safe
    * "...Value" setter that survives save/reload, like the card styling) plus the plain setter for
    * any pre-persist read, and NO_BORDER line style so only the fill shows.
    */
   private static FilterControlPlacement addFillRectangle(
      Viewsheet vs, String name, int x, int y, int width, int height, Color fill)
   {
      RectangleVSAssembly rect = new RectangleVSAssembly(vs, name);
      rect.initDefaultFormat();
      RectangleVSAssemblyInfo info = (RectangleVSAssemblyInfo) rect.getVSAssemblyInfo();
      Point pos = new Point(x, y);
      Dimension size = new Dimension(width, height);
      info.setPixelOffset(pos);
      info.setPixelSize(size);
      info.setLineStyleValue(StyleConstants.NO_BORDER);
      VSFormat fmt = info.getFormat().getUserDefinedFormat();
      fmt.setBackground(fill);
      fmt.setBackgroundValue(String.format("#%02x%02x%02x", fill.getRed(), fill.getGreen(), fill.getBlue()));
      vs.addAssembly(rect);
      return new FilterControlPlacement(rect.getName(), pos, size);
   }

   /**
    * Builds ONE selection/range control scoped to exactly one chart's own table, positioned at
    * the given fixed point (the top of that chart's own tile) and sized to {@code width} ×
    * {@code height} — the caller passes the CHART's own width and the reserved header height so
    * the control spans the full width of, and sits flush against the top of, the chart it
    * filters. Unlike {@link #build}, there is no multi-table candidate search — the single
    * {@code chartTableName} IS the candidate, so binding is unambiguous by construction (no
    * residual name-collision risk across charts).
    *
    * <p>When {@code chartAssemblyName} resolves to a real assembly on {@code vs}, the control and
    * that chart are styled as one visually-grouped "card" (matching background, a shared border
    * enclosing the pair with no seam where they abut) via {@link #applyGroupedCardStyle} —
    * otherwise the two would render as independent, visually disconnected floating elements. A
    * {@code null}/unresolvable name (e.g. a caller that hasn't tracked the assembly name) just
    * skips the styling, not the filter itself.
    *
    * @return the placement (assembly name/position/size) of the created control, if the field
    *         was found on the chart's table and a control was added; {@code null} if skipped
    *         (field not present on this chart's own table).
    */
   public FilterControlPlacement buildPerChart(Viewsheet vs, Worksheet baseWorksheet, int x, int y,
                                                int width, int height, FilterRequest request,
                                                String chartTableName, String chartAssemblyName)
   {
      List<String> tables = AddFilterService.findColumnMatchingChartTables(
         baseWorksheet, List.of(chartTableName), request.field());

      if(tables.isEmpty()) {
         return null;
      }

      ColumnRef colRef = AddFilterService.buildColumnRef(request.field(), request.dataType());
      AbstractSelectionVSAssembly control = createControlForType(vs, request.dataType(), colRef);

      if(request.label() != null && control instanceof TitledVSAssembly titled) {
         titled.setTitleValue(request.label());
      }

      Point pos = new Point(x, y);
      java.awt.Dimension size = new java.awt.Dimension(width, height);
      control.setTableNames(tables);
      control.setPixelOffset(pos);
      control.setPixelSize(size);
      vs.addAssembly(control);

      VSAssembly chartAssembly = chartAssemblyName != null ? vs.getAssembly(chartAssemblyName) : null;

      if(chartAssembly != null) {
         applyGroupedCardStyle(control, chartAssembly);
      }

      return new FilterControlPlacement(control.getName(), pos, size);
   }

   /**
    * Styles a per-chart filter control and the ONE chart it filters so they read as a single
    * visually-grouped card, instead of two independent floating assemblies with no visual
    * relationship — matching background fill on both, and a border drawn around the OUTER edge
    * of the pair only (the control's bottom edge and the chart's top edge, which abut directly,
    * are left borderless so there's no doubled seam line between them).
    *
    * <p>Deliberately direct-format styling rather than a real {@link Assembly} grouping construct
    * (e.g. a group container) — StyleBI's actual grouping mechanism carries real structural
    * invariants (child position sync, z-index recompute, tab-membership, dependency-cycle
    * checks) that a live rendering feedback loop is needed to get right; two independently
    * positioned assemblies with matching format achieve the same visual effect with no new
    * invariants to maintain.
    */
   private static void applyGroupedCardStyle(VSAssembly filterControl, VSAssembly chartAssembly) {
      String backgroundHex = toHex(CARD_BACKGROUND);
      BorderColors borderColors = new BorderColors(CARD_BORDER_COLOR, CARD_BORDER_COLOR,
         CARD_BORDER_COLOR, CARD_BORDER_COLOR);

      VSFormat filterFormat = filterControl.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      applyCardFormat(filterFormat, backgroundHex, borderColors, new Insets(
         // Insets(top, left, bottom, right) -- no bottom border: the chart's own top edge (also
         // borderless, below) abuts it directly.
         StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
         StyleConstants.NO_BORDER, StyleConstants.THIN_LINE));

      VSFormat chartFormat = chartAssembly.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      applyCardFormat(chartFormat, backgroundHex, borderColors, new Insets(
         // No top border -- abuts the filter control's borderless bottom edge directly.
         StyleConstants.NO_BORDER, StyleConstants.THIN_LINE,
         StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
   }

   /**
    * Sets background/border-colors/borders via BOTH the plain setters AND the "...Value" variants.
    * {@link VSCompositeFormat}'s effective getters (what actually renders) fall back to the
    * user-defined layer when {@code isXxxDefined() || isXxxValueDefined()} — confirmed live
    * (reloading a saved dashboard directly from the repository) that the plain setters' "xxxDefined"
    * boolean does NOT survive a save/reload round-trip even though the value itself does, while the
    * "...Value" setters (the pattern used throughout the codebase, e.g. {@code ChartVSAssemblyInfo},
    * {@code TableDataVSAssemblyInfo}, for exactly this kind of persisted format) flip
    * "isXxxValueDefined" instead. Calling both covers persistence (the Value variants) and any
    * immediate/pre-refresh read of the plain RValue-based getters.
    */
   private static void applyCardFormat(
      VSFormat format, String backgroundHex, BorderColors borderColors, Insets borders)
   {
      format.setBackground(CARD_BACKGROUND);
      format.setBackgroundValue(backgroundHex);
      format.setBorderColors(borderColors);
      format.setBorderColorsValue(borderColors);
      format.setBorders(borders);
      format.setBordersValue(borders);
   }

   private static String toHex(Color c) {
      return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
   }

   /** Shared card background for a per-chart filter + its chart -- a light, low-contrast tint so
    *  the grouping reads clearly without competing with the chart's own colors. */
   private static final Color CARD_BACKGROUND = new Color(244, 246, 249);

   /** Shared card border color -- a clearly-visible-but-neutral gray that outlines the grouped
    *  filter+chart without drawing attention away from the chart content itself. */
   private static final Color CARD_BORDER_COLOR = new Color(158, 166, 178);

   /** Filter-toolbar band fill -- a light blue-gray tint, subtly darker than the dashboard canvas,
    *  so the shared filter bar reads as its own toolbar region. */
   private static final Color BAND_BACKGROUND = new Color(235, 238, 243);

   /** Filter-toolbar bottom divider color -- matches {@link #CARD_BORDER_COLOR} for consistency
    *  with the per-chart cards. */
   private static final Color DIVIDER_COLOR = new Color(158, 166, 178);

   /** Divider bar thickness, in pixels. */
   private static final int DIVIDER_THICKNESS = 2;

   /**
    * Collects each merged chart's own final bound table name (its {@code SourceInfo.source},
    * exposed via {@link BindableVSAssembly#getTableName()}) — the candidate set
    * {@link AddFilterService#findColumnMatchingChartTables} matches against, instead of every
    * root worksheet table. Only {@link ChartVSAssembly} is considered: every visualization
    * {@link AddVisualizationService} merges into a dashboard is a single saved chart, so this is
    * the set of "each chart's own table," not an incidental subset of bindable assembly types.
    * Package-visible for the unit test.
    */
   List<String> mergedChartTableNames(Viewsheet vs) {
      List<String> names = new ArrayList<>();

      for(Assembly a : vs.getAssemblies()) {
         if(a instanceof ChartVSAssembly chart && chart.getTableName() != null) {
            names.add(chart.getTableName());
         }
      }

      return names;
   }

   /**
    * Package-visible for the unit test — the data-type → control-type branch. Thin wrapper
    * around {@code AddFilterService.createFilterAssembly}; see the class Javadoc reuse-seam
    * note. {@code colRef} is accepted as {@link DataRef} to match the test brief's helper
    * signature but must in practice be a {@link ColumnRef} (as {@code AddFilterService}
    * requires).
    */
   AbstractSelectionVSAssembly createControlForType(Viewsheet vs, String dtype, DataRef colRef) {
      return AddFilterService.createFilterAssembly(vs, dtype, (ColumnRef) colRef);
   }

   /** Matches {@link WizDashboardService#CANVAS_MARGIN} so the filter bar aligns with the left
    *  edge of the chart grid below it, instead of sitting flush against the canvas edge. */
   private static final int FILTER_BAR_X = WizDashboardService.CANVAS_MARGIN;
   private static final int FILTER_BAR_Y = WizDashboardService.CANVAS_MARGIN;
   private static final int FILTER_CONTROL_WIDTH = 200;

   /** Control height, in pixels — leaves a small margin under
    *  {@link WizDashboardService#FILTER_BAR_ROW_HEIGHT} (120px), the reserved row above charts. */
   private static final int FILTER_CONTROL_HEIGHT = 100;
}
