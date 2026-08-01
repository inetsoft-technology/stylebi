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
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
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
         // once, up front, so computeGridLayout can factor extra row height into EVERY tile's
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

         // Records each merged chart's own table name and assembly name, looked up after this loop
         // when placing per-chart filters (a filter binds to its chart's table, and is positioned
         // against its chart's actual post-merge geometry).
         java.util.Map<String, String> identifierToTableName = new java.util.HashMap<>();
         java.util.Map<String, String> identifierToAssemblyName = new java.util.HashMap<>();

         // headerHeights[i] is the header space (if any) reserved at THIS tile's own top, per
         // GridLayoutResult's contract: it is a BAND-WIDE amount, not a per-tile one -- a tile
         // with no per-chart filter of its own can still have headerHeights[i] > 0 if it shares a
         // band with one, so every chart in the band starts at the same Y (see computeGridLayout).
         GridLayoutResult gridResult =
            grid ? computeGridLayout(spans, rowSpans, hasPerChartFilter, layoutColumns) : null;
         List<TilePlacement> placements = grid ? gridResult.placements() : null;
         int[] headerHeights = grid ? gridResult.headerHeights() : null;

         int cumulativeY = topOffset;   // stack path only

         for(int i = 0; i < entries.size(); i++) {
            int x, y, tileTopY;
            // Declared here (rather than inside the `if(grid)` block below) so it's also in
            // scope for the pixelSize computation further down, which needs placement.width()/
            // height() regardless of which branch set x/y/tileTopY.
            TilePlacement placement = grid ? placements.get(i) : null;

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

               x = placement.x() + CANVAS_MARGIN;
               tileTopY = placement.y() + topOffset + CANVAS_MARGIN;
               // The chart itself starts BELOW the reserved header strip, if this tile's BAND has
               // one -- the tile's own reserved footprint (computed via computeGridLayout above)
               // already accounts for that extra height, so this only affects where the CHART
               // renders within it, not how much space the tile as a whole takes.
               y = tileTopY + headerHeights[i];
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
            // placement.height() includes the band's header reservation (and any stretch growth)
            // -- subtract the header height back out here so the CHART itself (not its tile's
            // header strip) gets resized; any stretch growth survives this subtraction since it
            // was added on top of the same base natural+header height.
            java.awt.Dimension pixelSize = grid ?
               new java.awt.Dimension(placement.width(), placement.height() - headerHeights[i]) :
               null;

            try {
               AddVisualizationService.MergedVisualizationInfo mergedInfo = addVisualizationService.addVisualization(
                  runtimeId, entries.get(i), x, y, 1.0f, pixelSize, principal);

               if(grid) {
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
         List<WizDashboardFilterBuilder.FilterControlPlacement> bandPlacements = new ArrayList<>();
         List<String> perChartFiltersApplied = new ArrayList<>();
         List<String> perChartFiltersSkipped = new ArrayList<>();
         boolean hasPerChartFilters = grid && event.getPerChartFilters() != null &&
            !event.getPerChartFilters().isEmpty();

         if(hasFilters || hasPerChartFilters) {
            Viewsheet vs = rvs.getViewsheet();
            Worksheet baseWs = (Worksheet) assetRepository.getSheet(
               vs.getBaseEntry(), principal, false, AssetContent.ALL);

            if(hasFilters) {
               // Align the shared filter bar to the merged charts' actual left edge and span a
               // toolbar band across their full width -- both derived from the charts' real
               // post-merge geometry (addVisualization offsets each chart by +CANVAS_MARGIN, so the
               // raw canvas margin no longer matches where the columns landed). The band is added
               // BEFORE the controls so they layer on top of it.
               java.awt.Rectangle chartsBox = mergedChartsBoundingBox(vs, identifierToAssemblyName.values());
               int barLeftX = chartsBox != null ? chartsBox.x : CANVAS_MARGIN;

               if(chartsBox != null) {
                  bandPlacements = filterBuilder.buildFilterBarBand(
                     vs, chartsBox.x, FILTER_BAND_TOP, chartsBox.width, FILTER_BAND_HEIGHT);
               }

               filterResult = applyFilters(vs, baseWs, event.getFilters(), barLeftX);
            }

            if(hasPerChartFilters) {
               for(WizDashboardEvent.PerChartFilterSpec spec : event.getPerChartFilters()) {
                  String tableName = identifierToTableName.get(spec.getIdentifier());
                  String chartName = identifierToAssemblyName.get(spec.getIdentifier());
                  inetsoft.uql.viewsheet.VSAssembly chartAssembly =
                     chartName != null ? vs.getAssembly(chartName) : null;

                  if(tableName == null || chartAssembly == null) {
                     perChartFiltersSkipped.add(spec.getField());
                     continue;
                  }

                  // Place the filter against the chart's ACTUAL post-merge geometry, not the
                  // pre-merge tile bounds: AddVisualizationService#addVisualization applies its
                  // own margin offset to each merged chart (observed +CANVAS_MARGIN on both axes),
                  // so the tile's computed x/y no longer match where the chart really landed.
                  // Reading the chart's own pixel offset/size and placing the control flush above
                  // it (same x, same width, filter-bottom == chart-top) is what actually makes the
                  // two line up as one enclosing card (see WizDashboardFilterBuilder#
                  // applyGroupedCardStyle) regardless of that merge offset.
                  java.awt.Point chartPos = chartAssembly.getPixelOffset();
                  java.awt.Dimension chartSize = chartAssembly.getPixelSize();
                  int filterX = chartPos.x;
                  int filterWidth = chartSize.width;
                  int filterY = chartPos.y - PER_CHART_FILTER_ROW_HEIGHT;

                  WizDashboardFilterBuilder.FilterControlPlacement placement = applyPerChartFilter(
                     vs, baseWs, spec, filterX, filterY, filterWidth, PER_CHART_FILTER_ROW_HEIGHT,
                     tableName, chartName);

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

         // Adaptive layouts are grid-only (mirroring the per-chart-filter and per-chart-filter-
         // gutter features) and skip any dashboard with a per-chart filter -- see the plan's
         // Global Constraints for why. Build them from the SAME identifiers/spans/assembly-name
         // tracking the merge loop above already populated.
         boolean hasPerChartFiltersRequested = event.getPerChartFilters() != null &&
            !event.getPerChartFilters().isEmpty();

         if(grid && !hasPerChartFiltersRequested) {
            List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements = new ArrayList<>();

            if(filterResult != null) {
               filterPlacements.addAll(filterResult.placements());
            }

            // Carry the toolbar band + divider rectangles into every adaptive tier too (same as the
            // filter controls above) -- an assembly with no per-tier layout entry is hidden when
            // that tier is selected, at their base position (the existing documented scope limit).
            filterPlacements.addAll(bandPlacements);

            String[] assemblyNamesInOrder = identifiers.stream()
               .map(identifierToAssemblyName::get)
               .filter(java.util.Objects::nonNull)
               .toArray(String[]::new);

            if(assemblyNamesInOrder.length == identifiers.size()) {
               LayoutInfo layoutInfo = new LayoutInfo();
               layoutInfo.setViewsheetLayouts(
                  buildAlternateLayouts(assemblyNamesInOrder, spans, rowSpans, topOffset, filterPlacements));
               rvs.getViewsheet().setLayoutInfo(layoutInfo);
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
    * <p>Package-private seam for unit testing (mirrors {@link #computeGridLayout}) — lets
    * {@link WizDashboardServiceGridTest} exercise the mapping/delegation with a mocked
    * {@link WizDashboardFilterBuilder}, independent of the live-engine-only compose+save path.
    */
   WizDashboardFilterBuilder.FilterResult applyFilters(Viewsheet vs, Worksheet baseWs,
                                                        List<WizDashboardEvent.FilterSpec> specs, int startX)
   {
      List<WizDashboardFilterBuilder.FilterRequest> reqs = specs.stream()
         .map(f -> new WizDashboardFilterBuilder.FilterRequest(
            f.getField(), f.getDataType(), f.getLabel(), f.isPreAggregation()))
         .collect(java.util.stream.Collectors.toList());
      return filterBuilder.build(vs, baseWs, reqs, startX);
   }

   /**
    * The bounding box (in pixels) enclosing every merged chart's ACTUAL post-merge position/size,
    * or {@code null} if none resolve. Used to align + span the shared filter toolbar to the real
    * chart columns (addVisualization offsets each chart, so pre-merge tile bounds don't match).
    */
   private static java.awt.Rectangle mergedChartsBoundingBox(
      Viewsheet vs, java.util.Collection<String> chartAssemblyNames)
   {
      int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
      boolean any = false;

      for(String name : chartAssemblyNames) {
         inetsoft.uql.viewsheet.VSAssembly a = name != null ? vs.getAssembly(name) : null;

         if(a == null) {
            continue;
         }

         java.awt.Point p = a.getPixelOffset();
         java.awt.Dimension d = a.getPixelSize();
         minX = Math.min(minX, p.x);
         minY = Math.min(minY, p.y);
         maxX = Math.max(maxX, p.x + d.width);
         maxY = Math.max(maxY, p.y + d.height);
         any = true;
      }

      return any ? new java.awt.Rectangle(minX, minY, maxX - minX, maxY - minY) : null;
   }

   /**
    * Maps a single {@link WizDashboardEvent.PerChartFilterSpec} to a
    * {@link WizDashboardFilterBuilder.FilterRequest} and delegates to
    * {@link WizDashboardFilterBuilder#buildPerChart}. Package-visible seam for unit testing
    * (mirrors {@link #applyFilters}), independent of the live-engine-only compose+save path.
    */
   WizDashboardFilterBuilder.FilterControlPlacement applyPerChartFilter(
      Viewsheet vs, Worksheet baseWs, WizDashboardEvent.PerChartFilterSpec spec,
      int x, int y, int width, int height, String chartTableName, String chartAssemblyName)
   {
      WizDashboardFilterBuilder.FilterRequest req = new WizDashboardFilterBuilder.FilterRequest(
         spec.getField(), spec.getDataType(), spec.getLabel(), spec.isPreAggregation());
      return filterBuilder.buildPerChart(vs, baseWs, x, y, width, height, req, chartTableName, chartAssemblyName);
   }

   /** Vertical row stride between successive merged visualizations, in pixels — used by both
    *  the single-column stack path and the grid path's row advance. */
   private static final int DASHBOARD_ROW_HEIGHT = 420;

   /** Reserved row height for the top filter bar, in pixels — sized so the compact filter toolbar
    *  band clears the first chart with a small gap (the chart is pushed to
    *  FILTER_BAR_ROW_HEIGHT + CANVAS_MARGIN + merge offset ≈ 60+24+24 = 108, just below the band's
    *  bottom at 76). Not a full chart tile's height. Dropped from 76 alongside the controls
    *  shrinking from a 60px to a 44px band: left at 76 the gap under the band would have grown to
    *  48px, which reads as the bar having been abandoned rather than deliberately spaced. */
   static final int FILTER_BAR_ROW_HEIGHT = 60;

   /** Top y (px) of the filter toolbar band -- a small inset above the controls (which sit at
    *  CANVAS_MARGIN=24). */
   private static final int FILTER_BAND_TOP = 12;

   /** Filter toolbar band height (px): the inset above the controls (they sit at CANVAS_MARGIN,
    *  the band starts at FILTER_BAND_TOP), the control band itself, and a small bottom inset. So
    *  the band bottom (its divider) lands at 12+64=76 -- above the first chart row (which the
    *  reserved FILTER_BAR_ROW_HEIGHT + margin + merge offset pushes to ~108), leaving a clean gap
    *  without the band looking oversized.
    *
    *  DERIVED from the builder's own control height rather than restated as a literal: the two were
    *  independent numbers, and shrinking the controls left the band still 80px tall with a visibly
    *  empty strip beneath them. */
   private static final int FILTER_BAND_BOTTOM_INSET = 8;
   // CANVAS_MARGIN is qualified, not bare: it is declared further down this file, and a SIMPLE-name
   // reference to a field declared later in the same class is an illegal forward reference.
   private static final int FILTER_BAND_HEIGHT = (WizDashboardService.CANVAS_MARGIN - FILTER_BAND_TOP)
      + WizDashboardFilterBuilder.FILTER_CONTROL_HEIGHT + FILTER_BAND_BOTTOM_INSET;

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
    *  {@link #computeGridLayout}); only the rendered chart size is capped, leaving a margin of
    *  unused space inside an oversized cell rather than a stretched chart. */
   private static final int MAX_TILE_WIDTH = 900;
   private static final int MAX_TILE_HEIGHT = 600;

   /** Extra header height reserved, in pixels, above a chart that owns a per-chart filter, OR
    *  above a chart that shares a band with a genuinely side-by-side sibling (another
    *  first-tile-in-its-column) that does -- additive to (never counted against)
    *  {@link #MAX_TILE_HEIGHT}, so the chart's own rendered size is untouched; only the tile
    *  grows to make room. See {@link #closeBand}/{@link GridLayoutResult} for the exact scoping
    *  (deliberately NOT "every tile in the band," which would shift unrelated charts stacked
    *  anywhere below an unrelated filtered one in the common single-column dashboard shape).
    *  {@link WizDashboardFilterBuilder#buildPerChart} sizes its control to this height and renders
    *  it as a DROPDOWN (one title row) rather than an open checkbox list, so this only has to cover
    *  a single collapsed control -- not several visible category rows. It was 120 while those
    *  controls rendered as open lists, which cost ~480px of pure filter chrome on a board with four
    *  per-chart filters; the shared filter bar stays separately sized by
    *  {@link WizDashboardFilterBuilder}'s FILTER_CONTROL_HEIGHT.
    *  Package-private (matching {@link #FILTER_BAR_ROW_HEIGHT}) so the grid tests assert geometry
    *  against the constant instead of a literal that silently rots when this is tuned. */
   static final int PER_CHART_FILTER_ROW_HEIGHT = 28;

   /**
    * The rendered pixel size for a tile spanning {@code spanCols} columns and {@code spanRows}
    * rows: its natural span-based footprint ({@code spanCols * DASHBOARD_COL_WIDTH} by
    * {@code spanRows * DASHBOARD_ROW_HEIGHT}), capped at {@link #MAX_TILE_WIDTH} by
    * {@link #MAX_TILE_HEIGHT}. Package-private for unit testing (mirrors {@link #computeGridLayout}).
    */
   static java.awt.Dimension tilePixelSize(int spanCols, int spanRows) {
      int width = Math.min(spanCols * DASHBOARD_COL_WIDTH, MAX_TILE_WIDTH);
      int height = Math.min(spanRows * DASHBOARD_ROW_HEIGHT, MAX_TILE_HEIGHT);
      return new java.awt.Dimension(width, height);
   }

   /**
    * The (x, y, width, height) footprint assigned to a tile by {@link #computeGridLayout}. Height
    * may be larger than the tile's own natural {@link #tilePixelSize} height if the stretch pass
    * grew it to match a taller neighbor sharing its band -- see the design spec's Stretch pass
    * section (docs/superpowers/specs/2026-07-25-dashboard-2d-grid-packing-design.md, in the
    * stylebi-wiz repo). {@code y} already reflects any band-wide per-chart-filter header shift
    * (see {@link GridLayoutResult#headerHeights()}) -- it is NOT the tile's natural band-relative
    * position when the band contains a filter. Package-private for unit testing.
    */
   record TilePlacement(int x, int y, int width, int height) {}

   /**
    * Return type of {@link #computeGridLayout}: every tile's placement, plus a PARALLEL
    * {@code headerHeights} array (same index as {@code spanCols}/{@code spanRows}/{@code placements})
    * recording how much header space, in pixels, is reserved at the TOP of each tile.
    *
    * <p>Every tile that personally owns a per-chart filter always reserves
    * {@link #PER_CHART_FILTER_ROW_HEIGHT} for itself. On top of that, when TWO OR MORE columns in
    * the SAME band are genuinely side-by-side siblings (each is the FIRST tile placed in its own
    * column) and at least one of those first-tiles owns a filter, EVERY other first-tile in that
    * band reserves the same header height too -- even if it has no filter of its own -- so the
    * row's tops visually align (a filterless sibling just gets blank space where its neighbor's
    * control renders). See {@link #closeBand} for where this is computed.
    *
    * <p>Deliberately scoped to FIRST-tiles only, not "every tile sharing a band": a band can
    * legitimately span the entire dashboard height in the common single-column (everything
    * stacked vertically, nothing ever fits beside anything) case, where there is only ever ONE
    * column and thus nothing to visually align against -- an earlier version of this reservation
    * applied to the whole band and, in that shape, shifted every chart in the dashboard down
    * whenever ANY one of them, however far down the stack, happened to own a filter. {@code
    * headerHeights[i] == 0} for a tile that neither owns a filter nor shares a band with a
    * misaligned filtered sibling (the overwhelmingly common case).
    */
   record GridLayoutResult(List<TilePlacement> placements, int[] headerHeights) {}

   /**
    * Tracks one open column within the CURRENT (still-open) band during
    * {@link #computeGridLayout}'s single pass: its fixed x-position and slot width (set by
    * whichever tile first opened it), the indices (into the tiles arrays) of every tile stacked
    * into it so far in order, and its running content height (columnY) -- the y-offset, relative
    * to the band's top, where the NEXT tile stacked into this column would start.
    */
   private static final class Column {
      final int x;
      final int slotWidth;
      final List<Integer> tileIndices = new ArrayList<>();
      int columnY;

      Column(int x, int slotWidth) {
         this.x = x;
         this.slotWidth = slotWidth;
      }
   }

   /**
    * Computes every tile's final (x, y, width, height) footprint in one pass, per the
    * shelf-skyline algorithm in
    * docs/superpowers/specs/2026-07-25-dashboard-2d-grid-packing-design.md (stylebi-wiz repo):
    * tiles are processed once, in the given order, into a sequence of bands. Within a band, each
    * tile first tries to open a new column to the right (same width check the old row-major
    * packer used); failing that, it stacks into whichever existing column in the band is wide
    * enough for it and has the least accumulated height so far (no height ceiling -- a stacked
    * column may end up taller than its siblings, which the stretch pass corrects for); failing
    * that too (no open column is wide enough), the band closes, every column shorter than the
    * band's tallest has its LAST tile stretched to close the gap, and a fresh band starts with
    * this tile as its first column. Replaces the old per-tile-index row-major packer, which could
    * not support the stretch pass (whether tile i stretches depends on later tiles that might
    * still join its column, and on the band's eventual final height -- neither knowable from a
    * partial replay).
    *
    * <p>Package-private for unit testing.
    */
   static GridLayoutResult computeGridLayout(
      int[] spanCols, int[] spanRows, boolean[] hasPerChartFilter, int layoutColumns)
   {
      int n = spanCols.length;
      TilePlacement[] result = new TilePlacement[n];
      int[] headerHeights = new int[n];
      int availableRowWidth = layoutColumns * DASHBOARD_COL_WIDTH + (layoutColumns - 1) * TILE_GUTTER;

      int cumulativeY = 0;
      List<Column> band = new ArrayList<>();
      int rowWidthUsed = 0;

      for(int k = 0; k < n; k++) {
         java.awt.Dimension natural = tilePixelSize(spanCols[k], spanRows[k]);
         int tileWidth = natural.width;
         // Every tile always reserves its OWN filter height directly, regardless of stack
         // position -- this part is unconditional, exactly like the tile's own natural size.
         int tileHeight = natural.height + (hasPerChartFilter[k] ? PER_CHART_FILTER_ROW_HEIGHT : 0);

         if(hasPerChartFilter[k]) {
            headerHeights[k] = PER_CHART_FILTER_ROW_HEIGHT;
         }

         int neededWidthForNewColumn =
            rowWidthUsed == 0 ? tileWidth : rowWidthUsed + TILE_GUTTER + tileWidth;

         if(neededWidthForNewColumn <= availableRowWidth) {
            int x = rowWidthUsed == 0 ? 0 : rowWidthUsed + TILE_GUTTER;
            Column column = new Column(x, tileWidth);
            column.tileIndices.add(k);
            column.columnY = tileHeight;
            band.add(column);
            result[k] = new TilePlacement(x, cumulativeY, tileWidth, tileHeight);
            rowWidthUsed = x + tileWidth;
            continue;
         }

         Column target = null;

         for(Column column : band) {
            if(column.slotWidth >= tileWidth && (target == null || column.columnY < target.columnY)) {
               target = column;
            }
         }

         if(target != null) {
            int y = cumulativeY + target.columnY + TILE_GUTTER;
            target.tileIndices.add(k);
            target.columnY += TILE_GUTTER + tileHeight;
            result[k] = new TilePlacement(target.x, y, tileWidth, tileHeight);
            continue;
         }

         int closedBandHeight = closeBand(band, result, headerHeights, hasPerChartFilter);
         cumulativeY += closedBandHeight + TILE_GUTTER;
         band = new ArrayList<>();
         rowWidthUsed = 0;

         Column column = new Column(0, tileWidth);
         column.tileIndices.add(k);
         column.columnY = tileHeight;
         band.add(column);
         result[k] = new TilePlacement(0, cumulativeY, tileWidth, tileHeight);
         rowWidthUsed = tileWidth;
      }

      closeBand(band, result, headerHeights, hasPerChartFilter);

      return new GridLayoutResult(java.util.Arrays.asList(result), headerHeights);
   }

   /**
    * Closes a band in three passes and returns its final height:
    * <ol>
    *   <li><b>Row-alignment pass</b> (new): if two or more columns exist in this band (i.e. there
    *       is an actual side-by-side row to keep aligned) and at least one column's FIRST tile
    *       owns a per-chart filter, every OTHER column's first tile reserves the same
    *       {@link #PER_CHART_FILTER_ROW_HEIGHT} too -- grown directly into that tile's own height
    *       (and {@code columnY}), with every tile stacked below it in the SAME column shifted
    *       down by the same amount to make room. A column whose first tile already owns a filter
    *       is skipped (it already reserved this in the main loop -- doing it again would double
    *       it). With only one column in the band (the common single-column-stack dashboard shape)
    *       this pass is a no-op: there is no sibling to align against.</li>
    *   <li><b>Stretch pass</b> (unchanged, but now runs against the row-alignment pass's
    *       already-updated {@code columnY} values): for every column shorter than the band's
    *       tallest column, grows the LAST tile placed in that column so its rendered height
    *       closes the gap. Never shrinks a tile.</li>
    * </ol>
    */
   private static int closeBand(
      List<Column> band, TilePlacement[] result, int[] headerHeights, boolean[] hasPerChartFilter)
   {
      if(band.size() > 1) {
         boolean anyFirstTileHasFilter = band.stream()
            .anyMatch(c -> hasPerChartFilter[c.tileIndices.get(0)]);

         if(anyFirstTileHasFilter) {
            for(Column column : band) {
               int firstIndex = column.tileIndices.get(0);

               if(hasPerChartFilter[firstIndex]) {
                  continue;   // already reserved its own header height above
               }

               TilePlacement firstTile = result[firstIndex];
               result[firstIndex] = new TilePlacement(firstTile.x(), firstTile.y(),
                  firstTile.width(), firstTile.height() + PER_CHART_FILTER_ROW_HEIGHT);
               headerHeights[firstIndex] = PER_CHART_FILTER_ROW_HEIGHT;
               column.columnY += PER_CHART_FILTER_ROW_HEIGHT;

               for(int i = 1; i < column.tileIndices.size(); i++) {
                  int idx = column.tileIndices.get(i);
                  TilePlacement old = result[idx];
                  result[idx] = new TilePlacement(
                     old.x(), old.y() + PER_CHART_FILTER_ROW_HEIGHT, old.width(), old.height());
               }
            }
         }
      }

      int bandHeight = band.stream().mapToInt(c -> c.columnY).max().orElse(0);

      for(Column column : band) {
         int shortfall = bandHeight - column.columnY;

         if(shortfall > 0) {
            int lastTileIndex = column.tileIndices.get(column.tileIndices.size() - 1);
            TilePlacement old = result[lastTileIndex];
            result[lastTileIndex] =
               new TilePlacement(old.x(), old.y(), old.width(), old.height() + shortfall);
         }
      }

      return bandHeight;
   }

   /** Compact tile size forced on every chart in the Mobile tier, ignoring its own type-based
    *  span -- desktop tile constants (900x600 cap) are far too large for a phone screen. */
   private static final int MOBILE_TILE_WIDTH = 350;
   private static final int MOBILE_TILE_HEIGHT = 300;

   /**
    * Builds the Mobile/Wide/Ultrawide {@link ViewsheetLayout} variants for a composed dashboard,
    * reusing the same {@link #computeGridLayout}/{@link #tilePixelSize} math the base (default)
    * layout already uses, computed for different column counts -- Mobile forces every chart to a fixed
    * {@link #MOBILE_TILE_WIDTH}x{@link #MOBILE_TILE_HEIGHT} tile stacked vertically, ignoring its
    * own span entirely.
    *
    * <p>Every chart AND every filter control (from {@code filterPlacements}, carried over
    * UNCHANGED at its base position/size into all three tiers -- a deliberate scope limit, see
    * the plan's Global Constraints) gets a {@link VSAssemblyLayout} entry in every returned
    * layout: {@link AbstractLayout#apply} hides any assembly with no entry when a layout is
    * selected, so omitting one here would make it vanish on that tier.
    *
    * <p>Callers must not invoke this for a dashboard with any per-chart filter (see Global
    * Constraints) -- this method has no per-tile Y-shift logic for that case.
    */
   static List<ViewsheetLayout> buildAlternateLayouts(
      String[] assemblyNames, int[] spanCols, int[] spanRows, int topOffset,
      List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements)
   {
      List<ViewsheetLayout> layouts = new ArrayList<>();
      layouts.add(buildMobileLayout(assemblyNames, topOffset, filterPlacements));
      layouts.add(buildGridTierLayout(assemblyNames, spanCols, spanRows, topOffset, filterPlacements,
         3, WizDeviceBootstrapService.WIDE_DEVICE_ID));
      layouts.add(buildGridTierLayout(assemblyNames, spanCols, spanRows, topOffset, filterPlacements,
         4, WizDeviceBootstrapService.ULTRAWIDE_DEVICE_ID));
      return layouts;
   }

   private static ViewsheetLayout buildMobileLayout(
      String[] assemblyNames, int topOffset,
      List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements)
   {
      ViewsheetLayout layout = new ViewsheetLayout();
      layout.setName("Mobile");
      layout.setMobileOnly(true);
      layout.setDeviceIds(new String[]{ WizDeviceBootstrapService.MOBILE_DEVICE_ID });
      // ViewsheetLayout defaults both flags to true, which forces the runtime viewsheet into
      // "scale to screen" mode and stretches every tile's assigned pixel size to fill the actual
      // browser width -- defeating the whole point of a fixed, pixel-exact adaptive grid.
      layout.setScaleToScreen(false);
      layout.setFitToWidth(false);

      List<VSAssemblyLayout> assemblyLayouts = new ArrayList<>();
      int y = topOffset + CANVAS_MARGIN;

      for(String assemblyName : assemblyNames) {
         assemblyLayouts.add(new VSAssemblyLayout(assemblyName, new java.awt.Point(CANVAS_MARGIN, y),
            new java.awt.Dimension(MOBILE_TILE_WIDTH, MOBILE_TILE_HEIGHT)));
         y += MOBILE_TILE_HEIGHT + TILE_GUTTER;
      }

      addFilterControlLayouts(assemblyLayouts, filterPlacements);
      layout.setVSAssemblyLayouts(assemblyLayouts);
      return layout;
   }

   private static ViewsheetLayout buildGridTierLayout(
      String[] assemblyNames, int[] spanCols, int[] spanRows, int topOffset,
      List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements,
      int layoutColumns, String deviceId)
   {
      ViewsheetLayout layout = new ViewsheetLayout();
      layout.setName(deviceId);
      layout.setMobileOnly(false);
      layout.setDeviceIds(new String[]{ deviceId });
      // See buildMobileLayout's comment: without this, the runtime viewsheet scales every tile up
      // to fill the actual browser width instead of rendering the pixel-exact computed grid.
      layout.setScaleToScreen(false);
      layout.setFitToWidth(false);

      boolean[] noPerChartFilters = new boolean[assemblyNames.length];
      List<TilePlacement> placements =
         computeGridLayout(spanCols, spanRows, noPerChartFilters, layoutColumns).placements();
      List<VSAssemblyLayout> assemblyLayouts = new ArrayList<>();

      for(int i = 0; i < assemblyNames.length; i++) {
         TilePlacement placement = placements.get(i);
         java.awt.Point pos = new java.awt.Point(
            placement.x() + CANVAS_MARGIN, placement.y() + topOffset + CANVAS_MARGIN);
         java.awt.Dimension size = new java.awt.Dimension(placement.width(), placement.height());
         assemblyLayouts.add(new VSAssemblyLayout(assemblyNames[i], pos, size));
      }

      addFilterControlLayouts(assemblyLayouts, filterPlacements);
      layout.setVSAssemblyLayouts(assemblyLayouts);
      return layout;
   }

   private static void addFilterControlLayouts(
      List<VSAssemblyLayout> assemblyLayouts,
      List<WizDashboardFilterBuilder.FilterControlPlacement> filterPlacements)
   {
      for(WizDashboardFilterBuilder.FilterControlPlacement placement : filterPlacements) {
         assemblyLayouts.add(new VSAssemblyLayout(
            placement.assemblyName(), placement.position(), placement.size()));
      }
   }

   private final ViewsheetService viewsheetService;
   private final AddVisualizationServiceProxy addVisualizationService;
   private final SecurityEngine securityEngine;
   private final WizDashboardFilterBuilder filterBuilder;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(WizDashboardService.class);
}
