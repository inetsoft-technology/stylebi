/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.web.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class RepositoryRecycleBinController {
   @Autowired
   public RepositoryRecycleBinController(RepositoryObjectService repositoryObjectService,
                                         ContentRepositoryTreeService contentRepositoryTreeService,
                                         SecurityProvider securityProvider,
                                         RepletRegistryManager registryManager)
   {
      this.repositoryObjectService = repositoryObjectService;
      this.contentRepositoryTreeService = contentRepositoryTreeService;
      this.securityProvider = securityProvider;
      this.registryManager = registryManager;
   }

   @GetMapping("/api/em/content/repository/folder/recycleBin")
   public RepositoryFolderRecycleBinSettingsModel getRecycleBinFolderSettings(Principal principal)
      throws Exception
   {
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      List<RepositoryFolderRecycleBinTableModel> table = new ArrayList<>();
      addRecycleSheets(table, principal, recycleBin);

      return RepositoryFolderRecycleBinSettingsModel.builder().table(table).build();
   }

   /**
    * Clear the recycle bin
    */
   @Secured(value = {
      @RequiredPermission(
         resourceType = ResourceType.REPORT,
         resource = "/",
         actions = ResourceAction.ADMIN
      ),
      @RequiredPermission(
         resourceType = ResourceType.ASSET,
         resource = "/",
         actions = ResourceAction.ADMIN
      )
   },
   operator = "OR")
   @DeleteMapping("/api/em/repository/recycle-bin/entries")
   public void clearRecycleBin(Principal principal) throws Exception {
      // clear asset repository
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      final List<AssetEntry> assetsInTrash = getAssetsInTrash();

      for(AssetEntry assetEntry : assetsInTrash) {
         LOG.debug("Removing asset: " + assetEntry);
         if(assetEntry.isFolder()) {
            repository.removeFolder(assetEntry, principal, true);
         }
         else {
            repository.removeSheet(assetEntry, principal, true);
         }
      }

      // clear recycle bin
      final RecycleBin recycleBin = RecycleBin.getRecycleBin();
      final List<String> toRemove = recycleBin.getEntries()
         .stream()
         .map(RecycleBin.Entry::getPath)
         .collect(Collectors.toList());

      for(String path : toRemove) {
         LOG.debug("Removing from recycle bin: " + path);
         recycleBin.removeEntry(path);
      }

      AutoSaveUtils.deleteAllAutoSaveFile();
   }

   private List<AssetEntry> getAssetsInTrash() throws Exception {
      return IndexedStorage.getIndexedStorage()
         .getKeys(key -> true)
         .stream()
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull)
         .filter(entry -> entry.isViewsheet() || entry.isWorksheet() || entry.isFolder())
         .filter(entry -> RecycleUtils.RECYCLE_BIN_FOLDER.equals(entry.getPath()))
         .collect(Collectors.toList());
   }

   private void addRecycleSheets(List<RepositoryFolderRecycleBinTableModel> table,
      Principal principal, RecycleBin recycleBin) throws Exception
   {
      //get vs entries
      AssetEntry.Selector vsSelector = new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET_SNAPSHOT,
            AssetEntry.Type.REPOSITORY_FOLDER);
      AssetEntry.Type vsEntryType = AssetEntry.Type.REPOSITORY_FOLDER;
      getRecycleNodeFromAssets(table, principal, recycleBin, vsSelector, vsEntryType);

      //get ws entries
      AssetEntry.Selector wsSelector = new AssetEntry.Selector(AssetEntry.Type.WORKSHEET,
            AssetEntry.Type.FOLDER);
      AssetEntry.Type wsEntryType = AssetEntry.Type.FOLDER;
      getRecycleNodeFromAssets(table, principal, recycleBin, wsSelector, wsEntryType);
   }

   private void getRecycleNodeFromAssets(List<RepositoryFolderRecycleBinTableModel> table,
                                         Principal principal, RecycleBin recycleBin,
                                         AssetEntry.Selector selector, AssetEntry.Type entryType)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry[] entries;
      IdentityID[] users = securityProvider.getUsers();

      if(repository != null) {
         AssetEntry pentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                 entryType, RecycleUtils.getRecycleBinPath(null), null);

         entries = repository.getEntries(pentry, principal,
                 ResourceAction.READ, selector);
         getRecycleNodeFromAssetEntries(table, entries, recycleBin, principal);

         for(IdentityID user : users) {
            pentry = new AssetEntry(AssetRepository.USER_SCOPE,
                                    entryType, RecycleUtils.getRecycleBinPath(null), user);

            entries = repository.getEntries(pentry, principal,
                                            ResourceAction.READ, selector);
            getRecycleNodeFromAssetEntries(table, entries, recycleBin, principal);
         }
      }
   }

   private void getRecycleNodeFromAssetEntries(List<RepositoryFolderRecycleBinTableModel> table,
                                               AssetEntry[] entries, RecycleBin recycleBin,
                                               Principal principal)
   {
      for(AssetEntry entry : entries) {
         RecycleBin.Entry binEntry = recycleBin.getEntry(entry.getPath());
         boolean securityCheck =  entry.getUser() != null ?
            securityProvider.checkPermission(principal, ResourceType.SECURITY_USER, entry.getUser().convertToKey(), ResourceAction.ADMIN) :
            securityProvider.checkPermission(principal, RecycleUtils.getAssetResourceType(entry), entry.getPath(), ResourceAction.ADMIN);

         if(binEntry != null && !entry.isRepositoryFolder() && securityCheck) {
            RepositoryFolderRecycleBinTableModel report =
               RepositoryFolderRecycleBinTableModel.builder()
                  .from(binEntry.getOriginalPath(),
                        RecycleUtils.getTypeLabel(binEntry.getType()), entry.getPath(),
                        binEntry.getOriginalUser() == null ?
                           Catalog.getCatalog().getString("GLOBAL") :
                           binEntry.getOriginalUser().name)
                  .dateDeleted(Tool.getDateTimeFormat().format(binEntry.getTimestamp()))
                  .build();

            table.add(report);
         }
      }
   }

   @GetMapping("/api/em/content/repository/recycle/node")
   public RepositoryRecycleBinEntryModel getRepositoryRecycleBinEntryModel(
      @RequestParam("path") String path)
   {
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      RecycleBin.Entry entry = recycleBin.getEntry(path);
      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));

      return RepositoryRecycleBinEntryModel.builder()
         .path(entry.getPath())
         .originalPath(entry.getOriginalPath())
         .originalName(entry.getName())
         .originalType(RecycleUtils.getTypeLabel(entry.getType()))
         .timestamp(entry.getTimestamp())
         .timeLabel(format.format(entry.getTimestamp()))
         .build();
   }

   @PostMapping("/api/em/content/repository/folder/recycleBin/delete")
   public String deleteRecycleBinFolder(
      @RequestBody() RepositoryFolderRecycleBinSettingsModel model, Principal principal) {
      List<RepositoryFolderRecycleBinTableModel> table = model.table();
      ArrayList<TreeNodeInfo> recycleNodes = new ArrayList<>();

      for(RepositoryFolderRecycleBinTableModel item : table) {
         RecycleBin recycleBinFile = RecycleBin.getRecycleBin();
         RecycleBin.Entry binEntry  = recycleBinFile.getEntry(item.path());

         final TreeNodeInfo recycleNodeInfo = new TreeNodeInfo.Builder()
            .label(binEntry.getName())
            .path(binEntry.getPath())
            .type(binEntry.getType())
            .build();

         recycleNodes.add(recycleNodeInfo);
      }

      TreeNodeInfo[] recycleNodesInfo = new TreeNodeInfo[recycleNodes.size()];
      repositoryObjectService.deleteNodes(
         recycleNodes.toArray(recycleNodesInfo), principal, false, false);

      return null;
   }

   @PostMapping("/api/em/content/repository/folder/recycleBin/restore")
   public String restoreRecycleBinFolder(
      @RequestBody() RepositoryFolderRecycleBinSettingsModel model, Principal principal)
      throws Exception
   {
      List<RepositoryFolderRecycleBinTableModel> table = model.table();
      boolean overwrite = model.overwrite();

      for(RepositoryFolderRecycleBinTableModel item : table) {
         restoreNode(item.path(), overwrite, principal);
      }

      return null;
   }

   @PostMapping("api/em/content/repository/tree/recycleNode/restore")
   public String restoreRecycleBinEntry(@RequestBody Map<String, String> entryPath,
                                      Principal principal)
      throws Exception
   {
      boolean overwrite = Boolean.parseBoolean(entryPath.get("overwrite"));
      return restoreNode(entryPath.get("path"), overwrite, principal);
   }

   public String restoreNode(String path, boolean overwrite, Principal principal) throws Exception {
      RecycleBin recycleBinFile = RecycleBin.getRecycleBin();
      RecycleBin.Entry rEntry  = recycleBinFile.getEntry(path);

      if(rEntry == null) {
         return null;
      }

      if(rEntry.isSheet()) {
         RecycleUtils.restoreSheet(rEntry, overwrite, principal, recycleBinFile);
      }
      else if(rEntry.isFolder()) {
         try {
            if(rEntry.isWSFolder()) {
               return RecycleUtils.restoreWSFolder(rEntry, overwrite, principal, recycleBinFile);
            }
            else {
               return RecycleUtils.restoreRepositoryFolder(rEntry, overwrite, principal, recycleBinFile);
            }
         }
         catch(Exception ex) {
            throw new MessageException("Failed to change registry folder from " + path +
               " to " + rEntry.getOriginalPath(), ex);
         }
      }

      return null;
   }

   private final RepositoryObjectService repositoryObjectService;
   private final ContentRepositoryTreeService contentRepositoryTreeService;
   private final SecurityProvider securityProvider;
   private final RepletRegistryManager registryManager;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
