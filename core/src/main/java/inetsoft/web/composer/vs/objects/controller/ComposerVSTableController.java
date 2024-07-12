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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.EmbeddedTableVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.*;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.event.table.ChangeVSTableCellsTextEvent;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.*;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

/**
 * Controller that processes vs table events in composer.
 */
@Controller
public class ComposerVSTableController {
   /**
    * Creates a new instance of <tt>ComposerVSTableController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public ComposerVSTableController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      VSObjectTreeService vsObjectTreeService,
      VSObjectModelFactoryService objectModelService,
      VSBindingService bfactory,
      AssetRepository assetRepository,
      ViewsheetService viewsheetService,
      CrosstabDrillHandler crosstabDrillHandler,
      VSAssemblyInfoHandler vsAssemblyInfoHandler)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.objectModelService = objectModelService;
      this.bfactory = bfactory;
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
      this.crosstabDrillHandler = crosstabDrillHandler;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
   }

    /**
      * Change table header text.
      *
      * @param event      the event parameters.
      * @param principal  a principal identifying the current user.
      * @param dispatcher the command dispatcher.
      *
      * @throws Exception if unable to retrieve/edit object.
      */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeColumnTitle/{row}/{col}")
   public void changeColumnTitle(@DestinationVariable("row") int row,
                                 @DestinationVariable("col") int col,
                                 @Payload ChangeVSObjectTextEvent event, Principal principal,
                                 CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockWrite();

      try {
         VSAssembly assembly = viewsheet.getAssembly(event.getName());
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if(assembly instanceof TableVSAssembly) {
            TableVSAssembly table = (TableVSAssembly) assembly;
            ColumnSelection columns = table.getColumnSelection();

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               ColumnRef ref = (ColumnRef) columns.getAttribute(i);

               if(ref.getAlias() != null && ref.getAlias().equals(event.getText())) {
                  MessageCommand command = new MessageCommand();
                  command.setType(MessageCommand.Type.ERROR);
                  command.setMessage(Catalog.getCatalog().getString("vs.table.duplicateAlias"));
                  dispatcher.sendCommand(command);
                  return;
               }
            }

            ColumnRef column = (ColumnRef) columns.getAttribute(getActualColIndex(columns, col));
            String oname = column.getAlias();

            if(oname == null) {
               oname = column.getAttribute();
            }

            column.setAlias(event.getText());

            if(!Tool.equals(oname, event.getText())) {
               syncHighlight(table, oname, event.getText());
            }

            FormatInfo finfo = table.getFormatInfo();
            FormatInfo nfinfo = new FormatInfo();

            for(TableDataPath path : finfo.getPaths()) {
               String[] pathArr = path.getPath();

               if(pathArr.length == 1 && Objects.equals(oname, pathArr[0])) {
                  TableDataPath path2 = (TableDataPath) path.clone(new String[]{ event.getText() });
                  nfinfo.setFormat(path2, finfo.getFormat(path));
               }
               else {
                  nfinfo.setFormat(path, finfo.getFormat(path));
               }
            }

            table.setFormatInfo(nfinfo);
            box.resetDataMap(table.getName());

            BindingModel binding = bfactory.createModel(table);
            SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
            dispatcher.sendCommand(bcommand);
            VSObjectModel model = objectModelService.createModel(table, rvs);
            RefreshVSObjectCommand command = new RefreshVSObjectCommand();
            command.setInfo(model);
            command.setWizardTemporary(table.isWizardTemporary());
         }
         else if(assembly instanceof CrosstabVSAssembly) {
            String oname = assembly.getAbsoluteName();
            boolean detail = oname.startsWith(Assembly.DETAIL);

            if(detail) {
               oname = oname.substring(Assembly.DETAIL.length());
            }

            VSTableLens lens = box.getVSTableLens(oname, detail);
            TableDataPath dataPath = lens.getTableDataPath(row, col);

            String messageTxt = event.getText();
            FormatInfo formatInfo = assembly.getFormatInfo();

            setAlias(dataPath, formatInfo, messageTxt);

            // in crosstab, summary header data path changes depending on whether there is
            // any group columns in binding. to end user they feel like the same cell.
            // since the header and group header data path won't exist on the same crosstab
            // at same time, we treat the two as the same for the purpose of renaming column
            if(dataPath.getType() == TableDataPath.GROUP_HEADER) {
               TableDataPath path2 = (TableDataPath) dataPath.clone();
               path2.setType(TableDataPath.HEADER);
               setAlias(path2, formatInfo, messageTxt);
            }
            else if(dataPath.getType() == TableDataPath.HEADER) {
               TableDataPath path2 = (TableDataPath) dataPath.clone();
               path2.setType(TableDataPath.GROUP_HEADER);
               setAlias(path2, formatInfo, messageTxt);
            }

            info.setFormatInfo(formatInfo);
         }

         int hint = assembly.setVSAssemblyInfo(info);
         ChangedAssemblyList clist = placeholderService.createList(false, dispatcher, rvs, linkUri);
         box.processChange(event.getName(), hint, clist);
         placeholderService.execute(rvs, event.getName(), linkUri, clist, dispatcher, true);
         placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
      }
      finally {
         box.unlockWrite();
      }
   }

   private void syncHighlight(TableVSAssembly table, String oname, String nname) {
      TableDataVSAssemblyInfo info = table.getTableDataVSAssemblyInfo();

      if(info == null) {
         return;
      }

      TableHighlightAttr hattr = info.getHighlightAttr();

      if(hattr == null) {
         return;
      }

      Map<TableDataPath, HighlightGroup> map = hattr.getHighlightMap();
      List<TableDataPath> list = new ArrayList<>(map.keySet());

      for(TableDataPath path : list) {
         if(path == null) {
            continue;
         }

         String[] tpaths = path.getPath();

         if(tpaths == null || tpaths.length != 1) {
            continue;
         }

         if(Objects.equals(oname, tpaths[0])) {
            TableDataPath path2 = (TableDataPath) path.clone(new String[]{ nname });
            HighlightGroup hg = hattr.getHighlight(path);
            map.remove(path);
            hattr.setHighlight(path2, hg);
         }
      }
   }

   // set alias for data path
   private void setAlias(TableDataPath dataPath, FormatInfo formatInfo, String messageTxt) {
      VSCompositeFormat format = formatInfo.getFormat(dataPath);

      if(format == null) {
         format = new VSCompositeFormat();
      }
      else {
         format = format.clone();
      }

      VSFormat ufmt = format.getUserDefinedFormat();

      if(ufmt == null) {
         ufmt = new VSFormat();
         format.setUserDefinedFormat(ufmt);
      }

      ufmt.setFormatValue(VSFormat.MESSAGE_FORMAT);
      ufmt.setFormatExtentValue(messageTxt);

      formatInfo.setFormat(dataPath, format);
   }

   /**
    * Change text of embedded table cell.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeCellText")
   public void changeCellText(@Payload ChangeVSTableCellsTextEvent event,
                              Principal principal,
                              @LinkUri String linkUri,
                              CommandDispatcher dispatcher)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      String tableName = event.getAssemblyName();
      VSAssembly assembly = viewsheet.getAssembly(tableName);

      assert assembly instanceof EmbeddedTableVSAssembly;

      EmbeddedTableVSAssembly table = (EmbeddedTableVSAssembly) assembly;
      ColumnSelection columns = table.getVisibleColumns();
      int hint = 0;
      boolean exceedText = false;

      for(ChangeVSTableCellsTextEvent.TableCellTextChange change : event.changes()) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(change.getCol());
         CellRef cellRef = new CellRef(ref.getDataRef().getName(), change.getRow());
         String text = change.getText();

         if(text.length() > Util.getOrganizationMaxCellSize()) {
            text = text.substring(0, Util.getOrganizationMaxCellSize());
            exceedText = true;
         }

         hint = updateCell(table, cellRef, text) | hint;
      }

      if(exceedText) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString("common.limited.text",
            Util.getOrganizationMaxCellSize()));
         command.setType(MessageCommand.Type.WARNING);
         dispatcher.sendCommand(command);
      }

      this.placeholderService.execute(rvs, table.getAbsoluteName(), linkUri, hint, dispatcher);
      this.placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
   }

   /**
    * Remove table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/deleteColumns")
   public void deleteColumns(@Payload RemoveTableColumnsEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      String tableName = event.getName();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(tableName);

      if(table != null) {
         // Call getVisibleColumns for below setColumnSelection will miss hidden column.
         ColumnSelection columns = table.getColumnSelection();
         SortInfo sortInfo = table.getSortInfo();
         DataRef[] ref = table.getBindingRefs();
         int[] cols = event.getColumns();
         Arrays.sort(cols);

         for(int i = cols.length - 1; i >= 0; i--) {
            int index = getActualColIndex(columns, cols[i]);
            DataRef column = ref[index];
            columns.removeAttribute(index);

            if(sortInfo != null) {
               sortInfo.removeSort(column);
            }
         }

         if(table instanceof EmbeddedTableVSAssembly) {
            new EmbeddedTableVSAQuery(box, tableName, false).resetEmbeddedData();
         }
         // clear binding if no column left
         else if(columns.getAttributeCount() == 0) {
            table.setSourceInfo(null);
         }

         int hint = table.setColumnSelection(columns);
         ChangedAssemblyList clist =
            placeholderService.createList(false, dispatcher, rvs, linkUri);
         box.processChange(event.getName(), hint, clist);
         placeholderService.execute(rvs, event.getName(), linkUri, clist, dispatcher, true);
         placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);

         BindingModel binding = bfactory.createModel(table);
         SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
         dispatcher.sendCommand(bcommand);
         VSObjectModel model = objectModelService.createModel(table, rvs);
         RefreshVSObjectCommand command = new RefreshVSObjectCommand();
         command.setInfo(model);
         command.setWizardTemporary(table.isWizardTemporary());
         dispatcher.sendCommand(command);
      }
   }

   /**
    * Show and hide table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/showHideColumns")
   public void hideColumns(@Payload ShowHideCrosstabColumnsEvent event, Principal principal,
                           CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(
              this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      String tableName = event.getName();
      CrosstabVSAssembly table = (CrosstabVSAssembly) vs.getAssembly(tableName);

      if(table == null) {
         return;
      }

      CrosstabVSAssemblyInfo tableInfo = table.getCrosstabInfo();
      int[] columns = event.getColumns();

      if(event.isShowColumns()) {
         tableInfo.clearHiddenColumns();
      }
      else if(columns != null && columns.length > 0) {
         VSTableLens lens = box.getVSTableLens(tableName, false);

         for(int column : columns) {
            tableInfo.addHiddenColumn(column, lens);
         }
      }

      int hint = 0;
      ChangedAssemblyList clist = placeholderService.createList(false, dispatcher, rvs, linkUri);
      box.processChange(event.getName(), hint, clist);
      placeholderService.execute(rvs, event.getName(), linkUri, clist, dispatcher, true);
      placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);

      BindingModel binding = bfactory.createModel(table);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      VSObjectModel model = objectModelService.createModel(table, rvs);
      RefreshVSObjectCommand command = new RefreshVSObjectCommand();
      command.setInfo(model);
      command.setWizardTemporary(table.isWizardTemporary());
      dispatcher.sendCommand(command);
   }

   /**
    * get actual index (on the displayed table) according to display index.
    * it could be different from column list index because column can been hidden.
    * @param columns all columnSelection
    * @param displayIndex display column index.
    */
   public static int getActualColIndex(ColumnSelection columns, int displayIndex) {
      if(columns != null) {
         // Starting from -1 is to prevent the actualIndex from being fixed to 1 when
         // displayIndex is 0.
         int actualIndex = -1, visibleIndex = -1;

         do {
            DataRef dataRef = columns.getAttribute(++actualIndex);

            // Just only accumulate the index for the visible column.
            if(dataRef instanceof ColumnRef && ((ColumnRef) dataRef).isVisible()) {
               visibleIndex++;
            }
         } while(visibleIndex < displayIndex);

         return actualIndex;
      }

      return displayIndex;
   }

   /**
    * Get the column index in ColumnSelection, taking into account of the hidden columns.
    * @param col the index in the displayed table.
    */
   public static int getBindingColIndex(ColumnSelection columns, int col) {
      int cidx = 0;

      for(; cidx < columns.getAttributeCount() && col >= 0; cidx++) {
         ColumnRef columnRef = (ColumnRef) columns.getAttribute(cidx);

         if(columnRef.isVisible()) {
            col--;
         }
      }

      return cidx - 1;
   }

   /**
    * Offset the column index according to the invisible columns.
    */
   public static int getOffsetColumnIndex(ColumnSelection columns, int dropIndex) {
      int actualIndex = dropIndex;

      for(int i = 0; i <= actualIndex && i < columns.getAttributeCount(); i++) {
         final DataRef ref = columns.getAttribute(i);

         if(ref instanceof ColumnRef && !((ColumnRef) ref).isVisible()) {
            actualIndex++;
         }
      }

      return actualIndex;
   }

   /**
    * Reset table layout.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/resetTableLayout")
   public void resetTableLayout(@Payload RemoveTableColumnsEvent event, Principal principal,
                                CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      String tableName = event.getName();
      TableDataVSAssembly table = (TableDataVSAssembly) vs.getAssembly(tableName);
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getInfo();

      if(info instanceof CalcTableVSAssemblyInfo) {
         TableLayout tableLayout = ((CalcTableVSAssemblyInfo) info).getTableLayout();
         tableLayout.resetColumnWidths();
      }

      info.resetRowHeights();
      info.resetColumnWidths();
      info.resetSizeInfo();
      info.resetRColumnWidths();
      info.setExplicitTableWidthValue(false);
      info.setUserHeaderRowHeight(false);
      info.setUserDataRowHeight(false);
      rvs.getViewsheetSandbox().resetDataMap(table.getAbsoluteName());
      BaseTableController.loadTableData(rvs, table.getAbsoluteName(), 0, 0, 100, "",
                                        dispatcher);
      placeholderService.refreshVSAssembly(rvs, table, dispatcher);
   }

   /**
    * Resize table cell.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeCellSize")
   public void resizeTableCell(@Payload ResizeTableCellEvent event, Principal principal,
                               @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      changeColumnWidth(event.colEvent(), principal, linkUri, dispatcher, false);
      changeRowHeight(event.rowEvent(), principal, dispatcher, false);
   }

   /**
    * Resize table columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeColumnWidth")
   public void changeColumnWidth(@Payload ResizeTableColumnEvent event, Principal principal,
                                 @LinkUri String linkUri,
                                 CommandDispatcher dispatcher) throws Exception
   {
      changeColumnWidth(event, principal, linkUri, dispatcher, true);
   }

   private void changeColumnWidth(ResizeTableColumnEvent event, Principal principal, String linkUri,
                                 CommandDispatcher dispatcher, boolean removePadding) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      try {
         box.lockRead();
         Viewsheet vs = rvs.getViewsheet();
         String tableName = event.getName();
         TableDataVSAssembly table = (TableDataVSAssembly) vs.getAssembly(tableName);

         if(table == null) {
            return;
         }

         final String name = table.getAbsoluteName();
         final VSTableLens lens = box.getVSTableLens(name, false);

         if(lens == null) {
            return;
         }

         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getInfo();
         boolean isMetadata = vs.getViewsheetInfo().isMetadata();
         Viewsheet topvs = VSUtil.getTopViewsheet(vs);
         float rscaleFont = topvs.getRScaleFont();
         final int startCol = event.getStartCol();
         final int endCol = event.getEndCol();
         final int row = event.getRow();
         final double[] newColWidths = event.getWidths();
         final TableDataPath dataPath = lens.getTableDataPath(row, startCol);
         final String[] path = dataPath.getPath();
         final boolean runtime = rvs.isRuntime();
         final boolean binding = rvs.getOriginalID() != null;
         final Predicate<Integer> hiddenColumn = (col) ->
            info instanceof CrosstabVSAssemblyInfo &&
               ((CrosstabVSAssemblyInfo) info).isColumnHidden(col, lens);

         if(path != null && path.length > 0) {
            int baseCol = TableTool.getCol(lens, path[0]);
            final int colCount = lens.getColCount();
            final int colSpan = endCol - startCol;
            final double[] colWidths = BaseTableController.getColWidths(table, lens);

            if(baseCol < 0) {
               baseCol = startCol;
            }

            for(int i = baseCol; i < colCount; i += colSpan) {
               final TableDataPath nextDataPath = lens.getTableDataPath(row, i);

               if(dataPath.equals(nextDataPath)) {
                  for(int j = 0; j < colSpan; j++) {
                     int col = i + j;

                     if(hiddenColumn.test(col)) {
                        continue;
                     }

                     double width = newColWidths[j] / rscaleFont;

                     if(removePadding) {
                        width = Math.max(0, width - lens.getCSSColumnPadding(col));
                     }

                     if(!Double.isNaN(width)) {
                        // corresponds to changing the pre-expansion width
                        if(runtime) {
                           if(isMetadata && info instanceof CalcTableVSAssemblyInfo) {
                              ((CalcTableVSAssemblyInfo) info).getTableLayout()
                                 .setColWidth(i, (int) width);
                           }
                        /* setting runtime colwidth causing col width to be lost in bookmark
                        else if(info instanceof TableVSAssemblyInfo) {
                           info.setColumnWidth(i + j, width);
                        }
                        */
                           else {
                              info.setColumnWidthValue2(col, width, lens);
                           }
                        }
                        // corresponds to changing the post-expansion width
                        else {
                           info.setColumnWidthValue2(col, width, lens);
                           // clear out runtime value (51040)
                           info.setColumnWidthValue(col, Double.NaN);
                        }
                     }
                  }
               }
            }

            // when resizing column, the last column is default to fill the table. if it's set
            // and less than the table width, it appears to have a size that's larger than
            // the displayed width. when shrink-to-fit is turned on, it may have a very small
            // width compared to it's design time width.
            if(!runtime && !binding &&
               !Double.isNaN(info.getColumnWidthValue(lens.getColCount() - 1)))
            {
               Dimension size = vs.getPixelSize(info);
               double total = Arrays.stream(colWidths).sum();
               boolean isHidden = false;

               if(hiddenColumn.test(colWidths.length - 1)) {
                  isHidden = true;
               }

               if(total < size.width && !isHidden) {
                  info.setColumnWidthValue(lens.getColCount() - 1,
                                           colWidths[colWidths.length - 1] + size.width - total);
               }
            }

            info.setExplicitTableWidthValue(true);
            int hint = table.setVSAssemblyInfo(info);
            placeholderService.refreshVSAssembly(rvs, table, dispatcher);
            placeholderService.execute(rvs, tableName, linkUri, hint, dispatcher);
            placeholderService.loadTableLens(rvs, tableName, linkUri, dispatcher);
         }
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Resize the row heights.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/changeRowHeight")
   public void changeRowHeight(@Payload ResizeTableRowEvent event, Principal principal,
                               CommandDispatcher dispatcher)
      throws Exception
   {
      changeRowHeight(event, principal, dispatcher, true);
   }

   private void changeRowHeight(ResizeTableRowEvent event, Principal principal,
                               CommandDispatcher dispatcher, boolean removePadding)
      throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      final Viewsheet vs = rvs.getViewsheet();
      final String tableName = event.getAssemblyName();
      final TableDataVSAssembly table = (TableDataVSAssembly) vs.getAssembly(tableName);
      final TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) table.getInfo();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      final VSTableLens lens = box.getVSTableLens(table.getAbsoluteName(), false);

      if(event.header()) {
         final int headerRowCount;

         if(info instanceof CalcTableVSAssemblyInfo) {
            headerRowCount = ((CalcTableVSAssemblyInfo) info).getHeaderRowCount();
         }
         else {
            headerRowCount = lens.getHeaderRowCount();
         }

         for(int i = 0; i < event.rowSpan() && i + event.row() < headerRowCount; i++) {
            int height = event.rowHeight();

            if(removePadding) {
               height = Math.max(0, height - lens.getCSSRowPadding(i + event.row()));
            }

            info.setHeaderRowHeight(i + event.row(), height);
            info.setUserHeaderRowHeight(true);
         }
      }
      else {
         int height = event.rowHeight();

         if(removePadding) {
            height = Math.max(0, height - lens.getCSSRowPadding(lens.getHeaderRowCount()));
         }

         info.setDataRowHeight(height);
         info.setUserDataRowHeight(true);
      }

      info.setExplicitTableWidthValue(true);
      placeholderService.refreshVSAssembly(rvs, table, dispatcher);

      if(lens != null) {
         lens.setRowHeights(null); // recalculate row heights
      }

      BaseTableController.loadTableData(rvs, table.getAbsoluteName(), box.getMode(),
                                        event.start(), event.rowCount(), "", dispatcher);
   }

   /**
    * Change table to a freehand table. Mimic of ConvertToCalcTableEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/table/convertToFreehand")
   public void convertToFreehandTable(@Payload ConvertToFreehandTableEvent event,
                                      Principal principal,
                                      @LinkUri String linkUri,
                                      CommandDispatcher dispatcher) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      int hint = 0;

      if(box == null || !(assembly instanceof TableDataVSAssembly)) {
         return;
      }

      if(assembly instanceof CrosstabVSAssembly && !event.isConfirmed()) {
         VSEventUtil.fixAggregateInfo(
            (CrosstabVSAssembly) assembly, rvs, assetRepository, principal);
      }

      // clear the crosstab drill before convert to calc, make crosstab data is same with calc.
      if(assembly instanceof CrosstabVSAssembly) {
         clearCrosstabDrill((CrosstabVSAssembly) assembly, rvs, event.getName(), linkUri,
            dispatcher);
         clearDateComparison((CrosstabVSAssembly) assembly, rvs, linkUri, dispatcher);
      }

      TableDataVSAssembly tableAssembly = (TableDataVSAssembly) assembly;
      SourceInfo sourceInfo = tableAssembly.getSourceInfo();
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Point pos = info.getLayoutPosition() != null ?
         info.getLayoutPosition() : viewsheet.getPixelPosition(info);
      String oname = info.getAbsoluteName();
      VSAssembly freehandAssembly =
         VSEventUtil.createVSAssembly(rvs, Viewsheet.FORMULA_TABLE_ASSET);
      assert freehandAssembly != null;
      CalcTableVSAssemblyInfo freehandInfo = (CalcTableVSAssemblyInfo)
         freehandAssembly.getVSAssemblyInfo();
      freehandInfo.setPixelOffset(pos);
      final VSTableLens lens = box.getVSTableLens(assembly.getAbsoluteName(), false);
      boolean mergeSpan = false;
      List<String> clearCalcs = null;

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         boolean reset = clearDataGroup(crosstabInfo.getRowHeaders());
         reset = reset || clearDataGroup(crosstabInfo.getColHeaders());
         reset = reset || clearDataGroup(crosstabInfo.getRuntimeRowHeaders());
         reset = reset || clearDataGroup(crosstabInfo.getRuntimeColHeaders());

         if(reset) {
            box.resetDataMap(assembly.getAbsoluteName());
         }

         freehandInfo.setFillBlankWithZeroValue(crosstabInfo.isFillBlankWithZero());
         freehandInfo.setSortOthersLastValue(crosstabInfo.isSortOthersLast());
         mergeSpan = crosstabInfo.getMergeSpanValue();
         crosstabInfo.setMergeSpan(true);
      }

      // 1. get metadata
      TableLens source = (TableLens) box.getData(oname);

      if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         // clear calculator(exclude Percent Calc), because freehand does not support calculator.
         clearCalcs = clearCalculators(crosstabInfo.getAggregates());
      }

      boolean vsSrc = sourceInfo != null && sourceInfo.getType() == SourceInfo.VS_ASSEMBLY;
      TableLens metadata = getMetadata(box, oname, vsSrc);

      // 2. generate crosstab/simple table layout
      TableLayout olayout = VSLayoutTool.generateLayout(tableAssembly, metadata, viewsheet);

      // 4. convert crosstab layout/table layout to calc layout
      TableLayout nlayout = VSTableConverter.changeTableMode(
         tableAssembly, olayout, TableLayout.CALC);
      fixLayoutAlias(nlayout, tableAssembly);
      fixPeridField(nlayout, VSUtil.isVSAssemblyBinding(assembly));

      // 5. set the table layout to calc table and copy the info from
      // crosstab/table
      ((CalcTableVSAssembly) freehandAssembly).setTableLayout(nlayout);
      hint |= freehandInfo.copyInfo(info, false);

      // useless for calc, because calc lens is not a filter
      // based on the base script table
      freehandInfo.setHighlightAttr(new TableHighlightAttr());
      freehandInfo.setHyperlinkAttr(new TableHyperlinkAttr());

      if(source != null) {
//         int hcols = source.getHeaderColCount();
//         int hcolWidth = 0;
//
//         for(int i = 0; i < hcols; i++) {
//            int colw = nlayout.getColWidth(i);
//
//            if(colw < 0) {
//               colw = AssetUtil.defw;
//            }
//
//            hcolWidth += colw;
//         }

         // avoid header columns too wide
//         if(hcolWidth < freehandInfo.getPixelSize().width / 2) {
//            freehandInfo.setHeaderColCount(hcols);
//         }

         freehandInfo.setHeaderColCount(source.getHeaderColCount());
         freehandInfo.setHeaderRowCount(source.getHeaderRowCount());
      }

      // 6. sync format
      String freehandName = freehandAssembly.getName();
      box.resetDataMap(freehandName);
      TableLens target = (TableLens) box.getData(freehandName);
      VSLayoutTool.syncCellFormat((CalcTableVSAssembly) freehandAssembly,
                                  source, target, olayout.isCrosstab(), clearCalcs, vsSrc);

      //7. sync columnWidth
      if(source != null) {
         final TableDataVSAssemblyInfo tableInfo = tableAssembly.getTableDataVSAssemblyInfo();
         final boolean hasHiddenColumns = tableInfo instanceof CrosstabVSAssemblyInfo &&
            ((CrosstabVSAssemblyInfo) tableInfo).hasHiddenColumn();
         freehandInfo.setExplicitTableWidthValue(hasHiddenColumns);

         for(int i = 0; i < source.getColCount(); i++) {
            double width = tableInfo.getColumnWidth2(i, source);
            freehandInfo.setColumnWidthValue2(i, width, target);
         }

         for(int i = 0; i < source.getColCount(); i++) {
            if(hasHiddenColumns && ((CrosstabVSAssemblyInfo) tableInfo).isColumnHidden(i, lens)) {
               freehandInfo.setColumnWidthValue(i, 0);
            }
         }
      }

      try {
         // @patch by davyc, when convert table to freehand, we create a new
         // freehand table, and remove old table, then rename the new table
         // name to old table name, the remove logic will cause problem for
         // scripting, see bug1410411580177
         // now this fix is just a patch fixing in 12.0 for RC
         ViewsheetScope.IGNORE_EXCEPTION.set(Boolean.TRUE);
         // @by davyc, convert should be later when calc table all initialized
         // otherwise its status is wrong(like binding source is wrong)
         // fix bug1398075422765
         // 3. use calc table assembly instead or crosstab/table
         convert(rvs, tableAssembly, oname, dispatcher);
         fixMergeSpan(nlayout, mergeSpan);

         // 7. keep the assembly name
         renameAssembly(freehandName, oname, rvs);
      }
      finally {
         ViewsheetScope.IGNORE_EXCEPTION.set(null);
      }

      // 8. execute the viewsheet
      this.placeholderService.execute(rvs, oname, linkUri, hint, dispatcher);
      // don't change table size after conversion
      //this.placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);

      if(freehandInfo instanceof BaseAnnotationVSAssemblyInfo) {
         LOG.warn("The table type is changed, its annotation will be lost");
      }

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);

      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
   }

   private void clearCrosstabDrill(CrosstabVSAssembly crosstabVSAssembly,
                                   RuntimeViewsheet rvs, String assemblyName, String linkUri,
                                   CommandDispatcher dispatcher)
      throws Exception
   {
      if(crosstabVSAssembly == null || crosstabVSAssembly.getCrosstabTree() == null) {
         return;
      }

      CrosstabTree crosstabTree = crosstabVSAssembly.getCrosstabTree();

      if(crosstabTree != null && crosstabTree.getExpandedPaths() != null &&
         crosstabTree.getExpandedPaths().size() > 0)
      {
         crosstabTree.clearDrills();
         crosstabDrillHandler.processChange(rvs, assemblyName, null,
            dispatcher, linkUri, true);
      }
   }

   private void clearDateComparison(CrosstabVSAssembly assembly, RuntimeViewsheet rvs,
                                    String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      if(assembly == null) {
         return;
      }

      CrosstabVSAssemblyInfo info = assembly.getCrosstabInfo();
      info.resetRuntimeDateComparisonInfo();
      info.setDateComparisonInfo(null);

      this.vsAssemblyInfoHandler.apply(rvs, info, viewsheetService, false, false,
         false, false, dispatcher, null, null, linkUri, null);
   }

   private void fixPeridField(TableLayout layout, boolean assemblyBinding) {
      int rcount = layout.getRowCount();
      int ccount = layout.getColCount();

      for(int i = 0; i < rcount; i++) {
         for(int j = 0; j < ccount; j++) {
            CellBinding binding = layout.getCellBinding(i, j);

            if(!(binding instanceof TableCellBinding)) {
               continue;
            }

            if(binding.getType() == TableCellBinding.BIND_COLUMN) {
               String value = binding.getValue();

               if(value.indexOf("_Period") != -1 && !assemblyBinding) {
                  binding.setType(TableCellBinding.BIND_TEXT);
               }
            }
         }
      }
   }

   private boolean clearDataGroup(DataRef[] dims) {
      if(dims == null || dims.length == 0) {
         return false;
      }
      boolean cleared = false;

      for(int i = 0; i < dims.length; i++) {
         if(!(dims[i] instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef dim = (VSDimensionRef) dims[i];

         if(dim.getNamedGroupInfo() != null &&
            Tool.equals(dim.getGroupType(), NamedRangeRef.DATA_GROUP + ""))
         {
            dim.setGroupType(null);
            dim.setNamedGroupInfo(null);
            cleared = true;
         }
      }

      return cleared;
   }

   private List<String> clearCalculators(DataRef[] aggrs) {
      List<String> clearCalcs = new ArrayList<>();

      if(aggrs == null || aggrs.length == 0) {
         return clearCalcs;
      }

      Arrays.stream(aggrs)
         .filter((aggr) -> !(((VSAggregateRef) aggr).getCalculator() instanceof PercentCalc))
         .forEach(aggr -> {
            VSAggregateRef aggRef = (VSAggregateRef) aggr;

            if(aggRef.getCalculator() != null) {
               clearCalcs.add(aggRef.getFullName());
               aggRef.setCalculator(null);
            }
         });

      return clearCalcs;
   }

   /**
    * Update the cell text.
    */
   private int updateCell(EmbeddedTableVSAssembly em, CellRef cref, String text) {
      Map<CellRef,Object> dmap = em.getStateDataMap();
      ColumnSelection columns = em.getColumnSelection();
      dmap = (Map) ((HashMap) dmap).clone();
      Object oldData = dmap.get(cref);
      cref.setRow(cref.getRow() + 1);
      ColumnRef column = null;

      // only attribute is available, so here we only check attribute
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) columns.getAttribute(i);

         if(tcolumn.getAttribute().equals(cref.getCol())) {
            column = tcolumn;
            break;
         }
      }

      if(text != null && text.length() == 0) {
         text = null;
      }

      text = text == null || text.length() == 0 ? null : text;
      String dtype = column != null ? column.getDataType() : XSchema.STRING;
      Object obj = Tool.getData(dtype, text);

      // use text when parse failure.
      if(obj == null && !StringUtils.isEmpty(text)) {
         obj = text;
      }

      if("".equals(text) && !XSchema.STRING.equals(dtype) &&
         !XSchema.CHAR.equals(dtype))
      {
         obj = null;
      }

      dmap.put(cref, obj);
      int hint = em.setStateDataMap(dmap);

      // fix bug1253519126172(none-boolean
      // data can be input to boolean column)
      if(hint == VSAssembly.NONE_CHANGED) {
         // if the input data is not equal to old data, we should still
         // refresh the table data even if the caculated-new-data is equal
         // to old data
         if(!Tool.equals(oldData, text)) {
            hint |= VSAssembly.OUTPUT_DATA_CHANGED;
         }
      }

      return hint;
   }

   private void fixLayoutAlias(TableLayout layout, TableDataVSAssembly assembly) {
      if(!(assembly instanceof TableVSAssembly)) {
         return;
      }

      Map<String, String> amap = new HashMap<>();
      ColumnSelection sel = ((TableVSAssembly) assembly).getColumnSelection();

      if(sel == null) {
         return;
      }

      for(int i = 0; i < sel.getAttributeCount(); i++) {
         DataRef ref = sel.getAttribute(i);

         if(ref instanceof ColumnRef) {
            String name = ((ColumnRef) ref).getAttribute();
            String alias = ((ColumnRef) ref).getAlias();

            if(alias != null && !name.equals(alias)) {
               amap.put(alias, name);
            }
         }
      }

      if(amap.size() <= 0) {
         return;
      }

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               CellBinding bind = region.getCellBinding(r, c);

               if(bind != null && bind.getType() == CellBinding.BIND_COLUMN) {
                  String cname = bind.getValue();
                  String oname = amap.get(cname);

                  if(oname != null) {
                     bind.setValue(oname);
                  }
               }
            }
         }
      }
   }

   private void fixMergeSpan(TableLayout layout, boolean mergeSpan) {
      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         if(region.getPath().getType() == TableDataPath.DETAIL) {
            for(int r = 0; r < region.getRowCount(); r++) {
               for(int c = 0; c < region.getColCount(); c++) {
                  CellBinding binding = region.getCellBinding(r, c);

                  if(binding != null && binding.getBType() == CellBinding.GROUP &&
                     binding instanceof TableCellBinding)
                  {
                     ((TableCellBinding) binding).setMergeCells(mergeSpan);
                  }
               }
            }
         }
      }
   }

   /**
    * Rename Assembly
    */
   private boolean renameAssembly(String oname, String nname, RuntimeViewsheet rvs)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs.renameAssembly(oname, nname)) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         ViewsheetScope scope = box.getScope();
         VariableScriptable vscriptable = scope.getVariableScriptable();
         VariableTable vtable = (VariableTable) vscriptable.unwrap();

         if(vtable != null && vtable.contains(oname)) {
            Object value = vtable.get(oname);
            vtable.remove(oname);
            vtable.put(nname, value);
         }

         return true;
      }

      return false;
   }

   /**
    * Keep the new assembly position in containers.
    */
   public static int convert(RuntimeViewsheet rvs, VSAssembly assembly, String oname,
                             CommandDispatcher dispatcher)
   {
      int hint = 0;
      VSAssembly container = assembly.getContainer();
      int idx = 0;

      if(container instanceof AbstractContainerVSAssembly) {
         String[] assemblies = ((AbstractContainerVSAssembly) container).getAssemblies();

         for(int i = 0; i < assemblies.length; i++) {
            if(Tool.equals(oname, assemblies[i])) {
               idx = i;
               break;
            }
         }
      }

      VSEventUtil.removeVSObject(rvs, oname, dispatcher);

      if(container instanceof AbstractContainerVSAssembly) {
         AbstractContainerVSAssembly containerVSAssembly =
            (AbstractContainerVSAssembly) container;
         String[] assemblies = containerVSAssembly.getAssemblies();
         List<String> list = new ArrayList<>(Arrays.asList(assemblies));
         list.add(idx, oname);
         containerVSAssembly.setAssemblies(list.toArray(new String[0]));
      }

      if(container instanceof TabVSAssembly) {
         ((TabVSAssembly) container).setSelectedValue(oname);
      }

      return hint;
   }

   /**
    * Get meta table data.
    */
   private static TableLens getMetadata(ViewsheetSandbox box, String name, boolean vsSrc) {
      Viewsheet vs = box.getViewsheet();
      boolean isMetadata = vs.getViewsheetInfo().isMetadata();
      TableLens lens = null;

      try {
         if(!vsSrc) {
            vs.getViewsheetInfo().setMetadata(true);
         }

         box.getVariableTable().put("calc_metadata", "true");
         box.resetDataMap(name);
         lens = (TableLens) box.getData(name);
      }
      catch(Exception ex) {
         LOG.info("Failed to get metadata: " + name, ex);
      }
      finally {
         vs.getViewsheetInfo().setMetadata(isMetadata);
         box.getVariableTable().remove("calc_metadata");
         box.resetDataMap(name);
      }

      return lens;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSObjectModelFactoryService objectModelService;
   private final VSBindingService bfactory;
   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler vsAssemblyInfoHandler;
   protected final CrosstabDrillHandler crosstabDrillHandler;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComposerVSTableController.class);
}
