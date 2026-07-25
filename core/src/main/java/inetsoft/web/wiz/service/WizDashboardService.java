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

         // Which tiles have a per-chart filter targeting them, keyed by identifier -- computed
         // once, up front, so gridOrigin can factor extra row height into EVERY tile's
         // reservation (not just the ones with a filter), and so the merge loop below can look
         // up "does THIS tile need extra height" by flat index without re-deriving it each time.
         boolean[] hasPerChartFilter = new boolean[entries.size()];

         if(grid && event.getPerChartFilters() != null) {
            java.util.Set<String> targeted = event.getPerChartFilters().stream()
               .map(WizDashboardEvent.PerChartFilterSpec::getIdentifier)
               .collect(java.util.stream.Collectors.toSet());

            for(int i = 0; i < identifiers.size(); i++) {
               hasPerChartFilter[i] = targeted.contains(identifiers.get(i));
            }
         }

         // Records each grid tile's own (x, topY) origin and pixel size -- topY is the very top
         // of the tile's reserved space (where a per-chart filter sits, if it has one), NOT the
         // chart's own (possibly filter-shifted) y. Also records each merged chart's own table
         // name. Both are looked up after this loop when placing per-chart filters.
         java.util.Map<String, java.awt.Rectangle> tileBounds = new java.util.HashMap<>();
         java.util.Map<String, String> identifierToTableName = new java.util.HashMap<>();
         java.util.Map<String, String> identifierToAssemblyName = new java.util.HashMap<>();

         int cumulativeY = topOffset;   // stack path only

         for(int i = 0; i < entries.size(); i++) {
            int x, y, tileTopY;

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

               java.awt.Point origin = gridOrigin(spans, rowSpans, hasPerChartFilter, layoutColumns, i);
               x = origin.x + CANVAS_MARGIN;
               tileTopY = origin.y + topOffset + CANVAS_MARGIN;
               // The chart itself starts BELOW the reserved filter strip, if this tile has one --
               // the tile's own reserved footprint (computed via gridOrigin above) already
               // accounts for that extra height, so this only affects where the CHART renders
               // within it, not how much space the tile as a whole takes.
               y = tileTopY + (hasPerChartFilter[i] ? PER_CHART_FILTER_ROW_HEIGHT : 0);
            }
            else {
               x = CANVAS_MARGIN;
               tileTopY = cumulativeY + CANVAS_MARGIN;
               y = tileTopY;
            }

            // Resize the merged chart to its allocated tile footprint (grid path only) --
            // otherwise a tile's computed (spanCols, spanRows) only ever reserved grid drop-
            // position spacing and never resized the chart itself. The stack path has no
            // per-visualization span data, so it passes null and preserves the chart's saved size.
            java.awt.Dimension pixelSize = grid ? tilePixelSize(spans[i], rowSpans[i]) : null;

            try {
               AddVisualizationService.MergedVisualizationInfo mergedInfo = addVisualizationService.addVisualization(
                  runtimeId, entries.get(i), x, y, 1.0f, pixelSize, principal);

               if(grid) {
                  int tileHeight = pixelSize.height + (hasPerChartFilter[i] ? PER_CHART_FILTER_ROW_HEIGHT : 0);
                  tileBounds.put(identifiers.get(i),
                     new java.awt.Rectangle(x, tileTopY, pixelSize.width, tileHeight));

                  if(mergedInfo.tableName() != null) {
                     identifierToTableName.put(identifiers.get(i), mergedInfo.tableName());
                  }

                  if(mergedInfo.assemblyName() != null) {
                     identifierToAssemblyName.put(identifiers.get(i), mergedInfo.assemblyName());
                  }
               }

               if(!grid) {
                  cumulativeY += DASHBOARD_ROW_HEIGHT + TILE_GUTTER;
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
         List<String> perChartFiltersApplied = new ArrayList<>();
         List<String> perChartFiltersSkipped = new ArrayList<>();
         boolean hasPerChartFilters = grid && event.getPerChartFilters() != null &&
            !event.getPerChartFilters().isEmpty();

         if(hasFilters || hasPerChartFilters) {
            Viewsheet vs = rvs.getViewsheet();
            Worksheet baseWs = (Worksheet) assetRepository.getSheet(
               vs.getBaseEntry(), principal, false, AssetContent.ALL);

            if(hasFilters) {
               filterResult = applyFilters(vs, baseWs, event.getFilters());
            }

            if(hasPerChartFilters) {
               for(WizDashboardEvent.PerChartFilterSpec spec : event.getPerChartFilters()) {
                  java.awt.Rectangle bounds = tileBounds.get(spec.getIdentifier());
                  String tableName = identifierToTableName.get(spec.getIdentifier());

                  if(bounds == null || tableName == null) {
                     perChartFiltersSkipped.add(spec.getField());
                     continue;
                  }

                  WizDashboardFilterBuilder.FilterControlPlacement placement =
                     applyPerChartFilter(vs, baseWs, spec, bounds.x, bounds.y, tableName);

                  if(placement != null) {
                     perChartFiltersApplied.add(spec.getField());
                  }
                  else {
                     perChartFiltersSkipped.add(spec.getField());
                  }
               }
            }
         }
         if(!hasPerChartFilters && event.getPerChartFilters() != null) {
            // Requested but not applicable (non-grid path, or an empty list) -- report every one
            // as skipped rather than silently dropping them. Deliberately NOT an `else if` on the
            // block above: that would only run when `hasFilters` is ALSO false, so a caller
            // requesting the shared bar (hasFilters=true) on the non-grid path together with
            // per-chart filters would take the `if` branch via hasFilters alone and this handling
            // would never run at all -- silently losing the per-chart filters instead of skipping
            // them. Checking `!hasPerChartFilters` directly (independent of `hasFilters`) covers
            // every case: grid=false, or grid=true with an empty/absent list.
            for(WizDashboardEvent.PerChartFilterSpec spec : event.getPerChartFilters()) {
               perChartFiltersSkipped.add(spec.getField());
            }
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

         result.setPerChartFiltersApplied(perChartFiltersApplied);
         result.setPerChartFiltersSkipped(perChartFiltersSkipped);

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

   /**
    * Maps a single {@link WizDashboardEvent.PerChartFilterSpec} to a
    * {@link WizDashboardFilterBuilder.FilterRequest} and delegates to
    * {@link WizDashboardFilterBuilder#buildPerChart}. Package-visible seam for unit testing
    * (mirrors {@link #applyFilters}), independent of the live-engine-only compose+save path.
    */
   WizDashboardFilterBuilder.FilterControlPlacement applyPerChartFilter(
      Viewsheet vs, Worksheet baseWs, WizDashboardEvent.PerChartFilterSpec spec,
      int x, int y, String chartTableName)
   {
      WizDashboardFilterBuilder.FilterRequest req =
         new WizDashboardFilterBuilder.FilterRequest(spec.getField(), spec.getDataType(), spec.getLabel());
      return filterBuilder.buildPerChart(vs, baseWs, x, y, req, chartTableName);
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

   /** Spacing added between adjacent tiles, in pixels -- both horizontally (between columns)
    *  and vertically (between rows), so tiles don't render flush against each other. Matches
    *  {@link #CANVAS_MARGIN} for visual consistency. Applied unconditionally, independent of
    *  {@code layoutColumns}. */
   private static final int TILE_GUTTER = 24;

   /** Left/top margin from the canvas edge, in pixels, applied uniformly to the filter bar and
    *  every merged chart tile -- unmargined content rendered flush against the viewsheet edge.
    *  Package-visible so {@link WizDashboardFilterBuilder} can align its own controls to it. */
   static final int CANVAS_MARGIN = 24;

   /** Ceiling on a merged chart's rendered width/height, in pixels, regardless of its tile's
    *  column/row span -- without this, a full-width/full-height tile (e.g. 2 cols x 2 rows)
    *  stretches to fill its entire reserved grid cell (1280x840), rendering far larger than a
    *  normal single chart. The tile still RESERVES its full span for grid positioning (see
    *  {@link #gridOrigin}); only the rendered chart size is capped, leaving a margin of unused
    *  space inside an oversized cell rather than a stretched chart. */
   private static final int MAX_TILE_WIDTH = 900;
   private static final int MAX_TILE_HEIGHT = 600;

   /** Extra row height reserved, in pixels, for a tile that has a per-chart filter -- additive
    *  to (never counted against) {@link #MAX_TILE_HEIGHT}, so the chart's own rendered size is
    *  untouched; only the tile grows to make room for the filter control above it. Matches
    *  {@link #FILTER_BAR_ROW_HEIGHT} exactly: {@link WizDashboardFilterBuilder#buildPerChart}
    *  sizes its control with the SAME {@code FILTER_CONTROL_HEIGHT} (100px) the shared filter
    *  bar uses -- there is no smaller "compact" control variant -- so the same 20px margin
    *  applies here too. */
   private static final int PER_CHART_FILTER_ROW_HEIGHT = 120;

   /**
    * The rendered pixel size for a tile spanning {@code spanCols} columns and {@code spanRows}
    * rows: its natural span-based footprint ({@code spanCols * DASHBOARD_COL_WIDTH} by
    * {@code spanRows * DASHBOARD_ROW_HEIGHT}), capped at {@link #MAX_TILE_WIDTH} by
    * {@link #MAX_TILE_HEIGHT}. Package-private for unit testing (mirrors {@link #gridOrigin}).
    */
   static java.awt.Dimension tilePixelSize(int spanCols, int spanRows) {
      int width = Math.min(spanCols * DASHBOARD_COL_WIDTH, MAX_TILE_WIDTH);
      int height = Math.min(spanRows * DASHBOARD_ROW_HEIGHT, MAX_TILE_HEIGHT);
      return new java.awt.Dimension(width, height);
   }

   /**
    * Row-major grid origin for the tile at flat index {@code i}, given per-tile column spans,
    * row spans, AND whether each tile has a per-chart filter (which adds
    * {@link #PER_CHART_FILTER_ROW_HEIGHT} to that tile's contribution to its row's reserved
    * height). This is the primary implementation; the two overloads below delegate to it with
    * an implicit all-false {@code hasPerChartFilter}, so their behavior is unchanged by this
    * parameter's addition.
    *
    * <p>Packing is still row-major/left-to-right/wrap-at-{@code layoutColumns} — each row's
    * HEIGHT is {@code max} of the tiles placed in it's {@link #tilePixelSize} height (the same
    * CAPPED height {@code composeDashboard} actually renders each chart at, plus
    * {@link #PER_CHART_FILTER_ROW_HEIGHT} for any tile with a per-chart filter), instead of
    * always {@code DASHBOARD_ROW_HEIGHT}. A tile's own Y depends only on the finalized height of
    * every row strictly before it, and every such row is fully scanned (all its tiles' heights
    * folded into that row's height, then closed out) before the loop reaches index {@code i} —
    * so no 2D occupancy grid is needed; a tile with spanRows > 1 does not "block" cells in the
    * row below for placement purposes (that would be true masonry/skyline packing, deliberately
    * not implemented — see the Phase 4 design spec). Returns the (x,y) drop origin in pixels.
    * Package-private for unit testing.
    */
   static java.awt.Point gridOrigin(int[] spanCols, int[] spanRows, boolean[] hasPerChartFilter,
                                     int layoutColumns, int i)
   {
      int col = 0;
      int cumulativeY = 0;
      int rowHeightPx = DASHBOARD_ROW_HEIGHT;   // tallest capped tile height seen so far in the CURRENT (still-open) row

      for(int k = 0; k <= i; k++) {
         int span = Math.max(1, Math.min(spanCols[k], layoutColumns));
         int tileHeightPx = tilePixelSize(spanCols[k], spanRows[k]).height +
            (hasPerChartFilter[k] ? PER_CHART_FILTER_ROW_HEIGHT : 0);

         if(col + span > layoutColumns) {   // doesn't fit in the current row → close it out
            cumulativeY += rowHeightPx + TILE_GUTTER;
            col = 0;
            rowHeightPx = DASHBOARD_ROW_HEIGHT;
         }

         if(k == i) {
            return new java.awt.Point(col * (DASHBOARD_COL_WIDTH + TILE_GUTTER), cumulativeY);
         }

         rowHeightPx = Math.max(rowHeightPx, tileHeightPx);
         col += span;

         if(col >= layoutColumns) {   // row exactly full → close it out now
            cumulativeY += rowHeightPx + TILE_GUTTER;
            col = 0;
            rowHeightPx = DASHBOARD_ROW_HEIGHT;
         }
      }

      return new java.awt.Point(0, 0);   // unreachable (i is always in range)
   }

   /**
    * Back-compat overload for callers without per-chart filter data (implicit
    * {@code hasPerChartFilter[k] == false} for every tile).
    */
   static java.awt.Point gridOrigin(int[] spanCols, int[] spanRows, int layoutColumns, int i) {
      boolean[] noFilters = new boolean[spanCols.length];
      return gridOrigin(spanCols, spanRows, noFilters, layoutColumns, i);
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
