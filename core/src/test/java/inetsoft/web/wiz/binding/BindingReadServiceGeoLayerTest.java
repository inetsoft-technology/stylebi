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
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.binding.model.graph.FeatureMappingInfo;
import inetsoft.web.binding.model.graph.GeographicOptionInfo;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import inetsoft.web.wiz.binding.model.GeoFieldLayer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81664: {@code get_binding} reported a geo field with no layer and no map type, so a caller
 * could not tell what a field was set to and had to guess when adding another.
 */
class BindingReadServiceGeoLayerTest {
   private static GeographicOptionInfo option(String layer, String mapType) {
      GeographicOptionInfo option = new GeographicOptionInfo();
      option.setLayerValue(layer);
      FeatureMappingInfo mapping = new FeatureMappingInfo();
      mapping.setType(mapType);
      option.setMapping(mapping);
      return option;
   }

   private static ChartGeoRefModel geo(String column, GeographicOptionInfo option) {
      ChartGeoRefModel ref = new ChartGeoRefModel();
      ref.setColumnValue(column);
      ref.setOption(option);
      return ref;
   }

   @Test
   void theLayerAndMapTypeComeFromTheGeoColumnNotTheShelfFieldsOwnOption() {
      ChartBindingModel model = new ChartBindingModel();
      // The shelf field carries a stale layer and no map type, as ChartGeoShelf leaves it.
      model.addGeoField(geo("Customer:State", option("1001", null)));
      model.addGeoCol(geo("Customer:State", option("2", "U.S.")));

      GeoFieldLayer layer = BindingReadService.geoLayers(model).get("Customer:State");

      assertEquals("State", layer.layer());
      assertEquals("U.S.", layer.mapType());
      assertEquals("2", layer.layerValue());
   }

   @Test
   void layerId2IsNamedProvinceOnCanadaAndMexicoAndStateElsewhere() {
      assertEquals("Province", BindingReadService.geoLayer(option("2", "Canada"), null).layer());
      assertEquals("Province", BindingReadService.geoLayer(option("2", "Mexico"), null).layer());
      assertEquals("State", BindingReadService.geoLayer(option("2", "U.S."), null).layer());
      assertEquals("City", BindingReadService.geoLayer(option("1001", "U.S."), null).layer());
      assertEquals("Zip", BindingReadService.geoLayer(option("1002", "U.S."), null).layer());
      assertEquals("Country", BindingReadService.geoLayer(option("1", "World"), null).layer());
   }

   @Test
   void aFieldWithNoMatchingColumnFallsBackToItsOwnOptionAndTheChartsMapType() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMapType("Canada");
      model.addGeoField(geo("Province", option("2", null)));

      GeoFieldLayer layer = BindingReadService.geoLayers(model).get("Province");

      assertEquals("Province", layer.layer());
      assertEquals("Canada", layer.mapType());
   }

   @Test
   void aDynamicLayerHasNoNameAndIsReportedRawAndNoLayerIsNull() {
      GeoFieldLayer dynamic = BindingReadService.geoLayer(option("$(layer)", "U.S."), null);
      assertNull(dynamic.layer());
      assertEquals("$(layer)", dynamic.layerValue());
      assertEquals("U.S.", dynamic.mapType());

      GeoFieldLayer none = BindingReadService.geoLayer(option(null, null), null);
      assertNull(none.layer());
      assertNull(none.layerValue());
      assertNull(none.mapType());
   }

   @Test
   void aChartWithoutGeoFieldsReportsNoLayers() {
      assertNull(BindingReadService.geoLayers(new ChartBindingModel()));
   }

   @Test
   void readPutsTheLayersOnTheBindingWithoutTouchingTheShelfEntries() {
      ChartBindingModel model = new ChartBindingModel();
      model.addGeoField(geo("Customer:State", option("2", null)));
      model.addGeoCol(geo("Customer:State", option("2", "U.S.")));
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Map1")).thenReturn(mock(ChartVSAssembly.class));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      AssemblyBinding result = new BindingReadService(
         binding, mock(DataRefModelFactoryService.class)).read(rvs, "Map1");

      assertEquals(1, result.shelves().get("geo").size());
      Map<String, GeoFieldLayer> layers = result.geoLayers();
      assertEquals("State", layers.get("Customer:State").layer());
      assertEquals("U.S.", layers.get("Customer:State").mapType());
   }
}
