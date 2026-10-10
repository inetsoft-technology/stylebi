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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.ColumnOptionDialogModel;
import inetsoft.web.composer.model.vs.ComboBoxEditorModel;
import inetsoft.web.composer.model.vs.DateEditorModel;
import inetsoft.web.composer.model.vs.EditorModel;
import inetsoft.web.composer.model.vs.FloatEditorModel;
import inetsoft.web.composer.model.vs.IntegerEditorModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;
import inetsoft.web.composer.model.vs.SelectionListDialogModel;
import inetsoft.web.composer.model.vs.SelectionListEditorModel;
import inetsoft.web.composer.model.vs.TextEditorModel;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code ColumnOptionService} is the wiz-agent bridge onto
 * {@code VSInputService.getColumnOptionDialogModel}/{@code setColumnOptionDialogModel} -- the
 * same native methods {@code ColumnOptionDialogController} calls for the Composer's own
 * column-header right-click "Column Options" dialog.
 */
@Tag("core")
class ColumnOptionServiceTest {
   // ── shared refusals ──────────────────────────────────────────────────────

   @Test
   void refusesAnUnknownAssembly() throws Exception {
      Harness h = harnessWith(null, columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Nope", 0));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesANonTableAssembly() throws Exception {
      VSAssembly notATable = mock(VSAssembly.class);
      Harness h = harnessWith(notATable, columns("STATE"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Gauge1", 0));

      assertTrue(e.getMessage().contains("not a Table assembly"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   // ── col resolution ────────────────────────────────────────────────────────

   @Test
   void resolvesColByVisibleIndex() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", 1);

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   @Test
   void resolvesColByColumnName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", "REGION");

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   @Test
   void refusesAnUnknownColumnName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Table1", "NOPE"));

      assertTrue(e.getMessage().contains("NOPE"), e.getMessage());
      assertTrue(e.getMessage().contains("STATE"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesAnOutOfRangeIndex() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.get("tok", principal(), "Table1", 5));

      assertTrue(e.getMessage().contains("out of range"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /**
    * A numeric-looking string is treated as an index, not a name -- matches
    * {@code requireInBounds}'s digit-string branch rather than falling through to name lookup.
    */
   @Test
   void treatsADigitStringAsAnIndexNotAName() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));

      h.service.get("tok", h.user, "Table1", "1");

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 1, h.user);
   }

   // ── set ───────────────────────────────────────────────────────────────────

   @Test
   void requiresInputControlWhenEnablingColumnEditing() throws Exception {
      Harness h = harnessWith(columns("STATE"));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", principal(), "Table1", 0, true, null, null, ""));

      assertTrue(e.getMessage().contains("inputControl"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /** The bug's own repro: a Text editor with a pattern and error message. */
   @Test
   void roundTripsATextEditor() throws Exception {
      Harness h = harnessWith(columns("STATE", "REGION"));
      TextEditorModel editor = new TextEditorModel();
      editor.setPattern("^[A-Z]{2}$");
      editor.setErrorMessage("{0}");

      h.service.set("tok", h.user, "Table1", "STATE", true, "Text", editor, "");

      ArgumentCaptor<ColumnOptionDialogModel> captor =
         ArgumentCaptor.forClass(ColumnOptionDialogModel.class);
      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         captor.capture(), eq(h.user), eq(h.dispatcher), eq(""));
      assertTrue(captor.getValue().isEnableColumnEditing());
      assertEquals("Text", captor.getValue().getInputControl());
      assertSame(editor, captor.getValue().getEditor());
   }

   @Test
   void disablingColumnEditingNeedsNoInputControlOrEditor() throws Exception {
      Harness h = harnessWith(columns("STATE"));

      h.service.set("tok", h.user, "Table1", 0, false, null, null, "");

      ArgumentCaptor<ColumnOptionDialogModel> captor =
         ArgumentCaptor.forClass(ColumnOptionDialogModel.class);
      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         captor.capture(), eq(h.user), eq(h.dispatcher), eq(""));
      assertFalse(captor.getValue().isEnableColumnEditing());
   }

   // ── #1: non-form table refused ──────────────────────────────────────────

   /**
    * The native Composer only shows "Column Options" for a Form table
    * ({@code SimpleTableModel}'s {@code form = info.isForm()}); a non-form table's write did
    * not survive to the next read even before this guard (discarded, or clobbered by a later
    * form-flip) -- refusing up front, named, replaces that silent no-op.
    */
   @Test
   void refusesSetOnANonFormTable() throws Exception {
      Harness h = harnessWith(columns("STATE"), false);
      TextEditorModel editor = new TextEditorModel();
      editor.setPattern("^[A-Z]{2}$");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, "Text", editor, ""));

      assertTrue(e.getMessage().contains("not a Form table"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /** Disabling column editing is itself a write too -- refused the same way. */
   @Test
   void refusesDisablingColumnEditingOnANonFormTable() throws Exception {
      Harness h = harnessWith(columns("STATE"), false);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, false, null, null, ""));

      assertTrue(e.getMessage().contains("not a Form table"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   /** get_column_options is a read -- not gated on form status. */
   @Test
   void allowsGetOnANonFormTable() throws Exception {
      Harness h = harnessWith(columns("STATE"), false);

      h.service.get("tok", h.user, "Table1", 0);

      verify(h.inputs).getColumnOptionDialogModel("rt1", "Table1", 0, h.user);
   }

   // ── #2: unparseable Date min/max refused ────────────────────────────────

   /**
    * {@code DateColumnOption.validate()} swallows an unparseable bound's {@code ParseException}
    * into an unconditional {@code return false} -- rejecting every value forever once stored.
    * Reusing {@code Tool.parseDate} itself here (the same parser {@code validate()} runs) closes
    * this without over-rejecting a format {@code Tool.parseDate} genuinely accepts.
    */
   @Test
   void refusesAnUnparseableDateMinimum() throws Exception {
      Harness h = harnessWith(columns("ORDER_DATE"));
      DateEditorModel editor = new DateEditorModel();
      editor.setMinimum("not-a-date");
      editor.setMaximum("2030-12-31");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, "Date", editor, ""));

      assertTrue(e.getMessage().contains("minimum"), e.getMessage());
      assertTrue(e.getMessage().contains("not-a-date"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesAnUnparseableDateMaximum() throws Exception {
      Harness h = harnessWith(columns("ORDER_DATE"));
      DateEditorModel editor = new DateEditorModel();
      editor.setMaximum("also-not-a-date");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, "Date", editor, ""));

      assertTrue(e.getMessage().contains("maximum"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void acceptsAParsableDateBound() throws Exception {
      Harness h = harnessWith(columns("ORDER_DATE"));
      DateEditorModel editor = new DateEditorModel();
      editor.setMinimum("2026-01-01");
      editor.setMaximum("2026-12-31");

      h.service.set("tok", h.user, "Table1", 0, true, "Date", editor, "");

      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         any(), eq(h.user), eq(h.dispatcher), eq(""));
   }

   @Test
   void blankDateBoundsAreNotParsed() throws Exception {
      Harness h = harnessWith(columns("ORDER_DATE"));
      DateEditorModel editor = new DateEditorModel();

      h.service.set("tok", h.user, "Table1", 0, true, "Date", editor, "");

      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         any(), eq(h.user), eq(h.dispatcher), eq(""));
   }

   // ── #5: unresolvable ComboBox query source refused ──────────────────────

   @Test
   void refusesAnUnresolvableComboBoxQueryTable() throws Exception {
      Harness h = harnessWith(columns("CUSTOMER_ID"));
      when(h.vsColumnHandler.getTableColumns(any(), eq("NoSuchTable123"), eq(h.user)))
         .thenReturn(new ColumnSelection());
      ComboBoxEditorModel editor = comboBoxQuery("NoSuchTable123", "COL", "COL");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, "ComboBox", editor, ""));

      assertTrue(e.getMessage().contains("NoSuchTable123"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void refusesAComboBoxQueryColumnNotOnTheResolvedTable() throws Exception {
      Harness h = harnessWith(columns("CUSTOMER_ID"));
      when(h.vsColumnHandler.getTableColumns(any(), eq("CUSTOMERS"), eq(h.user)))
         .thenReturn(columns("CUSTOMER_ID", "COMPANY_NAME"));
      ComboBoxEditorModel editor = comboBoxQuery("CUSTOMERS", "NoSuchColumn", "CUSTOMER_ID");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, "ComboBox", editor, ""));

      assertTrue(e.getMessage().contains("NoSuchColumn"), e.getMessage());
      verifyNoInteractions(h.inputs);
   }

   @Test
   void acceptsAResolvableComboBoxQuerySource() throws Exception {
      Harness h = harnessWith(columns("CUSTOMER_ID"));
      when(h.vsColumnHandler.getTableColumns(any(), eq("CUSTOMERS"), eq(h.user)))
         .thenReturn(columns("CUSTOMER_ID", "COMPANY_NAME"));
      ComboBoxEditorModel editor = comboBoxQuery("CUSTOMERS", "COMPANY_NAME", "CUSTOMER_ID");

      h.service.set("tok", h.user, "Table1", 0, true, "ComboBox", editor, "");

      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         any(), eq(h.user), eq(h.dispatcher), eq(""));
   }

   /**
    * embedded:false, query:false is reachable through the real Composer dialog too (the two
    * checkboxes are independent, not a radio group requiring one of them) -- not refused.
    */
   @Test
   void acceptsAComboBoxWithNeitherEmbeddedNorQuery() throws Exception {
      Harness h = harnessWith(columns("CUSTOMER_ID"));
      ComboBoxEditorModel editor = new ComboBoxEditorModel();
      editor.setEmbedded(false);
      editor.setQuery(false);

      h.service.set("tok", h.user, "Table1", 0, true, "ComboBox", editor, "");

      verify(h.inputs).setColumnOptionDialogModel(eq("rt1"), eq("Table1"), eq(0),
         any(), eq(h.user), eq(h.dispatcher), eq(""));
   }

   // ── #78154 ───────────────────────────────────────────────────────────────

   private void assertRefused(Harness h, String control, EditorModel editor, String... fragments) {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service.set("tok", h.user, "Table1", 0, true, control, editor, ""));

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage());
      }

      verifyNoInteractions(h.inputs);
   }

   private static IntegerEditorModel integerBounds(Integer min, Integer max) {
      IntegerEditorModel editor = new IntegerEditorModel();
      editor.setMinimum(min);
      editor.setMaximum(max);
      return editor;
   }

   private static ComboBoxEditorModel embeddedCombo(String dataType, String[] labels,
                                                    String... values)
   {
      ComboBoxEditorModel editor = new ComboBoxEditorModel();
      editor.setEmbedded(true);
      editor.setDataType(dataType);
      VariableListDialogModel list = new VariableListDialogModel();
      list.setDataType(dataType);
      list.setLabels(labels);
      list.setValues(values);
      editor.setVariableListDialogModel(list);
      return editor;
   }

   // S2b
   @Test
   void refusesInvertedIntegerBounds() throws Exception {
      assertRefused(harnessWith(columns("A")), "Integer", integerBounds(10, 1),
                    "editor.minimum", "must not be greater than", "editor.maximum");
   }

   @Test
   void refusesInvertedFloatBounds() throws Exception {
      FloatEditorModel editor = new FloatEditorModel();
      editor.setMinimum(5f);
      editor.setMaximum(1f);
      assertRefused(harnessWith(columns("A")), "Float", editor, "editor.minimum",
                    "must not be greater than");
   }

   @Test
   void refusesInvertedDateBounds() throws Exception {
      DateEditorModel editor = new DateEditorModel();
      editor.setMinimum("2030-01-01");
      editor.setMaximum("2020-01-01");
      assertRefused(harnessWith(columns("A")), "Date", editor, "editor.minimum",
                    "must not be greater than");
   }

   @Test
   void allowsEqualAndOneSidedBounds() throws Exception {
      Harness h = harnessWith(columns("A"));
      h.service.set("tok", h.user, "Table1", 0, true, "Integer", integerBounds(5, 5), "");
      h.service.set("tok", h.user, "Table1", 0, true, "Integer", integerBounds(5, null), "");
      DateEditorModel date = new DateEditorModel();
      date.setMinimum("2026-01-01");
      date.setMaximum("2026-01-01");
      h.service.set("tok", h.user, "Table1", 0, true, "Date", date, "");
      verify(h.inputs, times(3)).setColumnOptionDialogModel(any(), any(), anyInt(), any(), any(),
                                                           any(), any());
   }

   // S3c
   @Test
   void refusesColumnComboBoxFieldsThatOnlyTheComboBoxAssemblyStores() throws Exception {
      Harness h = harnessWith(columns("A"));
      ComboBoxEditorModel calendar = new ComboBoxEditorModel();
      calendar.setCalendar(true);
      assertRefused(h, "ComboBox", calendar, "editor.calendar", "ComboBox assembly");

      ComboBoxEditorModel minDate = new ComboBoxEditorModel();
      minDate.setMinDate("2020-01-01");
      assertRefused(h, "ComboBox", minDate, "editor.minDate");

      ComboBoxEditorModel dflt = new ComboBoxEditorModel();
      dflt.setDefaultValue("x");
      assertRefused(h, "ComboBox", dflt, "editor.defaultValue");

      ComboBoxEditorModel invalid = new ComboBoxEditorModel();
      invalid.setValid(false);
      assertRefused(h, "ComboBox", invalid, "editor.valid");
   }

   // S5b
   @Test
   void refusesAComboBoxQuerySourceWithATableButNoColumn() throws Exception {
      Harness h = harnessWith(columns("A"));
      when(h.vsColumnHandler.getTableColumns(any(), eq("CUSTOMERS"), eq(h.user)))
         .thenReturn(columns("CUSTOMER_ID", "COMPANY_NAME"));

      assertRefused(h, "ComboBox", comboBoxQuery("CUSTOMERS", null, null),
                    "selectionListEditorModel.column", "table alone binds nothing");
   }

   @Test
   void defaultsTheComboBoxQueryValueToTheColumn() throws Exception {
      Harness h = harnessWith(columns("A"));
      when(h.vsColumnHandler.getTableColumns(any(), eq("CUSTOMERS"), eq(h.user)))
         .thenReturn(columns("CUSTOMER_ID", "COMPANY_NAME"));
      ComboBoxEditorModel editor = comboBoxQuery("CUSTOMERS", "COMPANY_NAME", null);

      h.service.set("tok", h.user, "Table1", 0, true, "ComboBox", editor, "");

      assertEquals("COMPANY_NAME",
         editor.getSelectionListDialogModel().getSelectionListEditorModel().getValue());
      verify(h.inputs).setColumnOptionDialogModel(any(), any(), anyInt(), any(), any(), any(),
                                                  any());
   }

   // S5c
   @Test
   void refusesAnEmbeddedValueThatIsNotOfItsDataType() throws Exception {
      Harness h = harnessWith(columns("A"));
      assertRefused(h, "ComboBox", embeddedCombo("integer", new String[0], "1", "abc"),
                    "values[1]", "'abc'", "integer");
      assertRefused(h, "ComboBox", embeddedCombo("integer", new String[0], "1.5"), "values[0]");
      assertRefused(h, "ComboBox", embeddedCombo("boolean", new String[0], "abc"), "values[0]");
      assertRefused(h, "ComboBox", embeddedCombo("byte", new String[0], "300"), "values[0]");
   }

   @Test
   void refusesEmbeddedLabelValueCountAndDataTypeMismatch() throws Exception {
      Harness h = harnessWith(columns("A"));
      assertRefused(h, "ComboBox", embeddedCombo("string", new String[]{ "a" }, "x", "y"),
                    "labels", "values");

      ComboBoxEditorModel mismatch = embeddedCombo("integer", new String[0], "1");
      mismatch.setDataType("string");
      assertRefused(h, "ComboBox", mismatch, "dataType", "disagrees");
   }

   @Test
   void allowsValidEmbeddedValuesAndTheDeliberateNull() throws Exception {
      Harness h = harnessWith(columns("A"));
      h.service.set("tok", h.user, "Table1", 0, true, "ComboBox",
                    embeddedCombo("integer", new String[]{ "a", "b", "c" }, "1", "2", "__null__"),
                    "");
      verify(h.inputs).setColumnOptionDialogModel(any(), any(), anyInt(), any(), any(), any(),
                                                  any());
   }

   // ── fixtures ──────────────────────────────────────────────────────────────

   private record Harness(ColumnOptionService service, VSInputService inputs,
                          VSColumnHandler vsColumnHandler, CapturingCommandDispatcher dispatcher,
                          Principal user) {}

   private static ColumnSelection columns(String... names) {
      ColumnSelection selection = new ColumnSelection();

      for(String name : names) {
         selection.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      return selection;
   }

   private static ComboBoxEditorModel comboBoxQuery(String table, String column, String value) {
      ComboBoxEditorModel editor = new ComboBoxEditorModel();
      editor.setEmbedded(false);
      editor.setQuery(true);
      SelectionListEditorModel source = new SelectionListEditorModel();
      source.setTable(table);
      source.setColumn(column);
      source.setValue(value);
      SelectionListDialogModel dialog = new SelectionListDialogModel();
      dialog.setSelectionListEditorModel(source);
      editor.setSelectionListDialogModel(dialog);
      return editor;
   }

   private static TableVSAssembly tableWith(ColumnSelection visible, boolean form) {
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(info.getVisibleColumns()).thenReturn(visible);
      when(info.isForm()).thenReturn(form);
      return assembly;
   }

   private static Harness harnessWith(ColumnSelection visible) throws Exception {
      return harnessWith(tableWith(visible, true), visible);
   }

   /** {@code form} controls {@code TableVSAssemblyInfo.isForm()} -- default fixture is true. */
   private static Harness harnessWith(ColumnSelection visible, boolean form) throws Exception {
      return harnessWith(tableWith(visible, form), visible);
   }

   private static Harness harnessWith(VSAssembly assembly, ColumnSelection visible) throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      VSInputService inputs = mock(VSInputService.class);
      VSColumnHandler vsColumnHandler = mock(VSColumnHandler.class);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      Principal user = principal();

      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", dispatcher);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      doAnswer(invocation -> {
         ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
         return read.run(rvs, "rt1", dispatcher);
      }).when(sessions).read(anyString(), any(Principal.class), any());

      return new Harness(new ColumnOptionService(sessions, inputs, vsColumnHandler), inputs,
         vsColumnHandler, dispatcher, user);
   }

   private static Principal principal() {
      return mock(Principal.class);
   }
}
