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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
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
 *   <li>Persist via {@link WizUtil#saveWizSheet}, which finalizes the temporary dashboard
 *       worksheet and then runs the save callback, in the same order the Composer uses (a bare
 *       {@code viewsheetService.setViewsheet} call would skip the finalize step and leave the
 *       dashboard pointing at a temp worksheet entry).</li>
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
                               SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.addVisualizationService = addVisualizationService;
      this.securityEngine = securityEngine;
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

         int cumulativeY = 0;   // stack path only

         for(int i = 0; i < entries.size(); i++) {
            int x, y;

            if(grid) {
               java.awt.Point origin = gridOrigin(spans, layoutColumns, i);
               x = origin.x;
               y = origin.y;
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

         WizUtil.saveWizSheet(rvs, principal, savedVsEntry,
            () -> viewsheetService.setViewsheet(rvs.getViewsheet(), savedVsEntry, principal, true, true));

         WizDashboardResult result = new WizDashboardResult();
         result.setSavedViewsheetIdentifier(savedVsEntry.toIdentifier());
         result.setSkipped(skipped);
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

   /** Single-column vertical stride between successive merged visualizations, in pixels. */
   private static final int DASHBOARD_ROW_HEIGHT = 420;

   /** Horizontal stride between grid columns, in pixels (paired with DASHBOARD_ROW_HEIGHT). */
   private static final int DASHBOARD_COL_WIDTH = 640;   // confirm vs composer default viz width

   /**
    * Row-major grid origin for the tile at flat index {@code i}, given per-tile column spans.
    * Returns the (x,y) drop origin in pixels. Package-private for unit testing.
    */
   static java.awt.Point gridOrigin(int[] spanCols, int layoutColumns, int i) {
      int col = 0;
      int row = 0;

      for(int k = 0; k <= i; k++) {
         int span = Math.max(1, Math.min(spanCols[k], layoutColumns));

         if(col + span > layoutColumns) {   // doesn't fit in the current row → wrap
            col = 0;
            row++;
         }

         if(k == i) {
            return new java.awt.Point(col * DASHBOARD_COL_WIDTH, row * DASHBOARD_ROW_HEIGHT);
         }

         col += span;

         if(col >= layoutColumns) {   // row full → next row
            col = 0;
            row++;
         }
      }

      return new java.awt.Point(0, 0);   // unreachable (i is always in range)
   }

   private final ViewsheetService viewsheetService;
   private final AddVisualizationServiceProxy addVisualizationService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(WizDashboardService.class);
}
