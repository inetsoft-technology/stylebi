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
package inetsoft.web.admin.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.util.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.web.binding.model.ScriptTreeNodeData;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.analytic.web.adhoc.AdHocQueryHandler.DOT_FLAG;

@Service
public class ScheduleTaskFormulaService {
   /**
    * Get script definition for the schedule task parameters.
    *
    * @return
    * @throws Exception
    */
   public ObjectNode getScriptDefinition() throws Exception
   {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = createScriptDefinitions(mapper);

      return root;
   }

   private ObjectNode createScriptDefinitions(ObjectMapper mapper) throws IOException {
      ObjectNode root = mapper.createObjectNode();
      root.put("!name", "inetsoft");
      root.set("!define", mapper.createObjectNode());
      createStaticDefinitions(mapper, root);
      createUserDefinedScript(mapper, root);
      createTaskScopeScriptDefinition(mapper, root);

      return root;
   }

   private void createTaskScopeScriptDefinition(ObjectMapper mapper, ObjectNode root) {
      ScheduleParameterScope scheduleParameterScope = new ScheduleParameterScope();
      Object[] ids = scheduleParameterScope.getIds();

      if(ids != null) {
         for(Object id : ids) {
            if(!(id instanceof String)) {
               continue;
            }

            ObjectNode node = mapper.createObjectNode();
            node.put("prototype", "{}");
            node.put("!url", Tool.getHelpBaseURL() + "userhelp/#cshid=EMAddParameter");
            root.put(id.toString(), node);
         }
      }
   }

   private void createUserDefinedScript(ObjectMapper mapper, ObjectNode root) {
      List<String> list = AdHocQueryHandler.getUserDefinedScriptFunctions();

      if(list.size() == 0) {
         return;
      }

      for(int i = 0; i < list.size(); i++) {
         ObjectNode node = mapper.createObjectNode();
         node.put("!type", "fn()");
         node.put("prototype", "{}");
         root.put(list.get(i), node);
      }
   }

   private void createStaticDefinitions(ObjectMapper mapper, ObjectNode library)
      throws IOException
   {
      ObjectNode functions = (ObjectNode) mapper.readTree(
         getClass().getResource("/inetsoft/web/binding/js-functions.json"));
      library.setAll(functions);
   }

   /**
    * Test the schedule task parameter script.
    *
    * @param script script.
    * @return
    */
   public String testScheduleParameterExpression(String script) {
      Catalog catalog = Catalog.getCatalog();

      try {
         ScheduleParameterScope scope = new ScheduleParameterScope();
         ScriptEnv scriptEnv = scope.getScriptEnv();
         scriptEnv.addTopLevelParentScope(scope);
         scriptEnv.exec(scriptEnv.compile(script), scope, null, null);
      }
      catch(Exception ex) {
         return catalog.getString("schedule.task.action.scriptTestFailed", ex.getMessage());
      }

      return null;
   }

   public TreeNodeModel getFunctions() {
      Catalog catalog = Catalog.getCatalog();
      String rootLabel = catalog.getString("Functions");
      String rootName = "Functions";
      String jsFunctionLabel = catalog.getString("JavaScript Functions");
      String jsFunctionName = "JavaScript Functions";
      String excelFunctionLabel = catalog.getString("Excel-style Functions");
      String excelFunctionName = "Excel-style Functions";

      ItemMap functionMap = AdHocQueryHandler.getScriptFunctions(false, true);
      ItemMap excelFunctionMap = AdHocQueryHandler.getExcelScriptFunctions();

      TreeNodeModel jsFunctionsNode = createNode(
         jsFunctionLabel, null, false, rootName, rootLabel, null, jsFunctionName,
         null, getChildrenNodesFromMap(functionMap, jsFunctionName, jsFunctionLabel, catalog),
         false);
      TreeNodeModel excelFunctionsNode = createNode(
         excelFunctionLabel, null, false, rootName, rootLabel, null, excelFunctionName,
         null, getChildrenNodesFromMap(excelFunctionMap, excelFunctionName, excelFunctionLabel, catalog),
         false);

      return createNode(
         rootLabel, null, false, null, null, null, rootName,
         null, Arrays.asList(jsFunctionsNode, excelFunctionsNode), false);
   }

