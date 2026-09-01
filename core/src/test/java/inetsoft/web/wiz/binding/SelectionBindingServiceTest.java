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
         .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, null, false, "");

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
                      null, false, ""));

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
                   false, "");

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
            .setSource("tok", principal(), "Tree1", "ORDERS", List.of(), null, null, false, ""));

      assertTrue(thrown.getMessage().contains("at least one column"));
   }

   @Test
   void aSingleColumnTimeSliderBecomesASingleRange() throws Exception {
      TimeSliderVSAssembly assembly = mock(TimeSliderVSAssembly.class);
      RangeSliderPropertyDialogService sliderService = mock(RangeSliderPropertyDialogService.class);
      when(sliderService.getRangeSliderPropertyModel(eq("rt1"), eq("Slider1"), any()))
         .thenReturn(new RangeSliderPropertyDialogModel());

      Map<String, Object> result = harness(assembly, null, null, sliderService, null)
         .setSource("tok", principal(), "Slider1", "ORDERS", List.of("AMOUNT"), null, null, false,
                   "");

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
                   null, false, "");

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
                   false, "");

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
                      null, null, false, ""));

      assertTrue(thrown.getMessage().contains("calendar"));
   }

   @Test
   void refusesAnUnknownTableNamingWhatIsAvailable() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "NOPE", List.of("STATE"), null, null, false,
                      ""));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS"));
   }

   @Test
   void refusesAnUnknownColumnNamingWhatIsAvailable() {
      SelectionListVSAssembly assembly = mock(SelectionListVSAssembly.class);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(assembly, mock(SelectionListPropertyDialogService.class), null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", List.of("NO_SUCH_COLUMN"), null,
                      null, false, ""));

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
                      false, ""));

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
         .setSource("tok", principal(), "List1", "CUSTOMERS", List.of("NAME"), null, null, true,
                   "");

      verify(listService).setSelectionListPropertyModel(
         eq("rt1"), eq("List1"), any(), eq(""), any(), any());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static SelectionBindingService harness(VSAssembly assembly,
                                                   SelectionListPropertyDialogService listService,
                                                   SelectionTreePropertyDialogService treeService,
                                                   RangeSliderPropertyDialogService sliderService,
                                                   CalendarPropertyDialogService calendarService)
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
         calendarService == null ? mock(CalendarPropertyDialogService.class) : calendarService);
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
