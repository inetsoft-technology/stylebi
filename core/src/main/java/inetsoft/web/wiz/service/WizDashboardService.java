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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.WizUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.wiz.model.WizDashboardEvent;
import inetsoft.web.wiz.model.WizDashboardResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Composes a wiz dashboard viewsheet from a list of previously-saved visualization
 * viewsheets (POST /api/wiz/visualization/dashboard).
 *
 * <p>Steps:
 * <ol>
 *   <li>Validate {@code name} and a non-empty {@code identifiers} list.</li>
 *   <li>Parse every identifier and guard that its path is under
 *       {@link WizVisualizationService#VISUALIZATION_COMPONENTS_FOLDER_PATH} (fail loud
 *       otherwise).</li>
 *   <li>Action-level permission check (mirrors {@link WizVisualizationService#renderVisualization}),
 *       performed <b>before</b> any runtime viewsheet is opened.</li>
 *   <li>Mint a fresh, empty wiz-dashboard runtime viewsheet via
 *       {@link ViewsheetService#openTemporaryViewsheet(String, AssetEntry, Principal, Viewsheet.WizInfo)}
 *       with a 1-arg {@link Viewsheet.WizInfo#WizInfo(boolean)} (the 3-arg constructor leaves
 *       {@code isWizSheet()} false, which breaks {@link AddVisualizationService#addVisualization}).</li>
 *   <li>Merge each visualization in turn via {@link AddVisualizationServiceProxy#addVisualization}
 *       (not {@code addVisualizationsByIds}, which forces a measured 3-column grid) using a
 *       deterministic single-column vertical stack (x=0, y=running total of
 *       {@link #DASHBOARD_ROW_HEIGHT}). A per-visualization failure is logged and the identifier
 *       recorded in {@link WizDashboardResult#getSkipped()} rather than failing the whole
 *       compose; if every visualization is skipped, the compose fails loud
 *       ({@link IllegalArgumentException}, mapped by the controller to 400).</li>
 *   <li>When {@code tiles} are supplied, fail loud ({@link IllegalArgumentException}) if a
 *       tile's {@code identifier} doesn't match the {@code identifiers} entry at the same index
 *       — tiles and identifiers are consumed purely positionally (by index) for span/layout
 *       purposes, so a caller sending them out of order would otherwise silently assign the
 *       wrong span to the wrong visualization.</li>
 *   <li>When {@code event.getFilters()} is non-empty, reserve a top row (offset every merged
 *       chart's y by {@link #DASHBOARD_ROW_HEIGHT}), load the dashboard's merged base worksheet
 *       directly from the repository with {@code permission=false} (see the note on
 *       {@link #applyFilters} below — <b>not</b> {@code vs.getBaseWorksheet()}, which is stale,
 *       and <b>not</b> {@code permission=true}, which can fail the ACL check on this
 *       system-generated ephemeral entry), and build a filter bar via
 *       {@link #applyFilters}/{@link WizDashboardFilterBuilder#build}, recording
 *       {@link WizDashboardResult#getFiltersApplied()}/{@link WizDashboardResult#getFiltersSkipped()}.
 *       Absent/empty {@code filters} leaves the grid and result identical to the no-filter-bar
 *       case.</li>
 *   <li>Persist via {@link WizUtil#saveWizSheet}, which finalizes the temporary dashboard
 *       worksheet and then runs the save callback, in the same order the Composer uses (a bare
 *       {@code viewsheetService.setViewsheet} call would skip the finalize step and leave the
 *       dashboard pointing at a temp worksheet entry). The filter bar is built <b>before</b> this
 *       call so its controls — added to the in-memory {@code Viewsheet} — are persisted
 *       automatically.</li>
 *   <li>Always close the runtime in a {@code finally} block.</li>
 * </ol>
 *
 * <p><b>Test coverage note:</b> only the pre-open validation/guard/permission branches above are
 * unit-tested here ({@link WizDashboardServiceTest}). The happy-path compose+save and the
 * all-visualizations-skipped-&gt;400 path both require a live {@link ViewsheetService}/asset
 * engine to open a real runtime viewsheet and merge worksheets, and are verified later in the A4
 * integration test.
 */
@Service
public class WizDashboardService {
   public WizDashboardService(ViewsheetService viewsheetService,
                               AddVisualizationServiceProxy addVisualizationService,
                               SecurityEngine securityEngine,
                               WizDashboardFilterBuilder filterBuilder,
                               AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.addVisualizationService = addVisualizationService;
      this.securityEngine = securityEngine;
      this.filterBuilder = filterBuilder;
      this.assetRepository = assetRepository;
   }

   public WizDashboardResult composeDashboard(WizDashboardEvent event, Principal principal)
      throws Exception
   {
      if(event == null || Tool.isEmptyString(event.getName())) {
         throw new IllegalArgumentException("name is required");
      }

      List<String> identifiers = event.getIdentifiers();

      if(identifiers == null || identifiers.isEmpty()) {
         throw new IllegalArgumentException("identifiers are required");
      }

      // Parse + managed-folder guard for every input identifier (fail loud on out-of-folder).
      List<AssetEntry> entries = new ArrayList<>();

      for(String id : identifiers) {
         AssetEntry entry = AssetEntry.createAssetEntry(id);

         if(entry == null || entry.getPath() == null ||
            !entry.getPath().startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/"))
         {
            throw new IllegalArgumentException(
               "Identifier is not in the managed visualizations folder: " + id);
         }

         entries.add(entry);
      }

      // Action-level gate, mirroring WizVisualizationService#renderVisualization — performed
      // before any runtime viewsheet is opened.
      if(!securityEngine.checkPermission(principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS)) {
         throw new SecurityException(
            Catalog.getCatalog().getString("composer.authorization.permissionDenied"));
      }

      // Mint a fresh, empty wiz-dashboard runtime. The 1-arg WizInfo(true) sets isWizSheet()=true;
      // the 3-arg WizInfo(true, null, null) sets isWizVisualization() instead and leaves
      // isWizSheet()=false, which AddVisualizationService#addVisualization requires.
      String runtimeId = viewsheetService.openTemporaryViewsheet(
         null, null, principal, new Viewsheet.WizInfo(true));

      try {
         List<String> skipped = new ArrayList<>();
         int mergedCount = 0;

         boolean grid = event.getTiles() != null && !event.getTiles().isEmpty();
         int layoutColumns = event.getLayoutColumns() != null ?
            Math.max(1, event.getLayoutColumns()) : 2;
         int[] spans = grid ?
            event.getTiles().stream().mapToInt(t -> Math.max(1, t.getSpanCols())).toArray() : null;
         int[] rowSpans = grid ?
            event.getTiles().stream().mapToInt(t -> Math.max(1, t.getSpanRows())).toArray() : null;

         if(grid && spans.length != entries.size()) {
            throw new IllegalArgumentException(
               "tiles count (" + spans.length + ") does not match resolved visualization count (" +
               entries.size() + ")");
         }

         // When a filter bar will be built (Task 3, below), reserve its own top row so the
         // merged charts don't render underneath it. Its controls are much shorter than a full
         // chart tile, so this uses its own (smaller) row height rather than DASHBOARD_ROW_HEIGHT
         // -- reserving a full chart-height row left a large empty gap between the filter bar and
         // the charts below it.
         boolean hasFilters = event.getFilters() != null && !event.getFilters().isEmpty();
         int topOffset = hasFilters ? FILTER_BAR_ROW_HEIGHT : 0;

         int cumulativeY = topOffset;   // stack path only

         for(int i = 0; i < entries.size(); i++) {
            int x, y;

            if(grid) {
               // tiles[] and identifiers[] are consumed purely positionally below (spans[i]
               // paired with entries.get(i)/identifiers.get(i)) — guard that the caller actually
               // sent them in the same order, rather than silently mis-assigning spans.
               String tileIdentifier = event.getTiles().get(i).getIdentifier();

               if(!Objects.equals(tileIdentifier, identifiers.get(i))) {
                  throw new IllegalArgumentException(
                     "tiles[" + i + "].identifier (" + tileIdentifier + ") does not match " +
                     "identifiers[" + i + "] (" + identifiers.get(i) + ") — tiles must be listed " +
                     "in the same order as identifiers");
               }

               java.awt.Point origin = gridOrigin(spans, rowSpans, layoutColumns, i);
               x = origin.x;
               y = origin.y + topOffset;
            }
            else {
               x = 0;
               y = cumulativeY;
            }

            try {
               addVisualizationService.addVisualization(
                  runtimeId, entries.get(i), x, y, 1.0f, principal);

               if(!grid) {
                  cumulativeY += DASHBOARD_ROW_HEIGHT;
               }

               mergedCount++;
            }
            catch(Exception ex) {
               LOG.warn("Skipping unmergeable visualization [{}]: {}", identifiers.get(i), ex.getMessage());
               skipped.add(identifiers.get(i));
            }
         }

         if(mergedCount == 0) {
            throw new IllegalArgumentException("No renderable visualizations to compose (all skipped)");
         }

         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
         AssetEntry savedVsEntry = resolveTargetEntry(event.getExistingIdentifier(), event.getName(), principal);

         // Build the top filter bar (Task 3) before save, so the controls it adds to the
         // in-memory Viewsheet are persisted by the same WizUtil.saveWizSheet call below.
         //
         // The merge loop above (AddVisualizationService#addVisualization) saves the merged
         // worksheet to the repository via assetRepository.setSheet and repoints the runtime
         // Viewsheet's base entry via setBaseEntry(...), but never refreshes the runtime
         // Viewsheet's own transient `ws` cache — so vs.getBaseWorksheet() would still return
         // the original empty temp worksheet. Load the merged worksheet directly from the
         // repository instead — with permission=false, exactly like
         // AddFilterService#findTablesWithColumn (NOT Viewsheet#reloadBaseWorksheet, which uses
         // permission=true and can fail the ACL check, or return a stripped sheet, for a
         // principal with no explicit grant on this system-generated ephemeral entry — that
         // would reproduce the same every-filter-skipped symptom this fix targets) — so the
         // filter builder sees the actual merged root tables.
         WizDashboardFilterBuilder.FilterResult filterResult = null;

         if(hasFilters) {
            Viewsheet vs = rvs.getViewsheet();
            Worksheet baseWs = (Worksheet) assetRepository.getSheet(
               vs.getBaseEntry(), principal, false, AssetContent.ALL);
            filterResult = applyFilters(vs, baseWs, event.getFilters());
         }

         WizUtil.saveWizSheet(rvs, principal, savedVsEntry,
            () -> viewsheetService.setViewsheet(rvs.getViewsheet(), savedVsEntry, principal, true, true));

         WizDashboardResult result = new WizDashboardResult();
         result.setSavedViewsheetIdentifier(savedVsEntry.toIdentifier());
         result.setSkipped(skipped);

         if(filterResult != null) {
            result.setFiltersApplied(filterResult.applied());
            result.setFiltersSkipped(filterResult.skipped());
         }

         return result;
      }
      finally {
         try {
            viewsheetService.closeViewsheet(runtimeId, principal);
         }
         catch(Exception ignore) {
            LOG.warn("Failed to close runtime [{}] after dashboard compose", runtimeId);
         }
      }
   }

   /** Build the components-folder target entry; overwrite the existing one when provided. */
   private AssetEntry resolveTargetEntry(String existingIdentifier, String name, Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(!Tool.isEmptyString(existingIdentifier)) {
         AssetEntry existing = AssetEntry.createAssetEntry(existingIdentifier);

         if(existing == null || existing.getPath() == null ||
            !existing.getPath().startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/"))
         {
            throw new IllegalArgumentException("existingIdentifier is not in the managed visualizations folder");
         }

         existing.setAlias(name);
         return existing;
      }

      String newPath = WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/" + UUID.randomUUID();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, newPath, pId);
      entry.setAlias(name);
      return entry;
   }

   /**
    * Maps {@link WizDashboardEvent.FilterSpec}s to {@link WizDashboardFilterBuilder.FilterRequest}s
    * and builds the top filter bar against the already-merged dashboard {@code Viewsheet}.
    *
    * <p><b>Caller contract:</b> {@code baseWs} must be the dashboard's merged base worksheet,
    * loaded directly from the repository with {@code permission=false} — i.e.
    * {@code assetRepository.getSheet(vs.getBaseEntry(), principal, false, AssetContent.ALL)} —
    * the exact mirror of {@code AddFilterService.findTablesWithColumn}. Do <b>not</b> pass
    * {@code vs.getBaseWorksheet()} (stale — the merge never refreshes it) or a worksheet loaded
    * with {@code permission=true} (can fail the ACL check on this system-generated ephemeral
    * entry — see {@link WizDashboardFilterBuilder}'s class Javadoc). This method does not load
    * or reload the worksheet itself.</p>
    *
    * <p>Package-private seam for unit testing (mirrors {@link #gridOrigin}) — lets
    * {@link WizDashboardServiceGridTest} exercise the mapping/delegation with a mocked
    * {@link WizDashboardFilterBuilder}, independent of the live-engine-only compose+save path.
    */
   WizDashboardFilterBuilder.FilterResult applyFilters(Viewsheet vs, Worksheet baseWs,
                                                        List<WizDashboardEvent.FilterSpec> specs)
   {
      List<WizDashboardFilterBuilder.FilterRequest> reqs = specs.stream()
         .map(f -> new WizDashboardFilterBuilder.FilterRequest(f.getField(), f.getDataType(), f.getLabel()))
         .collect(java.util.stream.Collectors.toList());
      return filterBuilder.build(vs, baseWs, reqs);
   }

   /** Vertical row stride between successive merged visualizations, in pixels — used by both
    *  the single-column stack path and the grid path's row advance. */
   private static final int DASHBOARD_ROW_HEIGHT = 420;

   /** Reserved row height for the top filter bar, in pixels — matches the compact pixel size
    *  {@link WizDashboardFilterBuilder} gives its selection/range controls, not a full chart
    *  tile's height. */
   static final int FILTER_BAR_ROW_HEIGHT = 120;

   /** Horizontal stride between grid columns, in pixels (paired with DASHBOARD_ROW_HEIGHT). */
   private static final int DASHBOARD_COL_WIDTH = 640;   // confirm vs composer default viz width

   /**
    * Row-major grid origin for the tile at flat index {@code i}, given per-tile column AND row
    * spans. Packing is still row-major/left-to-right/wrap-at-{@code layoutColumns} (identical
    * grouping to the column-only overload below) — the only change is that each row's HEIGHT is
    * now {@code max(spanRows)} among the tiles placed in it, instead of always
    * {@code DASHBOARD_ROW_HEIGHT}. A tile's own Y depends only on the finalized height of every
    * row strictly before it, and every such row is fully scanned (all its tiles' spanRows folded
    * into that row's height, then closed out) before the loop reaches index {@code i} — so no
    * 2D occupancy grid is needed; a tile with spanRows > 1 does not "block" cells in the row
    * below for placement purposes (that would be true masonry/skyline packing, deliberately not
    * implemented — see the Phase 4 design spec). Returns the (x,y) drop origin in pixels.
    * Package-private for unit testing.
    */
   static java.awt.Point gridOrigin(int[] spanCols, int[] spanRows, int layoutColumns, int i) {
      int col = 0;
      int cumulativeY = 0;
      int rowHeight = 1;   // tallest spanRows seen so far in the CURRENT (still-open) row

      for(int k = 0; k <= i; k++) {
         int span = Math.max(1, Math.min(spanCols[k], layoutColumns));
         int rspan = Math.max(1, spanRows[k]);

         if(col + span > layoutColumns) {   // doesn't fit in the current row → close it out
            cumulativeY += rowHeight * DASHBOARD_ROW_HEIGHT;
            col = 0;
            rowHeight = 1;
         }

         if(k == i) {
            return new java.awt.Point(col * DASHBOARD_COL_WIDTH, cumulativeY);
         }

         rowHeight = Math.max(rowHeight, rspan);
         col += span;

         if(col >= layoutColumns) {   // row exactly full → close it out now
            cumulativeY += rowHeight * DASHBOARD_ROW_HEIGHT;
            col = 0;
            rowHeight = 1;
         }
      }

      return new java.awt.Point(0, 0);   // unreachable (i is always in range)
   }

   /**
    * Back-compat overload for callers with only column spans (implicit {@code spanRows[k] == 1}
    * for every tile — i.e. every row is exactly {@code DASHBOARD_ROW_HEIGHT}, matching Phase 2's
    * original behavior exactly).
    */
   static java.awt.Point gridOrigin(int[] spanCols, int layoutColumns, int i) {
      int[] unitRowSpans = new int[spanCols.length];
      java.util.Arrays.fill(unitRowSpans, 1);
      return gridOrigin(spanCols, unitRowSpans, layoutColumns, i);
   }

   private final ViewsheetService viewsheetService;
   private final AddVisualizationServiceProxy addVisualizationService;
   private final SecurityEngine securityEngine;
   private final WizDashboardFilterBuilder filterBuilder;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(WizDashboardService.class);
}
