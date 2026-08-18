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
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CalcTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
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
class TableBindingServiceTest {
   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   @Test
   void setShelfPostsTheModelItReadRatherThanAFreshOne() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.getName2Labels().put("Region", "Sales Region");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")));

      ApplyVSAssemblyInfoEvent event = capture(bindings);
      CrosstabBindingModel posted = (CrosstabBindingModel) event.getBinding();
      assertEquals("Sales Region", posted.getName2Labels().get("Region"),
                   "column labels must survive a shelf write no tool here touches");
      assertEquals(1, posted.getRows().size());
      assertEquals("Crosstab1", event.getName());
   }

   /**
    * The trap flag reports a binding that would produce a cartesian result. Turning it off to
    * make a call succeed trades a reported problem for an unreported one.
    */
   @Test
   void leavesTrapCheckingOn() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), new CrosstabBindingModel(), bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")));

      assertTrue(capture(bindings).isCheckTrap());
   }

   @Test
   void aPivotIsOneCheckpointNotTwo() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("Year")));
      ViewsheetSessionService sessions = sessionsFor(mock(CrosstabVSAssembly.class));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      serviceWith(sessions, existing, bindings)
         .moveField("tok", principal(), "Crosstab1", "rows", "cols", "Year", null);

      verify(sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size());
      assertEquals(1, posted.getCols().size());
   }

   @Test
   void addAndRemoveDelegate() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null);

      assertEquals(1, ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   @Test
   void readReportsTheObjectTypeAndShelvesWithoutMutating() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableBindingMutator.setShelf(existing, "groups", List.of(dim("Region")));
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));

      Map<String, Object> read = serviceWith(sessions, existing,
                                             mock(VSBindingModelService.class))
         .read("tok", principal(), "Table1");

      assertEquals("table", read.get("objectType"));
      @SuppressWarnings("unchecked")
      Map<String, Object> shelves = (Map<String, Object>) read.get("shelves");
      assertTrue(shelves.containsKey("groups"));
      assertTrue(shelves.containsKey("details"));
      assertFalse(shelves.containsKey("rows"), "a table has no rows shelf");
      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void refusesAChartNamingIt() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(ChartVSAssembly.class)),
         new inetsoft.web.binding.model.ChartBindingModel(), mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setShelf("tok", principal(), "Chart1", "rows", List.of(dim("Region"))));
      assertTrue(thrown.getMessage().contains("Chart1"));
      assertTrue(thrown.getMessage().contains("chart"));
   }

   @Test
   void refusesACalcTablePointingAtItsCellLayout() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(CalcTableVSAssembly.class)), new CrosstabBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.read("tok", principal(), "Calc1"));
      assertTrue(thrown.getMessage().contains("cell layout"));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   // ── set_table_source ──────────────────────────────────────────────────────
   //
   // A crosstab or table added in the Composer starts with no source. Its shelves can be
   // populated — set_table_fields reports success — and it renders nothing at all, because
   // shelves with no source have nothing to query. Nothing in the plugin could assign one.

   @Test
   void setSourceAssignsAnAssetSourceByName() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS", false);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(inetsoft.uql.asset.SourceInfo.ASSET, posted.getSource().getType(),
                   "worksheet tables bind as ASSET — the form used everywhere else");
   }

   @Test
   void setSourceRefusesATableTheAssemblyCannotSee() {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(mock(CrosstabVSAssembly.class), existing, mock(VSBindingModelService.class))
            .setSource("tok", principal(), "Crosstab1", "NOPE", false));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("ORDERS"), "list what it can bind to");
   }

   /**
    * Repointing a bound assembly discards every field on its shelves, because the columns
    * belong to the old source. Doing that silently on one call is the failure mode this whole
    * plugin family exists to avoid.
    */
   @Test
   void setSourceRefusesToDiscardBoundFieldsUnlessForced() {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setRows(List.of(new inetsoft.web.binding.model.BDimensionRefModel()));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness(mock(CrosstabVSAssembly.class), existing, mock(VSBindingModelService.class))
            .setSource("tok", principal(), "Crosstab1", "CUSTOMERS", false));

      assertTrue(thrown.getMessage().contains("force"), "name the way through");
   }

   @Test
   void setSourceProceedsWhenForced() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setRows(List.of(new inetsoft.web.binding.model.BDimensionRefModel()));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "CUSTOMERS", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("CUSTOMERS", posted.getSource().getSource());
   }

   /**
    * A calc table has a source like any other data assembly — {@code CalcTableVSAssembly} extends
    * {@code TableDataVSAssembly} and {@code CalcTableBindingModel} extends
    * {@code BaseTableBindingModel}. The blanket calc-table refusal exists because *shelves* do not
    * apply to it, but assigning a source is not a shelf operation, and without this a freehand
    * table can never be pointed at data: its cells bind, and it renders empty forever.
    */
   @Test
   void setSourceAcceptsACalcTableBecauseSourceIsNotAShelfOperation() throws Exception {
      CalcTableBindingModel existing = new CalcTableBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName("ORDERS");
      tables.add(table);
      existing.setTables(tables);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CalcTableVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Calc1", "ORDERS", false);

      BaseTableBindingModel posted = (BaseTableBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS", posted.getSource().getSource());
   }

   @Test
   void shelfWritesStillRefuseACalcTable() {
      TableBindingService service = serviceWith(
         sessionsFor(mock(CalcTableVSAssembly.class)), new CalcTableBindingModel(),
         mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         Exception.class,
         () -> service.setShelf("tok", principal(), "Calc1", "rows", List.of(dim("Region"))));
      assertTrue(thrown.getMessage().contains("cell layout"));
   }

   private static CrosstabBindingModel withTables(String... names) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();

      for(String name : names) {
         BindingModel.SourceTable table = new BindingModel.SourceTable();
         table.setName(name);
         tables.add(table);
      }

      model.setTables(tables);
      return model;
   }

   private static ApplyVSAssemblyInfoEvent capture(VSBindingModelService bindings)
      throws Exception
   {
      ArgumentCaptor<ApplyVSAssemblyInfoEvent> captor =
         ArgumentCaptor.forClass(ApplyVSAssemblyInfoEvent.class);
      verify(bindings).setBinding(eq("rt1"), captor.capture(), any(Principal.class), any());
      return captor.getValue();
   }

   private static TableBindingService harness(VSAssembly assembly, BindingModel model,
                                              VSBindingModelService bindings)
   {
      return serviceWith(sessionsFor(assembly), model, bindings);
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
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return sessions;
   }

   private static TableBindingService serviceWith(ViewsheetSessionService sessions,
                                                  BindingModel model,
                                                  VSBindingModelService bindings)
   {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(sessions, binding, bindings);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
