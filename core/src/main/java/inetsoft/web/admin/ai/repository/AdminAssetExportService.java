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

import inetsoft.web.admin.content.repository.ExportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.deploy.DeployService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves an asset selection, permission-filters it, computes dependents, and creates the
 * export zip -- all synchronously, all delegating to the EXISTING {@code DeployService}/{@code
 * ExportAssetServiceProxy} (no change to either). {@code
 * inetsoft.web.admin.content.repository.ExportAssetController}'s own {@code checkAssetPermission}/
 * {@code getDependentAssets} are deliberately NOT called -- both are {@code HttpSession}-bound
 * (wrapping the SAME {@code DeployService} methods this class calls directly), unusable by
 * wizClient's fully stateless, bearer-token-only requests (01-design.md section 1).
 */
@Component
public class AdminAssetExportService {
   @Autowired
   public AdminAssetExportService(RepositoryAssetSelectionResolver selectionResolver,
                                  DeployService deployService,
                                  ExportAssetServiceProxy exportAssetServiceProxy)
   {
      this.selectionResolver = selectionResolver;
      this.deployService = deployService;
      this.exportAssetServiceProxy = exportAssetServiceProxy;
   }

   public RepositoryExportResult export(RepositoryExportRequest req, Principal principal)
      throws Exception
   {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      List<String> viewsheetAssetIds = req.getViewsheetAssetIds() == null
         ? Collections.emptyList() : req.getViewsheetAssetIds();
      List<String> folderPaths = req.getFolderPaths() == null
         ? Collections.emptyList() : req.getFolderPaths();

      if(viewsheetAssetIds.isEmpty() && folderPaths.isEmpty()) {
         throw new IllegalArgumentException(
            "viewsheetAssetIds/folderPaths: at least one of these is required");
      }

      RepositoryAssetSelectionResolver.Resolution resolution =
         selectionResolver.resolve(viewsheetAssetIds, folderPaths, principal);

      if(resolution.resolved().isEmpty()) {
         throw new IllegalArgumentException(
            "viewsheetAssetIds/folderPaths: none of the given entries resolved to an existing " +
            "viewsheet or worksheet (" + String.join(", ", resolution.unresolvedEntities()) + ")");
      }

      SelectedAssetModelList requested = SelectedAssetModelList.builder()
         .selectedAssets(resolution.resolved())
         .build();

      SelectedAssetModelList permitted = deployService.filterEntities(requested, principal);
      List<String> skippedForPermission = diffPaths(requested.selectedAssets(),
         permitted.selectedAssets());

      RequiredAssetModelList dependents =
         deployService.getDependentAssetsList(permitted.selectedAssets(), principal);
      List<RequiredAssetModel> dependentAssets = applyDependencyMode(req, dependents);

      String name = req.getName() != null && !req.getName().trim().isEmpty()
         ? req.getName().trim() : "wiz-export-" + System.currentTimeMillis();

      ExportedAssetsModel exportModel = ExportedAssetsModel.builder()
         .name(name)
         .overwriting(false)
         .selectedEntities(permitted.selectedAssets())
         .dependentAssets(dependentAssets)
         .build();

      String exportId = UUID.randomUUID().toString();
      exportAssetServiceProxy.createExport(exportId, name, exportModel, principal);

      return new RepositoryExportResult(exportId, name + ".zip", permitted.selectedAssets().size(),
         resolution.unresolvedEntities(), skippedForPermission,
         dependentAssets.stream().map(RequiredAssetModel::name).collect(Collectors.toList()));
   }

   private static List<RequiredAssetModel> applyDependencyMode(RepositoryExportRequest req,
                                                                RequiredAssetModelList dependents)
   {
      String mode = req.getIncludeDependenciesMode() == null ? "all"
         : req.getIncludeDependenciesMode().trim().toLowerCase(Locale.ROOT);

      if("none".equals(mode)) {
         return Collections.emptyList();
      }

      if("list".equals(mode)) {
         Set<String> wanted = new HashSet<>(
            req.getIncludeDependencyNames() == null ? Collections.emptyList()
               : req.getIncludeDependencyNames());
         return dependents.requiredAssets().stream()
            .filter(a -> wanted.contains(a.name()))
            .collect(Collectors.toList());
      }

      return dependents.requiredAssets();
   }

   private static List<String> diffPaths(List<SelectedAssetModel> requested,
                                         List<SelectedAssetModel> permitted)
   {
      Set<String> permittedKeys = permitted.stream()
         .map(m -> m.type() + "|" + m.path())
         .collect(Collectors.toSet());
      return requested.stream()
         .filter(m -> !permittedKeys.contains(m.type() + "|" + m.path()))
         .map(SelectedAssetModel::path)
         .collect(Collectors.toList());
   }

   private final RepositoryAssetSelectionResolver selectionResolver;
   private final DeployService deployService;
   private final ExportAssetServiceProxy exportAssetServiceProxy;
}
