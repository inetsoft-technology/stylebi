/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.handler;

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.graph.MapData;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.web.binding.model.graph.FeatureMappingInfo;
import inetsoft.web.binding.model.graph.MapFeatureModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VSMapHandler {
   @Autowired
   public VSMapHandler(VSChartHandler chartHandler) {
      this.chartHandler = chartHandler;
   }

   public boolean getMappingStatus(RuntimeViewsheet rvs, ChartVSAssembly chart,
                                   String refName, FeatureMapping mapping) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      DataSet source = chartHandler.getChartData(box, chart);
      int index = GraphUtil.indexOfHeader(source, refName);
      boolean allMapped = MapHelper.isAllMapped(source, index, mapping, cinfo);

      return allMapped;
   }

   /**
    * GetGeoDataEvent
    **/
   public Map<String, Object> getGeoData(RuntimeViewsheet rvs, ChartVSAssembly chart,
      String refName) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      chartHandler.updateAllGeoColumns(box, vs, chart);
      String chartName = info.getAbsoluteName();
      VSChartGeoRef ref = chartHandler.getGeoRef(cinfo, refName);
      String type = cinfo.getMeasureMapType();

      if(ref != null && ref.getGeographicOption() != null &&
         ref.getGeographicOption().getMapping() != null)
      {
         type = ref.getGeographicOption().getMapping().getType();
      }

      Map<String,String> types = MapData.getMapTypesLabel();
      Map<String, Object> result = new HashMap<>();

      if(ref == null) {
         result.put("type", type);
         result.put("types", types);
      }
      else {
         Map<String,Integer> layers = MapData.getLayers(type);
         GeographicOption option = ref.getGeographicOption();
         int layer = option.getLayer();
         FeatureMapping mapping = option.getMapping();
         DataSet source = chartHandler.getChartData(box, chart);
         int index = GraphUtil.indexOfHeader(source, refName);
         SourceInfo sourceInfo = info.getSourceInfo();
         FeatureMapping amapping = MapHelper.getAutoMapping(vs, sourceInfo,
            ref.getName(), source, index, type, layer, chartName);
         TreeMap<String, FeatureMapping> mappings =
            MapHelper.getMappings(source, index, ref, type, layer, amapping, mapping);
         Map<String, FeatureMappingInfo> nmappings = new LinkedHashMap<>();

         for(String key : mappings.keySet()) {
            FeatureMapping fMapping = mappings.get(key);
            nmappings.put(Catalog.getCatalog().getString(key), new FeatureMappingInfo(fMapping));
         }

         boolean allMapped = MapHelper.isAllMapped(source, index, mapping, cinfo);

         result.put("type", type);
         result.put("layer", layer);
         result.put("types", types);
         result.put("layers", layers);
         result.put("mappings", nmappings);
         result.put("mappingStatus", allMapped);
      }

      return result;
   }

   public Map<String, Object> changeMapType(RuntimeViewsheet rvs, ChartVSAssembly chart,
      String refName, String type, String layerstr) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      chartHandler.updateAllGeoColumns(box, vs, chart);
      VSChartGeoRef ref = chartHandler.getGeoRef(cinfo, refName);

      if(ref == null) {
         // only set the map type at design time if it is a measure
         cinfo.setMeasureMapType(type);
      }

      String chartName = info.getAbsoluteName();
      GeographicOption option = ref == null ? null : ref.getGeographicOption();
      SourceInfo sourceInfo = info.getSourceInfo();
      DataSet source = chartHandler.getChartData(box, chart);
      int index = GraphUtil.indexOfHeader(source, refName);
      int layer = option != null && VSUtil.isDynamicValue(option.getLayerValue()) ?
         -1 : MapHelper.getAutoLayer(source, index, type);
      layer = layerstr == null ? layer : Integer.parseInt(layerstr);
      Map<String,String> types = MapData.getMapTypesLabel();
      FeatureMapping mapping = MapHelper.getAutoMapping(vs, sourceInfo,
         ref == null ? null : ref.getName(), source, index, type, layer, chartName);
      Map<String,Integer> layers = MapData.getLayers(type);
      TreeMap<String, FeatureMapping> mappings =
         MapHelper.getMappings(source, index, ref, type, layer, mapping, mapping);
      Map<String, FeatureMappingInfo> nmappings = new LinkedHashMap<>();

      for(String key : mappings.keySet()) {
         FeatureMapping fMapping = mappings.get(key);
         nmappings.put(Catalog.getCatalog().getString(key), new FeatureMappingInfo(fMapping));
      }

      Map<String, Object> result = new HashMap<>();
      result.put("type", type);
      result.put("layer", layer);
      result.put("types", types);
      result.put("layers", layers);
      result.put("mappings", nmappings);

      return result;
   }

   public Map<String, Object> getMappingData(RuntimeViewsheet rvs, ChartVSAssembly chart,
      VSChartGeoRef ref) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      String refName = ref.getName();
      FeatureMapping mapping = ref.getGeographicOption().getMapping();
      DataSet source = chartHandler.getChartData(box, chart);
      int index = GraphUtil.indexOfHeader(source, refName);
      MapHelper.updateFeatureMapping(source, index, mapping);
      String type = mapping.getType();
      int layer = mapping.getLayer();
      Map<String, MapFeature> features = MapHelper.getAutoMatchedFeatures(
         source, index, layer, cinfo, type);
      Map<String, MapFeatureModel> nfeatures = new HashMap<>();
      Iterator<String> it = features.keySet().iterator();

      while (it.hasNext()) {
         String key = (String) it.next();
         MapFeature feature = features.get(key);
         nfeatures.put(key, new MapFeatureModel(feature));
      }

      Map<String, Integer> unmatchedValue =
         MapHelper.getUnMatchedValues(source, index, features, mapping);
      String[] algorithms = MapHelper.getAlgorithms();
      String[] viewAlgorithms = MapHelper.getViewAlgorithms();
      FeatureMapping nmapping = (FeatureMapping) mapping.clone();
      nmapping.setMappings(new LinkedHashMap<>());
      Map<String, Integer> ounmatchedValue =
         MapHelper.getUnMatchedValues(source, index, new TreeMap(), nmapping);
      Map<String, String> manualMappings = MapHelper.getManualMappings(mapping);
      FeatureMapping smapping = MapHelper.getSavedMapping(mapping.getID());
      MapHelper.trimFeatureMapping(source, index, smapping);

      Map<String, Object> result = new HashMap<>();
      result.put("features", nfeatures);
      result.put("algorithms", algorithms);
      result.put("viewAlgorithms", viewAlgorithms);
      result.put("unmatchedValue", unmatchedValue);
      result.put("ounmatchedValue", ounmatchedValue);
      result.put("manualMappings", manualMappings);

      if(smapping != null) {
         MapHelper.trimFeatureMapping(source, index, smapping);
         result.put("smapping", smapping);
      }

      return result;
   }

   public List<MapFeatureModel> getLikelyFeatures(RuntimeViewsheet rvs,
      ChartVSAssembly chart, VSChartGeoRef ref, int row, String algorithm)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      String refName = ref.getName();
      FeatureMapping mapping = ref.getGeographicOption().getMapping();
      int layer = mapping.getLayer();
      String type = mapping.getType();
      Map<String, String> mappings = mapping.getMappings();
      chartHandler.updateGeoColumns(box, vs, chart, cinfo);
      DataSet source = chartHandler.getChartData(box, chart);
      int index = GraphUtil.indexOfHeader(source, refName);
      MapFeature[] likelyFeatures = MapHelper.getLikelyFeatures(source, index,
         row, type, layer, algorithm, mappings, cinfo);

      return Arrays.stream(likelyFeatures)
         .map(f -> new MapFeatureModel(f))
         .collect(Collectors.toList());
   }

   private final VSChartHandler chartHandler;
}
