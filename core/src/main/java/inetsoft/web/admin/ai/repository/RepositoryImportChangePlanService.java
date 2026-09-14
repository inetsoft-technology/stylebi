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
package inetsoft.web.admin.ai.repository;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves a staged import against a (possibly retargeted) destination into a {@link
 * RepositoryImportPlan}, computing a real before/after diff -- which target assets already exist
 * (will be overwritten) vs. are new -- and hashing it, the repository-import analog of {@code
 * inetsoft.web.admin.ai.AdminChangePlanService} (03-reconcile.md: adopts the full planHash/
 * taskToken discipline over a leaner acknowledgement-only alternative).
 *
 * <p><b>Disclosed limitation</b>: {@code willOverwrite}/{@code willCreate} are computed against
 * each entity's ORIGINAL recorded path ({@code SelectedAssetModel.path()}), not a retargeted
 * destination -- {@code DeployService.getJarFileInfo}'s own retargeting computation ({@code
 * DeployManagerService.getChangeRootFolderAsset}) is a private implementation detail not exposed
 * as a reusable method, so this preview cannot model "will overwrite AT THE NEW target" when
 * {@code targetFolderPath} is supplied. Only {@code VIEWSHEET}/{@code WORKSHEET} entries are
 * classified at all (this track's own scope, 03-reconcile.md) -- any other asset type present in
 * an uploaded jar (e.g. a data source or script from a jar built outside this plugin) is left out
 * of both lists.
 */
@Component
public class RepositoryImportChangePlanService {
   @Autowired
   public RepositoryImportChangePlanService(ImportAssetServiceProxy importService,
                                            AssetRepository assetRepository)
   {
      this.importService = importService;
      this.assetRepository = assetRepository;
   }

   public RepositoryImportPlan resolve(RepositoryImportRequest req, Principal principal)
      throws Exception
   {
      String task = requireNonBlank("task", req.getTask());
      String stagingToken = requireNonBlank("stagingToken", req.getStagingToken());
      String targetFolderPath = blankToNull(req.getTargetFolderPath());
      String targetOwner = blankToNull(req.getTargetOwner());
      Integer locationType = null;
      ExportedAssetsModel info;

      if(targetFolderPath != null) {
         ExportedAssetsModel base = importService.getJarFileInfo(stagingToken, principal);

         if(base == null) {
            throw new IllegalArgumentException(
               "stagingToken: no staged import found for this token -- it may have expired or " +
               "already been applied/discarded; call stage_repository_import again");
         }

         locationType = computeLocationType(base.selectedEntities());
         info = importService.updateImportInfo(
            stagingToken, targetFolderPath, locationType, targetOwner, principal);
      }
      else {
         info = importService.getJarFileInfo(stagingToken, principal);
      }

      if(info == null) {
         throw new IllegalArgumentException(
            "stagingToken: no staged import found for this token -- it may have expired or " +
            "already been applied/discarded; call stage_repository_import again");
      }

      List<String> ignoreAssetNames = req.getIgnoreAssetNames() == null
         ? Collections.emptyList() : req.getIgnoreAssetNames();
      List<String> ignoreList = ignoreListIndices(info.dependentAssets(), ignoreAssetNames);

      List<BookmarkConflict> bookmarkConflicts = importService.getBookmarkConflicts(
         stagingToken, targetFolderPath, locationType, targetOwner, true, ignoreList, principal);

      List<AssetExistenceEntry> willOverwrite = new ArrayList<>();
      List<AssetExistenceEntry> willCreate = new ArrayList<>();
      List<SelectedAssetModel> selectedEntities = info.selectedEntities() == null
         ? Collections.emptyList() : info.selectedEntities();

      for(SelectedAssetModel model : selectedEntities) {
         if(model.type() != RepositoryEntry.VIEWSHEET && model.type() != RepositoryEntry.WORKSHEET) {
            continue;
         }

         AssetExistenceEntry entry = new AssetExistenceEntry(model.path(), model.label(), null);

         if(alreadyExists(model)) {
            willOverwrite.add(entry);
         }
         else {
            willCreate.add(entry);
         }
      }

      boolean acknowledgeOverwriteRequired = !willOverwrite.isEmpty();
      String planHash = hash(stagingToken, targetFolderPath, targetOwner, ignoreAssetNames,
         req.getBookmarkResolutions(), willOverwrite, willCreate);
      String taskToken = TaskAuditToken.issue(planHash, task);

      return new RepositoryImportPlan(stagingToken, info, willOverwrite, willCreate,
         bookmarkConflicts == null ? Collections.emptyList() : bookmarkConflicts,
         acknowledgeOverwriteRequired, true, true, planHash, taskToken);
   }

   private boolean alreadyExists(SelectedAssetModel model) {
      String unscoped = SUtil.getUnscopedPath(model.path());
      IdentityID user = model.user();
      boolean isUserScope = user != null && !"__NULL__".equals(user.name);
      int scope = isUserScope ? AssetRepository.USER_SCOPE : AssetRepository.GLOBAL_SCOPE;
      AssetEntry.Type type = model.type() == RepositoryEntry.WORKSHEET
         ? AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET;
      AssetEntry entry = new AssetEntry(scope, type, unscoped, isUserScope ? user : null);

      try {
         return assetRepository.getAssetEntry(entry) != null;
      }
      catch(Exception e) {
         return false;
      }
   }

   private static int computeLocationType(List<SelectedAssetModel> entities) {
      boolean allWorksheets = entities != null && !entities.isEmpty() &&
         entities.stream().allMatch(e -> e.type() == RepositoryEntry.WORKSHEET);
      return allWorksheets ? RepositoryEntry.WORKSHEET_FOLDER : RepositoryEntry.FOLDER;
   }

   /** {@code ignoreAssetNames} names dependent assets by {@code RequiredAssetModel.name()};
    * {@code DeployService}'s own ignore list is index-based ({@code RequiredAssetModel.index()}
    * into the jar's dependent-asset array, matching the EM Angular UI's own convention read
    * directly from {@code import-asset-dialog.component.ts}). Package-visible for reuse by
    * {@link RepositoryImportApplyService}, which re-runs this same mapping at apply time. */
   static List<String> ignoreListIndices(List<RequiredAssetModel> dependentAssets,
                                         List<String> ignoreAssetNames)
   {
      if(dependentAssets == null || ignoreAssetNames == null || ignoreAssetNames.isEmpty()) {
         return Collections.emptyList();
      }

      Set<String> wanted = new HashSet<>(ignoreAssetNames);
      return dependentAssets.stream()
         .filter(a -> wanted.contains(a.name()))
         .map(a -> Integer.toString(a.index()))
         .collect(Collectors.toList());
   }

   private static String requireNonBlank(String field, String value) {
      String trimmed = blankToNull(value);

      if(trimmed == null) {
         throw new IllegalArgumentException(field + ": required");
      }

      return trimmed;
   }

   private static String blankToNull(String value) {
      if(value == null) {
         return null;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
   }

   /** SHA-256 over the canonical plan, same field-order/control-character contract every prior
    * area's own {@code hash} method uses. Deliberately excludes {@code task} (free-text,
    * audit-only label bound separately via {@link TaskAuditToken}). */
   private static String hash(String stagingToken, String targetFolderPath, String targetOwner,
                              List<String> ignoreAssetNames,
                              List<RepositoryImportRequest.BookmarkResolutionEntry> bookmarkResolutions,
                              List<AssetExistenceEntry> willOverwrite,
                              List<AssetExistenceEntry> willCreate)
   {
      StringBuilder canonical = new StringBuilder();
      canonical.append(marker(stagingToken)).append(SEP)
         .append(marker(targetFolderPath)).append(SEP)
         .append(marker(targetOwner)).append(SEP);

      List<String> sortedIgnore = ignoreAssetNames == null
         ? Collections.emptyList() : new ArrayList<>(ignoreAssetNames);
      Collections.sort(sortedIgnore);

      for(String name : sortedIgnore) {
         canonical.append("ignore:").append(name).append(SEP);
      }

      List<RepositoryImportRequest.BookmarkResolutionEntry> resolutions = bookmarkResolutions == null
         ? Collections.emptyList() : bookmarkResolutions;
      List<String> resolutionKeys = resolutions.stream()
         .map(r -> marker(r.getViewsheetPath()) + "|" + marker(r.getUser()) + "|" +
            marker(r.getBookmarkName()) + "|" + r.isKeepImported())
         .sorted()
         .collect(Collectors.toList());

      for(String key : resolutionKeys) {
         canonical.append("bookmark:").append(key).append(SEP);
      }

      for(AssetExistenceEntry entry : willOverwrite) {
         canonical.append("overwrite:").append(entry.path()).append(SEP);
      }

      for(AssetExistenceEntry entry : willCreate) {
         canonical.append("create:").append(entry.path()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a repository import plan", e);
      }
   }

   private static String marker(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final ImportAssetServiceProxy importService;
   private final AssetRepository assetRepository;
}
