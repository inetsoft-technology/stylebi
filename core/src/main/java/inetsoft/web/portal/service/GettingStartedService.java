/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.service;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.web.RecycleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Objects;

@Service
public class GettingStartedService {
   public GettingStartedService(AssetRepository assetRepository,
                                AnalyticRepository analyticRepository,
                                SecurityEngine securityEngine)
   {
      this.assetRepository = assetRepository;
      this.analyticRepository = analyticRepository;
      this.securityEngine = securityEngine;
   }

   public boolean hasCreateWSPermission(Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check Data Worksheet permission", e);

         return false;
      }
   }

   public boolean checkCreateDatasourcePermission(Principal principal) {
      try {
         PortalThemesManager manager = PortalThemesManager.getManager();

         if(!manager.getPortalTabs().stream().anyMatch(tab -> "Data".equals(tab.getName()))) {
            return false;
         }

         return securityEngine.checkPermission(
            principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS) ||
            securityEngine.checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER,
               "/", ResourceAction.WRITE);
      }
      catch(Exception ex) {
         LOG.warn("Failed to check data source permission", ex);

         return false;
      }
   }

   public boolean hasDashboardPermission(Principal principal) {
      try {
         return securityEngine.checkPermission(principal, ResourceType.VIEWSHEET,
            "*", ResourceAction.ACCESS);
      }
      catch(SecurityException ex) {
         LOG.warn("Failed to check Dashboard permission", ex);

         return false;
      }
   }

   public boolean hasPermission(Principal principal) {
      try {
         return checkCreateDatasourcePermission(principal) && hasDashboardPermission(principal) &&
            hasCreateWSPermission(principal);
      }
      catch(Exception ex) {
         return false;
      }
   }

   public boolean hasCreatedAssets(Principal principal) {
      final IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();
      String userName = IdentityID.getIdentityIDFromKey(principal.getName()).getName();

      return indexedStorage.getKeys(Objects::nonNull)
         .stream()
         .filter(key -> key != null)
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull)
         .filter(e -> !e.getType().isFolder() && e.getType().isViewsheet())
         .filter(AssetEntry::isValid)
         .filter(e -> {
            try {
               if(RecycleUtils.isInRecycleBin(e.getPath())) {
                  return false;
               }

               if(!securityEngine.isSecurityEnabled()) {
                  return true;
               }

               assetRepository.checkAssetPermission(principal, e, ResourceAction.READ);

               return true;
            }
            catch(Exception ex) {
               return false;
            }
         })
         .map(e -> {
            try {
               return assetRepository.getAssetEntry(e);
            }
            catch(Exception ex) {
               return null;
            }
         })
         .filter(Objects::nonNull)
         .anyMatch(e -> Objects.equals(e.getCreatedUsername(), userName));
   }

   public String getDefaultFolder(Principal principal, AssetEntry.Type type) {
      String orgId = ((XPrincipal) principal).getOrgId();

      if(Tool.equals(Organization.getSelfOrganizationID(), orgId)) {
         AssetEntry.Type folderType;

         if(type.equals(AssetEntry.Type.WORKSHEET)) {
            folderType = AssetEntry.Type.FOLDER;
         }
         else {
            folderType = AssetEntry.Type.REPOSITORY_FOLDER;
         }

         AssetEntry folder = new AssetEntry(AssetRepository.USER_SCOPE, folderType, "/",
            IdentityID.getIdentityIDFromKey(principal.getName()), Organization.getSelfOrganizationID());

         return folder.toIdentifier();
      }

      return null;
   }

   private final AssetRepository assetRepository;
   private final AnalyticRepository analyticRepository;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(GettingStartedService.class);
}
