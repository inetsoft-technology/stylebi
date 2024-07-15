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
package inetsoft.web.portal.data;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.util.MissingAssetClassNameException;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@RestController
public class DataSetController {
   @Autowired
   public DataSetController(DataSetService dataSetService) {
      this.dataSetService = dataSetService;
   }

   @GetMapping("api/portal/data/browser/**")
   public PortalDataBrowserModel getDataBrowser(@RemainingPath String folderPath,
                                                @RequestParam(value = "scope") Optional<Integer> scope,
                                                @RequestParam(value = "home", required = false) boolean home,
                                                @RequestParam(value = "moveFolders", required = false) String moveFolders,
                                                Principal principal)
      throws Exception
   {
      String[] movingFolders = null;

      if(moveFolders != null) {
         movingFolders = moveFolders.split(";");
      }

      String path = folderPath;
      int scopeValue = scope.orElse(AssetRepository.GLOBAL_SCOPE);

      if(scopeValue == AssetRepository.USER_SCOPE) {
         if("User Worksheet".equals(folderPath)) {
            path = "/";
         }
         else if(folderPath.startsWith("User Worksheet/")) {
            path = folderPath.substring("User Worksheet/".length());
         }
      }

      return dataSetService.getDataBrowser(path, scopeValue, movingFolders, home, principal);
   }

   @PostMapping("api/data/search/datasets/assetNames")
   public SearchDataResultsModel getSearchAssetNames(@RequestBody SearchDataCommand command,
                                                     Principal principal)
      throws Exception
   {
      WorksheetBrowserInfo[] searchResults =
         dataSetService.getSearchAssets(
            command.getPath(), command.getQuery(), command.getScope(), principal);

      if("/".equals(command.getPath()) && AssetRepository.GLOBAL_SCOPE == command.getScope()) {
         WorksheetBrowserInfo[] searchResults2 =
                 dataSetService.getSearchAssets(
                         command.getPath(), command.getQuery(), AssetRepository.USER_SCOPE, principal);

         searchResults = ArrayUtils.addAll(searchResults, searchResults2);
      }

      WorksheetBrowserInfo[] sortResults =
         dataSetService.getSortSearchResults(searchResults, command.getQuery());
      String[] searchResultsList = Arrays.stream(sortResults).map(WorksheetBrowserInfo::name)
         .toArray(String[]::new);
      SearchDataResultsModel model = new SearchDataResultsModel();
      model.setAssetNames(searchResultsList);

      return model;
   }

   @PostMapping("api/data/search/datasets")
   public SearchDataResultsModel getSearchAssets(@RequestBody SearchDataCommand command,
                                                 Principal principal)
      throws Exception
   {
      SearchDataResultsModel model = new SearchDataResultsModel();
      WorksheetBrowserInfo[] searchResults =
              dataSetService.getSearchAssets(
                      command.getPath(), command.getQuery(), command.getScope(), principal);

      if("/".equals(command.getPath()) && AssetRepository.GLOBAL_SCOPE == command.getScope()) {
         if(dataSetService.matchPrivateWorksheetFolder(command.getQuery(), principal)) {
            searchResults = ArrayUtils.add(searchResults, dataSetService.getUserWorksheetFolder(principal));
         }

         WorksheetBrowserInfo[] searchResults2 =
                 dataSetService.getSearchAssets(
                         command.getPath(), command.getQuery(), AssetRepository.USER_SCOPE, principal);

         searchResults = ArrayUtils.addAll(searchResults, searchResults2);
      }

      model.setAssets(dataSetService.getSortSearchResults(searchResults, command.getQuery()));

      return model;
   }

   @PostMapping("api/data/datasets/isDuplicate")
   public CheckDuplicateResponse duplicateAssetExists(
      @RequestBody CheckDuplicateRequest request,
      Principal principal)
      throws Exception
   {
      return dataSetService.isEntryDuplicated(request.path(), request.type(), request.newName(), request.scope(), principal);
   }

   /**
    * Check if any item paths are already present.
    */
   @RequestMapping(
      value = "/api/data/move/checkDuplicate",
      method = RequestMethod.POST)
   @ResponseBody
   public boolean checkItemsDuplicate(@RequestBody CheckItemsDuplicateRequest request,
                                      @RequestParam Optional<Integer> assetScope,
                                      @RequestParam Optional<Integer> targetScope,
                                      Principal principal)
      throws Exception
   {
      return dataSetService.checkItemsDuplicate(
         request.items(), request.path(),
         targetScope.orElse(AssetRepository.GLOBAL_SCOPE) ,principal);
   }