   public TreeNodeModel getOperationTree() {
      Catalog catalog = Catalog.getCatalog();
      String rootLabel = catalog.getString("Operators");
      String rootName = "Operators";

      ItemMap ops = AdHocQueryHandler.getScriptOperators();
      List<TreeNodeModel> fnodes = new ArrayList<>();
      Iterator<?> keys = ops.itemKeys();

      for(int i = 0; keys.hasNext(); i++) {
         String key = (String) keys.next();
         String funcLabel = catalog.getString(key);
         String funcName = "Operator" + i;

         List<TreeNodeModel> nodes = new ArrayList<>();
         String allFuncs = ops.getItem(key).toString();
         String[] funcs = allFuncs.split("\\$");

         for(int j = 0; j < funcs.length; j++) {
            String[] nodeInfo = funcs[j].split(";");
            String nodeName = "Operator" + i + "|" + j;
            String description;

            if(nodeInfo[1].contains("::")) {
               description = nodeInfo[1].substring(2);
            }
            else {
               description = nodeInfo[1];
            }

            String nodeLabel = nodeInfo[0];

            if(!description.trim().isEmpty()) {
               nodeLabel += " (" + catalog.getString(description) + ")";
            }

            String nodeData = nodeInfo[2];
            nodes.add(createNode(
               nodeLabel, nodeData, true, funcName, funcLabel, null, nodeName, null,
               Collections.emptyList()));
         }

         fnodes.add(createNode(
            funcLabel, null, false, rootName, rootLabel, null, funcName, null, nodes));
      }

      return createNode(
         rootLabel, null, false, null, null, null, rootName, null, fnodes, false);
   }

   private List<TreeNodeModel> getChildrenNodesFromMap(ItemMap map, String parentName,
                                                       String parentLabel, Catalog catalog)
   {
      List<TreeNodeModel> fnodes = new ArrayList<>();
      Iterator<?> keys = map.itemKeys();

      for(int i = 0; keys.hasNext(); i++) {
         String key = (String) keys.next();
         String funcLabel = key;
         List<String> labelInfo = Arrays.asList(funcLabel.split(";"));
         boolean dotScope = false;

         if(labelInfo.size() > 1 && labelInfo.contains(DOT_FLAG)) {
            dotScope = true;
            funcLabel = labelInfo.stream()
               .filter(part -> !Objects.equals(part, DOT_FLAG))
               .collect(Collectors.joining(";"));
         }

         funcLabel = catalog.getString(funcLabel);
         String funcName = "Function" + i;

         List<TreeNodeModel> nodes = new ArrayList<>();
         String allFuncs = map.getItem(key).toString();
         String[] funcNames = allFuncs.split("\\^");

         for(int j = 0; j < funcNames.length; j++) {
            String[] nodeInfo = funcNames[j].split(";");
            boolean dotItem = false;

            if(!dotScope && Arrays.asList(nodeInfo).contains(DOT_FLAG)) {
               dotItem = true;
            }

            nodes.add(createNode(
               nodeInfo[0], nodeInfo[1], true, funcName, funcLabel, null,
               "Function" + i + "|" + j, null, Collections.emptyList(),
               dotScope || dotItem));
         }

         fnodes.add(createNode(
            funcLabel, null, false, parentName, parentLabel, null, funcName,
            null, nodes));
      }

      return fnodes;
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
         null, dot);
   }

   private TreeNodeModel createNode(String label, String data, boolean leaf,
                                    String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded,
                                    List<String> fields, boolean dot)
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
}
