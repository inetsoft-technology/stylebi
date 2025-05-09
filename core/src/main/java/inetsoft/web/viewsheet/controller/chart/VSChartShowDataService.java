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
import inetsoft.cluster.*;
import inetsoft.graph.EGraph;
import inetsoft.graph.data.*;
import inetsoft.graph.geo.GeoDataSet;
import inetsoft.graph.geo.MappedDataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.*;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.adhoc.model.FormatInfoModel;
import inetsoft.web.composer.model.SortInfoModel;
import inetsoft.web.composer.model.vs.TableStylePaneModel;
import inetsoft.web.composer.vs.dialog.TableViewStylePaneController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.LoadPreviewTableCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableShowDetailsService;
import inetsoft.web.viewsheet.event.chart.VSChartShowDataEvent;
import inetsoft.web.viewsheet.model.PreviewTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.text.Format;
import java.util.*;
import java.util.stream.IntStream;

import static inetsoft.report.StyleConstants.H_RIGHT;

@Service
@ClusterProxy
public class VSChartShowDataService extends VSChartControllerService<VSChartShowDataEvent> {

   public VSChartShowDataService(ViewsheetService viewsheetService,
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
                            VSChartShowDataEvent event,
                            String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      processEvent(runtimeId, event, principal, chartState ->
         applyChanges(runtimeId, chartState.getViewsheetSandbox(),
                      chartState.getAssembly(), event, principal, dispatcher));

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public FormatInfoModel getFormatModel(@ClusterProxyKey String vsId, String wsId,
                                         String assemblyName, int columnIndex, Principal principal)
      throws Exception
   {
      if(assemblyName == null || vsId == null) {
         return null;
      }

      RuntimeViewsheet rvs = getViewsheetEngine().getViewsheet(vsId, principal);
      DataVSAssembly assembly = (DataVSAssembly) rvs.getViewsheet().getAssembly(assemblyName);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSChartShowDataEvent event = new VSChartShowDataEvent();
      event.setColumn(columnIndex);
      event.setWorksheetId(wsId);
      event.setChartName(assemblyName);
      FormatTableLens2 table = applyChanges(vsId, box, assembly, event, principal, null);
      FormatInfoModel format = new FormatInfoModel();

      if(table != null) {
         TableFormat tableFormat = table.getColTableFormat(columnIndex);

         if(tableFormat != null) {
            format.setFormat(tableFormat.format);
            format.setFormatSpec(tableFormat.format_spec);
            format.fixDateSpec(tableFormat.format, tableFormat.format_spec);
         }
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
      ColumnSelection dataColumns = vsobj.getDataColumns();

      if(dataColumns != null) {
         ColumnRef ref = (ColumnRef) dataColumns.getAttribute(columnIndex);
         ref.setPixelWidth(width);
      }

      return null;
   }

   /**
    * Apply changes to the table lens and return it. If the dispatcher is available,
    * send the table data.
    */
   private FormatTableLens2 applyChanges(String vsId, ViewsheetSandbox box, DataVSAssembly data,
                                         VSChartShowDataEvent event, Principal principal,
                                         CommandDispatcher dispatcher)
   {
      String name = event.getChartName();

      if(name == null) {
         return null;
      }

      VGraphPair pair;

      try {
         pair = box.getVGraphPair(name);
      }
      catch(MessageException messageException) {
         throw messageException;
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      DataSet dset = pair == null ? null : pair.getData();

      if(dset == null) {
         return null;
      }

      boolean isMap = dset instanceof GeoDataSet;
      DataSet filter = dset;

      while(true) {
         if(filter instanceof VSDataSet) {
            break;
         }
         else if(filter instanceof PairsDataSet) {
            dset = filter = ((PairsDataSet) filter).getDataSet();
         }
         else if(filter instanceof DataSetFilter) {
            boolean useBase = filter instanceof BrushDataSet ||
               filter instanceof GeoDataSet ||
               filter instanceof MappedDataSet;
            filter = ((DataSetFilter) filter).getDataSet();

            if(useBase) {
               dset = filter;
            }
         }
         else {
            break;
         }
      }

      // Bug #38011, don't show special column used to bind the background shape in script
      if(isMap && (dset instanceof ExpandableDataSet) &&
         dset.indexOfHeader("BackgroundShape") >= 0)
      {
         dset = (DataSet) dset.clone();
         ((ExpandableDataSet) dset).removeDimension("BackgroundShape");
      }

      DateComparisonFormat dcFormat = null;

      // dataset initialized in GraphGenerator.prepareDataSet() at which point has no scale
      // information yet. re-eval here to match the chart. (51813)
      if(dset instanceof VSDataSet) {
         EGraph graph = pair.getEGraph();
         dset.prepareGraph(graph, graph.getCoordinate(), null);
         dset.removeCalcValues();
         dset.prepareCalc(null, null, true);

         dcFormat = (DateComparisonFormat)
            GraphUtil.getAllScales(graph.getCoordinate()).stream()
               .map(s -> s.getAxisSpec().getTextSpec().getFormat())
               .filter(f -> f instanceof DateComparisonFormat).findFirst().orElse(null);
      }

      // trend/comparison as discrete measure
      boolean discreteCalc = dset instanceof VSDataSet &&
         Arrays.stream(((VSDataSet) dset).getDataRefs())
            .filter(ref -> ref instanceof ChartAggregateRef)
            .anyMatch(ref -> ((ChartAggregateRef) ref).isDiscrete() &&
               ((ChartAggregateRef) ref).getCalculator() != null);

      // project forward is performed at sub-graph level, so we should use sub-datasets to
      // capture the correct projected values. however, the sub-datasets may not have the
      // trend/comparison values defined on the chart level, so we will just use the
      // full dataset if project forward is not defined. (49744)
      // only necessary to use original dataset if there is discrete calc as in 49744. (50118)
      if(((AbstractDataSet) dset).getRowsProjectedForward() > 0 || !discreteCalc ||
         // need to wrap sub-dataset inside IntervalDataSet. (57229)
         dset instanceof IntervalDataSet)
      {
         dset = ((AbstractDataSet) dset).getFullProjectedDataSet();
      }

      DataSet sorted;

      if(dset instanceof FullProjectedDataSet) {
         sorted = new FullProjectedDataSet(getSortedSubdataSets((FullProjectedDataSet) dset));
      }
      else {
         // apply ValueOrder sorting to the data set
         SortedDataSet sorted0 = new SortedDataSet(dset);

         for(int i = 0; i < dset.getColCount(); i ++) {
            sorted0.addSortColumn(dset.getHeader(i), false);
         }

         sorted = sorted0;
      }

      TableLens table = new DataSetTable(sorted);
      ArrayList<Integer> list = new ArrayList<>();
      Set<Object> headers = new HashSet<>();
      boolean containsDuplicateCol = false;

      for(int i = 0; i < table.getColCount(); i++) {
         Object header =  table.getObject(0, i);

         if(headers.contains(header)) {
            containsDuplicateCol = true;
            continue;
         }

         headers.add(header);
         list.add(i);
      }

      if(containsDuplicateCol) {
         table = new ColumnMapFilter(table, list.stream()
            .mapToInt(Integer::intValue).toArray());
      }

      SortInfoModel detailsTableSortInfo = event.getSortInfo();
      table = hideFirstPeriod(data, table);

      if(detailsTableSortInfo != null &&
         detailsTableSortInfo.getSortValue() != XConstants.SORT_NONE)
      {
         ColumnSelection dataCols = data.getDataColumns();
         int col = detailsTableSortInfo.getCol();

         if(dataCols == null || col <= dataCols.getAttributeCount() - 1) {
            col = dataCols == null ? col : AssetUtil.findColumn(table, dataCols.getAttribute(col));
            table = new SortFilter(table, new int[]{ col },
                                   detailsTableSortInfo.getSortValue() == XConstants.SORT_ASC);
         }

         table.moreRows(Integer.MAX_VALUE);
      }

      if(detailsTableSortInfo != null && data instanceof ChartVSAssembly) {
         ChartVSAssemblyInfo info = ((ChartVSAssembly) data).getChartInfo();
         info.setSummarySortColValue(detailsTableSortInfo.getCol());
         info.setSummarySortValValue(detailsTableSortInfo.getSortValue());
      }

      String tname = "Data";
      ViewsheetService engine = getViewsheetEngine();
      RuntimeWorksheet rws;
      String wid;

      try {
         wid = event.getWorksheetId() != null ? event.getWorksheetId() :
            engine.openPreviewWorksheet(vsId, tname, principal);
         rws = engine.getWorksheet(wid, principal);
      }
      catch(RuntimeException runtimeException) {
         throw runtimeException;
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }

      AssetQuerySandbox box2 = rws.getAssetQuerySandbox();
      box2.resetTableLens(tname);
      rws.setParentID(vsId);
      Worksheet ws = rws.getWorksheet();

      DataTableAssembly assembly = (DataTableAssembly) ws.getAssembly(tname);
      assembly.setListener(null);
      assembly.setData(table);

      ColumnSelection infos = data.getDataColumns();
      ColumnSelection columns = assembly.getColumnSelection();

      if(data.getDataColumns() == null) {
         data.setDataColumns(columns);
      }

      for(int colIndex : event.getColumns()) {
         BaseTableShowDetailsService.handleColumnEvents(
            infos == null || infos.isEmpty() ? columns : infos,
            colIndex, event.getNewColName(), event.isToggleHide());
      }

      if(infos != null) {
         BaseTableShowDetailsService.updateColumnHeaders(table, infos, columns);
         updateDateColInDCFormat(infos, dcFormat);
      }

      if(event.getDndInfo() != null) {
         ColumnSelection cols = infos == null || infos.isEmpty() ? columns : infos;
         cols = BaseTableShowDetailsService.getDetailColumns(event.getDndInfo(), cols);
         data.setDataColumns(cols);
         assembly.setColumnSelection(cols);
         infos = cols;
         table = BaseTableShowDetailsService.createColumnMapFilter(table, cols, columns);
         assembly.setData(table);
         BaseTableShowDetailsService.updateColumnVisibility(infos, assembly.getColumnSelection());
      }
      else if(infos != null && !infos.isEmpty()) {
         boolean isChanged = BaseTableShowDetailsService.isColumnChanged(infos, columns);

         //If binding changed, so not apply detail order and clear detail columns.
         if(isChanged) {
            data.setDataColumns(columns);
            assembly.setData(table);
         }
         else {
            table = BaseTableShowDetailsService.createColumnMapFilter(table, infos, columns);
            assembly.setData(table);
            BaseTableShowDetailsService.updateColumnVisibility(infos, assembly.getColumnSelection());
         }
      }

      final ColumnSelection ninfos = data.getDataColumns();

      // 5002 should match the limit in preview-table.component.ts (5001)
      table = new DcFormatTableLens(new MaxRowsTableLens(table, 5002), dcFormat);

      // set columns visibility and format
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

         if(XConstants.DATE_FORMAT.equals(formatString) && !"Custom".equals(dateSpec)) {
            formatSpec = dateSpec;
         }

         Class<?> dtype = table.getColType(event.getColumn());

         // avoid changing number (default) alignment to be changed to left
         if(Number.class.isAssignableFrom(dtype)) {
            format.getUserDefinedFormat().setAlignmentValue(H_RIGHT);
         }

         format.getUserDefinedFormat().setFormatExtent(
            formatSpec != null && formatSpec.length() > 0 ?
               formatSpec : null);

         try {
            List<ColumnInfo> columnInfos =
               box2.getColumnInfos(tname, WorksheetEventUtil.getMode(assembly));
            ColumnSelection columnSelection = assembly.getColumnSelection();

            if(columnInfos.size() != table.getColCount()) {
               DataRef attribute = columnSelection.getAttribute(event.getColumn());

               if(attribute instanceof ColumnRef) {
                  columnInfo = new ColumnInfo((ColumnRef) attribute, null, null,
                                              null, null, null);
               }
            }

            if(columnInfo == null) {
               columnInfo = columnInfos.get(event.getColumn());
            }
         }
         catch(Exception e) {
            columnInfo = null;
         }
      }

      ((FormatTableLens2) table).addTableFormat(data, assembly, assembly.getColumnSelection(), columnInfo, format);

      // set sort ref
      SortInfo sortInfo = assembly.getSortInfo();
      sortInfo.clear();

      sortInfo.setListener(evt -> data.setDataSortRef((SortRef) evt.getNewValue()));

      SortRef sortRef = data.getDataSortRef();

      if(sortRef != null) {
         sortInfo.addSort(sortRef);
      }

      //Fix assemblyInfo class for export
      if(rws.isPreview()) {
         assembly.getWSAssemblyInfo().setClassName("WSPreviewTable");
      }

      DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo)data.getInfo();

      if(event.getDetailStyle() != null) {
         if("null".equals(event.getDetailStyle())) {
            dinfo.setDetailStyle(null);
         }
         else {
            dinfo.setDetailStyle(event.getDetailStyle());
         }
      }

      if(dispatcher != null) {
         int start = 0;
         table.moreRows(XTable.EOT);
         int end = table.getRowCount();
         int colCount = table.getColCount();
         PreviewTableCellModel[][] tableCells = new PreviewTableCellModel[end][colCount];
         int[] colWidths = {};

         if(ninfos != null) {
            colWidths = IntStream.range(0, ninfos.getAttributeCount())
               .map(i -> {
                  ColumnRef ref = (ColumnRef) ninfos.getAttribute(i);
                  return ref.getPixelWidth();
               }).toArray();
         }

         TableLens tlens = table;
         String style = dinfo.getDetailStyle();

         if(style != null) {
            String orgID = box != null && box.getAssetEntry() != null ? box.getAssetEntry().getOrgID() : null;
            TableStyle styleTable = VSUtil.getTableStyle(style, orgID);

            if(styleTable != null) {
               styleTable = (TableStyle) styleTable.clone();
               styleTable.setTable(table);
               tlens = styleTable;
            }
         }

         for(int row = start; row < end; row++) {
            for(int col = 0; col < colCount; col++) {
               if(((ColumnRef) ninfos.getAttribute(col)).isVisible()) {
                  tableCells[row][col] = BaseTableCellModel.createPreviewCell(tlens, row, col,
                                                                              style != null, null, null, box.getVariableTable());
               }
            }
         }

         TableViewStylePaneController styleController = new TableViewStylePaneController();
         TableStylePaneModel styleModel = new TableStylePaneModel();

         try {
            RuntimeViewsheet rvs = getViewsheetEngine().getViewsheet(vsId, principal);
            styleModel.setTableStyle(style);
            styleModel.setStyleTree(dialogService.getStyleTree(rvs, principal, false));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         LoadPreviewTableCommand command = LoadPreviewTableCommand.builder()
            .tableData(tableCells)
            .worksheetId(wid)
            .sortInfo(detailsTableSortInfo)
            .colWidths(colWidths)
            .styleModel(styleModel)
            .build();
         dispatcher.sendCommand(name, command);
      }

      return (FormatTableLens2) table;
   }

   private TableLens hideFirstPeriod(DataVSAssembly data, TableLens table) {
      if(DateComparisonUtil.appliedDateComparison(data.getVSAssemblyInfo())) {
         DateCompareAbleAssemblyInfo dcAssemblyInfo =
            (DateCompareAbleAssemblyInfo) data.getVSAssemblyInfo();
         DateComparisonInfo dcInfo = dcAssemblyInfo.getDateComparisonInfo();

         if(dcInfo != null && dcInfo.isStdPeriod() && !dcInfo.isValueOnly()) {
            Date startDate = dcInfo.getStartDate();
            VSDataRef dateComparisonRef = dcAssemblyInfo.getDateComparisonRef();
            int index = Util.findColumn(table, dateComparisonRef.getFullName());

            if(index >= 0) {
               HiddenRowColFilter hiddenRowColFilter = new HiddenRowColFilter(table);

               for(int row = 0; row < table.getRowCount(); row++) {
                  Object object = table.getObject(row, index);

                  if(object instanceof Date && ((Date) object).compareTo(startDate) < 0) {
                     hiddenRowColFilter.hiddenRow(row);
                  }
               }

               return hiddenRowColFilter;
            }
         }
      }

      return table;
   }

   private List<AbstractDataSet> getSortedSubdataSets(FullProjectedDataSet dset) {
      List<AbstractDataSet> subDataSets = new ArrayList<>();

      for(AbstractDataSet subSet : dset.getSubDataSets()) {
         // apply ValueOrder sorting to each sub data set
         SortedDataSet sortedSubSet = new SortedDataSet(subSet);

         for(int i = 0; i < subSet.getColCount(); i ++) {
            String header = subSet.getHeader(i);
            sortedSubSet.addSortColumn(header, false);
         }

         subDataSets.add(sortedSubSet);
      }

      return subDataSets;
   }

   /**
    * For dc chart, partCol in dcFormat should be updated to the new header after
    * rename the col header in show summary data pane.
    */
   private static void updateDateColInDCFormat(ColumnSelection detail,
                                               DateComparisonFormat format)
   {
      if(format == null || detail == null) {
         return;
      }

      ColumnSelection original = detail;
      ColumnSelection doNotApplyAlias = detail.clone();

      for(int i = 0; i < doNotApplyAlias.getAttributeCount(); i++) {
         DataRef attribute = doNotApplyAlias.getAttribute(i);

         if(attribute instanceof ColumnRef) {
            ((ColumnRef) attribute).setApplyingAlias(false);
         }
      }

      setFormatColAlias(format.getPartCol(), format, original, doNotApplyAlias, true);
      setFormatColAlias(format.getDateCol(), format, original, doNotApplyAlias, false);
   }

   private static void setFormatColAlias(String colName, DateComparisonFormat format, ColumnSelection original,
                                         ColumnSelection doNotApplyAlias, boolean partCol)
   {
      DataRef colRef = doNotApplyAlias.getAttribute(colName);

      if(colRef == null) {
         return;
      }

      int index = doNotApplyAlias.indexOfAttribute(colRef);

      if(index < 0) {
         return;
      }

      DataRef originalColRef = original.getAttribute(index);

      if(originalColRef instanceof ColumnRef && ((ColumnRef) originalColRef).isApplyingAlias()
         && colRef instanceof ColumnRef && !Tool.isEmptyString(((ColumnRef) colRef).getAlias()))
      {
         if(partCol) {
            format.setDatePartColAlias(((ColumnRef) colRef).getAlias());
         }
         else {
            format.setDateColAlias(((ColumnRef) colRef).getAlias());
         }
      }
   }

   private static class DcFormatTableLens extends FormatTableLens2 {
      public DcFormatTableLens(TableLens base, DateComparisonFormat dcFormat) {
         super(base);
         this.dcFormat = dcFormat;

         if(dcFormat != null) {
            partCol = Util.findColumn(base, Tool.isEmptyString(dcFormat.getDatePartColAlias()) ?
               dcFormat.getDatePartCol() : dcFormat.getDatePartColAlias());
            dateCol = Util.findColumn(base, Tool.isEmptyString(dcFormat.getDateColAlias()) ? dcFormat.getDateCol() :
               dcFormat.getDateColAlias());
         }
      }

      @Override
      protected Object format(int r, int c, Object obj) {
         if(r < getHeaderRowCount()) {
            return obj;
         }

         if(partCol == c && dateCol >= 0 && r > 0) {
            Object date = getTable().getObject(r, dateCol);
            Format fmt = getUserCellFormat(r, c);

            if(obj instanceof Integer) {
               return dcFormat.format((Integer) obj, (Date) date, fmt);
            }
            else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
               DCMergeDatePartFilter.MergePartCell cell = (DCMergeDatePartFilter.MergePartCell) obj;
               return fmt == null ? dcFormat.format(cell, (Date) date) :
                  DateComparisonUtil.formatPartMergeCell(cell, (Date) date, fmt);
            }
         }

         return super.format(r, c, obj);
      }

      public Format getUserCellFormat(int r, int c) {
         TableLens lens = Util.getNestedTable(this, FormatTableLens.class);

         if(lens == null) {
            return null;
         }

         TableFormat rowf = getRowTableFormat(r);
         TableFormat colf = getColTableFormat(c);
         TableFormat cellf = getCellTableFormat(r, c);

         if(rowf == null && colf == null && cellf == null) {
            return null;
         }

         return getCellFormat(r, c);
      }

      /**
       * Add a getCellFormat method for 3 parameters overriding
       * the method in AttributeTableLens.
       * @param r the specified row.
       * @param c the specified col.
       * @param cellOnly specified cellonly.
       * @return cell format for the specified cell.
       */
      @Override
      public Format getCellFormat(int r, int c, boolean cellOnly) {
         checkInit();
         TableFormat colf = getColTableFormat(c);
         TableFormat rowf = getRowTableFormat(r);
         TableFormat cellf = getCellTableFormat(r, c);
         Format defaultformat = cellOnly ? null : table.getDefaultFormat(r, c);

         if(table instanceof AbstractTableLens) {
            ((AbstractTableLens) table).setLocal(getLocale());
         }

         return mergeFormat(defaultformat,
                            attritable == null ? null : attritable.getCellFormat(r, c),
                            colf == null ? null : colf.getFormat(getLocale()),
                            rowf == null ? null : rowf.getFormat(getLocale()),
                            cellf == null ? null : cellf.getFormat(getLocale()));
      }

      private final DateComparisonFormat dcFormat;
      private int partCol = -1, dateCol = -1;
   }

   private final VSDialogService dialogService;
}