   @PostMapping("api/data/datasets/rename/**")
   public void renameWorksheet(@RequestBody WorksheetBrowserInfo info,
                               @RemainingPath String newName,
                               @RequestParam(value = "scope") Optional<Integer> scope,
                               Principal principal)
      throws Exception
   {
      int index = info.path().lastIndexOf("/");
      String newPath = index > 0 ? info.path().substring(0, index + 1) + newName : newName;
      String actionMessage = "Target Entry: " +
         dataSetService.getAuditPath(newPath, scope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
      dataSetService.renameWorksheet(info, newName, scope.orElse(AssetRepository.GLOBAL_SCOPE),
                                     principal, actionMessage);
   }

   @PostMapping("api/data/folders/rename/**")
   public void renameFolder(@RequestBody WorksheetBrowserInfo info,
                            @RemainingPath String newName,
                            Principal principal)
   {
      dataSetService.renameFolder(dataSetService.getAuditPath(info.path(), info.scope(), principal),
         info, newName, info.scope(), principal);
   }

   @PostMapping("/api/data/folders")
   public void addFolder(@RequestBody AddFolderRequest request, Principal principal)
      throws Exception
   {
      String path;
      String parentPath = request.parentPath();

      if(parentPath == null || "".equals(parentPath) || "/".equals(parentPath)) {
         path = request.name();
      }
      else {
         path = parentPath + "/" + request.name();
      }

      dataSetService.addFolder(dataSetService.getAuditPath(path, request.scope(), principal), path,
         request.scope(), principal);
   }

   /**
    * Gets the specified folder.
    *
    * @return the folder.
    *
    * @throws Exception if the folder could not be obtained.
    */
   @RequestMapping(
      value = "/api/data/folders/folder/**",
      method = RequestMethod.GET)
   @ResponseBody
   public WorksheetBrowserInfo getFolder(@RemainingPath String name,
                                         @RequestParam(value = "scope") Optional<Integer> scope,
                                         Principal principal)
      throws Exception
   {
      return dataSetService.getFolder(name, scope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
   }

   /**
    * Moves a folder to a different parent folder.
    *
    * @param command the move command.
    */
   @RequestMapping(
      value = "/api/data/folders/move",
      method = RequestMethod.POST)
   @ResponseBody
   public void moveFolder(@RequestBody MoveRequest command,
                          @RequestParam Optional<Integer> assetScope,
                          @RequestParam Optional<Integer> targetScope,
                          Principal principal)
      throws Exception
   {
      int oldScope = assetScope.orElse(AssetRepository.GLOBAL_SCOPE);
      int newScope = targetScope.orElse(AssetRepository.GLOBAL_SCOPE);
      dataSetService.moveFolder(
         dataSetService.getAuditPath(command.getOldPath(), oldScope, principal),
         command.getOldPath(), command.getId(), command.getPath(),
         oldScope, newScope, principal,
         "Target Entry: " + dataSetService.getAuditPath(
            command.getPath(), newScope, principal));
   }

   /**
    * Moves multiple dataset folders to a different parent folder.
    *
    * @param items the old folder data.
    *
    * @throws Exception if the folder could not be moved.
    */
   @RequestMapping(
      value = "/api/data/folders/moveFolders",
      method = RequestMethod.POST)
   @ResponseBody
   public MessageCommand moveFolders(@RequestBody MoveCommand[] items,
                           @RequestParam(value = "assetScope") Optional<Integer> assetScope,
                           @RequestParam(value = "targetScope") Optional<Integer> targetScope,
                           Principal principal)
      throws Exception
   {
      MessageCommand messageCommand = null;

      try {
         dataSetService.moveFolders(items, assetScope.orElse(AssetRepository.GLOBAL_SCOPE),
            targetScope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
      }
      catch(Exception ex) {
         if(ex instanceof MessageException) {
            MessageException messageException = (MessageException) ex;

            messageCommand = new MessageCommand();
            messageCommand.setMessage(messageException.getMessage());
            messageCommand.setType(MessageCommand.Type
               .fromCode(messageException.getWarningLevel()));
         }
         else {
            throw ex;
         }
      }

      return messageCommand;
   }

   // TODO check if asset dependency checks from composer can be reused
   @GetMapping("/api/data/folders/removeableStatus/**")
   public AssetDependenciesResponse checkFolderRemoveable(@RemainingPath String path,
                                                          @RequestParam(value = "scope") Optional<Integer> scope,
                                                          Principal principal)
      throws Exception
   {
      return dataSetService.isFolderRemoveable(path, scope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
   }

   @PostMapping("/api/data/removeableStatuses")
   public CheckRemovablesResponse checkRemoveable(@RequestBody CheckRemovablesRequest request,
                                                  Principal principal) throws Exception
   {
      CheckRemovablesResponse.Builder builder = CheckRemovablesResponse.builder();

      for(CheckRemovablesItem item : request.datasets()) {
         AssetDependenciesResponse response =
            dataSetService.isWorksheetRemoveable(item.path(), item.scope(), principal);
         builder.addAllDatasetDependencies(response.getDependencies());
      }

      for(CheckRemovablesItem item : request.folders()) {
         AssetDependenciesResponse response =
            dataSetService.isFolderRemoveable(item.path(), item.scope(), principal);
         builder.addAllFolderDependencies(response.getDependencies());
      }

      return builder.build();
   }

   @GetMapping("/api/data/datasets/removeableStatus/**")
   public AssetDependenciesResponse checkDatasetRemoveable(@RemainingPath String path,
                                                           @RequestParam(value = "scope") Optional<Integer> scope,
                                                           Principal principal)
      throws Exception
   {
      return dataSetService.isWorksheetRemoveable(path, scope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
   }

   @DeleteMapping("/api/data/folders/**")
   public void deleteFolder(@RemainingPath String path,
                            @RequestParam(value = "scope") Optional<Integer> scope,
                            Principal principal)
      throws Exception
   {
      int scopeValue = scope.orElse(AssetRepository.GLOBAL_SCOPE);
      dataSetService.deleteFolder(dataSetService.getAuditPath(path, scopeValue, principal),
                                  path, scopeValue, principal);
   }

   @DeleteMapping("/api/data/datasets/**")
   public DeleteDataSetResponse deleteDataSet(
      @RemainingPath String path, @RequestParam(value = "scope") Optional<Integer> scope,
      @RequestParam(value = "force") Optional<Boolean> force, Principal principal)
      throws Exception
   {
      try {
         dataSetService.deleteWorksheet(
            path, scope.orElse(AssetRepository.GLOBAL_SCOPE), principal, force.orElse(false));
         return DeleteDataSetResponse.builder()
            .successful(true)
            .corrupt(false)
            .build();
      }
      catch(MissingAssetClassNameException e) {
         LOG.error("Data set is corrupt, could not delete", e);
         return DeleteDataSetResponse.builder()
            .successful(false)
            .corrupt(true)
            .build();
      }
      catch(Exception e) {
         LOG.error("Failed to delete data set", e);
         return DeleteDataSetResponse.builder()
            .successful(false)
            .corrupt(false)
            .build();
      }
   }

   @PostMapping("/api/data/removeAll")
   public void removeAll(@RequestBody WorksheetBrowserInfo[] entries,
                         Principal principal) throws Exception
   {
      for(WorksheetBrowserInfo entry : entries) {
         if(entry.type() == AssetEntry.Type.FOLDER) {
            dataSetService.deleteFolder(
               dataSetService.getAuditPath(entry.path(), entry.scope(), principal),
               entry.path(), entry.scope(), principal);
         }
         else if(entry.type() == AssetEntry.Type.WORKSHEET) {
            dataSetService.deleteWorksheet(entry.path(), entry.scope(), principal, false);
         }
      }
   }

   /**
    * Moves a data set to a different parent folder.
    *
    * @param command the move command.
    */
   @PostMapping("/api/data/datasets/move")
   public void moveDataSet(@RequestBody MoveCommand command,
                           @RequestParam Optional<Integer> assetScope,
                           @RequestParam Optional<Integer> targetScope,
                           Principal principal)
      throws Exception
   {
      String newPath = dataSetService.createPath(command.getPath(), command.getName());
      String actionMessage = "Target Entry: " +
         dataSetService.getAuditPath(newPath, targetScope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
      dataSetService.moveDataSet(command.getOldPath(), command,
            assetScope.orElse(AssetRepository.GLOBAL_SCOPE),
            targetScope.orElse(AssetRepository.GLOBAL_SCOPE),
            principal, actionMessage);
   }

   /**
    * Moves datasets to a different parent folder.
    *
    * @param items the move commands for the datasets.
    *
    * @throws Exception if the datasets could not be moved.
    */
   @PostMapping("/api/data/datasets/moveDatasets")
   public MessageCommand moveDataSets(@RequestBody MoveCommand[] items,
                            @RequestParam Optional<Integer> assetScope,
                            @RequestParam Optional<Integer> targetScope,
                            Principal principal)
      throws Exception
   {
      MessageCommand messageCommand = null;

      try {
         dataSetService.moveDataSets(items, assetScope.orElse(AssetRepository.GLOBAL_SCOPE),
            targetScope.orElse(AssetRepository.GLOBAL_SCOPE), principal);
      }
      catch(Exception ex) {
         if(ex instanceof MessageException) {
            MessageException messageException = (MessageException) ex;

            messageCommand = new MessageCommand();
            messageCommand.setMessage(messageException.getMessage());
            messageCommand.setType(MessageCommand.Type
               .fromCode(messageException.getWarningLevel()));
         }
         else {
            throw ex;
         }
      }

      return messageCommand;
   }

   private final DataSetService dataSetService;
   private static final Logger LOG = LoggerFactory.getLogger(DataSetController.class.getName());
}
