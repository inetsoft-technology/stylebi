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

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.wiz.binding.ChartGeoShelf.Detected;
import inetsoft.web.wiz.binding.ChartGeoShelf.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79625: {@code set_chart_geo_shelf} wrote the geo shelf fields only, so a second field had
 * no geo column entry and the map failed to render with "Multiple map types are not supported."
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ChartGeoShelfTest {
   private static final Object[][] DATA = {
      { "State", "City", "Total" },
      { "California", "Los Angeles", 10 },
      { "New York", "New York City", 20 },
      { "Texas", "Houston", 30 },
      { "Florida", "Miami", 40 },
   };

   private Viewsheet vs;
   private ChartVSAssembly chart;
   private VSMapInfo info;
   private RuntimeViewsheet rvs;
   private VSChartHandler handler;
   private final inetsoft.web.wiz.viewsheet.ViewsheetSessionService sessions =
      mock(inetsoft.web.wiz.viewsheet.ViewsheetSessionService.class);

   @BeforeEach
   void setUp() throws Exception {
      vs = new Viewsheet();
      chart = new ChartVSAssembly();
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setName("Chart1");
      info = new VSMapInfo();
      ainfo.setVSChartInfo(info);
      vs.addAssembly(chart);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      // Only the two data-dependent steps are stubbed; geo column registration, detection and
      // the geo column copy are the real VSChartHandler / MapHelper code.
      handler = mock(VSChartHandler.class, CALLS_REAL_METHODS);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new BaseField(null, "State")));
      columns.addAttribute(new ColumnRef(new BaseField(null, "City")));
      doAnswer(inv -> {
         info.updateGeoColumns(vs, columns);
         return null;
      }).when(handler).updateAllGeoColumns(any(), any(), any());
      doReturn(new DefaultDataSet(DATA)).when(handler).getChartData(any(), any());
   }

   @Test
   void stateThenCityGetEqualNonEmptyMappingTypes() throws Exception {
      List<Detected> detected = ChartGeoShelf.apply(
         rvs, chart, handler, List.of(new Field("State", "State"), new Field("City", "City")));

      assertEquals("U.S.", mappingType("State"));
      assertEquals(mappingType("State"), mappingType("City"));
      assertEquals(2, detected.size());
      assertEquals("U.S.", detected.get(1).mapType());
      assertEquals(2, info.getGeoFields().length);
   }

   @Test
   void cityThenStateGetEqualNonEmptyMappingTypes() throws Exception {
      ChartGeoShelf.apply(
         rvs, chart, handler, List.of(new Field("City", "City"), new Field("State", "State")));

      assertFalse(mappingType("City").isEmpty());
      assertEquals(mappingType("City"), mappingType("State"));
   }

   @Test
   void singleFieldIsRegisteredToo() throws Exception {
      ChartGeoShelf.apply(rvs, chart, handler, List.of(new Field("State", "State")));

      assertEquals("U.S.", mappingType("State"));
      assertEquals(1, info.getGeoFields().length);
   }

   @Test
   void layerChangeOnAChartWithEntriesTakesEffect() throws Exception {
      ChartGeoShelf.apply(rvs, chart, handler, List.of(new Field("State", "State")));
      assertEquals(2, info.getRTGeoColumns().getAttribute("State") instanceof VSChartGeoRef g
         ? g.getGeographicOption().getLayer() : -1);

      ChartGeoShelf.apply(rvs, chart, handler, List.of(new Field("State", "City")));

      VSChartGeoRef design = (VSChartGeoRef) info.getGeoColumns().getAttribute("State");
      VSChartGeoRef runtime = (VSChartGeoRef) info.getRTGeoColumns().getAttribute("State");
      assertEquals(1001, design.getGeographicOption().getLayer());
      assertEquals(1001, design.getGeographicOption().getMapping().getLayer());
      assertEquals(1001, runtime.getGeographicOption().getLayer());
      assertEquals(1001, ((VSChartGeoRef) info.getGeoFields()[0]).getGeographicOption().getLayer());
   }

   @Test
   void twoPolygonLayersAreRefusedBeforeAnythingIsWritten() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.apply(rvs, chart, handler,
                             List.of(new Field("State", "State"), new Field("City", "Country"))));

      assertTrue(e.getMessage().contains("Multiple state or country layers"), e.getMessage());
      assertEquals(0, info.getGeoFields().length);
      assertEquals(0, info.getGeoColumns().getAttributeCount());
   }

   @Test
   void unknownLayerAndRepeatedColumnAreRefusedByName() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.validate(List.of(new Field("State", "Planet"))));
      assertTrue(e.getMessage().contains("Planet"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.validate(List.of(new Field("City", "City"), new Field("City", "City"))));
      assertTrue(e.getMessage().contains("more than once"), e.getMessage());
   }

   @Test
   void mixedMapTypesAreRefusedWithStyleBisOwnMessage() {
      VSMapInfo mixed = new VSMapInfo();
      VSChartGeoRef state = new VSChartGeoRef();
      state.setGroupColumnValue("State");
      state.getGeographicOption().setMapping(new FeatureMapping("id", "alg", "U.S.", 2));
      state.getGeographicOption().setLayerValue("2");
      VSChartGeoRef city = new VSChartGeoRef();
      city.setGroupColumnValue("City");
      city.getGeographicOption().setMapping(new FeatureMapping("id", "alg", "World", 1001));
      city.getGeographicOption().setLayerValue("1001");
      mixed.getGeoColumns().addAttribute(state);
      mixed.getGeoColumns().addAttribute(city);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.check(mixed, List.of(new Field("State", "State"), new Field("City", "City"))));

      assertTrue(e.getMessage().contains("Multiple map types are not supported."), e.getMessage());
   }

   @Test
   void detectionMovingAFieldOntoASecondPolygonLayerIsRefused() {
      VSMapInfo moved = new VSMapInfo();

      for(String name : List.of("State", "City")) {
         VSChartGeoRef col = new VSChartGeoRef();
         col.setGroupColumnValue(name);
         col.getGeographicOption().setMapping(new FeatureMapping("id", "alg", "U.S.", 2));
         col.getGeographicOption().setLayerValue("2");
         moved.getGeoColumns().addAttribute(col);
      }

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.check(moved, List.of(new Field("State", "State"), new Field("City", "City"))));

      assertTrue(e.getMessage().contains("Multiple state or country layers"), e.getMessage());
   }

   @Test
   void aCheckedExceptionAfterThePartialWriteRestoresTheCheckpoint() throws Exception {
      doThrow(new java.io.IOException("data failed")).when(handler).getChartData(any(), any());
      doAnswer(inv -> {
         ((inetsoft.web.wiz.viewsheet.ViewsheetSessionService.Mutation) inv.getArgument(2))
            .run(rvs, "rt", null);
         return List.of();
      }).when(sessions).mutate(any(), any(), any());
      when(rvs.restoreCurrentCheckpoint()).thenReturn(true);

      assertThrows(java.io.IOException.class, () -> ChartGeoShelf.write(
         sessions, "tok", null, "Chart1", handler, List.of(new Field("State", "State")), null));

      verify(rvs).restoreCurrentCheckpoint();
   }

   @Test
   void anExplicitMapTypeIsRespectedAndAStaleEntryDoesNotKeepItsOldType() throws Exception {
      ChartGeoShelf.apply(rvs, chart, handler, List.of(new Field("State", "State")), "U.S.");
      VSChartGeoRef stale = (VSChartGeoRef) info.getGeoColumns().getAttribute("State");
      stale.getGeographicOption().setMapping(new FeatureMapping("id", "alg", "Canada", 2));

      List<Detected> detected = ChartGeoShelf.apply(
         rvs, chart, handler, List.of(new Field("State", "State")), "U.S.");

      assertEquals("U.S.", detected.get(0).mapType());
      assertEquals("U.S.", mappingType("State"));
      assertEquals("U.S.", info.getMeasureMapType());
   }

   @Test
   void aLayerTheExplicitMapTypeDoesNotProvideIsRefusedBeforeAnythingIsWritten() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartGeoShelf.apply(rvs, chart, handler, List.of(new Field("State", "State")), "World"));

      assertTrue(e.getMessage().contains("not supported"), e.getMessage());
      assertEquals(0, info.getGeoFields().length);
      assertEquals(0, info.getGeoColumns().getAttributeCount());
   }

   private String mappingType(String column) {
      VSChartGeoRef col = (VSChartGeoRef) info.getGeoColumns().getAttribute(column);
      assertNotNull(col, "no geo column entry for " + column);
      String type = col.getGeographicOption().getMapping().getType();
      assertNotNull(type, "no mapping type for " + column);
      return type;
   }
}
