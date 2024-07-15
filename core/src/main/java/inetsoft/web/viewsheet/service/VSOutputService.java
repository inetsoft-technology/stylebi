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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class VSOutputService {
   /**
    * Created a new instance of VSOutputService
    */
   @Autowired
   public VSOutputService(
      VSInputService vsInputService,
      ViewsheetService viewsheetService) {
      this.vsInputService = vsInputService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets an AssetTreeModel of the viewsheet. Mimic of GetVSTreeModelEvent
    * @param rvs                       the runtime viewsheet
    * @param prefix                    the prefix string
    * @param source                    the source string
    * @param showMeasures              if it should return measures
    * @param showDimensions            if it should return dimensions
    * @param onlyCube                  if it should return only cubes
    * @param supportVSAssemblySource   if it should get vs assembly source
    * @param principal                 the principal user
    * @return the asset tree model of output tables and cubes
    * @throws Exception if could not create the asset tree model
    */
   public AssetTreeModel getVSTreeModel(RuntimeViewsheet rvs, String prefix, String source,
                                        String showMeasures, String showDimensions, String onlyCube,
                                        String supportVSAssemblySource, Principal principal)
      throws Exception
   {
      return getVSTreeModel(rvs, prefix, source, showMeasures, showDimensions, onlyCube,
         supportVSAssemblySource, null, principal);
   }

   public AssetTreeModel getVSTreeModel(RuntimeViewsheet rvs, String prefix, String source,
                                        String showMeasures, String showDimensions, String onlyCube,
                                        String supportVSAssemblySource, String showCube,
                                        Principal principal)
      throws Exception
   {
      AssetTreeModel model = new AssetTreeModel();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return model;
      }

      Properties props = new Properties();
      props.setProperty("runtime", rvs.isRuntime() + "");
      props.setProperty("showVariables", "false");
      props.setProperty("excludeAggCalc", "true");

      if(prefix != null) {
         props.setProperty("prefix", prefix);
      }

      if(source != null) {
         props.setProperty("source", source);
      }

      if(showMeasures != null) {
         props.setProperty("showMeasures", showMeasures);
      }

      if(showDimensions != null) {
         props.setProperty("showDimensions", showDimensions);
      }

      if(onlyCube != null) {
         props.setProperty("onlyCube", onlyCube);
      }

      if(showCube != null) {
         props.setProperty("showCube", showCube);
      }

      model = VSEventUtil.refreshBaseWSTree(viewsheetService.getAssetRepository(),
                                            principal, vs, props);

      if(supportVSAssemblySource != null && "true".equals(supportVSAssemblySource)) {
         VSEventUtil.appendVSAssemblyTree(vs, model, principal);
      }

      return model;
   }

   /**
    * Get the output tables tree for data output pane.
    * @param rvs        Runtime viewsheet instnace
    * @param principal  Principal user
    * @return  the Tree of tables and cubes for data output pane
    */
   public TreeNodeModel getOutputTablesTree(RuntimeViewsheet rvs, Principal principal) {
      List<TreeNodeModel> treeChildren = new ArrayList<>();

      treeChildren.add(TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("None"))
         .data("")
         .leaf(true)
         .build());

      Map<String, List<String>> lists = getOutputTables(rvs, principal);

      if(lists != null) {
         List<String> tables = lists.get("tables");
         List<String> cubeLabels = lists.get("cubeLabels");
         List<String> cubeData = lists.get("cubeData");
         List<String> description = lists.get("description");
         AssetEntry baseEntry = rvs.getViewsheet().getBaseEntry();
         String type = getTableType(baseEntry);

         for(int i = 0; i < tables.size(); i++) {
            treeChildren.add(TreeNodeModel.builder()
               .label(Tool.localize(tables.get(i)))
               .data(tables.get(i))
               .leaf(true)
               .tooltip(description.get(i) == null ? "" : description.get(i))
               .type(type)
               .build());
         }

         if(!cubeLabels.isEmpty()) {
            List<TreeNodeModel> cubeChildren = new ArrayList<>(cubeLabels.size());

            for(int i = 0; i < cubeLabels.size(); i++) {
               cubeChildren.add(TreeNodeModel.builder()
                  .label(localizeCube(cubeLabels.get(i), principal))
                  .data(cubeData.get(i))
                  .leaf(true)
                  .type("cube")
                  .build());
            }

            treeChildren.add(TreeNodeModel.builder()
               .label(Catalog.getCatalog().getString("Cubes"))
               .data("Cubes")
               .leaf(false)
               .children(cubeChildren)
               .build());
         }
      }

      return TreeNodeModel.builder().children(treeChildren).build();
   }

   public String getTableType(AssetEntry baseEntry) {
      String type = "table";

      if(baseEntry != null) {
         if(baseEntry.isLogicModel()) {
            type = "logical";
         }
         else if(baseEntry.isPhysicalTable()) {
            type = "physical-table";
         }
         else if(baseEntry.isQuery()) {
            type = "query";
         }
      }

      return type;
   }

   /**
    * Gets output tables and cubes. Mimic of GetOutputTablesEvent
    * @param rvs        The runtime viewsheet
    * @param principal  The principal user
    * @return map of output tables, cube data, cube names
    */
   public Map<String, List<String>> getOutputTables(RuntimeViewsheet rvs, Principal principal) {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();
      List<String> tables = new ArrayList<>();
      List<String> cubeLabels = new ArrayList<>();
      List<String> cubeData = new ArrayList<>();
      List<String> description = new ArrayList<>();

      if(ws != null && VSEventUtil.checkBaseWSPermission(vs, principal,
                                                         viewsheetService.getAssetRepository(),
                                                         ResourceAction.READ))
      {
         Assembly[] assemblies = ws.getAssemblies();

         for(Assembly assembly : assemblies) {
            if(!(assembly instanceof TableAssembly)) {
               continue;
            }

            TableAssembly tableAssembly = (TableAssembly) assembly;
            AssetEntry baseEntry = vs.getBaseEntry();

            if(!tableAssembly.isVisible() || !tableAssembly.isVisibleTable()) {
               continue;
            }

            if(baseEntry != null && (baseEntry.isLogicModel() || baseEntry.isQuery())) {
               tables.add(tableAssembly.getName());
               description.add(baseEntry.getProperty("Tooltip"));
            }
            else {
               tables.add(tableAssembly.getName());
               description.add(tableAssembly.getDescription());
            }
         }
      }

      Collections.sort(tables, new VSEventUtil.WSAssemblyComparator(ws));
      Properties prop = new Properties();
      prop.put("showDimensions", "false");
      Node node = new Node();

      try {
         node = VSEventUtil.getCubeTree(principal, prop, vs);
      }
      catch(Exception ex) {
         // TODO check if should handle exception
      }

      Node[] children = node.getNodes();
      AssetEntry assetEntry;

      for(Node child : children) {
         assetEntry = child.getEntry();

         if("true".equals(assetEntry.getProperty("isDomainCube"))) {
            continue;
         }

         cubeLabels.add(assetEntry.getProperty("localStr"));
         cubeData.add(assetEntry.getProperty("table"));
      }

      Map<String, List<String>> result = new HashMap<>();
      result.put("tables", tables);
      result.put("cubeLabels", cubeLabels);
      result.put("cubeData", cubeData);
      result.put("description", description);

      return result;
   }

   /**
    * Localized a cube name
    * @param name       the cube name
    * @param principal  the principal user
    * @return the localied cube name
    */
   private String localizeCube(String name, Principal principal) {
      Catalog cata = Catalog.getCatalog(principal, Catalog.REPORT);

      int idx = name.indexOf("(");
      String name0;
      String name1;

      StringBuilder sb = new StringBuilder();

      if(idx > 0 && name.endsWith(")")) {
         name0 = name.substring(0, idx);
         name1 = name.substring(idx + 1, name.lastIndexOf(')'));
         sb.append(cata.getString(name0));
         sb.append("(");
         sb.append(cata.getString(name1));
         sb.append(")");
      }
      else {
         return cata.getString(name);
      }

      return sb.toString();
   }

   /**
    * Get the tree for calendar data pane.
    * @param rvs        Runtime viewsheet instance
    * @param principal  Principal user
    * @return the tree used on the calendar data pane
    * @throws Exception if can not retrieve the tree
    */
   public TreeNodeModel getCalendarTablesTree(RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      AssetTreeModel assetTreeModel = getVSTreeModel(
         rvs, null, null, "false", "true", "false", null, principal);

      Object root = assetTreeModel.getRoot();

      if(root == null) {
         return TreeNodeModel.builder().build();
      }

      return TreeNodeModel.builder()
         .children(filterDateColumns((Node) root))
         .build();
   }

   public List<TreeNodeModel> filterDateColumns(Node parentNode) {
      List<TreeNodeModel> result = new ArrayList<>();
      Node[] nodes = parentNode.getNodes();

      for(int i = 0; i < nodes.length; i++) {
         Node node = nodes[i];
         AssetEntry entry = node.getEntry();
         String nodeLabel = entry.getProperty("localStr");

         if(nodeLabel == null || nodeLabel.isEmpty()) {
            nodeLabel = node.toString();
         }

         if(entry.isColumn()) {
            String type = entry.getProperty("dtype");

            if(XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type)) {
               OutputColumnRefModel columnModel = new OutputColumnRefModel();
               columnModel.setEntity(entry.getProperty("entity"));
               columnModel.setAttribute(entry.getProperty("attribute"));
               columnModel.setDataType(type);
               columnModel.setRefType(entry.getProperty("refType") == null ?
                  AbstractDataRef.NONE : Integer.parseInt(entry.getProperty("refType")));
               Map<String, String> properties = new HashMap<>();
               properties.put("refType", entry.getProperty("refType"));
               properties.put("mappingStatus", entry.getProperty("mappingStatus"));
               properties.put("formula", entry.getProperty("formula"));
               properties.put("basedOnDetail", entry.getProperty("basedOnDetail"));
               columnModel.setProperties(properties);
               columnModel.setPath(entry.getPath());
               columnModel.setTable(getEntryTable(entry));
               result.add(TreeNodeModel.builder()
                  .label(Tool.localize(nodeLabel))
                  .data(columnModel)
                  .type("columnNode")
                  .leaf(true)
                  .tooltip(entry.getProperty("Tooltip"))
                  .build());
            }
         }
         // No Cube branch in Calendar Data Tree
         else if(!"Cubes".equals(entry.getPath())) {
            result.add(TreeNodeModel.builder()
               .label(Tool.localize(nodeLabel))
               .data(entry)
               .type(entry.isTable() ? "table" : "folder")
               .leaf(false)
               .children(filterDateColumns(node))
               .tooltip(entry.getProperty("Tooltip"))
               .build());
         }
      }

      return result;
   }

   /**
    * Get the tree for range slider data pane.
    * @param rvs        Runtime viewsheet instance
    * @param principal  Principal user
    * @return the tree used on the range slider data pane
    * @throws Exception if can not retrieve the tree
    */
   public TreeNodeModel getRangeSliderTablesTree(RuntimeViewsheet rvs, Principal principal, boolean isComposite)
      throws Exception
   {
      AssetTreeModel assetTreeModel = getVSTreeModel(
         rvs, null, null, "false", "true", "false", "true", principal);

      Object root = assetTreeModel.getRoot();

      if(root == null) {
         return TreeNodeModel.builder().build();
      }

      return TreeNodeModel.builder()
         .children(createRangeSliderTree((Node) root, isComposite))
         .build();
   }

   /**
    * Create tree model for range slider data pane
    * @param parentNode the parent asset tree node
    */
   public List<TreeNodeModel> createRangeSliderTree(Node parentNode, boolean isComposite) {
      List<TreeNodeModel> result = new ArrayList<>();
      Node[] nodes = parentNode.getNodes();

      for(Node node : nodes) {
         AssetEntry entry = node.getEntry();
         String nodeLabel = entry.getProperty("localStr");

         if(nodeLabel == null || nodeLabel.isEmpty()) {
            nodeLabel = node.toString();
         }

         if(entry.isColumn()) {
            int refType = entry.getProperty("refType") == null ?
               AbstractDataRef.NONE : Integer.parseInt(entry.getProperty("refType"));
            String dataType = entry.getProperty("dtype");

            if((refType & AbstractDataRef.CUBE_DIMENSION) == AbstractDataRef.CUBE_DIMENSION ||
               XSchema.isNumericType(dataType) || XSchema.isDateType(dataType) || isComposite)
            {
               OutputColumnRefModel columnModel = new OutputColumnRefModel();
               columnModel.setEntity(entry.getProperty("entity"));
               columnModel.setAttribute(entry.getProperty("attribute"));
               columnModel.setCaption(entry.getProperty("caption"));
               columnModel.setDataType(dataType);
               columnModel.setRefType(refType);
               Map<String, String> properties = new HashMap<>();
               properties.put("refType", entry.getProperty("refType"));
               properties.put("mappingStatus", entry.getProperty("mappingStatus"));
               properties.put("formula", entry.getProperty("formula"));
               properties.put("basedOnDetail", entry.getProperty("basedOnDetail"));

               if((Integer.parseInt(entry.getProperty("type")) & XSourceInfo.VS_ASSEMBLY) != 0){
                  properties.put("dtype", entry.getProperty("dtype"));
                  properties.put("assembly", entry.getProperty("assembly"));
                  properties.put("attribute", entry.getProperty("attribute"));
                  properties.put("source", entry.getProperty("source"));
                  properties.put("type", entry.getProperty("type"));
               }

               columnModel.setProperties(properties);
               columnModel.setPath(entry.getPath());
               columnModel.setTable(getEntryTable(entry));
               result.add(TreeNodeModel.builder()
                  .label(Tool.localize(nodeLabel))
                  .data(columnModel)
                  .type("columnNode")
                  .leaf(true)
                  .tooltip(entry.getProperty("Tooltip"))
                  .build());
            }
         }
         else {
            result.add(TreeNodeModel.builder()
               .label(Tool.localize(nodeLabel))
               .data(entry)
               .type(getEntryType(entry))
               .leaf(false)
               .children(createRangeSliderTree(node, isComposite))
               .tooltip(entry.getProperty("Tooltip"))
               .build());
         }
      }

      return result;
   }

   /**
    * Get the tree for selection list data pane.
    * @param rvs        Runtime viewsheet instance
    * @param principal  Principal user
    * @return the tree used on the selection list data pane
    * @throws Exception if can not retrieve the tree
    */
   public TreeNodeModel getSelectionTablesTree(RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      AssetTreeModel assetTreeModel = getVSTreeModel(
         rvs, null, null, "false", "true", "false", "false", "true", principal);

      Object root = assetTreeModel.getRoot();

      if(root == null) {
         return TreeNodeModel.builder().build();
      }

      return TreeNodeModel.builder()
         .children(createSelectionTree((Node) root))
         .build();
   }

   /**
    * Create tree model for selection list data pane
    * @param parentNode the parent asset tree node
    */
   public List<TreeNodeModel> createSelectionTree(Node parentNode) {
      List<TreeNodeModel> nodes = new ArrayList<>();
      Node[] parentNodes = parentNode.getNodes();

      for(int i = 0; i < parentNodes.length; i ++) {
         Node node = parentNodes[i];
         AssetEntry entry = node.getEntry();
         String nodeLabel = entry.getProperty("localStr");

         if(nodeLabel == null || nodeLabel.isEmpty()) {
            nodeLabel = node.toString();
            if(nodeLabel.isEmpty()) {
               nodeLabel = "Column [" + i + "]";
            }
         }

         if(entry.isColumn()) {
            int refType = entry.getProperty("refType") == null ?
               AbstractDataRef.NONE : Integer.parseInt(entry.getProperty("refType"));
            String attr = entry.getProperty("attribute").isEmpty() ?
               "Column [" + i + "]" : entry.getProperty("attribute");
            OutputColumnRefModel columnModel = new OutputColumnRefModel();
            columnModel.setEntity(entry.getProperty("entity"));
            columnModel.setAttribute(attr);
            columnModel.setDataType(entry.getProperty("dtype"));
            columnModel.setRefType(refType);
            //Bug #16416 entry properties and path are required for getting tree node icon
            Map<String, String> properties = new HashMap<>();
            properties.put("refType", entry.getProperty("refType"));
            properties.put("caption", entry.getProperty("caption"));
            properties.put("mappingStatus", entry.getProperty("mappingStatus"));
            properties.put("formula", entry.getProperty("formula"));
            properties.put("basedOnDetail", entry.getProperty("basedOnDetail"));
            properties.put("dtype", entry.getProperty("dtype"));
            columnModel.setProperties(properties);
            columnModel.setPath(entry.getPath());

            columnModel.setTable(getEntryTable(entry));
            columnModel.setAlias(entry.getProperty("alias"));
            nodes.add(TreeNodeModel.builder()
               .label(Tool.localize(nodeLabel))
               .data(columnModel)
               .type("columnNode")
               .tooltip(entry.getProperty("Tooltip"))
               .leaf(true)
               .build());
         }
         else {
            nodes.add(TreeNodeModel.builder()
               .label(Tool.localize(nodeLabel))
                //Bug #16416 AssetEntry is required for getting tree node icon
               .data(entry)
               .type(getEntryType(entry))
               .leaf(false)
               .children(createSelectionTree(node))
               .tooltip(entry.getProperty("Tooltip"))
               .build());
         }
      }

      return nodes;
   }

   /**
    * Called by data output pane
    * @param rvs           RuntimeViewsheet instance
    * @param table         table
    * @param includeCalc   get calc tables
    * @param principal     the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getOutputTableColumns(RuntimeViewsheet rvs, String table,
                                                boolean includeCalc, Principal principal)
      throws Exception
   {
      return vsInputService.getTableColumns(rvs, table, null, null, null, false,
                                            false, includeCalc, false, false, false, principal);
   }

   /**
    * Called by selection data pane
    * @param rvs           RuntimeViewsheet instance
    * @param table         table
    * @param measureOnly   include only measures
    * @param principal     the principal user
    * @return the column selection
    * @throws Exception if can't retrieve columns
    */
   public ColumnSelection getOutputSelectionColumns(RuntimeViewsheet rvs, String table,
                                                    boolean measureOnly, Principal principal)
      throws Exception
   {
      ColumnSelection columnSelection = vsInputService.getTableColumns(rvs, table, null, null, null, false,
                                            measureOnly, true, false, false, false, principal);

      return columnSelection;
   }

   private String getEntryType(AssetEntry entry) {
      return entry.isTable() ? "table" : entry.isFolder() ? "folder" : entry.isWorksheet() ? "worksheet" : "";
   }

   private String getEntryTable(AssetEntry entry) {
      String table = entry.getProperty("table");

      // Logical Model column entries only set 'assembly' property referring to parent name
      if(table == null) {
         table = entry.getProperty("assembly");

         if(table != null) {
            table = VSUtil.getTableName(table);
         }
      }

      return table;
   }

   private final VSInputService vsInputService;
   private final ViewsheetService viewsheetService;
}
