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
 * this class's own javadoc on each method already promised.
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

         InsertTableRowEvent event = InsertTableRowEvent.builder()
            .assemblyName(assemblyName)
            .insert(!append)
            .row(index + headerRowOffset(rvs, assemblyName))
            .start(0)
            .build();
         formTableService.addRow(runtimeId, event, linkUri, dispatcher, user);
         populateSnapshot(result, dispatcher, assemblyName);
      });

      result.put("assembly", assemblyName);
      putWarnings(result, warnings);
      return result;
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
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return 0;
      }

      FormTableLens lens = box.get().getFormTableLens(assemblyName);
      return lens == null ? 0 : lens.getHeaderRowCount();
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
    * <p>A no-op (same "fail downstream instead" reasoning as {@link #headerRowOffset}) when the
    * lens can't be resolved, or {@code nativeRow} is out of the lens's own row range -- in both
    * cases the native call is about to fail on its own terms regardless of this check.
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
    * result back outside a live browser session. Limited to the first 100 rows -- the same window
    * {@code VSFormTableService} itself reloads on every call.
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
         result.put("rows", List.of());
         return;
      }

      result.put("rowCount", load.runtimeDataRowCount());
      result.put("rows", toRows(load));
   }

   private static List<List<Object>> toRows(LoadTableDataCommand load) {
      BaseTableCellModel[][] cells = load.tableCells();
      List<List<Object>> rows = new ArrayList<>();

      if(cells == null) {
         return rows;
      }

      for(BaseTableCellModel[] rowCells : cells) {
         List<Object> row = new ArrayList<>();

         for(BaseTableCellModel cell : rowCells) {
            row.add(cell == null ? null : cell.getCellData());
         }

         rows.add(row);
      }

      return rows;
   }

   private final ViewsheetSessionService sessions;
   private final VSFormTableService formTableService;
}
