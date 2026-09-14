/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Read/collision-check wrapper over the product's own {@code RecycleBin}/{@code RecycleUtils}
 * primitives -- never a call into {@code RepositoryRecycleBinController} itself
 * (track-a-recycle-bin/03-reconcile.md architecture constraint).
 *
 * <p>{@link #listEntries}/{@link #getEntry} read directly from {@link RecycleBin#getEntries()}
 * (the same bulk-read API {@code RepositoryRecycleBinController.clearRecycleBin} itself already
 * uses to enumerate everything in the bin) rather than re-deriving {@code
 * RepositoryRecycleBinController.addRecycleSheets}'s own {@code AssetRepository}-selector walk --
 * a recycle bin's storage is already a single, flat, per-organization bucket keyed by trash path
 * (see {@code RecycleBin.getStorage}), so no separate global/per-user-scope traversal is needed to
 * enumerate it. The same per-entry {@code ADMIN}-permission filter {@code
 * RepositoryRecycleBinController.getRecycleNodeFromAssetEntries} applies is reproduced in {@link
 * #isVisible}, so visibility here is never broader than the EM console's own recycle bin page --
 * only ever equal or narrower (this service does not additionally pre-filter by {@code READ} the
 * way the controller's own {@code AssetRepository.getEntries} call implicitly does, since a caller
 * who lacks {@code ADMIN} is refused by {@link #isVisible} regardless).
 */
@Component
public class RecycleBinService {
   @Autowired
   public RecycleBinService(RecycleBin recycleBin, AssetRepository assetRepository,
                            SecurityProvider securityProvider)
   {
      this.recycleBin = recycleBin;
      this.assetRepository = assetRepository;
      this.securityProvider = securityProvider;
   }

   /** Every entry in the calling organization's recycle bin the caller holds {@code ADMIN} on. */
   public List<RecycleBinEntryProjection> listEntries(Principal user) {
      List<RecycleBinEntryProjection> result = new ArrayList<>();

      for(RecycleBin.Entry entry : recycleBin.getEntries()) {
         if(isVisible(entry, user)) {
            result.add(project(entry));
         }
      }

      return result;
   }

   /** {@code Optional.empty()} both when the path is absent from the recycle bin AND when it is
    * present but not visible to {@code user} -- a caller must not be able to distinguish "does not
    * exist" from "exists, but you cannot see it" (same posture {@link #listEntries}'s own
    * filtering already implies). */
   public Optional<RecycleBin.Entry> getEntry(String path, Principal user) {
      RecycleBin.Entry entry = recycleBin.getEntry(path);

      if(entry == null || !isVisible(entry, user)) {
         return Optional.empty();
      }

      return Optional.of(entry);
   }

   /**
    * @throws MissingResourceException if {@code path} is absent from the recycle bin, or present
    *         but not visible to {@code user} (same 404-shaped contract {@code
    *         AdminViewsheetController}'s own area declares for a missing/invisible unit).
    */
   public RecycleBin.Entry requireEntry(String path, Principal user) throws MissingResourceException {
      return getEntry(path, user).orElseThrow(() -> new MissingResourceException(
         "path: no recycle bin entry found at \"" + path + "\" -- it may not exist, may already " +
         "have been restored/purged, or you may not have permission to see it"));
   }

   /**
    * Whether restoring {@code entry} to its own {@code originalPath} would collide with something
    * already occupying that destination -- mirrors the exact duplicate check {@code
    * RecycleUtils.restoreSheet}/{@code restoreWSFolder} run internally via {@code
    * AssetUtil.isDuplicatedEntry}, and the {@code registry.isFolder} check {@code
    * RecycleUtils.restoreRepositoryFolder} runs for a repository (dashboard) folder, exposed here
    * so {@link RecycleBinChangePlanService} can classify risk -- and refuse loud -- BEFORE ever
    * calling the real, mutating restore (track-a-recycle-bin/01-design.md section 4 risk 1).
    */
   public boolean wouldCollide(RecycleBin.Entry entry) throws Exception {
      if(entry.isSheet()) {
         String originalPath = entry.getOriginalPath();
         originalPath = SUtil.isMyDashboard(originalPath) ?
            originalPath.substring(Tool.MY_DASHBOARD.length() + 1) : originalPath;
         AssetEntry candidate = new AssetEntry(entry.getOriginalScope(),
            entry.getType() == RepositoryEntry.WORKSHEET ?
               AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET,
            originalPath, entry.getOriginalUser());
         return AssetUtil.isDuplicatedEntry(assetRepository, candidate);
      }

      if(entry.isWSFolder()) {
         AssetEntry candidate = new AssetEntry(entry.getOriginalScope(), AssetEntry.Type.FOLDER,
            entry.getOriginalPath(), entry.getOriginalUser());
         return AssetUtil.isDuplicatedEntry(assetRepository, candidate);
      }

      // Repository (dashboard) folder: RecycleUtils.restoreRepositoryFolder itself checks
      // registry.isFolder(originalPath), not AssetUtil.isDuplicatedEntry -- a repository folder is
      // a RepletRegistry label, not an AssetRepository entry.
      RepletRegistry registry = RecycleUtils.getRegistry(entry.getOriginalPath(),
         entry.getOriginalUser());
      return registry.isFolder(entry.getOriginalPath());
   }

   RecycleBinEntryProjection project(RecycleBin.Entry entry) {
      String scope = entry.getOriginalScope() == AssetRepository.USER_SCOPE ?
         RecycleBinEntryProjection.SCOPE_USER : RecycleBinEntryProjection.SCOPE_GLOBAL;
      String owner = entry.getOriginalUser() == null ? null : entry.getOriginalUser().convertToKey();
      String timestamp = entry.getTimestamp() == null ? null :
         DateTimeFormatter.ISO_INSTANT.format(entry.getTimestamp().toInstant());
      return new RecycleBinEntryProjection(entry.getPath(), entry.getOriginalPath(),
         entry.getName(), typeOf(entry), scope, owner, timestamp);
   }

   /** A stable, enum-shaped type string -- unlike {@code RecycleUtils.getTypeLabel}'s own
    * free-text EM-table label, which collapses both folder kinds to the ambiguous {@code
    * "folder"}/{@code "worksheet folder"} pair with no machine-stable contract. */
   static String typeOf(RecycleBin.Entry entry) {
      if(entry.getType() == RepositoryEntry.VIEWSHEET) {
         return RecycleBinEntryProjection.TYPE_DASHBOARD;
      }

      if(entry.getType() == RepositoryEntry.WORKSHEET) {
         return RecycleBinEntryProjection.TYPE_WORKSHEET;
      }

      if(entry.isWSFolder()) {
         return RecycleBinEntryProjection.TYPE_WORKSHEET_FOLDER;
      }

      return RecycleBinEntryProjection.TYPE_FOLDER;
   }

   /**
    * Reproduces {@code RepositoryRecycleBinController.getRecycleNodeFromAssetEntries}'s own
    * per-entry security check directly off the {@code RecycleBin.Entry} itself, rather than
    * re-deriving a live {@code AssetEntry} for it: {@code ADMIN} on {@code SECURITY_USER} for the
    * entry's original owner when it was a user-owned asset, otherwise {@code ADMIN} on the
    * entry's OWN current (in-trash) path under the resource type its original type implies -- the
    * same resource/path pair the original permission grant was copied onto when the asset was
    * moved into the recycle bin ({@code RecycleUtils.moveSheetToRecycleBin}'s own {@code
    * SecurityEngine.getSecurity().setPermission(otype, newEntry.getPath(), originalPermission)}).
    */
   boolean isVisible(RecycleBin.Entry entry, Principal user) {
      if(entry.getOriginalUser() != null) {
         return securityProvider.checkPermission(
            user, ResourceType.SECURITY_USER, entry.getOriginalUser().convertToKey(),
            ResourceAction.ADMIN);
      }

      ResourceType resourceType = isRepositoryTier(entry) ? ResourceType.REPORT : ResourceType.ASSET;
      return securityProvider.checkPermission(user, resourceType, entry.getPath(),
         ResourceAction.ADMIN);
   }

   private static boolean isRepositoryTier(RecycleBin.Entry entry) {
      return entry.getType() == RepositoryEntry.VIEWSHEET || entry.isFolder() && !entry.isWSFolder();
   }

   private final RecycleBin recycleBin;
   private final AssetRepository assetRepository;
   private final SecurityProvider securityProvider;
}
