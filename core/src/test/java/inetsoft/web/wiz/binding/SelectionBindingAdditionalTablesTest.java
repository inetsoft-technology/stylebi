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
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.*;
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

/**
 * Bug #78473 in the shape of regression case VFL-021 (Examples/Orders): CUSTOMERS has
 * CUSTOMER_ID and STATE, ORDERS has CUSTOMER_ID but no STATE. An additional table without the
 * bound column makes the selection union hide that column, so the list showed CUSTOMER_ID values
 * instead of states. The checks run on what the assembly's property model is written with.
 */
@Tag("core")
class SelectionBindingAdditionalTablesTest {
   @Test
   void inheritedOrdersIsDroppedAndDisclosedOnARebindToState() throws Exception {
      SelectionListPropertyDialogModel model = list("CUSTOMERS", List.of("ORDERS"));
      SelectionListPropertyDialogService listService = listService(model);

      Map<String, Object> result = service(mock(SelectionListVSAssembly.class), listService, null,
                                           null, null)
         .setSource("tok", user(), "L3", "CUSTOMERS", List.of("STATE"), null, null, null, null,
                    null, false, "");

      assertEquals(List.of("ORDERS"), result.get("droppedAdditionalTables"));
      assertEquals(List.of(), result.get("additionalTables"));
      assertEquals(List.of(), writtenList(listService).getSelectionListPaneModel()
         .getAdditionalTables());
   }

   @Test
   void explicitOrdersForStateIsRefusedNamingTheTableAndTheColumn() throws Exception {
      SelectionListPropertyDialogModel model = list(null, null);
      SelectionListPropertyDialogService listService = listService(model);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         service(mock(SelectionListVSAssembly.class), listService, null, null, null)
            .setSource("tok", user(), "L6", "CUSTOMERS", List.of("STATE"), List.of("ORDERS"),
                       null, null, null, null, false, ""));

