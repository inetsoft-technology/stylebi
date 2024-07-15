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
package inetsoft.web.portal.controller.database;

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.util.data.CommonKVModel;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.AutoDrillWorksheetParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class DataAutoDrillController {

   @GetMapping("api/portal/data/autodrill/worksheet/params")
   public AutoDrillWorksheetParameters collectParameters(@RequestParam("wsIdentifier") String wsIdentifier, XPrincipal user)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(wsIdentifier);
      AbstractSheet sheet = engine.getSheet(entry, user, true, AssetContent.ALL);

      if(sheet == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      Worksheet ws = (Worksheet) sheet;
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      VariableTable variableTable = box.getVariableTable();
      UserVariable[] vars = AssetEventUtil.executeVariables(
         null, box, variableTable, null, null, null, variableTable, true);
      List<String> list = new ArrayList<>();

      for(int i = 0; vars != null && i < vars.length ; i++) {
         list.add(vars[i].getName());
      }

      return new AutoDrillWorksheetParameters(list);
   }

   @GetMapping("api/portal/data/autodrill/worksheet/fields")
   public List<String> getWorksheetFields(@RequestParam("wsIdentifier") String wsIdentifier,
                                           Principal principal)
      throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(wsIdentifier);
      boolean permission = false; // already checked permission when load select worksheet dialog.
      AbstractSheet sheet = engine.getSheet(entry, principal, permission, AssetContent.ALL);

      if(sheet == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      Worksheet ws = (Worksheet) sheet;
      WSAssembly assembly = ws.getPrimaryAssembly();

      if(!(assembly instanceof TableAssembly)) {
         return null;
      }

      TableAssembly table = (TableAssembly) assembly;
      ColumnSelection columns = table.getColumnSelection(true);

      if(columns == null || columns.getAttributeCount() == 0) {
         return null;
      }

      List<String> list = new ArrayList<>();
      columns.stream().forEach(col -> list.add(col.getName()));

      return list;
   }

   private TreeNodeModel createTreeNode(HashMap<String, TreeSet<String>> queryMap, XRepository repository)
      throws Exception
   {
      TreeNodeModel root = TreeNodeModel.builder()
         .label("root")
         .children(createChildNode(queryMap, "/", repository))
         .build();

      return root;
   }

   private List<TreeNodeModel> createChildNode(HashMap<String, TreeSet<String>> queryMap, String ppath,
                                               XRepository repository)
      throws Exception
   {
      Set<String> keySet = queryMap.keySet();
      boolean match = keySet.stream().anyMatch((key) -> key.startsWith(ppath + "/"));
      List<TreeNodeModel> nodes = new ArrayList<>();

      if(!match && !Tool.equals("/", ppath)) {
         Iterator<String> iterator = queryMap.get(ppath).iterator();

         while(iterator.hasNext()) {
            String next = iterator.next();
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
               AssetEntry.Type.QUERY, ppath + "/" + next, null);
            nodes.add(TreeNodeModel.builder()
               .label(next)
               .data(entry)
               .icon("db-table-icon")
               .leaf(true)
               .build());
         }

         return nodes;
      }

      if(queryMap == null || queryMap.size() == 0) {
         return nodes;
      }

      Iterator<String> iterator = queryMap.get(ppath).iterator();

      while(iterator.hasNext()) {
         String name = iterator.next();
         String nodePath = Tool.equals("/", ppath) ? name : ppath + "/" + name;
         List<TreeNodeModel> childNodes = createChildNode(queryMap, nodePath, repository);
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_SOURCE, nodePath, null);
         TreeNodeModel nodeModel = TreeNodeModel.builder()
            .label(name)
            .data(entry)
            .children(childNodes)
            .icon(dataSourceIcon(nodePath, repository))
            .build();
         nodes.add(nodeModel);
      }

      return nodes;
   }

   private String dataSourceIcon(String nodePath, XRepository repository)
      throws Exception
   {
      XDataSource dataSource = repository.getDataSource(nodePath);

      return dataSource != null ? ContentRepositoryTreeService.getDataSourceIconClass(dataSource.getType()) :
         "folder-icon";
   }

   private void createFolderMap(List<String> fnames, HashMap<String, TreeSet<String>> folders) {
      Iterator<String> iterator = fnames.iterator();

      while(iterator.hasNext()) {
         String fname = iterator.next();
         createFolderMap0(fname, 0, folders);
      }
   }

   private void createFolderMap0(String fname, int index, HashMap<String, TreeSet<String>> folders) {
      String[] fpaths = fname.split("/");

      if(index > fpaths.length - 1 || fpaths.length == 1) {
         return;
      }

      String key = index == 0 ? "/" : null;

      for(int i = 0; i < index && i <= fpaths.length - 1; i++) {
         key = i == 0 ? fpaths[i] : key + "/" + fpaths[i];
      }

      if(key == null) {
         return;
      }

      String value = index == 0 ? fpaths[0] : fpaths[index];

      if(Tool.isEmptyString(value)) {
         return;
      }

      folders.compute(key, (k, v) -> {
         if(v == null) {
            v = new TreeSet<>();
         }

         v.add(value);

         return v;
      });

      createFolderMap0(fname, index + 1, folders);
   }

   private XRepository getXRepository() throws Exception {
      return XFactory.getRepository();
   }

   @Autowired
   private AssetRepository engine;
   private static final Logger LOG = LoggerFactory.getLogger(DataAutoDrillController.class.getName());
}
