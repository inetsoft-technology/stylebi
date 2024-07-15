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
   public AssetTreeController(AssetRepository assetRepository, ViewsheetService viewsheetService) {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
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
      LoadAssetTreeNodesValidator result;
      TreeNodeModel treeNodeModel;
      Catalog catalog = Catalog.getCatalog(principal, Catalog.REPORT);
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry expandedEntry = event.targetEntry();
      AssetEntry.Selector assetSelector = physical ?
         new AssetEntry.Selector(AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
                                 AssetEntry.Type.VIEWSHEET, AssetEntry.Type.DATA,
                                 AssetEntry.Type.PHYSICAL, AssetEntry.Type.REPOSITORY_FOLDER,
                                 AssetEntry.Type.VIEWSHEET, AssetEntry.Type.VIEWSHEET_SNAPSHOT,
                                 AssetEntry.Type.COLUMN,
                                 AssetEntry.Type.QUERY, AssetEntry.Type.QUERY_FOLDER,
                                 AssetEntry.Type.TABLE, AssetEntry.Type.REPORT_COMPONENT) :
         new AssetEntry.Selector(AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
                                 AssetEntry.Type.VIEWSHEET, AssetEntry.Type.DATA,
                                 AssetEntry.Type.REPOSITORY_FOLDER,
                                 AssetEntry.Type.VIEWSHEET, AssetEntry.Type.VIEWSHEET_SNAPSHOT,
                                 AssetEntry.Type.COLUMN,
                                 AssetEntry.Type.QUERY, AssetEntry.Type.QUERY_FOLDER,
                                 AssetEntry.Type.REPORT_COMPONENT);

      boolean worksheetPermission = assetRepository.checkPermission(
         principal, ResourceType.WORKSHEET, "*", EnumSet.of(ResourceAction.ACCESS)) ||
         readOnly;
      boolean viewsheetPermission = assetRepository.checkPermission(
         principal, ResourceType.VIEWSHEET, "*", EnumSet.of(ResourceAction.ACCESS)) ||
         readOnly;
      boolean sqlEnabled = SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);

      List<TreeNodeModel> updatedChildren = new ArrayList<>();

      if(expandedEntry == null) {
         List<TreeNodeModel> children = new ArrayList<>();

         if(includeDatasources) {
            addDataSourceRootNodes(children, user, principal);
         }

         if(includeWorksheets && worksheetPermission) {
            addWorksheetRootNodes(children, user, principal);
         }

         if(includeViewsheets && viewsheetPermission) {
            addViewsheetRootNodes(children, user, principal);
         }

         if(includeLibrary && (viewsheetPermission || worksheetPermission)) {
            addLibraryRootNodes(children, user, principal);
         }

         if(!includeLibrary && includeTableStyles && (viewsheetPermission || worksheetPermission)) {
            addTableStyleRootNodes(children, principal);
         }

         if(!includeLibrary && includeScripts && (viewsheetPermission || worksheetPermission)) {
            addScriptRootNodes(children, principal);
         }

         treeNodeModel = TreeNodeModel.builder()
            .children(children)
            .build();
      }
      else {
         //TODO fix SR principal scope proxy problem (see agile)
         AssetEntry[] entries = getFilterFor(expandedEntry);
         AssetTreeModel.Node atmNode = new AssetTreeModel.Node(expandedEntry);

         if(!"cubeRoot".equals(expandedEntry.getProperty("entryName"))) {
            XRepository rep = XFactory.getRepository();
            Set<UserVariable> list = new HashSet<>();

            if("true".equals(expandedEntry.getProperty("CUBE_TABLE")) ||
               expandedEntry.isDataSource())
            {
               UserVariable[] vars = null;

               try {
                  vars = rep.getConnectionParameters(
                     assetRepository.getSession(), ":" + expandedEntry.getName());
               }
               catch(RemoteException re) {
                  //Expand the node directly if can't get connection parameters.
               }

               if(vars != null) {
                  Collections.addAll(list, vars);
               }
            }

            if(list.size() > 0) {
               UserVariable[] vars = list.toArray(new UserVariable[0]);
               AssetUtil.validateAlias(vars);
               List<VariableAssemblyModelInfo> parameters = Arrays.stream(vars)
                  .filter(v -> SUtil.isNeedPrompt(principal, v))
                  .map(VariableAssemblyModelInfo::new)
                  .collect(Collectors.toList());

               if(parameters.size() > 0) {
                  result = LoadAssetTreeNodesValidator.builder()
                     .parameters(parameters)
                     .treeNodeModel(TreeNodeModel.builder().build())
                     .build();
                  return result;
               }
            }

            AssetEntry[] children;

            if(reportRepositoryEnabled &&
               (expandedEntry.getScope() == AssetRepository.REPOSITORY_SCOPE ||
                expandedEntry.getScope() == AssetRepository.REPORT_SCOPE))
            {
               children = getReportRepositoryChildren(expandedEntry, principal);
            }
            else if(event.loadAll() && (expandedEntry.isTable() || expandedEntry.isPhysicalTable())) {
               children = new AssetEntry[] {};
            }
            else {
               children = getChildren(expandedEntry, principal, assetSelector);
            }

            if(expandedEntry.isRoot() && expandedEntry.getScope() == AssetRepository.GLOBAL_SCOPE) {
               if(expandedEntry.getType() == AssetEntry.Type.FOLDER) {
                  TreeNodeModel userWSRootModel =
                     createUserWorksheetRoot(principal, includeDatasources, includeColumns,
                                             includeWorksheets, includeViewsheets,
                                             includeTableStyles, includeScripts, includeLibrary,
                                             reportRepositoryEnabled, readOnly,
                                             physical, event);
                  updatedChildren.add(userWSRootModel);
               }
               else if(expandedEntry.getType() == AssetEntry.Type.REPOSITORY_FOLDER) {
                  TreeNodeModel userVSRootModel =
                     createUserViewsheetRoot(principal, includeDatasources, includeColumns,
                                             includeWorksheets, includeViewsheets,
                                             reportRepositoryEnabled,
                                             readOnly, physical, event);
                  updatedChildren.add(userVSRootModel);
               }
            }

            for(AssetEntry ae : children) {
               if("Recycle Bin".equals(ae.getName()) || !RecycleUtils.isInRecycleBin(ae.getPath()))
               {
                  getSubEntries(assetRepository, principal, atmNode, ae,
                                new ArrayList<>(Arrays.asList(entries)), assetSelector,
                                ResourceAction.READ);
               }
            }
         }

         // Is root datasources folder
         if(expandedEntry.getScope() == AssetRepository.QUERY_SCOPE &&
            "/".equals(expandedEntry.getPath()) && includeColumns)
         {
            appendCubes(atmNode, principal);
         }

         if(includeColumns) {
            treeNodeModel = convertToTreeNodeModel(atmNode, catalog, sqlEnabled);
         }
         else {
            treeNodeModel = convertToTreeNodeModel(atmNode, catalog,
                                            AssetTreeController::isLeafNode, sqlEnabled);
         }
      }

      // Expand and populate children nodes
      for(TreeNodeModel child : treeNodeModel.children()) {
         AssetEntry childEntry = (AssetEntry) child.data();

         if(RecycleUtils.isInRecycleBin(childEntry.getPath())) {
            continue;
         }

         LoadAssetTreeNodesEvent childEvent = null;
         Optional<LoadAssetTreeNodesEvent> childEventOptional = event.expandedDescendants()
            .stream().filter((e) -> e.targetEntry() != null &&
               e.targetEntry().compareTo(childEntry) == 0).findFirst();

         if(childEventOptional.isPresent()) {
            childEvent = childEventOptional.get();
         }
         else if(event.loadAll()) {
            childEvent = LoadAssetTreeNodesEvent.builder().from(event)
               .targetEntry(childEntry)
               .loadAll(true)
               .build();
         }

         if(childEvent != null && child.children().isEmpty() && !child.leaf()) {
            child = TreeNodeModel.builder().from(child)
               .addAllChildren(getNodes(includeDatasources, includeColumns,
                                        includeWorksheets, includeViewsheets,
                                        includeTableStyles, includeScripts, includeLibrary,
                                        reportRepositoryEnabled, readOnly,
                                        physical, childEvent, principal)
                                  .treeNodeModel().children())
               .expanded(childEventOptional.isPresent())
               .build();
         }
         else if(childEvent != null && event.loadAll() && childEntry != null &&
            childEntry.isDataSourceFolder())
         {
            child = loadAllChildren(includeDatasources, includeColumns, includeWorksheets,
               includeViewsheets, includeTableStyles, includeScripts, includeLibrary, reportRepositoryEnabled, readOnly,
               physical, child, childEvent, principal,
               assetEntry -> assetEntry != null && assetEntry.isDataSourceFolder(),
               assetEntry -> assetEntry != null && assetEntry.isDataSource());
         }
         else if(childEvent != null && event.loadAll() && childEntry != null &&
            childEntry.isDataModelFolder())
         {
            child = loadAllChildren(includeDatasources, includeColumns, includeWorksheets,
               includeViewsheets, includeTableStyles, includeScripts, includeLibrary,
               reportRepositoryEnabled, readOnly,
               physical, child, childEvent, principal,
               assetEntry -> assetEntry != null && assetEntry.isDataModelFolder(),
               assetEntry -> assetEntry != null && assetEntry.isLogicModel());
         }

         updatedChildren.add(child);
      }

      // Expand children nodes based on path
      if(event.path() != null && event.index() + 1 < event.path().length &&
         event.path().length > 0)
      {
         for(TreeNodeModel child : updatedChildren) {
            final AssetEntry childEntry = (AssetEntry) child.data();
            final boolean pubRoot = childEntry.isRoot() && childEntry.isWorksheetFolder() &&
               childEntry.isFolder();

            if(!childEntry.isFolder()) {
               continue;
            }
            else if(event.index() == -1) {
               boolean isEntryInPath = false;

               // private ws/vs is under private folder below public root, force public
               // root to be expanded
               // Tablestyles should also be public
               if((pubRoot && event.scope() == AssetRepository.USER_SCOPE) ||
                  childEntry.isTableStyleFolder() || childEntry.isScriptFolder())
               {
                  isEntryInPath = true;
               }
               else if(assetRepository.supportsScope(childEntry.getScope())) {
                  AssetEntry[] grandchildren = null;

                  if(childEntry != null &&
                     (childEntry.getScope() == AssetRepository.REPOSITORY_SCOPE ||
                        childEntry.getScope() == AssetRepository.REPORT_SCOPE))
                  {
                     grandchildren = getReportRepositoryChildren(childEntry, principal);
                  }
                  else {
                     grandchildren = assetRepository.getEntries(
                        childEntry, principal, ResourceAction.READ, assetSelector);
                  }

                  for(AssetEntry grandchild : grandchildren) {
                     if(grandchild.getName().equals(event.path()[0])) {
                        isEntryInPath = true;
                        break;
                     }
                  }
               }

               if(!isEntryInPath) {
                  continue;
               }
            }
            else if(child.label() == null || !child.label().equals(event.path()[event.index()])) {
               continue;
            }

            LoadAssetTreeNodesEvent childEvent = LoadAssetTreeNodesEvent.builder().from(event)
               .targetEntry(childEntry)
               .scope(event.scope())
               // if private folder, start from the root of private folder (don't +1 to index)
               .index(pubRoot && event.scope() == AssetRepository.USER_SCOPE ? -1
                      : event.index() + 1)
               .build();

            String path = String.join("/", event.path());
            boolean selectedNodeRetrieved = child.children().stream()
               .anyMatch(node -> ((AssetEntry) node.data()).getPath().equals(path));
            boolean foundChild = false;

            for(TreeNodeModel node : child.children()) {
               if(path.startsWith(((AssetEntry) node.data()).getPath())) {
                  foundChild = true;
                  break;
               }
            }

            if(!selectedNodeRetrieved && (foundChild || child.children().isEmpty())) {
               List<TreeNodeModel> childNodes = getNodes(
                  includeDatasources, includeColumns, includeWorksheets,
                  includeViewsheets, includeTableStyles, includeScripts, includeLibrary,
                  reportRepositoryEnabled, readOnly, physical, childEvent, principal)
                  .treeNodeModel().children();

               child = TreeNodeModel.builder().from(child)
                  .children(childNodes)
                  .expanded(true)
                  .build();
            }
            else {
               child = TreeNodeModel.builder().from(child)
                  .expanded(true)
                  .build();
            }

            boolean replace = false;

            for(int i = 0; i < updatedChildren.size(); i++) {
               TreeNodeModel tnode = updatedChildren.get(i);

               if(Tool.equals(tnode.label(), child.label()) &&
                  Tool.equals(tnode.data(), childEntry))
               {
                  updatedChildren.set(i, child);
                  replace = true;
                  break;
               }
            }

            if(!replace) {
               updatedChildren.add(child);
            }
         }
      }

      treeNodeModel = TreeNodeModel.builder()
         .from(treeNodeModel)
         .children(updatedChildren)
         .build();

      result = LoadAssetTreeNodesValidator.builder().treeNodeModel(treeNodeModel).build();

      return result;
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
                     XTableStyle style = manager.getTableStyle(asset.getName());
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

   private TreeNodeModel loadAllChildren(boolean includeDatasources, boolean includeColumns,
                                         boolean includeWorksheets, boolean includeViewsheets,
                                         boolean includeTableStyles, boolean includeScripts, boolean includeLibrary,
                                         boolean reportRepositoryEnabled,
                                         boolean readOnly, boolean physical,
                                         TreeNodeModel treeNodeModel, LoadAssetTreeNodesEvent event,
                                         Principal principal,
                                         Function<AssetEntry, Boolean> folderMatcher,
                                         Function<AssetEntry, Boolean> assetMatcher)
           throws Exception
   {
      AssetEntry entry = (AssetEntry) treeNodeModel.data();

      if(entry == null || folderMatcher == null || assetMatcher == null ||
         !folderMatcher.apply(entry))
      {
         return treeNodeModel;
      }

      List<TreeNodeModel> updatedChildren = new ArrayList<>();

      for(TreeNodeModel child : treeNodeModel.children()) {
         AssetEntry centry = (AssetEntry) child.data();
         TreeNodeModel updateChild = child;

         if(folderMatcher.apply(centry)) {
            updateChild = loadAllChildren(includeDatasources, includeColumns, includeWorksheets,
                    includeViewsheets, includeTableStyles, includeScripts, includeLibrary, reportRepositoryEnabled,
                    readOnly, physical, child, event, principal,
                    folderMatcher, assetMatcher);
         }
         else if(assetMatcher.apply(centry)) {
            if(child.children().isEmpty() && !child.leaf()) {
               LoadAssetTreeNodesEvent childEvent = LoadAssetTreeNodesEvent.builder().from(event)
                       .targetEntry(centry)
                       .loadAll(true)
                       .build();

               updateChild = TreeNodeModel.builder().from(child)
                       .addAllChildren(getNodes(includeDatasources, includeColumns, includeWorksheets,
                               includeViewsheets, includeTableStyles, includeScripts, includeLibrary,
                               reportRepositoryEnabled, readOnly,
                               physical, childEvent, principal)
                               .treeNodeModel().children())
                       .expanded(false)
                       .build();
            }
         }

         updatedChildren.add(updateChild);
      }

      return TreeNodeModel.builder()
              .from(treeNodeModel)
              .children(updatedChildren)
              .build();
   }

   private AssetEntry[] getChildren(
      AssetEntry expandedEntry, Principal principal,
      AssetEntry.Selector assetSelector) throws Exception
   {
      return assetRepository.getEntries(
         expandedEntry, principal, ResourceAction.READ, assetSelector);
   }

   private AssetEntry[] getReportRepositoryChildren(
      AssetEntry expandedEntry, Principal principal) throws Exception
   {
      final AssetEntry.Selector repositorySelector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET
      );
      final int[] repositoryScope = new int[] {
         AssetRepository.REPORT_SCOPE,
         AssetRepository.REPOSITORY_SCOPE
      };

      return null;
   }

   private void appendCubes(AssetTreeModel.Node rootNode,
      Principal principal) throws Exception
   {
      List<AssetTreeModel.Node> cubes = AssetEventUtil.getCubes(principal, null, null);

      if(cubes == null || cubes.size() == 0) {
         return;
      }

      Collections.sort(cubes, new AssetEventUtil.NodeComparator());
      AssetEntry cubesEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.FOLDER, Catalog.getCatalog().getString("Cubes"), null);
      cubesEntry.setProperty("localStr", Catalog.getCatalog().getString("Cubes"));
      cubesEntry.setProperty("entryName", "cubeRoot");
      AssetTreeModel.Node cubesRootNode = new AssetTreeModel.Node(cubesEntry);
      cubes.forEach((cube) -> cubesRootNode.addNode(cube));
      rootNode.addNode(cubesRootNode);
   }

   private void addDataSourceRootNodes(List<TreeNodeModel> children, IdentityID user,
                                       Principal principal)
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, "/", user);
      Catalog catalog = Catalog.getCatalog();
      children.add(createNodeFromEntry(entry, catalog.getString("Data Source")));
   }

   private void addWorksheetRootNodes(List<TreeNodeModel> children, IdentityID user,
                                      Principal principal)
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
         "/", user);
      Catalog catalog = Catalog.getCatalog();
      children.add(createNodeFromEntry(entry, catalog.getString("Global Worksheet")));
   }

   private TreeNodeModel createUserWorksheetRoot(Principal principal,
      boolean includeDatasources, boolean includeColumns, boolean includeWorksheets,
      boolean includeViewsheets, boolean includeTableStyles, boolean includeScripts, boolean includeLibrary,
      boolean reportRepositoryEnabled,
      boolean readOnly, boolean physical, LoadAssetTreeNodesEvent event)
      throws Exception
   {
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry userWSRootEntry = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER,
         "/", user);
      TreeNodeModel userWSRootModel =
         createNodeFromEntry(userWSRootEntry, Catalog.getCatalog().getString("User Worksheet"));

      if(event.loadAll()) {
         LoadAssetTreeNodesEvent childEvent = LoadAssetTreeNodesEvent.builder().from(event)
            .targetEntry(userWSRootEntry)
            .loadAll(true)
            .build();

         userWSRootModel = TreeNodeModel.builder().from(userWSRootModel)
            .addAllChildren(getNodes(includeDatasources, includeColumns, includeWorksheets,
                                     includeViewsheets, includeTableStyles, includeScripts, includeLibrary,
                                     reportRepositoryEnabled, readOnly,
                                     physical, childEvent, principal)
                               .treeNodeModel().children())
            .build();
      }

      return userWSRootModel;
   }

   private void addViewsheetRootNodes(List<TreeNodeModel> children, IdentityID user,
                                      Principal principal)
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         "/", user);
      Catalog catalog = Catalog.getCatalog();

      children.add(createNodeFromEntry(entry, catalog.getString("Global Viewsheet")));
   }

   private void addLibraryRootNodes(List<TreeNodeModel> children, IdentityID user,
                                    Principal principal)
   {
      AssetEntry entry = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.LIBRARY_FOLDER, "/", user);
      children.add(createNodeFromEntry(entry, catalog.getString("Library")));
   }

   private void addTableStyleRootNodes(List<TreeNodeModel> children, Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry entry = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.TABLE_STYLE_FOLDER,
         "/" + TABLE_STYLE, principal == null ? null : pId);

      children.add(createNodeFromEntry(entry, catalog.getString("Table Styles")));
   }

   private void addScriptRootNodes(List<TreeNodeModel> children, Principal principal) {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry entry = new AssetEntry(
         AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT_FOLDER,
         "/" + SCRIPT, principal == null ? null : pId);

      children.add(createNodeFromEntry(entry, catalog.getString("Script Function")));
   }

   private TreeNodeModel createUserViewsheetRoot(
      Principal principal, boolean includeDatasources, boolean includeColumns,
      boolean includeWorksheets, boolean includeViewsheets, boolean reportRepositoryEnabled,
      boolean readOnly, boolean physical, LoadAssetTreeNodesEvent event)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry userVSRootEntry = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         "/", pId);
      TreeNodeModel userVSRootModel =
         createNodeFromEntry(userVSRootEntry, Catalog.getCatalog().getString("User Viewsheet"));

      if(event.loadAll()) {
         LoadAssetTreeNodesEvent childEvent = LoadAssetTreeNodesEvent.builder().from(event)
            .targetEntry(userVSRootEntry)
            .loadAll(true)
            .build();

         userVSRootModel = TreeNodeModel.builder().from(userVSRootModel)
            .addAllChildren(getNodes(includeDatasources, includeColumns, includeWorksheets,
                                     includeViewsheets, false, false, false,
                                     reportRepositoryEnabled, readOnly,
                                     physical, childEvent, principal)
                               .treeNodeModel().children())
            .build();
      }

      return userVSRootModel;
   }

   private static TreeNodeModel convertToTreeNodeModel(AssetTreeModel.Node node, Catalog catalog, boolean sqlEnabled) {
      return convertToTreeNodeModel(node, catalog, null, sqlEnabled);
   }

   public static TreeNodeModel convertToTreeNodeModel(AssetTreeModel.Node node,
                                                      Catalog catalog,
                                                      Function<AssetEntry, Boolean> leafFn, boolean sqlEnabled)
   {
      AssetTreeModel.Node[] nodes = node.getNodes();
      List<TreeNodeModel> children = new ArrayList<TreeNodeModel>();

      for(int i = 0; i < nodes.length; i++) {
         AssetEntry entry = nodes[i].getEntry();

         if(entry == null || entry.getPath() == null ||
            // composer shouldn't show built-in folder
            !entry.getPath().startsWith("Built-in Admin Reports"))
         {
            String emptyName = null;

            if("".equals(entry.toString()) &&
               (node.getEntry().isWorksheet() || node.getEntry().isQuery()))
            {
               emptyName = "Column[" + i +"]";
            }

            TreeNodeModel child = createChildNode(nodes[i], catalog, leafFn, sqlEnabled, emptyName);
            children.add(child);
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .leaf(leafFn == null ? children.isEmpty() : leafFn.apply(node.getEntry()))
         .build();
   }

   private static TreeNodeModel createChildNode(AssetTreeModel.Node node,
                                                Catalog catalog,
                                                Function<AssetEntry, Boolean> leafFn,
                                                boolean sqlEnabled, String emptyName)
   {
      final Function<AssetEntry, Boolean> isLeaf =
         leafFn == null ? AssetTreeController::isLeaf : leafFn;

      List<TreeNodeModel> children = Arrays.stream(node.getNodes())
         .map((n) -> createChildNode(n, catalog, isLeaf, sqlEnabled, null))
         .collect(Collectors.toList());

      AssetEntry entry = node.getEntry();
      String label = AssetUtil.getEntryLabel(entry, catalog);
      entry.setProperty("sqlEnabled", String.valueOf(sqlEnabled));

      return createNodeFromEntry(
         node.getEntry(), "".equals(label) ? emptyName : label, children, isLeaf);
   }

   public static AssetEntry[] getFilterFor(AssetEntry parentEntry) {
      AssetEntry temp = parentEntry;
      Deque<AssetEntry> stack = new ArrayDeque<>();

      while(temp != null) {
         stack.push(temp);
         temp = temp.getParent();
      }

      return stack.toArray(new AssetEntry[0]);
   }

   /**
    * Method for determining whether a given AssetEntry is a leaf node/
    *
    * @param entry an AssetEntry
    * @return true if entry is a leaf, false otherwise
    */
   public static boolean isLeaf(AssetEntry entry) {
      if(entry.getType() == AssetEntry.Type.WORKSHEET) {
         String wsType = entry.getProperty(AssetEntry.WORKSHEET_TYPE);
         int type = wsType == null ? 0 : Integer.parseInt(wsType);

         return type == Worksheet.VARIABLE_ASSET || type == Worksheet.NAMED_GROUP_ASSET;
      }

      return entry.getType() == AssetEntry.Type.VIEWSHEET
         || entry.getType() == AssetEntry.Type.COLUMN
         || entry.getType() == AssetEntry.Type.PHYSICAL_COLUMN
         || entry.getType() == AssetEntry.Type.WORKSHEET
         || entry.getType() == AssetEntry.Type.TABLE_STYLE
         || entry.getType() == AssetEntry.Type.SCRIPT;
   }

   /**
    * Method for determining whether a given AssetEntry is a leaf node, in the case where
    * we don't want to show details/columns (ie. picking data source for viewsheet).
    *
    * @param entry an AssetEntry
    * @return true if entry is a leaf, false otherwise
    */
   public static boolean isColumnParent(AssetEntry entry) {
      return entry.getType() == AssetEntry.Type.TABLE
         || entry.getType() == AssetEntry.Type.PHYSICAL_TABLE
         || entry.getType() == AssetEntry.Type.WORKSHEET
         || entry.getType() == AssetEntry.Type.LOGIC_MODEL
         || entry.getType() == AssetEntry.Type.QUERY;
   }

   public static boolean isLeafNode(AssetEntry entry) {
      return AssetTreeController.isColumnParent(entry) || entry.isViewsheet() ||
         entry.isTableStyle() || entry.isScript();
   }

   private static TreeNodeModel createNodeFromEntry(AssetEntry entry, String label) {
      return createNodeFromEntry(
         entry, label, Collections.emptyList(), AssetTreeController::isLeaf);
   }

   private static TreeNodeModel createNodeFromEntry(AssetEntry entry, String label,
                                                    List<TreeNodeModel> children,
                                                    Function<AssetEntry, Boolean> leafFn)
   {
      return TreeNodeModel.builder()
         .label(label)
         .data(entry)
         .dataLabel(entry.toIdentifier())
         .dragName(entry.getType().name().toLowerCase())
         .leaf(leafFn.apply(entry))
         .children(children)
         .tooltip(entry.getProperty("Tooltip"))
         .materialized(getMaterialized(entry))
         .build();
   }

   public static boolean getMaterialized(AssetEntry entry) {
      if(entry == null) {
         return false;
      }

      return MVManager.getManager().isMaterialized(entry.toIdentifier(), !entry.isViewsheet());
   }

   // TODO: This method should be removed at some point and replaced with something more fitting.
   // Copied from AssetEventUtil to properly resolve complicated things like Query Repository.

   /**
    * Get the sub entries.
    *
    * @param engine   the specified asset repository.
    * @param user     the specified principal.
    * @param parent   the specified parent node.
    * @param entry    current node.
    * @param filter   the specified entry filter.
    * @param selector the specified selector.
    */
   public static void getSubEntries(AssetRepository engine, Principal user,
      AssetTreeModel.Node parent, AssetEntry entry,
      ArrayList<AssetEntry> filter,
      AssetEntry.Selector selector,
      ResourceAction perm)
      throws Exception
   {
      AssetTreeModel.Node curNode = new AssetTreeModel.Node(entry);
      String folder = entry.getProperty("folder");

      if(folder != null && !entry.isTableStyleFolder() && !entry.isTableStyle()) {
         AssetEntry.Type folderType = AssetEntry.Type.FOLDER;

         if(entry.isQuery()) {
            folderType = AssetEntry.Type.QUERY_FOLDER;
         }
         else if(entry.isLogicModel()) {
            folderType = AssetEntry.Type.DATA_MODEL_FOLDER;
         }

         AssetEntry fentry = new AssetEntry(entry.getScope(), folderType, folder, null);
         fentry.copyProperties(entry);
         AssetTreeModel.Node fnode = parent.getNode(fentry);

         if(fnode == null) {
            parent.addNode(fnode = new AssetTreeModel.Node(fentry));
         }

         fnode.setRequested(true);
         fnode.addNode(curNode);
      }
      else if(parent.getNodeByEntry(entry) == null) {
         parent.addNode(curNode);

         if(entry.isDataSourceFolder()) {
            AssetEntry[] entries =
               engine.getEntries(entry, user, perm, selector);
            curNode.setRequested(true);

            for(AssetEntry ae : entries) {
               if(!ae.isRoot() && "".equals(ae.getName())) {
                  continue;
               }

               getSubEntries(engine, user, curNode, ae, filter,
                  selector, perm);
            }
         }
      }

      if(filter.contains(entry)) {
         curNode.setRequested(true);
         AssetEntry[] entries =
            engine.getEntries(entry, user, perm, selector);

         if(!entry.isLogicModel() && !entry.isTable()) {
            Arrays.sort(entries, new EntryComparator());
         }

         for(AssetEntry ae : entries) {
            if((!ae.isRoot() && "".equals(ae.getName()))
               || "Recycle Bin".equals(ae.getName()))
            {
               continue;
            }

            getSubEntries(engine, user, curNode, ae, filter, selector, perm);
         }
      }
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private static final String TABLE_STYLE = "Table Style";
   private static final String SCRIPT = "Script Function";
   private static final Catalog catalog = Catalog.getCatalog();

   private static class EntryComparator implements Comparator<AssetEntry> {
      @Override
      public int compare(AssetEntry e1, AssetEntry e2) {
         int s1 = assetScore(e1);
         int s2 = assetScore(e2);

         if(s1 != s2) {
            return s1 - s2;
         }

         if(e1.getType() != AssetEntry.Type.QUERY) {
            return e1.compareTo(e2);
         }

         String p1 = e1.getParentPath();
         String p2 = e2.getParentPath();

         if(isEmpty(p1) && isEmpty(p2)) {
            return e1.compareTo(e2);
         }

         if(isEmpty(p1) || isEmpty(p2)) {
            return isEmpty(p1) ? -1 : 1;
         }

         String[] folders1 = p1.split("/");
         String[] folders2 = p2.split("/");
         int len = Math.min(folders1.length, folders2.length);

         for(int i = 0; i < len; i++) {
            int res = folders1[i].compareTo(folders2[i]);

            if(res != 0) {
               return res;
            }
         }

         if(folders1.length != folders2.length) {
            return folders1.length > folders2.length ? -1 : 1;
         }

         return e1.compareTo(e2);
      }

      private boolean isEmpty(String str) {
         return str == null || "".equals(str.trim());
      }

      private int assetScore(AssetEntry entry) {
         switch(entry.getType()) {
            case DATA_SOURCE_FOLDER:
               return 0;
            case DATA_SOURCE:
               return 10;
            case PHYSICAL_FOLDER:
               return 20;
            case LOGIC_MODEL:
               return 30;
            case FOLDER:
               return 40;
            case PHYSICAL_TABLE:
               return 50;
            case PHYSICAL_COLUMN:
               return 60;
            case TABLE:
               return 70;
            case COLUMN:
               return 80;
            case QUERY:
               return 90;
         }

         return 100;
      }
   }
}
