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
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78530 (item 2): an aggregate calc field ({@code baseOnDetail} false) put on a crosstab's
 * rows/cols shelf or a table's details shelf used to pass every check, commit, and then fail at
 * query time. It is now refused inside the mutation, so {@code setBinding} never runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingAggregateCalcFieldShelfTest {
   private static final String TABLE = "Orders";
   private static final SourceInfo ORDERS = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void refusesAnAggregateCalcFieldOnCrosstabColsNamingTheFieldAndShelf() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(model, "cols", List.of(dim("Total Short")),
                                      rvsWithCalc(TABLE, "Total Short", false), ORDERS,
                                      refModelService()));

      assertTrue(e.getMessage().contains("'Total Short' is an aggregate calc field"),
                 e.getMessage());
      assertTrue(e.getMessage().contains("cols shelf"), e.getMessage());
      assertTrue(e.getMessage().contains("aggregates shelf"), e.getMessage());
      assertTrue(model.getCols().isEmpty());
   }

   @Test
   void refusesAnAggregateCalcFieldOnCrosstabRowsAndOnAddField() {
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);

      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(new CrosstabBindingModel(), "rows",
                                      List.of(dim("Total Short")), rvs, ORDERS,
                                      refModelService()));
      // untyped, the way an agent may send it
      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.addField(new CrosstabBindingModel(), "cols",
                                      new FieldRef("Total Short", null, null, null, null), null,
                                      rvs, ORDERS, refModelService()));
   }

   @Test
   void refusesAnAggregateCalcFieldOnATablesDetails() {
      TableBindingModel model = new TableBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(model, "details", List.of(dim("Total Short")),
                                      rvsWithCalc(TABLE, "Total Short", false), ORDERS,
                                      refModelService()));

      assertTrue(e.getMessage().contains("details shelf"), e.getMessage());
      assertTrue(e.getMessage().contains("crosstab's aggregates"), e.getMessage());
   }

   /** moveField's convertForShelf turns the measure into a dimension; the target write refuses. */
   @Test
   void refusesMovingAnAggregateCalcFieldFromAggregatesToCols() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "aggregates", List.of(measure("Total Short")), rvs,
                                   ORDERS, refModelService());

      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.moveField(model, "aggregates", "cols", "Total Short", null, rvs,
                                       ORDERS, refModelService()));
      assertTrue(model.getCols().isEmpty());
   }

   /** Control: a detail calc field is an ordinary column and goes anywhere. */
   @Test
   void acceptsADetailCalcFieldOnRowsColsAndDetails() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Net Sales", true);
      CrosstabBindingModel crosstab = new CrosstabBindingModel();
      TableBindingModel table = new TableBindingModel();

      TableBindingMutator.setShelf(crosstab, "rows", List.of(dim("Net Sales")), rvs, ORDERS,
                                   refModelService());
      TableBindingMutator.setShelf(crosstab, "cols", List.of(dim("Region")), rvs, ORDERS,
                                   refModelService());
      TableBindingMutator.setShelf(table, "details", List.of(dim("Net Sales")), rvs, ORDERS,
                                   refModelService());

      assertEquals(1, crosstab.getRows().size());
      assertEquals(1, table.getDetails().size());
   }

   /** A same-named aggregate calc field on another table must not refuse an ordinary column. */
   @Test
   void looksTheCalcFieldUpOnTheAssemblysOwnSourceOnly() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc("Other", "Total Short", false);
      when(rvs.getViewsheet().getCalcFieldSources()).thenReturn(List.of("Other"));
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableBindingMutator.setShelf(model, "cols", List.of(dim("Total Short")), rvs, ORDERS,
                                   refModelService());

      assertEquals(1, model.getCols().size());
   }

   /**
    * [A1]/[R3]: a crosstab sourced in this same call has an empty live SourceInfo; the lookup
    * falls back to the model's source, which applySource has already set.
    */
   @Test
   void fallsBackToTheModelSourceWhenTheLiveSourceIsEmpty() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setSource(BindingSources.assetSource(TABLE));
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);

      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(model, "rows", List.of(dim("Total Short")), rvs,
                                      new SourceInfo(), refModelService()));
      assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.setShelf(model, "rows", List.of(dim("Total Short")), rvs, null,
                                      refModelService()));
   }

   /**
    * A shelf an earlier server left holding one refuses further edits until the field is removed,
    * and remove_table_field (and moving it back to aggregates) clears it.
    */
   @Test
   void aStuckShelfCanStillBeCleared() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "cols", List.of(dim("Region"), dim("Total Short")));

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         TableBindingMutator.addField(model, "cols", dim("State"), null, rvs, ORDERS,
                                      refModelService()));
      assertTrue(e.getMessage().contains("remove_table_field"), e.getMessage());

      CrosstabBindingModel moved = new CrosstabBindingModel();
      TableBindingMutator.setShelf(moved, "cols", List.of(dim("Region"), dim("Total Short")));
      TableBindingMutator.moveField(moved, "cols", "aggregates", "Total Short", null, rvs,
                                    ORDERS, refModelService());
      assertEquals(1, moved.getCols().size());
      assertEquals(1, moved.getAggregates().size());

      TableBindingMutator.removeField(model, "cols", "Total Short", rvs, ORDERS,
                                      refModelService());
      assertEquals(1, model.getCols().size());
      assertEquals("Region", model.getCols().get(0).getName());
   }

   // ── through TableBindingService: refused before setBinding, so nothing is committed ───────

   @Test
   void moveTableFieldIsRefusedBeforeTheWrite() throws Exception {
      CrosstabVSAssembly assembly = crosstab(ORDERS);
      CrosstabBindingModel existing = new CrosstabBindingModel();
      existing.setSource(BindingSources.assetSource(TABLE));
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);
      TableBindingMutator.setShelf(existing, "aggregates", List.of(measure("Total Short")), rvs,
                                   ORDERS, refModelService());
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      assertThrows(IllegalArgumentException.class, () ->
         service(rvs, assembly, existing, bindings)
            .moveField("tok", principal(), "Crosstab1", "aggregates", "cols", "Total Short",
                       null));

      verify(bindings, never()).setBinding(anyString(), any(), any(), any());
   }

   @Test
   void addTableFieldOnAnAssemblySourcedInTheSameCallIsRefusedBeforeTheWrite() throws Exception {
      CrosstabVSAssembly assembly = crosstab(null);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      assertThrows(IllegalArgumentException.class, () ->
         service(rvsWithCalc(TABLE, "Total Short", false), assembly, new CrosstabBindingModel(),
                 bindings)
            .addField("tok", principal(), "Crosstab1", "cols", dim("Total Short"), null, TABLE));

      verify(bindings, never()).setBinding(anyString(), any(), any(), any());
   }

   @Test
   void setTableFieldsOnATablesDetailsIsRefusedBeforeTheWrite() throws Exception {
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(ORDERS);
      TableBindingModel existing = new TableBindingModel();
      existing.setSource(BindingSources.assetSource(TABLE));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      assertThrows(IllegalArgumentException.class, () ->
         service(rvsWithCalc(TABLE, "Total Short", false), assembly, existing, bindings)
            .setShelf("tok", principal(), "Table1", "details", List.of(dim("Total Short")),
                      null));

      verify(bindings, never()).setBinding(anyString(), any(), any(), any());
   }

   // ── helpers ──────────────────────────────────────────────────────────────

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef measure(String column) {
      return new FieldRef(column, "measure", null, null, null);
   }

   private static RuntimeViewsheet rvsWithCalc(String table, String column, boolean baseOnDetail) {
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(new AttributeRef(column));
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcField(table, column)).thenReturn(calc);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static CrosstabVSAssembly crosstab(SourceInfo source) {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getSourceInfo()).thenReturn(source);
      return assembly;
   }

   private static TableBindingService service(RuntimeViewsheet rvs, VSAssembly assembly,
                                              BindingModel model, VSBindingModelService bindings)
      throws Exception
   {
      when(rvs.getViewsheet().getAssembly(anyString())).thenReturn(assembly);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(sessions, binding, bindings, refModelService(),
                                     mock(inetsoft.web.composer.vs.dialog.HideColumnsDialogService.class),
                                     mock(inetsoft.web.binding.handler.VSAssemblyInfoHandler.class));
   }

   private static DataRefModelFactoryService refModelService() {
      return mock(DataRefModelFactoryService.class);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
