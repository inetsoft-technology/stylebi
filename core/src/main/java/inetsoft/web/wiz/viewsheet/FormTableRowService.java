/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.ColumnOption;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * Form Table row-level runtime edits: insert row, delete row(s), set a cell's value, and apply
 * (write back through the Form binding to the worksheet embedded table, plus save) -- the same
 * four actions the Preview toolbar's Insert Row / Delete Row / cell edit / Apply drive through
 * {@code VSFormTableController}'s STOMP endpoints, called here directly against
 * {@link VSFormTableService}, the same technique {@link InputValueService} uses for
 * {@code VSInputService}.
 *
 * <p><b>Insert/delete/set-cell only mutate the in-memory {@code FormTableLens}.</b> Nothing
 * reaches the worksheet's embedded table until {@link #apply}, which is StyleBI's own
 * {@code applyChanges}/{@code writeBackFormData} -- see {@link VSFormTableService} for the full
 * mechanism this wraps.
 *
 * <p><b>{@code isInsert()}/{@code isDel()}/{@code isForm()}/{@code isWriteBack()} are enforced
 * here, not by {@code VSFormTableService} itself.</b> {@code addRow}/{@code deleteRows} run
 * regardless of whether the Table property dialog's Insert/Delete checkboxes are on -- those
 * flags only gate the Preview toolbar's own button visibility. A STOMP caller bypassing the
 * toolbar (this bridge, or a hand-crafted frame) would otherwise do exactly what the hidden
 * button would have done. This class refuses instead, named, before ever calling into the native
 * service -- the same defensive posture {@code insert_row}/{@code delete_row} already take for a
 * worksheet's {@code EMBEDDED_SNAPSHOT} table.
 *
 * <p><b>Row indices this class accepts are 0-based DATA rows, not {@link FormTableLens}'s own
 * absolute (header-inclusive) rows.</b> {@code FormTableLens.insertRow}/{@code deleteRow}/
 * {@code setObject} all index from row 0 = the header row -- {@code insertRow(0)} explicitly
 * refuses with "Insert header cell is not allowed!", and {@code deleteRow(0)} has no such guard
 * at all, so an unadjusted index would either throw confusingly or silently delete the header.
 * {@link #headerRowOffset} resolves the live {@link FormTableLens} and adds its own
 * {@code getHeaderRowCount()} (not hardcoded to 1) to the caller's index before any native event
 * is built, so {@code index}/{@code row}/{@code rows} genuinely mean "0-based data row" the way
 * this class's own javadoc on each method already promised. The row snapshot every method returns
 * uses the same space: {@code rows[k]} is data row {@code k}, and the header row is returned
 * separately as {@code columns} (see {@link #populateSnapshot}).
 */
@Service
public class FormTableRowService {
   public FormTableRowService(ViewsheetSessionService sessions, VSFormTableService formTableService) {
      this.sessions = sessions;
      this.formTableService = formTableService;
   }

   /**
    * Inserts a blank row. {@code append} false inserts a new row AT {@code index}, shifting
    * {@code index} and everything after it down one; {@code append} true inserts a new row AFTER
    * {@code index} instead -- {@code FormTableLens.insertRow}/{@code appendRow}'s own distinction.
    */
   public Map<String, Object> insertRow(String sessionToken, Principal user, String assemblyName,
                                        int index, boolean append, String linkUri) throws Exception
   {
      Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireInsertable(rvs, assemblyName);
         requireInsertIndexInRange(rvs, assemblyName, index, append);

         InsertTableRowEvent event = InsertTableRowEvent.builder()
            .assemblyName(assemblyName)
            .insert(!append)
            .row(index + headerRowOffset(rvs, assemblyName))
            .start(0)
            .build();
         FormTableLens before = resolveLens(rvs, assemblyName);
         int rowsBefore = before == null ? -1 : dataRowCount(before);
         formTableService.addRow(runtimeId, event, linkUri, dispatcher, user);
         FormTableLens after = resolveLens(rvs, assemblyName);

         if(rowsBefore >= 0 && after != null && dataRowCount(after) <= rowsBefore) {
            throw new IllegalArgumentException(noRowInsertedMessage(
               assemblyName, rowsBefore, after.getHeaderRowCount(), dispatcher.getWarnings()));
         }

         populateSnapshot(result, dispatcher, assemblyName);
      });

      result.put("assembly", assemblyName);
      putWarnings(result, warnings);
      return result;
   }

   /**
    * {@code VSFormTableService.addRow} refuses an insert/append at the organization's
    * {@code max.row.count} by dispatching a WARNING and returning early, so the only way to
    * notice is that the row count did not grow. The native limit compares against the lens's
    * header-inclusive row count, hence the data-row capacity is {@code max - headerRows}.
    */
   private static String noRowInsertedMessage(String assemblyName, int dataRows, int headerRows,
                                              List<String> warnings)
   {
      int max = Util.getOrganizationMaxRow();
      StringBuilder message = new StringBuilder("No row was inserted into '")
         .append(assemblyName).append("'");

      if(max > 0) {
         message.append(": the organization row limit max.row.count = ").append(max)
            .append(" (it counts the ").append(headerRows).append(" header row(s)) allows at most ")
            .append(Math.max(0, max - headerRows)).append(" data rows and the table already has ")
            .append(dataRows).append('.');
      }
      else {
         message.append('.');
      }

      if(warnings != null && !warnings.isEmpty()) {
         message.append(" Server message: ").append(String.join(" ", warnings));
      }

      return message.toString();
   }

   /** Deletes one or more rows in a single call -- {@code VSFormTableService} itself sorts and
    *  applies them highest-index-first so earlier deletions do not shift later indices. */
   public Map<String, Object> deleteRows(String sessionToken, Principal user, String assemblyName,
                                         List<Integer> rows, String linkUri) throws Exception
   {
      if(rows == null || rows.isEmpty()) {
         throw new IllegalArgumentException(
            "'rows' is required and must name at least one 0-based data row index to delete.");
      }

      Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireDeletable(rvs, assemblyName);
         requireDeleteRowsInRange(rvs, assemblyName, rows);

         int offset = headerRowOffset(rvs, assemblyName);
         List<Integer> nativeRows = new ArrayList<>(rows.size());

         for(int r : rows) {
            nativeRows.add(r + offset);
         }

         DeleteTableRowsEvent event = DeleteTableRowsEvent.builder()
            .assemblyName(assemblyName)
            .addAllRows(nativeRows)
            .start(0)
            .build();
         formTableService.deleteRows(runtimeId, event, linkUri, dispatcher, user);
         populateSnapshot(result, dispatcher, assemblyName);
      });

      result.put("assembly", assemblyName);
      putWarnings(result, warnings);
      return result;
   }

   /**
    * Sets one cell's value on a (typically freshly inserted) row -- needed because a fresh row's
    * cells start blank, and {@link #apply} validates each column's {@code ColumnOption} against
    * whatever is there when it writes back.
    */
   public Map<String, Object> setCell(String sessionToken, Principal user, String assemblyName,
                                      int row, int col, String value, String linkUri) throws Exception
   {
      Map<String, Object> result = new LinkedHashMap<>();

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireForm(rvs, assemblyName);
         requireCellInRange(rvs, assemblyName, row, col);

         int nativeRow = row + headerRowOffset(rvs, assemblyName);
         requireEditableCell(rvs, assemblyName, nativeRow, row, col);

         ChangeFormTableCellInputEvent event = ChangeFormTableCellInputEvent.builder()
            .assemblyName(assemblyName)
            .row(nativeRow)
            .col(col)
            .data(value)
            .start(0)
            .build();
         formTableService.changeFormInput(runtimeId, event, linkUri, dispatcher, user);
         populateSnapshot(result, dispatcher, assemblyName);
      });

      result.put("assembly", assemblyName);
      result.put("row", row);
      result.put("col", col);
      putWarnings(result, warnings);
      return result;
   }

   /**
    * The actual write-back: commits every pending insert/delete/cell-edit through the Form
    * binding into the worksheet's embedded table, then saves the asset. Everything before this
    * call is in-memory only.
    */
   public Map<String, Object> apply(String sessionToken, Principal user, String assemblyName,
                                    String linkUri) throws Exception
   {
      Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         requireWriteBack(rvs, assemblyName);

         ApplyFormChangesEvent event = ApplyFormChangesEvent.builder()
            .assemblyName(assemblyName)
            .build();

         try {
            formTableService.applyChanges(runtimeId, event, linkUri, dispatcher, user);
         }
         catch(RuntimeException ex) {
            // writeBackFormData (ViewsheetSandbox) throws a bare RuntimeException with a
            // catalog-localized message (e.g. "write.back.failed.wrongSource",
            // "write.back.failed.noData") -- rethrown here as an IllegalArgumentException so
            // WizControllerErrorHandler surfaces it as a named 400 instead of losing the message
            // to the generic 500 a RuntimeException would otherwise fall through to.
            throw new IllegalArgumentException(
               "Apply failed for '" + assemblyName + "': " + ex.getMessage(), ex);
         }

         populateSnapshot(result, dispatcher, assemblyName);
      });

      result.put("assembly", assemblyName);
      result.put("applied", true);
      return result;
   }

   private static TableVSAssemblyInfo requireForm(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      if(!(assembly instanceof TableVSAssembly)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a Table assembly.");
      }

      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(!info.isForm()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is not a Form table -- enable Table > Form Options > Form " +
            "in the Composer before row edits are possible.");
      }

      return info;
   }

   private static void requireInsertable(RuntimeViewsheet rvs, String assemblyName) {
      TableVSAssemblyInfo info = requireForm(rvs, assemblyName);

      if(!info.isInsert()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' does not have Insert enabled in Table > Form Options. " +
            "The underlying service does not check this itself, so it is refused here instead " +
            "of silently doing what the Preview toolbar's hidden Insert button would have.");
      }
   }

   private static void requireDeletable(RuntimeViewsheet rvs, String assemblyName) {
      TableVSAssemblyInfo info = requireForm(rvs, assemblyName);

      if(!info.isDel()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' does not have Delete enabled in Table > Form Options. " +
            "The underlying service does not check this itself, so it is refused here instead " +
            "of silently doing what the Preview toolbar's hidden Delete button would have.");
      }
   }

   private static void requireWriteBack(RuntimeViewsheet rvs, String assemblyName) {
      TableVSAssemblyInfo info = requireForm(rvs, assemblyName);

      if(!info.isWriteBack()) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' does not have write-back enabled in Table > Form Options. " +
            "writeBackFormData silently does nothing for this case rather than throwing, so it " +
            "is refused here instead with a named reason.");
      }
   }

   /**
    * The offset between this class's caller-facing "0-based data row" and the absolute,
    * header-inclusive row index every {@link FormTableLens} method (and {@code
    * VSFormTableService}/the native STOMP controller it wraps) actually expects. Equal to the
    * resolved lens's own {@code getHeaderRowCount()} (ordinarily 1, never hardcoded to that in
    * case a future table type has more than one header row).
    *
    * <p>Returns 0 -- i.e. no translation -- when the lens cannot be resolved at all (sandbox not
    * runtime-mode, disposed, ...): the native call is going to fail downstream regardless of
    * what offset is applied here, since {@code VSFormTableService} needs the identical lens to
    * do anything.
    */
   private static int headerRowOffset(RuntimeViewsheet rvs, String assemblyName) throws Exception {
      FormTableLens lens = resolveLens(rvs, assemblyName);
      return lens == null ? 0 : lens.getHeaderRowCount();
   }

   private static FormTableLens resolveLens(RuntimeViewsheet rvs, String assemblyName)
      throws Exception
   {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();
      return box.isEmpty() ? null : box.get().getFormTableLens(assemblyName);
   }

   private static int dataRowCount(FormTableLens lens) {
      return lens.getRowCount() - lens.getHeaderRowCount();
   }

   /**
    * Insert accepts 0..dataRows (insert AT dataRows adds at the end); append accepts
    * 0..dataRows-1 (adds AFTER that row). Native {@code FormTableLens.insertRow} throws an
    * unmapped ArrayIndexOutOfBounds (a generic 500) outside those ranges, and RuntimeException
    * ("Insert header cell is not allowed!") when the target is the header row of a table with no
    * header rows.
    */
   private static void requireInsertIndexInRange(RuntimeViewsheet rvs, String assemblyName,
                                                 int index, boolean append) throws Exception
   {
      FormTableLens lens = resolveLens(rvs, assemblyName);

      if(lens == null) {
         return;
      }

      int dataRows = dataRowCount(lens);

      if(append) {
         if(dataRows < 1) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' has no data rows to append after (index " + index +
               "); use form_table_insert_row with mode 'insert' at index 0 instead.");
         }

         if(index < 0 || index > dataRows - 1) {
            throw new IllegalArgumentException(
               "'index' " + index + " is out of range for append on '" + assemblyName +
               "': valid range is 0.." + (dataRows - 1) + " (" + dataRows + " data row(s)).");
         }
      }
      else {
         if(index < 0 || index > dataRows) {
            throw new IllegalArgumentException(
               "'index' " + index + " is out of range for insert on '" + assemblyName +
               "': valid range is 0.." + dataRows + " (" + dataRows + " data row(s)).");
         }

         if(index + lens.getHeaderRowCount() == 0) {
            throw new IllegalArgumentException(
               "'index' 0 cannot be inserted at on '" + assemblyName + "': it has no header " +
               "row, so row 0 is its first row and the native insert refuses it. Use append " +
               "after a data row, or insert at index " + Math.min(1, dataRows) + " or later.");
         }
      }
   }

   /**
    * {@code FormTableLens.deleteRow} silently ignores an out-of-range row, and {@code
    * VSFormTableService.deleteRows} applies a repeated row index twice (deleting two distinct
    * rows), so both are refused here instead.
    */
   private static void requireDeleteRowsInRange(RuntimeViewsheet rvs, String assemblyName,
                                                List<Integer> rows) throws Exception
   {
      FormTableLens lens = resolveLens(rvs, assemblyName);

      if(lens == null) {
         return;
      }

      int dataRows = dataRowCount(lens);
      Set<Integer> seen = new HashSet<>();

      for(int r : rows) {
         if(r < 0 || r > dataRows - 1) {
            throw new IllegalArgumentException(
               "'rows' entry " + r + " is out of range for '" + assemblyName + "': valid range " +
               (dataRows > 0 ? "is 0.." + (dataRows - 1) : "is empty") + " (" + dataRows +
               " data row(s)).");
         }

         if(!seen.add(r)) {
            throw new IllegalArgumentException(
               "'rows' lists row " + r + " more than once; each row may be named only once.");
         }
      }
   }

   private static void requireCellInRange(RuntimeViewsheet rvs, String assemblyName, int row,
                                          int col) throws Exception
   {
      FormTableLens lens = resolveLens(rvs, assemblyName);

      if(lens == null) {
         return;
      }

      int dataRows = dataRowCount(lens);

      if(row < 0 || row > dataRows - 1) {
         throw new IllegalArgumentException(
            "'row' " + row + " is out of range for '" + assemblyName + "': valid range " +
            (dataRows > 0 ? "is 0.." + (dataRows - 1) : "is empty") + " (" + dataRows +
            " data row(s)).");
      }

      ColumnSelection visible = lens.getVisibleColumns();
      int cols = visible == null ? -1 : visible.getAttributeCount();

      if(cols >= 0 && (col < 0 || col > cols - 1)) {
         throw new IllegalArgumentException(
            "'col' " + col + " is out of range for '" + assemblyName + "': valid range " +
            (cols > 0 ? "is 0.." + (cols - 1) : "is empty") + " (" + cols +
            " visible column(s)).");
      }
   }

   /**
    * Mirrors the exact editable condition {@code BaseTableCellModel.createFormCell} computes for
    * display -- new row (column is form-optioned) OR table Edit is on (and column is
    * form-optioned) -- and refuses a {@code setCell} that would violate it, the same posture
    * {@link #requireInsertable}/{@link #requireDeletable}/{@link #requireWriteBack} already take
    * for the other three write paths. {@code VSFormTableService.changeFormInput} itself enforces
    * none of this: it would otherwise let a STOMP-bypassing caller change a cell the Preview
    * toolbar's own grid would render as non-editable and refuse to let a human click into.
    *
    * <p>A no-op when the lens can't be resolved. Row/column bounds are enforced earlier by
    * {@link #requireCellInRange}, so the range guard below is only defensive.
    */
   private static void requireEditableCell(RuntimeViewsheet rvs, String assemblyName,
                                           int nativeRow, int dataRow, int col) throws Exception
   {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return;
      }

      FormTableLens lens = box.get().getFormTableLens(assemblyName);

      if(lens == null || lens.rows() == null || nativeRow < 0 || nativeRow >= lens.rows().length) {
         return;
      }

      ColumnOption option = lens.getVisibleColumnOption(col);
      boolean newRow = FormTableRow.ADDED == lens.rows()[nativeRow].getRowState();
      boolean editable = newRow ? option.isForm() : lens.isEdit() && option.isForm();

      if(!editable) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "'[" + dataRow + "][" + col + "] is not editable -- its column " +
            "is not form-optioned, or (for a pre-existing row, not one just added via " +
            "form_table_insert_row) the table's Edit switch in Table > Form Options is off. " +
            "The underlying service does not check this itself, so it is refused here instead " +
            "of silently doing what the Preview toolbar's own grid would refuse to let a human " +
            "click into.");
      }
   }

   private static void putWarnings(Map<String, Object> result, List<String> warnings) {
      if(warnings != null && !warnings.isEmpty()) {
         result.put("warnings", warnings);
      }
   }

   /**
    * Pulls the {@code LoadTableDataCommand} every {@code VSFormTableService} method dispatches
    * (via {@code BaseTableService.loadTableData}) out of the capturing dispatcher, the same
    * technique {@code ViewsheetFormatService.getCellFormat} uses to read a command-delivered
    * result back outside a live browser session. Limited to the window
    * {@code VSFormTableService} itself reloads on every call (the first 100 lens rows, header
    * included).
    *
    * <p><b>{@code rows} holds DATA rows only, in the same 0-based index space this class's
    * {@code index}/{@code row}/{@code rows} inputs use.</b> The command's {@code tableCells} are
    * absolute lens rows {@code start..end-1}, so when {@code start} is inside the header the first
    * {@code headerRowCount - start} of them are header rows; those are dropped from {@code rows}
    * and the last of them is returned as {@code columns} instead (the visible column names, in the
    * same order {@code col} indexes). {@code rowCount} is the command's
    * {@code runtimeDataRowCount}, which is already data rows only. Every caller here passes
    * {@code start = 0}, so {@code rows[k]} is data row {@code k}; a non-zero {@code start} would
    * make {@code rows[0]} data row {@code start - headerRowCount} and would need that offset
    * exposed as well.
    */
   private static void populateSnapshot(Map<String, Object> result,
                                        CapturingCommandDispatcher dispatcher, String assemblyName)
   {
      LoadTableDataCommand load = null;

      for(CapturingCommandDispatcher.Command command : dispatcher.getCapturedCommands()) {
         if(Objects.equals(command.getAssembly(), assemblyName) &&
            command.getCommand() instanceof LoadTableDataCommand cmd)
         {
            load = cmd;
         }
      }

      if(load == null) {
         result.put("rowCount", null);
         result.put("columns", List.of());
         result.put("rows", List.of());
         return;
      }

      BaseTableCellModel[][] cells = load.tableCells();
      int headerRows = cells == null ? 0 :
         Math.min(cells.length, Math.max(0, load.headerRowCount() - load.start()));

      result.put("rowCount", load.runtimeDataRowCount());
      result.put("columns", headerRows > 0 ? toRow(cells[headerRows - 1]) : List.of());
      result.put("rows", toRows(cells, headerRows));
   }

   private static List<List<Object>> toRows(BaseTableCellModel[][] cells, int skip) {
      List<List<Object>> rows = new ArrayList<>();

      if(cells == null) {
         return rows;
      }

      for(int i = skip; i < cells.length; i++) {
         rows.add(toRow(cells[i]));
      }

      return rows;
   }

   private static List<Object> toRow(BaseTableCellModel[] rowCells) {
      List<Object> row = new ArrayList<>();

      if(rowCells != null) {
         for(BaseTableCellModel cell : rowCells) {
            row.add(cell == null ? null : cell.getCellData());
         }
      }

      return row;
   }

   private final ViewsheetSessionService sessions;
   private final VSFormTableService formTableService;
}