      assertTrue(thrown.getMessage().contains("'ORDERS'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("STATE"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Compatible tables: none"), thrown.getMessage());
      verify(listService, never()).setSelectionListPropertyModel(any(), any(), any(), any(), any(),
                                                                 any());
   }

   @Test
   void customerIdWithOrdersIsAcceptedAndAForceRepointToOrdersDropsTheOverlap() throws Exception {
      // VFL-016 S2's setup since #78473 (bind CUSTOMER_ID, which ORDERS has), then its repoint.
      SelectionListPropertyDialogModel model = list(null, null);
      SelectionListPropertyDialogService listService = listService(model);
      SelectionBindingService service =
         service(mock(SelectionListVSAssembly.class), listService, null, null, null);

      Map<String, Object> bound = service.setSource(
         "tok", user(), "L2", "customers", List.of("customer_id"), List.of("orders"), null, null,
         null, null, false, "");
      assertEquals(List.of("ORDERS"), bound.get("additionalTables"));
      assertFalse(bound.containsKey("droppedAdditionalTables"));

      Map<String, Object> repoint = service.setSource(
         "tok", user(), "L2", "ORDERS", List.of("CUSTOMER_ID"), null, null, null, null, null,
         true, "");
      assertEquals(List.of(), repoint.get("additionalTables"));
      assertEquals(List.of("ORDERS"), repoint.get("droppedAdditionalTables"));
   }

   @Test
   void calendarRefusesAnExplicitTableWithoutTheDateColumnAndAcceptsOneWithIt() throws Exception {
      CalendarPropertyDialogService refused = calendarService(new CalendarPropertyDialogModel());
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         service(mock(CalendarVSAssembly.class), null, null, null, refused)
            .setSource("tok", user(), "Cal1", "ORDERS", List.of("ORDER_DATE"),
                       List.of("CUSTOMERS"), null, null, null, null, false, ""));
      assertTrue(thrown.getMessage().contains("'CUSTOMERS'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("ORDER_DATE"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Compatible tables: SHIPMENTS"), thrown.getMessage());
      verify(refused, never()).setCalendarPropertyModel(any(), any(), any(), any(), any(), any());

      CalendarPropertyDialogModel accepted = new CalendarPropertyDialogModel();
      Map<String, Object> result = service(mock(CalendarVSAssembly.class), null, null, null,
                                           calendarService(accepted))
         .setSource("tok", user(), "Cal1", "ORDERS", List.of("ORDER_DATE"),
                    List.of("SHIPMENTS"), null, null, null, null, false, "");
      assertEquals(List.of("SHIPMENTS"), result.get("additionalTables"));
   }

   @Test
   void singleRangeSliderRefusesExplicitAndDropsInheritedTablesWithoutTheColumn()
      throws Exception
   {
      RangeSliderPropertyDialogService refused = sliderService(new RangeSliderPropertyDialogModel());
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         service(mock(TimeSliderVSAssembly.class), null, null, refused, null)
            .setSource("tok", user(), "Slider1", "ORDERS", List.of("AMOUNT"),
                       List.of("CUSTOMERS"), null, null, null, null, false, ""));
      assertTrue(thrown.getMessage().contains("AMOUNT"), thrown.getMessage());
      verify(refused, never()).setRangeSliderPropertyModel(any(), any(), any(), any(), any(),
                                                           any());

      RangeSliderPropertyDialogModel inherited = new RangeSliderPropertyDialogModel();
      inherited.getRangeSliderDataPaneModel().setSelectedTable("ORDERS");
      inherited.getRangeSliderDataPaneModel().setAdditionalTables(List.of("SHIPMENTS",
                                                                          "CUSTOMERS"));
      Map<String, Object> result = service(mock(TimeSliderVSAssembly.class), null, null,
                                           sliderService(inherited), null)
         .setSource("tok", user(), "Slider1", "ORDERS", List.of("AMOUNT"), null, null, null,
                    null, null, false, "");
      // SHIPMENTS.AMOUNT is an integer, which merges with ORDERS.AMOUNT (double).
      assertEquals(List.of("SHIPMENTS"), result.get("additionalTables"));
      assertEquals(List.of("CUSTOMERS"), result.get("droppedAdditionalTables"));
   }

   @Test
   void columnModeTreeRefusesATableMissingOneLevelAndDropsItWhenInherited() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      model.getSelectionTreePaneModel().setSelectedTable("CUSTOMERS");
      SelectionTreePropertyDialogService refused = treeService(model);
      // ORDERS has CUSTOMER_ID but not STATE.
      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
         service(mock(SelectionTreeVSAssembly.class), null, refused, null, null)
            .setSource("tok", user(), "Tree1", "CUSTOMERS", List.of("STATE", "CUSTOMER_ID"),
                       List.of("ORDERS"), null, null, null, null, false, ""));
      assertTrue(thrown.getMessage().contains("'ORDERS'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("STATE"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("STATE, CUSTOMER_ID"), thrown.getMessage());
      verify(refused, never()).setSelectionTreePropertyModel(any(), any(), any(), any(), any(),
                                                             any());

      SelectionTreePropertyDialogModel inherited = new SelectionTreePropertyDialogModel();
      inherited.getSelectionTreePaneModel().setSelectedTable("CUSTOMERS");
      inherited.getSelectionTreePaneModel().setAdditionalTables(List.of("ORDERS"));
      Map<String, Object> result = service(mock(SelectionTreeVSAssembly.class), null,
                                           treeService(inherited), null, null)
         .setSource("tok", user(), "Tree1", "CUSTOMERS", List.of("STATE", "CUSTOMER_ID"), null,
                    null, null, null, null, false, "");
      assertEquals(List.of("ORDERS"), result.get("droppedAdditionalTables"));
      assertEquals(List.of(), result.get("additionalTables"));
   }

   private static SelectionListPropertyDialogModel list(String table, List<String> additional) {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      model.getSelectionListPaneModel().setSelectedTable(table);
      model.getSelectionListPaneModel().setAdditionalTables(additional);
      return model;
   }

   private static SelectionListPropertyDialogModel writtenList(
      SelectionListPropertyDialogService listService)
      throws Exception
   {
      ArgumentCaptor<SelectionListPropertyDialogModel> captor =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(listService).setSelectionListPropertyModel(any(), any(), captor.capture(), any(),
                                                        any(), any());
      return captor.getValue();
   }

   private static SelectionListPropertyDialogService listService(
      SelectionListPropertyDialogModel model)
      throws Exception
   {
      SelectionListPropertyDialogService service = mock(SelectionListPropertyDialogService.class);
      when(service.getSelectionListPropertyModel(eq("rt1"), anyString(), any())).thenReturn(model);
      return service;
   }

   private static SelectionTreePropertyDialogService treeService(
      SelectionTreePropertyDialogModel model)
      throws Exception
   {
      SelectionTreePropertyDialogService service = mock(SelectionTreePropertyDialogService.class);
      when(service.getSelectionTreePropertyModel(eq("rt1"), anyString(), any())).thenReturn(model);
      return service;
   }

   private static RangeSliderPropertyDialogService sliderService(
      RangeSliderPropertyDialogModel model)
      throws Exception
   {
      RangeSliderPropertyDialogService service = mock(RangeSliderPropertyDialogService.class);
      when(service.getRangeSliderPropertyModel(eq("rt1"), anyString(), any())).thenReturn(model);
      return service;
   }

   private static CalendarPropertyDialogService calendarService(CalendarPropertyDialogModel model)
      throws Exception
   {
      CalendarPropertyDialogService service = mock(CalendarPropertyDialogService.class);
      when(service.getCalendarPropertyModel(eq("rt1"), anyString(), any())).thenReturn(model);
      return service;
   }

   private static SelectionBindingService service(VSAssembly assembly,
                                                  SelectionListPropertyDialogService listService,
                                                  SelectionTreePropertyDialogService treeService,
                                                  RangeSliderPropertyDialogService sliderService,
                                                  CalendarPropertyDialogService calendarService)
      throws Exception
   {
      BindableTable customers = new BindableTable("CUSTOMERS", null, List.of(
         new BindableField("CUSTOMER_ID", "integer", "dimension"),
         new BindableField("STATE", "string", "dimension")));
      BindableTable orders = new BindableTable("ORDERS", null, List.of(
         new BindableField("CUSTOMER_ID", "integer", "dimension"),
         new BindableField("AMOUNT", "double", "measure"),
         new BindableField("ORDER_DATE", "date", "dimension")));
      BindableTable shipments = new BindableTable("SHIPMENTS", null, List.of(
         new BindableField("AMOUNT", "integer", "measure"),
         new BindableField("ORDER_DATE", "timeInstant", "dimension")));

      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      when(fieldsService.list(eq("rt1"), isNull(), any()))
         .thenReturn(List.of(customers, orders, shipments));

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      return new SelectionBindingService(
         sessions, fieldsService,
         listService == null ? mock(SelectionListPropertyDialogService.class) : listService,
         treeService == null ? mock(SelectionTreePropertyDialogService.class) : treeService,
         sliderService == null ? mock(RangeSliderPropertyDialogService.class) : sliderService,
         calendarService == null ? mock(CalendarPropertyDialogService.class) : calendarService,
         mock(DataOutputService.class));
   }

   private static Principal user() {
      return () -> "admin";
   }
}
