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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.LibManager;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.RecycleUtils;
import inetsoft.web.composer.model.*;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint for the composer data tree.
 */
@RestController
public class AssetTreeController {
   /**
    * Creates a new instance of <tt>AssetTreeController</tt>.
    *
    * @param assetRepository the asset repository.
    */
   @Autowired
   public AssetTreeController(AssetRepository assetRepository, ViewsheetService viewsheetService, AssetTreeService assetTreeService) {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
      this.assetTreeService = assetTreeService;
   }

   @PostMapping("/api/vs/bindingtree/getConnectionParameters")
   public LoadAssetTreeNodesValidator getConnectionParameters(
      @RequestParam("rid") String rid,
      @RequestParam(value = "cubeData", required = false) String cubeData,
      @RequestBody(required = false) AssetEntry entry,
      Principal principal) throws Exception
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

   /**
    * Gets the child nodes of the specified parent node.
    *
    * @return the child nodes.
    */
   @PostMapping("/api/composer/asset_tree")
   public LoadAssetTreeNodesValidator getNodes(
      @RequestParam("includeDatasources") boolean includeDatasources,
      @RequestParam("includeColumns") boolean includeColumns,
      @RequestParam("includeWorksheets") boolean includeWorksheets,
      @RequestParam("includeViewsheets") boolean includeViewsheets,
      @RequestParam("includeTableStyles") boolean includeTableStyles,
      @RequestParam("includeScripts") boolean includeScripts,
      @RequestParam("includeLibrary") boolean includeLibrary,
      @RequestParam(value="reportRepositoryEnabled", required=false) boolean reportRepositoryEnabled,
      @RequestParam(value = "readOnly", required=false, defaultValue="false") boolean readOnly,
      @RequestParam(value = "physical", required = false, defaultValue = "true") boolean physical,
      @RequestBody LoadAssetTreeNodesEvent event, Principal principal)
      throws Exception
   {
      return assetTreeService.getNodes(includeDatasources, includeColumns, includeWorksheets, includeViewsheets,
         includeTableStyles, includeScripts, includeLibrary, reportRepositoryEnabled, readOnly,
         physical, event, principal);
   }

   @PostMapping("/api/composer/asset_tree/set-connection-variables")
   public MessageCommand setConnectionVariables(
      @RequestBody CollectParametersOverEvent event, Principal principal)
      throws Exception
   {
      VariableTable vtable = new VariableTable();
      Set<String> dbs = new HashSet<>();
      List<VariableAssemblyModelInfo> variables = event.variables();
      String message = null;

      if(variables != null) {
         for(VariableAssemblyModelInfo var : variables) {
            Object[] values = Arrays.stream(var.getValue())
               .map((val) -> val == null ? null : val.toString())
               .map((val) -> val == null || val.length() == 0 ? null :
               Tool.getData(var.getType(), val))
               .toArray(Object[]::new);

            String vname = var.getName();
            vtable.put(vname, values.length == 1 ? values[0] : values);

            if(vname.startsWith(XUtil.DB_PASSWORD_PREFIX)) {
               String db = vname.substring(XUtil.DB_PASSWORD_PREFIX.length());
               dbs.add(db);
            }
            else if(XUtil.DB_NAME_PARAMETER_NAME.equals(vname)) {
               // send the data base name as variable when the data base is text data base.
               if(var.getValue() != null && var.getValue().length == 1 &&
                  var.getValue()[0] instanceof String)
               {
                  dbs.add((String) var.getValue()[0]);
               }
            }
         }

         Object session = assetRepository.getSession();
         XRepository rep = XFactory.getRepository();
         Iterator iterator = dbs.iterator();

         while(iterator.hasNext()) {
            String db = (String) iterator.next();
            XDataSource ds = rep.getDataSource(db);

            if(db != null) {
               try {
                  rep.testDataSource(session, ds, vtable);
               }
               catch(Exception ex) {
                  message = ex.getMessage();
//                  command.addCommand(new MessageCommand(ex,
//                                                        MessageCommand.Type.ERROR));
                  continue;
               }

               String name = (String) vtable.get(XUtil.DB_USER_PREFIX + db);
               String pass = (String) vtable.get(XUtil.DB_PASSWORD_PREFIX + db);

               // copy to user to avoid always prompt
               if(name != null && pass != null && principal != null) {
                  ((XPrincipal) principal).setProperty(XUtil.DB_USER_PREFIX + db,
                                                  name);
                  ((XPrincipal) principal).setProperty(XUtil.DB_PASSWORD_PREFIX+ db,
                                                  pass);
               }

               rep.connect(session, ":" + db, vtable);
            }
         }
      }

      // set the parameters to principal. see SUtil.isNeedPrompt().
      if(Tool.isEmptyString(message) && variables != null && vtable != null) {
         for(VariableAssemblyModelInfo var : variables) {
            if(var == null || !vtable.contains(var.getName()) || var.getName() == null ||
               var.getName().startsWith(XUtil.DB_PASSWORD_PREFIX) ||
               var.getName().startsWith(XUtil.DB_USER_PREFIX) ||
               var.getName().equals(XUtil.DB_NAME_PARAMETER_NAME))
            {
               continue;
            }

            if(var.getName() != null && principal != null) {
               ((XPrincipal) principal).setParameter(var.getName(),
                  vtable.get(var.getName()));
            }
         }
      }

      MessageCommand command = new MessageCommand();
      command.setMessage(message);
      command.setType(MessageCommand.Type.ERROR);

      return command;
   }

   @PostMapping("/api/composer/asset_tree/check-recent-assets")
   public RecentAssetsCheckModel checkAssetRecents(@RequestBody RecentAssetsCheckModel model) {
      AssetEntry[] assets = model.assets();


      if(assets == null) {
         assets = new AssetEntry[0];
      }
      else {
         assets = Arrays.stream(assets).filter(asset -> {
            try {
               if(!assetRepository.containsEntry(asset)) {
                  LibManager manager = LibManager.getManager();

                  if(asset.isTableStyle()) {
                     XTableStyle style = manager.getTableStyleByName(asset.getProperty("styleName"));
                     return style != null;
                  }
                  else if(asset.isScript()) {
                     String script = manager.getScript(asset.getName());
                     return script != null;
                  }
               }

               return assetRepository.containsEntry(asset);
            }
            catch(Exception e) {
               return false;
            }
         }).toArray(AssetEntry[]::new);
      }

      return RecentAssetsCheckModel.builder().assets(assets).build();
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final AssetTreeService assetTreeService;
}
