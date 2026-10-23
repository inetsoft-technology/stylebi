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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.viewsheet.service.VSOutputService;
import inetsoft.web.wiz.binding.BindableFieldsService.AggregateSource;
import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The Charts and Crosstabs a range slider can filter on their aggregated values appear in the
 * listing of a range slider, and only there.
 */
@Tag("core")
class BindableFieldsServiceAggregateTest {
   @Test
   void aRangeSlidersListingAddsTheAggregateTablesWithADistinctTag() throws Exception {
      Fixture fixture = new Fixture(mock(TimeSliderVSAssembly.class));

      List<BindableTable> tables = fixture.service.list("rt1", "Slider1", principal());

      assertEquals(List.of("ORDERS", "Chart1"), tables.stream().map(BindableTable::name).toList());
      BindableTable orders = tables.get(0);
      BindableTable chart = tables.get(1);
      assertNull(orders.sourceType());
      assertEquals(BindableTable.VIEWSHEET_AGGREGATE, chart.sourceType());
      assertTrue(chart.isAggregate());
      assertFalse(chart.isComponent());
      assertEquals(List.of(new BindableField("Sum(Product:Total)", "double", "measure"),
                           new BindableField("Count(Order:Id)", "double", "measure")),
                   chart.fields());
      assertEquals(Boolean.FALSE, chart.current());
      // The tree reads the components' runtime info under the read lock.
      verify(fixture.box).lockRead();
      verify(fixture.box).unlockRead();
   }

   @Test
   void theTablesTheTreeOffersAreExposedUnchanged() throws Exception {
      Fixture fixture = new Fixture(mock(TimeSliderVSAssembly.class));

      List<AggregateSource> sources = fixture.service.aggregateSources("rt1", principal());

      assertEquals(1, sources.size());
      assertEquals("Chart1", sources.get(0).name());
      assertSame(fixture.total, sources.get(0).columns().get(0));
   }

   @Test
   void anUnscopedListingDoesNotOfferAggregates() throws Exception {
      Fixture fixture = new Fixture(mock(TimeSliderVSAssembly.class));

      List<BindableTable> tables = fixture.service.list("rt1", null, principal());

      assertEquals(List.of("ORDERS"), tables.stream().map(BindableTable::name).toList());
      verify(fixture.outputs, never()).getRangeSliderTablesTree(any(), any(), anyBoolean());
   }

   @Test
   void aChartOrASelectionListScopedListingDoesNotOfferAggregates() throws Exception {
      for(VSAssembly target : List.of(mock(ChartVSAssembly.class),
                                      mock(SelectionListVSAssembly.class)))
      {
         Fixture fixture = new Fixture(target);

         List<BindableTable> tables = fixture.service.list("rt1", "Slider1", principal());

         assertTrue(tables.stream().noneMatch(BindableTable::isAggregate),
                    target.getClass().getName());
         verify(fixture.outputs, never()).getRangeSliderTablesTree(any(), any(), anyBoolean());
      }
   }

   @Test
   void aViewsheetWithoutARuntimeSandboxOffersNoAggregates() throws Exception {
      Fixture fixture = new Fixture(mock(TimeSliderVSAssembly.class));
      when(fixture.rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      assertTrue(fixture.service.aggregateSources("rt1", principal()).isEmpty());
      assertEquals(List.of("ORDERS"),
                   fixture.service.list("rt1", "Slider1", principal()).stream()
                      .map(BindableTable::name).toList());
   }

   private static final class Fixture {
      Fixture(VSAssembly target) throws Exception {
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly("Slider1")).thenReturn(target);
         rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         box = mock(ViewsheetSandbox.class);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
         ViewsheetService engine = mock(ViewsheetService.class);
         when(engine.getViewsheet(eq("rt1"), any(Principal.class))).thenReturn(rvs);

         VSBindingTreeService tree = mock(VSBindingTreeService.class);
         TreeNodeModel worksheet = worksheetTree();
         when(tree.getBinding(eq("rt1"), any(), anyBoolean(), any(Principal.class), any()))
            .thenReturn(worksheet);

         total = aggregate("Chart1", "Sum(Product:Total)");
         OutputColumnRefModel count = aggregate("Chart1", "Count(Order:Id)");
         outputs = mock(VSOutputService.class);
         TreeNodeModel components = componentsTree(total, count);
         when(outputs.getRangeSliderTablesTree(eq(rvs), any(Principal.class), anyBoolean()))
            .thenReturn(components);
         service = new BindableFieldsService(tree, engine, outputs);
      }

      final RuntimeViewsheet rvs;
      final ViewsheetSandbox box;
      final VSOutputService outputs;
      final OutputColumnRefModel total;
      final BindableFieldsService service;
   }

   private static TreeNodeModel worksheetTree() {
      TreeNodeModel amount = TreeNodeModel.builder().label("AMOUNT").leaf(true)
         .data(entry(AssetEntry.Type.COLUMN, "/ORDERS/AMOUNT")).build();
      TreeNodeModel orders = TreeNodeModel.builder().label("ORDERS")
         .data(entry(AssetEntry.Type.TABLE, "/ORDERS")).addChildren(amount).build();
      return TreeNodeModel.builder().label("root").addChildren(orders).build();
   }

   /**
    * The range slider tree: the worksheet tables, then the "Components" folder holding one TABLE
    * node per chart/crosstab under {@code /assemblies/}, whose columns are the dialog's own nodes.
    */
   private static TreeNodeModel componentsTree(OutputColumnRefModel... columns) {
      TreeNodeModel.Builder chart = TreeNodeModel.builder().label("Chart1")
         .data(entry(AssetEntry.Type.TABLE, "/assemblies/Chart1"));

      for(OutputColumnRefModel column : columns) {
         chart.addChildren(TreeNodeModel.builder().label(column.getAttribute()).leaf(true)
                              .data(column).type("columnNode").build());
      }

      TreeNodeModel components = TreeNodeModel.builder().label("Components")
         .data(entry(AssetEntry.Type.REPOSITORY_FOLDER, "/Components"))
         .addChildren(chart.build()).build();
      return TreeNodeModel.builder().addChildren(worksheetTree()).addChildren(components).build();
   }

   private static OutputColumnRefModel aggregate(String assembly, String attribute) {
      OutputColumnRefModel ref = new OutputColumnRefModel();
      ref.setAttribute(attribute);
      ref.setDataType("double");
      ref.setTable(assembly);
      return ref;
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getType()).thenReturn(type);
      when(entry.getPath()).thenReturn(path);
      return entry;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
