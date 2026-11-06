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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.FormUtil;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.ImportXLSInspection;
import inetsoft.web.viewsheet.service.ImportXLSInspection.TableCheck;
import inetsoft.web.viewsheet.service.ImportXLSResult;
import inetsoft.web.viewsheet.service.ImportXLSResult.Outcome;
import inetsoft.web.viewsheet.service.ImportXLSResult.TableResult;
import inetsoft.web.viewsheet.service.ImportXLSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.function.Supplier;

/**
 * The Viewer's "Import Excel" for the wiz agent: reads an offline-edited export of the paired
 * dashboard and turns the differences into the Form tables' <b>pending</b> edits. Nothing is
 * written to a worksheet until {@code form_table_apply}.
 *
 * <p>The Viewer path ({@code ImportXLSController} + {@code ImportXLSControllerService}) rides an
 * upload endpoint plus a STOMP event, answers only with messages, and trusts the file. This class
 * calls the same {@link ImportXLSService} importer directly, and refuses -- before anything is
 * mutated, so no undo step and no revision is spent -- every input it would silently turn into
 * damage:
 * <ul>
 * <li>no Import toolbar permission (the Viewer hides the button on it; the upload endpoint itself
 * checks nothing);</li>
 * <li>a design-mode pairing, which has no runtime form state;</li>
 * <li>a file that is not a readable workbook, or has no "Current View" sheet;</li>
 * <li>a table whose rows have no row in the {@code _Backup} sheet (checked per table: the sheet
 * exists for any export of the current view, but only holds the tables that had a Form lens at
 * export time). The importer reads such a row as ADDED, so Apply would write every existing row a
 * second time;</li>
 * <li>a file whose header row is not as wide as the live table (the dashboard changed since the
 * export);</li>
 * <li>nothing in the file matching a Form table (the usual cause is an export with
 * {@code match=true}, which writes no named ranges);</li>
 * <li>pending edits on a table about to be imported, unless the caller says to discard them: the
 * import replaces a table's pending rows wholesale, and what is dropped cannot be got back.</li>
 * </ul>
 *
 * <p>The upload is written to a private temp file and deleted in a {@code finally}, never to the
 * CacheFS key the Viewer uses (runtime id + type), which would race with a person importing into the
 * same runtime.
 */
@Service
public class FormTableImportService {
   private static final Logger LOG = LoggerFactory.getLogger(FormTableImportService.class);

   @Autowired
   public FormTableImportService(ViewsheetSessionService sessions,
                                 CoreLifecycleService lifecycle, SecurityEngine securityEngine)
   {
      this(sessions, lifecycle, securityEngine, ImportXLSService::getInstance);
   }

   FormTableImportService(ViewsheetSessionService sessions, CoreLifecycleService lifecycle,
                          SecurityEngine securityEngine, Supplier<ImportXLSService> importer)
   {
      this.sessions = sessions;
      this.lifecycle = lifecycle;
      this.securityEngine = securityEngine;
      this.importer = importer;
   }

   /**
    * @param content        the workbook's bytes
    * @param fileType       {@code xls} or {@code xlsx}, case-insensitive
    * @param discardPending true to replace pending edits on the tables being imported
    */
   public Map<String, Object> importExcel(String sessionToken, Principal user, byte[] content,
                                          String fileType, boolean discardPending, String linkUri)
      throws Exception
   {
      if(!securityEngine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Import",
                                         ResourceAction.READ))
      {
         throw new SecurityException("No permission for viewsheet import.");
      }

      String type = normalizeType(fileType);

      if(content == null || content.length == 0) {
         throw new IllegalArgumentException("The Excel file is empty.");
      }

      checkSize(content.length);

      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      requireRuntimeFormDashboard(rvs);

      Path file = Files.createTempFile("wiz-form-import-", "." + type);

