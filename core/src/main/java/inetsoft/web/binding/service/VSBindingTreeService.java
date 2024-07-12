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
package inetsoft.web.binding.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class VSBindingTreeService {
   public VSBindingTreeService(VSTreeHandler treeHandler, ViewsheetService viewsheetService) {
      this.treeHandler = treeHandler;
      this.viewsheetService = viewsheetService;
   }

   /**
    *
    * @param runtimeId VS runtimeID
    * @param name Assembly name
    */
   public TreeNodeModel getBinding(String runtimeId, String name, boolean layoutMode,
                                   Principal principal)
      throws Exception
   {
      if(runtimeId == null) {
         return null;
      }

      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(name);
      VSAssemblyInfo info = assembly != null ? assembly.getVSAssemblyInfo() : null;
      AssetTreeModel assetTreeModel = null;

      box.lockRead();

      try {
         if(info == null) {
            assetTreeModel = treeHandler.getWSTreeModel(
               engine.getAssetRepository(), rvs, null, layoutMode, principal);
         }
         else if(info instanceof DataVSAssemblyInfo) {
            DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) info;
            final boolean appendComponentTree = !rvs.isRuntime() ||
               dinfo.getSourceInfo() != null &&
                  dinfo.getSourceInfo().getType() == SourceInfo.VS_ASSEMBLY;

            if(info instanceof ChartVSAssemblyInfo) {
               assetTreeModel = treeHandler.getChartTreeModel(
                  engine.getAssetRepository(), rvs, (ChartVSAssemblyInfo) info, false, principal);

               if(appendComponentTree) {
                  treeHandler.appendVSAssemblyTree(rvs, assetTreeModel, principal, assembly);
               }
            }
            else if(info instanceof TableDataVSAssemblyInfo) {
               assetTreeModel = treeHandler.getTableTreeModel(
                  engine.getAssetRepository(), rvs, (TableDataVSAssemblyInfo) info, principal);

               if(appendComponentTree) {
                  treeHandler.appendVSAssemblyTree(rvs, assetTreeModel, principal, assembly);
               }
            }
         }
      }
      finally {
         box.unlockRead();
      }

      if(assetTreeModel != null) {
         return treeHandler.createTreeNodeModel(
            (AssetTreeModel.Node) assetTreeModel.getRoot(), principal);
      }

      return null;
   }

   private VSTreeHandler treeHandler;
   private ViewsheetService viewsheetService;
}
