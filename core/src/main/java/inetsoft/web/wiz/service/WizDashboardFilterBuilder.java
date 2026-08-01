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
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TitledVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.asset.internal.AssetUtil;
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
   public record FilterRequest(String field, String dataType, String label, boolean preAggregation) {
      /** Compatibility constructor defaulting to post-aggregation (the original binding) -- used by
       *  the per-chart path and by tests that predate the pre-aggregation flag. */
      public FilterRequest(String field, String dataType, String label) {
         this(field, dataType, label, false);
      }
   }

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
         // Post-aggregation (default): bind to each chart's FINAL bound table -- the control
         // filters the already-aggregated display rows (see the class Javadoc). Pre-aggregation:
         // bind to the RAW source table(s) carrying the column (findColumnMatchingRootTables) so
         // the selection applies as a WHERE before any group-by, filtering the underlying rows and
         // letting the aggregate re-compute over the subset -- the only way to filter by an
         // orthogonal column that never survives to a chart's final table (e.g. order `state` on a
         // revenue-by-quarter chart). The caller only sets preAggregation for structurally-safe
         // charts (a simple per-group aggregate); a global-aggregate/normalization/window chart
         // would have its cross-row aggregate collapsed by a subset WHERE, which is exactly why the
         // default stays post-aggregation.
         List<String> tables = req.preAggregation()
            ? AddFilterService.findColumnMatchingRootTables(baseWorksheet, req.field())
            : AddFilterService.findColumnMatchingChartTables(baseWorksheet, chartTableNames, req.field());

         if(tables.isEmpty()) {
            skipped.add(req.field());
            continue;
         }

         ColumnRef colRef = AddFilterService.buildColumnRef(req.field(), req.dataType());
         AbstractSelectionVSAssembly control = createControlForType(vs, req.dataType(), colRef);

         if(req.label() != null && control instanceof TitledVSAssembly titled) {
            titled.setTitleValue(req.label());
         }

         // Only a control that draws NO title of its own gets a separate caption assembly (see
         // addCaption / FILTER_LABEL_HEIGHT): that is the TimeSlider, whose Angular component gates
         // its whole title header on isInSelectionContainer(). A SelectionList in DROPDOWN mode DOES
         // draw a title row, so a caption there printed the label TWICE -- once as the caption and
         // again as the dropdown's own title (observed live: "Res Partner Name" stacked over itself).
         //
         // EVERY control gets the SAME geometry regardless: the top FILTER_LABEL_HEIGHT of the band
         // is the caption strip, and the control itself is FILTER_CONTROL_ROW_HEIGHT below it. A
         // dropdown simply leaves its caption strip EMPTY (its label lives inside its own title row)
         // rather than growing to swallow it. An earlier version did the latter -- it had the
         // dropdown span the whole 60px band -- and the result was the live complaint this replaces:
         // a tall white box next to a visibly much shorter range slider. Reserving the strip for both
         // is what keeps the two control types' interactive rows on exactly the same baseline, which
         // is what "the bar stays even" means to someone looking at it.
         boolean rendersOwnTitle = control instanceof SelectionListVSAssembly;
         int controlHeight = FILTER_CONTROL_ROW_HEIGHT;

         // A shared-bar SelectionList must be a DROPDOWN, for the same reason the per-chart path
         // makes one (see addPerChartFilter): in list mode it draws a multi-row checkbox list, and
         // the bar reserves only FILTER_CONTROL_ROW_HEIGHT for it. A title row plus ~20px item
         // rows means one or two items are visible inside a scroll area -- useless for picking a
         // value out of, say, 141 customer names, which is exactly the case the FK-label filter
         // feature produces.
         //
         // Dropdown mode collapses it to a single title row that opens on click, so the full list is
         // reachable regardless of how little vertical space the bar can spare.
         // SelectionListVSAssemblyInfo#getSizeScale pins the Y scale to 1 in this mode, so it cannot
         // stretch back open. Title height is pinned to the SAME height the loop below reserves, so
         // drawn and reserved agree by construction rather than by two constants happening to match.
         // A TimeSliderVSAssembly (date/numeric) has no show type and is already single-row --
         // deliberately untouched, and it keeps the separate caption for the reason documented below.
         if(control instanceof SelectionListVSAssembly list) {
            list.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
            list.getSelectionListInfo().setTitleHeightValue(controlHeight);
            centerTitleVertically(list);
            applyDropdownPopupStyle(list);
         }

         // KNOWN UNFIXED: a shared-bar range slider renders with NO visible title -- a numeric slider
         // shows a bare "6..216" with nothing telling the user it filters partner_id. The label IS
         // applied above (setTitleValue). These explanations have each been RULED OUT by test, so do
         // NOT retry them:
         //   - the DESIGN title-visible value (getTitleVisibleValue) is already true by default;
         //   - the RUNTIME flag (isTitleVisible) is already true by default;
         //   - the title height is already AssetUtil.defh (20), not zero.
         // Explicit setTitleVisibleValue(true) and setTitleVisible(true) calls were both tried against
         // a live dashboard and changed nothing, and a test asserting either passes with AND without
         // them -- so any such "fix" here is vacuous. TitleInfo's no-arg constructor does leave
         // titleVisible as a valueless DynamicValue2 (vs TitleInfo(String) seeding "true"), which
         // looked like the cause but is not: both getters still report true.
         // Remaining hypothesis: the client-side TimeSlider component renders no title area at all,
         // which has to be investigated in the Angular viewer, not here. NOTE this is ALSO why the
         // caption cannot simply be deleted for every control: it is the slider's only label.

         // Caption above the control, so a user can tell what the control filters -- a bare "6..216"
         // range slider is otherwise unreadable. Its placement is returned alongside the control's:
         // an assembly with no per-tier layout entry is hidden when that tier is selected.
         if(!rendersOwnTitle && req.label() != null) {
            placements.add(addCaption(vs, "wizFilterLabel_" + control.getName(),
                                      x, y, FILTER_CONTROL_WIDTH, FILTER_LABEL_HEIGHT, req.label()));
         }

         // + FILTER_LABEL_HEIGHT unconditionally -- the caption strip is RESERVED for both control
         // types even though only the slider draws into it, so both controls' rows share a baseline.
         Point pos = new Point(x, y + FILTER_LABEL_HEIGHT);
         java.awt.Dimension size = new java.awt.Dimension(FILTER_CONTROL_WIDTH, controlHeight);
         control.setTableNames(tables);
         control.setPixelOffset(pos);
         // Explicit compact size: SelectionList/TimeSlider's own default pixel size is not
         // reliably short, and WizDashboardService reserves only FILTER_BAR_ROW_HEIGHT above the
         // charts -- an oversized control here would visually collide with the first chart row
         // instead of leaving the intended small gap.
         control.setPixelSize(size);
         vs.addAssembly(control);
         applied.add(req.field());
         placements.add(new FilterControlPlacement(control.getName(), pos, size));
         x += FILTER_CONTROL_WIDTH + FILTER_CONTROL_GAP;
      }

      return new FilterResult(applied, skipped, placements);
   }

   /**
    * Vertically centres a control's own title row.
    *
    * <p>Without this the shared-bar dropdown's title text rendered hard against the TOP of its
    * 60px row with a visibly empty band beneath it. Cause: nothing in this builder's path ever
    * calls {@code initDefaultFormat()} — {@code AddFilterService.createFilterAssembly} just
    * {@code new}s the assembly, and neither {@code SelectionListVSAssembly}'s constructor nor
    * {@code Viewsheet.addAssembly} calls it. That method is the ONLY thing that seeds a TITLEPATH
    * entry in the {@code FormatInfo} map (with {@code H_LEFT | V_CENTER}), so with no entry
    * {@code FormatInfo.getFormat(TITLEPATH, false)} synthesises one by copying the OBJECT format's
    * default layer, whose alignment is {@code VSFormat.ALIGN} = {@code H_LEFT | V_TOP}. That
    * reaches the client as {@code align-items: flex-start} on the title cell's flex container
    * (VSFormatModel → VSCSSUtil.getvAlign/getFlexAlignment → title-cell.component.html), i.e.
    * top-aligned. The per-chart path shows no visible symptom only because it reserves 28px, close
    * enough to the natural row height that there is little slack to misalign within.
    *
    * <p>This is approach (a) from the task brief — set V_CENTER on the TITLEPATH format — chosen
    * over "give the title its natural height and centre the control in the band" because that
    * would reintroduce the reserved-vs-drawn gap the title-height pinning exists to prevent (a
    * dropdown draws only its title row and ignores the rest of the assembly's pixel height).
    * H_LEFT is carried along deliberately: {@code fixAlignment} keeps the horizontal and vertical
    * bits independently, so writing only V_CENTER would zero the horizontal bit and centre the
    * caption text horizontally too.
    *
    * <p>The DESIGN ({@code ...Value}) setter is the load-bearing one — a composed dashboard is
    * saved and reopened — and the runtime setter is called alongside it, matching
    * {@link #applyCardFormat}'s pattern. Be aware the runtime call is belt-and-braces only and is
    * NOT observable: alignment is a {@code DynamicValue2}, whose {@code getRValue()} falls back to
    * the design value, so {@code getAlignment()} already reports V_CENTER from the {@code ...Value}
    * setter alone (mutation-checked — deleting {@code setAlignment} fails no test, deleting
    * {@code setAlignmentValue} fails
    * {@code sharedBarDropdownTitleIsVerticallyCentredNotPinnedToTheTopOfItsRow}). Written to the
    * USER-defined layer, not the default one, so it also outranks any CSS-layer alignment
    * ({@link VSCompositeFormat#getAlignment} consults the user layer first).
    */
   private static void centerTitleVertically(VSAssembly control) {
      FormatInfo fmtInfo = control.getVSAssemblyInfo().getFormatInfo();
      VSCompositeFormat titleFormat = fmtInfo.getFormat(VSAssemblyInfo.TITLEPATH);

      if(titleFormat == null) {
         titleFormat = new VSCompositeFormat();
         fmtInfo.setFormat(VSAssemblyInfo.TITLEPATH, titleFormat);
      }

      VSFormat fmt = titleFormat.getUserDefinedFormat();
      int align = StyleConstants.H_LEFT | StyleConstants.V_CENTER;
      fmt.setAlignmentValue(align);
      fmt.setAlignment(align);
   }

   /**
    * Gives a shared-bar dropdown an opaque background and an enclosing border, so its OPEN list
    * reads as a floating list instead of transparent text laid over the chart behind it.
    *
    * <p>The popup has no background of its own: {@code .selection-list-body} carries none in
    * vs-selection.component.scss, and the whole assembly (title row + popup) is painted by the one
    * {@code [style.background-color]="model.objectFormat.background"} binding on its
    * {@code .vs-object} wrapper. That background is unset here for the same reason the title was
    * top-aligned — nothing calls {@code initDefaultFormat()} — so the list rendered fully
    * transparent and its items overlapped the chart content underneath, unreadable.
    *
    * <p>Set on the OBJECT format (not TITLEPATH): the popup is part of the assembly, not the title
    * row. White rather than {@link #CARD_BACKGROUND} so the control reads as an input widget
    * against the tinted {@link #BAND_BACKGROUND} toolbar band and the open list reads as floating
    * ABOVE the chart rather than as another tinted panel of it. Goes through
    * {@link #applyCardFormat} for the persist-safe both-setters treatment — a background set only
    * via the plain setter looks right in a unit test and vanishes for the user on reload.
    */
   private static void applyDropdownPopupStyle(VSAssembly control) {
      applyCardFormat(control.getVSAssemblyInfo().getFormat().getUserDefinedFormat(),
                      DROPDOWN_BACKGROUND,
                      new BorderColors(CARD_BORDER_COLOR, CARD_BORDER_COLOR,
                                       CARD_BORDER_COLOR, CARD_BORDER_COLOR),
                      new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                                 StyleConstants.THIN_LINE, StyleConstants.THIN_LINE));
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
   /**
    * Adds a small static text caption at (x, y) reading {@code label} -- the stand-in for the title a
    * standalone range slider refuses to render (see FILTER_LABEL_HEIGHT). Deliberately a plain
    * TextVSAssembly rather than a styled one: it must read as a field caption, not compete with the
    * control below it.
    */
   private static FilterControlPlacement addCaption(
      Viewsheet vs, String name, int x, int y, int width, int height, String label)
   {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.initDefaultFormat();
      TextVSAssemblyInfo info = (TextVSAssemblyInfo) text.getVSAssemblyInfo();
      Point pos = new Point(x, y);
      Dimension size = new Dimension(width, height);
      info.setPixelOffset(pos);
      info.setPixelSize(size);
      info.setValue(label);
      // setValue alone is the RUNTIME value; the design value is what survives the composed
      // dashboard being saved and reopened (the same distinction that bit the title work).
      info.setTextValue(label);
      vs.addAssembly(text);
      return new FilterControlPlacement(text.getName(), pos, size);
   }

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
    * filters. Unlike {@link #build}, there is no board-wide candidate search — post-aggregation
    * (the default) the single {@code chartTableName} IS the candidate, and pre-aggregation the
    * candidates are only the root tables reachable from it, so either way the binding is scoped to
    * this one chart by construction (no residual name-collision risk across charts).
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
      // Post-aggregation (default): bind to this chart's FINAL bound table -- the control filters
      // the already-aggregated display rows (see the class Javadoc). Pre-aggregation: bind to the
      // raw source table(s) carrying the column that are REACHABLE FROM THIS CHART's own table
      // (findColumnMatchingRootTablesForChart) so the selection applies as a WHERE before any
      // group-by, filtering the underlying rows and letting the aggregate re-compute over the
      // subset -- the only way to filter by an orthogonal column that never survives to the
      // chart's final table (e.g. order `state` on a revenue-by-quarter chart).
      //
      // Key distinction from the shared-bar path in build(): that one resolves via
      // findColumnMatchingRootTables, which scans EVERY visible root table in the merged dashboard
      // worksheet, so one WHERE can also reach a chart whose cross-row math (a window function, a
      // global-aggregate ratio) a subset WHERE would collapse -- forcing its caller to veto the
      // filter unless every chart sharing the column name is structurally safe. Here the control
      // belongs to exactly ONE chart's tile and binds only to THAT chart's own raw source, so this
      // chart's safety is decided independently of the rest of the board and no such veto is
      // needed. Default (false) stays post-aggregation, unchanged.
      List<String> tables = request.preAggregation()
         ? AddFilterService.findColumnMatchingRootTablesForChart(
              baseWorksheet, chartTableName, request.field())
         : AddFilterService.findColumnMatchingChartTables(
              baseWorksheet, List.of(chartTableName), request.field());

      if(tables.isEmpty()) {
         return null;
      }

      ColumnRef colRef = AddFilterService.buildColumnRef(request.field(), request.dataType());
      AbstractSelectionVSAssembly control = createControlForType(vs, request.dataType(), colRef);

      // A per-chart control lives INSIDE its chart's tile, so a multi-row checkbox list steals
      // that height from the chart itself (four such filters cost ~480px on a five-chart board).
      // Dropdown mode collapses it to a single title row -- SelectionListVSAssemblyInfo#getSizeScale
      // pins the Y scale to 1 in that mode, so it cannot stretch back open. Applied HERE rather than
      // in createControlForType because that factory delegates to AddFilterService, whose own
      // interactive add-filter flow must keep StyleBI's default list rendering. A TimeSliderVSAssembly
      // (date/numeric) has no show type and is already single-row, so it is deliberately untouched.
      if(control instanceof SelectionListVSAssembly list) {
         list.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
         // A dropdown draws ONLY its title row and ignores the rest of the assembly's pixel height
         // (default AssetUtil.defh = 20). Anything reserved beyond what the row draws shows up as a
         // GAP between the filter and its chart, breaking the single-enclosing-card look
         // applyGroupedCardStyle builds. Pin the row to the caller's reserved height so the two agree
         // by construction instead of relying on two constants happening to match. The DESIGN value
         // (not the runtime one) is set because a composed dashboard is saved and reopened.
         list.getSelectionListInfo().setTitleHeightValue(height);
      }

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
      BorderColors borderColors = new BorderColors(CARD_BORDER_COLOR, CARD_BORDER_COLOR,
         CARD_BORDER_COLOR, CARD_BORDER_COLOR);

      VSFormat filterFormat = filterControl.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      applyCardFormat(filterFormat, CARD_BACKGROUND, borderColors, new Insets(
         // Insets(top, left, bottom, right) -- no bottom border: the chart's own top edge (also
         // borderless, below) abuts it directly.
         StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
         StyleConstants.NO_BORDER, StyleConstants.THIN_LINE));

      VSFormat chartFormat = chartAssembly.getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      applyCardFormat(chartFormat, CARD_BACKGROUND, borderColors, new Insets(
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
    *
    * <p>Takes the {@link Color} and derives the hex itself, rather than taking both: an earlier
    * signature took a {@code backgroundHex} String but passed a hard-coded {@link #CARD_BACKGROUND}
    * to the plain setter, so a second caller with a different color would have silently written two
    * DIFFERENT backgrounds into the two layers — which layer you read would decide which color you
    * got.
    */
   private static void applyCardFormat(
      VSFormat format, Color background, BorderColors borderColors, Insets borders)
   {
      format.setBackground(background);
      format.setBackgroundValue(toHex(background));
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

   /** Shared-bar dropdown fill -- opaque WHITE, deliberately not {@link #CARD_BACKGROUND}: this
    *  background paints the control's OPEN popup list, which floats over a chart, so it has to read
    *  as a floating list rather than as another tinted panel belonging to the chart. White also
    *  makes the collapsed control read as an input widget against the tinted
    *  {@link #BAND_BACKGROUND} toolbar band. */
   private static final Color DROPDOWN_BACKGROUND = Color.WHITE;

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
   /**
    * Horizontal gap between adjacent shared-bar controls, in pixels. Without it the stride equalled
    * the control width exactly, so controls butted edge-to-edge and read as one continuous widget --
    * two range sliders side by side looked like a single double-ended slider, and it was not obvious
    * where one filter ended and the next began.
    */
   private static final int FILTER_CONTROL_GAP = 16;
   /**
    * Height of the caption strip above every shared-bar control, in pixels. Only the range slider
    * DRAWS into it (a dropdown's caption there printed the label twice, since it renders its own
    * title row) but both control types RESERVE it, so their interactive rows sit on a common
    * baseline and the bar reads as one row of controls rather than a ragged step.
    *
    * A range slider will NOT draw its own title: vs-range-slider.component.html gates the whole
    * title header on {@code @if (isInSelectionContainer())}, which is true only inside a
    * VSSelectionContainer or for an adhoc filter -- so a standalone bar control renders no title
    * area no matter what the server sets (design AND runtime titleVisible are already true and the
    * title height is already AssetUtil.defh; all three were ruled out by test). Rather than widen
    * that shared component's behaviour for every standalone slider in the product, draw our own
    * caption beside the control.
    */
   /* Package-visible so the geometry tests can assert against the constant rather than a literal
    * that silently rots when this is tuned -- matching FILTER_CONTROL_HEIGHT and
    * WizDashboardService's own PER_CHART_FILTER_ROW_HEIGHT. */
   static final int FILTER_LABEL_HEIGHT = 16;

   /**
    * The DRAWN height of a shared-bar control itself (excluding the caption strip above it), in
    * pixels — deliberately the SAME constant the per-chart path sizes its control to, which is the
    * established natural height for both of these widgets.
    *
    * <p>It used to be {@code FILTER_CONTROL_HEIGHT - FILTER_LABEL_HEIGHT} for a slider and the full
    * {@code FILTER_CONTROL_HEIGHT} (60px) for a dropdown, on the theory that a dropdown should grow
    * to swallow the caption height it does not need. Live, that made the dropdown render as a tall
    * white box beside a much shorter range slider — the opposite of even. Both control types now
    * draw at this one height and differ only in whether the strip above them carries a caption.
    */
   private static final int FILTER_CONTROL_ROW_HEIGHT = WizDashboardService.PER_CHART_FILTER_ROW_HEIGHT;

   /** Total band height one shared-bar control occupies, in pixels: its caption strip plus its own
    *  row. Identical for both control types, which is what keeps the bar even; only whether the
    *  strip is drawn into differs. Derived rather than a literal so it cannot drift out of step
    *  with the two heights it is the sum of.
    *
    *  <p>Package-visible so {@link WizDashboardService#FILTER_BAND_HEIGHT} can size the toolbar
    *  band from it instead of carrying an independent literal that has to be remembered and
    *  re-tuned whenever this changes — exactly what left a visibly empty strip under the controls
    *  the first time this shrank. */
   static final int FILTER_CONTROL_HEIGHT = FILTER_LABEL_HEIGHT + FILTER_CONTROL_ROW_HEIGHT;
}
