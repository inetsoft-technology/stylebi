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
package inetsoft.web.composer.ws.service;

import inetsoft.report.*;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
public class SaveWorksheetService {
   @Autowired
   public SaveWorksheetService(AssetRepository assetRepository) {
      this.assetRepository = assetRepository;
   }

   public void syncNameGroupInfo(Worksheet ws, Principal principal) throws Exception {
      AssetEntry[] outerDependencies = ws.getOuterDependencies(true);

      if(outerDependencies == null || outerDependencies.length == 0) {
         return;
      }

      for(AssetEntry outerDependency : outerDependencies) {
         AbstractSheet sheet =
            assetRepository.getSheet(outerDependency, principal, false, AssetContent.ALL);

         if(sheet instanceof Viewsheet) {
            Viewsheet viewsheet = (Viewsheet) sheet;
            Assembly[] assemblies = viewsheet.getAssemblies();
            boolean change = false;

            for(Assembly assembly : assemblies) {
               if(!(assembly instanceof CalcTableVSAssembly)) {
                  continue;
               }

               change = syncCalcTableNamedGroup((CalcTableVSAssembly) assembly, change);
            }

            if(change) {
               assetRepository.setSheet(outerDependency, sheet, principal, false);
            }
         }
      }
   }

   private boolean syncCalcTableNamedGroup(CalcTableVSAssembly assembly, boolean change) {
      CalcTableVSAssemblyInfo calcAssemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      TableLayout tableLayout = calcAssemblyInfo.getTableLayout();
      List<CellBindingInfo> cellInfos = tableLayout.getCellInfos(true);

      if(cellInfos == null || cellInfos.size() == 0) {
         return change;
      }

      for(CellBindingInfo cellInfo : cellInfos) {
         if(cellInfo == null) {
            continue;
         }

         TableCellBinding cellBinding = ((TableLayout.TableCellBindingInfo) cellInfo).getCellBinding();

         if(cellBinding == null || cellBinding.getOrderInfo(false) == null) {
            continue;
         }

         OrderInfo orderInfo = cellBinding.getOrderInfo(false);
         XNamedGroupInfo namedGroupInfo = orderInfo.getNamedGroupInfo();

         if(namedGroupInfo != null && namedGroupInfo instanceof AssetNamedGroupInfo) {
            AssetNamedGroupInfo oldInfo = (AssetNamedGroupInfo) namedGroupInfo;
            AssetEntry entry = oldInfo.getEntry();
            NamedGroupAssembly namedGroupAssembly = LayoutTool.getNamedGroupAssembly(entry);

            if(namedGroupAssembly != null) {
               AssetNamedGroupInfo newInfo = new AssetNamedGroupInfo(entry, namedGroupAssembly);

               if(oldInfo != null && !oldInfo.equalsContent(newInfo)) {
                  orderInfo.setNamedGroupInfo(newInfo);
                  change = true;
               }
            }
         }
      }

      return change;
   }

   private AssetRepository assetRepository;
   private static final Logger LOG =
      LoggerFactory.getLogger(SaveWorksheetService.class);
}
