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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.security.auth.MissingResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.TimeZone;

@Service
public class SheetService {
   @Autowired
   public SheetService(ResourcePermissionService permissionService)
   {
      this.permissionService = permissionService;
   }

   public RepositorySheetSettingsModel getSheetSettings(AssetEntry entry0,
                                                        ResourceType resourceType,
                                                        String timeZone,
                                                        String owner,
                                                        Principal principal) throws Exception
   {
      final String path = entry0.getPath();
      final String identifier = entry0.toIdentifier();
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry entry = registryManager.getAssetEntry(identifier, principal);

      if(entry == null) {
         // account for vs snapshots
         entry0 = new AssetEntry(entry0.getScope(), AssetEntry.Type.VIEWSHEET_SNAPSHOT, path, entry0.getUser());
         final String vsSnapshotIdentifier = entry0.toIdentifier();
         entry = registryManager.getAssetEntry(vsSnapshotIdentifier, principal);

         if(entry == null) {
            throw new MissingResourceException(entry0.toIdentifier());
         }
      }

      AbstractSheet vs = null;
      boolean corrupt = false;

      try {
         vs = repository.getSheet(entry, principal, false, AssetContent.ALL);
      }
      catch(MissingAssetClassNameException e) {
         LOG.error("Sheet {} is corrupt", entry.getPath(), e);
         corrupt = true;
      }

      String description = "";

      if(vs instanceof Viewsheet) {
         description = ((Viewsheet) vs).getViewsheetInfo().getDescription();
      }
      else if(vs instanceof VSSnapshot) {
         description = ((VSSnapshot) vs).getSnapshotDescription();
      }
      else if(entry.isWorksheet()) {
         description = entry.getProperty("description");
      }

      String unknown = Catalog.getCatalog().getString("Unknown");

      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));
      format.setTimeZone(TimeZone.getTimeZone(timeZone));
      String created = entry.getCreatedUsername() == null ?
         unknown : entry.getCreatedUsername();
      created += " - ";
      created += entry.getCreatedDate() == null ?
         unknown : format.format(entry.getCreatedDate());

      String modified = entry.getModifiedUsername() == null ?
         unknown : entry.getModifiedUsername();
      modified += " - ";
      modified += entry.getModifiedDate() == null ?
         unknown : format.format(entry.getModifiedDate());
      EnumSet<ResourceAction> resourceActions;

      if(resourceType == ResourceType.REPORT) {
         resourceActions = ResourcePermissionService.ADMIN_SHARE_ACTIONS;
      }
      else {
         resourceActions = ResourcePermissionService.ADMIN_ACTIONS;
      }

      final ResourcePermissionModel tableModel = owner != null ? null :
         permissionService.getTableModel(path, resourceType, resourceActions, principal);
      return RepositorySheetSettingsModel.builder()
         .name(entry.getName())
         .alias(entry.getAlias() == null ? "" : entry.getAlias())
         .description(description == null ? "" : description)
         .created(created)
         .modified(modified)
         .permissionTableModel(tableModel)
         .mvEnabled(!entry.isWorksheet() ||
                       SreeEnv.getBooleanProperty("ws.mv.enabled"))
         .corrupt(corrupt)
         .build();
   }

   public AssetEntry setSheetSettings(String identifier, Principal principal,
                                      RepositorySheetSettingsModel model) throws Exception
   {
      return registryManager.updateSheet(
         identifier, model.oname(), model.name(), model.alias(), model.description(), principal);
   }

   private final RepletRegistryManager registryManager = new RepletRegistryManager();
   private final ResourcePermissionService permissionService;
   private static final Logger LOG = LoggerFactory.getLogger(SheetService.class.getName());
}
