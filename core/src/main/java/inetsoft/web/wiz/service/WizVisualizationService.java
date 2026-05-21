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
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.WizUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.wiz.service.VisualizationService;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizVisualizationSaveEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

@Service
public class WizVisualizationService {
   public WizVisualizationService(ViewsheetService viewsheetService,
                                   AssetRepository assetRepository,
                                   AssemblyImageService assemblyImageService,
                                   BinaryTransferService binaryTransferService,
                                   VSExportService vsExportService)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.assemblyImageService = assemblyImageService;
      this.binaryTransferService = binaryTransferService;
      this.vsExportService = vsExportService;
   }

   /**
    * Returns the folder tree rooted at {@link VisualizationService#VISUALIZATION_COMPONENTS_FOLDER_PATH}.
    * Folder nodes and viewsheet leaf nodes are both included. Used by the WIZ Save dialog.
    */
   public TreeNodeModel getVisualizationFolderTree(Principal principal) throws Exception {
      AssetEntry rootEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH, null);

      ensureFolder(rootEntry, principal);

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
    *       {@link GenerateWsService#WORKSHEET_COMPONENTS_FOLDER_PATH}.</li>
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

      if(!targetFolderPath.startsWith(VisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH)) {
         throw new IllegalArgumentException(
            "targetFolderPath must be under the visualization components folder");
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
      // Reset position so the assembly appears at the top-left of the new single-assembly viewsheet.
      cloned.setPixelOffset(new Point(24, 24));
      newVs.addAssembly(cloned);

      // ── Step 6: Ensure target ViewSheet folder exists ─────────────────────────
      AssetEntry targetFolder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         targetFolderPath, null);
      ensureFolder(targetFolder, principal);

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
      // If viewsheet save fails, clean up the already-persisted worksheet to avoid orphans.
      try {
         viewsheetService.setViewsheet(newVs, newVsEntry, principal, true, true);
      }
      catch(Exception e) {
         if(newWsEntry != null) {
            try {
               assetRepository.removeSheet(newWsEntry, principal, true);
            }
            catch(Exception ce) {
               LOG.warn("Failed to clean up orphaned worksheet after viewsheet save failure: {}",
                        newWsEntry.getPath(), ce);
            }
         }

         throw e;
      }

      WizVisualizationSaveResult result = new WizVisualizationSaveResult();
      result.setSavedViewsheetIdentifier(newVsEntry.toIdentifier());

      // Try to generate thumbnail from the source runtime viewsheet (non-fatal)
      String runtimeId = event.getSourceViewsheetRuntimeId();

      if(!Tool.isEmptyString(runtimeId)) {
         try {
            // Validate ownership: getViewsheet enforces that the runtime viewsheet
            // belongs to this principal, preventing cross-session thumbnail access.
            if(viewsheetService.getViewsheet(runtimeId, principal) == null) {
               LOG.warn("runtimeId '{}' not accessible for principal '{}', skipping thumbnail",
                        runtimeId, principal.getName());
            }
            else {
               AssemblyImageService.ImageRenderResult imgResult =
                  assemblyImageService.downloadAssemblyImage(
                     runtimeId, Tool.byteEncode(event.getAssemblyName()),
                     THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                     true, principal);

               if(imgResult != null && imgResult.getRetryAfter() > 0) {
                  LOG.debug("downloadAssemblyImage not yet ready for '{}' (retryAfter={}), skipping thumbnail",
                            event.getAssemblyName(), imgResult.getRetryAfter());
               }
               else if(imgResult != null && imgResult.getImageData() != null) {
                  byte[] imageBytes = binaryTransferService.getData(imgResult.getImageData());

                  if(imageBytes != null && imageBytes.length > 0) {
                     if(imgResult.isPng()) {
                        // 1×1 is a StyleBI placeholder returned for assembly types that
                        // downloadAssemblyImage does not support (e.g. Crosstab, Table).
                        // In that case fall back to a full-viewsheet PNG export + crop.
                        String thumbnailValue = null;
                        try {
                           BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));

                           if(decoded != null && decoded.getWidth() > 1 && decoded.getHeight() > 1) {
                              thumbnailValue = "data:image/png;base64," +
                                 Base64.getEncoder().encodeToString(imageBytes);
                           }
                        }
                        catch(Exception e) {
                           LOG.debug("Failed to decode PNG for '{}': {}. Attempting full-viewsheet fallback.",
                                     event.getAssemblyName(), e.getMessage());
                        }

                        if(thumbnailValue == null) {
                           LOG.debug("PNG was 1×1 or undecodable for '{}', using full-viewsheet fallback",
                                     event.getAssemblyName());
                           thumbnailValue = renderFallbackThumbnail(
                              runtimeId, event.getAssemblyName(), principal);
                        }

                        result.setThumbnail(thumbnailValue);
                     }
                     else {
                        result.setThumbnail(normalizeSvgNamespace(
                           new String(imageBytes, StandardCharsets.UTF_8)));
                     }
                  }
               }
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to generate thumbnail for assembly '{}' (non-fatal): {}",
                     event.getAssemblyName(), e.getMessage());
         }
      }

      return result;
   }

   // ── Private helpers ───────────────────────────────────────────────────────────

   /**
    * Loads the source Worksheet, clones it, removes all tables not required by the
    * given assembly, and persists the trimmed clone under
    * {@link GenerateWsService#WORKSHEET_COMPONENTS_FOLDER_PATH}/{targetSubFolder}/{uuid}.
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
      // Collect removal candidates first to avoid ConcurrentModificationException.
      Worksheet newWs = (Worksheet) sourceWs.clone();
      List<String> toRemove = new ArrayList<>();

      for(Assembly wsAssembly : newWs.getAssemblies()) {
         if(!requiredTables.contains(wsAssembly.getName())) {
            toRemove.add(wsAssembly.getName());
         }
      }

      for(String name : toRemove) {
         newWs.removeAssembly(name);
      }

      // Resolve worksheet target folder — mirrors the viewsheet folder under a parallel root
      String wsFolderPath = resolveWorksheetFolderPath(targetFolderPath);
      AssetEntry wsFolder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, wsFolderPath, null);
      ensureFolder(wsFolder, principal);

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
    * Collects the DFS dependency chain of worksheet table names starting from
    * {@code rootTableName}. Follows join-table sub-tables and mirror-table targets.
    * Returns an empty set when {@code rootTableName} is blank.
    */
   private Set<String> collectRequiredTables(Worksheet ws, String rootTableName) {
      Set<String> required = new LinkedHashSet<>();

      if(Tool.isEmptyString(rootTableName) || ws == null) {
         return required;
      }

      Deque<String> stack = new ArrayDeque<>();
      stack.push(rootTableName);

      while(!stack.isEmpty()) {
         String name = stack.pop();

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
                  stack.push(pair[0]);
               }

               if(pair[1] != null) {
                  stack.push(pair[1]);
               }
            }
         }
         else if(a instanceof MirrorAssembly mirror) {
            String mirrored = mirror.getAssemblyName();

            if(!Tool.isEmptyString(mirrored)) {
               stack.push(mirrored);
            }
         }
      }

      return required;
   }

   /**
    * Maps a viewsheet folder path to a parallel worksheet folder path.
    * Replaces the viewsheet components prefix with the worksheet components prefix, or
    * falls back to {@link GenerateWsService#WORKSHEET_COMPONENTS_FOLDER_PATH}.
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

   private void ensureFolder(AssetEntry folder, Principal principal) throws Exception {
      try {
         if(!assetRepository.containsEntry(folder)) {
            assetRepository.addFolder(folder, principal);
         }
      }
      catch(Exception e) {
         if(!assetRepository.containsEntry(folder)) {
            throw e;
         }

         LOG.debug("Folder creation race (folder now exists, proceeding): {}", e.getMessage());
      }
   }

   /**
    * Exports the source runtime viewsheet as a full PNG and crops to the target assembly bounds.
    * Used as a fallback for assembly types (Crosstab, Table) that downloadAssemblyImage
    * does not support — those return a 1×1 placeholder PNG instead of real content.
    */
   private String renderFallbackThumbnail(String runtimeId, String assemblyName, Principal principal) {
      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

         if(rvs == null) {
            LOG.warn("renderFallbackThumbnail: RuntimeViewsheet not found for id='{}'", runtimeId);
            return null;
         }

         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            LOG.warn("renderFallbackThumbnail: Viewsheet is null for id='{}'", runtimeId);
            return null;
         }

         Assembly assembly = vs.getAssembly(assemblyName);

         if(!(assembly instanceof VSAssembly)) {
            LOG.warn("renderFallbackThumbnail: assembly '{}' not found or not VSAssembly (type={})",
                     assemblyName, assembly == null ? "null" : assembly.getClass().getSimpleName());
            return null;
         }

         Point offset = assembly.getPixelOffset();
         Dimension size = assembly.getPixelSize();
         LOG.debug("renderFallbackThumbnail: assembly='{}' offset={} size={}", assemblyName, offset, size);

         if(size == null || size.width <= 0 || size.height <= 0) {
            LOG.warn("renderFallbackThumbnail: invalid size {} for assembly '{}'", size, assemblyName);
            return null;
         }

         // Export full viewsheet to an in-memory PNG.
         // current=true is required: with current=false and no bookmarks, the exporter's
         // export() method is never called and write() produces 0 bytes.
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         vsExportService.exportViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PNG,
            true, false, true, false, false, null, false,
            new ExportResponse(baos), principal);

         byte[] pngBytes = baos.toByteArray();
         LOG.debug("renderFallbackThumbnail: export produced {} bytes for assembly '{}'",
                   pngBytes.length, assemblyName);

         if(pngBytes.length == 0) {
            LOG.warn("renderFallbackThumbnail: exportViewsheet produced 0 bytes for assembly '{}'", assemblyName);
            return null;
         }

         BufferedImage full = ImageIO.read(new ByteArrayInputStream(pngBytes));

         if(full == null) {
            LOG.warn("renderFallbackThumbnail: ImageIO.read returned null for assembly '{}'", assemblyName);
            return null;
         }

         LOG.debug("renderFallbackThumbnail: full image {}x{}, crop at ({},{}) size {}x{}",
                   full.getWidth(), full.getHeight(),
                   offset != null ? offset.x : 0, offset != null ? offset.y : 0,
                   size.width, size.height);

         // Crop to assembly bounds (PNGCoordinateHelper renders at 1:1 pixel scale)
         int cropX = Math.max(0, offset != null ? offset.x : 0);
         int cropY = Math.max(0, offset != null ? offset.y : 0);
         int cropW = Math.min(size.width, full.getWidth() - cropX);
         int cropH = Math.min(size.height, full.getHeight() - cropY);

         if(cropW <= 1 || cropH <= 1) {
            LOG.warn("renderFallbackThumbnail: crop dimensions too small ({}x{}) for assembly '{}'",
                     cropW, cropH, assemblyName);
            return null;
         }

         BufferedImage cropped = full.getSubimage(cropX, cropY, cropW, cropH);

         if(cropW > THUMBNAIL_WIDTH || cropH > THUMBNAIL_HEIGHT) {
            double scale = Math.min((double) THUMBNAIL_WIDTH / cropW, (double) THUMBNAIL_HEIGHT / cropH);
            int scaledW = Math.max(1, (int)(cropW * scale));
            int scaledH = Math.max(1, (int)(cropH * scale));
            BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
               g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                  RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               g.drawImage(cropped, 0, 0, scaledW, scaledH, null);
            }
            finally {
               g.dispose();
            }
            cropped = scaled;

            LOG.debug("renderFallbackThumbnail: scaled crop from {}x{} to {}x{}", cropW, cropH, scaledW, scaledH);
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ImageIO.write(cropped, "PNG", out);
         byte[] croppedBytes = out.toByteArray();

         if(croppedBytes.length == 0) {
            LOG.warn("renderFallbackThumbnail: re-encoded PNG is empty for assembly '{}'", assemblyName);
            return null;
         }

         LOG.debug("renderFallbackThumbnail: success, cropped PNG {} bytes for assembly '{}'",
                   croppedBytes.length, assemblyName);
         return "data:image/png;base64," + Base64.getEncoder().encodeToString(croppedBytes);
      }
      catch(OutOfMemoryError oom) {
         // Log at ERROR so repeated OOM occurrences are visible as systemic memory pressure,
         // not buried as routine warnings.
         LOG.error("renderFallbackThumbnail: out of memory exporting viewsheet PNG for assembly '{}' " +
                   "— if this recurs, investigate heap usage during concurrent thumbnail generation",
                   assemblyName);
         return null;
      }
      catch(Exception e) {
         LOG.warn("renderFallbackThumbnail: exception for assembly '{}': {}",
                  assemblyName, e.getMessage(), e);
         return null;
      }
   }

   /**
    * Removes the custom XML namespace prefix (e.g. "a0:") that Batik adds to the SVG namespace
    * so the resulting markup can be rendered directly by browsers.
    *
    * Batik generates: {@code <a0:svg xmlns:xlink="..." xmlns:a0="http://www.w3.org/2000/svg">}
    * Browser requires: {@code <svg xmlns="http://www.w3.org/2000/svg">}
    *
    * <p>The method scans all {@code xmlns:PREFIX} declarations until it finds one whose value is
    * exactly {@value #SVG_NS}. This avoids incorrectly treating an earlier {@code xmlns:xlink}
    * declaration as the SVG-namespace prefix (a real Batik output ordering).
    *
    * <p>Note: String.replace is a literal full-text replacement, not XML-aware. An attribute value
    * or text node containing the literal prefix string (e.g. "&lt;a0:") would be incorrectly
    * mutated. This is not expected in normal Batik chart output.
    *
    * <p>Note: only element start/end tags are de-prefixed. Attributes carrying the same namespace
    * prefix (e.g. {@code a0:fill="..."}) are not stripped. Batik does not apply the SVG namespace
    * prefix to attribute names, so this is not an issue in practice.
    */
   private static String normalizeSvgNamespace(String svg) {
      if(svg == null) {
         return null;
      }

      int searchFrom = 0;

      while(true) {
         int nsIdx = svg.indexOf("xmlns:", searchFrom);

         if(nsIdx < 0) {
            return svg; // no xmlns: declaration maps to the SVG namespace
         }

         int eqIdx = svg.indexOf('=', nsIdx + 6);

         if(eqIdx < 0) {
            return svg;
         }

         String prefix = svg.substring(nsIdx + 6, eqIdx).trim();

         if(prefix.isEmpty()) {
            searchFrom = nsIdx + 1;
            continue;
         }

         // Only normalize when this declaration's value is the SVG namespace URI
         String afterEq = svg.substring(eqIdx + 1).stripLeading();

         if(!afterEq.startsWith("\"" + SVG_NS + "\"") && !afterEq.startsWith("'" + SVG_NS + "'")) {
            searchFrom = nsIdx + 1;
            continue;
         }

         String prefixColon = prefix + ":";
         return svg
            .replace("xmlns:" + prefix + "=\"" + SVG_NS + "\"", "xmlns=\"" + SVG_NS + "\"")
            .replace("<" + prefixColon, "<")
            .replace("</" + prefixColon, "</");
      }
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final AssemblyImageService assemblyImageService;
   private final BinaryTransferService binaryTransferService;
   private final VSExportService vsExportService;
   private static final Logger LOG = LoggerFactory.getLogger(WizVisualizationService.class);
   private static final int THUMBNAIL_WIDTH  = 400;
   private static final int THUMBNAIL_HEIGHT = 300;
   private static final String SVG_NS = "http://www.w3.org/2000/svg";
}
