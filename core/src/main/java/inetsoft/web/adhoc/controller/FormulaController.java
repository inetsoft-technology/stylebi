/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.adhoc.controller;

import inetsoft.analytic.web.adhoc.AdHocQueryHandler;
import inetsoft.util.*;
import inetsoft.web.binding.model.ScriptTreeNodeData;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.reportviewer.HandleExceptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.analytic.web.adhoc.AdHocQueryHandler.DOT_FLAG;

@RestController
public class FormulaController {
   /**
    * Creates a new instance of <tt>FormulaController</tt>.
    */
   @Autowired
   public FormulaController() {
   }

   @RequestMapping(value = "/api/ws/formula/function", method=RequestMethod.GET)
   @HandleExceptions
   public TreeNodeModel getFunctions() throws Exception {
      ItemMap functionMap = AdHocQueryHandler.getScriptFunctions(false);
      ItemMap excelFunctionMap = AdHocQueryHandler.getExcelScriptFunctions();
      TreeNodeModel jsFunctionsNode = createNode(Catalog.getCatalog().getString("JavaScript Functions"),
         null, false, false,
         getChildrenNodesFromMap(functionMap), null);
      TreeNodeModel excelFunctionsNode = createNode(Catalog.getCatalog().getString("Excel-style Functions"),
         null, false, false,
         getChildrenNodesFromMap(excelFunctionMap), null);

      return createNode(
         Catalog.getCatalog().getString("Functions"), null, false, true,
         Arrays.asList(jsFunctionsNode, excelFunctionsNode), null);
   }

   @RequestMapping(value = "/api/ws/formula/operation", method=RequestMethod.GET)
   @HandleExceptions
   public TreeNodeModel getOperation() throws Exception {
      ItemMap operatorMap = AdHocQueryHandler.getScriptOperators();
      Iterator iter = operatorMap.itemKeys();
      List<TreeNodeModel> children = new ArrayList<>();

      while(iter.hasNext()) {
         String key = (String) iter.next();
         List<TreeNodeModel> children0 = new ArrayList<>();

         String alloperators = operatorMap.getItem(key).toString();
         String[] operatorNames = alloperators.split("\\$");

         for(String name: operatorNames) {
            String[] nodeInfo = name.split(";");
            String description;

            if(nodeInfo[1].contains("::")) {
               description = nodeInfo[1].substring(2);
            }
            else {
               description = nodeInfo[1];
            }

            description = nodeInfo[0] + (description.trim().length() != 0 ?
               "  (" + Catalog.getCatalog().getString(description) + ")" : "");

            TreeNodeModel child = createNode(description, nodeInfo[2], true, false);
            children0.add(child);
         }

         TreeNodeModel node = createNode(Catalog.getCatalog().getString(key),
            null, false, false, children0, null);
         children.add(node);
      }

      return createNode(
         Catalog.getCatalog().getString("Operators"), null, false, true, children, null);
   }

   private TreeNodeModel createNode(String label, String data, boolean isLeaf,
                                    boolean expanded)
   {
      return createNode(label, data, isLeaf, expanded, Collections.emptyList(), null, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean isLeaf,
                                    boolean expanded, boolean dot)
   {
      return createNode(label, data, isLeaf, expanded, Collections.emptyList(), null, dot);
   }

   private TreeNodeModel createNode(String label, String data, boolean isLeaf,
                                    boolean expanded, List<TreeNodeModel> children, String icon)
   {
      return createNode0(label, data, isLeaf, expanded, children, null, null, icon, false);
   }

   private TreeNodeModel createNode(String label, String data, boolean isLeaf,
                                    boolean expanded, List<TreeNodeModel> children, String icon,
                                    boolean dot)
   {
      return createNode0(label, data, isLeaf, expanded, children, null, null, icon, dot);
   }

   private TreeNodeModel createNode0(String label, String data, boolean isLeaf,
                                     boolean expanded, List<TreeNodeModel> children,
                                     String tip, String type, String icon, boolean dot)
   {
      return TreeNodeModel.builder()
         .label(label)
         .data(ScriptTreeNodeData.builder()
            .data(data)
            .dot(dot)
            .build()
         )
         .leaf(isLeaf)
         .expanded(expanded)
         .children(children)
         .tooltip(tip)
         .type(type)
         .icon(icon)
         .build();
   }

   private List<TreeNodeModel> getChildrenNodesFromMap(ItemMap map) {
      Iterator iter = map.itemKeys();
      List<TreeNodeModel> children = new ArrayList<>();

      while(iter.hasNext()) {
         String key = (String) iter.next();
         String funcLabel = key;
         List<String> labelInfo = Arrays.asList(funcLabel.split(";"));
         boolean dotScope = false;

         if(labelInfo.size() > 1 && labelInfo.contains(DOT_FLAG)) {
            dotScope = true;
            funcLabel = labelInfo.stream()
               .filter(part -> !Objects.equals(part, DOT_FLAG))
               .collect(Collectors.joining(";"));
         }

         List<TreeNodeModel> children0 = new ArrayList<>();

         String allfuncs = map.getItem(key).toString();
         String[] funcNames = allfuncs.split("\\^");

         for(String name: funcNames) {
            String[] nodeInfo = name.split(";");
            boolean dotItem = false;

            if(!dotScope && Arrays.asList(nodeInfo).contains(DOT_FLAG)) {
               dotItem = true;
            }

            TreeNodeModel child = createNode(nodeInfo[0], nodeInfo[1], true, false,
               dotScope || dotItem);
            children0.add(child);
         }

         TreeNodeModel node = createNode(Catalog.getCatalog().getString(funcLabel),
            null, false, false, children0, null);
         children.add(node);
      }

      return children;
   }
}
