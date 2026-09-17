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

   /**
    * Verifies that the recycled asset {@code entry} points at is still resolvable at its own trash
    * path -- the source-side twin of {@link #wouldCollide}, mirroring the lookups {@code
    * RecycleUtils.restoreSheet}/{@code restoreWSFolder} run internally ({@code getSheetEntry}/{@code
    * getAssetEntry}/{@code getFolderAssetEntry}) and throw on, and the {@code RecycleUtils.getRegistry}
    * load {@code restoreRepositoryFolder} opens with, exposed here so {@code
    * RecycleBinChangesetApplyService} can run them BEFORE marking the item mutation-entered
    * (bug #76672 follow-up: those lookups all fire strictly before their own method's first
    * mutating call, so a throw from one leaves nothing changed).
    *
    * <p>Deliberately stops short of {@code RecycleUtils.validatePath}/{@code checkParentFolderExist}
    * -- the next statements in those methods -- because both of those already MUTATE
    * ({@code repository.addFolder}/{@code registry.addFolder}), so they cannot be part of a
    * pre-mutation gate.
    *
    * @throws MissingResourceException if the recycled asset is no longer resolvable (e.g. it was
    *         purged concurrently between preview and apply), matching the 404-shaped contract
    *         {@link #requireEntry} already declares.
    */
   public void requireRestorableSource(RecycleBin.Entry entry, Principal user) throws Exception {
      if(entry.isSheet()) {
         // Mirrors RecycleUtils.restoreSheet's own trash-path derivation and getSheetEntry lookup.
         String path = entry.getOriginalScope() == AssetRepository.USER_SCOPE ?
            Tool.MY_DASHBOARD + "/" + entry.getPath() : entry.getPath();
         boolean viewsheet = entry.getType() != RepositoryEntry.WORKSHEET;
         AssetEntry.Type type = viewsheet ? AssetEntry.Type.VIEWSHEET : AssetEntry.Type.WORKSHEET;
         AssetEntry source = lookupSheetSource(type, path, entry.getOriginalUser());

         if(source == null && viewsheet) {
            // getSheetEntry's own snapshot fallback, which is always global-scoped there even for a
            // My Dashboards path.
            source = lookup(AssetEntry.Type.VIEWSHEET_SNAPSHOT, path, null, false);
         }

         if(source != null) {
            // restoreSheet immediately re-reads the entry a second time, now under the type the
            // first lookup resolved, and throws again on null (RecycleUtils lines 257-267). That
            // second miss is just as pre-mutation as the first -- most visibly for a My Dashboards
            // dashboard that only resolved through the global snapshot fallback above, whose
            // user-scoped snapshot re-read comes back null.
            source = lookupSheetSource(source.getType(), path, entry.getOriginalUser());
         }

         if(source == null) {
            throw new MissingResourceException(
               "path: the recycled asset at \"" + path + "\" owned by " + entry.getOriginalUser() +
               " could not be found -- it may already have been purged since preview");
         }

         return;
      }

      if(entry.isWSFolder()) {
         // Mirrors RecycleUtils.restoreWSFolder's two-step resolution: getAssetEntry(FOLDER, path)
         // followed by the getFolderAssetEntry scan of the parent folder. Both dereference their
         // result unchecked there (the latent NPEs at RecycleUtils lines 314/320), so both are
         // pre-mutation failure points this gate has to cover.
         String path = entry.getPath();
         AssetEntry source = lookupWSFolderSource(path, entry.getOriginalUser());

         if(source != null) {
            source = findChildFolder(source, user);
         }

         if(source == null) {
            throw new MissingResourceException(
               "path: the recycled worksheet folder at \"" + path + "\" could not be found -- it " +
               "may already have been purged since preview");
         }

         return;
      }

      // Repository (dashboard) folder: restoreRepositoryFolder opens with this same registry load,
      // the only thing it does before checkParentFolderExist starts mutating -- the same gate
      // applyPurge's own repository-folder branch already uses. Today wouldCollide happens to run
      // the identical load a few lines earlier on the restore path, so this is belt-and-braces
      // there; it is kept so this method's own contract holds for all three entry kinds rather
      // than silently no-opping for one of them.
      RecycleUtils.getRegistry(entry.getOriginalPath(), entry.getOriginalUser());
   }

   /**
    * Mirrors {@code RecycleUtils.getSheetEntry}, which picks the user-scoped lookup off the PATH
    * ({@code SUtil.isMyDashboard}) -- restoreSheet prepends the My Dashboards prefix itself when
    * the entry was user-scoped, so the path alone carries that decision.
    */
   private AssetEntry lookupSheetSource(AssetEntry.Type type, String path, IdentityID owner)
      throws Exception
   {
      return lookup(type, path, owner, path != null && SUtil.isMyDashboard(path));
   }

   /**
    * Mirrors {@code RecycleUtils.restoreWSFolder}, which -- unlike the sheet path -- picks the
    * user-scoped lookup off the OWNER alone ({@code getOriginalUser() != null}). A recycled
    * worksheet folder's trash path is a bare {@code "Recycle Bin/<uuid>"} with no My Dashboards
    * prefix (see {@code moveAssetFolderToRecycleBin}), so keying off the path here would refuse
    * every user-scoped folder restore.
    */
   private AssetEntry lookupWSFolderSource(String path, IdentityID owner) throws Exception {
      return lookup(AssetEntry.Type.FOLDER, path, owner, owner != null);
   }

   /** Mirrors {@code RecycleUtils.getAssetEntry}'s two overloads: the user-scoped one strips a My
    * Dashboards prefix when the path carries one, the global one takes the path as-is. */
   private AssetEntry lookup(AssetEntry.Type type, String path, IdentityID owner, boolean userScope)
      throws Exception
   {
      if(path == null) {
         return null;
      }

      AssetEntry candidate;

      if(userScope) {
         candidate = new AssetEntry(AssetRepository.USER_SCOPE, type,
            SUtil.isMyDashboard(path) ? path.substring(Tool.MY_DASHBOARD.length() + 1) : path,
            owner);
      }
      else {
         candidate = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null);
      }

      return assetRepository.getAssetEntry(candidate);
   }

   /** Mirrors {@code RecycleUtils.getFolderAssetEntry}: the folder as listed by its own parent. */
   private AssetEntry findChildFolder(AssetEntry folder, Principal user) throws Exception {
      AssetEntry parent = folder.getUser() == null ?
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                        folder.getParentPath(), null) :
         new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, folder.getParentPath(),
                        folder.getUser());
      AssetEntry[] entries = assetRepository.getEntries(parent, user, ResourceAction.READ,
         new AssetEntry.Selector(AssetEntry.Type.FOLDER));

      for(AssetEntry child : entries) {
         if(child.toIdentifier().equals(folder.toIdentifier())) {
            return child;
         }
      }

      return null;
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
