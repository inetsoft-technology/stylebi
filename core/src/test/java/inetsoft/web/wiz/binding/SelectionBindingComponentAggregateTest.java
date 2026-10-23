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
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.CalendarPropertyDialogService;
import inetsoft.web.composer.vs.dialog.DataOutputService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionTreePropertyDialogService;
import inetsoft.web.wiz.binding.BindableFieldsService.AggregateSource;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A range slider binds to a Chart/Crosstab's aggregated values (the Composer's "Components" node)
 * through the same {@code set_selection_source} call; nothing else can.
 */
@Tag("core")
class SelectionBindingComponentAggregateTest {
   @Test
   void aRangeSliderBindsToAChartAggregateAsTheDialogStoresIt() throws Exception {
      RangeSliderPropertyDialogService sliderService = sliderService(new RangeSliderPropertyDialogModel());
      Map<String, Object> result = bind(sliderService, "Chart1", List.of("sum(product:total)"),
                                        null, false);

      RangeSliderDataPaneModel pane = savedPane(sliderService);
      assertEquals("Chart1", pane.getSelectedTable());
      assertTrue(pane.isAssemblySource());
      assertFalse(pane.isComposite());
      assertEquals(0, pane.getAdditionalTables().size());
      assertEquals(1, pane.getSelectedColumns().length);
      // The tree's own node: setTimeInfo stores sourceType from properties["type"].
      assertSame(CHART1_TOTAL, pane.getSelectedColumns()[0]);
      assertEquals(String.valueOf(XSourceInfo.VS_ASSEMBLY),
                   pane.getSelectedColumns()[0].getProperties().get("type"));
      assertEquals("Chart1", result.get("table"));
      assertEquals(List.of("Sum(Product:Total)"), result.get("columns"));
      assertEquals(BindableTable.VIEWSHEET_AGGREGATE, result.get("sourceType"));
   }

   @Test
   void severalAggregatesOfOneChartBecomeACompositeRange() throws Exception {
      RangeSliderPropertyDialogService sliderService = sliderService(new RangeSliderPropertyDialogModel());
      bind(sliderService, "Chart1", List.of("Sum(Product:Total)", "Count(Order:Id)"), null, false);

      RangeSliderDataPaneModel pane = savedPane(sliderService);
      assertTrue(pane.isComposite());
      assertEquals(2, pane.getSelectedColumns().length);
      assertSame(CHART1_TOTAL, pane.getSelectedColumns()[0]);
      assertSame(CHART1_COUNT, pane.getSelectedColumns()[1]);
   }

   @Test
   void aCrosstabAggregateBindsTheSameWay() throws Exception {
      RangeSliderPropertyDialogService sliderService = sliderService(new RangeSliderPropertyDialogModel());
      bind(sliderService, "Crosstab1", List.of("Sum(Total)"), null, false);

      RangeSliderDataPaneModel pane = savedPane(sliderService);
      assertEquals("Crosstab1", pane.getSelectedTable());
      assertTrue(pane.isAssemblySource());
      assertSame(CROSSTAB1_TOTAL, pane.getSelectedColumns()[0]);
   }

   @Test
   void anUnknownAggregateListsTheOnesTheComponentOffers() {
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(sliderService(new RangeSliderPropertyDialogModel()), "Chart1",
              List.of("Sum(Missing)"), null, false));

