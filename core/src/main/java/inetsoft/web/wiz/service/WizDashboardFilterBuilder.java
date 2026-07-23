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

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds one selection control per {@link FilterRequest}, binds it to every merged root
 * worksheet table exposing the requested column, and positions the controls as a top
 * filter bar on an already-composed dashboard {@link Viewsheet}.
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
 *       (a)). {@code AddFilterService.findTablesWithColumn} still does its own
 *       {@code AssetRepository}/{@code Principal} reload first (needed there because a
 *       *runtime* viewsheet's base worksheet can go stale after an async merge+save), then
 *       delegates to this shared loop. This builder instead calls the shared loop directly
 *       against {@code vs.getBaseWorksheet()} — no reload is needed here because this
 *       builder operates on an already-composed, in-memory dashboard {@code Viewsheet} that
 *       Task 3 built in one shot (not a runtime object edited incrementally across separate
 *       save/reload round-trips).</li>
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
 * fabricated here): column-matching across merged root tables
 * ({@link #findMergedTablesWithColumn}) and actual runtime filtering of charts both require
 * a live composed dashboard with a real worksheet. The automated coverage in
 * {@code WizDashboardFilterBuilderTest} is limited to the pure data-type → control-type
 * branch via the package-visible {@link #createControlForType}.</p>
 */
public class WizDashboardFilterBuilder {
   public record FilterRequest(String field, String dataType, String label) {}
   public record FilterResult(List<String> applied, List<String> skipped) {}

   /**
    * Builds one selection control per request, binds it to every merged root table exposing
    * the column, positions the controls as a top filter bar, and adds them to {@code vs}.
    *
    * @return which fields bound to &gt;=1 table (applied) vs none (skipped).
    */
   public FilterResult build(Viewsheet vs, List<FilterRequest> requests) {
      List<String> applied = new ArrayList<>();
      List<String> skipped = new ArrayList<>();
      int x = FILTER_BAR_X;
      int y = FILTER_BAR_Y;

      for(FilterRequest req : requests) {
         List<String> tables = findMergedTablesWithColumn(vs, req.field());

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
         vs.addAssembly(control);
         applied.add(req.field());
         x += FILTER_CONTROL_WIDTH;
      }

      return new FilterResult(applied, skipped);
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

   /**
    * Thin wrapper around the shared {@code AddFilterService.findColumnMatchingRootTables}
    * loop, applied directly to this already-composed dashboard's base worksheet. See the
    * class Javadoc reuse-seam note for why no {@code AssetRepository}/{@code Principal}
    * reload is needed here (unlike {@code AddFilterService.findTablesWithColumn}).
    */
   private List<String> findMergedTablesWithColumn(Viewsheet vs, String attribute) {
      Worksheet ws = vs.getBaseWorksheet();
      return AddFilterService.findColumnMatchingRootTables(ws, attribute);
   }

   private static final int FILTER_BAR_X = 0;
   private static final int FILTER_BAR_Y = 0;
   private static final int FILTER_CONTROL_WIDTH = 200;
}
