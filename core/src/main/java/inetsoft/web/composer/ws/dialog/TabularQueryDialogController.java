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
package inetsoft.web.composer.ws.dialog;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import jakarta.servlet.http.HttpServletRequest;
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

@Controller
public class TabularQueryDialogController extends WorksheetController {
   @Autowired
   public TabularQueryDialogController(TabularQueryDialogServiceProxy dialogServiceProxy) {
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @GetMapping("/api/composer/ws/tabular-query-dialog-model")
   @ResponseBody
   public TabularQueryDialogModel getModel(@RequestParam("runtimeId") String runtimeId,
      @RequestParam(name = "tableName", required = false) String tableName,
      @RequestParam(name = "dataSource", required = false) String dataSource,
      Principal principal) throws Exception
   {
      return dialogServiceProxy.getModel(runtimeId, tableName, dataSource, principal);
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
         if(runtimeId != null) {
            VariableTable vars = dialogServiceProxy.updateVariableTable(runtimeId, principal);

            if(vars != null) {
               query.setVariableTable(vars);
            }
         }

         if(tabularView == null) {
            LayoutCreator layoutCreator = new LayoutCreator();
            tabularView = layoutCreator.createLayout(query);
         }

         List<String> records = dialogServiceProxy.getThreadRecords(runtimeId, tableName, principal);
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
      dialogServiceProxy.setModel(super.getRuntimeId(), model, principal, commandDispatcher);
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

   private TabularQueryDialogServiceProxy dialogServiceProxy;
}
