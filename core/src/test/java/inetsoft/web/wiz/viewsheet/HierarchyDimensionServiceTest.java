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
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.ChartAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.CrosstabAdvancedPaneModel;
import inetsoft.web.composer.model.vs.CrosstabPropertyDialogModel;
import inetsoft.web.composer.model.vs.HierarchyPropertyPaneModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSDimensionMemberModel;
import inetsoft.web.composer.model.vs.VSDimensionModel;
import inetsoft.web.composer.vs.dialog.ChartPropertyDialogService;
import inetsoft.web.composer.vs.dialog.CrosstabPropertyDialogService;
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
class HierarchyDimensionServiceTest {
   /** The write shape: one dimension, members in the order the columns were given. */
   @Test
   void addAppendsADimensionWithMembersInOrder() throws Exception {
      Harness h = harnessChart(paneWith(column("Country"), column("State"), column("City")));

      h.service.add("tok", principal(), "Chart1", List.of("Country", "State", "City"), null, "");

      VSDimensionModel[] written = writtenDimensions(h);
      assertEquals(1, written.length);
      assertEquals(3, written[0].getMembers().length);
      assertEquals("Country", written[0].getMembers()[0].getDataRef().getName());
      assertEquals("State", written[0].getMembers()[1].getDataRef().getName());
      assertEquals("City", written[0].getMembers()[2].getDataRef().getName());
   }

   /**
    * <b>The landmine this service exists to avoid.</b> A column used as a hierarchy member must
    * not also show up in the submitted {@code columnList} -- {@code setCube} treats whatever is
    * left there as measures, so a leftover entry becomes a duplicate measure (or, if a lookup
    * happens to succeed, throws trying to remove it from a fixed-size list). See
    * {@link HierarchyDimensionService}'s class doc.
    */
   @Test
   void addRemovesTheConsumedColumnsFromTheSubmittedColumnList() throws Exception {
      Harness h = harnessChart(
         paneWith(column("Country"), column("State"), column("Revenue")));

      h.service.add("tok", principal(), "Chart1", List.of("Country", "State"), null, "");

      OutputColumnRefModel[] remaining = writtenColumnList(h);
      assertEquals(1, remaining.length);
      assertEquals("Revenue", remaining[0].getName());
   }

   /** The member's {@code dataRef} is the pane's own instance, not a hand-built copy. */
   @Test
   void addUsesTheActualColumnInstanceFromThePane() throws Exception {
      OutputColumnRefModel country = column("Country");
      Harness h = harnessChart(paneWith(country, column("State")));

      h.service.add("tok", principal(), "Chart1", List.of("Country"), null, "");

      assertSame(country, writtenDimensions(h)[0].getMembers()[0].getDataRef());
   }

   @Test
   void addDefaultsToNoDateGroupingWhenOmitted() throws Exception {
      Harness h = harnessChart(paneWith(column("Order Date"), column("State")));

      h.service.add("tok", principal(), "Chart1", List.of("Order Date", "State"), null, "");

      assertEquals(DateRangeRef.NONE_INTERVAL, writtenDimensions(h)[0].getMembers()[0].getOption());
   }

   @Test
   void addAppliesTheRequestedDateLevel() throws Exception {
      Harness h = harnessChart(paneWith(column("Order Date"), column("State")));

      h.service.add("tok", principal(), "Chart1", List.of("Order Date", "State"),
                    List.of("quarter", ""), "");

      assertEquals(DateRangeRef.QUARTER_INTERVAL,
                  writtenDimensions(h)[0].getMembers()[0].getOption());
      assertEquals(DateRangeRef.NONE_INTERVAL,
                  writtenDimensions(h)[0].getMembers()[1].getOption());
   }

