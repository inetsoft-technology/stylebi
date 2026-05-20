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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.WizUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.wiz.service.VisualizationService;
import inetsoft.web.wiz.service.GenerateWsService;
import inetsoft.web.wiz.model.WizVisualizationSaveEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class WizVisualizationService {
   public WizVisualizationService(ViewsheetService viewsheetService,
                                   AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   /**
    * Returns the folder tree rooted at {@link VisualizationService#VISUALIZATION_COMPONENTS_FOLDER_PATH}.
    * Only folder nodes are included (no viewsheet leaves). Used by the WIZ Save dialog.
    */
   public TreeNodeModel getVisualizationFolderTree(Principal principal) throws Exception {
      AssetEntry rootEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH, null);

      ensureRepositoryFolderExists(rootEntry, principal);

      AssetEntry.Selector treeSelector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.REPOSITORY_FOLDER, AssetEntry.Type.VIEWSHEET);

      return buildFolderTree(rootEntry, "Visualization Components", principal, treeSelector);
   }

   /**
    * Saves a single assembly from the chat's shared ViewSheet into a new dedicated ViewSheet
    * under the user-selected folder in {@link VisualizationService#VISUALIZATION_COMPONENTS_FOLDER_PATH}.
    *
    * <p>Steps:
    * <ol>
    *   <li>Load the source (chat shared) ViewSheet and locate the target assembly.</li>
    *   <li>Load the source Worksheet and clone it.</li>
    *   <li>Remove all worksheet tables NOT required by the target assembly.</li>
    *   <li>Persist the trimmed Worksheet under
    *       {@link VisualizationService#WORKSHEET_COMPONENTS_FOLDER_PATH}.</li>
    *   <li>Create a new single-assembly ViewSheet pointing to the new Worksheet.</li>
    *   <li>Set {@code visualizationScope=SHARED}, {@code conversationId}, and
    *       {@code sourceAssemblyName} on the new AssetEntry.</li>
    *   <li>Persist the new ViewSheet under the user-selected folder.</li>
    * </ol>
    */
   public WizVisualizationSaveResult saveVisualization(WizVisualizationSaveEvent event,
                                                        Principal principal)
      throws Exception
   {
      if(Tool.isEmptyString(event.getSourceViewsheetIdentifier())) {
         throw new IllegalArgumentException("sourceViewsheetIdentifier is required");
      }

      if(Tool.isEmptyString(event.getAssemblyName())) {
         throw new IllegalArgumentException("assemblyName is required");
      }

      // ── Step 1: Load source ViewSheet ────────────────────────────────────────
      AssetEntry sourceVsEntry = AssetEntry.createAssetEntry(event.getSourceViewsheetIdentifier());

      if(sourceVsEntry == null) {
         throw new IllegalArgumentException(
            "Cannot parse sourceViewsheetIdentifier: " + event.getSourceViewsheetIdentifier());
      }

      Viewsheet sourceVs = (Viewsheet) assetRepository.getSheet(
         sourceVsEntry, principal, false, AssetContent.ALL);

      if(sourceVs == null) {
         throw new IllegalStateException(
            "Source ViewSheet not found: " + event.getSourceViewsheetIdentifier());
      }

      // ── Step 2: Find target assembly ─────────────────────────────────────────
      Assembly rawAssembly = sourceVs.getAssembly(event.getAssemblyName());

      if(rawAssembly == null) {
         throw new IllegalArgumentException(
            "Assembly not found in source ViewSheet: " + event.getAssemblyName());
      }

      if(!(rawAssembly instanceof VSAssembly)) {
         throw new IllegalArgumentException(
            "Assembly is not a viewsheet assembly: " + event.getAssemblyName());
      }

      VSAssembly assembly = (VSAssembly) rawAssembly;

      // ── Step 3: Resolve target folder path ───────────────────────────────────
      String targetFolderPath = event.getTargetFolderPath();

      if(Tool.isEmptyString(targetFolderPath)) {
         targetFolderPath = VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH;
      }

      // ── Step 4: Save the trimmed Worksheet ───────────────────────────────────
      String alias = !Tool.isEmptyString(event.getDisplayName())
         ? event.getDisplayName()
         : event.getAssemblyName();
      AssetEntry newWsEntry = saveWorksheet(sourceVs, assembly, targetFolderPath, alias, principal);

      // ── Step 5: Build new single-assembly ViewSheet ───────────────────────────
      Viewsheet newVs = new Viewsheet();

      if(newWsEntry != null) {
         newVs.setBaseEntry(newWsEntry);
      }
      else if(sourceVs.getBaseEntry() != null) {
         // Fallback: keep original worksheet reference if save was skipped
         newVs.setBaseEntry(sourceVs.getBaseEntry());
      }

      VSAssembly cloned = (VSAssembly) assembly.clone();
      cloned.setPrimary(true);
      newVs.addAssembly(cloned);

      // ── Step 6: Ensure target ViewSheet folder exists ─────────────────────────
      AssetEntry targetFolder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         targetFolderPath, null);
      ensureRepositoryFolderExists(targetFolder, principal);

      // ── Step 7: Create new AssetEntry and set WIZ properties ─────────────────
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String newPath = targetFolderPath + "/" + UUID.randomUUID();
      AssetEntry newVsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, newPath, pId);

      // Set a human-readable alias so the viewsheet is identifiable in the repository.
      if(!Tool.isEmptyString(alias)) {
         newVsEntry.setAlias(alias);
      }

      newVsEntry.setProperty("visualizationScope", WizUtil.VisualizationScope.PUBLIC.getValue());
      newVsEntry.setProperty("sourceAssemblyName", event.getAssemblyName());

      if(!Tool.isEmptyString(event.getConversationId())) {
         newVsEntry.setProperty("conversationId", event.getConversationId());
      }

      if(!Tool.isEmptyString(event.getDisplayName())) {
         newVsEntry.setProperty("displayName", event.getDisplayName());
      }

      // ── Step 8: Persist ViewSheet ─────────────────────────────────────────────
      viewsheetService.setViewsheet(newVs, newVsEntry, principal, true, true);

      WizVisualizationSaveResult result = new WizVisualizationSaveResult();
      result.setSavedViewsheetIdentifier(newVsEntry.toIdentifier());
      return result;
   }

   // ── Private helpers ───────────────────────────────────────────────────────────

   /**
    * Loads the source Worksheet, clones it, removes all tables not required by the
    * given assembly, and persists the trimmed clone under
    * {@link VisualizationService#WORKSHEET_COMPONENTS_FOLDER_PATH}/{targetSubFolder}/{uuid}.
    *
    * @return the saved Worksheet's AssetEntry, or {@code null} if no worksheet was found
    */
   private AssetEntry saveWorksheet(Viewsheet sourceVs, VSAssembly assembly,
                                     String targetFolderPath, String alias, Principal principal)
      throws Exception
   {
      AssetEntry sourceWsEntry = sourceVs.getBaseEntry();

      if(sourceWsEntry == null || !sourceWsEntry.isWorksheet()) {
         return null;
      }

      // Only DataVSAssembly references a worksheet table; others need no worksheet.
      String rootTable = assembly instanceof DataVSAssembly
         ? ((DataVSAssembly) assembly).getTableName()
         : null;

      if(Tool.isEmptyString(rootTable)) {
         return null;
      }

      Worksheet sourceWs = (Worksheet) assetRepository.getSheet(
         sourceWsEntry, principal, false, AssetContent.ALL);

      if(sourceWs == null) {
         LOG.warn("Source Worksheet not found: {}", sourceWsEntry.getPath());
         return null;
      }

      Set<String> requiredTables = collectRequiredTables(sourceWs, rootTable);

      // Clone and strip all tables not in the dependency chain of the target assembly.
      // collectRequiredTables already follows MirrorAssembly chains, so no special
      // casing is needed here.
      Worksheet newWs = (Worksheet) sourceWs.clone();

      for(Assembly wsAssembly : newWs.getAssemblies()) {
         if(!requiredTables.contains(wsAssembly.getName())) {
            newWs.removeAssembly(wsAssembly.getName());
         }
      }

      // Resolve worksheet target folder — mirrors the viewsheet folder under a parallel root
      String wsFolderPath = resolveWorksheetFolderPath(targetFolderPath);
      AssetEntry wsFolder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, wsFolderPath, null);
      ensureWorksheetFolderExists(wsFolder, principal);

      // Persist
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      String wsPath = wsFolderPath + "/" + UUID.randomUUID();
      AssetEntry newWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, wsPath, pId);

      if(!Tool.isEmptyString(alias)) {
         newWsEntry.setAlias(alias);
      }

      viewsheetService.setWorksheet(newWs, newWsEntry, principal, true, true);
      return newWsEntry;
   }

   /**
    * Collects the BFS dependency chain of worksheet table names starting from
    * {@code rootTableName}. Follows join-table sub-tables and mirror-table targets.
    * Returns an empty set (keep all) when {@code rootTableName} is blank.
    */
   private Set<String> collectRequiredTables(Worksheet ws, String rootTableName) {
      Set<String> required = new LinkedHashSet<>();

      if(Tool.isEmptyString(rootTableName) || ws == null) {
         return required;
      }

      Deque<String> queue = new ArrayDeque<>();
      queue.push(rootTableName);

      while(!queue.isEmpty()) {
         String name = queue.pop();

         if(required.contains(name)) {
            continue;
         }

         required.add(name);
         Assembly a = ws.getAssembly(name);

         if(a instanceof AbstractJoinTableAssembly joinTable) {
            Enumeration<?> opTables = joinTable.getOperatorTables();

            while(opTables.hasMoreElements()) {
               String[] pair = (String[]) opTables.nextElement();

               if(pair[0] != null) {
                  queue.push(pair[0]);
               }

               if(pair[1] != null) {
                  queue.push(pair[1]);
               }
            }
         }
         else if(a instanceof MirrorAssembly mirror) {
            String mirrored = mirror.getAssemblyName();

            if(!Tool.isEmptyString(mirrored)) {
               queue.push(mirrored);
            }
         }
      }

      return required;
   }

   /**
    * Maps a viewsheet folder path to a parallel worksheet folder path.
    * Replaces the viewsheet components prefix with the worksheet components prefix, or
    * falls back to {@link VisualizationService#WORKSHEET_COMPONENTS_FOLDER_PATH}.
    */
   private String resolveWorksheetFolderPath(String vsFolderPath) {
      if(vsFolderPath.startsWith(VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH)) {
         String suffix = vsFolderPath.substring(
            VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH.length());

         return GenerateWsService.WORKSHEET_COMPONENTS_FOLDER_PATH + suffix;
      }

      return GenerateWsService.WORKSHEET_COMPONENTS_FOLDER_PATH;
   }

   private TreeNodeModel buildFolderTree(AssetEntry entry, String label, Principal principal,
                                          AssetEntry.Selector treeSelector)
   {
      List<TreeNodeModel> children = new ArrayList<>();

      try {
         AssetEntry[] entries = assetRepository.getEntries(
            entry, principal, ResourceAction.READ, treeSelector);

         if(entries != null) {
            for(AssetEntry child : entries) {
               if(child.isFolder() || child.isRepositoryFolder()) {
                  children.add(buildFolderTree(child, child.getName(), principal, treeSelector));
               }
               else if(child.isViewsheet()) {
                  // Include saved viewsheets as leaf nodes so the dialog shows existing content
                  String vsLabel = !Tool.isEmptyString(child.getAlias())
                     ? child.getAlias() : child.getName();
                  children.add(TreeNodeModel.builder()
                     .label(vsLabel)
                     .data(child)
                     .leaf(true)
                     .build());
               }
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to load tree for: {}", entry.getPath(), e);
      }

      return TreeNodeModel.builder()
         .label(label)
         .data(entry)
         .leaf(false)
         .children(children)
         .build();
   }

   private void ensureRepositoryFolderExists(AssetEntry folder, Principal principal)
      throws Exception
   {
      try {
         if(!assetRepository.containsEntry(folder)) {
            assetRepository.addFolder(folder, principal);
         }
      }
      catch(Exception e) {
         if(!assetRepository.containsEntry(folder)) {
            throw e;
         }

         LOG.debug("Repository folder creation exception (folder now exists, proceeding): {}",
                   e.getMessage());
      }
   }

   private void ensureWorksheetFolderExists(AssetEntry folder, Principal principal)
      throws Exception
   {
      try {
         if(!assetRepository.containsEntry(folder)) {
            assetRepository.addFolder(folder, principal);
         }
      }
      catch(Exception e) {
         if(!assetRepository.containsEntry(folder)) {
            throw e;
         }

         LOG.debug("Worksheet folder creation exception (folder now exists, proceeding): {}",
                   e.getMessage());
      }
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(WizVisualizationService.class);
}
