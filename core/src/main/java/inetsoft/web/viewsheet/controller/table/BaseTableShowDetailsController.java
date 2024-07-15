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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.ShowDetailEvent;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.*;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.SortInfoModel;
import inetsoft.web.composer.model.vs.TableStylePaneModel;
import inetsoft.web.composer.vs.dialog.TableViewStylePaneController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.LoadPreviewTableCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.table.ShowDetailsEvent;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.IntStream;

@Controller
public class BaseTableShowDetailsController extends BaseTableController<ShowDetailsEvent> {
   @Autowired
   public BaseTableShowDetailsController(RuntimeViewsheetRef runtimeViewsheetRef,
                                         PlaceholderService placeholderService,
                                         ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   /**
    * Get the format model for the detail table column. Uses FormatInfoModel,
    * but only really needs the format information.
    * @param vsId      runtime viewsheet id
    * @param event     information about the table
    * @param principal the user
    * @return
    * @throws Exception
    */
   @PostMapping("/table/show-details/format-model")
   @ResponseBody
   public FormatInfoModel getFormatModel(@DecodeParam("vsId") String vsId,
                                         @RequestBody ShowDetailsEvent event,
                                         Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      FormatTableLens2 table = applyChanges(rvs, event, principal, null, false);

      if(table == null) {
         return null;
      }

      int eventColumn = event.column().size() == 0 ? 0 : event.column().get(0);
      TableFormat tableFormat = table.getColTableFormat(eventColumn);
      FormatInfoModel format = new FormatInfoModel();

      if(tableFormat != null) {
         format.setFormat(tableFormat.format);
         format.setFormatSpec(tableFormat.format_spec);
         format.fixDateSpec(tableFormat.format, tableFormat.format_spec);
      }

      format.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));
      return format;
   }

   @Override
   @LoadingMask
   @Undoable
   @MessageMapping("/table/show-details")
   public void eventHandler(@Payload ShowDetailsEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      // Runtime Viewsheet
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      applyChanges(rvs, event, principal, dispatcher, true);
   }

   /**
    * Apply changes to table lens and return the lens. If dispatcher available,
    * then send table data.
    */
   private FormatTableLens2 applyChanges(RuntimeViewsheet rvs, ShowDetailsEvent event,
                                         Principal principal, CommandDispatcher dispatcher,
                                         boolean strictMatchNull)
      throws Exception
   {
      // Event properties
      Map<Integer, int[]> selectedCells = event.getSelectedCells();
      String name = event.getAssemblyName();
      int eventColumn = event.column().size() == 0 ? 0 : event.column().get(0);

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = box.getViewsheet();
      final TableDataVSAssembly data = (TableDataVSAssembly) viewsheet.getAssembly(name);
      int hint;
      ConditionList conds = new ConditionList();

      if(data instanceof CrosstabVSAssembly) {
         conds = createCrosstabConditions((CrosstabVSAssembly) data, box, name, selectedCells, strictMatchNull);
      }
      else if(data instanceof CalcTableVSAssembly) {
         String tipMsg = Catalog.getCatalog().getString(
            "composer.vs.calctable.formula.showdetail");
         conds = createCalcTableConditions(
            (CalcTableVSAssembly) data, box, name, selectedCells, tipMsg, dispatcher);
      }
      else if(data instanceof TableVSAssembly) {
         TableLens lens = (TableLens) box.getData(name);
         ((TableVSAssembly) data).setShowDetail(true);

         for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
            int row = entry.getKey();
            int[] cols = entry.getValue();

            for(int col : cols) {
               conds = createTableConditions((TableVSAssembly) data, row, col, lens, conds);
            }
         }
      }

      if(conds != null) {
         conds.trim();
      }

      if(conds == null || conds.getSize() == 0) {
         // send an empty LoadPreviewTableCommand to clear the preserveSelection flag
         LoadPreviewTableCommand command =
            LoadPreviewTableCommand.builder()
               .styleModel(new TableStylePaneModel())
               .build();
         dispatcher.sendCommand(name, command);
         return null;
      }

      hint = data.setDetailConditionList(conds);

      ChangedAssemblyList clist;

      if(dispatcher != null) {
         clist = placeholderService.createList(false, dispatcher, rvs, null);
      }
      else {
         clist = new ChangedAssemblyList(false);
      }

      hint = hint | VSAssembly.DETAIL_INPUT_DATA_CHANGED;
      box.processChange(name, hint, clist, DataMap.DETAIL | DataMap.BRUSH);

      TableLens tableLens = (TableLens) box.getData(name, true,
                                                    DataMap.DETAIL | DataMap.BRUSH);

      if(tableLens == null) {
         return null;
      }

      String id = rvs.getID();
      String tname = "Data";
      String wid = event.worksheetId() != null ? event.worksheetId() :
              viewsheetService.openPreviewWorksheet(id, tname, principal);
      RuntimeWorksheet rws = viewsheetService.getWorksheet(wid, principal);
      AssetQuerySandbox box2 = rws.getAssetQuerySandbox();
      Worksheet ws = rws.getWorksheet();

      DataTableAssembly assembly = (DataTableAssembly) ws.getAssembly(tname);
      assembly.setListener(null);
      boolean tableMode = assembly.isRuntime();
      box2.resetTableLens(tname);
      rws.setParentID(id);
      assembly.setListener(null);
      assembly.setData(tableLens);
      assembly.setRuntime(rvs.isRuntime());
      ((WSAssemblyInfo) assembly.getInfo()).setClassName("WSPreviewTable");

      // set sort ref
      SortInfo sortInfo = assembly.getSortInfo();
      sortInfo.clear();
      assembly.setRuntime(tableMode);
      tableLens.moreRows(XTable.EOT);
      ColumnSelection columns = assembly.getColumnSelection();
      columns = columns == null ? columns : columns.clone();
      ColumnSelection infos = data.getDetailColumns();
      tableLens = DateComparisonUtil.hiddenTempGroupAndDatePartRefs(data, viewsheet, tableLens, columns);

      for(int colIndex : event.column()) {
         BaseTableShowDetailsController.handleColumnEvents(
            infos == null || infos.isEmpty() ? columns : infos,
            colIndex, event.newColName(), event.toggleHide());
      }

      if(infos != null) {
         BaseTableShowDetailsController.updateColumnHeaders(tableLens, infos, columns);
      }

      // If dnd to change order of column, create new detail column order according dnd info.
      // If dnd many times, the detail columns will save the last order.
      if(event.dndInfo() != null) {
         ColumnSelection cols = infos == null || infos.isEmpty() ? columns : infos;
         cols = getDetailColumns(event.dndInfo(), cols);
         data.setDetailColumns(cols);
         assembly.setColumnSelection(cols);
         tableLens = createColumnMapFilter(tableLens, cols, columns);
         assembly.setData(tableLens);
      }
      else if(infos != null && !infos.isEmpty()) {
         if(isColumnChanged(infos, columns)) {
            data.setDetailColumns(columns);
            infos = data.getDetailColumns();
            assembly.setData(tableLens);
         }
         else {
            tableLens = createColumnMapFilter(tableLens, infos, columns);
            assembly.setData(tableLens);
            BaseTableShowDetailsController.updateColumnVisibility(infos, assembly.getColumnSelection());
         }
      }

      SortInfoModel detailsTableSortInfo = event.sortInfo();

      if(detailsTableSortInfo != null) {
         final int sortColIdx = detailsTableSortInfo.getCol();

         if(sortColIdx < infos.getAttributeCount()) {
            final DataRef sortCol = infos.getAttribute(sortColIdx);
            final SortRef sortRef = new SortRef(sortCol);
            sortRef.setOrder(detailsTableSortInfo.getSortValue());
            data.setDataSortRef(sortRef);
         }
      }

      final SortRef sortRef = data.getDataSortRef();

      if(sortRef != null) {
         boolean columnFound = false;

         for(int i = 0; i < infos.getAttributeCount() && !columnFound; i++) {
            final DataRef column = infos.getAttribute(i);

            if(column.getName().equals(sortRef.getName())) {
               if(detailsTableSortInfo == null) {
                  detailsTableSortInfo = new SortInfoModel();
                  detailsTableSortInfo.setCol(i);
                  detailsTableSortInfo.setSortValue(sortRef.getOrder());
               }

               tableLens = new SortFilter(tableLens, new int[] {i},
                                          sortRef.getOrder() == XConstants.SORT_ASC);
               tableLens.moreRows(Integer.MAX_VALUE);
               columnFound = true;
            }
         }

         if(!columnFound) {
            data.setDataSortRef(null);
         }
      }

      FormatTableLens2 table = new FormatTableLens2(tableLens);
      Tool.localizeHeader(table);

      for(int i = 0; i < table.getColCount(); i++) {
         String header = XUtil.getHeader(table, i).toString();
         table.setColHeader(i, header);
      }

      ColumnInfo columnInfo = null;
      VSCompositeFormat format = null;
      FormatInfoModel formatModel = event.format();

      if(formatModel != null) {
         format = new VSCompositeFormat();
         String formatString = FormatInfoModel.getDurationFormat(formatModel.getFormat(),
            formatModel.isDurationPadZeros());
         String formatSpec = formatModel.getFormatSpec();
         String dateSpec = formatModel.getDateSpec();
         format.getUserDefinedFormat().setFormat(formatString);

         if(XConstants.DATE_FORMAT.equals(formatString) && !"Custom".equals(dateSpec)) {
            formatSpec = dateSpec;
         }

         format.getUserDefinedFormat().setFormatExtent(
            formatSpec != null && formatSpec.length() > 0 ?
            formatSpec : null);

         try {
            List<ColumnInfo> columnInfos =
               box2.getColumnInfos(tname, WorksheetEventUtil.getMode(assembly));
            ColumnRef col = (ColumnRef) assembly.getColumnSelection().getAttribute(eventColumn);

            for(ColumnInfo info : columnInfos) {
               if(Tool.equals(info.getColumnRef(), col)) {
                  columnInfo = info;
                  break;
               }
            }
         }
         catch(Exception e) {
            columnInfo = null;
         }
      }

      table.addTableFormat(data, assembly, assembly.getColumnSelection(), columnInfo, format);
      DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) data.getInfo();

      if(event.detailStyle() != null) {
         if("null".equals(event.detailStyle())) {
            dinfo.setDetailStyle(null);
         }
         else {
            dinfo.setDetailStyle(event.detailStyle());
         }
      }

      if(dispatcher != null) {
         int start = 0;
         // 5002 should match the limit in preview-table.component.ts (5001)
         int end = Math.min(table.getRowCount(), 5002);
         int colCount = table.getColCount();

         if(colCount > 500) {
            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(
               Catalog.getCatalog().getString("vs.showDetail.columnExceed", colCount));
            dispatcher.sendCommand(cmd);
            colCount = 500;
         }

         PreviewTableCellModel[][] tableCells = new PreviewTableCellModel[end][colCount];
         int[] colWidths = {};

         if(data.getDetailColumns() == null || data.getDetailColumns().isEmpty()) {
            data.setDetailColumns(columns);
         }

         final ColumnSelection infos0 = data.getDetailColumns();
         colWidths = IntStream.range(0, infos0.getAttributeCount())
            .map(i -> {
               ColumnRef ref = (ColumnRef) infos0.getAttribute(i);
               return ref.getPixelWidth();
            }).toArray();

         TableLens tlens = table;
         String style = dinfo.getDetailStyle();

         if(style != null) {
            TableStyle styleTable = VSUtil.getTableStyle(style);

            if(styleTable != null) {
               styleTable = (TableStyle) styleTable.clone();
               styleTable.setTable(table);
               tlens = styleTable;
            }
         }

         for(int row = start; row < end; row++) {
            for(int col = 0; col < colCount; col++) {
               if(((ColumnRef) infos0.getAttribute(col)).isVisible()) {
                  String alias = row == 0 ? infos0.getAttribute(col).getName() : null;

                  tableCells[row][col] = BaseTableCellModel.createPreviewCell(
                     tlens, row, col, style != null, alias, null, box.getAllVariables());
               }
            }
         }

         TableViewStylePaneController styleController = new TableViewStylePaneController();
         TableStylePaneModel styleModel = new TableStylePaneModel();
         styleModel.setTableStyle(style);
         styleModel.setStyleTree(styleController.getStyleTree(rvs, principal, false));

         LoadPreviewTableCommand command =
            LoadPreviewTableCommand.builder()
               .tableData(tableCells)
               .worksheetId(wid)
               .sortInfo(detailsTableSortInfo)
               .colWidths(colWidths)
               .styleModel(styleModel)
               .build();
         dispatcher.sendCommand(name, command);
      }

      if(data instanceof TableVSAssembly) {
         ((TableVSAssembly) data).setShowDetail(false);
      }

      return table;
   }

   public static ColumnSelection getDetailColumns(DetailDndInfo dnd, ColumnSelection infos) {
      if(infos == null) {
         return null;
      }

      int[] dragIndexes = dnd.getDragIndexes();
      int dropIndex = dnd.getDropIndex();

      ColumnSelection columns2 = new ColumnSelection();

      // Add columns no dragged.
      for(int i = 0; i < infos.getAttributeCount(); i++) {
         boolean isDragged = false;

         for(int j = 0; j < dragIndexes.length; j++) {
            int dragIndex = dragIndexes[j];

            if(i == dragIndex) {
               isDragged = true;
               break;
            }
         }

         if(!isDragged) {
            columns2.addAttribute(infos.getAttribute(i));
         }
      }

      for(int i = 0; i < dragIndexes.length; i++) {
         int dragIndex = dragIndexes[i];

         if(dragIndex < dropIndex) {
            dropIndex--;
         }
      }

      // Add columns dragged to drop index.
      for(int i = 0; i < dragIndexes.length; i++) {
         columns2.addAttribute(dropIndex, infos.getAttribute(dragIndexes[i]));
      }

      return columns2;
   }

   public static void handleColumnEvents(ColumnSelection cols, int columnIndex,
                                         String newColName, boolean toggleHidden)
   {
      if(newColName != null) {
         ((ColumnRef) cols.getAttribute(columnIndex)).setAlias(newColName);
      }

      if(toggleHidden) {
         if(columnIndex == -1) {
            //Turn all columns to visible
            Enumeration iter = cols.getAttributes();

            while(iter.hasMoreElements()) {
               ColumnRef ref = (ColumnRef) iter.nextElement();
               ref.setVisible(true);
            }
         }
         else {
            //Make selected column hidden
            ((ColumnRef) cols.getAttribute(columnIndex)).setVisible(false);
         }
      }

   }

   public static TableLens createColumnMapFilter(TableLens lens, ColumnSelection detail,
                                                  ColumnSelection original)
   {

      if(detail == null || detail.getAttributeCount() != original.getAttributeCount()) {
         return lens;
      }

      int count = original.getAttributeCount();
      int[] colMap = new int[count];

      for(int i = 0; i < count; i++) {
         DataRef dref = detail.getAttribute(i);
         int index = original.indexOfAttribute(dref);

         if(index == -1) {
            DataRef dref2 = original.getAttribute(dref.getName());
            index = original.indexOfAttribute(dref2);
         }

         colMap[i] = index;
      }

      return new ColumnMapFilter(lens, colMap);
   }

   public static boolean isColumnChanged(ColumnSelection detail, ColumnSelection original) {
      if(detail == null || original == null) {
         return true;
      }

      if(detail.getAttributeCount() != original.getAttributeCount()) {
         return true;
      }

      int count = original.getAttributeCount();

      for(int i = 0; i < count; i++) {
         DataRef dref = detail.getAttribute(i);
         int index = original.indexOfAttribute(dref);

         if(index == -1) {
            return true;
         }
      }

      return false;
   }

   public static void updateColumnHeaders(TableLens table, ColumnSelection detail,
                                            ColumnSelection original)
   {
      for(int i = 0; i < original.getAttributeCount(); i ++) {
         ColumnRef col1 = (ColumnRef) original.getAttribute(i);
         int index = detail.indexOfAttribute(col1);

         if(index ==  -1) {
            continue;
         }

         ColumnRef col2 = (ColumnRef) detail.getAttribute(index);
         String alias = col2.getAlias();

         if(alias != null && !alias.isEmpty()) {
            table.setObject(0, i, alias);
         }
      }
   }

   public static void updateColumnVisibility(ColumnSelection detail,
                                         ColumnSelection original)
   {
      for(int i = 0; i < original.getAttributeCount(); i ++) {
         ColumnRef col1 = (ColumnRef) original.getAttribute(i);
         ColumnRef col2 = (ColumnRef) detail.getAttribute(i);

         col1.setVisible(col2.isVisible());
      }
   }

   private static ConditionList createCrosstabConditions(CrosstabVSAssembly assembly,
                                                         ViewsheetSandbox box,
                                                         String name,
                                                         Map<Integer, int[]> selectedCells,
                                                         boolean strictMatchNull)
      throws Exception
   {
      ConditionList conds = new ConditionList();
      TableLens lens = (TableLens) box.getData(name);
      VSCrosstabInfo vinfo = assembly.getVSCrosstabInfo();
      DataRef[] rheaders = vinfo.getPeriodRuntimeRowHeaders();
      DataRef[] rheaders2 = vinfo.getRuntimeRowHeaders();
      DataRef[] cheaders = vinfo.getRuntimeColHeaders();
      DataRef[] aggrs = vinfo.getRuntimeAggregates();
      Viewsheet viewsheet = box.getViewsheet();
      // true if no grouping and no aggregate (scatter)
      boolean plain = false;
      // true if only aggregate and no grouping
      boolean grand = false;

      if(lens == null) {
         return conds;
      }

      if(rheaders.length == 0 && cheaders.length == 0) {
         DataRef[] oaggrs = vinfo.getDesignAggregates();
         plain = true;

         for(int i = 0; i < oaggrs.length; i++) {
            VSAggregateRef vref = (VSAggregateRef) oaggrs[i];
            boolean isCube = (vref.getRefType() & DataRef.CUBE) == DataRef.CUBE;
            AggregateFormula formula = vref.getFormula();
            DataRef cref = null;

            for(int j = 0; j < aggrs.length; j++) {
               if(vref.equalsContent(aggrs[j])) {
                  cref = getBaseRef(aggrs[j]);
                  break;
               }
            }

            boolean isAggrCalc = (cref instanceof CalculateRef)
               && !((CalculateRef) cref).isBaseOnDetail();

            // using detail data as condition if not aggregated
            // except cube measure
            if(formula != null && formula != AggregateFormula.NONE || isCube || isAggrCalc) {
               plain = false;
               grand = true;
               break;
            }
         }
      }

      int rcnt = 0;
      int ccnt = 0;

      if(lens instanceof CrossFilter) {
         CrossFilter clensFilter = (CrossFilter) lens;
         rcnt = clensFilter.getRowHeaderCount();
         ccnt = clensFilter.getColHeaderCount();
      }
      else if(lens instanceof HiddenRowColFilter) {
         HiddenRowColFilter clensFilter = (HiddenRowColFilter) lens;

         if(clensFilter.getTable() instanceof CrossFilter) {
            CrossFilter cross = (CrossFilter) clensFilter.getTable();
            rcnt = cross.getRowHeaderCount();
            ccnt = cross.getColHeaderCount();
         }
      }

      boolean period = rheaders.length - rheaders2.length == 1;
      TableLens toplens = lens;
      List<ConditionList> allCondList = new ArrayList<>();

      for(Map.Entry<Integer, int[]> entry : selectedCells.entrySet()) {
         int row = entry.getKey();

         for(int col : entry.getValue()) {
            // fix bug1238665789479 & bug124237476220
            // the measure title cannot trigger out any data,
            // so it should be disabled like headers title
            if(row == 0 && cheaders.length == 0 ||
               col == 0 && rheaders.length == 0 && cheaders.length != 0) {
               continue;
            }

            TableDataPath path = toplens.getDescriptor().getCellDataPath(row, col);

            if(path == null) {
               continue;
            }

            if(plain) {
               if(row < 0) {
                  continue;
               }
            }
            else if(path.getType() == TableDataPath.GRAND_TOTAL) {
               String[] paths = path.getPath();
               // just grand total summary cells do not show all details
               if(paths != null && (paths.length == 1 ||
                  paths.length == 2 && (isRowGrandTotal(paths[0]) || isColGrandTotal(paths[0])) ||
                  paths.length == 3 && isRowGrandTotal(paths[0]) && isColGrandTotal(paths[1])))
               {
                  grand = true;
               }
            }
            else if(path.getType() != TableDataPath.HEADER && grand) {
               // ok
            }
            else if(path.getType() != TableDataPath.SUMMARY &&
               // allow brushing on headers in crosstab
               path.getType() != TableDataPath.GROUP_HEADER) {
               continue;
            }

            List<ConditionList> condList = new ArrayList<>();

            // using detail data as condition if not aggregated, scatter
            if(plain) {
               int baserow = row;
               ConditionList list = new ConditionList();

               for(int j = 0; j < lens.getColCount(); j++) {
                  DataRef column = aggrs[j];
                  column = getBaseRef(column);
                  Object val = lens.getObject(baserow, j);
                  Condition cond = new Condition(column.getDataType());
                  cond.setConvertingType(false);
                  cond.setOperation(val == null || val.toString().length() == 0 ?
                                       Condition.NULL : Condition.EQUAL_TO);
                  cond.addValue(val);
                  list.append(new ConditionItem(column, cond, 0));
                  list.append(new JunctionOperator(JunctionOperator.AND, 0));
               }

               condList.add(list);
            }
            // selecting grand total shows all details
            else if(grand && aggrs.length != 0) {
               ConditionList list = new ConditionList();
               int n = aggrs.length - 1;
               DataRef column = aggrs[Math.min(col, n)];
               column = getBaseRef(column);

               if((column instanceof CalculateRef)
                  && !((CalculateRef) column).isBaseOnDetail()) {
                  ExpressionRef eref =
                     (ExpressionRef) ((CalculateRef) column).getDataRef();
                  // add the calculate field sub aggregate ref to base table
                  String expression = eref.getExpression();
                  List<String> matchNames = new ArrayList<>();
                  List<AggregateRef> aggs = VSUtil.findAggregate(viewsheet,
                                                                 assembly.getTableName(), matchNames, expression);

                  if(aggs != null && aggs.size() == 1) {
                     column = getBaseRef(aggs.get(0));
                  }
               }

               Condition cond = new Condition(column.getDataType());
               cond.setConvertingType(false);
               cond.setOperation(Condition.NULL);
               cond.setNegated(true);
               list.append(new ConditionItem(column, cond, 0));
               condList.add(list);
            }
            else {
               SourceInfo sinfo = assembly.getSourceInfo();
               String cubeType = VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());

               boolean xmla = XCube.SQLSERVER.equals(cubeType) || XCube.MONDRIAN.equals(cubeType);
               boolean drillThrough = xmla || XCube.MODEL.equals(cubeType);

               if(col >= rcnt) {
                  ConditionList conds1 = new ConditionList();
                  ShowDetailEvent.createCrosstabConditions(assembly, conds1, cheaders,
                                                           col, lens, true, cubeType,
                                                           0, path, xmla);
                  conds1.trim();
                  condList.add(conds1);
               }

               if(row >= cheaders.length) {
                  int offset = 0; //period ? 1 : 0;
                  ConditionList conds2 = new ConditionList();
                  ShowDetailEvent.createCrosstabConditions(assembly, conds2, rheaders,
                                                           row, lens, false, cubeType,
                                                           offset, path, xmla);
                  conds2.trim();
                  condList.add(conds2);
               }

               // do not drill through for dimensions
               if(drillThrough && !(row >= cheaders.length && col >= rcnt)) {
                  return null;
               }

               // measure condition is not necessary for logical model
               if(XCube.SQLSERVER.equals(cubeType) || XCube.MONDRIAN.equals(cubeType)) {
                  // in case no column header at all
                  boolean sbs = false;
                  int hrcnt = rcnt;
                  int hccnt = ccnt;

                  if(lens instanceof CrossFilter) {
                     CrossFilter crosstab = (CrossFilter) lens;
                     sbs = crosstab.isSummarySideBySide();
                     hrcnt = crosstab.getHeaderRowCount();
                     hccnt = crosstab.getHeaderColCount();
                  }
                  else if(lens instanceof HiddenRowColFilter) {
                     HiddenRowColFilter clensFilter = (HiddenRowColFilter) lens;
                     hrcnt = clensFilter.getHeaderRowCount();
                     hccnt = clensFilter.getHeaderColCount();
                  }

                  int hcnt = sbs ? hccnt : hrcnt;
                  int index = sbs ? col : row;
                  int idx = (index - hcnt) % aggrs.length;

                     /*
                     int clen = Math.max(cheaders.length, 1);
                     int idx = (row - clen) % aggrs.length;
                     */
                  DataRef column = aggrs[idx];

                  if(column instanceof VSAggregateRef) {
                     VSAggregateRef vaggr = (VSAggregateRef) column;
                     column = XMLAUtil.shrinkColumnRef(
                        (ColumnRef) vaggr.getDataRef());
                  }

                  ConditionList list = new ConditionList();
                  Condition cond = new Condition(column.getDataType());
                  cond.setConvertingType(false);
                  cond.setOperation(Condition.NULL);
                  list.append(new ConditionItem(column, cond, 0));
                  condList.add(list);
               }
            }

            ConditionList cellConds = ConditionUtil.mergeConditionList(condList,
               JunctionOperator.AND);
            cellConds.trim();
            allCondList.add(cellConds);
         }
      }

      return ConditionUtil.mergeConditionList(allCondList,
         JunctionOperator.OR);
   }

   /**
    * Create the table condition.
    */
   private static ConditionList createTableConditions(TableVSAssembly tv, int row, int col,
                                                     TableLens lens, ConditionList conds)
      throws Exception
   {
      String tname = tv.getTableName();
      Viewsheet vs2 = tv.getViewsheet();
      Worksheet ws = vs2.getBaseWorksheet();
      TableAssembly tassembly = (TableAssembly) ws.getAssembly(tname);

      if(tassembly instanceof MirrorTableAssembly) {
         tassembly = ((MirrorTableAssembly) tassembly).getTableAssembly();
      }

      ColumnSelection columns = tv.getColumnSelection();
      AggregateInfo ainfo = tassembly.getAggregateInfo();
      ColumnSelection columns2 = tassembly.getColumnSelection(true);
      DataRef acolumn = col >= columns.getAttributeCount() ? null :
         columns.getAttribute(col);

      if(acolumn == null || !lens.moreRows(row)) {
         return conds;
      }

      ConditionList nconds = new ConditionList();

      for(int j = 0, cidx = -1; j < columns.getAttributeCount(); j++) {
         DataRef column = columns.getAttribute(j);
         DataRef column2 = columns2.getAttribute(column.getName());

         if(column instanceof ColumnRef && !((ColumnRef) column).isVisible()) {
            continue;
         }

         cidx++;

         if(column2 == null && column instanceof ColumnRef) {
            ColumnRef cref = (ColumnRef) column;
            DataRef bref = cref.getDataRef();

            if(bref instanceof AliasDataRef) {
               bref = ((AliasDataRef) bref).getDataRef();
            }

            column2 = columns2.getAttribute(bref.getName());
         }

         if(column2 == null) {
            continue;
         }

         if(ainfo != null && !ainfo.containsGroup(column2) &&
            (!ainfo.isEmpty() ||
               // if no aggregate, treat all non-numeric as dimension
               // to be included in condition
               XSchema.isNumericType(column2.getDataType())))
         {
            continue;
         }

         if(AssetUtil.findColumn(lens, column) == -1) {
            continue;
         }

         GroupRef grp = ainfo.getGroup(column2);
         Object val = lens.getObject(row, cidx);
         ConditionList namedConds = null;

         if(grp != null) {
            namedConds = getXNamedConds(grp, val, ws);

            if(namedConds != null) {
               List list = new ArrayList();
               nconds.trim();
               namedConds.trim();
               list.add(nconds);
               list.add(namedConds);
               nconds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            }
         }

         if(namedConds == null && grp != null && isDateOfGroup(grp) &&
            // could be date part grouping
            val instanceof java.util.Date)
         {
            DateRangeRef drr = new DateRangeRef(
               "_daterange_", ((ColumnRef) column).getDataRef());
            drr.setOriginalType(column.getDataType());
            Condition cond = new Condition(drr.getDataType());
            cond.setConvertingType(false);
            int dgroup = grp.getDateGroup();
            Calendar cal = Calendar.getInstance();
            cal.setTime(getSQLDate(val));

            if(dgroup == XConstants.MONTH_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.MONTH_INTERVAL);
               cond.addValue(cal.get(Calendar.MONTH));
            }
            else if(dgroup == XConstants.QUARTER_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.QUARTER_INTERVAL);
               cond.addValue((cal.get(Calendar.MONTH)) / 3 + 1);
            }
            else if(dgroup == XConstants.WEEK_OF_YEAR_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.WEEK_INTERVAL);
               cond.addValue(cal.get(Calendar.WEEK_OF_YEAR));
            }
            else if(dgroup == XConstants.DAY_OF_WEEK_DATE_GROUP) {
               drr.setDateOption(DateRangeRef.DAY_OF_WEEK_PART);
               cond.addValue(cal.get(Calendar.DAY_OF_WEEK));
            }

            ColumnRef cr = new ColumnRef(drr);
            cond.setOperation(Condition.EQUAL_TO);
            nconds.append(new ConditionItem(cr, cond, 0));
         }
         else if(namedConds == null) {
            Condition cond = new Condition(column.getDataType());
            cond.setConvertingType(false);
            cond.addValue(val);
            boolean isDate = val instanceof java.util.Date;

            if(isDate && grp != null && grp.getDateGroup() == XConstants.QUARTER_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.MONTH, 3);
               cal.add(Calendar.DATE, -1);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(isDate && grp != null && grp.getDateGroup() == XConstants.YEAR_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.YEAR, 1);
               cal.add(Calendar.DATE, -1);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(isDate && grp != null && grp.getDateGroup() == XConstants.MONTH_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.MONTH, 1);
               cal.add(Calendar.DATE, -1);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else if(isDate && grp != null && grp.getDateGroup() == XConstants.WEEK_DATE_GROUP) {
               Calendar cal = Calendar.getInstance();
               cal.setTime(getSQLDate(val));
               cal.add(Calendar.DATE, 7);
               cond.addValue(cal.getTime());
               cond.setOperation(Condition.BETWEEN);
            }
            else {
               cond.setOperation(val == null ? Condition.NULL : Condition.EQUAL_TO);
            }

            nconds.append(new ConditionItem(column2, cond, 0));
         }

         nconds.append(new JunctionOperator(JunctionOperator.AND, 0));
      }

      nconds.trim();
      List list = new ArrayList();
      list.add(conds);
      list.add(nconds);
      ConditionList clist =
         ConditionUtil.mergeConditionList(list, JunctionOperator.OR);
      return clist == null ? conds : clist;
   }

   private static boolean isRowGrandTotal(String path) {
      return path != null && "ROW_GRAND_TOTAL".equals(path);
   }

   private static boolean isColGrandTotal(String path) {
      return path != null && "COL_GRAND_TOTAL".equals(path);
   }

   /**
    * Convert date types.
    */
   private static java.sql.Date getSQLDate(Object val) {
      return (val instanceof java.sql.Date)
         ? (java.sql.Date) val
         : (val instanceof java.util.Date
         ? new java.sql.Date(((java.util.Date) val).getTime())
         : null);
   }

   /**
    * Check if date of group or not.
    */
   private static boolean isDateOfGroup(GroupRef gr) {
      return gr.getDateGroup() == XConstants.QUARTER_OF_YEAR_DATE_GROUP ||
         gr.getDateGroup() == XConstants.MONTH_OF_YEAR_DATE_GROUP ||
         gr.getDateGroup() == XConstants.DAY_OF_WEEK_DATE_GROUP ||
         gr.getDateGroup() == XConstants.WEEK_OF_YEAR_DATE_GROUP;
   }

   private static ConditionList getXNamedConds(GroupRef group, Object val,
                                               Worksheet ws)
   {
      String named = group.getNamedGroupAssembly();

      if(named == null) {
         return null;
      }

      Object assembly = ws.getAssembly(named);

      if(!(assembly instanceof NamedGroupAssembly)) {
         return null;
      }

      NamedGroupAssembly nassembly = (NamedGroupAssembly) assembly;
      NamedGroupInfo ginfo = nassembly.getNamedGroupInfo();

      if(ginfo == null || ginfo.isEmpty()) {
         return null;
      }

      DataRef base = group.getDataRef();
      return getNamedConds(base.getName(), base, val, ginfo.getOthers(), ginfo);
   }

   /**
    * Get named conditions.
    */
   private static ConditionList getNamedConds(String cname, DataRef column,
                                              Object val, int otherType,
                                              XNamedGroupInfo named)
   {
      if(named == null || named.isEmpty()) {
         return null;
      }

      String str = val instanceof String ? (String) val : null;
      boolean others = otherType == OrderInfo.GROUP_OTHERS &&
         Catalog.getCatalog().getString("Others").equals(str);
      ConditionList nconds = str == null ? null : named.getGroupCondition(str);

      // process leave others
      if((nconds == null || nconds.isEmpty()) && !others) {
         nconds = new ConditionList();
         column = getBaseRef(column);
         Condition cond = new Condition(column.getDataType());
         cond.setConvertingType(false);
         cond.setOperation(val == null ?
                              Condition.NULL : Condition.EQUAL_TO);
         cond.addValue(val);
         nconds.append(new ConditionItem(column, cond, 0));
      }

      // others?
      if(others) {
         String[] grps = named.getGroups();
         List allconds = new ArrayList();

         for(String grp : grps) {
            nconds = named.getGroupCondition(grp);
            nconds = (ConditionList) nconds.clone();

            if(named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
               named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO)
            {
               syncConditionList(nconds, cname);
            }

            allconds.add(nconds);
         }

         nconds = ConditionUtil.mergeConditionList(
            allconds, JunctionOperator.OR);
         nconds = (ConditionList) ConditionUtil.not(nconds);
      }
      else if(named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
         named.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO)
      {
         nconds = (ConditionList) nconds.clone();
         syncConditionList(nconds, cname);
      }
      else if(nconds != null) {
         nconds = (ConditionList) nconds.clone();
      }

      return nconds;
   }

   /**
    * Synchronize condition list.
    */
   private static void syncConditionList(ConditionList list, String name) {
      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);
         DataRef ref = item.getAttribute();
         String attr = ref.getAttribute().trim();

         if(Catalog.getCatalog().getString("this").equals(attr) ||
            "this".equals(attr))
         {
            item.setAttribute(new ColumnRef(new AttributeRef(name)));
         }
      }
   }
}
