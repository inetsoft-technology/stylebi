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

package inetsoft.web.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.cluster.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.script.*;
import inetsoft.report.script.viewsheet.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ScriptIterator;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.script.JSObject;
import inetsoft.util.script.TimeoutContext;
import inetsoft.web.binding.handler.VSTreeHandler;
import inetsoft.web.binding.model.ScriptTreeNodeData;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class VSScriptableService {

   public VSScriptableService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ObjectNode getScriptDefinition(@ClusterProxyKey String vsId, String assemblyName, String tableName,
                                         boolean isCondition, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String vsName = null;

      int dot = assemblyName == null ? -1 : assemblyName.lastIndexOf(".");

      if(dot != -1) {
         vsName = assemblyName.substring(0, dot);
      }

      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = createScriptDefinitions(mapper);

      try {
         box.lockRead();
         createComponentDefinitions(mapper, root, rvs, vsName, assemblyName);
         createParameterDefinitions(mapper, root, viewsheetService, rvs, vsName);
         createFieldDefinitions(mapper, root, rvs, viewsheet, assemblyName, tableName,
                                isCondition);
      }
      finally {
         box.unlockRead();
      }

      // copy assembly properties and methods to top-level scope
      if(assemblyName != null && root.has(assemblyName)) {
         ObjectNode assemblyNode = (ObjectNode) root.get(assemblyName);

         for(Iterator<Map.Entry<String, JsonNode>> i = assemblyNode.fields(); i.hasNext();) {
            Map.Entry<String, JsonNode> e = i.next();
            root.set(e.getKey(), e.getValue().deepCopy());
         }
      }

      return root;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getColumnTree(@ClusterProxyKey String vsId, String assemblyName, String tableName,
                                      boolean isCondition, boolean isVSOption, VSTreeHandler treeHandler, Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      String vsName = null;

      int dot = assemblyName == null ? -1 : assemblyName.lastIndexOf(".");

      if(dot != -1) {
         vsName = assemblyName.substring(0, dot);
      }

      final String rootName = "data";
      final String rootLabel = catalog.getString("Data");
      final String rootData = null;

      List<TreeNodeModel> children = new ArrayList<>();
      TreeNodeModel componentsNode = createComponentsNode(
         rvs, vsName, rootName, rootLabel, rootData, catalog);

      if(componentsNode != null) {
         children.add(componentsNode);
      }

      TreeNodeModel parametersNode = createParametersNode(
         viewsheetService, rvs, vsName, rootName, rootLabel, rootData, catalog);

      if(parametersNode != null) {
         children.add(parametersNode);
      }

      if(assemblyName != null || isVSOption) {
         TreeNodeModel tablesNode = createTablesNode(viewsheetService, rvs, vsName, rootName,
                                                     rootLabel, rootData, catalog, treeHandler, principal);

         if(tablesNode != null) {
            children.add(tablesNode);
         }
      }

      return createNode(rootLabel, rootData, false, null, null,
                        null, rootName, null, children, true);
   }

   private void createComponentDefinitions(ObjectMapper mapper, ObjectNode library,
                                           RuntimeViewsheet rvs, String vsName,
                                           String assemblyName)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if(vsName != null) {
         box = box.getSandbox(vsName);
      }

      Viewsheet vs = rvs.getViewsheet();
      ViewsheetScope scope = box.getScope();
      Object[] ids = scope.getIds();
      Tool.qsort(ids, true);

      if(ids == null || ids.length == 0) {
         return;
      }

      ScriptIterator.setProcessing(true);

      for(Object id : ids) {
         String name = (String) id;
         VSAScriptable scriptable = scope.getVSAScriptable(name);
         VSAssembly assembly = vs.getAssembly(name);

         if(scriptable == null || AnnotationVSUtil.isAnnotation(assembly)) {
            continue;
         }

         createAssemblyDefinition(mapper, library, name, scriptable);
      }

      // Current assembly properties can be accessed directly without assembly name.
      // Such as: Crosstab1.alignment   equals to   alignment
      JsonNode jsonNode = library.get(assemblyName);

      if(assemblyName != null && jsonNode != null) {
         library.setAll((ObjectNode) jsonNode);
      }

      ScriptIterator.setProcessing(false);
   }

   private void createAssemblyDefinition(ObjectMapper mapper, ObjectNode library, String name,
                                         VSAScriptable scriptable)
   {
      scriptable.setIncludeAllIDs(false);
      Object[] ids = scriptable.getIds();
      scriptable.setIncludeAllIDs(true);

      if(ids == null || ids.length == 0) {
         return;
      }

      Arrays.sort(ids);
      ObjectNode scriptableNode = mapper.createObjectNode();
      library.set(name, scriptableNode);

      for(Object id : ids) {
         String property = (String) id;

         if(("scheduleAction".equals(property) || "taskName".equals(property) ||
            "addAction".equals(property)) &&
            !ViewsheetScope.VIEWSHEET_SCRIPTABLE.equals(name))
         {
            continue;
         }

         try {
            createComponentPropertyDefinitions(mapper, library, scriptableNode,
                                               scriptable, property);
         }
         catch(Exception e) {
            //throw new RuntimeException(
            //"Failed to add property \"" + property + "\" of object \"" + name + "\"", e);
         }
      }

      ObjectNode viewsheetNode = (ObjectNode) library.get("viewsheet");

      if(viewsheetNode == null) {
         viewsheetNode = mapper.createObjectNode();
         library.set("viewsheet", viewsheetNode);
      }

      viewsheetNode.set(name, scriptableNode.deepCopy());
   }


   private ObjectNode createScriptDefinitions(ObjectMapper mapper) throws IOException {
      ObjectNode root = mapper.createObjectNode();
      root.put("!name", "inetsoft");
      root.set("!define", mapper.createObjectNode());
      createStaticDefinitions(mapper, root);
      createUserDefinedScript(mapper, root);
      return root;
   }

   private void createStaticDefinitions(ObjectMapper mapper, ObjectNode library)
      throws IOException
   {
      ObjectNode functions = (ObjectNode)
         mapper.readTree(getClass().getResource("js-functions.json"));
      ObjectNode generated = (ObjectNode) mapper.readTree(
         getClass().getResource("/inetsoft/web/binding/js-functions.generated.json"));

      for(Iterator<Map.Entry<String, JsonNode>> it = generated.fields(); it.hasNext();) {
         Map.Entry<String, JsonNode> e = it.next();
         functions.set(e.getKey(), e.getValue());
      }

      // remove reporting only scripts from function tree
      functions.remove(sreeOnly);

      // calc functions can be accessed directly without qualified with CALC
      if(functions.get("CALC") != null) {
         library.setAll((ObjectNode) functions.get("CALC"));
      }

      library.setAll(functions);

      ObjectNode chartConstants = (ObjectNode) library.get("Chart");

      for(String mapType: MapData.getMapTypes()) {
         ObjectNode typeNode = mapper.createObjectNode();
         typeNode.put("!type", "string");
         chartConstants.set("MAP_TYPE_" + mapType.toUpperCase(), typeNode);
      }
   }

   private void createUserDefinedScript(ObjectMapper mapper, ObjectNode root) {
      List<String> list = AdHocQueryHandler.getUserDefinedScriptFunctions();

      if(list.size() == 0) {
         return;
      }

      for(int i = 0; i < list.size(); i++) {
         if(root.get(list.get(i)) != null) {
            continue;
         }

         ObjectNode node = mapper.createObjectNode();
         node.put("!type", "fn()");
         node.put("prototype", "{}");

         if(getUrl(list.get(i)) != null) {
            node.put("!url", getUrl(list.get(i)));
         }

         root.put(list.get(i), node);
      }
   }

   private void createParameterDefinitions(ObjectMapper mapper, ObjectNode library,
                                           ViewsheetService engine, RuntimeViewsheet rvs,
                                           String vsName)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if(vsName != null) {
         box = box.getSandbox(vsName);
      }

      ViewsheetScope scope = box.getScope();
      VariableScriptable vscriptable = scope.getVariableScriptable();
      VariableTable vtable = (VariableTable) vscriptable.unwrap();

      // per usa support's request, allow script to access user in viewsheet
      if(!vtable.contains("__principal__") && box.getUser() != null) {
         Principal principal = box.getUser();
         vtable.put("__principal__", principal);
         vtable.put("_USER_", XUtil.getUserName(principal));
         vtable.put("_ROLES_", XUtil.getUserRoleNames(principal));
         vtable.put("_GROUPS_", XUtil.getUserGroups(principal));
         vscriptable = new VariableScriptable(vtable);
      }

      Object[] ids = vscriptable.getIds();
      Set<String> aids = new HashSet<>();

      if(ids != null) {
         for(Object id : ids) {
            if(id != null) {
               aids.add((String) id);
            }
         }
      }

      collectSheetParameters(engine, rvs, aids);
      ids = aids.toArray(new String[0]);
      Arrays.sort(ids);

      ObjectNode parameter = mapper.createObjectNode();
      library.set("parameter", parameter);
      createProperties(mapper, library, parameter, vscriptable, ids);
   }

   /**
    * Create parameters nodes by variables of the viewsheet.
    */
   private TreeNodeModel createParametersNode(ViewsheetService engine,
                                              RuntimeViewsheet rvs, String vsName,
                                              String parentName, String parentLabel,
                                              Object parentData, Catalog catalog)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      if(vsName != null) {
         box = box.getSandbox(vsName);
      }

      ViewsheetScope scope = box.getScope();
      VariableScriptable vscriptable = scope.getVariableScriptable();
      VariableTable vtable = (VariableTable) vscriptable.unwrap();

      // per usa support's request, allow script to access user in viewsheet
      if(!vtable.contains("__principal__") && box.getUser() != null) {
         Principal principal = box.getUser();
         vtable.put("__principal__", principal);
         vtable.put("_USER_", XUtil.getUserName(principal));
         vtable.put("_ROLES_", XUtil.getUserRoleNames(principal));
         vtable.put("_GROUPS_", XUtil.getUserGroups(principal));
         vscriptable = new VariableScriptable(vtable);
      }

      Object[] ids = vscriptable.getIds();
      Set<String> aids = new HashSet<>();

      if(ids != null) {
         for(Object id : ids) {
            if(id != null) {
               aids.add((String) id);
            }
         }
      }

      collectSheetParameters(engine, rvs, aids);
      ids = new String[0];
      ids = aids.toArray(ids);
      Arrays.sort(ids);

      final String nodeName = "parameter";
      final String nodeLabel = catalog.getString("Parameter");
      final String nodeData = "parameter";

      List<TreeNodeModel> children = new ArrayList<>();

      for(Object id : ids) {
         String name = (String) id;
         TreeNodeModel node = createNode(
            name, name, true, nodeName, nodeLabel, nodeData, "param", null,
            Collections.emptyList());
         children.add(node);
      }

      return createNode(
         nodeLabel, nodeData, false, parentName, parentLabel, parentData, nodeName, null,
         children);
   }

   private TreeNodeModel createTablesNode(ViewsheetService engine,
                                          RuntimeViewsheet rvs, String vsName,
                                          String parentName, String parentLabel,
                                          Object parentData, Catalog catalog,
                                          VSTreeHandler treeHandler, Principal principal)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      final String nodeName = "table";
      final String nodeLabel = catalog.getString("Data");
      final String nodeData = "table";
      AssetTreeModel assetTreeModel = null;

      try {
         assetTreeModel = treeHandler.getWSTreeModel(
            engine.getAssetRepository(), rvs, null, false, principal);
      }
      catch(Exception e) {
         return null;
      }

      if(assetTreeModel == null || rvs.getViewsheet().getBaseEntry() == null) {
         return null;
      }

      AssetTreeModel.Node sourceRoot = (AssetTreeModel.Node) assetTreeModel.getRoot();
      AssetEntry.Type type = rvs.getViewsheet().getBaseEntry().getType();

      if(rvs.getViewsheet().getBaseEntry().isWorksheet()) {
         sourceRoot = sourceRoot.getNodes()[0];

         if(!sourceRoot.getEntry().isWorksheet()) {
            sourceRoot = sourceRoot.getNodes()[0];
         }
      }

      TreeNodeModel root = createTreeNodeModel(sourceRoot, principal, type);

      return createNode(nodeLabel, nodeData, false, parentName, parentLabel, parentData,
                        nodeName, null, root.children());
   }

   public TreeNodeModel createTreeNodeModel(AssetTreeModel.Node node, Principal user,
                                            AssetEntry.Type type)
   {
      List<TreeNodeModel> children = Arrays.stream(node.getNodes())
         .filter(n -> isDataSourceNode(n.getEntry()))
         .map(n -> createChildTreeNode(node, n, user, type))
         .collect(Collectors.toList());

      return TreeNodeModel.builder()
         .children(children)
         .expanded(true)
         .build();
   }


   private boolean isDataSourceNode(AssetEntry entry) {
      return entry.isTable() || entry.isPhysicalTable() || entry.isLogicModel() ||
         entry.isWorksheet() || entry.isQuery() || entry.isPhysicalFolder();
   }

   private TreeNodeModel createChildTreeNode(AssetTreeModel.Node pnode, AssetTreeModel.Node node,
                                             Principal user, AssetEntry.Type type)
   {
      String parent = pnode.toString();
      String pnodeName = pnode.getEntry().getType().name();
      String nodeName = node.getEntry().getType().name();
      String nodeData = node.toString();
      boolean isLeaf = node.getEntry().isColumn();
      String dtype = node.getEntry().getProperty("dtype");

      // The parentdata property is using to get script strings in script pane. For logic model,
      // click its column, should get model name as table name. The column name should be:
      // entity:column
      // all format will be:  model['entity:column']
      if(type == AssetEntry.Type.LOGIC_MODEL && nodeName == "COLUMN") {
         nodeData = parent + ":" + nodeData;
         parent = pnode.getParent().toString();
      }

      List<TreeNodeModel> children = Arrays.stream(node.getNodes())
         .filter(n -> !isCalc(n))
         .map(n -> createChildTreeNode(node, n, user, type))
         .collect(Collectors.toList());

      if(isLeaf && XSchema.isDateType(dtype)) {
         Catalog catalog = Catalog.getCatalog(user);
         createDateTreeNodes(type, dtype, parent, nodeData, node, children, catalog);
      }

      return createNode(nodeData, nodeData, isLeaf, pnodeName, parent, parent, nodeName, null,
                        children);
   }


   private void createDateTreeNodes(AssetEntry.Type type,String dtype, String parent,
                                    String nodeData, AssetTreeModel.Node node,
                                    List<TreeNodeModel> children, Catalog catalog)
   {
      String nodeName = node.getEntry().getType().name();
      String[] levels = Util.getDateParts(dtype);

      for(int i = 0; i < levels.length; i++) {
         String label = catalog.getString(levels[i]) + "(" + nodeData + ")";
         String data = Util.getDatePartFunc(levels[i]) + "(" + nodeData + ")";
         TreeNodeModel child = createNode(label, data, true,
                                          nodeName, nodeData, parent, nodeName, null, new ArrayList<>(), false,
                                          null, false, Util.DATE_PART_COLUMN);
         children.add(child);
      }
   }

   private boolean isCalc(AssetTreeModel.Node node) {
      return "true".equals(node.getEntry().getProperty("isCalc"));
   }

   /**
    * create components node.
    */
   private TreeNodeModel createComponentsNode(RuntimeViewsheet rvs, String vsName,
                                              String parentName, String parentLabel,
                                              Object parentData, Catalog catalog)
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      if(vsName != null) {
         box = box.getSandbox(vsName);
      }

      Viewsheet vs = rvs.getViewsheet();
      final String nodeName = "components";
      final String nodeLabel = catalog.getString("Component");
      final String nodeData = null;
      List<TreeNodeModel> children = new ArrayList<>();

      box.lockRead();
      ScriptIterator.setProcessing(true);

      try {
         ViewsheetScope scope = box.getScope();
         Object[] ids = scope.getIds();
         Tool.qsort(ids, true);

         if(ids == null || ids.length == 0) {
            return null;
         }

         for(Object id : ids) {
            String name = (String) id;
            VSAScriptable scriptable = scope.getVSAScriptable(name);
            VSAssembly assembly = vs.getAssembly(name);

            if(scriptable == null || AnnotationVSUtil.isAnnotation(assembly) ||
               WizardRecommenderUtil.isWizardTempAssembly(name) ||
               name != null && name.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX))
            {
               continue;
            }

            TreeNodeModel script = createScriptableNode(
               name, scriptable, nodeName, nodeLabel, nodeData);

            if(script != null) {
               children.add(script);
            }
         }
      }
      finally {
         ScriptIterator.setProcessing(false);
         box.unlockRead();
      }

      return createNode(nodeLabel, nodeData, false, parentName, parentLabel, parentData,
                        nodeName, null, children);
   }


   /**
    * Create scriptable node.
    */
   private TreeNodeModel createScriptableNode(String name, VSAScriptable scriptable,
                                              String parentName, String parentLabel,
                                              Object parentData)
   {
      scriptable.setIncludeAllIDs(false);
      Object[] ids0 = scriptable.getIds();
      scriptable.setIncludeAllIDs(true);

      if(ids0 == null || ids0.length == 0) {
         return null;
      }

      Arrays.sort(ids0);

      final String nodeName = "component";

      List<TreeNodeModel> children = new ArrayList<>();

      for(Object id0 : ids0) {
         String name0 = (String) id0;

         if(("scheduleAction".equals(name0) || "taskName".equals(name0) ||
            "addAction".equals(name0)) &&
            !ViewsheetScope.VIEWSHEET_SCRIPTABLE.equals(name))
         {
            continue;
         }

         if(("addTarget").equals(name0)) {
            continue;
         }

         children.add(createComponentPropertyNode(
            scriptable, name0, nodeName, name, name));
      }

      return createNode(
         name, name, false, parentName, parentLabel, parentData, nodeName, null,
         children);
   }

   private void createComponentPropertyDefinitions(ObjectMapper mapper, ObjectNode library,
                                                   ObjectNode object, VSAScriptable scriptable,
                                                   String name)
   {
      final boolean writeBinding = scriptable instanceof ChartVSAScriptable;

      if(("addTarget").equals(name)) {
         return;
      }

      if(scriptable instanceof CrosstabVSAScriptable && "bindingInfo".equals(name)) {
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("bindingInfo", child);
         createBindingInfoDefinition(
            mapper, library, child, ((CrosstabVSAScriptable) scriptable).getBindingInfo());
      }
      else if(scriptable instanceof CalcTableVSAScriptable && "layoutInfo".equals(name)) {
         ObjectNode child = mapper.createObjectNode();
         object.set("layoutInfo", child);
         VSTableLayoutInfo layoutInfo = ((CalcTableVSAScriptable) scriptable).getLayoutInfo();
         Object[] ids = layoutInfo.getIds();
         createProperties0(mapper, library, child, scriptable, ids, "CalcTable.layoutInfo.");
      }
      else if(writeBinding && "bindingInfo".equals(name)) {
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("bindingInfo", child);
         createBindingInfoDefinition(
            mapper, library, child, ((ChartVSAScriptable) scriptable).getBindingInfo());
      }
      else if(writeBinding && "xAxis".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getAxisIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("xAxis", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.xAxis.");
      }
      else if(writeBinding && "yAxis".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getAxisIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("yAxis", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.yAxis.");
      }
      else if(writeBinding && "y2Axis".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getAxisIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("y2Axis", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.y2Axis.");
      }
      else if(writeBinding && "axis".equals(name)) {
         createChartFieldDefinitions(
            mapper, library, object, scriptable, "axis",
            ((ChartVSAScriptable) scriptable).getFieldAxisIds());
      }
      else if(writeBinding && "xTitle".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getTitleIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("xTitle", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.xTitle.");
      }
      else if(writeBinding && "x2Title".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getTitleIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("x2Title", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.x2Title.");
      }
      else if(writeBinding && "yTitle".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getTitleIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("yTitle", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.yTitle.");
      }
      else if(writeBinding && "y2Title".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getTitleIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("y2Title", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.y2Title.");
      }
      else if(writeBinding && "colorLegend".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getLegendIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("colorLegend", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.colorLegend.");
      }
      else if(writeBinding && "shapeLegend".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getLegendIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("shapeLegend", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.shapeLegend.");
      }
      else if(writeBinding && "sizeLegend".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getLegendIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("sizeLegend", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.sizeLegend.");
      }
      else if(writeBinding && "colorLegends".equals(name)) {
         createChartArrayDefinitions(mapper, library, object, "colorLegends");
      }
      else if(writeBinding && "shapeLegends".equals(name)) {
         createChartArrayDefinitions(mapper, library, object, "shapeLegends");
      }
      else if(writeBinding && "sizeLegends".equals(name)) {
         createChartArrayDefinitions(mapper, library, object, "sizeLegends");
      }
      else if(writeBinding && "valueFormats".equals(name)) {
         createChartArrayDefinitions(mapper, library, object, "valueFormats");
      }
      else if(writeBinding && "graph".equals(name)) {
         Object[] ids = ((ChartVSAScriptable) scriptable).getEGraphIds();
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);
         object.set("graph", child);
         createProperties0(mapper, library, child, scriptable, ids, "Chart.graph.");
      }
      else if("highlighted".equals(name)) {
         createHighlightedDefinitions(mapper, library, object, scriptable);
      }
      else {
         createProperties(mapper, library, object, scriptable, new Object[] { name });
      }
   }


   private TreeNodeModel createComponentPropertyNode(VSAScriptable scriptable,
                                                     String name, String parentName,
                                                     String parentLabel,
                                                     Object parentData)
   {
      final boolean writeBinding = scriptable instanceof ChartVSAScriptable;
      final String nodeName = parentLabel;
      final String nodeLabel = parentLabel;
      final Object nodeData = parentData;

      List<TreeNodeModel> children0 = new ArrayList<>();
      List<TreeNodeModel> list;
      List<String> fields = null;

      if(scriptable instanceof CrosstabVSAScriptable && "bindingInfo".equals(name)) {
         list = createBindingInfoNodeList(
            ((CrosstabVSAScriptable) scriptable).getBindingInfo(), nodeName, nodeLabel,
            nodeData);

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "bindingInfo".equals(name)) {
         list = createBindingInfoNodeList(
            ((ChartVSAScriptable) scriptable).getBindingInfo(), nodeName, nodeLabel,
            nodeData);

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(scriptable instanceof CalcTableVSAScriptable && "layoutInfo".equals(name)) {
         Object[] ids = ((CalcTableVSAScriptable) scriptable).getLayoutInfo().getIds();
         list = createNodes(ids, nodeName, nodeLabel, nodeData, "layoutInfo");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "xAxis".equals(name)) {
         Object[] xAxis = ((ChartVSAScriptable) scriptable).getAxisIds();
         list = createNodes(xAxis, nodeName, nodeLabel, nodeData, "xAxis");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "yAxis".equals(name)) {
         Object[] yAxis = ((ChartVSAScriptable) scriptable).getAxisIds();
         list = createNodes(yAxis, nodeName, nodeLabel, nodeData, "yAxis");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "y2Axis".equals(name)) {
         Object[] y2Axis = ((ChartVSAScriptable) scriptable).getAxisIds();
         list = createNodes(y2Axis, nodeName, nodeLabel, nodeData, "y2Axis");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "axis".equals(name)) {
         Object[] axis = ((ChartVSAScriptable) scriptable).getFieldAxisIds();
         list = createNodes(axis, nodeName, nodeLabel, nodeData, "axis");

         if(list != null) {
            children0.addAll(list);
         }

         fields = getChartFields((Scriptable) scriptable.get("axis", scriptable));
      }

      if(writeBinding && "xTitle".equals(name)) {
         Object[] title = ((ChartVSAScriptable) scriptable).getTitleIds();
         list = createNodes(title, nodeName, nodeLabel, nodeData, "xTitle");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "x2Title".equals(name)) {
         Object[] title = ((ChartVSAScriptable) scriptable).getTitleIds();
         list = createNodes(title, nodeName, nodeLabel, nodeData, "x2Title");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "yTitle".equals(name)) {
         Object[] title = ((ChartVSAScriptable) scriptable).getTitleIds();
         list = createNodes(title, nodeName, nodeLabel, nodeData, "yTitle");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "y2Title".equals(name)) {
         Object[] title = ((ChartVSAScriptable) scriptable).getTitleIds();
         list = createNodes(title, nodeName, nodeLabel, nodeData, "y2Title");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "colorLegend".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "colorLegend");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "shapeLegend".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "shapeLegend");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "sizeLegend".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "sizeLegend");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if(writeBinding && "colorLegends".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "colorLegends");

         if(list != null) {
            children0.addAll(list);
         }

         fields = getChartFields((Scriptable) scriptable.get("colorLegends", scriptable));
      }

      if(writeBinding && "shapeLegends".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "shapeLegends");

         if(list != null) {
            children0.addAll(list);
         }

         fields = getChartFields((Scriptable) scriptable.get("shapeLegends", scriptable));
      }

      if(writeBinding && "sizeLegends".equals(name)) {
         Object[] legend = ((ChartVSAScriptable) scriptable).getLegendIds();
         list = createNodes(legend, nodeName, nodeLabel, nodeData, "sizeLegends");

         if(list != null) {
            children0.addAll(list);
         }

         fields = getChartFields((Scriptable) scriptable.get("sizeLegends", scriptable));
      }

      if(writeBinding && "valueFormats".equals(name)) {
         Object[] format = ((ChartVSAScriptable) scriptable).getValueFormatIds();
         list = createNodes(format, nodeName, nodeLabel, nodeData, "valueFormats");

         if(list != null) {
            children0.addAll(list);
         }

         fields = getChartFields((Scriptable) scriptable.get("valueFormats", scriptable));
      }

      if(writeBinding && "graph".equals(name)) {
         Object[] graph = ((ChartVSAScriptable) scriptable).getEGraphIds();
         list = createNodes(graph, nodeName, nodeLabel, nodeData, "graph");

         if(list != null) {
            children0.addAll(list);
         }
      }

      if("highlighted".equals(name)) {
         list = createHighlightedNodes(scriptable, nodeName, nodeLabel, nodeData);

         if(list != null) {
            children0.addAll(list);
         }
      }

      return createNode(
         name, name, children0.isEmpty(), parentName, parentLabel, parentData, nodeName,
         scriptable.getSuffix(name), children0, false, fields, false, true);
   }

   private void createChartFieldDefinitions(ObjectMapper mapper, ObjectNode library,
                                            ObjectNode object, Scriptable scriptable, String name,
                                            Object[] ids)
   {
      ObjectNode child = mapper.createObjectNode();
      object.set(name, child);
      ScriptPropertyTool.fixPropertyLink(scriptable, null, name, child);

      if(ids == null || ids.length == 0) {
         return;
      }

      Object obj = scriptable.get("axis", scriptable);

      // fix Bug #35556, avoid get UniqueTag.NOT_FOUND.
      if(obj instanceof UniqueTag) {
         return;
      }

      Scriptable fieldsScriptable = (Scriptable) obj;
      Object[] fieldIds = fieldsScriptable.getIds();

      if(fieldIds == null || fieldIds.length == 0) {

         return;
      }

      Arrays.sort(fieldIds);

      for(Object fieldId : fieldIds) {
         String field = (String) fieldId;
         Scriptable fieldScriptable = (Scriptable) fieldsScriptable.get(field, fieldsScriptable);
         ObjectNode fieldNode = mapper.createObjectNode();

         if(!Tool.isValidIdentifier(field)) {
            field = "['" + field.replace("'", "\\'") + "']";
         }

         child.set(field, fieldNode);
         createProperties0(mapper, library, fieldNode, fieldScriptable, ids, "Chart.axis.");
      }
   }

   private void createChartArrayDefinitions(ObjectMapper mapper, ObjectNode root, ObjectNode object, String name) {
      ObjectNode child = mapper.createObjectNode();
      ObjectNode itemDef = mapper.createObjectNode();
      object.set(name, child);
      String itemTypeName = name + "Def";
      child.put("!type", "[" + itemTypeName + "]");
      ObjectNode defNode = (ObjectNode) root.get("!define");

      if(defNode == null) {
         defNode = mapper.createObjectNode();
         root.set("!define", defNode);
      }

      defNode.set(itemTypeName, itemDef);
      List<String> properties = getProperties(name);
      String prefix = getPrefix(name);

      for(int i = 0; i < properties.size(); i++) {
         String property = properties.get(i);
         ObjectNode propNode = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(null, prefix, property, propNode);
         itemDef.set(property, propNode);
      }
   }


   private List<String> getProperties(String name) {
      ArrayList<String> list = new ArrayList<>();
      list.add("color");
      list.add("font");
      list.add("format");

      if("colorLegends".equals(name) || "shapeLegends".equals(name) || "sizeLegends".equals(name)) {
         list.add("noNull");
         list.add("title");
         list.add("titleVisible");
      }
      else if("valueFormats".equals(name)) {
         list.add("rotation");
      }

      return list;
   }

   private String getPrefix(String name) {
      if("colorLegends".equals(name)) {
         return "Chart.colorLegends.";
      }
      else if("shapeLegends".equals(name)) {
         return "Chart.shapeLegends.";
      }
      else if("sizeLegends".equals(name)) {
         return "Chart.sizeLegends.";
      }
      else if("valueFormats".equals(name)) {
         return "Chart.valueFormats.";
      }

      return null;
   }

   private List<String> getChartFields(Scriptable sobj) {
      Object[] ids = sobj.getIds();

      if(ids == null || ids.length == 0) {
         return null;
      }

      return Arrays.stream(ids)
         .map(id -> id == null ? null : String.valueOf(id))
         .collect(Collectors.toList());
   }

   private void createHighlightedDefinitions(ObjectMapper mapper, ObjectNode library,
                                             ObjectNode object, VSAScriptable scriptable)
   {
      if(scriptable instanceof ChartVSAScriptable ||
         scriptable instanceof DataVSAScriptable ||
         scriptable instanceof OutputVSAScriptable)
      {
         ObjectNode child = mapper.createObjectNode();
         ScriptPropertyTool.fixPropertyLink(scriptable, null, "highlighted", child);
         child.put("!type", "+Object");
         object.set("highlighted", child);
         Object obj = scriptable.get("highlighted", scriptable);

         if(obj instanceof Scriptable) {
            Scriptable highlights = (Scriptable) obj;
            Object[] ids = highlights.getIds();

            if(ids != null && ids.length > 0) {
               for(int i = 0; i < ids.length; i++) {
                  if(ids[i].toString().indexOf(" ") > 0) {
                     ids[i] = "['" + ids[i] + "']";
                  }
               }

               createProperties(mapper, library, child, highlights, ids);
            }
         }
      }
   }

   private List<TreeNodeModel> createHighlightedNodes(VSAScriptable scriptable,
                                                      String parentName,
                                                      String parentLabel,
                                                      Object parentData)
   {
      if(scriptable instanceof ChartVSAScriptable ||
         scriptable instanceof DataVSAScriptable ||
         scriptable instanceof OutputVSAScriptable)
      {
         Object obj = scriptable.get("highlighted", scriptable);

         if(obj instanceof Scriptable) {
            Scriptable  highlightes = (Scriptable) obj;

            if(highlightes.getIds().length > 0) {
               return createNodes(
                  highlightes.getIds(), parentName, parentLabel, parentData,
                  "highlighted");
            }
         }
      }

      return null;
   }

   private void createProperties(ObjectMapper mapper, ObjectNode library, ObjectNode object,
                                 Scriptable scriptable, Object[] ids)
   {
      createProperties0(mapper, library, object, scriptable, ids, null);
   }

   private void createProperties0(ObjectMapper mapper, ObjectNode library, ObjectNode object,
                                  Scriptable scriptable, Object[] ids, String prefix)
   {
      if(ids == null) {
         return;
      }

      ids = Arrays.stream(ids)
         .filter(Objects::nonNull)
         .sorted(Tool::compare)
         .toArray(Object[]::new);

      if(ids.length == 0) {
         return;
      }

      TimeoutContext.enter();

      try {
         for(Object id : ids) {
            String property = String.valueOf(id);
            Object value = null;

            // optimization, get is expensive.
            if("webMapStyle".equals(property)) {
               value = "Basic";
            }
            else if(scriptable.has(property, scriptable)) {
               try {
                  value = scriptable.get(property, scriptable);
               }
               catch(Exception ex) {
                  LOG.debug("Failed to get scriptable property: " + property, ex);
               }
            }

            createProperty(mapper, library, object, property, value, prefix, scriptable);
         }
      }
      finally {
         Context.exit();
      }
   }

   private void createProperty(ObjectMapper mapper, ObjectNode library, ObjectNode object,
                               String property, Object value, String prefix, Scriptable scriptable)
   {
      if(scriptable instanceof VariableScriptable) {
         createDefaultProperty(mapper, library, object, property, value);
         return;
      }


      ObjectNode propNode = mapper.createObjectNode();

      if(property.endsWith("()")) {
         property = property.replace("()", "");
      }

      if(ScriptPropertyTool.getPropertyLink(scriptable, prefix, property) != null) {
         ScriptPropertyTool.fixPropertyLink(scriptable, prefix, property, propNode);
         object.set(property, propNode);
      }
      else {
         createDefaultProperty(mapper, library, object, property, value);
      }
   }

   private void createDefaultProperty(ObjectMapper mapper, ObjectNode library, ObjectNode object,
                                      String property, Object value)
   {
      if(value instanceof FunctionObject) {
         ObjectNode fnNode = mapper.createObjectNode();
         fnNode.put("!type", "fn() -> ?");
         object.set(property, fnNode);
      }
      else if(value instanceof TableArray) {
         ObjectNode array = mapper.createObjectNode();
         object.set(property, array);

         ObjectNode field = mapper.createObjectNode();
         array.set("length", field);
         field.put("!type", "number");

         field = mapper.createObjectNode();
         array.set("size", field);
         field.put("!type", "number");

         ObjectNode row = mapper.createObjectNode();
         array.set("<i>", row);

         field = mapper.createObjectNode();
         row.set("length", field);
         field.put("!type", "number");

         int length = (Integer) ((TableArray) value).get("length", (TableArray) value);

         if(length > 1) {
            TableRow tableRow = (TableRow) ((TableArray) value).get(1, (TableArray) value);
            Object[] rowIds = tableRow.getIds();

            for(Object rowId : rowIds) {
               if(!"length".equals(rowId)) {
                  field = mapper.createObjectNode();
                  row.set((String) rowId, field);
                  field.put("!type", "?");
               }
            }
         }

         row.put("<i>", "?");
      }
      else {
         // Bug #62293, don't add JSObjects, otherwise tern is not able to infer types of these
         // variables and display the help links
         if(!(value instanceof JSObject)) {
            object.put(property, getScriptType(mapper, library, value));
         }
      }
   }



   private String getScriptType(ObjectMapper mapper, ObjectNode library, Object value) {
      if(value instanceof Scriptable) {
         ObjectNode definitions = (ObjectNode) library.get("!define");
         String type = value.getClass().getSimpleName();

         if(!definitions.has(type)) {
            ObjectNode typeDefinition = mapper.createObjectNode();
            definitions.set(type, typeDefinition);
            Object[] typeIds = ((Scriptable) value).getIds();
            createProperties(mapper, library, typeDefinition, (Scriptable) value, typeIds);
         }

         return type;
      }
      else if(value instanceof Number) {
         return "number";
      }
      else if(value instanceof Boolean) {
         return "bool";
      }
      else if(value instanceof String) {
         return "string";
      }
      else if(value instanceof Date) {
         return "+Date";
      }
      else if(value != null && value.getClass().isArray()) {
         Class<?> componentClass = value.getClass().getComponentType();

         if(Number.class.isAssignableFrom(componentClass)) {
            return "[number]";
         }
         else if(Boolean.class.isAssignableFrom(componentClass)) {
            return "[bool]";
         }
         else if(String.class.isAssignableFrom(componentClass)) {
            return "[string]";
         }
         else if(Scriptable.class.isAssignableFrom(componentClass)) {
            if(Array.getLength(value) == 0) {
               return "[?]";
            }

            return "[" + getScriptType(mapper, library, Array.get(value, 0)) + "]";
         }
      }

      return "?";
   }

   /**
    * create nodes by ids.
    */
   private List<TreeNodeModel> createNodes(Object[] ids, String parentName,
                                           String parentLabel, Object parentData,
                                           String name)
   {
      List<TreeNodeModel> nodes = new ArrayList<>();

      if(ids == null || ids.length == 0) {
         return null;
      }

      Arrays.sort(ids);

      for(Object id : ids) {
         String name0 = (String) id;
         TreeNodeModel node = createNode(
            name0, name0, true, parentName, parentLabel, parentData, name,
            null, Collections.emptyList(), false, null, false, true);
         nodes.add(node);
      }

      return nodes;
   }

   private void createBindingInfoDefinition(ObjectMapper mapper, ObjectNode library,
                                            ObjectNode object, PropertyScriptable bindingScriptable)
   {
      createProperties(mapper, library, object, bindingScriptable, bindingScriptable.getIds());
   }

   /**
    * create nodes by binding info for the cross tab or chart.
    */
   private List<TreeNodeModel> createBindingInfoNodeList(
      PropertyScriptable bindingScriptable, String parentName, String parentLabel,
      Object parentData)
   {
      Object[] ids = bindingScriptable.getIds();

      if(ids == null || ids.length == 0) {
         return null;
      }

      return createNodes(ids, parentName, parentLabel, parentData, "bindingInfo");
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children,
         false, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean dot)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children,
         false, dot);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    boolean dot)
   {
      return createNode(
         label, data, leaf, parentName, parentLabel, parentData, name, suffix, children, expanded,
         null, dot, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    List<String> fields, boolean dot, boolean component)
   {
      return TreeNodeModel.builder()
         .label(label)
         .leaf(leaf)
         .expanded(expanded)
         .children(children)
         .data(ScriptTreeNodeData.builder()
                  .data(data)
                  .dot(dot)
                  .parentName(parentName)
                  .parentLabel(parentLabel)
                  .parentData(parentData)
                  .name(name)
                  .suffix(suffix)
                  .fields(fields)
                  .build())
         .build();
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    List<String> fields, boolean dot, String type)
   {
      return TreeNodeModel.builder()
         .label(label)
         .leaf(leaf)
         .expanded(expanded)
         .children(children)
         .type(type)
         .data(ScriptTreeNodeData.builder()
                  .data(data)
                  .dot(dot)
                  .parentName(parentName)
                  .parentLabel(parentLabel)
                  .parentData(parentData)
                  .name(name)
                  .suffix(suffix)
                  .component(null)
                  .fields(fields)
                  .build())
         .build();
   }

   /**
    * Get sheet parameters.
    */
   private void collectSheetParameters(ViewsheetService engine,
                                       RuntimeViewsheet rvs, Set<String> aids)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      ViewsheetInfo vinfo = vs.getViewsheetInfo();

      if(vinfo == null) {
         return;
      }

      List params = new ArrayList();

      try {
         VSEventUtil.refreshParameters(engine, rvs.getViewsheetSandbox(),
                                       vs, false, null, params, true);
      }
      catch(Exception ex) {
         // ignore it
      }

      for(Object obj : params) {
         if(obj != null) {
            aids.add(((UserVariable) obj).getName());
         }
      }
   }

   /**
    * Get avaliable columns.
    */
   private ColumnSelection getColumns(RuntimeViewsheet rvs, Viewsheet vs,
                                      Assembly vassembly, Assembly wassembly,
                                      String tname)
   {
      ColumnSelection columns = null;
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box != null && vassembly instanceof ChartVSAssembly) {
         String cname = vassembly.getName();

         try {
            DataSet data = (DataSet) box.getData(cname);

            if(data != null) {
               VSDataSet vdata = null;
               DataSet tmp = data;

               while(tmp != null) {
                  if(tmp instanceof VSDataSet) {
                     vdata = (VSDataSet) tmp;
                     break;
                  }

                  tmp = tmp instanceof DataSetFilter ?
                     ((DataSetFilter) tmp).getDataSet() : null;
               }

               if(vdata != null) {
                  TableLens table = vdata.getTable();

                  if(table != null) {
                     columns = new ColumnSelection();

                     for(int c = 0; c < table.getColCount(); c++) {
                        Object header = Util.getHeader(table, c);
                        columns.addAttribute(new ColumnRef(
                           new AttributeRef(null, header.toString())));
                     }
                  }
               }
               else if(data != null) {
                  columns = new ColumnSelection();

                  for(int c = 0; c < data.getColCount(); c++) {
                     columns.addAttribute(new ColumnRef(
                        new AttributeRef(null, data.getHeader(c))));
                  }
               }
            }
         }
         catch(Exception ex) {
            columns = null;
            // ignore it
         }
      }

      if(columns == null) {
         if(wassembly instanceof CubeTableAssembly) {
            ColumnSelection cols0 =
               ((CubeTableAssembly) wassembly).getColumnSelection();
            columns = getColumns(vassembly, cols0);
         }
         else {
            columns = ((TableAssembly) wassembly).getColumnSelection().clone();
         }

         VSUtil.appendCalcFields(columns, tname, vs, true);
      }

      return columns;
   }

   /**
    * Get the binding columns of the assembly.
    * @param vassembly the viewsheet assembly.
    */
   private ColumnSelection getColumns(Assembly vassembly, ColumnSelection cols0)
   {
      ColumnSelection columns = new ColumnSelection();

      if(vassembly instanceof CrosstabVSAssembly &&
         ((CrosstabVSAssembly) vassembly).getVSCrosstabInfo() != null)
      {
         DataRef[] aggs = ((CrosstabVSAssembly)
            vassembly).getVSCrosstabInfo().getRuntimeAggregates();
         DataRef[] rows = ((CrosstabVSAssembly)
            vassembly).getVSCrosstabInfo().getRuntimeRowHeaders();
         DataRef[] cols = ((CrosstabVSAssembly)
            vassembly).getVSCrosstabInfo().getRuntimeColHeaders();
         fixColumns(cols0, aggs, columns, false);
         fixColumns(cols0, rows, columns, false);
         fixColumns(cols0, cols, columns, false);
      }
      else if(vassembly instanceof ChartVSAssembly &&
         ((ChartVSAssembly) vassembly).getBindingRefs() != null)
      {
         DataRef[] refs = ((ChartVSAssembly) vassembly).getBindingRefs();
         fixColumns(cols0, refs, columns, false);
      }

      return columns;
   }

   /**
    * Fix the columns with the .
    * @param cols0 the old column selection in the viewsheet assembly.
    * @param refs the dataref from the worksheet assembly.
    * @param columns the new column selection will be store the columns
    * in therefs.
    */
   private void fixColumns(ColumnSelection cols0, DataRef[] refs,
                           ColumnSelection columns, Boolean isColumnRef)
   {
      for(int i = 0; i < refs.length; i++) {
         if(isColumnRef && cols0.containsAttribute(refs[i])) {
            columns.addAttribute(refs[i]);
         }
         else if(!isColumnRef &&
            cols0.containsAttribute(new ColumnRef(refs[i])))
         {
            columns.addAttribute(new ColumnRef(refs[i]));
         }
      }
   }

   private static String getUrl(String funcName) {
      if(!FUNCTION_CSHIDS.containsKey(funcName)) {
         return null;
      }

      return "https://www.inetsoft.com/docs/stylebi/index.html#cshid=" +
         FUNCTION_CSHIDS.get(funcName);
   }

   private static final Set sreeOnly = new HashSet();

   static {
      sreeOnly.add("showReplet");
      sreeOnly.add("showReport");
      sreeOnly.add("showURL");
      sreeOnly.add("promptParameters");
      sreeOnly.add("sendRequest");
      sreeOnly.add("refresh");
      sreeOnly.add("reprint");
      sreeOnly.add("setChanged");
      sreeOnly.add("scrollTo");
      sreeOnly.add("showStatus");
      sreeOnly.add("dataBinding");
   }

   public ObjectNode getScriptDefinition(RuntimeWorksheet rws, TableAssembly table,
                                         Principal principal) throws Exception
   {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = createScriptDefinitions(mapper);
      createParameterDefinitions(mapper, root, rws, principal);
      createFieldDefinitions(mapper, root, table);
      return root;
   }

   private void createParameterDefinitions(ObjectMapper mapper, ObjectNode library,
                                           RuntimeWorksheet rws, Principal principal)
      throws Exception
   {
      ObjectNode parameters = mapper.createObjectNode();
      library.set("parameter", parameters);

      Worksheet ws = rws.getWorksheet();
      Set<String> added = new HashSet<>();

      for(Assembly assembly : ws.getAssemblies()) {
         WSAssembly wsAssembly = (WSAssembly) assembly;

         if(wsAssembly.isVariable() && wsAssembly.isVisible()) {
            VariableAssembly variableAssembly = (VariableAssembly) wsAssembly;
            UserVariable variable = variableAssembly.getVariable();

            if(variable != null && !added.contains(variable.getName())) {
               added.add(variable.getName());
               String type = "?";

               if(variable.getTypeNode() != null) {
                  type = getScriptType(variable.getTypeNode().getType());
               }

               parameters.put(variable.getName(), type);
            }
         }
      }

      // for bug1291823096435, display the parameters which is defined in SRPrincipal
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      box.setBaseUser(principal);
      VariableTable vtable = box.getVariableTable();
      Enumeration<?> keys = vtable.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();

         if(!added.contains(key)) {
            added.add(key);
            Object value = vtable.get(key);
            createProperty(mapper, library, parameters, key, value, null, null);
         }
      }
   }

   private void createFieldDefinitions(ObjectMapper mapper, ObjectNode library, TableAssembly table)   {
      boolean isMV = false; // TODO
      ColumnSelection columns = table.getColumnSelection(false);
      ObjectNode fields = mapper.createObjectNode();
      library.set("field", fields);

      if(isMV) {
//         for(var i:int = 0; i < MV_PROPS.length; i++) {
//            fields.push(new AttributeRef(null, MV_PROPS[i]));
//         }
      }
      else {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef ref = columns.getAttribute(i);

            while(ref instanceof DataRefWrapper) {
               ref = ((DataRefWrapper) ref).getDataRef();

               if(ref instanceof ColumnRef) {
                  break;
               }
            }

            if(ref != null && !(ref instanceof ColumnRef)) {
               ref = new ColumnRef(ref);
            }

            ColumnRef dref = (ColumnRef) ref;

            if(dref == null || dref.isExpression()) {
               continue;
            }

            String name = dref.getName() == null ? dref.getView() : dref.getName();
            String type = getScriptType(dref.getDataType());
            fields.put(name, type);
         }
      }
   }

   private void createFieldDefinitions(ObjectMapper mapper, ObjectNode library,
                                       RuntimeViewsheet rvs, Viewsheet viewsheet,
                                       String assemblyName, String tableName, boolean isCondition)
   {
      if(isCondition) {
         return;
      }

      Assembly vassembly = viewsheet.getAssembly(assemblyName);

      if(vassembly == null || !(vassembly instanceof DataVSAssembly)) {
         return;
      }

      Worksheet ws = viewsheet.getBaseWorksheet();

      if(ws == null) {
         return;
      }

      tableName = tableName == null ? ((VSAssembly) vassembly).getTableName() : tableName;
      Assembly assembly = ws.getAssembly(tableName);

      if(assembly == null) {
         return;
      }

      ColumnSelection columns = getColumns(rvs, viewsheet, vassembly, assembly, tableName);
      ObjectNode fields = mapper.createObjectNode();
      library.set("fields", fields);

      Enumeration<?> attrs = columns.getAttributes();

      while(attrs.hasMoreElements()) {
         ColumnRef col = (ColumnRef) attrs.nextElement();
         DataRef ref = col.getDataRef();
         String field = col.getAttribute();

         if((vassembly instanceof CrosstabVSAssembly) || (vassembly instanceof ChartVSAssembly)) {
            String caption = GraphUtil.getCaption(ref);
            field = caption != null && caption.length() != 0 ? caption : field;
         }

         if(field == null || !col.isVisible()) {
            continue;
         }

         fields.put(field, getScriptType(col.getDataType()));
      }
   }

   private String getScriptType(String schemaType) {
      return XUtil.getScriptType(schemaType);
   }






   private ViewsheetService viewsheetService;
   private static final Map<String, String> FUNCTION_CSHIDS = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(VSScriptableService.class);

   static {
      FUNCTION_CSHIDS.put("createBulletGraph", "createBulletGraph");
   }
}
