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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TabularQueryDialogController extends WorksheetController {
   @Autowired
   public TabularQueryDialogController(SecurityEngine securityEngine, XRepository repository) {
      this.securityEngine = securityEngine;
      this.repository = repository;
   }

   @GetMapping("/api/composer/ws/tabular-query-dialog-model")
   @ResponseBody
   public TabularQueryDialogModel getModel(@RequestParam("runtimeId") String runtimeId,
      @RequestParam(name = "tableName", required = false) String tableName,
      @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      Worksheet ws = getWorksheetEngine().getWorksheet(runtimeId, principal).getWorksheet();
      TabularView tabularView = null;
      final String queryType;

      if(tableName != null) {
         TabularTableAssembly assembly = (TabularTableAssembly) ws.getAssembly(tableName);
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         TabularQuery query = info.getQuery();
         LayoutCreator layoutCreator = new LayoutCreator();
         tabularView = layoutCreator.createLayout(query);
         queryType = query.getType();

         if(query.getDataSource() != null) {
            dataSource = query.getDataSource().getFullName();
         }

         List<String> records = getThreadRecords(runtimeId, tableName, principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
      }
      else {
         queryType = "";
      }

      XDataSource currentDS = repository.getDataSource(dataSource);
      String[] dataSourceNames = repository.getDataSourceFullNames();

      List<String> datasources = Arrays.stream(dataSourceNames)
         .sorted()
         .filter(dsname -> {
            try {
               XDataSource ds = repository.getDataSource(dsname);
               boolean sameClass = (currentDS != null && currentDS.getClass().equals(ds.getClass()));
               boolean sameType = (currentDS == null && queryType.equals(ds.getType()));
               return securityEngine.checkPermission(
                  principal, ResourceType.DATA_SOURCE, dsname, ResourceAction.READ) &&
                  (sameClass || sameType);
            }
            catch(Exception e) {
               LOG.debug("Datasource not available for dialog {}", dsname, e);
               return false;
            }
         })
         .collect(Collectors.toList());

      TabularQueryDialogModel model = new TabularQueryDialogModel();
      model.setDataSource(dataSource);
      model.setDataSources(datasources);
      model.setTableName(tableName);
      model.setTabularView(tabularView);
      return model;
   }

   /**
    * Refreshes a tabular view.
    *
    * @return updated tabular view
    */
   @PostMapping("/api/composer/ws/tabular-query-dialog/refreshView")
   @ResponseBody
   public TabularView refreshTabularView(@RequestBody(required = false) TabularView tabularView,
                   @RequestParam("dataSource") String dataSource,
                   @RequestParam(name = "runtimeId", required = false) String runtimeId,
                   @RequestParam(name = "tableName", required = false) String tableName,
                   Principal principal, HttpServletRequest request) throws Exception
   {
      TabularUtil.setSessionId(request.getSession().getId());
      TabularQuery query = TabularUtil.createQuery(dataSource);

      if(query != null) {
         if(tabularView == null) {
            LayoutCreator layoutCreator = new LayoutCreator();
            tabularView = layoutCreator.createLayout(query);
         }

         List<String> records = getThreadRecords(runtimeId, tableName, principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
      }

      return tabularView;
   }

   @PostMapping("/api/composer/ws/tabular-query-dialog/oauth-params")
   @ResponseBody
   public TabularOAuthParams getOAuthParameters(@RequestBody TabularQueryOAuthParamsRequest request,
                                                @RequestParam("dataSource") String dataSource,
                                                HttpServletRequest httpRequest)
   {
      TabularUtil.setSessionId(httpRequest.getSession().getId());
      TabularQuery query = TabularUtil.createQuery(dataSource);
      TabularOAuthParams.Builder builder = TabularOAuthParams.builder();

      String license = SreeEnv.getProperty("license.key");
      int index = license.indexOf(',');

      if(index >= 0) {
         license = license.substring(0, index);
      }

      builder.license(license);

      if(query != null) {
         TabularUtil.refreshView(request.view(), query);
         Map<String, String> params = TabularUtil.getOAuthParameters(
            request.user(), request.password(), request.clientId(), request.clientSecret(),
            request.scope(), request.authorizationUri(), request.tokenUri(), request.flags(),
            query);

         if(params != null) {
            builder
               .clientId(params.get("clientId"))
               .clientSecret(params.get("clientSecret"))
               .authorizationUri(params.get("authorizationUri"))
               .tokenUri(params.get("tokensUri"));

            String scope = params.get("scope");

            if(scope != null) {
               builder.addScope(scope.split(" "));
            }

            String flags = params.get("flags");

            if(flags != null) {
               builder.addFlags(flags.split(" "));
            }
         }
      }

      return builder.build();
   }

   @PostMapping("/api/composer/ws/tabular-query-dialog/oauth-tokens")
   @ResponseBody
   public TabularView setOAuthTokens(@RequestBody TabularQueryOAuthTokens tokens,
                                     @RequestParam("dataSource") String dataSource,
                                     HttpServletRequest request)
   {
      TabularUtil.setSessionId(request.getSession().getId());
      TabularQuery query = TabularUtil.createQuery(dataSource);

      if(query != null) {
         Tokens params = Tokens.builder()
            .accessToken(tokens.accessToken())
            .refreshToken(tokens.refreshToken())
            .issued(tokens.issued())
            .expiration(tokens.expiration())
            .scope(tokens.scope())
            .properties(tokens.properties())
            .build();
         TabularUtil.setOAuthTokens(params, query, tokens.method(), tokens.view());
      }

      return tokens.view();
   }

   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/ws/dialog/tabular-query-dialog-model")
   public void setModel(
      @Payload TabularQueryDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      String dataSource = model.getDataSource();
      TabularView tabularView = model.getTabularView();
      String name = model.getTableName();

      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      TabularTableAssembly assembly = name == null ? null :
         (TabularTableAssembly) ws.getAssembly(name);

      if(assembly == null) {
         name = model.getTableName() == null ?
            AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET) : model.getTableName();
         assembly = new TabularTableAssembly(ws, name);
         AssetEventUtil.adjustAssemblyPosition(assembly, ws);
         TabularQuery query = TabularUtil.createQuery(dataSource);
         List<String> records = getThreadRecords(rws.getID(), name, principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
         Exception ex = null;

         try {
            setUpTable(assembly, query, dataSource, rws);
         }
         catch(Exception ex0) {
            ex = ex0;
         }

         ws.addAssembly(assembly);
         WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);

         if(ex == null) {
            WorksheetEventUtil.loadTableData(rws, name, true, true);
         }

         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
      else {
         TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
         TabularQuery query = info.getQuery();
         XDataSource ds = null;

         try {
            if(dataSource != null) {
               ds = XFactory.getRepository().getDataSource(dataSource);
            }
         }
         catch(Exception e) {
            LOG.warn("Unable to update query datasource: " + query.getName(), e);
         }

         if(query.getDataSource() == null || ds != null &&
            (!query.getDataSource().getFullName().equals(ds.getFullName()) ||
               ds.getLastModified() > query.getDataSource().getLastModified()))
         {
            query.setDataSource(ds);
         }

         List<String> records = getThreadRecords(rws.getID(), model.getTableName(), principal);
         TabularUtil.refreshView(tabularView, query, records, principal);
         setUpTable(assembly, query, dataSource, rws);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      }

      WorksheetEventUtil.refreshColumnSelection(rws, name, true);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
      WorksheetEventUtil.refreshVariables(rws, super.getWorksheetEngine(), name, commandDispatcher);

      final UserMessage msg = CoreTool.getUserMessage();

      if(msg != null) {
         final MessageCommand messageCommand = MessageCommand.fromUserMessage(msg);
         messageCommand.setAssemblyName(assembly.getName());
         commandDispatcher.sendCommand(messageCommand);
      }
   }

   /**
    * Gets the tree node for the specified path
    *
    * @param property the name of the property in the bean.
    * @return tree node
    */
   @PostMapping("/api/composer/ws/tabular-query-dialog/browse")
   @ResponseBody
   public TreeNodeModel browse(
      @RequestParam(name = "dataSource") String dataSource,
      @RequestParam(name = "property") String property,
      @RequestParam(name = "path") String path,
      @RequestParam(name = "all") boolean all,
      @RequestBody TabularView tabularView)
   {
      TabularQuery query = TabularUtil.createQuery(dataSource);
      TreeNodeModel.Builder rootNodeBuilder = TreeNodeModel.builder();

      if(query != null) {
         TabularUtil.setValuesToBean(tabularView.getViews(), query,
            TabularUtil.getPropertyMap(query.getClass()));
         LayoutCreator layoutCreator = new LayoutCreator();
         tabularView = layoutCreator.createLayout(query);
         TabularUtil.callEditorMethods(tabularView.getViews(), query);

         TabularView view = getView(tabularView, property);
         assert view != null;
         TabularEditor editor = view.getEditor();
         String[] editorPropertyNames = editor.getEditorPropertyNames();
         String[] editorPropertyValues = editor.getEditorPropertyValues();
         String relativeTo = null;
         boolean foldersOnly = false;
         List<String> acceptTypes = new ArrayList<>();

         if(editorPropertyNames != null) {
            for(int i = 0; i < editorPropertyNames.length; i++) {
               String name = editorPropertyNames[i];

               if("relativeTo".equals(name)) {
                  relativeTo = editorPropertyValues[i];

                  if(relativeTo == null || relativeTo.isEmpty()) {
                     relativeTo = "/";
                  }
               }
               else if("foldersOnly".equals(name)) {
                  foldersOnly = Boolean.parseBoolean(editorPropertyValues[i]);
               }
               else if("acceptTypes".equals(name) && !all) {
                  acceptTypes.addAll(Arrays.asList(editorPropertyValues[i].split(",")));
               }
            }
         }

         boolean root = "/".equals(path);
         FileFilter filter = file -> {
            if(acceptTypes.isEmpty()) {
               return true;
            }

            if(!Files.isReadable(file.toPath())) {
               return false;
            }

            if(file.isHidden()) {
               return false;
            }

            if(file.isDirectory()) {
               return true;
            }

            for(String type : acceptTypes) {
               if(file.getName().endsWith(type)) {
                  return true;
               }
            }

            return false;
         };

         if(relativeTo != null) {
            List<TreeNodeModel> folderNodes = new ArrayList<>();
            List<TreeNodeModel> fileNodes = new ArrayList<>();
            // If the file explorer needs to start at a directory with all the
            // drives. Don't check if it's hidden since drives are always "hidden"
            if("/".equals(relativeTo) && "/".equals(path)) {
               for(File fileRoot : File.listRoots()) {
                  String fileName = fileRoot.getAbsolutePath();
                  TabularFileModel tabularFileModel = new TabularFileModel();
                  tabularFileModel.setFolder(true);
                  tabularFileModel.setPath(fileName.replace("\\", "/"));
                  tabularFileModel.setAbsolutePath(fileName);
                  TreeNodeModel treeNode = TreeNodeModel.builder()
                     .data(tabularFileModel)
                     .label(fileName)
                     .leaf(false)
                     .build();
                  folderNodes.add(treeNode);
               }
            }
            else {
               File[] list = FileSystemService.getInstance()
                  .getFile(relativeTo + "/" + path + "/")
                  .listFiles(filter);

               if(list != null) {
                  for(File file : list) {
                     if(file.isDirectory() || !foldersOnly) {
                        TabularFileModel tabularFileModel = new TabularFileModel();
                        tabularFileModel.setFolder(file.isDirectory());
                        tabularFileModel.setPath(root ? file.getName() : path + "/" + file.getName());
                        tabularFileModel.setAbsolutePath(file.getAbsolutePath());
                        TreeNodeModel treeNode = TreeNodeModel.builder()
                           .data(tabularFileModel)
                           .label(file.getName())
                           .leaf(!file.isDirectory())
                           .build();
                        folderNodes.add(treeNode);
                     }
                  }
               }
            }

            TabularFileModel tabularFileModel = new TabularFileModel();
            tabularFileModel.setFolder(true);
            tabularFileModel.setPath(path);
            rootNodeBuilder.data(tabularFileModel);
            rootNodeBuilder.addAllChildren(folderNodes);
            rootNodeBuilder.addAllChildren(fileNodes);
         }
      }

      return rootNodeBuilder.build();
   }

   private void setUpTable(TabularTableAssembly assembly,
      TabularQuery query, String dataSource, RuntimeWorksheet rws) throws Exception
   {
      TabularTableAssemblyInfo info = (TabularTableAssemblyInfo) assembly.getTableInfo();
      info.setQuery(query);
      SourceInfo sinfo = new SourceInfo(SourceInfo.DATASOURCE, dataSource, dataSource);
      info.setSourceInfo(sinfo);
      assembly.loadColumnSelection
         (rws.getAssetQuerySandbox().getVariableTable(), true,
          rws.getAssetQuerySandbox().getQueryManager());
   }

   /**
    * Gets the view that has the given property name
    *
    * @param view         root tabular view
    * @param propertyName property name
    * @return tabular view
    */
   private TabularView getView(TabularView view, String propertyName) {
      if(propertyName.equals(view.getValue())) {
         return view;
      }

      for(TabularView tView : view.getViews()) {
         TabularView result = getView(tView, propertyName);

         if(result != null) {
            return result;
         }
      }

      return null;
   }

   private ArrayList<String> getThreadRecords(String runtimeId, String tableName,
                                              Principal principal) throws Exception
   {
      ArrayList<String> records = new ArrayList<>();

      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread parentThread = (GroupedThread) Thread.currentThread();

         for(Object record : parentThread.getRecords()) {
            if(record instanceof String) {
               records.add((String) record);
            }
         }
      }
      else if(runtimeId != null){
         RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);

         records.add(LogContext.WORKSHEET.getRecord(rws.getEntry().getPath()));

         if(tableName != null) {
            records.add(LogContext.ASSEMBLY.getRecord(tableName));
         }

         records.add(LogContext.DASHBOARD.getRecord(rws.getEntry().getPath()));
      }

      return records;
   }

   private final SecurityEngine securityEngine;
   private final XRepository repository;
   private static final Logger LOG = LoggerFactory.getLogger(TabularQueryDialogController.class);
}
