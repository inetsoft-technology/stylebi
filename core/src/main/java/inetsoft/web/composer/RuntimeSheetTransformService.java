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

package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.RenameInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ClusterProxy
public class RuntimeSheetTransformService {
   public RuntimeSheetTransformService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateRenameInfos(@ClusterProxyKey String id,
                                 AssetEntry entry, List<RenameInfo> renameInfos) {
      viewsheetService.updateRenameInfos(id, entry, renameInfos);
      return null;
   }

   ViewsheetService viewsheetService;
}
