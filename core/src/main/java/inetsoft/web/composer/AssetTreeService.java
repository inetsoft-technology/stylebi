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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.web.composer.model.LoadAssetTreeNodesValidator;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class AssetTreeService {

   public AssetTreeService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public LoadAssetTreeNodesValidator getConnectionParameters(@ClusterProxyKey String rid, String cubeData, AssetEntry entry,
                                                              AssetRepository assetRepository, Principal principal) throws Exception
   {
      if(rid == null || "".equals(rid)) {
         return null;
      }

      LoadAssetTreeNodesValidator result = null;
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rid, principal);
      Viewsheet vs = rvs.getViewsheet();

      if(entry != null && "true".equals(entry.getProperty("CUBE_TABLE")) || cubeData != null) {
         String source;
         String name;

         if(cubeData == null) {
            source = entry.getParentPath();
            name = entry.getName();
         }
         else {
            if(!cubeData.startsWith(Assembly.CUBE_VS)) {
               return null;
            }

            String path = cubeData.substring(Assembly.CUBE_VS.length());
            int index = path.lastIndexOf("/");
            source = path.substring(0, index);
            name = path.substring(index + 1);
         }

         XRepository rep = XFactory.getRepository();
         ViewsheetInfo vinfo = vs.getViewsheetInfo();

         if(vinfo != null && vinfo.isDisableParameterSheet()) {
            return result;
         }

         XDataSource ds = rep.getDataSource(source);

         if(!(ds instanceof JDBCDataSource) && !(ds instanceof XMLADataSource))
         {
            return result;
         }

         VariableTable vtbl = new VariableTable();
         XUtil.copyDBCredentials((XPrincipal)principal, vtbl);

         if(vtbl.contains(XUtil.DB_USER_PREFIX + source)) {
            rep.connect(assetRepository.getSession(), ":" + source, vtbl);
            return result;
         }

         try{
            UserVariable[] vars = rep.getConnectionParameters(
               assetRepository.getSession(), ":" + name);

            if(vars != null && vars.length > 0) {
               AssetUtil.validateAlias(vars);
               List<VariableAssemblyModelInfo> parameters =
                  Arrays.stream(vars)
                     .map(VariableAssemblyModelInfo::new)
                     .collect(Collectors.toList());

               result = LoadAssetTreeNodesValidator.builder()
                  .parameters(parameters)
                  .treeNodeModel(TreeNodeModel.builder().build())
                  .build();
            }
         }
         catch(RemoteException re) {
            //Expand the node directly if can't get connection parameters.
         }
      }

      return result;
   }

   private ViewsheetService viewsheetService;
}
