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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.CalendarPropertyDialogService;
import inetsoft.web.composer.vs.dialog.DataOutputService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionTreePropertyDialogService;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SelectionBindingServiceTest {
   @Test
   void bindsASelectionListToOneColumn() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(new SelectionListPropertyDialogModel());

      Map<String, Object> result = harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, null, null,
                   null, null, false, "");

      ArgumentCaptor<SelectionListPropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), captor.capture(), eq(""), any(), any());
      SelectionListPaneModel pane = captor.getValue().getSelectionListPaneModel();
      assertEquals("ORDERS", pane.getSelectedTable());
      assertEquals("STATE", pane.getSelectedColumn().getAttribute());
      assertEquals("ORDERS", result.get("table"));
      assertEquals(List.of("STATE"), result.get("columns"));
   }

   @Test
   void refusesTwoColumnsOnASelectionList() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, listService, null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE", "CITY"), null,
                      null, null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("selection list"));
   }

   @Test
   void bindsASelectionTreeWithOrderedLevels() throws Exception {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      harness(assembly, null, treeService, null, null)
         .setSource("tok", principal(), "Tree1", "ORDERS", List.of("STATE", "CITY"), null, null,
                   null, null, null, false, "");

      ArgumentCaptor<SelectionTreePropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionTreePropertyDialogModel.class);
      verify(treeService).setSelectionTreePropertyModel(
         eq("rt1"), eq("Tree1"), captor.capture(), eq(""), any(), any());
      SelectionTreePaneModel pane = captor.getValue().getSelectionTreePaneModel();
      OutputColumnRefModel[] levels = pane.getSelectedColumns();
      assertEquals(2, levels.length);
      assertEquals("STATE", levels[0].getAttribute());
      assertEquals("CITY", levels[1].getAttribute());
      assertEquals(SelectionTreeVSAssemblyInfo.COLUMN, pane.getMode());
   }

   @Test
   void refusesZeroColumnsOnASelectionTree() {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, mock(SelectionTreePropertyDialogService.class), null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", List.of(), null, null, null, null,
                      null, false, ""));

      assertTrue(thrown.getMessage().contains("at least one column"));
   }

   // ── ID-hierarchy mode (regression for Bug #76768) ───────────────────────────

   @Test
   void bindsASelectionTreeInIdHierarchyMode() throws Exception {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      Map<String, Object> result = harness(assembly, null, treeService, null, null)
         .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "CITY", "STATE",
                   "ORDER_DATE", false, "");

      ArgumentCaptor<SelectionTreePropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionTreePropertyDialogModel.class);
      verify(treeService).setSelectionTreePropertyModel(
         eq("rt1"), eq("Tree1"), captor.capture(), eq(""), any(), any());
      SelectionTreePaneModel pane = captor.getValue().getSelectionTreePaneModel();
      assertEquals(SelectionTreeVSAssemblyInfo.ID, pane.getMode());
      assertEquals("CITY", pane.getParentId());
      assertEquals("STATE", pane.getId());
      assertEquals("ORDER_DATE", pane.getLabel());
      assertEquals("CITY", pane.getParentIdRef().getAttribute());
      assertEquals("STATE", pane.getIdRef().getAttribute());
      assertEquals("ORDER_DATE", pane.getLabelRef().getAttribute());
      assertEquals("CITY", result.get("parentIdColumn"));
      assertEquals("STATE", result.get("idColumn"));
      assertEquals("ORDER_DATE", result.get("labelColumn"));
   }

   @Test
   void resolvesMixedCaseIdModeColumnsToTheirCanonicalNames() throws Exception {
      // Bug #76747 fixed this exact class of bug for additionalTables: a case-mismatched literal
      // (e.g. "city" for a real "CITY" column) must persist the canonical DB casing, not the raw
      // caller input, in both the DynamicValue-backed parentId/id/label strings and the response
      // -- SelectionTreeVSAQuery2.refreshSelectionValue0 matches those strings against dataRefs
      // (always canonical) with a case-sensitive Tool.equals, so a raw, wrong-case value would
      // silently render an empty or broken tree despite set_selection_source reporting success.
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      Map<String, Object> result = harness(assembly, null, treeService, null, null)
         .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "city", "state",
                   "order_date", false, "");

      ArgumentCaptor<SelectionTreePropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionTreePropertyDialogModel.class);
      verify(treeService).setSelectionTreePropertyModel(
         eq("rt1"), eq("Tree1"), captor.capture(), eq(""), any(), any());
      SelectionTreePaneModel pane = captor.getValue().getSelectionTreePaneModel();
      assertEquals("CITY", pane.getParentId());
      assertEquals("STATE", pane.getId());
      assertEquals("ORDER_DATE", pane.getLabel());
      assertEquals("CITY", pane.getParentIdRef().getAttribute());
      assertEquals("STATE", pane.getIdRef().getAttribute());
      assertEquals("ORDER_DATE", pane.getLabelRef().getAttribute());
      assertEquals("CITY", result.get("parentIdColumn"));
      assertEquals("STATE", result.get("idColumn"));
      assertEquals("ORDER_DATE", result.get("labelColumn"));
   }

   @Test
   void refusesADynamicReferenceOnAnIdModeColumn() {
      // #76768: unlike `measure`, an id/parentId/label column cannot support "$(ComponentName)"
      // -- SelectionTreeVSAQuery2.refreshSelectionValue0 matches these columns by name against
      // assembly.getDataRefs(), which never contains an entry for a dynamic (non-literal) value,
      // so the tree would silently render empty/broken rather than actually swap. Refused loudly
      // instead, naming the field.
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, treeService, null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "CITY", "STATE",
                      "$(RadioButton1)", false, ""));

      assertTrue(thrown.getMessage().contains("labelColumn"));
      assertTrue(thrown.getMessage().contains("does not accept"));
      verifyNoInteractions(treeService);
   }

   @Test
   void refusesPartialIdModeColumns() {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, mock(SelectionTreePropertyDialogService.class), null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "CITY", "STATE",
                      null, false, ""));

      assertTrue(thrown.getMessage().contains("idColumn"));
      assertTrue(thrown.getMessage().contains("labelColumn"));
   }

   @Test
   void refusesCombiningColumnsWithIdModeColumns() {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, mock(SelectionTreePropertyDialogService.class), null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", List.of("STATE"), null, null,
                      "CITY", "STATE", "ORDER_DATE", false, ""));

      assertTrue(thrown.getMessage().contains("columns"));
   }

   @Test
   void refusesAnUnknownIdModeColumnNamingWhatIsAvailable() throws Exception {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, treeService, null, null)
            .setSource("tok", principal(), "Tree1", "ORDERS", null, null, null, "CITY",
                      "NO_SUCH_COLUMN", "ORDER_DATE", false, ""));

      assertTrue(thrown.getMessage().contains("NO_SUCH_COLUMN"));
      assertTrue(thrown.getMessage().contains("STATE"));
   }

   @Test
   void refusesIdModeColumnsOnANonTreeAssembly() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      // A selection list has no ID-hierarchy mode. parentIdColumn/idColumn/labelColumn take the
      // place of 'columns' in the request, so a selection list given these instead sees an empty
      // 'columns' and is refused the same way a selection list given zero columns always was --
      // the arity check below has no assembly-type awareness of idMode, and does not need one.
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", null, null, null, "CITY", "STATE",
                      "ORDER_DATE", false, ""));

      assertTrue(thrown.getMessage().contains("selection list"));
   }

   @Test
   void aSingleColumnTimeSliderBecomesASingleRange() throws Exception {
      TimeSliderVSAssembly assembly = mock(TimeSliderVSAssembly.class);
      RangeSliderPropertyDialogService sliderService = mock(RangeSliderPropertyDialogService.class);
      when(sliderService.getRangeSliderPropertyModel(eq("rt1"), eq("Slider1"), any()))
         .thenReturn(new RangeSliderPropertyDialogModel());

      Map<String, Object> result = harness(assembly, null, null, sliderService, null)
         .setSource("tok", principal(), "Slider1", "ORDERS", List.of("AMOUNT"), null, null, null,
                   null, null, false, "");

      ArgumentCaptor<RangeSliderPropertyDialogModel> captor =
         ArgumentCaptor.forClass(RangeSliderPropertyDialogModel.class);
      verify(sliderService).setRangeSliderPropertyModel(
         eq("rt1"), eq("Slider1"), captor.capture(), eq(""), any(), any());
      RangeSliderDataPaneModel pane = captor.getValue().getRangeSliderDataPaneModel();
      assertFalse(pane.isComposite());
      assertEquals(1, pane.getSelectedColumns().length);
      assertEquals(Boolean.FALSE, result.get("composite"));
   }

   @Test
   void aMultiColumnTimeSliderBecomesComposite() throws Exception {
      TimeSliderVSAssembly assembly = mock(TimeSliderVSAssembly.class);
      RangeSliderPropertyDialogService sliderService = mock(RangeSliderPropertyDialogService.class);
      when(sliderService.getRangeSliderPropertyModel(eq("rt1"), eq("Slider1"), any()))
         .thenReturn(new RangeSliderPropertyDialogModel());

      Map<String, Object> result = harness(assembly, null, null, sliderService, null)
         .setSource("tok", principal(), "Slider1", "ORDERS", List.of("STATE", "AMOUNT"), null,
                   null, null, null, null, false, "");

      ArgumentCaptor<RangeSliderPropertyDialogModel> captor =
         ArgumentCaptor.forClass(RangeSliderPropertyDialogModel.class);
      verify(sliderService).setRangeSliderPropertyModel(
         eq("rt1"), eq("Slider1"), captor.capture(), eq(""), any(), any());
      RangeSliderDataPaneModel pane = captor.getValue().getRangeSliderDataPaneModel();
      assertTrue(pane.isComposite());
      assertEquals(2, pane.getSelectedColumns().length);
      assertEquals(Boolean.TRUE, result.get("composite"));
   }

   @Test
   void bindsACalendarToOneColumn() throws Exception {
      CalendarVSAssembly assembly = mock(CalendarVSAssembly.class);
      CalendarPropertyDialogService calendarService = mock(CalendarPropertyDialogService.class);
      when(calendarService.getCalendarPropertyModel(eq("rt1"), eq("Calendar1"), any()))
         .thenReturn(new CalendarPropertyDialogModel());

      harness(assembly, null, null, null, calendarService)
         .setSource("tok", principal(), "Calendar1", "ORDERS", List.of("ORDER_DATE"), null, null,
                   null, null, null, false, "");

      ArgumentCaptor<CalendarPropertyDialogModel> captor =
         ArgumentCaptor.forClass(CalendarPropertyDialogModel.class);
      verify(calendarService).setCalendarPropertyModel(
         eq("rt1"), eq("Calendar1"), captor.capture(), eq(""), any(), any());
      CalendarDataPaneModel pane = captor.getValue().getCalendarDataPaneModel();
      assertEquals("ORDER_DATE", pane.getSelectedColumn().getAttribute());
   }

   @Test
   void refusesTwoColumnsOnACalendar() {
      CalendarVSAssembly assembly = mock(CalendarVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, null, null, null, mock(CalendarPropertyDialogService.class))
            .setSource("tok", principal(), "Calendar1", "ORDERS", List.of("ORDER_DATE", "STATE"),
                      null, null, null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("calendar"));
   }

   @Test
   void refusesAnUnknownTableNamingWhatIsAvailable() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "NOPE", List.of("STATE"), null, null, null,
                      null, null, false, ""));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS"));
   }

   @Test
   void refusesAnUnknownColumnNamingWhatIsAvailable() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", List.of("NO_SUCH_COLUMN"), null,
                      null, null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("NO_SUCH_COLUMN"));
      assertTrue(thrown.getMessage().contains("STATE"));
   }

   @Test
   void refusesRepointingToADifferentTableWithoutForce() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      SelectionListPropertyDialogModel bound = new SelectionListPropertyDialogModel();
      bound.getSelectionListPaneModel().setSelectedTable("ORDERS");
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(bound);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, listService, null, null, null)
            .setSource("tok", principal(), "List1", "CUSTOMERS", List.of("NAME"), null, null,
                      null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("force"));
   }

   @Test
   void repointsToADifferentTableWhenForced() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      SelectionListPropertyDialogModel bound = new SelectionListPropertyDialogModel();
      bound.getSelectionListPaneModel().setSelectedTable("ORDERS");
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(bound);

      harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "CUSTOMERS", List.of("NAME"), null, null, null,
                   null, null, true, "");

      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), any(), eq(""), any(), any());
   }

   // ── additionalTables resolution (regression for Bug #76747) ─────────────────

   @Test
   void resolvesAMixedCaseAdditionalTableToItsCanonicalName() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(new SelectionListPropertyDialogModel());

      Map<String, Object> result = harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"),
                   List.of("customers"), null, null, null, null, false, "");

      ArgumentCaptor<SelectionListPropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), captor.capture(), eq(""), any(), any());
      SelectionListPaneModel pane = captor.getValue().getSelectionListPaneModel();
      assertEquals(List.of("CUSTOMERS"), pane.getAdditionalTables());
      assertEquals("ORDERS", result.get("table"));
   }

   @Test
   void refusesAnUnknownAdditionalTableNamingWhatIsAvailable() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"),
                      List.of("NOPE"), null, null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("List1"));
      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS"));
   }

   /** Omitting additionalTables on a rebind used to clear the shared-filter tables. */
   @Test
   void keepsExistingAdditionalTablesWhenTheFieldIsOmitted() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      SelectionListPropertyDialogModel existing = new SelectionListPropertyDialogModel();
      existing.getSelectionListPaneModel().setSelectedTable("ORDERS");
      existing.getSelectionListPaneModel().setAdditionalTables(List.of("CUSTOMERS"));
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(existing);

      Map<String, Object> result = harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "ORDERS", List.of("CITY"), null, null, null,
                   null, null, false, "");

      ArgumentCaptor<SelectionListPropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), captor.capture(), eq(""), any(), any());
      assertEquals(List.of("CUSTOMERS"),
                   captor.getValue().getSelectionListPaneModel().getAdditionalTables());
      assertEquals(List.of("CUSTOMERS"), result.get("additionalTables"));
   }

   @Test
   void clearsAdditionalTablesWhenAnEmptyListIsSent() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      SelectionListPropertyDialogModel existing = new SelectionListPropertyDialogModel();
      existing.getSelectionListPaneModel().setSelectedTable("ORDERS");
      existing.getSelectionListPaneModel().setAdditionalTables(List.of("CUSTOMERS"));
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(existing);

      Map<String, Object> result = harness(assembly, listService, null, null, null)
         .setSource("tok", principal(), "List1", "ORDERS", List.of("CITY"), List.of(), null,
                   null, null, null, false, "");

      assertEquals(List.of(), existing.getSelectionListPaneModel().getAdditionalTables());
      assertEquals(List.of(), result.get("additionalTables"));
   }

   @Test
   void keepsACalendarsAdditionalTablesWhenTheFieldIsOmitted() throws Exception {
      CalendarVSAssembly assembly = mock(CalendarVSAssembly.class);
      CalendarPropertyDialogService calendarService = mock(CalendarPropertyDialogService.class);
      CalendarPropertyDialogModel existing = new CalendarPropertyDialogModel();
      existing.getCalendarDataPaneModel().setSelectedTable("ORDERS");
      existing.getCalendarDataPaneModel().setAdditionalTables(List.of("CUSTOMERS"));
      when(calendarService.getCalendarPropertyModel(eq("rt1"), eq("Cal1"), any()))
         .thenReturn(existing);

      harness(assembly, null, null, null, calendarService)
         .setSource("tok", principal(), "Cal1", "ORDERS", List.of("ORDER_DATE"), null, null,
                   null, null, null, false, "");

      assertEquals(List.of("CUSTOMERS"), existing.getCalendarDataPaneModel().getAdditionalTables());
   }

   // ── measure resolution ──────────────────────────────────────────────────────

   /** Stubs the property dialog's measure dropdown source, with its leading null-named "None". */
   private static DataOutputService measures(String... names) throws Exception {
      List<OutputColumnRefModel> columns = new ArrayList<>();
      columns.add(new OutputColumnRefModel());

      for(String name : names) {
         OutputColumnRefModel column = new OutputColumnRefModel();
         column.setName(name);
         columns.add(column);
      }

      DataOutputService dataOutput = mock(DataOutputService.class);
      when(dataOutput.getOutputSelectionColumns(eq("rt1"), any(), any()))
         .thenReturn(columns.toArray(new OutputColumnRefModel[0]));
      return dataOutput;
   }

   private static String boundMeasure(DataOutputService dataOutput, List<String> additional,
                                      String measure)
      throws Exception
   {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(model);

      harness(assembly, listService, null, null, null, dataOutput)
         .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), additional,
                   measure, null, null, null, false, "");

      return model.getSelectionListPaneModel().getSelectionMeasurePaneModel().getMeasure();
   }

   @Test
   void canonicalizesAMeasureAgainstTheDialogsMeasureList() throws Exception {
      assertEquals("AMOUNT", boundMeasure(measures("STATE", "AMOUNT"), null, "amount"));
   }

   @Test
   void refusesAMeasureTheDialogWouldNotOffer() throws Exception {
      DataOutputService dataOutput = measures("STATE", "AMOUNT");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> boundMeasure(dataOutput, null, "NOPE"));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("AMOUNT"));
   }

   /** The dialog lists the measures the table shares with every additional table. */
   @Test
   void looksUpTheMeasureAcrossTheTableAndItsAdditionalTables() throws Exception {
      DataOutputService dataOutput = measures("NAME");

      assertEquals("NAME", boundMeasure(dataOutput, List.of("customers"), "NAME"));
      verify(dataOutput).getOutputSelectionColumns(
         eq("rt1"), eq(List.of("ORDERS", "CUSTOMERS")), any());
   }

   @Test
   void passesDynamicAndExpressionMeasuresThroughUnchecked() throws Exception {
      DataOutputService dataOutput = measures("AMOUNT");

      assertEquals("$(Measure1)", boundMeasure(dataOutput, null, "$(Measure1)"));
      assertEquals("=field['AMOUNT'] * 2", boundMeasure(dataOutput, null, "=field['AMOUNT'] * 2"));
      verifyNoInteractions(dataOutput);
   }

   // ── Logical Model column resolution (regression for Bug #76700) ────────────

   /**
    * {@code SelectionListPaneModel.selectedColumn} used to be built by splitting a folded
    * Logical-Model column ({@code "Customer:Region"}) into {@code entity}/{@code attribute}. But
    * the property dialog's own read-back tree ({@code getSelectionTablesTree}) represents that
    * same column with {@code entity} left {@code null} and the whole compound string as
    * {@code attribute} — the shape every logical-model column entry uses server-side. The
    * mismatch meant the read-back match ({@code SelectionDialogService.
    * findSelectedOutputColumnRefModel}) never found the column back, so {@code selectedColumn}
    * read back {@code null} and the rendered list stayed empty on every affected assembly (Bug
    * #76700 / VFL-001, confirmed live against Examples/Orders' "Order Model").
    */
   @Test
   void doesNotSplitAFoldedLogicalModelColumnIntoEntityAndAttribute() throws Exception {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);
      SelectionListPropertyDialogService listService = mock(SelectionListPropertyDialogService.class);
      when(listService.getSelectionListPropertyModel(eq("rt1"), eq("List1"), any()))
         .thenReturn(new SelectionListPropertyDialogModel());

      BindableTable orderModel = new BindableTable("Order Model", null, List.of(
         new BindableField("Customer:Region", "string", "dimension")));
      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      when(fieldsService.list(eq("rt1"), isNull(), any())).thenReturn(List.of(orderModel));

      SelectionBindingService service = new SelectionBindingService(
         sessionsFor(assembly), fieldsService, listService,
         mock(SelectionTreePropertyDialogService.class), mock(RangeSliderPropertyDialogService.class),
         mock(CalendarPropertyDialogService.class), mock(DataOutputService.class));

      service.setSource("tok", principal(), "List1", "Order Model", List.of("Customer:Region"),
                        null, null, null, null, null, false, "");

      ArgumentCaptor<SelectionListPropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), captor.capture(), eq(""), any(), any());
      OutputColumnRefModel selectedColumn =
         captor.getValue().getSelectionListPaneModel().getSelectedColumn();
      assertNull(selectedColumn.getEntity(),
         "entity must stay null, matching the read-back tree's own shape for a logical-model " +
         "column");
      assertEquals("Customer:Region", selectedColumn.getAttribute(),
         "the whole compound column name is the attribute — splitting it broke the read-back " +
         "match");
   }

   /** Same fix, exercised through {@code columnRefs()} — the array form a selection tree uses. */
   @Test
   void doesNotSplitFoldedLogicalModelColumnsOnASelectionTree() throws Exception {
      SelectionTreeVSAssembly assembly = mock(SelectionTreeVSAssembly.class);
      SelectionTreePropertyDialogService treeService = mock(SelectionTreePropertyDialogService.class);
      when(treeService.getSelectionTreePropertyModel(eq("rt1"), eq("Tree1"), any()))
         .thenReturn(new SelectionTreePropertyDialogModel());

      BindableTable orderModel = new BindableTable("Order Model", null, List.of(
         new BindableField("Customer:Region", "string", "dimension"),
         new BindableField("Customer:City", "string", "dimension")));
      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      when(fieldsService.list(eq("rt1"), isNull(), any())).thenReturn(List.of(orderModel));

      SelectionBindingService service = new SelectionBindingService(
         sessionsFor(assembly), fieldsService, mock(SelectionListPropertyDialogService.class),
         treeService, mock(RangeSliderPropertyDialogService.class),
         mock(CalendarPropertyDialogService.class), mock(DataOutputService.class));

      service.setSource("tok", principal(), "Tree1", "Order Model",
                        List.of("Customer:Region", "Customer:City"), null, null, null, null, null,
                        false, "");

      ArgumentCaptor<SelectionTreePropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionTreePropertyDialogModel.class);
      verify(treeService).setSelectionTreePropertyModel(
         eq("rt1"), eq("Tree1"), captor.capture(), eq(""), any(), any());
      OutputColumnRefModel[] levels =
         captor.getValue().getSelectionTreePaneModel().getSelectedColumns();
      assertEquals(2, levels.length);
      assertNull(levels[0].getEntity());
      assertEquals("Customer:Region", levels[0].getAttribute());
      assertNull(levels[1].getEntity());
      assertEquals("Customer:City", levels[1].getAttribute());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static SelectionBindingService harness(VSAssembly assembly,
                                                   SelectionListPropertyDialogService listService,
                                                   SelectionTreePropertyDialogService treeService,
                                                   RangeSliderPropertyDialogService sliderService,
                                                   CalendarPropertyDialogService calendarService)
      throws Exception
   {
      return harness(assembly, listService, treeService, sliderService, calendarService, null);
   }

   private static SelectionBindingService harness(VSAssembly assembly,
                                                   SelectionListPropertyDialogService listService,
                                                   SelectionTreePropertyDialogService treeService,
                                                   RangeSliderPropertyDialogService sliderService,
                                                   CalendarPropertyDialogService calendarService,
                                                   DataOutputService dataOutputService)
      throws Exception
   {
      BindableTable orders = new BindableTable("ORDERS", null, List.of(
         new BindableField("STATE", "string", "dimension"),
         new BindableField("CITY", "string", "dimension"),
         new BindableField("AMOUNT", "double", "measure"),
         new BindableField("ORDER_DATE", "timestamp", "dimension")));
      BindableTable customers = new BindableTable("CUSTOMERS", null, List.of(
         new BindableField("NAME", "string", "dimension")));

      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      when(fieldsService.list(eq("rt1"), isNull(), any()))
         .thenReturn(List.of(orders, customers));

      return new SelectionBindingService(
         sessionsFor(assembly), fieldsService,
         listService == null ? mock(SelectionListPropertyDialogService.class) : listService,
         treeService == null ? mock(SelectionTreePropertyDialogService.class) : treeService,
         sliderService == null ? mock(RangeSliderPropertyDialogService.class) : sliderService,
         calendarService == null ? mock(CalendarPropertyDialogService.class) : calendarService,
         dataOutputService == null ? mock(DataOutputService.class) : dataOutputService);
   }

   private static ViewsheetSessionService sessionsFor(VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
