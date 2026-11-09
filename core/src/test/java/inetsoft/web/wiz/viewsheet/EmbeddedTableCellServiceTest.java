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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SnapshotEmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CellRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableServiceProxy;
import inetsoft.web.viewsheet.event.table.ChangeVSTableCellsTextEvent;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.viewsheet.EmbeddedTableCellService.CellChange;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code EmbeddedTableCellService} bridges a batch of Viewer-style cell edits of a plain
 * Embedded Table through {@code ComposerVSTableService.changeCellText}. The data objects here are
 * real ({@code XEmbeddedTable}, {@code ColumnSelection}); the assemblies, session and native
 * service are mocks.
 *
 * <p>Table: columns {@code name} (string), {@code qty} (integer), {@code due} (date),
 * {@code flag} (boolean), {@code ratio} (double); three data rows; qty of row 1 is 20.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class EmbeddedTableCellServiceTest {
   // ── refusals about the assembly ──────────────────────────────────────────

   @Test
   void refusesAnUnknownAssembly() throws Exception {
      Harness h = new Harness(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Nope", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("Unknown assembly 'Nope'"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesANonTableAssembly() throws Exception {
      Harness h = new Harness(mock(ChartVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Chart1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("not an Embedded Table"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesAPlainTableThatIsNotAnEmbeddedTable() throws Exception {
      Harness h = new Harness(mock(TableVSAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("not an Embedded Table"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesAFormEnabledEmbeddedTableAndPointsAtTheFormTool() throws Exception {
      Harness h = new Harness();
      when(h.info.isForm()).thenReturn(true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("Form-enabled"), e.getMessage());
      assertTrue(e.getMessage().contains("form_table_set_cell"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesATableWhoseWorksheetSourceIsASnapshot() throws Exception {
      Harness h = new Harness();
      when(h.worksheet.getAssembly("WS")).thenReturn(mock(SnapshotEmbeddedTableAssembly.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("snapshot"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesATableWithNoVisibleColumns() throws Exception {
      Harness h = new Harness();
      when(h.table.getVisibleColumns()).thenReturn(new ColumnSelection());

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("no visible columns"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesAMissingSourceTable() throws Exception {
      Harness h = new Harness();
      when(h.worksheet.getAssembly("WS")).thenReturn(null);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "x")));

      assertTrue(e.getMessage().contains("no editable source"), e.getMessage());
      h.assertNothingWritten();
   }

   // ── refusals about the request ───────────────────────────────────────────

   @Test
   void refusesEmptyOrMissingChanges() throws Exception {
      Harness h = new Harness();

      Exception empty = assertThrows(IllegalArgumentException.class,
         () -> h.service.setCells("tok", h.user, "Table1", List.of(), ""));
      Exception missing = assertThrows(IllegalArgumentException.class,
         () -> h.service.setCells("tok", h.user, "Table1", null, ""));

      assertTrue(empty.getMessage().contains("'changes'"), empty.getMessage());
      assertTrue(missing.getMessage().contains("'changes'"), missing.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesARowOutsideTheBaseDataAndNamesTheRange() throws Exception {
      Harness h = new Harness();

      for(int row : new int[] { -1, 3, 99 }) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> h.set("Table1", change(row, 0, "x")));

         assertTrue(e.getMessage().contains("'row' " + row), e.getMessage());
         assertTrue(e.getMessage().contains("0..2"), e.getMessage());
         assertTrue(e.getMessage().contains("3 data row(s)"), e.getMessage());
      }

      h.assertNothingWritten();
   }

   @Test
   void refusesAColumnOutsideTheVisibleColumnsAndNamesTheRange() throws Exception {
      Harness h = new Harness();

      for(int col : new int[] { -1, 5, 40 }) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> h.set("Table1", change(0, col, "x")));

         assertTrue(e.getMessage().contains("'col' " + col), e.getMessage());
         assertTrue(e.getMessage().contains("0..4"), e.getMessage());
         assertTrue(e.getMessage().contains("5 visible column(s)"), e.getMessage());
      }

      h.assertNothingWritten();
   }

   @Test
   void refusesAChangeMissingItsRowOrCol() throws Exception {
      Harness h = new Harness();

      Exception noRow = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", new CellChange(null, 0, "x")));
      Exception noCol = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", new CellChange(0, null, "x")));

      assertTrue(noRow.getMessage().contains("'row'"), noRow.getMessage());
      assertTrue(noCol.getMessage().contains("'col'"), noCol.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesTheSameCellTwiceInOneBatchInsteadOfLastWins() throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(1, 1, "5"), change(0, 0, "x"), change(1, 1, "6")));

      assertTrue(e.getMessage().contains("'changes[2]'"), e.getMessage());
      assertTrue(e.getMessage().contains("more than once") ||
                 e.getMessage().contains("only once"), e.getMessage());
      h.assertNothingWritten();
   }

   // ── value parsing ────────────────────────────────────────────────────────

   @ParameterizedTest
   @ValueSource(strings = { "abc", "2.5", "1e3x", "NaN", " " })
   void refusesTextThatIsNotAnIntegerForAnIntegerColumn(String text) throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 1, text)));

      assertTrue(e.getMessage().contains("'qty' of 'Table1', integer column"), e.getMessage());
      assertTrue(e.getMessage().contains("not a valid integer"), e.getMessage());
      h.assertNothingWritten();
   }

   @ParameterizedTest
   @ValueSource(strings = { "2026-02-30", "not a date", "2026-13-01", "10:30:00" })
   void refusesTextThatIsNotADateForADateColumn(String text) throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 2, text)));

      assertTrue(e.getMessage().contains("'due' of 'Table1', date column"), e.getMessage());
      h.assertNothingWritten();
   }

   @ParameterizedTest
   @ValueSource(strings = { "maybe", "1", "yes" })
   void refusesTextThatIsNotABooleanForABooleanColumn(String text) throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 3, text)));

      assertTrue(e.getMessage().contains("boolean column"), e.getMessage());
      assertTrue(e.getMessage().contains("not a valid boolean"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesNanAndHexTextForADoubleColumn() throws Exception {
      Harness h = new Harness();

      for(String text : new String[] { "NaN", "0x10", "1d" }) {
         assertThrows(IllegalArgumentException.class,
            () -> h.set("Table1", change(0, 4, text)), text);
      }

      h.assertNothingWritten();
   }

   @ParameterizedTest
   @ValueSource(ints = { 1, 2, 3, 4 })
   void refusesAnEmptyStringForANonStringColumnAndPointsAtNull(int col) throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, col, "")));

      assertTrue(e.getMessage().contains("empty string"), e.getMessage());
      assertTrue(e.getMessage().contains("Send null"), e.getMessage());
      h.assertNothingWritten();
   }

   @Test
   void refusesTheFakeNullSentinelForANonStringColumn() throws Exception {
      Harness h = new Harness();

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 1, "__null__")));

      assertTrue(e.getMessage().contains("Send null"), e.getMessage());
      h.assertNothingWritten();
   }

   // ── batch atomicity: every refusal before any write ──────────────────────

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 2 })
   void aBatchWithOneInvalidChangeAtAnyPositionWritesNothing(int badAt) throws Exception {
      Harness h = new Harness();
      CellChange[] batch = {
         change(0, 0, "ok"), change(1, 1, "7"), change(2, 4, "1.5")
      };
      batch[badAt] = change(2, 1, "not a number");

      assertThrows(IllegalArgumentException.class, () -> h.set("Table1", batch));

      h.assertNothingWritten();
      assertTrue(h.stateMap.isEmpty());
   }

   @Test
   void aBatchWhoseLastChangeHasAnOutOfRangeColumnWritesNothing() throws Exception {
      Harness h = new Harness();

      assertThrows(IllegalArgumentException.class,
         () -> h.set("Table1", change(0, 0, "a"), change(1, 0, "b"), change(2, 9, "c")));

      h.assertNothingWritten();
      assertTrue(h.stateMap.isEmpty());
   }

   // ── what is sent, echoed and stored ──────────────────────────────────────

   @Test
   void echoesThePreviousValueReadBeforeTheWrite() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1", change(1, 1, "21"), change(0, 0, "Z"));

      List<Map<String, Object>> changes = changes(result);
      assertEquals(20, Integer.parseInt((String) changes.get(0).get("previous")));
      assertEquals("21", changes.get(0).get("value"));
      assertEquals("qty", changes.get(0).get("column"));
      assertEquals(1, changes.get(0).get("row"));
      assertEquals(1, changes.get(0).get("col"));
      assertEquals("A", changes.get(1).get("previous"));
      assertEquals("Z", changes.get(1).get("value"));
   }

   @Test
   void previousPrefersAnEarlierEditOverTheOriginalWorksheetValue() throws Exception {
      Harness h = new Harness();
      h.stateMap.put(new CellRef("qty", 2), 99);

      Map<String, Object> result = h.set("Table1", change(1, 1, "21"));

      assertEquals("99", changes(result).get(0).get("previous"));
   }

   @Test
   void previousIsNullForAnEmptyOriginalCell() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1", change(0, 4, "1.5"));

      assertNull(changes(result).get(0).get("previous"));
   }

   @Test
   void aNullOrOmittedValueClearsTheCellByStringAsEmptyText() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1", change(1, 1, null), change(0, 0, null));

      List<ChangeVSTableCellsTextEvent.TableCellTextChange> sent = h.sentChanges();
      assertEquals("", sent.get(0).getText());
      assertEquals("", sent.get(1).getText());
      assertNull(changes(result).get(0).get("value"));
      assertNull(changes(result).get(1).get("value"));
   }

   @Test
   void theStringNullIsLiteralTextInAStringColumn() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1", change(0, 0, "null"));

      assertEquals("null", h.sentChanges().get(0).getText());
      assertEquals("null", changes(result).get(0).get("value"));
   }

   /**
    * The native {@code updateCell} reads "" as a cleared cell for every column type, so "" in a
    * string column clears it. The response says so by echoing a null value.
    */
   @Test
   void anEmptyStringInAStringColumnIsSentAsTheNativeClear() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1", change(0, 0, ""));

      assertEquals("", h.sentChanges().get(0).getText());
      assertNull(changes(result).get(0).get("value"));
   }

   @Test
   void aMultiCellBatchIsOneNativeEventWithCanonicalTextAndRawIndexes() throws Exception {
      Harness h = new Harness();

      Map<String, Object> result = h.set("Table1",
         change(0, 0, "Q1"), change(1, 1, " 7 "), change(2, 2, "2026-03-15"),
         change(0, 3, "TRUE"), change(2, 4, "2.50"));

      ArgumentCaptor<ChangeVSTableCellsTextEvent> captor =
         ArgumentCaptor.forClass(ChangeVSTableCellsTextEvent.class);
      verify(h.composer, times(1)).changeCellText(eq("rt1"), captor.capture(), eq(h.user),
                                                  eq("lnk"), any());
      assertEquals("Table1", captor.getValue().getAssemblyName());

      List<ChangeVSTableCellsTextEvent.TableCellTextChange> sent = h.sentChanges();
      assertEquals(5, sent.size());
      int[][] at = { {0, 0}, {1, 1}, {2, 2}, {0, 3}, {2, 4} };
      String[] text = { "Q1", "7", "2026-03-15", "true", "2.50" };

      for(int i = 0; i < 5; i++) {
         assertEquals(at[i][0], sent.get(i).getRow(), "row " + i);
         assertEquals(at[i][1], sent.get(i).getCol(), "col " + i);
         assertEquals(text[i], sent.get(i).getText(), "text " + i);
      }

      assertEquals("Table1", result.get("assembly"));
      assertEquals(5, result.get("applied"));
      assertEquals(true, result.get("submitOnChange"));
      assertFalse(result.containsKey("warnings"));
      verify(h.sessions, times(1)).mutate(eq("tok"), eq(h.user), any());
   }

   @Test
   void reportsTheSubmitOnChangeFlagAndRelaysWarnings() throws Exception {
      Harness h = new Harness();
      when(h.info.isSubmitOnChange()).thenReturn(false);
      h.warnings = List.of("Text was limited to 10 characters.");

      Map<String, Object> result = h.set("Table1", change(0, 0, "x"));

      assertEquals(false, result.get("submitOnChange"));
      assertEquals(List.of("Text was limited to 10 characters."), result.get("warnings"));
   }

   // ── fixtures ─────────────────────────────────────────────────────────────

   private static CellChange change(int row, int col, String value) {
      return new CellChange(row, col, value);
   }

   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> changes(Map<String, Object> result) {
      return (List<Map<String, Object>>) result.get("changes");
   }

   private static final class Harness {
      final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      final ComposerVSTableServiceProxy composer = mock(ComposerVSTableServiceProxy.class);
      final Principal user = mock(Principal.class);
      final EmbeddedTableVSAssemblyInfo info = mock(EmbeddedTableVSAssemblyInfo.class);
      final EmbeddedTableVSAssembly table = mock(EmbeddedTableVSAssembly.class);
      final Worksheet worksheet = mock(Worksheet.class);
      final Map<CellRef, Object> stateMap = new HashMap<>();
      final EmbeddedTableCellService service;
      List<String> warnings = List.of();

      Harness() throws Exception {
         this(null);
         assembly = table;
         wire();
      }

      Harness(VSAssembly other) throws Exception {
         assembly = other;
         service = new EmbeddedTableCellService(sessions, composer);
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenAnswer(inv -> assembly);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            mutation.run(rvs, "rt1", mock(CapturingCommandDispatcher.class));
            return warnings;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }

      private void wire() {
         String[] types = { "string", "integer", "date", "boolean", "double" };
         String[] names = { "name", "qty", "due", "flag", "ratio" };
         Object[][] rows = {
            names,
            { "A", 10, null, null, null },
            { "B", 20, null, null, null },
            { "C", 30, null, null, null }
         };
         XEmbeddedTable original = new XEmbeddedTable(types.clone(), rows);
         XEmbeddedTable current = new XEmbeddedTable(types.clone(), rows);
         ColumnSelection columns = new ColumnSelection();

         for(int i = 0; i < names.length; i++) {
            ColumnRef ref = new ColumnRef(new AttributeRef(null, names[i]));
            ref.setDataType(types[i]);
            columns.addAttribute(ref);
         }

         EmbeddedTableAssembly source = mock(EmbeddedTableAssembly.class);
         when(source.getOriginalEmbeddedData()).thenReturn(original);
         when(source.getEmbeddedData()).thenReturn(current);
         when(source.getColumnSelection(false)).thenReturn(columns);
         when(worksheet.getAssembly("WS")).thenReturn(source);

         when(info.isForm()).thenReturn(false);
         when(info.isSubmitOnChange()).thenReturn(true);
         when(table.getVSAssemblyInfo()).thenReturn(info);
         when(table.getVisibleColumns()).thenReturn(columns);
         when(table.getWorksheet()).thenReturn(worksheet);
         when(table.getTableName()).thenReturn("WS");
         when(table.getStateDataMap()).thenReturn(stateMap);
      }

      Map<String, Object> set(String name, CellChange... changes) throws Exception {
         return service.setCells("tok", user, name, Arrays.asList(changes), "lnk");
      }

      List<ChangeVSTableCellsTextEvent.TableCellTextChange> sentChanges() throws Exception {
         ArgumentCaptor<ChangeVSTableCellsTextEvent> captor =
            ArgumentCaptor.forClass(ChangeVSTableCellsTextEvent.class);
         verify(composer).changeCellText(anyString(), captor.capture(), any(), any(), any());
         return Arrays.asList(captor.getValue().changes());
      }

      /** A refused call neither reached the native service nor opened an undo/broadcast step. */
      void assertNothingWritten() throws Exception {
         verifyNoInteractions(composer);
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      }

      private VSAssembly assembly;
   }
}
