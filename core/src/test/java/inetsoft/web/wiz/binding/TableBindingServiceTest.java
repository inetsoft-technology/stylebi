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
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

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
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

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
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());

      harness(assembly, existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, null);

      assertEquals(1, ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   // ── bug #76350, PCB-004: set_table_fields/add_table_field reported ok:true and never
   // established a source on a sourceless crosstab/table, so the assembly rendered empty with no
   // error -- the identical shape PCB-002 already fixed for charts (ChartBindingService.setShelf
   // + applySource). BindingAgentControllerTest drives the resolve-then-refuse half through the
   // controller endpoint; these assert the service actually applies a resolved source.
   @Test
   void setShelfEstablishesTheGivenSourceWhenTheModelHasNone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), "ORDERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(), "the shelf write itself must still land");
   }

   @Test
   void setShelfLeavesAnAlreadyBoundSourceAlone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), "CUSTOMERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS", posted.getSource().getSource(),
                   "a bound source is never repointed as a side effect of a shelf write -- " +
                   "that is set_table_source with force, not this");
   }

   @Test
   void addFieldEstablishesTheGivenSourceWhenTheModelHasNone() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, "ORDERS");

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertNotNull(posted.getSource(), "the model must carry a source after the write");
      assertEquals("ORDERS", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(), "the field write itself must still land");
   }

   @Test
   void readReportsTheObjectTypeAndShelvesWithoutMutating() throws Exception {
      TableBindingModel existing = new TableBindingModel();
      TableBindingMutator.setShelf(existing, "details", List.of(dim("Region")));
      ViewsheetSessionService sessions = sessionsFor(mock(TableVSAssembly.class));

      Map<String, Object> read = serviceWith(sessions, existing,
                                             mock(VSBindingModelService.class))
         .read("tok", principal(), "Table1");

      assertEquals("table", read.get("objectType"));
      @SuppressWarnings("unchecked")
      Map<String, Object> shelves = (Map<String, Object>) read.get("shelves");
      assertTrue(shelves.containsKey("details"));
      assertFalse(shelves.containsKey("groups"),
                  "a table has no grouping — that is Crosstab's job — and this shelf can never " +
                  "be written, so it should not be advertised as readable either");
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
         () -> service.setShelf("tok", principal(), "Chart1", "rows", List.of(dim("Region")),
                                null));
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
      assertTrue(posted.getRows().isEmpty(),
                 "force:true must discard the old source's fields, not just skip the refusal");
   }

   /**
    * The regression for the original bug where {@code force:true} skipped the refusal but never
    * actually discarded anything: setSource left every shelf's old-source field refs in place,
    * and those stale refs were written straight back onto the live assembly by the factory that
    * follows this mutation — read back later (e.g. via get_binding) as fields from a source the
    * assembly no longer has. This case uses a new source whose columns genuinely do not include
    * any of the old ones, so every field is correctly discarded either way; the sibling test
    * {@link #setSourceKeepsFieldsThatStillResolveInTheNewSourceWhenForced} covers the case this
    * one cannot distinguish -- a column that exists in both sources.
    */
   @Test
   void setSourceDiscardsFieldsThatDoNotResolveInTheNewSourceWhenForced() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("CUSTOMERS", List.of("STATE", "RESELLER", "CUSTOMER_ID"),
               "ORDERS1", List.of("ORDER_ID", "ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("STATE")));
      TableBindingMutator.setShelf(existing, "cols", List.of(dim("RESELLER")));
      TableBindingMutator.setShelf(existing, "aggregates",
                                   List.of(new FieldRef("CUSTOMER_ID", "measure", "sum", null,
                                                        null)));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS1", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS1", posted.getSource().getSource());
      assertTrue(posted.getRows().isEmpty(),
                 "STATE does not exist in ORDERS1, so it must not survive");
      assertTrue(posted.getCols().isEmpty(),
                 "RESELLER does not exist in ORDERS1, so it must not survive");
      assertTrue(posted.getAggregates().isEmpty(),
                 "CUSTOMER_ID does not exist in ORDERS1, so it must not survive");
   }

   /**
    * The regression for the divergence the parity audit found relative to the UI's own repoint
    * path: {@code VSAssemblyInfoHandler}'s own comment states the intent plainly ("check the old
    * binding columns when source changed, if cannot found the columns in the source, just remove
    * them") -- implying a column that DOES still resolve must be kept, not blanket-discarded.
    * Repointing to a same-shaped source (e.g. a partitioned/monthly sibling table) must not lose
    * bindings a human doing the equivalent repoint would keep.
    */
   @Test
   void setSourceKeepsFieldsThatStillResolveInTheNewSourceWhenForced() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("ORDERS", List.of("ORDER_ID", "ORDER_DATE", "REGION"),
               "ORDERS_V2", List.of("ORDER_ID", "ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("ORDER_DATE"), dim("REGION")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS_V2", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals("ORDERS_V2", posted.getSource().getSource());
      assertEquals(1, posted.getRows().size(),
                   "ORDER_DATE still exists in ORDERS_V2 and must survive; REGION does not and " +
                   "must not");
      assertEquals("ORDER_DATE", posted.getRows().get(0).getColumnValue());
   }

   /**
    * The regression for the repair-review finding on the fix above: {@link #columnsOf} already
    * matches a qualified new-source column ({@code "table.attribute"}) against an unqualified
    * old field, but the reverse direction -- an old bound field whose own column name is
    * qualified (because ITS source was the joined/merged table) repointed at a new source whose
    * columns are unqualified -- went unhandled, so the field was discarded even though the same
    * repoint done by a human in the UI would keep it.
    */
   @Test
   void setSourceKeepsAQualifiedOldFieldThatResolvesUnqualifiedInTheNewSource() throws Exception {
      CrosstabBindingModel existing = withTablesAndColumns(
         Map.of("ORDERS", List.of("ORDERS.ORDER_DATE", "ORDERS.REGION"),
               "ORDERS_V2", List.of("ORDER_DATE")));
      TableBindingMutator.setShelf(existing, "rows",
                                   List.of(dim("ORDERS.ORDER_DATE"), dim("ORDERS.REGION")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS_V2", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size(),
                   "ORDERS.ORDER_DATE resolves against ORDERS_V2's unqualified ORDER_DATE and " +
                   "must survive; ORDERS.REGION does not and must not");
      assertEquals("ORDERS.ORDER_DATE", posted.getRows().get(0).getColumnValue());
   }

   /**
    * force:true against the source the assembly already has is a no-op repoint, not a real
    * source change — nothing to discard, and doing so anyway would destroy a binding the caller
    * never asked to touch.
    */
   @Test
   void setSourceForcedButUnchangedKeepsFields() throws Exception {
      CrosstabBindingModel existing = withTables("ORDERS", "CUSTOMERS");
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("STATE")));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(mock(CrosstabVSAssembly.class), existing, bindings)
         .setSource("tok", principal(), "Crosstab1", "ORDERS", true);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size(),
                    "the source didn't actually change, so nothing should be discarded");
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
         () -> service.setShelf("tok", principal(), "Calc1", "rows", List.of(dim("Region")),
                                null));
      assertTrue(thrown.getMessage().contains("cell layout"));
   }

   // ── no-source refusal ─────────────────────────────────────────────────────
   //
   // set_table_fields/add_table_field must refuse a non-empty shelf write on an assembly with
   // no source, rather than silently applying it and rendering nothing — see setSource's javadoc
   // above for why nothing else here catches this.

   @Test
   void setShelfRefusesWhenAssemblyHasNoSource() {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      TableBindingService service =
         serviceWith(sessionsFor(assembly), new CrosstabBindingModel(),
                     mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")),
                                null));
      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("set_table_source"));
   }

   @Test
   void setShelfClearingAnEmptyShelfIsNotRefusedEvenWithNoSource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, new CrosstabBindingModel(), bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(), null);

      assertEquals(0,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   @Test
   void setShelfProceedsWhenAssemblyHasASource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .setShelf("tok", principal(), "Crosstab1", "rows", List.of(dim("Region")), null);

      assertEquals(1,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   @Test
   void addFieldRefusesWhenAssemblyHasNoSource() {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      TableBindingService service =
         serviceWith(sessionsFor(assembly), new CrosstabBindingModel(),
                     mock(VSBindingModelService.class));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null,
                                null));
      assertTrue(thrown.getMessage().contains("Crosstab1"));
      assertTrue(thrown.getMessage().contains("set_table_source"));
   }

   @Test
   void addFieldProceedsWhenAssemblyHasASource() throws Exception {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(new inetsoft.uql.asset.SourceInfo());
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(new inetsoft.web.binding.model.SourceInfo());
      existing.getSource().setSource("ORDERS");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .addField("tok", principal(), "Crosstab1", "rows", dim("Region"), null, null);

      assertEquals(1,
         ((CrosstabBindingModel) capture(bindings).getBinding()).getRows().size());
   }

   /**
    * The guard is scoped to {@link TableBindingService#setShelf}/{@link
    * TableBindingService#addField}'s own call sites, not pushed into {@code applyWithContext}
    * itself — {@link TableBindingService#moveField} shares that plumbing and must not be refused
    * on a sourceless assembly, since by construction a sourceless assembly's shelves can never
    * have anything on them to move once setShelf/addField are guarded.
    */
   @Test
   void moveFieldIsNotRefusedEvenWithNoSource() throws Exception {
      CrosstabBindingModel existing = new CrosstabBindingModel();
      TableBindingMutator.setShelf(existing, "rows", List.of(dim("Region"), dim("Year")));
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(null);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      harness(assembly, existing, bindings)
         .moveField("tok", principal(), "Crosstab1", "rows", "cols", "Year", null);

      CrosstabBindingModel posted = (CrosstabBindingModel) capture(bindings).getBinding();
      assertEquals(1, posted.getRows().size());
      assertEquals(1, posted.getCols().size());
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

   /** Like {@link #withTables}, but each table carries the column names given for it. */
   private static CrosstabBindingModel withTablesAndColumns(Map<String, List<String>> byTable) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      List<BindingModel.SourceTable> tables = new ArrayList<>();

      for(Map.Entry<String, List<String>> entry : byTable.entrySet()) {
         BindingModel.SourceTable table = new BindingModel.SourceTable();
         table.setName(entry.getKey());
         List<BindingModel.SourceTableColumn> columns = new ArrayList<>();

         for(String column : entry.getValue()) {
            columns.add(new BindingModel.SourceTableColumn(column, "string"));
         }

         table.setColumns(columns);
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
      return new TableBindingService(sessions, binding, bindings,
                                     mock(inetsoft.web.binding.service.DataRefModelFactoryService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
