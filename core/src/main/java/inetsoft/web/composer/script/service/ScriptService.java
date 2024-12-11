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
package inetsoft.web.composer.script.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.composition.SheetLibraryService;
import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.report.internal.graph.MapData;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.util.ItemMap;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.binding.model.ScriptTreeNodeData;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.analytic.web.adhoc.AdHocQueryHandler.DOT_FLAG;

@Service
public class ScriptService {
   @Autowired
   public ScriptService(SheetLibraryService sheetLibraryService,
                        AssetRepository assetRepository,
                        ResourcePermissionService permissionService
                        ) {
      this.sheetLibraryService = sheetLibraryService;
      this.assetRepository = assetRepository;
      this.permissionService = permissionService;
   }

   public TreeNodeModel getFunctionTree(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      String rootLabel = catalog.getString("Functions");
      String rootName = "Functions";
      String jsFunctionLabel = catalog.getString("JavaScript Functions");
      String jsFunctionName = "JavaScript Functions";
      String excelFunctionLabel = catalog.getString("Excel-style Functions");
      String excelFunctionName = "Excel-style Functions";

      ItemMap functionMap = AdHocQueryHandler.getScriptFunctions();
      ItemMap excelFunctionMap = AdHocQueryHandler.getExcelScriptFunctions();

      TreeNodeModel jsFunctionsNode = createNode(jsFunctionLabel, null, false, rootName,
         rootLabel, null, jsFunctionName, null,
         getChildrenNodesFromMap(functionMap, jsFunctionName, jsFunctionLabel, catalog), false);
      TreeNodeModel excelFunctionsNode = createNode(excelFunctionLabel, null, false, rootName,
         rootLabel, null, excelFunctionName, null,
         getChildrenNodesFromMap(excelFunctionMap, excelFunctionName, excelFunctionLabel, catalog), false);

      return createNode(rootLabel, null, false, null, null,
         null, rootName, null, Arrays.asList(jsFunctionsNode, excelFunctionsNode), false);
   }

   private List<TreeNodeModel> getChildrenNodesFromMap(ItemMap map, String parentName,
                                                       String parentLabel, Catalog catalog) {
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

            nodes.add(createNode(nodeInfo[0], nodeInfo[1], true, funcName, funcLabel, null,
               "Function" + i + "|" + j, null, Collections.emptyList(), dotScope || dotItem));
         }

         fnodes.add(createNode(funcLabel, null, false, parentName, parentLabel,
            null, funcName, null, nodes));
      }

      fnodes.sort((node1, node2) -> node1.compareTo(node2));

      return fnodes;
   }

   public TreeNodeModel createNode(String label, String data, boolean leaf, String parentName, String parentLabel,
                                    Object parentData, String name, String suffix, List<TreeNodeModel> children) {
      return createNode(label, data, leaf, parentName, parentLabel, parentData, name, suffix,
         children, false, false);
   }

   public TreeNodeModel createNode(String label, String data, boolean leaf, String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean dot) {
      return createNode(label, data, leaf, parentName, parentLabel, parentData, name, suffix,
         children, false, dot);
   }

   public TreeNodeModel createNode(String label, String data, boolean leaf, String parentName, String parentLabel,
                                    Object parentData, String name, String suffix,
                                    List<TreeNodeModel> children, boolean expanded, boolean dot) {
      return createNode(label, data, leaf, parentName, parentLabel, parentData, name, suffix, children,
         expanded, null, dot);
   }


   public TreeNodeModel createNode(String label, String data, boolean leaf, String parentName, String parentLabel,
                                    Object parentData, String name, String suffix, List<TreeNodeModel> children,
                                    boolean expanded, List<String> fields, boolean dot) {
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

   public ObjectNode createScriptDefinitions(ObjectMapper mapper) throws IOException {
      ObjectNode root = mapper.createObjectNode();
      root.put("!name", "inetsoft");
      root.set("!define", mapper.createObjectNode());
      createStaticDefinitions(mapper, root);
      createUserDefinedScript(mapper, root);
      return root;
   }

   public void createStaticDefinitions(ObjectMapper mapper, ObjectNode library) throws IOException {
      ObjectNode functions = (ObjectNode)
         mapper.readTree(getClass().getResource("/inetsoft/web/binding/js-functions.json"));
      ObjectNode generated = (ObjectNode) mapper.readTree(
         getClass().getResource("/inetsoft/web/binding/js-functions.generated.json"));

      for(Iterator<Map.Entry<String, JsonNode>> it = generated.fields(); it.hasNext(); ) {
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

      for(String mapType : MapData.getMapTypes()) {
         ObjectNode typeNode = mapper.createObjectNode();
         typeNode.put("!type", "string");
         chartConstants.set("MAP_TYPE_" + mapType.toUpperCase(), typeNode);
      }
   }

   public void createUserDefinedScript(ObjectMapper mapper, ObjectNode root) {
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

   public void updateScriptDependencies(String oscript, String nscript, AssetEntry entry) {
      DependencyHandler.getInstance().updateScriptDependencies(oscript, nscript, entry);
   }

   public static String getUrl(String funcName) {
      if(!FUNCTION_CSHIDS.containsKey(funcName)) {
         return null;
      }

      return "https://www.inetsoft.com/docs/stylebi/index.html#cshid=" +
         FUNCTION_CSHIDS.get(funcName);
   }

   public static final Set sreeOnly = new HashSet();

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


   private static final Map<String, String> FUNCTION_CSHIDS = new HashMap<>();
   private final SheetLibraryService sheetLibraryService;
   private final AssetRepository assetRepository;
   private final ResourcePermissionService permissionService;
}
