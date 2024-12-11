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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.SortColumnDialogModel;
import inetsoft.web.composer.model.ws.SortColumnEditorModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

@Controller
public class SortColumnDialogController extends WorksheetController {
   @RequestMapping(
      value = "/api/composer/ws/dialog/sort-column-dialog-model/{runtimeid}",
      method = RequestMethod.GET)
   @ResponseBody
   public SortColumnDialogModel getModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tname,
      Principal principal) throws Exception
   {
      SortColumnDialogModel model = null;
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      Worksheet ws = rws.getWorksheet();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      Map<String, String> map = box.getColumnInfoMapping(tname);

      if(table != null) {
         AggregateInfo ainfo = table.getAggregateInfo();
         ColumnSelection cs = table.getColumnSelection();
         List<String> columnDescriptions = new ArrayList<>();
         List<String> originalNames = new ArrayList<>();
         List<String> rangeColumns = new ArrayList<>();
         ColumnSelection sorted = VSUtil.sortColumns(cs);
         Enumeration attributes = sorted.getAttributes();
         List<String> selectColumns = new ArrayList<>();
         List<String> noSelectColumns = new ArrayList<>();

         for(int i = 0; attributes.hasMoreElements(); i++) {
            ColumnRef column = (ColumnRef) attributes.nextElement();
            GroupRef group = ainfo.getGroup(column);

            if(group == null || !group.isTimeSeries()) {
               String colName = column.getName();
               String refName = column.getDataRef().getName();

               if(!colName.equals(refName) && !map.containsKey(refName)) {
                  map.put(refName, colName);
                  colName = refName;
               }
               else {
                  colName = colName.isEmpty() ? "Column[" + i + "]" : colName;
               }

               boolean selected = Stream.concat(
                     Arrays.stream(ainfo.getAggregates()).map(AggregateRef::getName),
                     Arrays.stream(ainfo.getGroups()).map(GroupRef::getName)
                  )
                  .anyMatch(colName::equals);

               (selected ? selectColumns : noSelectColumns).add(colName);

               columnDescriptions.add(column.getDescription());
               originalNames.add(ColumnRef.getAttributeView(column));
               boolean range = column.getDataRef() instanceof DateRangeRef ||
                  column.getDataRef() instanceof NumericRangeRef;
               rangeColumns.add(range + "");
            }
         }

         SortInfo sortinfo = table.getSortInfo();

         if(sortinfo == null) {
            sortinfo = new SortInfo();
         }

         Collections.sort(noSelectColumns);
         Collections.sort(selectColumns);
         List<String> availableColumns = new ArrayList<>(selectColumns);
         availableColumns.addAll(noSelectColumns);
         sortinfo = (SortInfo) sortinfo.clone();
         sortinfo.validate(cs);
         model = new SortColumnDialogModel();
         model.setName(tname);
         model.getSortColumnEditorModel().setAliasMap(map);
         model.getSortColumnEditorModel().setAvailableColumns(
            availableColumns.toArray(new String[0]));
         model.getSortColumnEditorModel().setColumnDescriptions(
            columnDescriptions.toArray(new String[0]));
         model.getSortColumnEditorModel().setOriginalNames(
            originalNames.toArray(new String[0]));
         model.getSortColumnEditorModel().setRangeColumns(
            rangeColumns.toArray(new String[0]));
         SortRef[] sorts = Arrays.stream(sortinfo.getSorts())
            .filter((sortRef) -> sortRef.getOrder() != XConstants.SORT_NONE)
            .toArray(SortRef[]::new);
         int[] orders = new int[sorts.length];
         String[] columns = new String[sorts.length];

         for(int i = 0; i < sorts.length; i++) {
            orders[i] = sorts[i].getOrder();
            ColumnRef col = (ColumnRef) sorts[i].getDataRef();
            columns[i] = col.getAlias() == null ? sorts[i].getName() : col.getDataRef().getName();
            int index = cs.indexOfAttribute(col);
            columns[i] = "".equals(columns[i]) ? "Column[" + index + "]" : columns[i];
         }

         model.getSortColumnEditorModel().setOrders(orders);
         model.getSortColumnEditorModel().setSelectedColumns(columns);
      }

      return model;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/sort-column-dialog-model")
   public void setSortInfo(
      @Payload SortColumnDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = model.getName();
      SortInfo sortInfo = new SortInfo();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(name);

      // may remove assembly by undo/redo.
      if(assembly == null) {
         return;
      }

      SortColumnEditorModel sortColumnEditorModel = model.getSortColumnEditorModel();
      Map<String, String> aliasMap = sortColumnEditorModel.getAliasMap();
      String[] selectedColumns = sortColumnEditorModel.getSelectedColumns();
      ColumnSelection cols = assembly.getColumnSelection();

      for(int i = 0; selectedColumns != null && i < selectedColumns.length; i++) {
         SortRef ref = new SortRef();
         ref.setOrder(sortColumnEditorModel.getOrders()[i]);
         String selectedName = sortColumnEditorModel.getSelectedColumns()[i];
         DataRef dref = null;

         if(aliasMap != null && aliasMap.containsKey(selectedName)) {
            selectedName = aliasMap.get(selectedName);
            dref = cols.getAttribute(selectedName);
         }
         else {
            dref = cols.getAttribute(selectedName);

            if(dref == null) {
               dref = getCol(cols, selectedName);
            }
         }

         ref.setDataRef(dref);
         sortInfo.addSort(ref);
      }

      assembly.setSortInfo(sortInfo);
      AggregateInfo ainfo = assembly.getAggregateInfo();

      if(ainfo != null && ainfo.isCrosstab()) {
         // refresh public column selection before loading data to provide right columnselection
         // for AssetQueryCacheNormalizer.
         rws.getAssetQuerySandbox().refreshColumnSelection(name, true, false);
      }

      WorksheetEventUtil.loadTableData(rws, name, true, true);
      WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, name, true, true);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
   }

   private ColumnRef getCol(ColumnSelection cols, String selectedName) {
      for(int j = 0; j < cols.getAttributeCount(); j++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(j);
         String colName = col.getAlias() != null ? col.getDataRef().getName() :
            col.getName();
         colName = "".equals(colName) ? "Column[" + j + "]" : colName;

         if(Tool.equals(selectedName, colName)) {
            return col;
         }
      }

      return null;
   }
}
