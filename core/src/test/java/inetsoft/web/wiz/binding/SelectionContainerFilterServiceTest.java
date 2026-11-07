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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.ImmutableVSTableTrapModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.wiz.binding.BindableFieldsService.AggregateSource;
import inetsoft.web.wiz.binding.SelectionContainerFilterService.CreatedFilter;
import inetsoft.web.wiz.binding.SelectionContainerFilterService.Request;
import inetsoft.web.wiz.binding.SelectionContainerFilterService.Result;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80450: edit(op:'add_filter_to_container'). Real Viewsheet and container; the VSBindingService
 * stand-in appends the filter {@code insertChild} would build (the type rule itself is StyleBI's
 * and is exercised there), so what is under test is everything the wrapper adds: column
 * resolution, the duplicate pre-check, the trap gate, and the created[] diff.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionContainerFilterServiceTest {
   private Viewsheet vs;
   private ViewsheetSessionService sessions;
   private BindableFieldsService fields;
   private VSBindingService binding;
   private QueryManagerService queries;
   private SelectionContainerFilterService service;
   private boolean trap;
   private boolean skipInsert;

   private void setup() throws Exception {
      vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      add(new TableVSAssembly(vs, "OrderTable"), 300, 20);
      CurrentSelectionVSAssembly box = new CurrentSelectionVSAssembly(vs, "Box");
      add(box, 20, 20);
      box.setAssemblies(new String[0]);
      CurrentSelectionVSAssembly grouped = new CurrentSelectionVSAssembly(vs, "GroupedBox");
      add(grouped, 900, 20);
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      add(group, 900, 20);
      group.setAssemblies(new String[] { "GroupedBox" });

      sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.runtimeId(anyString(), any())).thenReturn("rt1");
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, "rt1", null);
         return List.of();
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      fields = mock(BindableFieldsService.class);
      when(fields.list(eq("rt1"), isNull(), any())).thenReturn(List.of(
         new BindableTable("ORDERS", null, List.of(
            new BindableField("Region", XSchema.STRING, "dimension"),
            new BindableField("Total", XSchema.DOUBLE, "measure"),
            new BindableField("Active", XSchema.BOOLEAN, "dimension"))),
         new BindableTable("CUSTOMERS", null, List.of(
            new BindableField("Region", XSchema.STRING, "dimension"))),
         new BindableTable("MODEL", null, List.of(
            new BindableField("Customer:Region", XSchema.STRING, "dimension"))),
         new BindableTable("Chart1", null, List.of(), List.of(), BindableTable.VIEWSHEET)));
      when(fields.aggregateSources(eq("rt1"), any())).thenReturn(List.of(
         new AggregateSource("Crosstab1", List.of(column("Sum(Total)", XSchema.DOUBLE)))));

      binding = mock(VSBindingService.class);
      trap = false;
      skipInsert = false;
      when(binding.checkVSSelectionTrap(eq("rt1"), any(), any())).thenAnswer(inv ->
         ImmutableVSTableTrapModel.builder().showTrap(trap).build());
      doAnswer(inv -> {
         if(skipInsert) {
            return null;
         }

         InsertSelectionChildEvent e = inv.getArgument(1);
         CurrentSelectionVSAssembly c = (CurrentSelectionVSAssembly) vs.getAssembly(e.getName());
         List<String> list = new ArrayList<>(Arrays.asList(c.getAssemblies()));
         int to = e.getToIndex();

         for(OutputColumnRefModel col : e.getColumns()) {
            boolean slider = XSchema.isNumericType(col.getDataType()) ||
               XSchema.isDateType(col.getDataType());
            String name = slider ? "RangeSlider" + (vs.getAssemblies().length) :
               "SelectionList" + (vs.getAssemblies().length);
            AbstractSelectionVSAssembly child = slider ? new TimeSliderVSAssembly(vs, name) :
               new SelectionListVSAssembly(vs, name);
            child.setTableName(col.getTable());
            child.setTableNames(List.of(col.getTable()));
            setRef(child, new AttributeRef(col.getEntity(), col.getAttribute()));
            vs.addAssembly(child);
            list.add(to++, name);
         }

         c.setAssemblies(list.toArray(new String[0]));
         return null;
      }).when(binding).insertChild(anyString(), any(), anyString(), any(), any(), anyBoolean());

      queries = mock(QueryManagerService.class);
      service = new SelectionContainerFilterService(sessions, fields, binding, queries);
   }

   private static void setRef(AbstractSelectionVSAssembly child, DataRef ref) {
      if(child instanceof SelectionListVSAssembly list) {
         list.setDataRef(ref);
      }
      else {
         SingleTimeInfo info = new SingleTimeInfo();
         info.setDataRef(ref);
         ((TimeSliderVSAssembly) child).setTimeInfo(info);
      }
   }

   private static OutputColumnRefModel column(String name, String type) {
      OutputColumnRefModel ref = new OutputColumnRefModel();
      ref.setAttribute(name);
      ref.setName(name);
      ref.setDataType(type);
      return ref;
   }

   private void add(VSAssembly a, int x, int y) {
      a.setPixelOffset(new Point(x, y));
      a.setPixelSize(new Dimension(200, 100));
      vs.addAssembly(a);
   }

   private Result run(String container, String table, Integer index, Boolean confirmed,
                      String... columns) throws Exception
   {
      return service.addFilters("tok", () -> "admin",
                                new Request(container, List.of(columns), table, index, confirmed),
                                "");
   }

   private IllegalArgumentException refused(String container, String table, Boolean confirmed,
                                            String... columns)
   {
      return assertThrows(IllegalArgumentException.class,
                          () -> run(container, table, null, confirmed, columns));
   }

   private void assertNothingMutated() throws Exception {
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      verify(binding, never()).insertChild(any(), any(), any(), any(), any(), anyBoolean());
   }

   @Test
   void aTextColumnBecomesASelectionListAndANumberARangeSlider() throws Exception {
      setup();
      Result result = run("Box", "ORDERS", null, null, "Region", "Total");

      assertEquals(2, result.created().size());
      CreatedFilter region = result.created().get(0);
      CreatedFilter total = result.created().get(1);
      assertEquals("Selection List", region.type());
      assertEquals("Region", region.column());
      assertEquals("ORDERS", region.table());
      assertEquals("Range Slider", total.type());
      assertEquals(List.of(region.name(), total.name()),
                   Arrays.asList(((CurrentSelectionVSAssembly) vs.getAssembly("Box"))
                                    .getAssemblies()));
      assertFalse(result.trap().detected());
   }

   @Test
   void theEventCarriesTheContainerIndexAndResolvedColumnsAndInsertChildThrows() throws Exception {
      setup();
      run("Box", "orders", null, null, "region");
      ArgumentCaptor<InsertSelectionChildEvent> captor =
         ArgumentCaptor.forClass(InsertSelectionChildEvent.class);
      verify(binding).insertChild(eq("rt1"), captor.capture(), any(), any(), any(), eq(true));
      InsertSelectionChildEvent event = captor.getValue();
      assertEquals("Box", event.getName());
      assertEquals(0, event.getToIndex());
      assertEquals(1, event.getColumns().length);
      assertEquals("ORDERS", event.getColumns()[0].getTable());
      assertEquals("Region", event.getColumns()[0].getAttribute());
      assertEquals(XSchema.STRING, event.getColumns()[0].getDataType());
   }

   @Test
   void indexPlacesTheFirstNewFilterAndAppendIsTheDefault() throws Exception {
      setup();
      run("Box", "ORDERS", null, null, "Region");
      Result second = run("Box", "ORDERS", 0, null, "Active");
      assertEquals(0, second.index());
      assertEquals(second.created().get(0).name(),
                   ((CurrentSelectionVSAssembly) vs.getAssembly("Box")).getAssemblies()[0]);
      IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
         () -> run("Box", "ORDERS", 9, null, "Total"));
      assertTrue(range.getMessage().contains("'index' 9 is out of range"), range.getMessage());
   }

   @Test
   void anUnknownColumnListsTheAvailableOnesAndChangesNothing() throws Exception {
      setup();
      IllegalArgumentException e = refused("Box", "ORDERS", null, "Nope");
      assertTrue(e.getMessage().contains("'Nope' is not a column of 'ORDERS'"), e.getMessage());
      assertTrue(e.getMessage().contains("Region, Total, Active"), e.getMessage());
      assertNothingMutated();
   }

   @Test
   void aColumnListedTwiceIsRefusedBeforeAnyChange() throws Exception {
      setup();
      IllegalArgumentException e = refused("Box", "ORDERS", null, "Region", "region");
      assertTrue(e.getMessage().contains("'Region' is listed more than once"), e.getMessage());
      assertNothingMutated();
   }

   @Test
   void aColumnAlreadyFilteredInTheContainerIsRefusedNotSilentlySkipped() throws Exception {
      setup();
      run("Box", "ORDERS", null, null, "Region");
      IllegalArgumentException e = refused("Box", "ORDERS", null, "Region");
      assertTrue(e.getMessage().contains("already has a filter on 'Region' of 'ORDERS'"),
                 e.getMessage());
      verify(binding, times(1)).insertChild(any(), any(), any(), any(), any(), anyBoolean());
   }

   @Test
   void aLogicalModelColumnStoredAsEntityAndAttributeStillCountsAsADuplicate() throws Exception {
      setup();
      SelectionListVSAssembly dragged = new SelectionListVSAssembly(vs, "Dragged");
      dragged.setTableName("MODEL");
      dragged.setTableNames(List.of("MODEL"));
      dragged.setDataRef(new AttributeRef("Customer", "Region"));
      vs.addAssembly(dragged);
      ((CurrentSelectionVSAssembly) vs.getAssembly("Box"))
         .setAssemblies(new String[] { "Dragged" });

      IllegalArgumentException e = refused("Box", "MODEL", null, "Customer:Region");
      assertTrue(e.getMessage().contains("already has a filter on 'Customer:Region'"),
                 e.getMessage());
      assertTrue(e.getMessage().contains("'Dragged'"), e.getMessage());
      assertNothingMutated();
   }

   @Test
   void theSameColumnOfAnotherTableIsNotADuplicate() throws Exception {
      setup();
      run("Box", "ORDERS", null, null, "Region");
      Result other = run("Box", "CUSTOMERS", null, null, "Region");
      assertEquals("CUSTOMERS", other.created().get(0).table());
   }

   @Test
   void aTrapIsRefusedWithoutConfirmedAndNothingIsCheckpointed() throws Exception {
      setup();
      trap = true;
      IllegalArgumentException e = refused("Box", "ORDERS", null, "Region");
      assertTrue(e.getMessage().contains("selection trap"), e.getMessage());
      assertTrue(e.getMessage().contains("Nothing was changed"), e.getMessage());
      assertTrue(e.getMessage().contains("confirmed:true"), e.getMessage());
      assertNothingMutated();
   }

   @Test
   void confirmedProceedsPastATrapAndReportsIt() throws Exception {
      setup();
      trap = true;
      Result result = run("Box", "ORDERS", null, true, "Region");
      assertTrue(result.trap().detected());
      assertTrue(result.trap().confirmed());
      assertEquals(1, result.created().size());
   }

   @Test
   void confirmedWithoutATrapIsIgnored() throws Exception {
      setup();
      Result result = run("Box", "ORDERS", null, true, "Region");
      assertFalse(result.trap().detected());
      assertFalse(result.trap().confirmed());
   }

   @Test
   void aChartOrCrosstabAsTheTableIsRefusedWithThePointerToSetSelectionSource() throws Exception {
      setup();
      IllegalArgumentException crosstab = refused("Box", "Crosstab1", null, "Sum(Total)");
      assertTrue(crosstab.getMessage().contains("cannot filter on the chart/crosstab 'Crosstab1'"),
                 crosstab.getMessage());
      assertTrue(crosstab.getMessage().contains("set_selection_source"), crosstab.getMessage());
      IllegalArgumentException chart = refused("Box", "Chart1", null, "Region");
      assertTrue(chart.getMessage().contains("cannot bind to 'Chart1'"), chart.getMessage());
      assertNothingMutated();
   }

   @Test
   void aDeniedCubeReadPermissionStopsBeforeAnyChange() throws Exception {
      setup();
      doThrow(new SecurityException("no cube read")).when(queries)
         .checkCubeTableReadPermission(eq("ORDERS"), any());
      assertThrows(SecurityException.class, () -> run("Box", "ORDERS", null, null, "Region"));
      assertNothingMutated();
   }

   @Test
   void aNonContainerAndAContainerInsideAGroupAreRefused() throws Exception {
      setup();
      IllegalArgumentException table = refused("OrderTable", "ORDERS", null, "Region");
      assertTrue(table.getMessage().contains("not a Selection Container"), table.getMessage());
      IllegalArgumentException grouped = refused("GroupedBox", "ORDERS", null, "Region");
      assertTrue(grouped.getMessage().contains("inside Group 'Group1'"), grouped.getMessage());
      IllegalArgumentException missing = refused("Ghost", "ORDERS", null, "Region");
      assertTrue(missing.getMessage().contains("Unknown assembly 'Ghost'"), missing.getMessage());
      assertNothingMutated();
   }

   @Test
   void theTableIsInferredWhenExactlyOneCarriesEveryColumnElseRefused() throws Exception {
      setup();
      Result inferred = run("Box", null, null, null, "Total");
      assertEquals("ORDERS", inferred.created().get(0).table());

      IllegalArgumentException ambiguous = refused("Box", null, null, "Region");
      assertTrue(ambiguous.getMessage().contains("more than one table"), ambiguous.getMessage());
      assertTrue(ambiguous.getMessage().contains("ORDERS") &&
                    ambiguous.getMessage().contains("CUSTOMERS"), ambiguous.getMessage());

      IllegalArgumentException none = refused("Box", null, null, "Total", "Nope");
      assertTrue(none.getMessage().contains("No single table carries every column"),
                 none.getMessage());
   }

   @Test
   void aSkippedInsertFailsLoudInsteadOfReportingSuccess() throws Exception {
      setup();
      skipInsert = true;
      IllegalStateException e = assertThrows(IllegalStateException.class,
         () -> run("Box", "ORDERS", null, null, "Region"));
      assertTrue(e.getMessage().contains("did not create a filter for 'Region'"), e.getMessage());
   }

   @Test
   void blankAndMissingArgumentsAreRefused() throws Exception {
      setup();
      assertThrows(IllegalArgumentException.class, () -> service.addFilters(
         "tok", () -> "admin", new Request(null, List.of("Region"), null, null, null), ""));
      assertThrows(IllegalArgumentException.class, () -> service.addFilters(
         "tok", () -> "admin", new Request("Box", List.of(), null, null, null), ""));
      assertThrows(IllegalArgumentException.class, () -> service.addFilters(
         "tok", () -> "admin", new Request("Box", Arrays.asList("Region", " "), null, null, null),
         ""));
      assertNothingMutated();
   }
}