      assertTrue(thrown.getMessage().contains("'Sum(Missing)' is not a column of 'Chart1'"),
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Sum(Product:Total), Count(Order:Id)"),
                 thrown.getMessage());
   }

   @Test
   void anUnknownTableNamesTheChartsAndCrosstabsAmongTheAvailable() {
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(sliderService(new RangeSliderPropertyDialogModel()), "Nope",
              List.of("Sum(Total)"), null, false));

      assertTrue(thrown.getMessage().contains("cannot bind to 'Nope'"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("ORDERS") &&
                    thrown.getMessage().contains("Chart1") &&
                    thrown.getMessage().contains("Crosstab1"), thrown.getMessage());
   }

   @Test
   void selectionListTreeAndCalendarRefuseAnAggregateSourceByName() {
      for(VSAssembly assembly : List.of(mock(SelectionListVSAssembly.class),
                                        mock(SelectionTreeVSAssembly.class),
                                        mock(CalendarVSAssembly.class)))
      {
         Exception thrown = assertThrows(IllegalArgumentException.class, () ->
            harness(assembly, mock(RangeSliderPropertyDialogService.class))
               .setSource("tok", principal(), "Sel1", "Chart1", List.of("Sum(Product:Total)"),
                         null, null, null, null, null, false, ""));

         assertTrue(thrown.getMessage().contains("only a range slider can bind to the chart or " +
                                                    "crosstab 'Chart1'"), thrown.getMessage());
      }
   }

   @Test
   void aWorksheetTableUnknownToASelectionListKeepsTheOrdinaryRefusal() {
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(mock(SelectionListVSAssembly.class), mock(RangeSliderPropertyDialogService.class))
            .setSource("tok", principal(), "List1", "Nope", List.of("STATE"), null, null, null,
                      null, null, false, ""));

      assertTrue(thrown.getMessage().contains("cannot bind to 'Nope'"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("Chart1"), thrown.getMessage());
   }

   @Test
   void aNameBothAWorksheetTableAndAChartIsRefused() {
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(sliderService(new RangeSliderPropertyDialogModel()), "ORDERS",
              List.of("AMOUNT"), null, false, new AggregateSource("ORDERS", List.of(CHART1_TOTAL))));

      assertTrue(thrown.getMessage().contains("names both a worksheet table and the chart/crosstab"),
                 thrown.getMessage());
   }

   @Test
   void additionalTablesAreRefusedWithAnAggregateSource() throws Exception {
      RangeSliderPropertyDialogService sliderService = sliderService(new RangeSliderPropertyDialogModel());

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(sliderService, "Chart1", List.of("Sum(Product:Total)"), List.of("ORDERS"), false));
      assertTrue(thrown.getMessage().contains("'additionalTables'"), thrown.getMessage());
      verify(sliderService, never()).setRangeSliderPropertyModel(any(), any(), any(), any(), any(), any());

      // [] is the explicit "none" and is accepted.
      bind(sliderService, "Chart1", List.of("Sum(Product:Total)"), List.of(), false);
      assertTrue(savedPane(sliderService).isAssemblySource());
   }

   @Test
   void inheritedAdditionalTablesAreDroppedAndDisclosed() throws Exception {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      model.getRangeSliderDataPaneModel().setSelectedTable("ORDERS");
      model.getRangeSliderDataPaneModel().setAdditionalTables(new ArrayList<>(List.of("CUSTOMERS")));
      RangeSliderPropertyDialogService sliderService = sliderService(model);

      Map<String, Object> result = bind(sliderService, "Chart1", List.of("Sum(Product:Total)"),
                                        null, true);

      assertEquals(0, savedPane(sliderService).getAdditionalTables().size());
      assertEquals(List.of("CUSTOMERS"), result.get("droppedAdditionalTables"));
   }

   @Test
   void repointingBetweenAWorksheetTableAndAnAggregateNeedsForce() throws Exception {
      RangeSliderPropertyDialogModel onWorksheet = new RangeSliderPropertyDialogModel();
      onWorksheet.getRangeSliderDataPaneModel().setSelectedTable("ORDERS");
      RangeSliderPropertyDialogService toComponent = sliderService(onWorksheet);

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(toComponent, "Chart1", List.of("Sum(Product:Total)"), null, false));
      assertTrue(thrown.getMessage().contains("force:true"), thrown.getMessage());
      verify(toComponent, never()).setRangeSliderPropertyModel(any(), any(), any(), any(), any(), any());

      RangeSliderPropertyDialogModel onComponent = new RangeSliderPropertyDialogModel();
      onComponent.getRangeSliderDataPaneModel().setSelectedTable("Chart1");
      onComponent.getRangeSliderDataPaneModel().setAssemblySource(true);
      RangeSliderPropertyDialogService toWorksheet = sliderService(onComponent);

      thrown = assertThrows(IllegalArgumentException.class, () ->
         bind(toWorksheet, "ORDERS", List.of("AMOUNT"), null, false));
      assertTrue(thrown.getMessage().contains("force:true"), thrown.getMessage());

      // Forced back to a worksheet table: no component marker is left on the stored columns.
      bind(toWorksheet, "ORDERS", List.of("AMOUNT"), null, true);
      RangeSliderDataPaneModel pane = savedPane(toWorksheet);
      assertFalse(pane.isAssemblySource());
      assertEquals("ORDERS", pane.getSelectedTable());
      assertNull(pane.getSelectedColumns()[0].getProperties().get("type"));
   }

   @Test
   void rebindingToAnotherAggregateOfTheSameChartNeedsNoForce() throws Exception {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      model.getRangeSliderDataPaneModel().setSelectedTable("Chart1");
      model.getRangeSliderDataPaneModel().setAssemblySource(true);
      RangeSliderPropertyDialogService sliderService = sliderService(model);

      bind(sliderService, "Chart1", List.of("Count(Order:Id)"), null, false);

      assertSame(CHART1_COUNT, savedPane(sliderService).getSelectedColumns()[0]);
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static final OutputColumnRefModel CHART1_TOTAL = aggregate("Chart1", "Sum(Product:Total)");
   private static final OutputColumnRefModel CHART1_COUNT = aggregate("Chart1", "Count(Order:Id)");
   private static final OutputColumnRefModel CROSSTAB1_TOTAL = aggregate("Crosstab1", "Sum(Total)");

   /** A column node as {@code VSOutputService.createRangeSliderTree} emits it for a component. */
   private static OutputColumnRefModel aggregate(String assembly, String attribute) {
      OutputColumnRefModel ref = new OutputColumnRefModel();
      ref.setAttribute(attribute);
      ref.setDataType("double");
      ref.setTable(assembly);
      Map<String, String> properties = new HashMap<>();
      properties.put("dtype", "double");
      properties.put("assembly", assembly);
      properties.put("attribute", attribute);
      properties.put("source", assembly);
      properties.put("type", String.valueOf(XSourceInfo.VS_ASSEMBLY));
      ref.setProperties(properties);
      return ref;
   }

   private static RangeSliderPropertyDialogService sliderService(RangeSliderPropertyDialogModel model)
      throws Exception
   {
      RangeSliderPropertyDialogService service = mock(RangeSliderPropertyDialogService.class);
      when(service.getRangeSliderPropertyModel(eq("rt1"), eq("Slider1"), any())).thenReturn(model);
      return service;
   }

   private static RangeSliderDataPaneModel savedPane(RangeSliderPropertyDialogService service)
      throws Exception
   {
      ArgumentCaptor<RangeSliderPropertyDialogModel> captor =
         ArgumentCaptor.forClass(RangeSliderPropertyDialogModel.class);
      verify(service, atLeastOnce()).setRangeSliderPropertyModel(
         eq("rt1"), eq("Slider1"), captor.capture(), eq(""), any(), any());
      return captor.getValue().getRangeSliderDataPaneModel();
   }

   private static Map<String, Object> bind(RangeSliderPropertyDialogService sliderService,
                                           String table, List<String> columns,
                                           List<String> additionalTables, boolean force,
                                           AggregateSource... components)
      throws Exception
   {
      return harness(mock(TimeSliderVSAssembly.class), sliderService, components)
         .setSource("tok", principal(), "Slider1", table, columns, additionalTables, null, null,
                   null, null, force, "");
   }

   private static SelectionBindingService harness(VSAssembly assembly,
                                                  RangeSliderPropertyDialogService sliderService,
                                                  AggregateSource... components)
      throws Exception
   {
      BindableTable orders = new BindableTable("ORDERS", null, List.of(
         new BindableField("STATE", "string", "dimension"),
         new BindableField("AMOUNT", "double", "measure")));
      BindableTable customers = new BindableTable("CUSTOMERS", null, List.of(
         new BindableField("STATE", "string", "dimension"),
         new BindableField("AMOUNT", "double", "measure")));
      BindableFieldsService fieldsService = mock(BindableFieldsService.class);
      when(fieldsService.list(eq("rt1"), isNull(), any())).thenReturn(List.of(orders, customers));
      when(fieldsService.aggregateSources(eq("rt1"), any())).thenReturn(
         components.length > 0 ? List.of(components) :
            List.of(new AggregateSource("Chart1", List.of(CHART1_TOTAL, CHART1_COUNT)),
                    new AggregateSource("Crosstab1", List.of(CROSSTAB1_TOTAL))));

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
         sessions, fieldsService, mock(SelectionListPropertyDialogService.class),
         mock(SelectionTreePropertyDialogService.class), sliderService,
         mock(CalendarPropertyDialogService.class), mock(DataOutputService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