   @Test
   void addRefusesAnUnknownDateLevel() {
      Harness h = harnessChart(paneWith(column("Order Date")));

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", List.of("Order Date"),
                                       List.of("fortnight"), ""));
      assertNoWrite(h);
   }

   @Test
   void addRefusesAnUnknownColumn() {
      Harness h = harnessChart(paneWith(column("Country")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.add("tok", principal(), "Chart1", List.of("Nope"), null, ""));
      assertTrue(thrown.getMessage().contains("list_hierarchy_dimensions"));
      assertNoWrite(h);
   }

   @Test
   void addRefusesTheSameColumnTwiceInOneCall() {
      Harness h = harnessChart(paneWith(column("Country"), column("State")));

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1",
                                       List.of("Country", "Country"), null, ""));
      assertNoWrite(h);
   }

   @Test
   void addRefusesNoColumns() {
      Harness h = harnessChart(paneWith(column("Country")));

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", List.of(), null, ""));
      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", null, null, ""));
      assertNoWrite(h);
   }

   @Test
   void addRefusesACubeSourcedAssembly() {
      HierarchyPropertyPaneModel pane = paneWith(column("Country"));
      pane.setCube(true);
      Harness h = harnessChart(pane);

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Chart1", List.of("Country"), null, ""));
      assertNoWrite(h);
   }

   @Test
   void addRefusesANonChartNonCrosstabAssembly() {
      Harness h = harness(mock(TextVSAssembly.class), paneWith(column("Country")));

      assertThrows(Exception.class,
                   () -> h.service.add("tok", principal(), "Text1", List.of("Country"), null, ""));
      assertNoWrite(h);
   }

   @Test
   void addWorksOnACrosstabToo() throws Exception {
      Harness h = harnessCrosstab(paneWith(column("Country"), column("State")));

      h.service.add("tok", principal(), "Crosstab1", List.of("Country", "State"), null, "");

      VSDimensionModel[] written = writtenCrosstabDimensions(h);
      assertEquals(1, written.length);
      assertEquals(2, written[0].getMembers().length);
   }

   /** One call, one checkpoint. */
   @Test
   void addWritesExactlyOnceInOneMutation() throws Exception {
      Harness h = harnessChart(paneWith(column("Country"), column("State")));

      h.service.add("tok", principal(), "Chart1", List.of("Country"), null, "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      verify(h.chartService, times(1)).setChartPropertyModel(
         anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
         any(Principal.class), any());
   }

   @Test
   void removeDropsTheDimensionAndFreesItsColumns() throws Exception {
      VSDimensionModel countryState = dimension(column("Country"), column("State"));
      VSDimensionModel category = dimension(column("Category"));
      HierarchyPropertyPaneModel pane =
         paneWithDimensions(new OutputColumnRefModel[] {
                                column("Country"), column("State"), column("Category"),
                                column("Revenue")
                             },
                             countryState, category);
      Harness h = harnessChart(pane);

      Map<String, Object> result = h.service.remove("tok", principal(), "Chart1", 0, "");

      VSDimensionModel[] written = writtenDimensions(h);
      assertEquals(1, written.length);
      OutputColumnRefModel[] remaining = writtenColumnList(h);
      List<String> names = java.util.Arrays.stream(remaining)
         .map(OutputColumnRefModel::getName).toList();
      assertTrue(names.contains("Country"), "freed by the removal");
      assertTrue(names.contains("State"), "freed by the removal");
      assertFalse(names.contains("Category"), "still consumed by the surviving dimension");
      assertEquals(1, result.get("remaining"));
   }

   @Test
   void removeRefusesAnOutOfRangeIndex() {
      Harness h = harnessChart(paneWithDimensions(
         new OutputColumnRefModel[] { column("Country") }, dimension(column("Country"))));

      Exception thrown = assertThrows(
         Exception.class, () -> h.service.remove("tok", principal(), "Chart1", 4, ""));
      assertTrue(thrown.getMessage().contains("list_hierarchy_dimensions"));
      assertNoWrite(h);
   }

   @Test
   void listDoesNotWrite() throws Exception {
      Harness h = harnessChart(paneWith(column("Country"), column("State")));

      Map<String, Object> out = h.service.list("tok", principal(), "Chart1");

      verify(h.sessions, never()).mutate(anyString(), any(Principal.class), any());
      assertNoWrite(h);
      assertEquals(List.of(), out.get("dimensions"));
      assertEquals(List.of("Country", "State"), out.get("availableColumns"));
   }

   @Test
   void listReportsExistingDimensionsAndTheirFreeColumns() throws Exception {
      HierarchyPropertyPaneModel pane = paneWithDimensions(
         new OutputColumnRefModel[] { column("Country"), column("State"), column("Revenue") },
         dimension(column("Country"), column("State")));
      Harness h = harnessChart(pane);

      Map<String, Object> out = h.service.list("tok", principal(), "Chart1");

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> dims = (List<Map<String, Object>>) out.get("dimensions");
      assertEquals(1, dims.size());
      assertEquals(0, dims.get(0).get("index"));
      assertEquals(List.of("Revenue"), out.get("availableColumns"));
   }

   private static VSDimensionModel[] writtenDimensions(Harness h) throws Exception {
      return writtenChartPane(h).getDimensions();
   }

   private static OutputColumnRefModel[] writtenColumnList(Harness h) throws Exception {
      return writtenChartPane(h).getColumnList();
   }

   private static VSDimensionModel[] writtenCrosstabDimensions(Harness h) throws Exception {
      return writtenCrosstabPane(h).getDimensions();
   }

   private static HierarchyPropertyPaneModel writtenChartPane(Harness h) throws Exception {
      ArgumentCaptor<ChartPropertyDialogModel> captor =
         ArgumentCaptor.forClass(ChartPropertyDialogModel.class);
      verify(h.chartService).setChartPropertyModel(anyString(), anyString(), captor.capture(),
                                                    anyString(), any(Principal.class), any());
      return captor.getValue().getHierarchyPropertyPaneModel();
   }

   private static HierarchyPropertyPaneModel writtenCrosstabPane(Harness h) throws Exception {
      ArgumentCaptor<CrosstabPropertyDialogModel> captor =
         ArgumentCaptor.forClass(CrosstabPropertyDialogModel.class);
      verify(h.crosstabService).setCrosstabPropertyModel(
         anyString(), anyString(), captor.capture(), anyString(), any(Principal.class), any());
      return captor.getValue().getHierarchyPropertyPaneModel();
   }

   private static void assertNoWrite(Harness h) {
      try {
         verify(h.chartService, never()).setChartPropertyModel(
            anyString(), anyString(), any(ChartPropertyDialogModel.class), anyString(),
            any(Principal.class), any());
         verify(h.crosstabService, never()).setCrosstabPropertyModel(
            anyString(), anyString(), any(CrosstabPropertyDialogModel.class), anyString(),
            any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static OutputColumnRefModel column(String name) {
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setName(name);
      column.setEntity(null);
      column.setAttribute(name);
      column.setDataType(name.toLowerCase().contains("date") ? "timestamp" : "string");
      return column;
   }

   private static VSDimensionModel dimension(OutputColumnRefModel... columns) {
      VSDimensionMemberModel[] members = new VSDimensionMemberModel[columns.length];

      for(int i = 0; i < columns.length; i++) {
         VSDimensionMemberModel member = new VSDimensionMemberModel();
         member.setDataRef(columns[i]);
         members[i] = member;
      }

      VSDimensionModel dim = new VSDimensionModel();
      dim.setMembers(members);
      return dim;
   }

   private static HierarchyPropertyPaneModel paneWith(OutputColumnRefModel... columns) {
      return paneWithDimensions(columns);
   }

   private static HierarchyPropertyPaneModel paneWithDimensions(OutputColumnRefModel[] columns,
                                                                 VSDimensionModel... dimensions)
   {
      HierarchyPropertyPaneModel pane = new HierarchyPropertyPaneModel();
      pane.setColumnList(columns);
      pane.setDimensions(dimensions);
      pane.setCube(false);
      return pane;
   }

   private record Harness(HierarchyDimensionService service, ViewsheetSessionService sessions,
                          ChartPropertyDialogService chartService,
                          CrosstabPropertyDialogService crosstabService) {}

   private static Harness harnessChart(HierarchyPropertyPaneModel pane) {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      return harness(assembly, pane);
   }

   private static Harness harnessCrosstab(HierarchyPropertyPaneModel pane) {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      return harness(assembly, pane);
   }

   private static Harness harness(VSAssembly assembly, HierarchyPropertyPaneModel pane) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      ChartPropertyDialogService chartService = mock(ChartPropertyDialogService.class);
      CrosstabPropertyDialogService crosstabService = mock(CrosstabPropertyDialogService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         if(assembly instanceof ChartVSAssembly) {
            when(chartService.getChartPropertyDialogModel(anyString(), anyString(),
                                                          any(Principal.class)))
               .thenReturn(chartModel(pane));
         }
         else if(assembly instanceof CrosstabVSAssembly) {
            when(crosstabService.getCrosstabPropertyDialogModel(anyString(), anyString(),
                                                                any(Principal.class)))
               .thenReturn(crosstabModel(pane));
         }
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new HierarchyDimensionService(sessions, chartService, crosstabService),
                         sessions, chartService, crosstabService);
   }

   private static ChartPropertyDialogModel chartModel(HierarchyPropertyPaneModel pane) {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      model.setChartAdvancedPaneModel(new ChartAdvancedPaneModel());
      model.setHierarchyPropertyPaneModel(pane);
      return model;
   }

   private static CrosstabPropertyDialogModel crosstabModel(HierarchyPropertyPaneModel pane) {
      CrosstabPropertyDialogModel model = new CrosstabPropertyDialogModel();
      model.setCrosstabAdvancedPaneModel(new CrosstabAdvancedPaneModel());
      model.setHierarchyPropertyPaneModel(pane);
      return model;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
