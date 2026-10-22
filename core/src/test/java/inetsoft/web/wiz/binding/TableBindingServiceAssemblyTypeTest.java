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
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.service.*;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78533: every table/* wiz endpoint on an assembly that is not a table or crosstab leaked
 * {@code VSBindingService.createModel}'s raw "No model factory registered for assembly type
 * inetsoft.uql.viewsheet.SelectionListVSAssembly" instead of a refusal, because
 * {@code requireTableBinding} built the binding model before checking the assembly's type.
 *
 * <p>Unlike {@link TableBindingServiceTest}, this drives a real {@code VSBindingService} with the
 * real model factories registered (spied only to observe {@code createModel}), over real
 * assemblies. A mocked {@code createModel} would hide the ordering, since it never throws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingServiceAssemblyTypeTest {
   /** One assembly of every type the table tools cannot bind. */
   static Stream<Arguments> nonTableAssemblies() {
      return Stream.of(
         Arguments.of("SelectionList1",
            (BiFunction<Viewsheet, String, VSAssembly>) SelectionListVSAssembly::new,
            "a SelectionList"),
         Arguments.of("SelectionTree1",
            (BiFunction<Viewsheet, String, VSAssembly>) SelectionTreeVSAssembly::new,
            "a SelectionTree"),
         Arguments.of("Gauge1",
            (BiFunction<Viewsheet, String, VSAssembly>) GaugeVSAssembly::new, "a Gauge"),
         Arguments.of("Text1",
            (BiFunction<Viewsheet, String, VSAssembly>) TextVSAssembly::new, "a Text"),
         Arguments.of("Image1",
            (BiFunction<Viewsheet, String, VSAssembly>) ImageVSAssembly::new, "an Image"),
         Arguments.of("Calendar1",
            (BiFunction<Viewsheet, String, VSAssembly>) CalendarVSAssembly::new, "a Calendar"),
         Arguments.of("TimeSlider1",
            (BiFunction<Viewsheet, String, VSAssembly>) TimeSliderVSAssembly::new,
            "a TimeSlider"),
         Arguments.of("ComboBox1",
            (BiFunction<Viewsheet, String, VSAssembly>) ComboBoxVSAssembly::new, "a ComboBox"),
         Arguments.of("CurrentSelection1",
            (BiFunction<Viewsheet, String, VSAssembly>) CurrentSelectionVSAssembly::new,
            "a CurrentSelection"),
         Arguments.of("Embedded1",
            (BiFunction<Viewsheet, String, VSAssembly>) (vs, name) ->
               new Viewsheet().createVSAssembly(name),
            "an embedded viewsheet"),
         Arguments.of("CheckBox1",
            (BiFunction<Viewsheet, String, VSAssembly>) CheckBoxVSAssembly::new, "a CheckBox"),
         Arguments.of("RadioButton1",
            (BiFunction<Viewsheet, String, VSAssembly>) RadioButtonVSAssembly::new,
            "a RadioButton"),
         Arguments.of("Slider1",
            (BiFunction<Viewsheet, String, VSAssembly>) SliderVSAssembly::new, "a Slider"),
         Arguments.of("TextInput1",
            (BiFunction<Viewsheet, String, VSAssembly>) TextInputVSAssembly::new, "a TextInput"),
         Arguments.of("Rectangle1",
            (BiFunction<Viewsheet, String, VSAssembly>) RectangleVSAssembly::new,
            "a Rectangle"));
   }

   /** A call into one table/* endpoint of the service. */
   @FunctionalInterface
   interface Endpoint {
      Object call(TableBindingService service, String assembly) throws Exception;
   }

   /** Every public TableBindingService endpoint that goes through requireTableBinding. */
   static Map<String, Endpoint> endpoints() {
      Map<String, Endpoint> endpoints = new LinkedHashMap<>();
      endpoints.put("read", (s, a) -> s.read(TOKEN, USER, a));
      endpoints.put("setShelf", (s, a) -> s.setShelf(TOKEN, USER, a, "rows", List.of(), null));
      endpoints.put("addField", (s, a) -> s.addField(
         TOKEN, USER, a, "rows", new FieldRef("Region", "dimension", null, null, null), null,
         null));
      endpoints.put("removeField", (s, a) -> s.removeField(TOKEN, USER, a, "rows", "Region"));
      endpoints.put("moveField",
                    (s, a) -> s.moveField(TOKEN, USER, a, "rows", "cols", "Region", null));
      endpoints.put("setSort", (s, a) -> s.setSort(
         TOKEN, USER, a, "rows", "Region", null,
         new DimensionSortRanking.Sort("asc", null, null)));
      endpoints.put("setRanking", (s, a) -> s.setRanking(
         TOKEN, USER, a, "rows", "Region", null,
         new DimensionSortRanking.Ranking("top", 3, "Sales", null)));
      endpoints.put("setOptions", (s, a) -> s.setOptions(TOKEN, USER, a, Map.of("rowTotals", true)));
      endpoints.put("setSource", (s, a) -> s.setSource(TOKEN, USER, a, "Query1", false));
      endpoints.put("setColumnLabels",
                    (s, a) -> s.setColumnLabels(TOKEN, USER, a, Map.of("Region", "R"), null));
      endpoints.put("setColumnWidths",
                    (s, a) -> s.setColumnWidths(TOKEN, USER, a, Map.of("Region", 100.0)));
      endpoints.put("setRowHeights", (s, a) -> s.setRowHeights(
         TOKEN, USER, a, Map.of("0", 40.0), null, false, false));
      endpoints.put("setCrosstabColumnVisibility", (s, a) -> s.setCrosstabColumnVisibility(
         TOKEN, USER, a, List.of("Region"), null, false));
      endpoints.put("requireTableAssembly", (s, a) -> {
         s.requireTableAssembly(TOKEN, USER, a);
         return null;
      });
      return endpoints;
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("nonTableAssemblies")
   void everyTableEndpointRefusesANonTableByItsType(
      String name, BiFunction<Viewsheet, String, VSAssembly> create, String typeText)
      throws Exception
   {
      Fixture fixture = new Fixture();
      VSAssembly assembly = fixture.add(create.apply(fixture.vs, name));

      // The defect's precondition: there is no factory for this type, so createModel would leak.
      IllegalArgumentException raw = assertThrows(IllegalArgumentException.class,
         () -> fixture.binding.createModel(assembly));
      assertTrue(raw.getMessage().startsWith("No model factory registered"), raw.getMessage());
      clearInvocations(fixture.binding);

      for(Map.Entry<String, Endpoint> endpoint : endpoints().entrySet()) {
         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> endpoint.getValue().call(fixture.service, name), endpoint.getKey());
         String message = thrown.getMessage();

         assertEquals("'" + name + "' is " + typeText + ", not a table or crosstab. " +
                      "The table tools only work on Table and Crosstab assemblies.",
                      message, endpoint.getKey());
         assertFalse(message.contains("inetsoft."), endpoint.getKey() + ": " + message);
      }

      verify(fixture.binding, never()).createModel(any());
      verify(fixture.bindings, never()).setBinding(anyString(), any(), any(), any());
   }

   @Test
   void aChartKeepsItsChartRefusal() throws Exception {
      Fixture fixture = new Fixture();
      fixture.add(new ChartVSAssembly(fixture.vs, "Chart1"));

      for(Map.Entry<String, Endpoint> endpoint : endpoints().entrySet()) {
         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> endpoint.getValue().call(fixture.service, "Chart1"), endpoint.getKey());

         assertEquals("'Chart1' is a chart, not a table or crosstab. " +
                      "Use the chart binding tools for charts.",
                      thrown.getMessage(), endpoint.getKey());
      }

      verify(fixture.binding, never()).createModel(any());
   }

   @Test
   void aCalcTableKeepsItsCalcTableRefusalOnShelfEndpoints() throws Exception {
      Fixture fixture = new Fixture();
      fixture.add(new CalcTableVSAssembly(fixture.vs, "Calc1"));

      for(Map.Entry<String, Endpoint> endpoint : endpoints().entrySet()) {
         if("setSource".equals(endpoint.getKey()) ||
            "setColumnWidths".equals(endpoint.getKey()) ||
            "setRowHeights".equals(endpoint.getKey())) {
            continue;
         }

         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> endpoint.getValue().call(fixture.service, "Calc1"), endpoint.getKey());

         assertTrue(thrown.getMessage().contains("is a calc table"),
                    endpoint.getKey() + ": " + thrown.getMessage());
      }

      verify(fixture.binding, never()).createModel(any());
   }

   /** set_table_source is allowed on a calc table; its branch still runs before the type gate. */
   @Test
   void aCalcTableStillReachesItsModelForSetSource() throws Exception {
      Fixture fixture = new Fixture();
      VSAssembly calc = fixture.add(new CalcTableVSAssembly(fixture.vs, "Calc1"));

      Exception thrown = assertThrows(Exception.class,
         () -> fixture.service.setSource(TOKEN, USER, "Calc1", "Query1", false));

      // No base worksheet, so the table cannot be matched -- but only after the model was built.
      assertFalse(String.valueOf(thrown.getMessage()).contains("not a table or crosstab"),
                  thrown.getMessage());
      verify(fixture.binding).createModel(calc);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("tableAssemblies")
   void tablesAndCrosstabsStillReachTheirModel(
      String name, BiFunction<Viewsheet, String, VSAssembly> create, String shelf)
      throws Exception
   {
      Fixture fixture = new Fixture();
      VSAssembly assembly = fixture.add(create.apply(fixture.vs, name));

      fixture.service.requireTableAssembly(TOKEN, USER, name);
      Map<String, Object> read = fixture.service.read(TOKEN, USER, name);

      assertNotNull(read);
      assertTrue(String.valueOf(read).contains(shelf), String.valueOf(read));
      verify(fixture.binding).createModel(assembly);
   }

   static Stream<Arguments> tableAssemblies() {
      return Stream.of(
         Arguments.of("Table1",
            (BiFunction<Viewsheet, String, VSAssembly>) TableVSAssembly::new, "details"),
         Arguments.of("EmbeddedTable1",
            (BiFunction<Viewsheet, String, VSAssembly>) EmbeddedTableVSAssembly::new, "details"),
         Arguments.of("Crosstab1",
            (BiFunction<Viewsheet, String, VSAssembly>) CrosstabVSAssembly::new, "rows"));
   }

   @Test
   void anUnknownAssemblyIsStillNamed() throws Exception {
      Fixture fixture = new Fixture();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fixture.service.requireTableAssembly(TOKEN, USER, "Missing1"));

      assertEquals("Unknown assembly 'Missing1'.", thrown.getMessage());
   }

   private static final String TOKEN = "tok";
   private static final Principal USER = () -> "agent";

   /**
    * A real VSBindingService with the production model factories, over a real viewsheet. The
    * session service runs each mutation against that viewsheet.
    */
   private static final class Fixture {
      final Viewsheet vs = new Viewsheet();
      final VSBindingService binding;
      final VSBindingModelService bindings = mock(VSBindingModelService.class);
      final TableBindingService service;

      Fixture() throws Exception {
         DataRefModelFactoryService refService = refService();
         VSTableBindingFactory tableFactory = new VSTableBindingFactory(refService);
         List<VSBindingFactory<?, ?>> factories = List.of(
            tableFactory, embeddedTableFactory(tableFactory, refService),
            new VSCrosstabBindingFactory(refService), new VSCalcTableBindingFactory(refService),
            new VSChartBindingFactory(null, null, refService, null));
         binding = spy(new VSBindingService(null, null, null, null, factories, refService, null,
                                            null, null, null, null, null, null, null));

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.runtimeId(anyString(), any(Principal.class))).thenReturn("rt1");
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return List.of();
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         service = new TableBindingService(sessions, binding, bindings, refService,
                                           mock(HideColumnsDialogService.class),
                                           mock(VSAssemblyInfoHandler.class));
      }

      VSAssembly add(VSAssembly assembly) {
         vs.addAssembly(assembly);
         return assembly;
      }

      /** The embedded-table factory is a package-private inner class of VSTableBindingFactory. */
      private static VSBindingFactory<?, ?> embeddedTableFactory(
         VSTableBindingFactory outer, DataRefModelFactoryService refService) throws Exception
      {
         Class<?> cls = Class.forName(
            VSTableBindingFactory.class.getName() + "$VSEmbeddedTableBindingFactory");
         Constructor<?> constructor =
            cls.getDeclaredConstructor(VSTableBindingFactory.class, DataRefModelFactoryService.class);
         constructor.setAccessible(true);
         return (VSBindingFactory<?, ?>) constructor.newInstance(outer, refService);
      }

      private static DataRefModelFactoryService refService() {
         BDimensionRefModel.VSDimensionRefModelFactory dimensionFactory =
            new BDimensionRefModel.VSDimensionRefModelFactory();
         DataRefModelFactoryService service = new DataRefModelFactoryService(
            List.of(dimensionFactory, new AttributeRefModel.AttributeRefModelFactory()));
         dimensionFactory.setDataRefModelFactoryService(service);
         return service;
      }
   }
}
