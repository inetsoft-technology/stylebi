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

package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.composer.vs.objects.controller.VSTableService;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.table.BaseTableEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSFormatModel;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.model.table.BaseTableModel;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Payload;

import java.awt.*;
import java.security.Principal;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class BaseTableService<T extends BaseTableEvent> {

   /**
    * Creates a new instance of <tt>BaseTableService</tt>.
    *
    */
   protected BaseTableService(CoreLifecycleService coreLifecycleService,
                              ViewsheetService viewsheetService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Ensure a uniform api for the event handlers
    *
    * @param event      the type of event that this controller handles
    * @param principal  principal object
    * @param dispatcher command dispatcher
    * @param linkUri    the base link URI for the application.
    */
   public abstract Void eventHandler(String runtimeId, T event, Principal principal,
                                     CommandDispatcher dispatcher,
                                     String linkUri) throws Exception;

   public static void loadTableData(RuntimeViewsheet rvs, String name, int mode,
                                    int start, int rowCount, String linkUri,
                                    CommandDispatcher dispatcher)
      throws Exception
   {
      loadTableData(rvs, name, mode, start, rowCount, linkUri, dispatcher, true);
   }

   /**
    * Loads the requested table data and sends it to the client. This method will
    * be called on the initial table load
    *
    * @param rvs        the runtime viewsheet instance.
    * @param name       the table assembly name.
    * @param mode       the mode.
    * @param start      the index of the first row to return.
    * @param rowCount   the number of rows to return.
    * @param linkUri    the base URI from which to retrieve assets.
    * @param dispatcher the command dispatcher.
    * @param refreshData send command to refresh data.
    */
   public static void loadTableData(RuntimeViewsheet rvs, String name, int mode,
                                    int start, int rowCount, String linkUri,
                                    CommandDispatcher dispatcher, boolean refreshData)
      throws Exception
   {
      // for Feature #26586, add ui processing time record.

      ProfileUtils.addExecutionBreakDownRecord(rvs.getViewsheetSandbox().getID(),
                                               ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            loadTableData0(rvs, name, mode, start, rowCount, linkUri, dispatcher, refreshData);
         }
      );

      //loadTableData0(rvs, name, mode, start, rowCount, linkUri, dispatcher, refreshData);
   }

   /**
    * Loads the requested table data and sends it to the client. This method will
    * be called on the initial table load
    *  @param rvs        the runtime viewsheet instance.
    * @param name       the table assembly name.
    * @param mode       the mode.
    * @param start      the index of the first row to return.
    * @param rowCount   the number of rows to return.
    * @param linkUri    the base URI from which to retrieve assets.
    * @param dispatcher the command dispatcher.
    * @param refreshData send command to refresh data.
    */
   private static void loadTableData0(RuntimeViewsheet rvs, String name, int mode,
                                      int start, int rowCount, String linkUri,
                                      CommandDispatcher dispatcher, boolean refreshData)
   {
      long startTime = System.currentTimeMillis();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || box == null) {
         return;
      }

      try {
         String oname = name;
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         VSAssembly vsassembly = null;

         // @by stephenwebster, For bug1434058776599
         // It doesn't seem reasonable to let the event occur, if later on
         // in VSEventUtil we remove the LoadTableCommand.  This will prevent
         // unnecessary work from occurring if the assembly generating the
         // command is not visible in the tab at design time.
         try {
            vsassembly = rvs.getViewsheet().getAssembly(oname);

            if(!VSEventUtil.isVisibleTabVS(vsassembly, rvs.isRuntime())) {
               return;
            }
         }
         catch(Exception ex) {
            //ignore, not expecting any exception here.
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);

         if(vsassembly instanceof CalcTableVSAssembly) {
            CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) vsassembly.getVSAssemblyInfo();
            int properColCount = getHeaderColCount((TableDataVSAssembly) vsassembly, lens);

            if(info.getHeaderColCount() != properColCount) {
               info.setHeaderColCount(properColCount);
               lens = box.getVSTableLens(oname, detail);
            }
         }

         FormTableLens formLens = null;

         // disable editing on data tip and pop
         if(!vs.getDataTips().contains(oname) &&
            !vs.getFlyoverViews().contains(oname) &&
            !vs.getPopComponents().contains(oname))
         {
            formLens = box.getFormTableLens(oname);
         }

         if(lens == null) {
            return;
         }

         if(Util.isTimeoutTable(lens)) {
            MessageCommand scmd = new MessageCommand();
            Catalog catalog = Catalog.getCatalog();
            String msg = catalog.getString("common.timeout", name);
            scmd.setMessage(msg);
            scmd.setType(MessageCommand.Type.ERROR);
            dispatcher.sendCommand(scmd);
            return;
         }

         int ccount = lens.getColCount();

         if(ccount > 500) {
            Catalog catalog = Catalog.getCatalog();
            String message = catalog.getString("common.colMaxCount", ccount, name);
            CoreTool.addUserMessage(message);
            lens.setMaxCols(500);
         }

         final int tableSize = vsassembly.getPixelSize().height;
         final int[] rowHeights = lens.getRowHeights();
         int sumRowHeight = 0;
         int row = 0;

         if(rowHeights != null) {
            for(row = 0; row < rowHeights.length && sumRowHeight < tableSize; row++) {
               sumRowHeight += rowHeights[row];

               // if row heights are set to 0 then try not to load the whole table
               if(row >= rowCount && row > sumRowHeight) {
                  break;
               }
            }
         }

         rowCount = Math.max(row, rowCount);

         // check more rows to init table, fix bug1243742452824
         lens.moreRows(start + rowCount - 1);
         lens.setLinkURI(linkUri);
         final int tableRowCount = lens.getRowCount();
         int count = tableRowCount;
         boolean more = count < 0;
         count = count < 0 ? -count - 1 : count;
         more = more || count > start + rowCount;

         if(!more) {
            LOG.debug("Table " + name + " finished processing: " + tableRowCount);
         }

         int end = Math.min(count, start + rowCount);
         String limitMessage = "";

         if(!more && (rvs.isPreview() || rvs.isViewer())) {
            limitMessage = VSEventUtil.addWarningText(lens, box, name, true);
         }

         // this looks backwards but it's not
         int runtimeRowHeaderCount = lens.getHeaderColCount();
         int runtimeColHeaderCount = lens.getHeaderRowCount();

         // @by davidd, 2011-11-11 Update Crosstab cached table lens, used
         // for improving performance of Drilldowns..
         if(vsassembly instanceof CrosstabVSAssembly) {
            CrosstabVSAssembly cvsa = (CrosstabVSAssembly) vsassembly;

            // Only save the lens, if this request is the more recent.
            if(startTime > cvsa.getLastDrillDownRequest()) {
               cvsa.setLastTableLens(lens);
            }

            final VSCrosstabInfo crosstabInfo =
               ((CrosstabVSAssembly) vsassembly).getVSCrosstabInfo();

            if(crosstabInfo != null) {
               final DataRef[] runtimeColHeaders = crosstabInfo.getRuntimeColHeaders();
               final DataRef[] runtimeRowHeaders = crosstabInfo.getRuntimeRowHeaders();

               if(runtimeRowHeaders != null) {
                  runtimeRowHeaderCount = runtimeRowHeaders.length;
               }

               if(runtimeColHeaders != null) {
                  runtimeColHeaderCount = runtimeColHeaders.length;
               }
            }
         }

         if(vsassembly instanceof TableDataVSAssembly) {
            ((TableDataVSAssembly) vsassembly).setLastStartRow(start);
         }

         VSEventUtil.getAssemblyInfo(rvs, vsassembly);
         addScriptables(lens, vsassembly, box);

         if(refreshData) {
            BaseTableCellModel[][] tableCells = getTableCells(vsassembly.getVSAssemblyInfo(), lens,
                                                              start, end, formLens, rvs.isRuntime());
            BaseTableCellModel[][] tableHeaderCells = null;

            //if assembly is Table, and start != 0, then should send table header to web when load
            //table data. Because the order of the title may change.
            if(start != 0 && vsassembly instanceof TableDataVSAssembly) {
               tableHeaderCells = getTableCells(vsassembly.getVSAssemblyInfo(), lens,
                                                0, lens.getHeaderRowCount(), formLens, rvs.isRuntime());
            }

            HyperlinkModel[] rowHyperlinks = null;

            if(formLens != null && formLens.isEdit()) {
               rowHyperlinks = new HyperlinkModel[]{};
            }
            else {
               rowHyperlinks = getRowHyperlinks(lens, vsassembly, start, end);
            }

            LoadTableDataCommand.Builder command = LoadTableDataCommand.builder()
               .tableCells(tableCells)
               .start(start)
               .end(end)
               .formChanged(formLens != null && formLens.isChanged())
               .limitMessage(limitMessage)
               .runtimeRowHeaderCount(runtimeRowHeaderCount)
               .runtimeColHeaderCount(runtimeColHeaderCount)
               .runtimeDataRowCount(tableRowCount - runtimeRowHeaderCount)
               .tableHeaderCells(tableHeaderCells)
               .rowHyperlinks(rowHyperlinks);
            setLayout((TableDataVSAssembly) vsassembly, lens, command);
            dispatcher.sendCommand(name, command.build());
         }

         DateComparisonUtil.checkGraphValidity(vsassembly.getVSAssemblyInfo(), lens);
      }
      catch(ColumnNotFoundException | ExpiredSheetException | CancelledException |
            MessageException | ConfirmException e)
      {
         throw e;
      }
      catch(BoundTableNotFoundException e) {
         Throwable thrown = LOG.isDebugEnabled() ? e : null;
         LOG.warn("Failed to load the table data: {}", e.getMessage(), thrown);
         Catalog catalog = Catalog.getCatalog();
         MessageCommand scmd = new MessageCommand();
         scmd.setMessage(catalog.getString("common.nodata", e.getMessage()));
         scmd.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(scmd);
      }
      catch(Throwable ex) {
         while(ex instanceof RuntimeException && ex.getCause() != null) {
            ex = ex.getCause();
         }

         if(ex instanceof ScriptException) {
            ViewsheetScope scope = box.getScope();
            ScriptEnv senv = scope.getScriptEnv();
            String suggestion = senv.getSuggestion((ScriptException)ex, null, scope);
            String updateMessage = "Script execution error in assembly: " +
               name + (suggestion != null ? "\nTo fix: " + suggestion : "") +
               "\nScript failed:\n" + ex.getMessage();
            MessageCommand cmd = new MessageCommand();
            cmd.setMessage(updateMessage);
            cmd.setType(MessageCommand.Type.INFO);
            dispatcher.sendCommand(cmd);
            return;
         }

         LOG.error("Failed to load the table data", ex);
         Catalog catalog = Catalog.getCatalog();
         String message = catalog.getString("common.nodata", ex.getMessage());
         MessageCommand scmd = new MessageCommand();
         scmd.setMessage(message);
         scmd.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(scmd);
      }
   }

   public static int getHeaderRowCount(TableDataVSAssembly assembly, VSTableLens lens) {
      return getHeaderCount(assembly,
                            () -> ((CalcTableVSAssemblyInfo) assembly.getInfo()).getHeaderRowCount(),
                            () -> lens.getHeaderRowCount());
   }

   public static int getHeaderColCount(TableDataVSAssembly assembly, VSTableLens lens) {
      int hcol = getHeaderCount(assembly,
                                () -> ((CalcTableVSAssemblyInfo) assembly.getInfo()).getHeaderColCount(),
                                () -> lens.getHeaderColCount());

      for(int i = 0; i < lens.getHeaderRowCount() && lens.moreRows(i); i++) {
         for(int j = 0; j < hcol; j++) {
            Dimension span = lens.getSpan(i, j);

            if(span != null && j + span.width > hcol) {
               CoreTool.addUserMessage(
                  Catalog.getCatalog().getString("composer.vs.calctable.headerZero"));
               hcol = 0;

               break;
            }
         }
      }

      return hcol;
   }

   private static int getHeaderCount(TableDataVSAssembly assembly,
                                     Supplier<Integer> calcTableSupplier, Supplier<Integer> otherSupplier)
   {
      int headerCount;

      // calc tables use design values for the header row/col count. if you have a header cell
      // that expands then we use the cinfo value to determine what rows are fixed/scrolling
      if(assembly instanceof CalcTableVSAssembly) {
         headerCount = calcTableSupplier.get();
      }
      else {
         headerCount = otherSupplier.get();
      }

      return headerCount;
   }

   private static void setLayout(TableDataVSAssembly assembly, VSTableLens lens,
                                 LoadTableDataCommand.Builder builder)
   {
      int colCount = lens.getColCount();
      int headerRowCount = getHeaderRowCount(assembly, lens);
      int headerColCount = getHeaderColCount(assembly, lens);

      int dataColCount = colCount - headerColCount;

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) info;
      lens.initTableGrid(tinfo);
      double[] colWidths = getColWidths(assembly, lens);
      lens.initTableGrid(tinfo);
      lens.setColWidths(colWidths);

      int rowCount = lens.getRowCount();
      final int availableRowCount = Math.max((rowCount < 0 ? -rowCount - 1 : rowCount), 0);
      int dataRowCount = Math.max(0, availableRowCount - headerRowCount);
      boolean isWrappedHeader = headerRowCount > 0 && isWrapped(lens, headerRowCount - 1);
      int[] headerRowHeights;

      if(tinfo instanceof CrossBaseVSAssemblyInfo) {
         headerRowHeights = ((CrossBaseVSAssemblyInfo) tinfo)
            .getHeaderRowHeights(isWrappedHeader, headerRowCount);
      }
      else {
         headerRowHeights = tinfo.getHeaderRowHeights(isWrappedHeader);
      }

      int dataRowHeight = tinfo.getDataRowHeight(lens.getHeaderRowCount());
      int cssHeaderRowHeight = lens.getCSSHeaderRowHeight(tinfo);
      int cssDataRowHeight = lens.getCSSDataRowHeight(tinfo);

      if(cssHeaderRowHeight > 0) {
         Arrays.fill(headerRowHeights, cssHeaderRowHeight);
      }

      if(cssDataRowHeight > 0) {
         dataRowHeight = cssDataRowHeight;
      }

      if(dataRowHeight > 0) {
         dataRowHeight += lens.getCSSRowPadding(lens.getHeaderRowCount());
      }

      if(lens.getHeaderRowCount() > 0) {
         for(int i = 0; i < headerRowHeights.length; i++) {
            if(headerRowHeights[i] > 0) {
               headerRowHeights[i] += lens.getCSSRowPadding(i);
            }
         }
      }

      int scrollHeight = dataRowCount * dataRowHeight;

      builder.rowCount(availableRowCount)
         .colCount(colCount)
         .headerRowCount(headerRowCount)
         .headerColCount(headerColCount)
         .dataRowCount(dataRowCount)
         .dataColCount(dataColCount)
         .runtimeDataRowCount(dataRowCount)
         .colWidths(colWidths)
         .headerRowHeights(headerRowHeights)
         .dataRowHeight(dataRowHeight)
         .scrollHeight(scrollHeight)
         .headerRowPositions()
         .dataRowPositions();

      // calculate cell heights and positions before creating the annotations
      calculateCellHeights(lens, colCount, headerRowCount, dataRowCount, info, builder);
   }

   private static double getLayoutWidth(TableDataVSAssemblyInfo info, double width, int i,
                                        VSTableLens lens)
   {
      // @by stephenwebster, For Bug #18103
      // Determine expanded column widths from the table layout if defined.
      // The strategy will be to prefer the layout widths if no widths have been defined
      if(info instanceof CalcTableVSAssemblyInfo && (Double.isNaN(width) || width < 0)) {
         TableLayout tableLayout = ((CalcTableVSAssemblyInfo) info).getTableLayout();
         TableDataPath columnDataPath = getColumnPath(i, lens);

         if(columnDataPath != null && tableLayout != null && columnDataPath.getPath() != null &&
            columnDataPath.getPath().length > 0)
         {
            int baseColumnIndex = TableTool.getCol(lens, columnDataPath.getPath()[0]);

            if(baseColumnIndex < tableLayout.getColCount()) {
               return tableLayout.getColWidth(baseColumnIndex);
            }
         }
      }

      return width;
   }

   /**
    * Get an unique path to identify a column.
    * @param col The column index
    * @return The TableDataPath represented by this column.
    */
   private static TableDataPath getColumnPath(int col, VSTableLens lens) {
      TableDataDescriptor desc = lens.getDescriptor();
      TableDataPath path = desc.getColDataPath(col);

      if(path != null) {
         return path;
      }

      return null;
   }

   /**
    * Find column with same data path from the beginning.
    */
   private static int findSameColumn(int col, VSTableLens lens, double[] colWidths) {
      TableDataPath path = getColumnPath(col, lens);

      if(path != null) {
         for(int i = 0; i < colWidths.length; i++) {
            if(colWidths[i] >= 0 && path.equals(getColumnPath(i, lens))) {
               return i;
            }
         }
      }

      return -1;
   }

   public static boolean isWrapped(VSTableLens lens, int headerRowCount) {
      boolean wrapped = false;
      int colCount = lens.getColCount();

      for(int i = 0; i < colCount && lens.moreRows(headerRowCount); i++) {
         VSFormat format = lens.getFormat(headerRowCount, i);

         if(format != null && format.isWrapping()) {
            wrapped = true;
            break;
         }
      }

      return wrapped;
   }

   public static int[] getHeaderRowPositions(VSTableLens lens, boolean isWrapped,
                                             int headerRowCount)
   {
      int[] headerRowPositions = new int[headerRowCount + 1];

      if(!isWrapped) {
         return headerRowPositions;
      }

      int position = 0;
      int height;

      for(int row = 0; row < headerRowCount && lens.moreRows(row); row++) {
         headerRowPositions[row] = position;
         height = (int) lens.getRowHeightWithPadding(lens.getWrappedHeight(row, true), row);
         position += height;
      }

      headerRowPositions[headerRowCount] = position;

      return headerRowPositions;
   }

   public static int[] getDataRowPositions(VSTableLens lens, boolean isWrapped,
                                           int headerRowCount, int dataRowCount)
   {
      int[] dataRowPositions = new int[dataRowCount + 1];

      if(!isWrapped) {
         return dataRowPositions;
      }

      int position = 0;
      int height = 0;

      for(int row = headerRowCount, r = 0; r < dataRowCount && lens.moreRows(row); row++, r++) {
         dataRowPositions[r] = position;

         if(row < lens.getRowHeights().length) {
            height = (int) lens.getRowHeightWithPadding(lens.getWrappedHeight(row, true), row);
         }

         position += height;
      }

      if(dataRowCount >= 0 && dataRowPositions.length > dataRowCount) {
         dataRowPositions[dataRowCount] = position;
      }

      return dataRowPositions;
   }

   private static void calculateCellHeights(VSTableLens lens, int colCount, int headerRowCount,
                                            int dataRowCount, VSAssemblyInfo info,
                                            LoadTableDataCommand.Builder builder)
   {
      boolean wrapped = false;

      if(info instanceof TableDataVSAssemblyInfo) {
         wrapped = !((TableDataVSAssemblyInfo) info).getRowHeights().isEmpty();
      }

      if(wrapped) {
         // if row height is set explicitly (in date comparison hideFirstPeriod),
         // individual row heights should be passed so the setting would be applied.
      }
      else if(info instanceof CrossBaseVSAssemblyInfo || info instanceof CalcTableVSAssemblyInfo) {
         FormatInfo finfo = info.getFormatInfo();

         for(TableDataPath path : finfo.getPaths()) {
            VSCompositeFormat fmt = finfo.getFormat(path);

            if(fmt != null && fmt.isWrapping()) {
               wrapped = true;
            }
         }
      }
      else {
         wrapped = headerRowCount > 0 && isWrapped(lens, headerRowCount - 1);
         wrapped = wrapped || isWrapped(lens, headerRowCount);
      }

      if(wrapped) {
         // Since this is used to also calculate the row heights, we need one extra position
         // to calculate the last row height.
         int[] headerRowPositions = getHeaderRowPositions(lens, true, headerRowCount);
         int[] dataRowPositions = getDataRowPositions(lens, true, headerRowCount, dataRowCount);

         final int dataRowsHeight = dataRowPositions[dataRowCount];
         builder.headerRowPositions(headerRowPositions)
            .dataRowPositions(dataRowPositions)
            .scrollHeight(dataRowsHeight);
      }

      builder.wrapped(wrapped);
   }

   /**
    * {@link TableConditionUtil#createCalcTableConditions(VSAssembly, int[][], String, ViewsheetSandbox)}
    *
    * Use for both flyovers and showdetails
    */
   public static ConditionList createCalcTableConditions(
      CalcTableVSAssembly assembly,
      ViewsheetSandbox box,
      String name,
      Map<Integer, int[]> selectedCells,
      String tipMsg,
      CommandDispatcher dispatcher)
      throws Exception
   {
      java.util.List<int[]> rowcols = new ArrayList<>();

      for(Integer row : selectedCells.keySet()) {
         int[] cols = selectedCells.get(row);

         for(int i = 0; i < cols.length; i++) {
            rowcols.add(new int[] { cols[i], row });
         }
      }

      int[][] rowcolsArr = rowcols.toArray(new int[0][0]);
      TableLayout layout = assembly.getTableLayout();
      boolean isEmpty = isEmptyCell(layout, rowcolsArr, name, box);

      if(isEmpty) {
         MessageCommand scmd = new MessageCommand();
         scmd.setMessage(Catalog.getCatalog().getString("composer.vs.calctable.empty.cell.showdetail"));
         scmd.setType(MessageCommand.Type.INFO);
         dispatcher.sendCommand(scmd);
         return new ConditionList();
      }

      ConditionList conds = TableConditionUtil.createCalcTableConditions(assembly, rowcolsArr, name, box);
      conds.trim();

      if(conds.getSize() < 1 && tipMsg != null) {
         MessageCommand scmd = new MessageCommand();
         scmd.setMessage(tipMsg);
         scmd.setType(MessageCommand.Type.INFO);
         dispatcher.sendCommand(scmd);
         return new ConditionList();
      }

      return conds;
   }

   private static boolean isEmptyCell(TableLayout layout, int[][] rowcols, String name, ViewsheetSandbox box)
      throws Exception
   {
      TableLens lens = (TableLens) box.getData(name);
      RuntimeCalcTableLens rlens = (RuntimeCalcTableLens) Util.getNestedTable(lens, RuntimeCalcTableLens.class);
      boolean isEmpty = true;

      for(int i = 0; i < rowcols.length; i++) {
         int col = rowcols[i][0];
         int row = rowcols[i][1];
         int lr = rlens.getRow(row);
         int lc = rlens.getCol(col);

         if(lr >= 0 && lc >= 0) {
            TableCellBinding binding = (TableCellBinding) layout.getCellBinding(lr, lc);

            if(binding != null) {
               isEmpty = false;
               break;
            }
         }
      }

      return isEmpty;
   }

   /**
    * Get a {@link BaseTableCellModel} array for the given rows
    *
    * @param lens       The table lens of the table
    * @param start      The starting row
    * @param end        The ending row
    * @param formLens   The form table lens
    * @param rt         Whether the viewsheet is in runtime mode
    *
    * @return A 2d array of BaseTableCells representing the table
    */
   private static BaseTableCellModel[][] getTableCells(VSAssemblyInfo info,
                                                       VSTableLens lens,
                                                       int start, int end,
                                                       FormTableLens formLens,
                                                       boolean rt)
   {
      int colCount = lens.getColCount();
      // in case table changed during reload, make sure no negative rows
      end = Math.max(start, end);
      int rowCount = end - start;
      SpanMap spanMap = lens.getSpanMap(start, rowCount);
      BaseTableCellModel[][] tableCells = new BaseTableCellModel[rowCount][colCount];
      RuntimeCalcTableLens free = (RuntimeCalcTableLens)
         Util.getNestedTable(lens, RuntimeCalcTableLens.class);
      int[] spanRow = new int[colCount];
      Arrays.fill(spanRow, start);
      CrossTabFilter filter = Util.getCrosstab(lens);
      Map<VSFormat, VSFormatModel> formatModelCache = new HashMap<>();

      for(int i = start; i < end; i++) {
         for(int j = 0; j < colCount; j++) {
            Rectangle span = spanMap.get(i, j);

            if(span != null && tableCells[i - start][j] == null) {
               span.height = Math.min(span.height, end - i);
               span.width = Math.min(span.width, colCount - j);

               // Create "empty span cell" cells in the rectangular area described
               // by the lens span excluding the current cell
               if(span.width > 1 || span.height > 1) {
                  for(int k = 0; k < span.height; k++) {
                     for(int m = 0; m < span.width; m++) {
                        if(k != 0 || m != 0) {
                           final BaseTableCellModel spanCell = BaseTableCellModel.createSpanCell();
                           spanCell.setColSpan(span.width - m);
                           spanCell.setRowSpan(span.height - k);
                           tableCells[i - start + k][j + m] = spanCell;
                        }
                     }
                  }
               }

               // Create the current cell with the row/col span attributes
               BaseTableCellModel cell;

               if(formLens == null) {
                  cell = BaseTableCellModel.createTableCell(info, lens, i, j, spanRow[j]++, formatModelCache);

                  for(int colSpanCount = 1; colSpanCount < span.width; colSpanCount++) {
                     spanRow[j + colSpanCount]++;
                  }

                  for(int rowSpanCount = 1; rowSpanCount < span.height; rowSpanCount++) {
                     spanRow[j]++;
                  }
               }
               else {
                  cell = BaseTableCellModel.createFormCell(info, formLens, lens, i, j, formatModelCache);
               }

               cell.setColSpan(span.width);
               cell.setRowSpan(span.height);

               if(info instanceof CrosstabVSAssemblyInfo) {
                  CrosstabVSAssemblyInfo crosstabInfo = (CrosstabVSAssemblyInfo) info;
                  TableDataPath path = lens.getTableDataPath(i, j);

                  DataRef ref = VSTableService.getCrosstabCellDataRef(
                     crosstabInfo.getVSCrosstabInfo(), path, i, j, true);

                  if(ref != null) {
                     String field = ((VSDataRef) ref).getFullName();

                     if(ref instanceof VSDimensionRef) {
                        VSDimensionRef dimRef = (VSDimensionRef) ref;
                        cell.setPeriod(dimRef.getDates() != null && dimRef.getDates().length >= 2);
                     }

                     if(ref instanceof VSAggregateRef) {
                        field = VSUtil.getAggregateField(field, ref);
                     }

                     cell.setField(field);
                  }

                  if(ref instanceof VSDimensionRef) {
                     // populate drill level
                     VSDimensionRef dim = (VSDimensionRef) ref;
                     cell.setDrillLevel(VSUtil.getDrillLevel(dim, crosstabInfo.getXCube()));

                     // setGrouped
                     SNamedGroupInfo groupInfo = (SNamedGroupInfo) dim.getNamedGroupInfo();
                     boolean grouped = false;

                     if(cell.getCellData() != null && groupInfo != null) {
                        grouped = groupInfo.getGroupValue(cell.getCellData() + "") != null;
                     }

                     cell.setGrouped(grouped);
                  }
                  else if(ref instanceof VSAggregateRef && ((VSAggregateRef) ref).getCalculator() != null) {
                     Calculator calculator = ((VSAggregateRef) ref).getCalculator();
                     cell.setHasCalc(!calculator.supportSortByValue());
                  }
               }

               formatCellData(cell, info);
               tableCells[i - start][j] = cell;
            }
            else if(tableCells[i - start][j] == null) {
               BaseTableCellModel cell;

               // If not created already or a spanning cell then create normally
               if(formLens == null) {
                  cell = BaseTableCellModel.createTableCell(info, lens, i, j, spanRow[j]++, formatModelCache);
               }
               else {
                  cell = BaseTableCellModel.createFormCell(info, formLens, lens, i, j, formatModelCache);
               }

               if(info instanceof CrosstabVSAssemblyInfo) {
                  CrosstabVSAssemblyInfo crosstabInfo = (CrosstabVSAssemblyInfo) info;

                  TableDataPath path = lens.getTableDataPath(i, j);
                  DataRef ref = VSTableService.getCrosstabCellDataRef(
                     crosstabInfo.getVSCrosstabInfo(), path, i, j, true);

                  if(ref != null) {
                     String field = ((VSDataRef) ref).getFullName();

                     if(ref instanceof VSAggregateRef && ((VSAggregateRef) ref).getCalculator() != null) {
                        Calculator calculator = ((VSAggregateRef) ref).getCalculator();
                        cell.setHasCalc(!calculator.supportSortByValue());
                     }

                     // fix Bug #24393, someone add a logic in VSAggregateRef.update
                     // function in v11.3 for a feature with no feature id. This logic
                     // create AliasDataRef for the columnRef and using the fullname of
                     // the VSAggregateRef as the name of the AliasDataRef which causes
                     // the fullname of VSAggregateRef contains duplicate formula, change
                     // the sensitive logic may cause some unknow problem, so fix this
                     // bug here.
                     if(ref instanceof VSAggregateRef) {
                        field = VSUtil.getAggregateField(field, ref);
                     }

                     cell.setField(field);
                  }

                  if(ref instanceof VSDimensionRef) {
                     // populate drill level
                     VSDimensionRef dim = (VSDimensionRef) ref;
                     cell.setDrillLevel(VSUtil.getDrillLevel(dim, crosstabInfo.getXCube()));
                     cell.setPeriod(dim.getDates() != null && dim.getDates().length >= 2);
                     // grouped
                     SNamedGroupInfo groupInfo = (SNamedGroupInfo)
                        ((VSDimensionRef) ref).getNamedGroupInfo();
                     final String cellString = getCellString(cell.getCellData());
                     boolean grouped = groupInfo != null && !(groupInfo instanceof DCNamedGroupInfo)
                        && cellString != null && groupInfo.getGroupValue(cellString) != null;
                     cell.setGrouped(grouped);
                  }
               }

               formatCellData(cell, info);
               tableCells[i - start][j] = cell;
            }

            if((info instanceof CalcTableVSAssemblyInfo) && tableCells[i - start][j] != null) {
               TableLayout layout = ((CalcTableVSAssemblyInfo) info).getTableLayout();
               int r = (free != null) ? free.getRow(i) : i;
               int c = (free != null) ? free.getCol(j) : j;
               CellBinding binding = layout.getCellBinding(r, c);

               if(binding != null) {
                  tableCells[i - start][j].setBindingType(binding.getType());
               }
            }

            if(filter != null && tableCells[i - start][j] != null) {
               int row = i;
               int col = j;

               if(DateComparisonUtil.appliedDateComparison(info)) {
                  row = TableTool.getBaseRowIndex(lens, filter, i);
                  col = TableTool.getBaseColIndex(lens, filter, j);
                  row = row < 0 ? i : row;
                  col = col < 0 ? j : col;
               }

               tableCells[i - start][j].setGrandTotalRow(filter.isGrandTotalRow(row));
               tableCells[i - start][j].setGrandTotalCol(filter.isGrandTotalCol(col));
               tableCells[i - start][j].setTotalRow(filter.isTotalRow(row));
               tableCells[i - start][j].setTotalCol(filter.isTotalCol(col));
               tableCells[i - start][j].setGrandTotalHeaderCell(
                  filter.isGrandTotalHeaderCell(row, col));
            }
         }
      }

      return tableCells;
   }

   private static HyperlinkModel[] getRowHyperlinks(VSTableLens table, VSAssembly vsassembly,
                                                    int start, int end)
   {
      if(table == null || !(vsassembly instanceof TableVSAssembly) ||
         vsassembly instanceof EmbeddedTableVSAssembly)
      {
         return new HyperlinkModel[]{};
      }

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) vsassembly.getVSAssemblyInfo();

      if(!info.getFormValue() && info.getRowHyperlink() != null) {
         HyperlinkModel[] hyperlinks = new HyperlinkModel[end - start];
         Hyperlink link = info.getRowHyperlink();

         for(int i = start; table.moreRows(i); i++) {
            if(i == end) {
               break;
            }

            if(i < table.getHeaderRowCount()) {
               hyperlinks[i - start] = null;
            }
            else {
               Hyperlink.Ref ref = new Hyperlink.Ref(link, table, i, -1);
               addOthersLinkParameters(table, link, ref);
               hyperlinks[i - start] = HyperlinkModel.createHyperlinkModel(ref);
            }
         }

         return hyperlinks;
      }

      return new HyperlinkModel[]{};
   }

   private static void addOthersLinkParameters(VSTableLens table, Hyperlink hyperlink,
                                               Hyperlink.Ref ref)
   {
      if(hyperlink.isSendReportParameters()) {
         VariableTable linkVarTable = table.getLinkVarTable();
         addLinkParameter(ref, linkVarTable);
      }

      if(hyperlink.isSendSelectionParameters()) {
         Hashtable selections = table.getLinkSelections();
         VSUtil.addSelectionParameter(ref, selections);
      }
   }

   private static void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      if(vtable == null) {
         return;
      }

      List<String> exists = new ArrayList<>();
      Enumeration<String> pnames = hlink.getParameterNames();
      Enumeration<String> vnames = vtable.keys();

      while(pnames.hasMoreElements()) {
         exists.add(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = vnames.nextElement();

         // don't include _USER_, _ROLES_, __principal__ entries
         if(exists.contains(name) || VariableTable.isContextVariable(name)) {
            continue;
         }

         Util.addLinkParameter(hlink, vtable, name);
      }
   }

   private static String getCellString(Object data) {
      return Objects.toString(data, null);
   }


   /**
    * Format the date data
    */
   protected static Object formatCellData(BaseTableCellModel cell, VSAssemblyInfo assemblyInfo) {
      if(!(assemblyInfo instanceof TableVSAssemblyInfo) || assemblyInfo instanceof TableVSAssemblyInfo &&
         !((TableVSAssemblyInfo) assemblyInfo).isForm() && !(assemblyInfo instanceof EmbeddedTableVSAssemblyInfo))
      {
         return cell.getCellData();
      }

      if(cell.getCellData() instanceof Timestamp) {
         return cell.getCellData();
      }
      else if(cell.getCellData() instanceof Time) {
         SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
         cell.formatCellData(sdf.format(((Time) cell.getCellData())));
      }
      else if(cell.getCellData() instanceof Date) {
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
         cell.formatCellData(sdf.format(((Date) cell.getCellData())));
      }

      return cell.getCellData();
   }

   /**
    * Get the base ref of an aggregate ref.
    */
   protected static DataRef getBaseRef(DataRef ref) {
      if(ref instanceof VSAggregateRef) {
         ref = ((VSAggregateRef) ref).getDataRef();

         if(ref instanceof ColumnRef) {
            ColumnRef cref = (ColumnRef) ref;
            DataRef bref = cref.getDataRef();

            if(bref instanceof AliasDataRef) {
               ref = ((AliasDataRef) bref).getDataRef();
            }
         }
      }

      return ref;
   }

   /**
    * Add scriptables to asset query sandbox.
    */
   private static void addScriptables(VSTableLens lens,
                                      VSAssembly vsassembly,
                                      ViewsheetSandbox vsbox)
   {
      TableHighlightAttr.HighlightTableLens table = (TableHighlightAttr.HighlightTableLens) Util.getNestedTable(
         lens, TableHighlightAttr.HighlightTableLens.class);

      if(table == null || vsassembly == null) {
         return;
      }

      table.setQuerySandbox(vsbox.getConditionAssetQuerySandbox(
         vsassembly.getViewsheet()));
   }

   /**
    * Load some properties for BaseTableModel which will be used to
    * expand table in vs binding pane.
    * @param  rvs       the runtime viewsheet.
    * @param  assembly  the target TableDataVSAssembly.
    * @param  model     the BaseTableModel to add properties.
    */
   public static void loadTableModelProperties(RuntimeViewsheet rvs,
                                               TableDataVSAssembly assembly,
                                               BaseTableModel model)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSTableLens lens = box.getVSTableLens(assembly.getAbsoluteName(), false);
      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(lens != null && tinfo != null) {
         model.setRowCount(lens.getRowCount());
         model.setDataRowHeight(tinfo.getDataRowHeight());
         model.setHeaderRowCount(lens.getHeaderRowCount());
         model.setColWidths(getColWidths(assembly, lens));
      }
   }

   /**
    * Return column widths of the target table.
    */
   public static double[] getColWidths(TableDataVSAssembly assembly, VSTableLens lens) {
      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) assembly.getInfo();
      Viewsheet vs = assembly.getViewsheet();
      Viewsheet topvs = VSUtil.getTopViewsheet(vs);
      float rscaleFont = topvs.getRScaleFont();
      int colCount = lens.getColCount();
      double[] colWidths = new double[colCount];
      double totalW = tinfo.getLayoutSize() != null ? tinfo.getLayoutSize().width :  tinfo.getPixelSize().width;

      for(int i = 0; i < colCount; i++) {
         colWidths[i] = getLayoutWidth(tinfo, tinfo.getColumnWidth2(i, lens), i, lens);
      }

      for(int i = 0; i < colCount; i++) {
         double width = colWidths[i];

         // setColumnWidth() changes column width for all columns of that type (35620)
         if((Double.isNaN(width) || width < 0) && assembly instanceof TableVSAssembly) {
            int col0 = findSameColumn(i, lens, colWidths);

            if(col0 >= 0) {
               width = tinfo.getColumnWidth2(col0, lens) == 0 ? 0 : colWidths[col0];
            }
         }

         boolean noWidth = Double.isNaN(width) || width < 0;
         width = lens.getColumnWidthWithPadding(width, i);

         // if column width not explicitly specified, fill up the table to match
         // the design view
         boolean fillWidth = i == colCount - 1 &&
            (!tinfo.isShrink() && totalW > width && width > 0 ||
               noWidth && totalW > AssetUtil.defw);

         if(fillWidth) {
            width = totalW;
         }
         else if(noWidth) {
            if(noWidth && i == colCount - 1 && i < lens.getColumnWidths().length &&
               lens.getColumnWidths()[i] > 0 && !Double.isNaN(lens.getColumnWidths()[i]))
            {
               width = lens.getColumnWidthWithPadding(lens.getColumnWidths()[i], i);
            }
            else {
               width = lens.getColumnWidthWithPadding(AssetUtil.defw, i);
            }
         }

         colWidths[i] = width * rscaleFont;
         totalW -= width;
      }

      return colWidths;
   }

   protected final CoreLifecycleService coreLifecycleService;
   protected final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(BaseTableService.class);
}
