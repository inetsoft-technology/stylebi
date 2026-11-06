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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.FormUtil;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.viewsheet.service.ImportXLSInspection.TableCheck;
import inetsoft.web.viewsheet.service.ImportXLSResult.Outcome;
import inetsoft.web.viewsheet.service.ImportXLSResult.TableResult;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug 80481: {@link FormTableImportService}, the wiz agent's Import Excel for a Form table.
 * The importer ({@code ImportXLSService}) and the session service are mocked; the lenses are real
 * {@link FormTableLens}es, since the service reads and edits their pending state.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableImportServiceTest {
   private static final byte[] XLSX = {'P', 'K', 3, 4};

   private MockedStatic<FormUtil> formUtil;
   private final Principal user = () -> "agent";

   private ViewsheetSessionService sessions;
   private SecurityEngine security;
   private ImportXLSService importer;
   private RuntimeViewsheet rvs;
   private final Map<String, FormTableLens> lenses = new LinkedHashMap<>();
   private final List<Path> seenFiles = new ArrayList<>();
   private FormTableImportService service;
   private boolean runtime = true;

   @BeforeEach
   void setUp() throws Exception {
      formUtil = Mockito.mockStatic(FormUtil.class);
      formUtil.when(() -> FormUtil.containsForm(any(), anyBoolean())).thenReturn(true);

      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq("Import"),
                                    eq(ResourceAction.READ))).thenReturn(true);

      importer = mock(ImportXLSService.class);
      sessions = mock(ViewsheetSessionService.class);
      rvs = mock(RuntimeViewsheet.class);
      lenses.put("Orders", lens());
      lenses.put("Returns", lens());
      rebuildRuntime();
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      // like ViewsheetSessionService.mutate: run the mutation, hand back the dispatcher's warnings
      when(sessions.mutate(anyString(), any(Principal.class),
                           any(ViewsheetSessionService.Mutation.class)))
         .thenAnswer(inv -> {
            inv.<ViewsheetSessionService.Mutation>getArgument(2)
               .run(rvs, "rid", mock(CapturingCommandDispatcher.class));
            return List.of();
         });

      service = new FormTableImportService(sessions, mock(CoreLifecycleService.class), security,
                                           () -> importer);
      stubInspection(inspection(check("Orders"), check("Returns")));
   }

   @AfterEach
   void release() {
      formUtil.close();
   }

   private void rebuildRuntime() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);

      for(Map.Entry<String, FormTableLens> e : lenses.entrySet()) {
         when(box.getFormTableLens(e.getKey())).thenReturn(e.getValue());
      }

      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.isRuntime()).thenAnswer(inv -> runtime);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
   }

   private static FormTableLens lens() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {{"Region", "Amount"},
                                                                   {"East", 1}, {"West", 2}});
      base.setHeaderRowCount(1);
      return new FormTableLens(base);
   }

   private static TableCheck check(String name) {
      return new TableCheck(name, true, true, true, 2, 2, 2, 0, 2, 2);
   }

   private static ImportXLSInspection inspection(TableCheck... tables) {
      return new ImportXLSInspection(true, true, true, List.of(tables));
   }

   private void stubInspection(ImportXLSInspection inspection) throws Exception {
      when(importer.inspect(any(Path.class), anyString(), any(), any())).thenAnswer(inv -> {
         Path file = inv.getArgument(0);
         seenFiles.add(file);
         assertTrue(Files.exists(file), "the inspector is handed a real file");
         return inspection;
      });
   }

   private static ImportXLSResult imported(String... tables) {
      List<TableResult> results = new ArrayList<>();

      for(String t : tables) {
         results.add(new TableResult(t, Outcome.IMPORTED, 1, 2, 3, 4, 7));
      }

      return new ImportXLSResult(results, List.of(), Set.of());
   }

   private void stubImport(ImportXLSResult result) throws Exception {
      when(importer.importWorkbook(any(Path.class), anyString(), any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            seenFiles.add(inv.getArgument(0));
            return result;
         });
   }

   private Map<String, Object> run(boolean discard) throws Exception {
      return service.importExcel("tok", user, XLSX, "xlsx", discard, "");
   }

   private IllegalArgumentException refused(boolean discard) {
      return assertThrows(IllegalArgumentException.class, () -> run(discard));
   }

   /** A refusal costs no undo step: nothing may have gone through mutate(), nothing imported. */
   private void assertNothingMutated() throws Exception {
      verify(sessions, never()).mutate(anyString(), any(Principal.class),
                                       any(ViewsheetSessionService.Mutation.class));
      verify(importer, never()).importWorkbook(any(), any(), any(), any(), any(), any(), any());

      for(FormTableLens lens : lenses.values()) {
         assertFalse(lens.isChanged());
      }

      for(Path file : seenFiles) {
         assertFalse(Files.exists(file), "the temp file is deleted: " + file);
      }
   }

   // --- refusals, all before the mutation ------------------------------------------------------

   @Test
   void refusesWithoutTheImportToolbarPermissionBeforeTouchingTheSession() throws Exception {
      when(security.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq("Import"),
                                    eq(ResourceAction.READ))).thenReturn(false);

      assertThrows(SecurityException.class, () -> run(false));

      verifyNoInteractions(sessions);
      verifyNoInteractions(importer);
   }

   @Test
   void refusesAnUnknownFileTypeAndAnEmptyFile() throws Exception {
      Exception type = assertThrows(IllegalArgumentException.class,
         () -> service.importExcel("tok", user, XLSX, "csv", false, ""));
      assertTrue(type.getMessage().contains("XLS or XLSX"), type.getMessage());

      Exception empty = assertThrows(IllegalArgumentException.class,
         () -> service.importExcel("tok", user, new byte[0], "xlsx", false, ""));
      assertTrue(empty.getMessage().contains("empty"), empty.getMessage());
      verifyNoInteractions(importer);
   }

   @Test
   void acceptsTheFileTypeInAnyCase() throws Exception {
      stubImport(imported("Orders"));
      assertEquals(Boolean.TRUE, service.importExcel("tok", user, XLSX, " XLSX ", false, "")
         .get("ok"));
   }

   @Test
   void refusesADesignModePairingNamingPreview() throws Exception {
      runtime = false;

      IllegalArgumentException e = refused(false);

      assertTrue(e.getMessage().contains("Preview"), e.getMessage());
      assertTrue(e.getMessage().contains("additive"), e.getMessage());
      verifyNoInteractions(importer);
      assertNothingMutated();
   }

   @Test
   void refusesADashboardWithoutAFormTable() throws Exception {
      formUtil.when(() -> FormUtil.containsForm(any(), anyBoolean())).thenReturn(false);

      assertTrue(refused(false).getMessage().contains("no Form table"));
      verifyNoInteractions(importer);
   }

   @Test
   void refusesAFileThatIsNotAReadableWorkbook() throws Exception {
      stubInspection(new ImportXLSInspection(false, false, false, List.of()));

      assertTrue(refused(false).getMessage().contains("not a readable Excel workbook"));
      assertNothingMutated();
   }

   @Test
   void refusesAWorkbookWithoutTheCurrentViewSheet() throws Exception {
      stubInspection(new ImportXLSInspection(true, false, false, List.of()));

      String message = refused(false).getMessage();

      assertTrue(message.contains("'Current View'"), message);
      assertTrue(message.contains("match: false"), message);
      assertNothingMutated();
   }

   @Test
   void whenNothingMatchesTheMessageGivesTheMatchFalseRecipe() throws Exception {
      stubInspection(inspection(
         new TableCheck("Orders", true, true, false, 0, 2, 0, 0, -1, 2)));

      String message = refused(false).getMessage();

      assertTrue(message.contains("match: false"), message);
      assertTrue(message.contains("onlyDataComponents: false"), message);
      assertTrue(message.contains("Orders"), "it names the Form tables: " + message);
      assertNothingMutated();
   }

   @Test
   void aHiddenOrNonFormTableDoesNotCountAsAMatch() throws Exception {
      stubInspection(inspection(
         new TableCheck("Orders", false, true, true, 2, 2, 2, 0, 2, 2),
         new TableCheck("Plain", true, false, true, 2, 0, 2, 0, 2, 2)));

      assertTrue(refused(false).getMessage().contains("No Form table of this dashboard"));
      assertNothingMutated();
   }

   /** The workbook has a _Backup sheet, but not the rows of one of its tables. */
   @Test
   void theBackupRefusalIsPerTable() throws Exception {
      stubInspection(inspection(check("Orders"),
                                new TableCheck("Returns", true, true, true, 2, 2, 2, 2, 2, 2)));

      String message = refused(false).getMessage();

      assertTrue(message.contains("Returns"), message);
      assertFalse(message.contains("Orders"), "Orders is fine: " + message);
      assertTrue(message.contains("_Backup"), message);
      assertTrue(message.contains("second time"), message);
      assertNothingMutated();
   }

   @Test
   void theBackupRefusalSaysWhenThereIsNoBackupSheetAtAll() throws Exception {
      stubInspection(new ImportXLSInspection(true, true, false,
         List.of(new TableCheck("Orders", true, true, true, 2, 2, 2, 2, 2, 2))));

      assertTrue(refused(false).getMessage().contains("no _Backup sheet at all"));
      assertNothingMutated();
   }

   @Test
   void refusesRowsThatLostTheirRowNames() throws Exception {
      stubInspection(inspection(new TableCheck("Orders", true, true, true, 2, 2, 0, 0, 2, 2)));

      String message = refused(false).getMessage();

      assertTrue(message.contains("row name"), message);
      assertNothingMutated();
   }

   @Test
   void anEmptyTableMayGainTypedInRowsWithoutRowNames() throws Exception {
      stubInspection(inspection(new TableCheck("Orders", true, true, true, 3, 0, 0, 0, 2, 2)));
      stubImport(imported("Orders"));

      assertEquals(Boolean.TRUE, run(false).get("ok"));
   }

   @Test
   void refusesAFileWhoseColumnCountDiffersFromTheLiveTable() throws Exception {
      stubInspection(inspection(new TableCheck("Orders", true, true, true, 2, 2, 2, 0, 3, 2)));

      String message = refused(false).getMessage();

      assertTrue(message.contains("Orders: file has 3 column(s), the dashboard's table has 2"),
                 message);
      assertNothingMutated();
   }

   @Test
   void refusesPendingEditsUnlessTheyAreDiscarded() throws Exception {
      lenses.get("Orders").addDeleteRows(List.of(new FormTableRow(2)));

      String message = refused(false).getMessage();

      assertTrue(message.contains("Orders"), message);
      assertFalse(message.contains("Returns"), message);
      assertTrue(message.contains("discardPending"), message);
      assertTrue(message.contains("form_table_apply"), message);
      assertNothingMutatedExceptPending("Orders");
   }

   private void assertNothingMutatedExceptPending(String pendingTable) throws Exception {
      verify(sessions, never()).mutate(anyString(), any(Principal.class),
                                       any(ViewsheetSessionService.Mutation.class));
      verify(importer, never()).importWorkbook(any(), any(), any(), any(), any(), any(), any());
      assertEquals(1, lenses.get(pendingTable).rows(FormTableRow.DELETED).length,
                   "the pending edits are exactly as they were");
   }

   // --- the import itself --------------------------------------------------------------------

   @Test
   void successReportsPerTableCountsAndStaysPending() throws Exception {
      stubImport(new ImportXLSResult(
         List.of(new TableResult("Orders", Outcome.IMPORTED, 1, 2, 3, 4, 7),
                 TableResult.of("Returns", Outcome.NOT_IN_FILE),
                 TableResult.of("Plain", Outcome.NOT_A_FORM_TABLE),
                 TableResult.of("Hidden", Outcome.SKIPPED_INVISIBLE)),
         List.of("Returns"), Set.of()));

      Map<String, Object> result = run(false);

      assertEquals(Boolean.TRUE, result.get("ok"));
      assertEquals(Boolean.FALSE, result.get("applied"));
      assertEquals(List.of("Returns"), result.get("notFound"));
      assertEquals(List.of("Plain"), result.get("notFormTables"));
      assertEquals(List.of("Hidden"), result.get("skippedInvisible"));
      assertEquals(List.of(), result.get("notInRange"));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> tables = (List<Map<String, Object>>) result.get("tables");
      Map<String, Object> orders = tables.get(0);
      assertEquals("IMPORTED", orders.get("outcome"));
      assertEquals(1, orders.get("added"));
      assertEquals(2, orders.get("changed"));
      assertEquals(3, orders.get("unchanged"));
      assertEquals(4, orders.get("deleted"));
      assertEquals(7, orders.get("rowCount"));
      assertEquals("NOT_IN_FILE", tables.get(1).get("outcome"));
      assertFalse(tables.get(1).containsKey("added"), "no counts for a table that was not imported");

      @SuppressWarnings("unchecked")
      Map<String, Object> snapshots = (Map<String, Object>) result.get("snapshots");
      assertEquals(Set.of("Orders"), snapshots.keySet());
   }

   @Test
   void everyTableSkippedForRangeFailsLoudAndNamesTheTables() throws Exception {
      stubImport(new ImportXLSResult(
         List.of(TableResult.of("Orders", Outcome.NOT_IN_RANGE)), List.of(), Set.of("Orders")));

      String message = refused(false).getMessage();

      assertTrue(message.contains("No table was imported"), message);
      assertTrue(message.contains("Orders"), message);
      assertTrue(message.contains("column option"), message);
   }

   @Test
   void theTempFileIsDeletedAfterASuccessfulImport() throws Exception {
      stubImport(imported("Orders"));

      run(false);

      assertFalse(seenFiles.isEmpty());

      for(Path file : seenFiles) {
         assertFalse(Files.exists(file));
         assertTrue(file.startsWith(Path.of(System.getProperty("java.io.tmpdir"))),
                    "a private temp file, not the Viewer's CacheFS key: " + file);
         assertFalse(file.toString().contains("ImportXLSControllerService"), file.toString());
      }
   }

   @Test
   void theTempFileIsDeletedWhenTheImporterThrows() throws Exception {
      when(importer.importWorkbook(any(Path.class), anyString(), any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            seenFiles.add(inv.getArgument(0));
            throw new java.io.FileNotFoundException();
         });

      assertThrows(java.io.FileNotFoundException.class, () -> run(false));

      assertFalse(seenFiles.isEmpty());
      assertFalse(Files.exists(seenFiles.get(0)));
   }

   // --- discardPending ---------------------------------------------------------------------

   @Test
   void discardPendingClearsThePendingDeletesBeforeTheImportAppendsItsOwn() throws Exception {
      FormTableRow oldDelete = new FormTableRow(2);
      lenses.get("Orders").addDeleteRows(List.of(oldDelete));
      FormTableRow fileDelete = new FormTableRow(2);

      when(importer.importWorkbook(any(Path.class), anyString(), any(), any(), any(), any(), any()))
         .thenAnswer(inv -> {
            // the importer appends; at this moment the table's own pending delete must be gone
            assertEquals(0, lenses.get("Orders").rows(FormTableRow.DELETED).length);
            lenses.get("Orders").addDeleteRows(List.of(fileDelete));
            return imported("Orders");
         });

      run(true);

      FormTableRow[] deleted = lenses.get("Orders").rows(FormTableRow.DELETED);
      assertEquals(1, deleted.length);
      assertSame(fileDelete, deleted[0]);
   }

   @Test
   void discardPendingPutsTheDeletesBackOnATableThatWasNotImported() throws Exception {
      FormTableRow ordersDelete = new FormTableRow(2);
      FormTableRow returnsDelete = new FormTableRow(2);
      lenses.get("Orders").addDeleteRows(List.of(ordersDelete));
      lenses.get("Returns").addDeleteRows(List.of(returnsDelete));

      stubImport(new ImportXLSResult(
         List.of(new TableResult("Orders", Outcome.IMPORTED, 0, 0, 2, 0, 2),
                 TableResult.of("Returns", Outcome.NOT_IN_RANGE)),
         List.of(), Set.of("Returns")));

      run(true);

      assertSame(returnsDelete, lenses.get("Returns").rows(FormTableRow.DELETED)[0],
                 "a table the import skipped keeps its pending edits");
      assertEquals(0, lenses.get("Orders").rows(FormTableRow.DELETED).length,
                   "the imported table's old pending delete is gone (the stub added none)");
   }

   @Test
   void discardPendingRestoresEverythingWhenNothingWasImported() throws Exception {
      FormTableRow ordersDelete = new FormTableRow(2);
      lenses.get("Orders").addDeleteRows(List.of(ordersDelete));
      stubImport(new ImportXLSResult(
         List.of(TableResult.of("Orders", Outcome.NOT_IN_RANGE),
                 TableResult.of("Returns", Outcome.NOT_IN_RANGE)),
         List.of(), Set.of("Orders", "Returns")));

      assertThrows(IllegalArgumentException.class, () -> run(true));

      assertSame(ordersDelete, lenses.get("Orders").rows(FormTableRow.DELETED)[0]);
   }

   @Test
   void tablesNotInTheFileKeepTheirPendingEditsEvenWithDiscard() throws Exception {
      FormTableRow returnsDelete = new FormTableRow(2);
      lenses.get("Returns").addDeleteRows(List.of(returnsDelete));
      // the file only has Orders
      stubInspection(inspection(check("Orders"),
                                new TableCheck("Returns", true, true, false, 0, 2, 0, 0, -1, 2)));
      stubImport(imported("Orders"));

      run(false);

      assertSame(returnsDelete, lenses.get("Returns").rows(FormTableRow.DELETED)[0]);
   }
}
