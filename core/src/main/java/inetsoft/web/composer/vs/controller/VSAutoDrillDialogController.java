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
package inetsoft.web.composer.vs.controller;

import inetsoft.report.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import inetsoft.web.composer.model.SortInfoModel;
import inetsoft.web.composer.model.VSAutoDrillDialogModel;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.composer.model.vs.HyperlinkModel.ParameterValueModel;
import inetsoft.web.composer.vs.objects.event.AutoDrillEvent;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

@RestController
public class VSAutoDrillDialogController {
   @PostMapping("api/get-drill-model")
   public VSAutoDrillDialogModel getDrillModel(@RequestBody AutoDrillEvent drillevent,
      Principal principal) throws Exception
   {
      HyperlinkModel linkModel = drillevent.getLink();
      SortInfoModel sortInfo = drillevent.getSortInfo();
      TableLens lens = null;
      boolean wsSource = !StringUtils.isEmpty(linkModel.getWsIdentifier());

      if(wsSource) {
         lens = executeWsTable(linkModel, principal);
      }

      if(sortInfo != null && sortInfo.getSortValue() != XConstants.SORT_NONE) {
         int col = Util.findColumn(lens, sortInfo.getField());
         lens = new SortFilter(lens, new int[] {col},
            sortInfo.getSortValue() == XConstants.SORT_ASC);
      }

      if(lens != null) {
         lens.moreRows(Integer.MAX_VALUE);
      }

      VSAutoDrillDialogModel model = getDataModel(lens, linkModel, wsSource, principal);
      return model;
   }

   private VSAutoDrillDialogModel getDataModel(TableLens data, HyperlinkModel linkModel,
                                               boolean wsSource, Principal principal)
      throws Exception
   {
      if(data == null) {
         return null;
      }

      ParameterValueModel[] vals = linkModel.getParameterValues();
      List<String> fieldNames = new ArrayList<>();
      int k = 0;

      for(ParameterValueModel val: vals) {
         String name = val.getName();

         if(name.startsWith(StyleConstants.PARAM_PREFIX)) {
            fieldNames.add(val.getValue());
         }
      }

      Collections.sort(fieldNames);
      List<Integer> params = new ArrayList<>();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(data, true);

      for(String fieldName : fieldNames) {
         int col = Util.findColumn(columnIndexMap, fieldName);

         if(col < 0) {
            continue;
         }

         params.add(col);
      }

      String[][] arrs = new String[data.getRowCount()][data.getColCount()];

      while(data.moreRows(k)) {
         List<String> rows = new ArrayList<>();
         boolean header = k < data.getHeaderRowCount();

         for(int j = 0; params != null && j < params.size(); j++) {
            Object headerStr = data.getObject(k, params.get(j));

            if(wsSource && header) {
               ColumnSelection columnSelection =
                  getDependingWSColumnSelection(linkModel.getWsIdentifier(), principal);
               int colIndex = params.get(j);

               if(columnSelection != null && colIndex < columnSelection.getAttributeCount() &&
                  columnSelection.getAttribute(colIndex).getName() != null)
               {
                  headerStr = columnSelection.getAttribute(colIndex).getName();
               }
            }

            String val = Tool.encodeParameter(headerStr);
            val = getSpecialCharacters(val);
            rows.add(val);
         }

         arrs[k] = rows.toArray(new String[rows.size()]);
         k++;
      }

      VSAutoDrillDialogModel model = new VSAutoDrillDialogModel(arrs);

       return model;
   }

   private ColumnSelection getDependingWSColumnSelection(String wsIdentifier, Principal principal)
      throws Exception
   {
      if(wsIdentifier == null) {
         return null;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(wsIdentifier);
      AbstractSheet sheet = engine.getSheet(entry, principal, false, AssetContent.ALL);

      if(sheet == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      Worksheet ws = (Worksheet) sheet;
      WSAssembly assembly = ws.getPrimaryAssembly();

      if(!(assembly instanceof TableAssembly table)) {
         return null;
      }

      ColumnSelection columns = table.getColumnSelection(true);

      return columns == null || columns.isEmpty() ? null : columns;
   }

   private TableLens executeWsTable(HyperlinkModel linkModel, Principal principal) throws Exception
   {
      String wsIdentifier = linkModel.getWsIdentifier();
      AssetEntry entry = AssetEntry.createAssetEntry(wsIdentifier);
      AbstractSheet sheet = engine.getSheet(entry, principal, true, AssetContent.ALL);

      if(sheet == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.sheetCannotFount", entry.toString()));
      }

      Worksheet ws = (Worksheet) sheet;
      WSAssembly assembly = ws.getPrimaryAssembly();

      if(!(assembly instanceof TableAssembly)) {
         LOG.warn("Primary assembly of {0} is not a table", entry);
         return null;
      }

      HyperlinkModel.ParameterValueModel[] paramValues = linkModel.getParameterValues();
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setVPMUser((XPrincipal) principal);
      VariableTable variableTable = new VariableTable();
      UserVariable[] vars = AssetEventUtil.executeVariables(
         null, box, variableTable, null, null, null, variableTable, true);
      String varName = null;

      for(int i = 0; vars != null && i < vars.length; i++) {
         UserVariable userVar = vars[i];

         if(userVar == null) {
            continue;
         }

         varName = userVar.getName();
         String val = getQvarName(paramValues, varName);
         Object oval = val == null ? null : val;

         if(oval != null) {
            // remove prefix for date if it exists
            oval = Tool.removeDateParamPrefix(oval.toString());
            XTypeNode xtype = userVar.getTypeNode();

            if(xtype != null) {
               String type = xtype.getType();
               oval = Tool.getData(type, oval);
            }
         }

         variableTable.put(varName, oval);
      }

      if(variableTable.size() == 1 && !variableTable.contains(varName)) {
         variableTable.put(varName, getSubQueryName(paramValues));
      }

      return box.getTableLens(assembly.getName(), AssetQuerySandbox.RUNTIME_MODE, variableTable);
   }

   public String getQvarName(HyperlinkModel.ParameterValueModel[] paramValues, String qvarName) {
      String val = null;

      for(int i = 0; i < paramValues.length; i++) {
         if(paramValues[i].getName().equals(
               Tool.byteDecode(StyleConstants.SUB_QUERY_PARAM_PREFIX + qvarName))) {
            val = paramValues[i].getValue();
            return val;
         }
      }

      return val;
   }

   public String getSubQueryName(HyperlinkModel.ParameterValueModel[] paramValues) {
      String val = null;

      for(int i = 0; i < paramValues.length; i++) {
         if(paramValues[i].getName().equals(StyleConstants.SUB_QUERY_PARAM)) {
            val = paramValues[i].getValue();
            return val;
         }
      }

      return val;
   }

   public String getSpecialCharacters(String data) {
      String prefix = Tool.getDateParamPrefix(data);

      if("".equals(data.trim()) || data == null ||
         data.indexOf(NULL) >= 0)
      {
         return "";
      }
      else if(prefix != null) {
         return data.substring(prefix.length());
      }
      else if(data.indexOf(QUOT) >= 0) {
         return data.substring(QUOT.length());
      }

      return data;
   }

   @Autowired
   private AssetRepository engine;
   private static final String NULL = "^null^";
   private static final String QUOT = "^quot^";
   private static final Logger LOG = LoggerFactory.getLogger(VSAutoDrillDialogController.class);
}