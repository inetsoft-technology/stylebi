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

package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.ChartVSSelectionUtil;
import inetsoft.cluster.*;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.internal.table.FormatTableLens2;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSSelection;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.SortInfoModel;
import inetsoft.web.composer.model.vs.TableStylePaneModel;
import inetsoft.web.composer.vs.dialog.TableViewStylePaneController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.LoadPreviewTableCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableShowDetailsService;
import inetsoft.web.viewsheet.event.chart.VSChartShowDetailsEvent;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.util.List;
import java.util.stream.IntStream;

@Service
@ClusterProxy
public class VSChartShowDetailsService extends VSChartControllerService<VSChartShowDetailsEvent> {

   public VSChartShowDetailsService(ViewsheetService viewsheetService,
                                    CoreLifecycleService coreLifecycleService,
                                    VSChartAreasServiceProxy vsChartAreasServiceProxy,
                                    VSDialogService dialogService)
   {
      super(coreLifecycleService, viewsheetService, vsChartAreasServiceProxy);
      this.dialogService = dialogService;
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            VSChartShowDetailsEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, chartState -> {
         try {
            applyChanges(chartState.getRuntimeViewsheet(), chartState.getAssembly(),
                         event, principal, linkUri, dispatcher);
         }
         catch(RuntimeException ex) {
            throw ex;
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      });

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public FormatInfoModel getFormatModel(@ClusterProxyKey String vsId, String wsId,
                                         String assemblyName, int columnIndex, String selected,
                                         boolean isRangeSelection, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = getViewsheetEngine().getViewsheet(vsId, principal);
      final ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(assemblyName);
      VSChartShowDetailsEvent event = new VSChartShowDetailsEvent();
      event.setChartName(assemblyName);
      event.setWorksheetId(wsId);
      event.setColumn(columnIndex);
      event.setRangeSelection(isRangeSelection);
      event.setSelected(selected);
      FormatTableLens2 table = applyChanges(rvs, chart, event, principal, null, null);

      if(table == null) {
         return null;
      }

      TableFormat tableFormat = table.getColTableFormat(columnIndex);

      FormatInfoModel format = new FormatInfoModel();

      if(tableFormat != null) {
         format.setFormat(tableFormat.format);
         format.setFormatSpec(tableFormat.format_spec);
         format.fixDateSpec(tableFormat.format, tableFormat.format_spec);
      }

      format.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));
      return format;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setColWidth(@ClusterProxyKey String vsId, String assemblyName, int columnIndex,
                           int width, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = getViewsheetEngine().getViewsheet(vsId, principal);
      final DataVSAssembly vsobj = (DataVSAssembly) rvs.getViewsheet().getAssembly(assemblyName);
      ColumnSelection details = vsobj.getDetailColumns();

      if(details != null) {
         ColumnRef ref = (ColumnRef) details.getAttribute(columnIndex);
         ref.setPixelWidth(width);
      }

      return null;
   }

   /**
    * Only use auto testing to get show detail datas
    * @return table lens
    */
   private FormatTableLens2 getShowDetailDatas(VSChartShowDetailsEvent event,  RuntimeViewsheet rvs, String assemblyName,
                                              Principal principal) throws Exception {
      final ChartVSAssembly chart = (ChartVSAssembly) rvs.getViewsheet().getAssembly(assemblyName);
      return applyChanges(rvs, chart, event, principal, null, null);
   }

