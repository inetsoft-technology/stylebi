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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * Turns caller-supplied {@code viewsheetAssetIds}/{@code folderPaths} into a
 * {@link SelectedAssetModel} list for {@code DeployService.filterEntities}/
 * {@code getDependentAssetsList}, expanding a folder into every viewsheet/worksheet stored
 * under it (recursively), scoped to viewsheet/worksheet only (03-reconcile.md, track-d).
 *
 * <p>No existing class does this expansion: {@code DeployService.getEntryAssets} has no branch
 * for a bare folder {@code RepositoryEntry} type at all -- {@code repositoryEntryTypeToAssetType}
 * returns null for it, silently dropped by {@code getEntryAssets} -- confirming the real EM UI
 * itself expands a folder selection into individual viewsheet/worksheet entries client-side
 * before ever calling export/create. This resolver replicates that expansion server-side, using
 * only community-tier primitives ({@link AssetRepository#getAllEntries}) -- {@code
 * ViewsheetApiService}/{@code AdminViewsheetController}'s own folder-listing wrapper lives in
 * {@code enterprise}, which {@code community/core} cannot depend on (verified: {@code
 * community/core}'s own {@code pom.xml} carries no dependency on {@code enterprise}), so reusing
 * it as 01-design.md's primary text suggested is not actually possible -- only the design's own
 * parenthetical fallback ("a package-local re-implementation over ContentRepositoryTreeService")
 * is buildable, and {@link AssetRepository#getAllEntries} (not {@code ContentRepositoryTreeService}
 * itself, which has no folder-content listing method) turned out to be the exact already-public,
 * community-tier, recursive primitive needed -- the same one {@code
 * ViewsheetChangePlanService.findFolderContents} (enterprise) already calls for its own,
 * differently-scoped folder-delete preflight.
 */
@Component
public class RepositoryAssetSelectionResolver {
   /** Result of resolving a caller's asset selection: entities usable by {@code DeployService},
    * plus every {@code viewsheetAssetIds}/{@code folderPaths} entry that did NOT resolve to a
    * real, existing viewsheet/worksheet -- named here, never silently dropped (repo CLAUDE.md's
    * "tool-misuse is a plugin gap" rule). */
   public record Resolution(List<SelectedAssetModel> resolved, List<String> unresolvedEntities) {
   }

   public Resolution resolve(List<String> viewsheetAssetIds, List<String> folderPaths,
                             Principal principal)
      throws Exception
   {
      List<SelectedAssetModel> resolved = new ArrayList<>();
      List<String> unresolved = new ArrayList<>();
      AssetRepository repository = AssetUtil.getAssetRepository(false);

      if(viewsheetAssetIds != null) {
         for(String assetId : viewsheetAssetIds) {
            SelectedAssetModel model = resolveAssetId(assetId, repository);

            if(model == null) {
               unresolved.add(assetId);
            }
            else {
               resolved.add(model);
            }
         }
      }

      if(folderPaths != null) {
         for(String folderPath : folderPaths) {
            List<SelectedAssetModel> folderEntries = resolveFolder(folderPath, repository, principal);

            if(folderEntries == null) {
               unresolved.add(folderPath);
            }
            else {
               resolved.addAll(folderEntries);
            }
         }
      }

      return new Resolution(dedupe(resolved), unresolved);
   }

   private SelectedAssetModel resolveAssetId(String assetId, AssetRepository repository) {
      AssetEntry entry;

      try {
         entry = AssetEntry.createAssetEntry(assetId);
      }
      catch(Exception e) {
         return null;
      }

      if(entry == null || !(entry.getType() == AssetEntry.Type.VIEWSHEET ||
         entry.getType() == AssetEntry.Type.WORKSHEET))
      {
         return null;
      }

      AssetEntry found;

      try {
         found = repository.getAssetEntry(entry);
      }
      catch(Exception e) {
         return null;
      }

      if(found == null) {
         return null;
      }

      return toSelectedAssetModel(entry);
   }

   /** Returns {@code null} (not an empty list) when the folder path itself does not resolve, so
    * the caller can tell "folder not found" apart from "folder found but empty". */
   private List<SelectedAssetModel> resolveFolder(String folderPath, AssetRepository repository,
                                                   Principal principal)
      throws Exception
   {
      String path = folderPath == null ? "" : folderPath.trim();

      if(path.startsWith("/")) {
         path = path.substring(1);
      }

      AssetEntry root = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.REPOSITORY_FOLDER, path, null);

      AssetEntry[] entries;

      try {
         entries = repository.getAllEntries(root, principal, ResourceAction.READ,
            new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET, AssetEntry.Type.WORKSHEET));
      }
      catch(Exception e) {
         return null;
      }

      if(entries == null) {
         return null;
      }

      List<SelectedAssetModel> result = new ArrayList<>();

      for(AssetEntry entry : entries) {
         result.add(toSelectedAssetModel(entry));
      }

      return result;
   }

   /** Mirrors {@code DeployService.getUserEntryPath} (private there, replicated here per this
    * codebase's own "replicated rather than shared" precedent): a user-owned viewsheet/worksheet
    * carries a {@code "My Dashboards/"} (worksheet: {@code "My Dashboards/Worksheets/"}) prefix
    * in {@code SelectedAssetModel.path()}, which {@code SUtil.getUnscopedPath} (verified by
    * reading it directly) strips back off on the way into {@code DeployService.filterEntities}/
    * {@code getEntryAssets} -- a global asset's path carries no such prefix. */
   private SelectedAssetModel toSelectedAssetModel(AssetEntry entry) {
      boolean isWorksheet = entry.getType() == AssetEntry.Type.WORKSHEET;
      int repositoryType = isWorksheet ? RepositoryEntry.WORKSHEET : RepositoryEntry.VIEWSHEET;
      IdentityID user = entry.getUser();
      String path = entry.getPath();

      if(user != null) {
         path = isWorksheet ? Tool.MY_DASHBOARD + "/" + RepositoryEntry.WORKSHEETS_FOLDER + "/" + path
            : Tool.MY_DASHBOARD + "/" + path;
      }

      // typeName()/typeLabel() are placeholders: DeployService.filterEntities unconditionally
      // recomputes both for every permitted entity (confirmed by reading it directly), so this
      // resolver's own values are never actually read downstream -- only non-null, as the
      // interface requires.
      return SelectedAssetModel.builder()
         .path(path)
         .type(repositoryType)
         .typeName("")
         .typeLabel("")
         .user(user)
         .build();
   }

   private static List<SelectedAssetModel> dedupe(List<SelectedAssetModel> models) {
      Map<String, SelectedAssetModel> byKey = new LinkedHashMap<>();

      for(SelectedAssetModel model : models) {
         String key = model.type() + "|" + model.path() + "|" +
            (model.user() == null ? "" : model.user().convertToKey());
         byKey.putIfAbsent(key, model);
      }

      return new ArrayList<>(byKey.values());
   }
}
