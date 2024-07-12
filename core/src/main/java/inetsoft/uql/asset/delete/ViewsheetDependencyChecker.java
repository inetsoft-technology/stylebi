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
package inetsoft.uql.asset.delete;

import inetsoft.report.CellBinding;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.AssetDependencyTransformer;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ViewsheetDependencyChecker extends DependencyChecker {
   public ViewsheetDependencyChecker(AssetEntry asset) {
      this.entry = asset;

      vs = DependencyTool.getAssetElement(entry);
   }

   public AssetEntry getViewsheet() {
      return entry;
   }

   public List<DeleteInfo> hasDependency(List<DeleteInfo> infos, boolean checkAll)
         throws Exception
   {
      if(vs == null) {
         return null;
      }

      List<DeleteInfo> result = new ArrayList<>();
      DeleteInfo dinfo = null;
      NodeList assemblies = DependencyTool.getChildNodes(xpath, vs,
              AssetDependencyTransformer.ASSET_DEPEND_ELEMENTS);

      for(DeleteInfo info : infos) {
         List<DeleteInfo> calcDeleteInfos = getCalcDataRefDeleteInfo(info);
         calcDeleteInfos.add(info);

         for(DeleteInfo deleteInfo : calcDeleteInfos) {
            for(int i = 0; i < assemblies.getLength(); i++) {
               Element assembly = (Element) assemblies.item(i);

               if(assembly == null) {
                  continue;
               }

               Element assemblyInfo = DependencyTool.getChildNode(xpath, assembly,
                  "./assemblyInfo");
               NodeList ainfo = assemblyInfo.getElementsByTagName("ainfo");

               if(ainfo != null && ainfo.getLength() == 1 &&
                  ainfo.item(0).getParentNode() != null)
               {
                  Element aelem = (Element) ainfo.item(0);

                  while(aelem.hasChildNodes()) {
                     aelem.removeChild(aelem.getFirstChild());
                  }

                  aelem.getParentNode().removeChild(aelem);
               }

               // If delete table, only check source is used, show warning.
               if(deleteInfo.isTable()) {
                  if(isSameSource(assembly, deleteInfo)) {
                     dinfo = deleteInfo;
                  }
                  else {
                     continue;
                  }
               }

               if(deleteInfo.isColumn()) {
                  if(!isSameSource(assembly, deleteInfo)) {
                     continue;
                  }

                  dinfo = null;

                  if(checkDataRef(assemblyInfo, deleteInfo) || checkBindingInfo(assembly, deleteInfo) ||
                     checkCellBindings(assembly, deleteInfo) ||
                     checkSelectionMeasureValue(assembly, deleteInfo) ||
                     checkVSFieldValue(assembly, deleteInfo))
                  {
                     dinfo = deleteInfo;
                  }
               }

               if(dinfo != null) {
                  if(!checkAll) {
                     return Collections.singletonList(deleteInfo);
                  }

                  result.add(deleteInfo);
                  break;
               }
            }
         }
      }

      return makeEmptyToNone(result);
   }

   /**
    * Get names of the vs calc data ref that depends on info ref.
    * @param info delete info.
    * @return
    */
   private List<DeleteInfo> getCalcDataRefDeleteInfo(DeleteInfo info) {
      List<DeleteInfo> result = new ArrayList<>();

      if(info == null || vs == null) {
         return result;
      }

      Node allCalc = DependencyTool.getChildNode(xpath, vs, ".//allcalc");

      if(allCalc == null) {
         return result;
      }

      NodeList calcFields = DependencyTool.getChildNodes(xpath, (Element) allCalc,
         ".//dataRef[@class='inetsoft.uql.viewsheet.CalculateRef']/" +
            "dataRef[@class='inetsoft.uql.erm.ExpressionRef']");

      if(calcFields != null && calcFields.getLength() > 0) {
         for(int i = 0; i < calcFields.getLength(); i++) {
            Element element = (Element) calcFields.item(i);

            if(element == null) {
               continue;
            }

            String expression = Tool.getValue(element);

            if(expression == null) {
               continue;
            }

            String refName = Tool.getAttribute(element, "name");

            if(expression.contains("field['" + info.getName() + "']") &&
               !Tool.isEmptyString(refName))
            {
               DeleteInfo deleteInfo = new DeleteInfo(refName, info.getType(), info.getSource(),
                  info.getTable());
               result.add(deleteInfo);
            }
         }
      }

      return result;
   }

   private boolean checkBindingInfo(Element elem, DeleteInfo info) {
      NodeList colValues = DependencyTool.getChildNodes(xpath, elem,
        "./assemblyInfo/bindingInfo/columnValue");

      if(checkNodesValue(colValues, info)) {
         return true;
      }

      NodeList colValues2 = DependencyTool.getChildNodes(xpath, elem,
        "./assemblyInfo/bindingInfo/column2Value");

      if(checkNodesValue(colValues2, info)) {
         return true;
      }

      return false;
   }

   private boolean checkCellBindings(Element elem, DeleteInfo info) {
      NodeList list = DependencyTool.getChildNodes(xpath, elem, ".//cellBinding");

      for(int i = 0; i < list.getLength(); i++) {
         Element binding = (Element) list.item(i);

         if(checkCellBinding(binding, info)) {
            return true;
         }
      }

      return false;
   }

   private boolean checkCellBinding(Element binding, DeleteInfo info) {
      if(("" + CellBinding.BIND_COLUMN).equals(Tool.getAttribute(binding, "type"))) {
         if(checkNodeValue(Tool.getChildNodeByTagName(binding, "value"), info)) {
            return true;
         }
      }
      else if(("" + CellBinding.BIND_FORMULA).equals(Tool.getAttribute(binding, "type"))) {
         if(checkCellBindingToList(binding, info)) {
            return true;
         }
      }

      return false;
   }

   private boolean checkNodesValue(NodeList colValues, DeleteInfo info) {
      for(int i = 0;  colValues != null && i < colValues.getLength(); i++) {
         Element node = (Element) colValues.item(i);

         if(node == null) {
            continue;
         }

         if(checkNodeValue(node, info)) {
            return true;
         }
      }

      return false;
   }

   private boolean checkSelectionMeasureValue(Element assembly, DeleteInfo info) {
      NodeList measures = DependencyTool.getChildNodes(xpath, assembly,
        "./assemblyInfo/measure");

      if(checkNodesValue(measures, info)) {
         return true;
      }

      NodeList measureValues = DependencyTool.getChildNodes(xpath, assembly, "./assemblyInfo" +
         "/measureValue");

      if(checkNodesValue(measureValues, info)) {
         return true;
      }

      return false;
   }

   private boolean checkVSFieldValue(Element assembly, DeleteInfo info) {
      NodeList fields = DependencyTool.getChildNodes(xpath, assembly,
        ".//VSFieldValue[@fieldName]");

      for(int i = 0; i < fields.getLength(); i++) {
         Element fieldValue = (Element) fields.item(i);
         String attr = Tool.getAttribute(fieldValue, "fieldName");

         if(Tool.equals(attr, info.getName())) {
            return true;
         }
      }

      return false;
   }

   private boolean checkNodeValue(Element elem, DeleteInfo info) {
      if(Objects.equals(Tool.getValue(elem), info.getName())) {
         return true;
      }

      return false;
   }

   @Override
   protected boolean isSameSource(Element elem, DeleteInfo info) throws Exception {
      // For query, there is no need to check source, only check data ref and bindings.
      // For vs, it can only binding one query.
      if(info.isQuery()) {
         return true;
      }

      return DependencyTool.isSameVSSource(elem, info, false);
   }

   /**
    * Check the toList formula of cell binding.
    * @param elem cell binding element.
    * @param info delete info.
    * @return
    */
   protected boolean checkCellBindingToList(Element elem, DeleteInfo info) {
      if(elem == null || info == null) {
         return false;
      }

      Element child = Tool.getChildNodeByTagName(elem, "value");
      String val = Tool.getValue(child);

      if(val != null) {
         Matcher matcher = toListPattern.matcher(val);

         if(!matcher.matches()) {
            return false;
         }

         String toListValue = matcher.group(1);

         if(toListValue != null && toListValue.contains("data['" + info.getName() + "']")) {
            return true;
         }
      }

      return false;
   }

   private static final Pattern toListPattern = Pattern.compile("^toList\\(([\\S\\s]*)\\)$");
   private AssetEntry entry;
   private Element vs;
}
