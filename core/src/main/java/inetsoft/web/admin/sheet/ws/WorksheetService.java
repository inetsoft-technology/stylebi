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
package inetsoft.web.admin.sheet.ws;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;

/**
 * {@code WorksheetService} implements the worksheet management logic shared by the enterprise
 * Public API ({@code WorksheetApiService}) and the admin-ai plugin. It always operates on the
 * caller's current organization -- there is no organization-switching concept here, that is an
 * enterprise-only capability layered on top by {@code WorksheetApiService}.
 */
@Service
public class WorksheetService {
   @Autowired
   public WorksheetService(AssetRepository assetRepository,
                           RepletRegistryService repletRegistryService)
   {
      this.assetRepository = assetRepository;
      this.repletRegistryService = repletRegistryService;
   }

   /**
    * Gets the list of available worksheets in the caller's current organization.
    */
   public SheetList getWorksheets(Principal user) throws Exception {
      SheetList list = new SheetList();
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      AssetEntry root = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null);
      AssetEntry[] entries = assetRepository.getAllEntries(
         root, user, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.WORKSHEET));
      Arrays.stream(entries)
         .map(this::convertEntry)
         .sorted(Comparator.comparing(Sheet::getAsset))
         .forEachOrdered(list.getSheets()::add);

      root = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", pId);
      entries = assetRepository.getAllEntries(
         root, user, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.WORKSHEET));
      Arrays.stream(entries)
         .map(this::convertEntry)
         .sorted(Comparator.comparing(Sheet::getAsset))
         .forEachOrdered(list.getSheets()::add);

      return list;
   }

   /**
    * A minimal alias/description pair for a worksheet.
    */
   public record Metadata(String alias, String description) {
   }

   /**
    * Reads a worksheet's current alias/description. Unlike a viewsheet, a worksheet's
    * description is a plain {@code AssetEntry} property, so this read needs no sheet content
    * load at all.
    */
   public Metadata getWorksheetSettingsMetadata(String id, Principal user) throws Exception {
      AssetEntry entry = getAssetEntry(id, user);
      return new Metadata(entry.getAlias(), entry.getProperty("description"));
   }

   /**
    * Updates a worksheet's alias/description without renaming it.
    */
   public void updateMetadata(String id, String alias, String description, Principal principal)
      throws Exception
   {
      repletRegistryService.updateSheet(id, null, null, alias, description, principal);
   }

   /**
    * Renames a worksheet.
    */
   public void renameWorksheet(String id, String path, boolean global, IdentityID owner,
                               Principal user) throws Exception
   {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_RENAME, "",
         ActionRecord.OBJECT_TYPE_WORKSHEET, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");

      try {
         AssetEntry oldEntry = getAssetEntry(id, user);
         AssetEntry newEntry = new AssetEntry(
            global ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE,
            AssetEntry.Type.WORKSHEET, path, global ? null : owner);

         //handle site admin in different organization, ensure same org for asset rename
         newEntry.setOrgID(oldEntry.getOrgID());
         record.setObjectName(oldEntry.getName());
         record.setActionError("Target Entry: " + newEntry.getDescription());

         assetRepository.changeSheet(oldEntry, newEntry, user, true, true, false);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(UnauthorizedAccessException | MissingResourceException e) {
         record.setActionError("Failed to rename worksheet: " + id);
         throw e;
      }
      catch(MessageException e) {
         record.setActionError("Failed to rename worksheet: " + id);

         if("FOLDER_REQUIRED".equals(e.getKeywords())) {
            throw new MissingResourceException(e.getMessage(), e);
         }

         throw new RuntimeException("Failed to rename worksheet: " + id, e);
      }
      catch(Exception e) {
         record.setActionError("Failed to rename worksheet: " + id);
         throw new RuntimeException("Failed to rename worksheet: " + id, e);
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   /**
    * Deletes a worksheet.
    */
   public void deleteWorksheet(String id, Principal user) throws Exception {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_DELETE, "",
         ActionRecord.OBJECT_TYPE_WORKSHEET, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");

      try {
         AssetEntry entry = getAssetEntry(id, user);
         record.setObjectName(entry.getName());
         assetRepository.removeSheet(entry, user, true);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(UnauthorizedAccessException | MissingResourceException e) {
         record.setActionError("Failed to delete worksheet: " + id);
         throw e;
      }
      catch(Exception e) {
         record.setActionError("Failed to delete worksheet: " + id);
         throw new RuntimeException("Failed to delete worksheet: " + id, e);
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   private Sheet convertEntry(AssetEntry entry) {
      return new Sheet(
         entry.toIdentifier(), entry.getPath(), entry.toView(),
         entry.getScope() == AssetRepository.GLOBAL_SCOPE, entry.getUser(), null);
   }

   private AssetEntry getAssetEntry(String identifier, Principal user) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);
      AssetEntry resolved = assetRepository.getAssetEntry(entry);

      if(resolved == null) {
         throw new MissingResourceException(entry.toIdentifier());
      }

      checkPermission(entry, user, ResourceAction.READ);
      entry.setAlias(resolved.getAlias());
      entry.setProperty("description", resolved.getProperty("description"));
      return entry;
   }

   private void checkPermission(AssetEntry entry, Principal user, ResourceAction action)
      throws Exception
   {
      try {
         assetRepository.checkAssetPermission(user, entry, action);
      }
      catch(Exception e) {
         throw new UnauthorizedAccessException(entry.toIdentifier());
      }
   }

   private final AssetRepository assetRepository;
   private final RepletRegistryService repletRegistryService;
}