   /**
    * Apply changes to the table lens and return it. If the dispatcher is available,
    * send the table data.
    * @return table lens
    */
   private FormatTableLens2 applyChanges(RuntimeViewsheet rvs, ChartVSAssembly chartVSAssembly,
                                         VSChartShowDetailsEvent event, Principal principal, String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String name = event.getChartName();
      int hint = VSAssembly.DETAIL_INPUT_DATA_CHANGED;
      VSDataSet alens;

      try {
         alens = (VSDataSet) box.getData(name, true, DataMap.ZOOM);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      String value = event.getSelected();
      String ctype = VSUtil.getCubeType(chartVSAssembly);
      boolean drillThrough = XCube.SQLSERVER.equals(ctype) ||
         XCube.MONDRIAN.equals(ctype) || XCube.MODEL.equals(ctype);

      // do not drill through for dimensions
      if(value == null || !value.contains("INDEX:") && drillThrough) {
         return null;
      }

      // do not apply brush for chart has script
      VGraphPair pair = null;

      try {
         pair = box.getVGraphPair(name);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      if(!isSelectionActionSupported(pair, dispatcher)) {
         return null;
      }

      VGraph vgraph = pair != null ? pair.getRealSizeVGraph() : null;

      if(vgraph == null) {
         return null;
      }

      DataSet vdset = vgraph.getCoordinate().getDataSet();
      VSDataSet lens = vdset instanceof VSDataSet
         ? (VSDataSet) vdset : (VSDataSet) box.getData(name);
      VSChartInfo cinfo = chartVSAssembly.getVSChartInfo();
      VSSelection selection = ChartVSSelectionUtil.getVSSelection(
         value, lens, alens, vdset, event.isRangeSelection(), cinfo,
         XCube.SQLSERVER.equals(ctype) || XCube.MONDRIAN.equals(ctype),
         null, true, false, false);

      if(selection == null || selection.isEmpty()) {
         return null;
      }

      DateComparisonUtil.fixDatePartSelection(chartVSAssembly, lens, selection);
      chartVSAssembly.setDetailSelection(selection);
      ChangedAssemblyList clist;

      if(dispatcher != null) {
         clist = createList(false, dispatcher, rvs, linkUri);
      }
      else {
         clist = new ChangedAssemblyList(false);
      }

      hint = hint | VSAssembly.DETAIL_INPUT_DATA_CHANGED;
      TableLens tableLens = null;

      try {
         box.processChange(name, hint, clist);
         tableLens = (TableLens) box.getData(name, true, DataMap.DETAIL | DataMap.BRUSH);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      if(tableLens == null) {
         return null;
      }

      ViewsheetService engine = getViewsheetEngine();
      String runtimeId = rvs.getID();
      String tname = "Data";
      RuntimeWorksheet rws = null;
      String wid;

      try {
         wid = event.getWorksheetId() != null ? event.getWorksheetId() :
            engine.openPreviewWorksheet(runtimeId, tname, principal);
         rws = engine.getWorksheet(wid, principal);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      AssetQuerySandbox box2 = rws.getAssetQuerySandbox();
      Worksheet ws = rws.getWorksheet();

      DataTableAssembly assembly = (DataTableAssembly) ws.getAssembly(tname);
      assembly.setListener(null);
      assembly.setData(tableLens);
      assembly.setRuntime(rvs.isRuntime());
      boolean tableMode = assembly.isRuntime();

      box2.resetTableLens(tname);
      rws.setParentID(runtimeId);
      assembly.setListener(null);

      ColumnSelection infos = chartVSAssembly.getDetailColumns();
      ColumnSelection columns = assembly.getColumnSelection();
      columns = columns == null ? columns : columns.clone();
      ColumnSelection columns2 = new ColumnSelection();
      tableLens = DateComparisonUtil.hiddenTempGroupAndDatePartRefs(
         chartVSAssembly, rvs.getViewsheet(), tableLens, columns);

      for(int colIndex : event.getColumns()) {
         BaseTableShowDetailsService.handleColumnEvents(
            infos == null || infos.isEmpty() ? columns : infos,
            colIndex, event.getNewColName(), event.isToggleHide());
      }

      if(infos != null) {
         BaseTableShowDetailsService.updateColumnHeaders(tableLens, infos,
                                                            assembly.getColumnSelection());
      }

      if(event.getDndInfo() != null) {
         ColumnSelection cols = infos == null || infos.isEmpty() ? columns : infos;
         cols = BaseTableShowDetailsService.getDetailColumns(event.getDndInfo(), cols);
         chartVSAssembly.setDetailColumns(cols);
         assembly.setColumnSelection(cols);
         infos = cols;
         tableLens = BaseTableShowDetailsService.createColumnMapFilter(tableLens, cols, columns);
      }
      else if(infos != null && !infos.isEmpty()) {
         boolean isChanged = BaseTableShowDetailsService.isColumnChanged(infos, columns);

         //If binding changed, so not apply detail order and clear detail columns.
         if(isChanged) {
            chartVSAssembly.setDetailColumns(columns);
            assembly.setData(tableLens);
         }
         else {
            tableLens = BaseTableShowDetailsService.createColumnMapFilter(
               tableLens, infos, columns);
            assembly.setData(tableLens);
         }
      }
      else {
         chartVSAssembly.setDetailColumns(columns);
      }

      // 5002 should match the limit in preview-table.component.ts (5001)
      FormatTableLens2 table = new FormatTableLens2(tableLens);
      Tool.localizeHeader(table);
      ((WSAssemblyInfo) assembly.getInfo()).setClassName("WSPreviewTable");

      // set sort ref
      SortInfo sortInfo = assembly.getSortInfo();
      sortInfo.clear();

      sortInfo.setListener(new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            chartVSAssembly.setSortRef((SortRef) evt.getNewValue());
         }
      });

      SortRef sortRef = chartVSAssembly.getSortRef();
      sortRef = sortRef != null ? (SortRef) sortRef.clone() : null;

      if(sortRef != null) {
         sortInfo.addSort(sortRef);
      }

      ColumnInfo columnInfo = null;
      VSCompositeFormat format = null;
      FormatInfoModel formatModel = event.getFormat();

      if(formatModel != null) {
         format = new VSCompositeFormat();
         String formatString = FormatInfoModel.getDurationFormat(formatModel.getFormat(),
                                                                 formatModel.isDurationPadZeros());
         String formatSpec = formatModel.getFormatSpec();
         String dateSpec = formatModel.getDateSpec();
         format.getUserDefinedFormat().setFormat(formatString);

         if(XConstants.CURRENCY_FORMAT.equals(formatString) ||
            XConstants.PERCENT_FORMAT.equals(formatString) ||
            XConstants.DECIMAL_FORMAT.equals(formatString))
         {
            format.getUserDefinedFormat()
               .setAlignmentValue(StyleConstants.H_RIGHT | StyleConstants.V_TOP);
         }
         else {
            format.getUserDefinedFormat()
               .setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_TOP);
         }

         if(XConstants.DATE_FORMAT.equals(formatString) && !"Custom".equals(dateSpec)) {
            formatSpec = dateSpec;
         }

         format.getUserDefinedFormat().setFormatExtent(
            formatSpec != null && formatSpec.length() > 0 ?
               formatSpec : null);

         try {
            List<ColumnInfo> columnInfos =
               box2.getColumnInfos(tname, WorksheetEventUtil.getMode(assembly));
            ColumnRef col = (ColumnRef) assembly.getColumnSelection().getAttribute(event.getColumn());

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

      table.addTableFormat(chartVSAssembly, assembly, assembly.getColumnSelection(), columnInfo, format);
      DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) chartVSAssembly.getInfo();

      if(event.getDetailStyle() != null) {
         if("null".equals(event.getDetailStyle())) {
            dinfo.setDetailStyle(null);
         }
         else {
            dinfo.setDetailStyle(event.getDetailStyle());
         }
      }

      if(infos == null || infos.isEmpty()) {
         chartVSAssembly.setDetailColumns(columns);
      }
      else {
         for(int i = 0; i < infos.getAttributeCount(); i++) {
            DataRef col = infos.getAttribute(i);
            DataRef col0 = columns.findAttribute(col);

            // use the ref from the data table so if the column type
            // change, the detail column selection is updated
            if(col0 != null) {
               columns2.addAttribute(col0);

               if(col instanceof ColumnRef && col0 instanceof ColumnRef) {
                  ((ColumnRef) col0).setVisible(((ColumnRef) col).isVisible());
                  ((ColumnRef) col0).setAlias(((ColumnRef) col).getAlias());
                  ((ColumnRef) col0).setPixelWidth(((ColumnRef) col).getPixelWidth());
               }
            }
         }

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef col = columns.getAttribute(i);

            if(!columns2.containsAttribute(col)) {
               columns2.addAttribute(col);
            }
         }

         assembly.setColumnSelection(columns2);
      }

      assembly.setRuntime(tableMode);

      if(dispatcher != null) {
         int start = 0;
         table.moreRows(TableLens.EOT);
         // 5002 should match the limit in preview-table.component.ts (5001)
         int end = Math.min(table.getRowCount(), 5002);
         int colCount = table.getColCount();

         if(colCount > 500) {
            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(Catalog.getCatalog().getString("vs.showDetail.columnExceed", colCount));
            dispatcher.sendCommand(cmd);
            colCount = 500;
         }

         PreviewTableCellModel[][] tableCells = new PreviewTableCellModel[end][colCount];
         final ColumnSelection infos0 = chartVSAssembly.getDetailColumns();
         int[] colWidths = {};

         if(infos0 != null) {
            ColumnSelection finalNinfos = infos0;
            colWidths = IntStream.range(0, infos0.getAttributeCount())
               .map(i -> ((ColumnRef) finalNinfos.getAttribute(i)).getPixelWidth()).toArray();
         }

         TableLens tlens = table;
         String style = dinfo.getDetailStyle();
         SortInfoModel sortModel = event.getSortInfo();

         if(sortModel != null && sortModel.getSortValue() != XConstants.SORT_NONE) {
            tlens = new SortFilter(tlens, new int[] { sortModel.getCol()},
                                   sortModel.getSortValue() == XConstants.SORT_ASC);
            tlens.moreRows(Integer.MAX_VALUE);
         }

         if(style != null) {
            String orgID = box != null && box.getAssetEntry() != null ? box.getAssetEntry().getOrgID() : null;
            TableStyle styleTable = VSUtil.getTableStyle(style, orgID);

            if(styleTable != null) {
               styleTable = styleTable.clone();
               styleTable.setTable(tlens);
               tlens = styleTable;
            }
         }

         for(int row = start; row < end; row++) {
            for(int col = 0; col < colCount; col++) {
               DataRef attribute = infos0.getAttribute(col);

               if(!(attribute instanceof  ColumnRef) || ((ColumnRef) attribute).isVisible()) {
                  String alias = row == 0 ? infos0.getAttribute(col).getName() : null;

                  tableCells[row][col] = BaseTableCellModel.createPreviewCell(tlens, row, col,
                                                                              style != null, alias, null, box.getVariableTable());
               }
            }
         }

         TableViewStylePaneController styleController = new TableViewStylePaneController();
         TableStylePaneModel styleModel = new TableStylePaneModel();

         try {
            styleModel.setTableStyle(style);
            styleModel.setStyleTree(dialogService.getStyleTree(rvs, principal, false));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         if(XCube.SQLSERVER.equals(ctype) && colCount == 0) {
            return null;
         }

         LoadPreviewTableCommand command = LoadPreviewTableCommand.builder()
            .tableData(tableCells)
            .worksheetId(wid)
            .sortInfo(sortModel)
            .colWidths(colWidths)
            .styleModel(styleModel)
            .build();

         dispatcher.sendCommand(name, command);
      }

      return table;
   }

   private final VSDialogService dialogService;
}
