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
package inetsoft.web.admin.content.repository;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class AutoSaveController {
   @Autowired
   public AutoSaveController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Delete the assets
    */
   @PostMapping("/api/em/content/repository/autosave/delete")
   public void deleteAutoSaveAssets(@RequestBody Map<String, String> entryPath, Principal user)
       throws Exception
   {
      String[] ids = Tool.split(entryPath.get("ids"), ',');

      for(int i = 0; i < ids.length; i++) {
         String path = ids[i];
         AutoSaveUtils.deleteAutoSaveFile(path, user);
         int type = path.startsWith("8^WORKSHEET^") ? RepositoryEntry.AUTO_SAVE_WS :
                 RepositoryEntry.AUTO_SAVE_VS;
         String objectName = Util.getObjectFullPath(type, path, user, null);
         ActionRecord actionRecord = SUtil.getActionRecord(user,
                 ActionRecord.ACTION_NAME_DELETE, objectName, ActionRecord.OBJECT_TYPE_ASSET);
         Audit.getInstance().auditAction(actionRecord, user);
      }
   }

   /**
    * Get the assets time
    */
   @PostMapping("/api/em/content/repository/autosave/gettime")
   public String getAutoSaveTime(@RequestBody Map<String, String> entryPath, Principal user)
       throws Exception
   {
      String id = entryPath.get("id");
      String clientTimeZone = entryPath.get("timezoneid");

      return AutoSaveUtils.getAutoSavedTime(id, user, clientTimeZone);
   }

   /**
    * Restore the assets
    */
   @PostMapping("/api/em/content/repository/autosave/restore")
   public void restoreAutoSaveAssets(@RequestBody Map<String, String> entryPath, Principal user)
      throws Exception
   {
      boolean overwrite = Boolean.parseBoolean(entryPath.get("overwrite"));
      String[] ids = Tool.split(entryPath.get("ids"), ',');
      String fileName = entryPath.get("name");
      String folder = entryPath.get("folder");
      String assetName = "/".equals(folder) ? fileName : folder + "/" + fileName;

      if(ids.length == 1) {
         restoreAutoSaveAsset(ids[0], assetName, overwrite, user);
         AutoSaveUtils.deleteAutoSaveFile(ids[0], user);
         return;
      }

      for(int i = 0; i < ids.length; i++) {
         String nname = assetName + i;
         restoreAutoSaveAsset(ids[i], nname, overwrite, user);
         AutoSaveUtils.deleteAutoSaveFile(ids[i], user);
      }
   }

   private void restoreAutoSaveAsset(String id, String assetName, boolean override,
                                     Principal principal) throws Exception
   {
      // Get auto save sheet from engine.
      AssetEntry entry = AutoSaveUtils.createAssetEntry(id);
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AbstractSheet sheet = repository.getSheet(entry, principal, false, AssetContent.ALL);
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      // Save auto save sheet to engine.
      AssetEntry.Type type = id.startsWith("8^VIEWSHEET") ? AssetEntry.Type.VIEWSHEET :
         AssetEntry.Type.WORKSHEET;
      AssetEntry nentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, assetName,
         pId);

      if(!override && viewsheetService.isDuplicatedEntry(repository, nentry)) {
         return;
      }

      repository.setSheet(nentry, sheet, principal, false);
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
         assetName, AssetEventUtil.getObjectType(entry));
      Audit.getInstance().auditAction(actionRecord, principal);
   }

   /**
    * Get the repository tree root
    */
   @GetMapping("/api/em/content/repository/autosave/tree")
   public RestoreAssetTreeListModel getRepositoryTree(Principal principal,
      @RequestParam("isvs") boolean isVS) throws Exception
   {
      return getAssetFolder(principal, isVS);
   }

   private RestoreAssetTreeListModel getAssetFolder(Principal user, boolean isVS) throws Exception {
      RestoreAssetTreeListModel.Builder builder = RestoreAssetTreeListModel.builder();
      Set<String> keys = isVS ?
         IndexedStorage.getIndexedStorage().getKeys(this::isViewsheetFolder) :
         IndexedStorage.getIndexedStorage().getKeys(this::isWorksheetFolder);
      List<String> folders = new ArrayList<String>();
      String root = null;

      for(String key : keys) {
         AssetEntry entry = AssetEntry.createAssetEntry(key);

         if(entry.getScope() == AssetRepository.USER_SCOPE) {
            continue;
         }

         // Root node, do not add it, only add its children folder.
         if(key.contains("^/")) {
            if(!key.startsWith("1^8^__NULL__^/")) {
               root = key;
            }

            continue;
         }

         folders.add(key);
      }

      Catalog catalog = Catalog.getCatalog();

      RestoreAssetTreeModel rootNode = new RestoreAssetTreeModel.Builder()
                                            .id("/")
                                            .label(catalog.getString(
                                             isVS ? "Repository" : "Data " + "Worksheet"))
                                            .folder(true)
                                            .children(getChildren(root, folders))
                                            .build();

      builder.addNodes(rootNode);

      return builder.build();
   }

   private List<RestoreAssetTreeModel> getChildren(String parent, List<String> folders) {
      List<RestoreAssetTreeModel> list = new ArrayList<RestoreAssetTreeModel>();
      Iterator<String> iterator = folders.iterator();

      while(iterator.hasNext()) {
         String folder = iterator.next();

         if(isParentChild(parent, folder)) {
            AssetEntry entry = AssetEntry.createAssetEntry(folder);

            RestoreAssetTreeModel cnode = new RestoreAssetTreeModel.Builder()
                    .id(entry.getPath())
                    .label(entry.getName())
                    .folder(true)
                    .children(getChildren(folder, folders))
                    .build();

            list.add(cnode);
         }
      }

      list.sort(new DefaultComparator());

      return list;
   }

   // Child node must start with parent node string and has one more character "/".
   private boolean isParentChild(String parent, String child) {
      // vs root: 1^4097^__NULL__^/
      // ws root: 1^1^__NULL__^/
      // the root folder children do not have /
      if(parent.startsWith("1^4097^__NULL__^/") || parent.startsWith("1^1^__NULL__^/")) {
         return !child.contains("/");
      }

      int plen = Tool.split(parent, '/').length;
      int clen = Tool.split(child, '/').length;

      return plen + 1 == clen;
   }

   private boolean isViewsheetFolder(String key) {
      if(key == null || key.contains(RecycleUtils.RECYCLE_BIN_FOLDER)) {
         return false;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(key);
      return entry != null && entry.isRepositoryFolder();
   }

   private boolean isWorksheetFolder(String key) {
      if(key == null || key.contains(RecycleUtils.RECYCLE_BIN_FOLDER)) {
         return false;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(key);
      return entry != null && entry.isWorksheetFolder() && !entry.isRepositoryFolder();
   }

   private final ViewsheetService viewsheetService;
}
