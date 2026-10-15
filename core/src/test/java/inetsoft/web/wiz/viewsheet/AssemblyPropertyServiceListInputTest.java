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
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.CheckboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.ComboBoxEditorModel;
import inetsoft.web.composer.model.vs.ComboboxPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78470: the couplings and checks {@link ListInputEditorGuard} applies to a
 * CheckBox/ComboBox/RadioButton editor written through {@code set_assembly_properties}, at model
 * level (the dialog service is a mock). The real {@code VSInputService} getter/setter runs in
 * {@link AssemblyPropertyServiceListInputRealStackTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceListInputTest {
   // ── dataType sync (the dialog's updateType) ─────────────────────────────

   /** VFI-011's one-call patch: the list type follows the editor, the bounds stay. */
   @Test
   void syncsTheListDataTypeOnAOneCallCalendarPatch() {
      for(String editor : new String[] { E, CAPITALISED_E }) {
         ComboboxPropertyDialogModel model = combobox("string", false, "", "");

         set(model, Map.of(editor + ".calendar", true, editor + ".dataType", "date",
                           editor + ".minDate", "2024-01-01", editor + ".maxDate", "2025-10-31"));

         ComboBoxEditorModel ed = editor(model);
         assertEquals("date", ed.getVariableListDialogModel().getDataType(), editor);
         assertEquals("2024-01-01", ed.getMinDate(), editor);
         assertEquals("2025-10-31", ed.getMaxDate(), editor);
         assertTrue(ed.isCalendar(), editor);
      }
   }

   /** A list dataType the patch writes itself is kept, even when it equals the stored one. */
   @Test
   void keepsAListDataTypeWrittenInTheSamePatch() {
      ComboboxPropertyDialogModel model = combobox("string", false, "", "");

      set(model, Map.of(E + ".dataType", "date",
                        E + ".variableListDialogModel.dataType", "string"));

      assertEquals("date", editor(model).getDataType());
      assertEquals("string", editor(model).getVariableListDialogModel().getDataType());
   }

   /** A CheckBox editor dataType change syncs the list type and is strict-checked against it. */
   @Test
   void syncsACheckboxListTypeAndChecksTheValuesAgainstIt() {
      CheckboxPropertyDialogModel ok = checkbox("1", "2");
      setCheckbox(ok, Map.of(CHECKBOX_E + ".dataType", "integer"));
      assertEquals("integer", ok.getCheckboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getVariableListDialogModel().getDataType());

      CheckboxPropertyDialogModel bad = checkbox("1", "abc");
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setCheckbox(bad, Map.of(CHECKBOX_E + ".dataType", "integer")));
      assertTrue(e.getMessage().contains("'abc'"), e.getMessage());
   }

   @Test
   void aDataTypeChangeToStringClearsTheBoundsAndCalendar() {
      ComboboxPropertyDialogModel model = combobox("date", true, "2024-01-01", "2024-12-31");

      set(model, Map.of(E + ".dataType", "string"));

      ComboBoxEditorModel ed = editor(model);
      assertEquals("string", ed.getVariableListDialogModel().getDataType());
      assertEquals("", ed.getMinDate());
      assertEquals("", ed.getMaxDate());
      assertFalse(ed.isCalendar());
   }

   @Test
   void aDataTypeChangeToTimeInstantClearsTheBoundsAndTurnsServerTimeZoneOn() {
      ComboboxPropertyDialogModel model = combobox("date", true, "2024-01-01", "2024-12-31");

      set(model, Map.of(E + ".dataType", "timeInstant"));

      ComboBoxEditorModel ed = editor(model);
      assertEquals("", ed.getMinDate());
      assertEquals("", ed.getMaxDate());
      assertTrue(ed.isCalendar());
      assertTrue(ed.isServerTZ());
   }

   /** An explicit serverTZ is kept even when it equals the stored value (refute C1). */
   @Test
   void keepsAServerTimeZoneWrittenInTheSamePatch() {
      ComboboxPropertyDialogModel model = combobox("date", false, "", "");

      set(model, Map.of(E + ".dataType", "timeInstant", E + ".serverTZ", false));

      assertFalse(editor(model).isServerTZ());
   }

   /**
    * A combo left drifted by the old write path (reads "string" with calendar on and bounds
    * stored) is repaired by re-sending the dataType with its bounds: the bounds the patch
    * writes are kept even though they equal the stored ones (refute C1/C2).
    */
   @Test
   void repairsADriftedComboWhenTheBoundsAreReSentWithTheDataType() {
      ComboboxPropertyDialogModel model = combobox("string", true, "2024-01-01", "2025-10-31");

      set(model, Map.of(E + ".dataType", "date", E + ".minDate", "2024-01-01",
                        E + ".maxDate", "2025-10-31"));

      ComboBoxEditorModel ed = editor(model);
      assertEquals("date", ed.getVariableListDialogModel().getDataType());
      assertEquals("2024-01-01", ed.getMinDate());
      assertEquals("2025-10-31", ed.getMaxDate());
      assertTrue(ed.isCalendar());
   }

   /** The dataType alone repairs the type, but clears the bounds, as the dialog does. */
   @Test
   void reSendingOnlyTheDataTypeClearsTheBoundsOfADriftedCombo() {
      ComboboxPropertyDialogModel model = combobox("string", true, "2024-01-01", "2025-10-31");

      set(model, Map.of(E + ".dataType", "date"));

      assertEquals("", editor(model).getMinDate());
      assertEquals("", editor(model).getMaxDate());
   }

   // ── bound values ────────────────────────────────────────────────────────

   @Test
   void refusesMalformedOrImpossibleBoundsNamingTheFieldAndFormat() throws Exception {
      String[][] cases = {
         { "date", "minDate", "not-a-date", "yyyy-MM-dd" },
         { "date", "minDate", "05/12/2024", "yyyy-MM-dd" },
         { "date", "maxDate", "2024-02-30", "yyyy-MM-dd" },
         { "date", "minDate", "2024-01-01 10:00:00", "yyyy-MM-dd" },
         { "date", "minDate", "$notAVariable", "yyyy-MM-dd" },
         { "timeInstant", "minDate", "2024-01-01", "yyyy-MM-dd HH:mm:ss" },
         { "timeInstant", "maxDate", "2024-01-01 25:00:00", "yyyy-MM-dd HH:mm:ss" },
      };

      for(String[] c : cases) {
         ComboboxPropertyDialogModel model = combobox("string", false, "", "");
         Map<String, Object> patch = new LinkedHashMap<>();
         patch.put(E + ".calendar", true);
         patch.put(E + ".dataType", c[0]);
         patch.put(E + "." + c[1], c[2]);

         Exception e = assertThrows(IllegalArgumentException.class, () -> set(model, patch),
                                    String.join(" ", c));

         assertTrue(e.getMessage().contains(E + "." + c[1]), e.getMessage());
         assertTrue(e.getMessage().contains("(" + c[2] + ")"), e.getMessage());
         assertTrue(e.getMessage().contains(c[3]), e.getMessage());
         verifyNothingWritten();
      }
   }

   @Test
   void storesValidBoundsZeroPadded() {
      ComboboxPropertyDialogModel date = combobox("string", false, "", "");
      set(date, Map.of(E + ".calendar", true, E + ".dataType", "date",
                       E + ".minDate", "2024-1-5", E + ".maxDate", "2024-12-31"));
      assertEquals("2024-01-05", editor(date).getMinDate());

      ComboboxPropertyDialogModel instant = combobox("string", false, "", "");
      set(instant, Map.of(E + ".calendar", true, E + ".dataType", "timeInstant",
                          E + ".minDate", "2024-01-01  9:5:0",
                          E + ".maxDate", "2024-01-01 23:59:59"));
      assertEquals("2024-01-01 09:05:00", editor(instant).getMinDate());
      assertEquals("2024-01-01 23:59:59", editor(instant).getMaxDate());
   }

   @Test
   void refusesInvertedBoundsAndAcceptsEqualOnes() throws Exception {
      ComboboxPropertyDialogModel inverted = combobox("date", true, "", "");
      Exception e = assertThrows(IllegalArgumentException.class, () -> set(inverted, Map.of(
         E + ".minDate", "2025-10-31", E + ".maxDate", "2024-01-01")));
      assertTrue(e.getMessage().contains("after maxDate"), e.getMessage());
      verifyNothingWritten();

      ComboboxPropertyDialogModel equal = combobox("date", true, "", "");
      set(equal, Map.of(E + ".minDate", "2024-01-01", E + ".maxDate", "2024-01-01"));
      assertEquals("2024-01-01", editor(equal).getMaxDate());
   }

   @Test
   void passesVariableAndScriptBoundsThrough() {
      ComboboxPropertyDialogModel model = combobox("date", true, "", "");

      set(model, Map.of(E + ".minDate", "$(start)", E + ".maxDate", "=today()"));

      assertEquals("$(start)", editor(model).getMinDate());
      assertEquals("=today()", editor(model).getMaxDate());
   }

   /** Lead decision C3: a legacy malformed bound does not block a write that leaves it alone. */
   @Test
   void leavesALegacyMalformedBoundAloneForAnUnrelatedWrite() {
      ComboboxPropertyDialogModel model = combobox("date", true, "05/12/2024", "2024-02-30");

      set(model, Map.of("visible", "false"));

      assertEquals("05/12/2024", editor(model).getMinDate());
   }

   /** ...but a write of the calendar flag puts the stored bounds back in play. */
   @Test
   void checksAStoredBoundWhenThePatchWritesTheCalendarFlag() {
      ComboboxPropertyDialogModel model = combobox("date", false, "05/12/2024", "");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(model, Map.of(E + ".calendar", true)));

      assertTrue(e.getMessage().contains(E + ".minDate"), e.getMessage());
   }

   // ── bounds / calendar the store would drop (refute O2) ─────────────────

   @Test
   void refusesABoundOnANonDateCombo() throws Exception {
      for(String type : new String[] { "string", "time" }) {
         ComboboxPropertyDialogModel model = combobox(type, false, "", "");

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> set(model, Map.of(E + ".minDate", "2024-01-01")));

         assertTrue(e.getMessage().contains("would be dropped"), e.getMessage());
         assertTrue(e.getMessage().contains(E + ".minDate"), e.getMessage());
         verifyNothingWritten();
      }
   }

   @Test
   void refusesCalendarModeOnANonDateCombo() throws Exception {
      ComboboxPropertyDialogModel model = combobox("string", false, "", "");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(model, Map.of(E + ".calendar", true)));

      assertTrue(e.getMessage().contains(E + ".dataType"), e.getMessage());
      verifyNothingWritten();
   }

   /** An explicit calendar:true is refused, not silently turned off by the sync (refute C1). */
   @Test
   void refusesAnExplicitCalendarWithAStringDataTypeEvenWhenUnchanged() {
      ComboboxPropertyDialogModel model = combobox("date", true, "", "");

      assertThrows(IllegalArgumentException.class,
                   () -> set(model, Map.of(E + ".dataType", "string", E + ".calendar", true)));
   }

   /** A combo already reading "string" with calendar on is not blocked on an unrelated write. */
   @Test
   void leavesALegacyStringCalendarComboAloneForAnUnrelatedWrite() {
      ComboboxPropertyDialogModel model = combobox("string", true, "2024-01-01", "");

      assertDoesNotThrow(() -> set(model, Map.of("visible", "false")));
   }

   // ── calendar with a query-populated list (refute O1) ───────────────────

   @Test
   void refusesCalendarModeTogetherWithAQueryList() throws Exception {
      ComboboxPropertyDialogModel query = combobox("date", false, "", "");
      editor(query).setQuery(true);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> set(query, Map.of(E + ".calendar", true)));
      assertTrue(e.getMessage().contains("query:false"), e.getMessage());
      verifyNothingWritten();

      ComboboxPropertyDialogModel calendar = combobox("date", true, "", "");
      assertThrows(IllegalArgumentException.class, () -> set(calendar, Map.of("query", true)));
      verifyNothingWritten();

      ComboboxPropertyDialogModel both = combobox("date", false, "", "");
      editor(both).setQuery(true);
      set(both, Map.of(E + ".calendar", true, "query", false));
      assertFalse(editor(both).isQuery());
   }

   @Test
   void leavesALegacyCalendarQueryComboAloneForAnUnrelatedWrite() {
      ComboboxPropertyDialogModel model = combobox("date", true, "", "");
      editor(model).setQuery(true);

      assertDoesNotThrow(() -> set(model, Map.of("visible", "false")));
   }

   // ── harness ─────────────────────────────────────────────────────────────

   static final String E =
      "comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel";
   private static final String CAPITALISED_E =
      "ComboboxGeneralPaneModel.ListValuesPaneModel.ComboBoxEditorModel";
   private static final String CHECKBOX_E =
      "checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel";

   private static VSInputService lastInputService;

   private static ComboboxPropertyDialogModel combobox(String dataType, boolean calendar,
                                                       String minDate, String maxDate)
   {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      ComboBoxEditorModel editor = editor(model);
      editor.setDataType(dataType);
      editor.getVariableListDialogModel().setDataType(dataType);
      editor.setCalendar(calendar);
      editor.setMinDate(minDate);
      editor.setMaxDate(maxDate);
      return model;
   }

   private static CheckboxPropertyDialogModel checkbox(String... values) {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      ComboBoxEditorModel editor =
         model.getCheckboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel();
      editor.setDataType("string");
      editor.setEmbedded(true);
      editor.getVariableListDialogModel().setDataType("string");
      editor.getVariableListDialogModel().setLabels(values);
      editor.getVariableListDialogModel().setValues(values);
      return model;
   }

   private static ComboBoxEditorModel editor(ComboboxPropertyDialogModel model) {
      return model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel();
   }

   private static void set(ComboboxPropertyDialogModel model, Map<String, Object> patch) {
      VSInputService input = mock(VSInputService.class);

      try {
         when(input.getComboboxPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }

      run(service(mock(ComboBoxVSAssembly.class), input), "ComboBox1", patch);
   }

   private static void setCheckbox(CheckboxPropertyDialogModel model, Map<String, Object> patch) {
      VSInputService input = mock(VSInputService.class);

      try {
         when(input.getCheckBoxPropertyModel(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }

      run(service(mock(inetsoft.uql.viewsheet.CheckBoxVSAssembly.class), input), "CheckBox1",
          patch);
   }

   private static void run(AssemblyPropertyService service, String name,
                           Map<String, Object> patch)
   {
      try {
         service.set("tok", principal(), name, patch, "");
      }
      catch(RuntimeException ex) {
         throw ex;
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private static void verifyNothingWritten() throws Exception {
      verify(lastInputService, never())
         .setComboboxPropertyDialogModel(any(), any(), any(), any(), any(), any());
   }

   private static AssemblyPropertyService service(VSAssembly assembly, VSInputService input) {
      lastInputService = input;
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      when(vs.getBaseWorksheet()).thenReturn(new Worksheet());
      when(vs.getAllVariables()).thenReturn(new inetsoft.uql.schema.UserVariable[0]);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");
      return service(rvs, input);
   }

   static AssemblyPropertyService service(RuntimeViewsheet rvs, VSInputService input) {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new AssemblyPropertyService(
         sessions, mock(GaugePropertyDialogService.class), mock(ImagePropertyDialogService.class),
         mock(TextPropertyDialogService.class), mock(ChartPropertyDialogService.class),
         mock(TableViewPropertyDialogService.class), mock(CrosstabPropertyDialogService.class),
         mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class), input,
         mock(RangeSliderPropertyDialogService.class), mock(CalendarPropertyDialogService.class),
         mock(TabPropertyDialogService.class), mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class), mock(LinePropertyDialogService.class),
         mock(OvalPropertyDialogService.class), mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
         mock(VSColumnHandler.class), mock(DataOutputService.class));
   }

   static Principal principal() {
      return () -> "admin";
   }
}