      try {
         Files.write(file, content);
         return importFile(sessionToken, user, rvs, file, type, discardPending, linkUri);
      }
      finally {
         try {
            Files.deleteIfExists(file);
         }
         catch(Exception e) {
            file.toFile().deleteOnExit();
            LOG.warn("Could not delete the temporary import file {}: {}", file, e.getMessage());
         }
      }
   }

   private Map<String, Object> importFile(String sessionToken, Principal user,
                                          RuntimeViewsheet rvs, Path file, String type,
                                          boolean discardPending, String linkUri)
      throws Exception
   {
      ImportXLSService xls = importer.get();
      Catalog catalog = Catalog.getCatalog();
      ImportXLSInspection inspection = xls.inspect(file, type, rvs, catalog);
      List<TableCheck> matched = refuseUnlessImportable(inspection, rvs);
      List<String> pending = pendingTables(rvs, matched);

      if(!pending.isEmpty() && !discardPending) {
         throw new IllegalArgumentException(
            "Importing would replace the pending (not yet applied) row edits of " +
            String.join(", ", pending) + ". The import swaps a table's pending rows for the file's " +
            "wholesale, and what is dropped cannot be recovered. Nothing was changed. Commit them " +
            "first with form_table_apply, or pass discardPending: true to throw them away.");
      }

      Map<String, Object> result = new LinkedHashMap<>();
      Map<String, Map<String, Object>> snapshots = new LinkedHashMap<>();
      ImportXLSResult[] imported = new ImportXLSResult[1];

      List<String> warnings = sessions.mutate(sessionToken, user, (rvs2, runtimeId, dispatcher) -> {
         Map<String, List<FormTableRow>> clearedDeletes = new HashMap<>();

         if(discardPending) {
            // the importer appends the file's deleted rows to the pending ones; the table's own
            // pending deletes have to go first, and come back if the table is not imported
            for(TableCheck check : matched) {
               FormTableLens lens = lens(rvs2, check.name());
               List<FormTableRow> rows = List.of(lens.rows(FormTableRow.DELETED));
               clearedDeletes.put(check.name(), rows);

               for(FormTableRow row : rows) {
                  lens.removeDeletedRow(row);
               }
            }
         }

         imported[0] = xls.importWorkbook(file, type, rvs2, linkUri, dispatcher, lifecycle,
                                          catalog);

         Set<String> importedNames = new HashSet<>();

         for(TableResult table : imported[0].tables()) {
            if(table.outcome() == Outcome.IMPORTED) {
               importedNames.add(table.assembly());
               Map<String, Object> snapshot = new LinkedHashMap<>();
               FormTableRowService.populateSnapshot(snapshot, dispatcher, table.assembly());
               snapshots.put(table.assembly(), snapshot);
            }
         }

         for(Map.Entry<String, List<FormTableRow>> cleared : clearedDeletes.entrySet()) {
            if(!importedNames.contains(cleared.getKey())) {
               lens(rvs2, cleared.getKey()).addDeleteRows(cleared.getValue());
            }
         }
      });

      ImportXLSResult done = imported[0];
      List<String> notInRange = new ArrayList<>(new TreeSet<>(done.notInRange()));

      if(snapshots.isEmpty()) {
         throw new IllegalArgumentException(
            "No table was imported" +
            (notInRange.isEmpty() ? "." : ": the file has a value that breaks a column option in " +
               String.join(", ", notInRange) + " (a table is skipped whole, not row by row).") +
            " Fix the values in the Excel file and import again.");
      }

      result.put("ok", true);
      result.put("applied", false);
      result.put("tables", done.tables().stream().map(FormTableImportService::toMap).toList());
      result.put("notFound", names(done, Outcome.NOT_IN_FILE));
      result.put("notInRange", notInRange);
      result.put("notFormTables", names(done, Outcome.NOT_A_FORM_TABLE));
      result.put("skippedInvisible", names(done, Outcome.SKIPPED_INVISIBLE));
      result.put("snapshots", snapshots);

      List<String> all = new ArrayList<>(warnings == null ? List.of() : warnings);
      all.addAll(writeBackWarnings(rvs, done));
      result.put("warnings", all);
      return result;
   }

   /**
    * Every refusal that can be decided from the file and the live tables alone.
    *
    * @return the Form tables the import will replace the rows of
    */
   private List<TableCheck> refuseUnlessImportable(ImportXLSInspection inspection,
                                                   RuntimeViewsheet rvs)
   {
      if(!inspection.readable()) {
         throw new IllegalArgumentException(
            "The file is not a readable Excel workbook of the type it was sent as (.xls or .xlsx).");
      }

      if(!inspection.hasCurrentView()) {
         throw new IllegalArgumentException(
            "The workbook has no 'Current View' sheet, so it is not an Excel export of this " +
            "dashboard's current view. Causes: it was exported with current:false (bookmarks " +
            "only), by another tool, or on a server in a different language (the sheet name is " +
            "localized). Re-export with export_viewsheet {format: \"Excel\", match: false, " +
            "onlyDataComponents: false, current: true}.");
      }

      List<TableCheck> formTables = inspection.tables().stream()
         .filter(t -> t.visible() && t.formTable()).toList();
      List<TableCheck> matched = formTables.stream().filter(TableCheck::inFile).toList();

      if(matched.isEmpty()) {
         throw new IllegalArgumentException(
            "No Form table of this dashboard has named ranges in the file. Form tables here: " +
            (formTables.isEmpty() ? "(none visible)" : names(formTables)) + ". The usual cause is " +
            "an export with match: true (the default) or onlyDataComponents: true, which write " +
            "no named ranges and no _Backup sheet; re-export with export_viewsheet {format: " +
            "\"Excel\", match: false, onlyDataComponents: false, current: true}, edit that file, " +
            "and import it. Renaming a table or deleting its sheet area also unmatches it.");
      }

      List<TableCheck> unbacked = matched.stream().filter(t -> t.unbackedRows() > 0).toList();

      if(!unbacked.isEmpty()) {
         throw new IllegalArgumentException(
            "The file's _Backup sheet has no rows for " + names(unbacked) +
            (inspection.hasBackup() ? " (the sheet exists but these tables were written without " +
               "their rows -- an export made from a pairing with no runtime form data, such as " +
               "the Composer's Design tab)" : " (the file has no _Backup sheet at all)") +
            ". Without them every existing row would be read as a new row and Apply would write " +
            "it a second time. Nothing was changed. Re-export from the Preview or viewer pairing " +
            "with export_viewsheet {format: \"Excel\", match: false, onlyDataComponents: false, " +
            "current: true}, and do not delete or edit the hidden _Backup sheet.");
      }

      List<TableCheck> unnamed = matched.stream()
         .filter(t -> t.lensRows() > 0 && t.dataRows() > 0 && t.namedRows() == 0).toList();

      if(!unnamed.isEmpty()) {
         throw new IllegalArgumentException(
            "None of the rows of " + names(unnamed) + " in the file carries a row name, so each " +
            "would be read as a new row and duplicate the table's existing rows on Apply. The " +
            "first-column named ranges were lost (a copy/paste into a new file, or another " +
            "editor). Nothing was changed. Edit the exported file in place.");
      }

      List<TableCheck> wrongWidth = matched.stream().filter(TableCheck::columnCountDiffers).toList();

      if(!wrongWidth.isEmpty()) {
         StringBuilder detail = new StringBuilder();

         for(TableCheck t : wrongWidth) {
            detail.append(detail.length() == 0 ? "" : "; ").append(t.name()).append(": file has ")
               .append(t.fileColumns()).append(" column(s), the dashboard's table has ")
               .append(t.lensColumns());
         }

         throw new IllegalArgumentException(
            "The file's table layout no longer matches the dashboard (" + detail + "), so the " +
            "dashboard changed since the export, or a column was added/removed in Excel. Nothing " +
            "was changed. Export again and redo the edits.");
      }

      return matched;
   }

   private static List<String> pendingTables(RuntimeViewsheet rvs, List<TableCheck> matched)
      throws Exception
   {
      List<String> pending = new ArrayList<>();

      for(TableCheck check : matched) {
         if(lens(rvs, check.name()).isChanged()) {
            pending.add(check.name());
         }
      }

      return pending;
   }

   private static FormTableLens lens(RuntimeViewsheet rvs, String assembly) throws Exception {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty() || box.get().getFormTableLens(assembly) == null) {
         throw new IllegalArgumentException(
            "'" + assembly + "' has no runtime form table data in this pairing any more. " +
            "Reconnect the Preview or viewer pairing and retry.");
      }

      return box.get().getFormTableLens(assembly);
   }

   private static void requireRuntimeFormDashboard(RuntimeViewsheet rvs) {
      if(!rvs.isRuntime()) {
         throw new IllegalArgumentException(
            "form_table_import_excel fills the runtime form state, which exists only in a Preview " +
            "or viewer pairing; this pairing is the Composer's Design tab, where a form table has " +
            "no row data. Click Preview in the Composer toolbar, connect the pairing code the " +
            "preview shows with connect_sheet {code, additive: true}, and retry there.");
      }

      if(rvs.getViewsheetSandbox().isEmpty()) {
         throw new IllegalArgumentException(
            "This pairing's viewsheet has no runtime sandbox (it was closed or has not finished " +
            "loading). Reconnect the Preview or viewer pairing and retry.");
      }

      if(!FormUtil.containsForm(rvs.getViewsheet(), true)) {
         throw new IllegalArgumentException(
            "This dashboard has no Form table (a Table with Form enabled in Table > Form " +
            "Options), so there is nothing an offline Excel edit could be imported into.");
      }
   }

   /** {@code applyChanges} is refused without write-back; say so now, not after the edits. */
   private static List<String> writeBackWarnings(RuntimeViewsheet rvs, ImportXLSResult result) {
      List<String> warnings = new ArrayList<>();
      Viewsheet vs = rvs.getViewsheet();

      for(TableResult table : result.tables()) {
         if(table.outcome() != Outcome.IMPORTED) {
            continue;
         }

         VSAssembly assembly = vs.getAssembly(table.assembly());

         if(assembly instanceof TableVSAssembly &&
            !((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).isWriteBack())
         {
            warnings.add("'" + table.assembly() + "' does not have write-back enabled in Table > " +
                         "Form Options, so form_table_apply will refuse it.");
         }
      }

      return warnings;
   }

   private static String normalizeType(String fileType) {
      String type = fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);

      if(!"xls".equals(type) && !"xlsx".equals(type)) {
         throw new IllegalArgumentException(
            "'fileType' must be XLS or XLSX, got " + (fileType == null ? "nothing" : "'" + fileType +
            "'") + ".");
      }

      return type;
   }

   /** Same limit, and same fallback to the CSV one, as the worksheet Excel import. */
   private static void checkSize(long size) {
      String max = SreeEnv.getProperty("excel.import.max");

      if(max == null) {
         max = SreeEnv.getProperty("csv.import.max");
      }

      if(max == null) {
         return;
      }

      long maxBytes;

      try {
         maxBytes = Long.parseLong(max);
      }
      catch(NumberFormatException e) {
         LOG.warn("Ignoring non-numeric excel.import.max/csv.import.max value: {}", max);
         return;
      }

      if(size > maxBytes) {
         throw new IllegalArgumentException(
            "The Excel file exceeds the maximum allowed size of " + maxBytes + " bytes " +
            "(excel.import.max).");
      }
   }

   private static String names(List<TableCheck> tables) {
      return String.join(", ", tables.stream().map(TableCheck::name).toList());
   }

   private static List<String> names(ImportXLSResult result, Outcome outcome) {
      return result.tables().stream().filter(t -> t.outcome() == outcome)
         .map(TableResult::assembly).toList();
   }

   private static Map<String, Object> toMap(TableResult table) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("assembly", table.assembly());
      map.put("outcome", table.outcome().name());

      if(table.outcome() == Outcome.IMPORTED) {
         map.put("added", table.added());
         map.put("changed", table.changed());
         map.put("unchanged", table.unchanged());
         map.put("deleted", table.deleted());
         map.put("rowCount", table.rowCount());
      }

      return map;
   }

   private final ViewsheetSessionService sessions;
   private final CoreLifecycleService lifecycle;
   private final SecurityEngine securityEngine;
   private final Supplier<ImportXLSService> importer;
}
