/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.AutoSaveUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.Principal;
import java.util.Map;

@Service
@ClusterProxy
public class AutoSaveService {
   public AutoSaveService(ViewsheetService viewsheetService) {
      this.viewsheetService =viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean restoreAutoSaveAssets(@ClusterProxyKey String id, String assetName, boolean override,
                                     Principal principal)
      throws Exception
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
         return false;
      }

      repository.setSheet(nentry, sheet, principal, false);
      ActionRecord actionRecord = SUtil.getActionRecord(principal, ActionRecord.ACTION_NAME_CREATE,
                                                        assetName, AssetEventUtil.getObjectType(entry));
      Audit.getInstance().auditAction(actionRecord, principal);
      return true;
   }


   private final ViewsheetService viewsheetService;
}
