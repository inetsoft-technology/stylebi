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

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import org.springframework.stereotype.Component;

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
   public record FilterResult(List<String> applied, List<String> skipped) {}

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
    * @return which fields bound to &gt;=1 table (applied) vs none (skipped).
    */
   public FilterResult build(Viewsheet vs, Worksheet baseWorksheet, List<FilterRequest> requests) {
      List<String> applied = new ArrayList<>();
      List<String> skipped = new ArrayList<>();
      int x = FILTER_BAR_X;
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

         control.setTableNames(tables);
         control.setPixelOffset(new Point(x, y));
         // Explicit compact size: SelectionList/TimeSlider's own default pixel size is not
         // reliably short, and WizDashboardService reserves only FILTER_BAR_ROW_HEIGHT (120px)
         // above the charts -- an oversized control here would visually collide with the first
         // chart row instead of leaving the intended small gap.
         control.setPixelSize(new java.awt.Dimension(FILTER_CONTROL_WIDTH, FILTER_CONTROL_HEIGHT));
         vs.addAssembly(control);
         applied.add(req.field());
         x += FILTER_CONTROL_WIDTH;
      }

      return new FilterResult(applied, skipped);
   }

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

   private static final int FILTER_BAR_X = 0;
   private static final int FILTER_BAR_Y = 0;
   private static final int FILTER_CONTROL_WIDTH = 200;

   /** Control height, in pixels — leaves a small margin under
    *  {@link WizDashboardService#FILTER_BAR_ROW_HEIGHT} (120px), the reserved row above charts. */
   private static final int FILTER_CONTROL_HEIGHT = 100;
}
