/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal.graph;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geo.*;
import inetsoft.graph.geo.solver.*;
import inetsoft.graph.internal.GTool;
import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.ChartElementDef;
import inetsoft.report.internal.binding.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class that handles map type, layer and mapping.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class MapHelper {
   /**
    * Distance algorithm.
    */
   public static final String ALGORITHM_NONE = "None";
   /**
    * Distance algorithm.
    */
   public static final String ALGORITHM_DISTANCE = "Distance";
   /**
    * Double Metaphone algorithm.
    */
   public static final String ALGORITHM_DOUBLE_METAPHONE = "Double Metaphone";
   /**
    * Metaphone algorithm.
    */
   public static final String ALGORITHM_METAPHONE = "Metaphone";
   /**
    * Soundex algorithm.
    */
   public static final String ALGORITHM_SOUNDEX = "Soundex";
   /**
    * Built-in id.
    */
   public static final String BUILD_IN_MAPPING_ID = "Built-in";

   /**
    * Get polygon layer supported by the specified map type.
    */
   public static int getPolygonLayer(String type) {
      Map<String,Integer> map = MapData.getLayers(type);

      for(Map.Entry<String, Integer> entry : map.entrySet()) {
         int layer = entry.getValue();

         if(!MapData.isPointLayer(layer)) {
            return layer;
         }
      }

      return -1;
   }

   /**
    * Get layer name.
    * @param type map type.
    * @param layer map layer.
    */
   public static String getLayerName(String type, int layer) {
      Map<String,Integer> map = MapData.getLayers(type);

      for(Map.Entry<String,Integer> entry : map.entrySet()) {
         if(entry.getValue() == layer) {
            return entry.getKey();
         }
      }

      return null;
   }

   /**
    * Check if the specified layer is supported the specified type.
    * @param type map type.
    * @param layer map layer.
    */
   public static boolean isLayerSupported(String type, int layer) {
      return MapData.getLayers(type).values().contains(layer);
   }

   /**
    * Get automatic detect map type.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param layer the layer of geographic ref.
    */
   public static String getAutoType(DataSet source, int sourceColumn, int layer) {
      Map<Integer,Map<String,Collection>> typeMap = getTypeMap();
      Map<String,Collection> map = typeMap.get(layer);
      String type = "World";

      if(map == null) {
         LOG.error("Layer types not defined: " + layer);
         return type;
      }

      List<String> types = new ArrayList<>(map.keySet());
      int cnt = 0;

      for(int i = 0; i < types.size(); i++) {
         String type0 = types.get(i);
         String[] autoCodes = getAutoMatchedGeoCodes(source, sourceColumn, type0, layer);
         Collection codes = new HashSet(map.get(type0)); // optimization
         int cnt0 = 0;

         for(int j = 0; j < autoCodes.length; j++) {
            if(codes.contains(autoCodes[j])) {
               cnt0++;
            }
         }

         if(cnt0 > cnt) {
            type = type0;
            cnt = cnt0;
         }
      }

      return type;
   }

   /**
    * Init type map.
    */
   private static synchronized Map<Integer,Map<String,Collection>> getTypeMap() {
      if(typeMap == null) {
         try {
            initTypeMap();
         }
         catch(Exception ex) {
            LOG.error("Failed to initialize type map", ex);
         }
      }

      return typeMap;
   }

   /**
    * Init type map.
    */
   private static void initTypeMap() throws Exception {
      typeMap = new HashMap<>();

      for(int layer : MapData.getLayerIds()) {
         Map<String,Collection> map = new TreeMap<>();
         boolean point = MapData.isPointLayer(layer);
         List allnames = new ArrayList(GeoPoints.WORLD.getNames());

         for(String type : MapData.getMapTypes()) {
            if(!isLayerSupported(type, layer)) {
               continue;
            }

            if(point) {
               map.put(type, MapData.getGeoPoints(type, layer).getNames());
               allnames.addAll(MapData.getGeoPoints(type, layer).getNames());
            }
            else {
               map.put(type, MapData.getGeoMap(type, layer, false).getNames());
            }
         }

         if(point) {
            map.put("World", allnames);
         }

         typeMap.put(layer, map);
      }
   }

   /**
    * Get automatic detect map layer.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param type map type.
    */
   public static int getAutoLayer(DataSet source, int sourceColumn, String type) {
      if(layerMap == null) {
         initLayerMap();
      }

      int[] layers = null;
      String[] alltypes; // all map types to try

      if(type == null || type.equals("")) {
         layers = MapData.getLayerIds();
         alltypes = MapData.getMapTypes();
      }
      else {
         List list = new ArrayList(MapData.getLayers(type).values());
         layers = new int[list.size()];
         alltypes = new String[] { type };

         for(int i = 0; i < list.size(); i++) {
            layers[i] = ((Integer) list.get(i)).intValue();
         }
      }

      int layer = (layers.length == 0) ? -1 : MapData.LAYER_CITY;
      int cnt = 0;

      for(int i = 0; i < layers.length; i++) {
         int layer0 = layers[i];
         String[] autoCodes = getAutoMatchedGeoCodes(source, sourceColumn,
                                                     type, layer0);
         Collection layerCodes = (Collection) layerMap.get(layer0);
         int cnt0 = 0;

         if(layerCodes != null) {
            cnt0 = countContained(autoCodes, layerCodes);
         }
         // no name table, raw value (e.g. zip)
         else {
            try {
               for(String maptype : alltypes) {
                  if(MapData.isPointLayer(layer0)) {
                     GeoPoints gpts = MapData.getGeoPoints(maptype, layer0);

                     if(gpts != null) {
                        cnt0 = countContained(autoCodes, gpts.getNames());
                     }
                  }
                  else {
                     GeoMap gmap = MapData.getGeoMap(maptype, layer0, false);

                     if(gmap != null) {
                        cnt0 = countContained(autoCodes, gmap.getNames());
                     }
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to load map data", ex);
            }
         }

         if(cnt0 > cnt) {
            layer = layer0;
            cnt = cnt0;
         }
      }

      // no match, use the first layer as default
      if(cnt == 0) {
         Map<String,Integer> layermap = MapData.getLayers(type);
         List<Integer> layerList = new ArrayList<>(layermap.values());

         if(layerList.size() > 0) {
            Collections.sort(layerList);
            layer = layerList.get(0);
         }
      }

      return layer;
   }

   /**
    * Count how many items in autoCodes are in codes.
    */
   private static int countContained(String[] autoCodes, Collection codes) {
      int cnt0 = 0;

      for(int j = 0; j < autoCodes.length; j++) {
         if(codes.contains(autoCodes[j])) {
            cnt0++;
         }
      }

      return cnt0;
   }

   /**
    * Init layer map.
    */
   private static void initLayerMap() {
      layerMap = new HashMap<>();
      int[] layers = MapData.getLayerIds();

      for(int i = 0; i < layers.length; i++) {
         int layer = layers[i];
         NameTable nameTable = MapData.getNameTable(layer);

         if(nameTable != null) {
            Collection names = nameTable.getNames();
            layerMap.put(layer, names);
         }
      }
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param sourceColumn source geographic column index.
    * @param type map type.
    * @param layer the layer of geographic ref.
    * @return a map key is feature map, value is cell value in data set.
    */
   public static Map<String, MapFeature> getAutoMatchedFeatures(DataSet source,
      int sourceColumn, String type, int layer)
   {
      int[] nameColumns = MapData.getNameColumns(layer);
      Map features = new HashMap();

      // fix Bug #25802, should not force to set named table if using map itself
      // which has a Feature ID and polygons, default to mapping the first column
      // to fix this bug.
      if(nameColumns.length == 0) {
         nameColumns = new int[1];
         nameColumns[0] = 0;
      }

      for(int i = 0; i < nameColumns.length; i++) {
         Map features0 = getAutoMatchedFeatures(source, sourceColumn, layer,
                                                nameColumns[i], type);

         addAutoMatchedFeatures(features, features0, layer);
      }

      Map<String,Collection> map = getTypeMap().get(layer);

      // exclude unmatched type
      if(type != null && type.length() > 0 && map != null) {
         Collection typeCodes = map.get(type);
         ArrayList values = new ArrayList(features.keySet());

         if(typeCodes != null) {
            typeCodes = new HashSet(typeCodes); // optimization

            for(int i = values.size() - 1; i >= 0; i--) {
               String value = (String) values.get(i);
               MapFeature feature = (MapFeature) features.get(value);
               String code = feature.getGeoCode();

               if(!typeCodes.contains(code)) {
                  features.remove(value);
               }
            }
         }
      }

      return features;
   }

   /**
    * Add auto matched feature.
    */
   private static void addAutoMatchedFeature(Map<String, MapFeature>
      features, String value, MapFeature feature, int layer)
   {
      Set keys = features.keySet();
      String ovalue = value + "";

      // 1.avoid duplicated value
      // 2.top layer duplicated value is not allowed
      for(int i = 2; !isTopLayer(layer) && keys.contains(value) &&
         !feature.equals(features.get(value)); i++)
      {
         value = ovalue + "(" + i + ")";
      }

      if(!keys.contains(value)) {
         features.put(value, feature);
      }
   }

   /**
    * Check if the layer is top layer.
    */
   private static boolean isTopLayer(int layer) {
      int[] ids = MapData.getLayerIds();
      Arrays.sort(ids);

      return ids.length > 0 && layer == ids[0];
   }

   /**
    * Add auto matched feature.
    */
   private static void addAutoMatchedFeatures(Map<String, MapFeature>
      features1, Map<String, MapFeature> features2, int layer)
   {
      for(Map.Entry<String, MapFeature> entry : features2.entrySet()) {
         addAutoMatchedFeature(features1, entry.getKey(), entry.getValue(),
            layer);
      }
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param type map type.
    * @return a map key is feature map, value is cell value in data set.
    */
   public static Map<String, MapFeature> getAutoMatchedFeatures(DataSet source,
      int childCol, int childLayer, int parentCol, int parentLayer, String type,
      FeatureMapping mapping)
   {
      // if name table is not defined by a name column is specified in mapdata,
      // we assume it's a one-to-one mapping (e.g. zipcode)
      if(getNameTable(childLayer, type) == null) {
         return getAutoMatchedFeatures(source, childCol, type, childLayer);
      }

      int[] childCols = MapData.getNameColumns(childLayer);
      int[] parentCols = MapData.getNameColumns(childLayer, parentLayer);
      Map features = new HashMap();
      int[] sourceColumns = new int[] {childCol, parentCol};

      for(int i = 0; i < childCols.length; i++) {
         for(int j = 0; j < parentCols.length; j++) {
            int[] nameColumns = new int[] {childCols[i], parentCols[j]};
            int[] layers = new int[] {childLayer, parentLayer};
            FeatureMapping[] mappings = new FeatureMapping[] {mapping};
            Map features0 = getAutoMatchedFeatures(
               source, sourceColumns, layers, nameColumns, type, mappings);

            addAutoMatchedFeatures(features, features0, childLayer);
         }
      }

      return features;
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param type map type.
    * @param layer the layer of geographic ref.
    * @return a map key is feature map, value is cell value in data set.
    */
   public static Map<String, MapFeature> getAutoMatchedFeatures(DataSet source,
      int index, int layer, ChartInfo cinfo, String type)
   {
      GeoRef pref = getParentRef(cinfo, layer, source, index);

      return getAutoMatchedFeatures(source, index, layer, pref, type);
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param type map type.
    * @param layer the layer of geographic ref.
    * @return a map key is feature map, value is cell value in data set.
    */
   public static Map<String, MapFeature> getAutoMatchedFeatures(DataSet source,
      int index, int layer, GeoRef[] grefs, String type)
   {
      GeoRef pref = null;

      for(GeoRef gref:grefs) {
         if(isParentLayer(gref, layer)) {
            pref = gref;
            break;
         }
      }

      return getAutoMatchedFeatures(source, index, layer, pref, type);
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param type map type.
    * @param layer the layer of geographic ref.
    * @return a map key is feature map, value is cell value in data set.
    */
   private static Map<String, MapFeature> getAutoMatchedFeatures(DataSet source,
      int index, int layer, GeoRef pref, String type)
   {
      if(pref == null) {
         return MapHelper.getAutoMatchedFeatures(source, index, type, layer);
      }
      else {
         int pindex = GraphUtil.indexOfHeader(source, pref.getName());
         int player = pref.getGeographicOption().getLayer();
         FeatureMapping mapping = pref.getGeographicOption().getMapping();
         return MapHelper.getAutoMatchedFeatures(source, index, layer, pindex,
            player, type, mapping);
      }
   }

   /**
    * Get parent layer geo ref.
    */
   private static GeoRef getParentRef(ChartInfo info, int layer, DataSet source,
      int index)
   {
      if(info instanceof VSChartInfo) {
         ColumnSelection rcols = ((VSChartInfo) info).getRTGeoColumns();

         for(int i = 0; i < rcols.getAttributeCount(); i++) {
            DataRef ref = rcols.getAttribute(i);

            if(GraphUtil.indexOfHeader(source, ref.getName()) == index) {
               continue;
            }

            if(isParentLayer(ref, layer)) {
               return (GeoRef) ref;
            }
         }
      }
      else if(info instanceof MapInfo) {
         ChartRef[] refs = ((MapInfo) info).getRTGeoFields();

         for(int i = 0; i < refs.length; i++) {
            if(GraphUtil.indexOfHeader(source, refs[i].getName()) == index) {
               continue;
            }

            if(isParentLayer(refs[i], layer)) {
               return (GeoRef) refs[i];
            }
         }
      }

      return null;
   }

   /**
    * Check if it is parent layer.
    */
   private static boolean isParentLayer(DataRef ref, int layer) {
      if(ref instanceof GeoRef) {
         GeoRef gref = (GeoRef) ref;
         GeographicOption opt = gref.getGeographicOption();

         if(opt.getLayer() < layer) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get best name column for mapping.
    * @param source map data set.
    * @param sourceColumn source geographic column index.
    * @param type map type.
    */
   public static int getBestNameColumn(DataSet source, int sourceColumn,
      String type, int childLayer, int parentLayer)
   {
      int[] nameColumns = MapData.getNameColumns(childLayer, parentLayer);
      int cnt = 0;
      int column = 0;

      for(int i = 0; i < nameColumns.length; i++) {
         Map features = getAutoMatchedFeatures(source, sourceColumn, childLayer,
                                               nameColumns[i], type);

         if(features.size() > cnt) {
            cnt = features.size();
            column = nameColumns[i];
         }
      }

      return column;
   }

   /**
    * Get best name column for mapping.
    * @param source map data set.
    * @param sourceColumn source geographic column index.
    * @param type map type.
    * @param layer the layer of geographic ref.
    */
   public static int getBestNameColumn(DataSet source, int sourceColumn,
                                       String type, int layer)
   {
      int[] nameColumns = MapData.getNameColumns(layer);
      int cnt = 0;
      int column = 0;

      for(int i = 0; i < nameColumns.length; i++) {
         Map features = getAutoMatchedFeatures(source, sourceColumn, layer,
                                               nameColumns[i], type);

         if(features.size() > cnt) {
            cnt = features.size();
            column = nameColumns[i];
         }
      }

      return column;
   }

   /**
    * Get automatched geo codes.
    * @param source map data set.
    * @param sourceColumn source geographic column index.
    * @param type the map type.
    * @param layer the layer of geographic ref.
    */
   private static String[] getAutoMatchedGeoCodes(DataSet source,
      int sourceColumn, String type, int layer)
   {
      Map map = getAutoMatchedFeatures(source, sourceColumn, type, layer);
      List<MapFeature> features = new ArrayList(map.values());
      String[] geoCodes = new String[features.size()];

      for(int i = 0; i < geoCodes.length; i++) {
         geoCodes[i] = features.get(i).getGeoCode();
      }

      return geoCodes;
   }

   /**
    * Get automatched geo codes.
    * @param source map data set.
    * @param sourceColumn source geographic column index.
    * @param type the map type.
    * @param layer the layer of geographic ref.
    */
   private static String[] getAutoMatchedGeoCodes(DataSet source,
      int sourceColumn, String type, int layer, ChartInfo info)
   {
      Map map = getAutoMatchedFeatures(source, sourceColumn, layer, info, type);
      List<MapFeature> features = new ArrayList(map.values());
      String[] geoCodes = new String[features.size()];

      for(int i = 0; i < geoCodes.length; i++) {
         geoCodes[i] = features.get(i).getGeoCode();
      }

      return geoCodes;
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @param sourceColumn source column index.
    * @param layer map layer.
    * @param nameColumn name column index.
    * @return a map key is feature map, value is cell value in data set.
    */
   private static Map<String, MapFeature> getAutoMatchedFeatures(
      DataSet source, int sourceColumn, int layer, int nameColumn, String type)
   {
      int[] sourceColumns = new int[] {sourceColumn};
      int[] nameColumns = new int[] {nameColumn};
      int[] layers = new int[] {layer};

      return getAutoMatchedFeatures(source, sourceColumns, layers, nameColumns, type, null);
   }

   /**
    * Get automatic matched Features.
    * @param source map data set.
    * @return a map key is feature map, value is cell value in data set.
    */
   private static Map<String, MapFeature> getAutoMatchedFeatures(
      DataSet source, int[] sourceColumns, int[] layers, int[] nameColumns,
      String type, FeatureMapping[] mappings)
   {
      Map features = new LinkedHashMap();

      for(int i = 0; i < sourceColumns.length; i++) {
         if(sourceColumns[i] == -1) {
            return features;
         }
      }

      ExactAlgorithm algorithm = new ExactAlgorithm();
      Set geoCodes = new HashSet(); // matched geo codes
      NameTable[] names = new NameTable[layers.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = getNameTable(layers[i], type);
      }

      for(int i = 0; i < source.getRowCount(); i++) {
         NameMatcher[] matchers = null;
         int cnt = sourceColumns.length;

         if(mappings != null && cnt == mappings.length + 1) {
            matchers = new StaticNameMatcher[cnt];
            matchers[0] = null;

            for(int j = 1; j < cnt; j++) {
               int[] columns = new int[] {sourceColumns[j]};
               String[][] mapping = getMappings(mappings[j - 1]);
               matchers[j] = new StaticNameMatcher(columns, mapping);
            }
         }

         List<String> matchedGeoCodes = algorithm.findMatchIDs(source, sourceColumns,
            names, nameColumns, i, matchers);
         Map<Integer,Map<String,Collection>> typeMap = getTypeMap();
         Collection typeCodes = layers.length > 0 && typeMap.containsKey(layers[0]) && type != null
            ? typeMap.get(layers[0]).get(type) : null;
         String geoCode = null;

         for(String code: matchedGeoCodes) {
            if(typeCodes == null || typeCodes.contains(code)) {
               geoCode = code;
               break;
            }
         }

         if(geoCode != null) {
            MapFeature feature = createMapFeature(names[0], geoCode);

            if(feature == null) {
               continue;
            }

            String value = GTool.toString(source.getData(sourceColumns[0], i));

            if(value == null) {
               continue;
            }

            geoCodes.add(feature);
            addAutoMatchedFeature(features, value, feature, layers[0]);
         }
      }

      return features;
   }

   /**
    * Create MapFeature.
    * @param names name table.
    * @param geoCode specified geo code.
    */
   private static MapFeature createMapFeature(NameTable names, String geoCode) {
      String name = getFeatureName(names, geoCode);

      return name == null || name.length() == 0 ? null :
         new MapFeature(name, geoCode);
   }

   /**
    * Get feature name by geo code.
    */
   private static String getFeatureName(NameTable table, String geoCode) {
      if(table == null) {
         return geoCode;
      }

      String name = getFeatureName0(table, geoCode);

      if(isDuplicatedName(table, name)) {
         return Tool.replaceAll(table.getLabel(geoCode), ", ", "/");
      }
      else {
         return name;
      }
   }

   /**
    * Get feature name by geo code.
    */
   private static String getFeatureName0(NameTable table, String geoCode) {
      String label = table.getLabel(geoCode);
      String[] labels = label.split(", ");
      return labels[0];
   }

   /**
    * Check if this feature name is duplicated.
    */
   private static boolean isDuplicatedName(NameTable table, String name) {
      String tname = table.getName();
      ArrayList dnames = dupNames.get(tname);

      if(dnames == null) {
         dnames = new ArrayList();
         Set names = new HashSet();
         List<String> geoCodes = new ArrayList<>(table.getNames());

         for(String geoCode:geoCodes) {
            String fname = getFeatureName0(table, geoCode);

            if(names.contains(fname)) {
               dnames.add(fname);
            }

            names.add(fname);
         }

         dupNames.put(tname, dnames);
      }

      return dnames.contains(name);
   }

   /**
    * Get feature name by geo code.
    */
   public static String getFeatureName(int layer, String geoCode) {
      NameTable names = MapData.getNameTable(layer);
      return getFeatureName(names, geoCode);
   }

   /**
    * Get unmatched values.
    * @param source map data set.
    * @param mapping feature mapping.
    * @return a map, key is row index, value is the cell in the source dataset.
    */
   public static Map<String, Integer> getUnMatchedValues(DataSet source,
      int sourceColumn, FeatureMapping mapping)
   {
      return getUnMatchedValues(source, sourceColumn, mapping, null);
   }

   /**
    * Get unmatched values.
    * @param source map data set.
    * @param mapping feature mapping.
    * @return a map, key is row index, value is the cell in the source dataset.
    */
   public static Map<String, Integer> getUnMatchedValues(DataSet source,
      int sourceColumn, FeatureMapping mapping, ChartInfo info)
   {
      String type = mapping.getType();
      int layer = mapping.getLayer();
      Map mfeatures = info == null
         ? getAutoMatchedFeatures(source, sourceColumn, type, layer)
         : getAutoMatchedFeatures(source, sourceColumn, layer, info, type);

      return getUnMatchedValues(source, sourceColumn, mfeatures, mapping);
   }

   /**
    * Get unmatched values.
    * @param source map data set.
    * @param type map type.
    * @param layer map layer.
    * @return a map, key is row index, value is the cell in the source dataset.
    */
   public static Map<String, Integer> getUnMatchedValues(DataSet source,
      int sourceColumn, String type, int layer)
   {
      Map mfeatures = getAutoMatchedFeatures(source, sourceColumn, type, layer);

      return getUnMatchedValues(source, sourceColumn, mfeatures, null);
   }

   /**
    * Get unmatched values.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param autoMatchedFeatures auto matched features.
    * @return a map key is row index, value is cell value in dataset.
    */
   public static Map<String, Integer> getUnMatchedValues(DataSet source,
      int sourceColumn, Map autoMatchedFeatures, FeatureMapping mapping)
   {
      Set avalues = new HashSet(autoMatchedFeatures.keySet()); // auto matched
      Map uvalues = new TreeMap(new ValueComparator());
      Collection mvalues = mapping == null ? null :
         mapping.getMappings().keySet(); // manual matched

      if(sourceColumn == -1) {
         return uvalues;
      }

      for(int i = 0; i < source.getRowCount(); i++) {
         String value = Tool.toString(source.getData(sourceColumn, i));

         if(!avalues.contains(value) && mvalues != null &&
            !mvalues.contains(value) && !uvalues.containsValue(value))
         {
            uvalues.put(value, Integer.valueOf(i));
         }
      }

      return uvalues;
   }

   /**
    * Get supported match algorithm names.
    */
   public static String[] getAlgorithms(boolean containsNone) {
      if(containsNone) {
         return new String[] {ALGORITHM_NONE,
                              ALGORITHM_DISTANCE,
                              ALGORITHM_DOUBLE_METAPHONE,
                              ALGORITHM_METAPHONE,
                              ALGORITHM_SOUNDEX};
      }
      else {
         return new String[] {ALGORITHM_DISTANCE,
                              ALGORITHM_DOUBLE_METAPHONE,
                              ALGORITHM_METAPHONE,
                              ALGORITHM_SOUNDEX};
      }
   }

   /**
    * Get supported match algorithm names.
    */
   public static String[] getAlgorithms() {
      return getAlgorithms(true);
   }

    /**
    * Get supported match view algorithm names.
    */
   public static String[] getViewAlgorithms(boolean containsNone) {
      Catalog catalog = Catalog.getCatalog();
      if(containsNone) {
         return new String[] {catalog.getString(ALGORITHM_NONE),
                              catalog.getString(ALGORITHM_DISTANCE),
                              catalog.getString(ALGORITHM_DOUBLE_METAPHONE),
                              catalog.getString(ALGORITHM_METAPHONE),
                              catalog.getString(ALGORITHM_SOUNDEX)};
      }
      else {
         return new String[] {catalog.getString(ALGORITHM_DISTANCE),
                              catalog.getString(ALGORITHM_DOUBLE_METAPHONE),
                              catalog.getString(ALGORITHM_METAPHONE),
                              catalog.getString(ALGORITHM_SOUNDEX)};
      }
   }

   /**
    * Get supported match view algorithm names.
    */
   public static String[] getViewAlgorithms() {
      return getViewAlgorithms(true);
   }

   /**
    * Get geo code.
    */
   public static String getGeoCodeByLabel(String type, int layer,
      String label)
   {
      NameTable names = getNameTable(layer, type);
      return names == null ? null : names.getId(label);
   }

   /**
    * Get likely matched map feature.
    * @param source map data set.
    * @param sourceColumn source column index.
    * @param row row index.
    * @param type map type.
    * @param layer map layer.
    * @param name algorithm name.
    * @param mappings manual mappings.
    */
   public static MapFeature[] getLikelyFeatures(DataSet source,
      int sourceColumn, int row, String type, int layer, String name,
      Map<String, String> mappings, ChartInfo info)
   {
      NameTable names = getNameTable(layer, type);
      boolean isNone = name.equals(ALGORITHM_NONE) || names == null || name.equals("null");
      List<String> codes;

      if(isNone) {
         codes = new ArrayList<>(typeMap.get(layer).get(type));
      }
      else {
         AbstractDistanceAlgorithm algorithm = createAlgorithm(name);
         int[] sourceColumns = new int[] {sourceColumn};
         int[] nameColumns = MapData.getNameColumns(layer);
         int nameColumn = 0;
         int distance = Integer.MAX_VALUE;

         // get best name column
         for(int i = 0; i < nameColumns.length; i++) {
            int nameColumn0 = nameColumns[i];
            Match match = algorithm.findBestMatch(source, sourceColumns, names,
               new int[] {nameColumn0}, row);
            int distance0 = match.getDistance();

            if(distance0 < distance) {
               nameColumn = nameColumn0;
               distance = distance0;
            }
         }

         codes = algorithm.findMatchIDs(source, sourceColumns, names,
            new int[] {nameColumn}, row);
      }

      String[] autoCodes = getAutoMatchedGeoCodes(source, sourceColumn, type, layer, info);

      // exclude auto matching
      /*
      for(int i = 0; i < autoCodes.length; i++) {
         codes.remove(autoCodes[i]);
      }
      */

      // exclude unmatched type
      Collection typeCodes = typeMap.get(layer).get(type);

      if(typeCodes != null) {
         for(int i = codes.size() - 1; i >= 0; i--) {
            String code = codes.get(i);

            if(!typeCodes.contains(code)) {
               codes.remove(i);
            }
         }
      }

      List features = new ArrayList();

      for(int i = 0; i < codes.size(); i++) {
         MapFeature feature = createMapFeature(names, codes.get(i));

         if(feature != null) {
            features.add(feature);
         }
      }

      if(isNone) {
         Collections.sort(features, new MapFeatureComparator());
      }

      MapFeature[] arr = new MapFeature[features.size()];
      features.toArray(arr);

      return arr;
   }

   /**
    * Get algorithm.
    */
   private static AbstractDistanceAlgorithm createAlgorithm(String name) {
      String className = "inetsoft.graph.geo.solver.";

      if(name.equals(ALGORITHM_DISTANCE)) {
         className += "DistanceAlgorithm";
      }
      else if(name.equals(ALGORITHM_DOUBLE_METAPHONE)) {
         className += "DoubleMetaphoneAlgorithm";
      }
      else if(name.equals(ALGORITHM_METAPHONE)) {
         className += "MetaphoneAlgorithm";
      }
      else if(name.equals(ALGORITHM_SOUNDEX)) {
         className += "SoundexAlgorithm";
      }

      AbstractDistanceAlgorithm algorithm = null;

      try {
         algorithm = (AbstractDistanceAlgorithm)
            Class.forName(className).newInstance();
      }
      catch(Exception ex) {
         LOG.error("Failed to create a new instance of distance algorithm: " +
            className, ex);
      }

      return algorithm;
   }

   /**
    * Save the specified feature mapping to dataspace.
    * @param mapping feature mapping to save.
    */
   public static void saveMapping(FeatureMapping mapping) {
      // remove duplicate mapping
      List<FeatureMapping> amappings = getAllSavedMappings();
      String id = mapping.getID();
      boolean dup = isDuplicatedMapping(id);

      if(dup) {
         amappings.remove(getSavedMapping(id));
      }

      amappings.add(mapping);

      DataSpace space = DataSpace.getDataSpace();
      String dir = SreeEnv.getProperty("map.mapping.dir");
      String file = mapping.getID() + MAP_FILE_EXTENSION;

      try {
         if(dup) {
            space.delete(dir, file);
         }

         try(DataSpace.Transaction tx = space.beginTransaction();
             OutputStream out = tx.newStream(dir, file))
         {
            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            mapping.writeXML(writer);
            writer.flush();
            tx.commit();
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to save feature mapping", ex);
      }
   }

   /**
    * Check if the specified mapping id is duplicated.
    * @param id feature mapping id.
    */
   public static boolean isDuplicatedMapping(String id) {
      return getSavedMapping(id) != null;
   }

   /**
    * Get all feature mapping saved in dataspace.
    */
   public static List<FeatureMapping> getAllSavedMappings() {
      if(mappings == null) {
         String[] files = getMappingFiles();
         mappings = new ArrayList(files.length);

         for(int i = 0; i < files.length; i++) {
            mappings.add(getMapping0(files[i]));
         }
      }

      return mappings;
   }

   /**
    * Get mapping from dataspace by specified mapping id.
    * @param id feature mapping id.
    */
   public static FeatureMapping getSavedMapping(String id) {
      List<FeatureMapping> mappings = getAllSavedMappings();

      for(int i = 0; i < mappings.size(); i++) {
         FeatureMapping mapping = mappings.get(i);

         if(id.equals(mapping.getID())) {
            return (FeatureMapping) mapping.clone();
         }
      }

      return null;
   }

   /**
    * Get mapping from dataspace by specified mapping file name.
    */
   private static FeatureMapping getMapping0(String file) {
      DataSpace space = DataSpace.getDataSpace();
      String dir = SreeEnv.getProperty("map.mapping.dir");
      FeatureMapping mapping = new FeatureMapping();

      try(InputStream in = space.getInputStream(dir, file)) {
         if(in != null) {
            Document doc = Tool.parseXML(in);
            Element root = doc.getDocumentElement();
            mapping.parseXML(root);
         }
      }
      catch(Exception ex) {
         LOG.error("Invalid mapping file: " + file, ex);
      }

      return mapping;
   }

   /**
    * Get all mapping file names.
    */
   private static String[] getMappingFiles() {
      String dir = SreeEnv.getProperty("map.mapping.dir");
      DataSpace space = DataSpace.getDataSpace();
      String[] files = space.list(dir);

      if(files == null) {
         LOG.warn("Invalid mapping directory:" + dir);
         return new String[0];
      }

      Vector names = new Vector();

      for(int i = 0; i < files.length; i++) {
         if(files[i].endsWith(MAP_FILE_EXTENSION)) {
            names.add(files[i]);
         }
      }

      Collections.sort(names);
      return (String[]) names.toArray(new String[names.size()]);
   }

   /**
    * Get specified type and layer saved mappings.
    */
   public static List<FeatureMapping> getSavedMappings(String type,
                                                       int layer)
   {
      List<FeatureMapping> all = getAllSavedMappings();
      List<FeatureMapping> mappings = new ArrayList<>();

      for(int i = 0; i < all.size(); i++) {
         FeatureMapping mapping = all.get(i);

         if(type.equals(mapping.getType()) && layer == mapping.getLayer()) {
            mappings.add(mapping);
         }
      }

      return mappings;
   }

   /**
    * Check if the specified columne is all mapped.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param mapping the feature mapping.
    */
   public static boolean isAllMapped(DataSet source, int sourceColumn,
      FeatureMapping mapping, ChartInfo info)
   {
      if(mapping == null || source == null || sourceColumn == -1) {
         return false;
      }

      String type = mapping.getType();
      int layer = mapping.getLayer();
      Map<String, Integer> unmatched =
         getUnMatchedValues(source, sourceColumn, mapping, info);
      Map mappings = mapping.getMappings();

      for(Map.Entry<String, Integer> entry : unmatched.entrySet()) {
         if(mappings.get(entry.getKey()) == null) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get available mappings of the specified geographic ref.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param currMapping current editing feature mapping.
    * @return the mappings map, key is mapping name for display , value is
    * feature mapping.
    */
   public static TreeMap<String, FeatureMapping> getMappings(DataSet source,
      int sourceColumn, GeoRef geoRef, String type, int layer,
      FeatureMapping builtInMapping, FeatureMapping currMapping)
   {
      TreeMap<String, FeatureMapping> mappings =
         new TreeMap(new MappingIDComparator());

      if(type == null) {
         return mappings;
      }

      boolean isBuiltIn = currMapping != null &&
         BUILD_IN_MAPPING_ID.equals(currMapping.getID());
      FeatureMapping bmapping = isBuiltIn ? currMapping : builtInMapping;
      mappings.put(BUILD_IN_MAPPING_ID, bmapping);

      List<FeatureMapping> saveMappings = getSavedMappings(type, layer);

      for(int i = 0; i < saveMappings.size(); i++) {
         FeatureMapping savedMapping = saveMappings.get(i);

         if(!isBuiltIn && currMapping.getID().equals(savedMapping.getID())) {
            mappings.put(currMapping.getID(),
               (FeatureMapping) currMapping.clone());
         }
         else {
            String id = savedMapping.getID();
            FeatureMapping mapping = (FeatureMapping) savedMapping.clone();
            trimFeatureMapping(source, sourceColumn, mapping);
            mappings.put(id, mapping);
         }
      }

      return mappings;
   }

   /**
    * Remove manual values that does not contains.
    */
   public static void trimFeatureMapping(DataSet source, int sourceColumn,
      FeatureMapping mapping)
   {
      if(mapping == null) {
         return;
      }

      Set<String> values = new HashSet<>();

      for(int i = 0; i < source.getRowCount(); i++) {
         values.add(Tool.toString(source.getData(sourceColumn, i)));
      }

      List<String> mvalues = new ArrayList<>(mapping.getMappings().keySet());

      for(String mvalue:mvalues) {
         if(!values.contains(mvalue)) {
            mapping.removeMapping(mvalue);
         }
      }
   }

   /* Get best algorithm.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param type map type.
    * @param layer the layer of geographic ref.
    */
   public static String getBestAlgorithm(DataSet source, int sourceColumn,
      String type, int layer)
   {
      Map<String, Integer> unmatched =
         getUnMatchedValues(source, sourceColumn, type, layer);
      int[] sourceColumns = new int[] {sourceColumn};
      NameTable nameTable = getNameTable(layer, type);
      int[] nameColumns = MapData.getNameColumns(layer);
      String[] names = getAlgorithms(false);
      String name = names[0];
      Integer[] rows = new Integer[unmatched.size()];
      unmatched.values().toArray(rows);
      int sum = 0;

      // algorithm loop
      for(int i = 0; i < names.length; i++) {
         String name0 = names[i];
         AbstractDistanceAlgorithm algorithm = createAlgorithm(name0);

         // name column loop
         for(int j = 0; j < nameColumns.length; j++) {
            int sum0 = 0;
            int[] ncols = new int[] {nameColumns[j]};

            for(int k = 0; k < rows.length; k++) {
               Match match = algorithm.findBestMatch(source, sourceColumns,
                  nameTable, ncols, rows[k]);
               sum0 += match.getDistance();
            }

            if(sum0 < sum) {
               sum = sum0;
               name = name0;
            }
         }
      }

      return name;
   }

   /**
    * Get same mapping from viewsheet.
    * @param vs viewsheet that contains the map.
    * @param sourceInfo map source info.
    * @param refName the geographic ref name.
    * @param type the client current select map type.
    * @param layer the client current select layer.
    * @param name the current assembly name.
    */
   public static FeatureMapping getSameMapping(Viewsheet vs,
      SourceInfo sourceInfo, String refName, String type, int layer, String name)
   {
      Assembly[] assemblies = vs.getAssemblies(true);
      List<ChartInfo> cinfos = new ArrayList<>();

      for(int i = 0; i < assemblies.length; i++) {
         Assembly assembly = assemblies[i];

         if(assembly instanceof ChartVSAssembly) {
            if(assembly.getName().equals(name)) {
               continue;
            }

            ChartVSAssembly chart = (ChartVSAssembly) assembly;
            ChartVSAssemblyInfo info = (ChartVSAssemblyInfo)
               chart.getVSAssemblyInfo();
            VSChartInfo cinfo = info.getVSChartInfo();
            SourceInfo sinfo = chart.getSourceInfo();

            if(Tool.equals(sinfo, sourceInfo) &&
               Tool.equals(type, cinfo.getMapType()))
            {
               cinfos.add(cinfo);
            }
         }
      }

      return getSameMapping(cinfos, refName, type, layer);
   }

   /**
    * Get same mapping.
    */
   private static FeatureMapping getSameMapping(List<ChartInfo> cinfos,
      String refName, String type, int layer)
   {
      for(int i = 0; i < cinfos.size(); i++) {
         ChartInfo cinfo = cinfos.get(i);
         ChartRef[] refs = getGeoRefs(cinfo);

         for(int j = 0; j < refs.length; j++) {
            GeoRef geoRef = (GeoRef) refs[j];

            if(Objects.equals(refName, geoRef.getName()) &&
               layer == geoRef.getGeographicOption().getLayer())
            {
               FeatureMapping fm = geoRef.getGeographicOption().getMapping();

               if(fm != null && Objects.equals(fm.getType(), type)) {
                  return fm;
               }
            }
         }
      }

      return null;
   }

   /**
    * Get geo fields from info.
    */
   private static ChartRef[] getGeoRefs(ChartInfo cinfo) {
      if(cinfo instanceof MapInfo) {
         return ((MapInfo) cinfo).getRTGeoFields();
      }
      else if(cinfo instanceof VSChartInfo) {
         VSChartInfo vsinfo = (VSChartInfo) cinfo;
         ColumnSelection cols = vsinfo.getRTGeoColumns();
         List list = new ArrayList();

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            DataRef ref = cols.getAttribute(i);

            if(ref instanceof GeoRef) {
               list.add(ref);
            }
         }

         ChartRef[] refs = new ChartRef[list.size()];
         list.toArray(refs);

         return refs;
      }

      return new ChartRef[0];
   }

   /**
    * Get auto mapping.
    * @param vs viewsheet that contains the map.
    * @param sourceInfo map source info.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param type the client current select map type.
    * @param layer the client current select layer.
    */
   public static FeatureMapping getAutoMapping(Viewsheet vs,
      SourceInfo sourceInfo, String refName, DataSet source, int sourceColumn,
      String type, int layer)
   {
      return getAutoMapping(vs, sourceInfo, refName, source, sourceColumn, type,
         layer, null);
   }

   /**
    * Get auto mapping.
    * @param vs viewsheet that contains the map.
    * @param sourceInfo map source info.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @param type the client current select map type.
    * @param layer the client current select layer.
    * @param assemblyName the current edit assembly name.
    */
   public static FeatureMapping getAutoMapping(Viewsheet vs,
      SourceInfo sourceInfo, String refName, DataSet source, int sourceColumn,
      String type, int layer, String assemblyName)
   {
      FeatureMapping sameMapping = getSameMapping(vs, sourceInfo, refName,
         type, layer, assemblyName);

      if(sameMapping != null) {
         FeatureMapping mapping = (FeatureMapping) sameMapping.clone();
         mapping.setID(BUILD_IN_MAPPING_ID);
         return mapping;
      }

      String algorithm = getBestAlgorithm(source, sourceColumn, type, layer);
      FeatureMapping mapping = new FeatureMapping(BUILD_IN_MAPPING_ID,
         algorithm, type, layer);

      return mapping;
   }

   /**
    * Auto detect map type, layer and mapping.
    * @param vs viewsheet that contains the map.
    * @param sourceInfo map source info.
    * @param info map info.
    * @param source map data set.
    * @return true if type, layer or mapping changed.
    */
   public static boolean autoDetect(Viewsheet vs, SourceInfo sourceInfo,
      ChartInfo info, GeographicOption opt, String refName, DataSet source)
   {
      int sourceColumn = GraphUtil.indexOfHeader(source, refName);
      boolean changed = autoDetectLayer(info, opt, sourceColumn, source);
      String type = autoDetectType(info, opt, sourceColumn, source);

      if(type == null) {
         type = info.getMapType();
      }
      else {
         changed = true;
      }

      int layer = opt.getLayer();
      boolean isMappingValid = isMappingValid(opt.getMapping(), type, layer);

      if(!isMappingValid) {
         FeatureMapping mapping = getAutoMapping(vs, sourceInfo, refName,
            source, sourceColumn, type, layer);
         opt.setMapping(mapping);
         changed = true;
      }

      return changed;
   }

   /**
    * Auto detect layer.
    * @param info map info.
    * @param source map data set.
    * @param sourceColumn geographic ref index.
    * @return true if layer changes.
    */
   private static boolean autoDetectLayer(ChartInfo info, GeographicOption opt,
                                          int sourceColumn, DataSet source)
   {
      String type = getBoundMapType(info);
      int layer = opt.getLayer();
      boolean isLayerValid = isCompatible(type, layer);

      if(!isLayerValid) {
         layer = getAutoLayer(source, sourceColumn, type);
         opt.setLayerValue(layer + "");
         return true;
      }

      return false;
   }

   // this method returns the map type that is specified user by geo column
   private static String getBoundMapType(ChartInfo info) {
      String type = info.getMapType();

      if(info instanceof MapInfo) {
         // if it's the default type, return null so the auto-detect will check all
         // possible map types instead of assuming it's World. (51972)
         if("World".equals(type) && StringUtils.isEmpty(info.getMeasureMapType())) {
            type = "";
         }
      }

      return type;
   }

   /**
    * Auto detect type.
    */
   private static String autoDetectType(ChartInfo info, GeographicOption opt,
                                         int sourceColumn, DataSet source)
   {
      String type = getBoundMapType(info);
      boolean isTypeValid = isValidType(type);

      if(!isTypeValid) {
         int layer = opt.getLayer();
         type = getAutoType(source, sourceColumn, layer);
         return type;
      }

      return null;
   }

   /**
    * Check if the spefield layer is compatible with the specified type.
    */
   private static boolean isCompatible(String type, int layer) {
      Collection layers = MapData.getLayers(type).values();

      return layers.contains(layer);
   }

   /**
    * Check if the type is valid.
    */
   public static boolean isValidType(String type) {
      String[] types = MapData.getMapTypes();

      for(int i = 0; i < types.length; i++) {
         if(types[i].equals(type)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if mapping is valid.
    */
   private static boolean isMappingValid(FeatureMapping mapping, String type,
      int layer)
   {
      return mapping != null && type.equals(mapping.getType()) &&
         layer == mapping.getLayer();
   }

   /**
    * Get meta data for specified map type and layer.
    * @param type map type.
    * @param layer map layer.
    * @param index city mete data index.
    */
   public static String[] getMetaData(String type, int layer, int index) {
      if(MapData.isPointLayer(layer)) {
         String[][] data = MapData.getPointSampleData(type, layer);
         index = index >= data.length ? data.length - 1 : index;

         return data[index];
      }
      else {
         return MapData.getShapeSampleData(type, layer);
      }
   }

   /**
    * Get longitude meta data.
    */
   public static Double[] getLonMetaData(String type, int index) {
      // for explicit measure only map, type is null
      type = isValidType(type) ? type : "World";
      Point2D[] pts = getMetaData(type, index);
      Double[] lons = new Double[pts.length];

      for(int i = 0; i < pts.length; i++) {
         lons[i] = Double.valueOf(pts[i].getX());
      }

      return lons;
   }

   /**
    * Get longitude meta data.
    */
   public static Double[] getLatMetaData(String type, int index) {
      // for explicit measure only map, type is null
      type = isValidType(type) ? type : "World";
      Point2D[] pts = getMetaData(type, index);
      Double[] lats = new Double[pts.length];

      for(int i = 0; i < pts.length; i++) {
         lats[i] = Double.valueOf(pts[i].getY());
      }

      return lats;
   }

   /**
    * Get meta data for explicit measure.
    */
   private static Point2D[] getMetaData(String type, int index) {
      String[] cities = getMetaData(type, MapData.LAYER_CITY, index);
      DataSet source = new DefaultDataSet(new String[][] {{"city"}, {cities[0]},
         {cities[1]}});
      int sourceColumn = 0;
      int layer = MapData.LAYER_CITY;

      String[] codes = getAutoMatchedGeoCodes(source, sourceColumn, type, layer);
      Point2D[] pts = new Point2D[codes.length];

      GeoPoints points = MapData.getGeoPoints(type, layer);

      try {
         for(int i = 0; i < pts.length; i++) {
            pts[i] = points.getPoint(codes[i]);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get the measure meta-data for type " +
            type + " at index " + index, ex);
      }

      return pts;
   }

   /**
    * Get manual mappings, key is geo code, value is feature name.
    */
   public static Map<String, String> getManualMappings(FeatureMapping mapping) {
      Map<String, String> mappings = mapping.getMappings();
      Map<String, String> map = new HashMap<>();
      int layer = mapping.getLayer();

      for(Map.Entry<String, String> entry : mappings.entrySet()) {
         String geoCode = entry.getValue();
         String name = getFeatureName(layer, geoCode);
         map.put(geoCode, name);
      }

      return map;
   }

   /**
    * Map feature comparator.
    */
   private static class MapFeatureComparator implements Comparator {
      /**
       * Compare two map feature.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         if(obj1 instanceof MapFeature && obj2 instanceof MapFeature) {
            String name1 = ((MapFeature) obj1).getName();
            String name2 = ((MapFeature) obj2).getName();

            return name1.compareTo(name2);
         }

         return 0;
      }
   }

   /**
    * Mapping id comparator.
    */
   private static class MappingIDComparator implements Comparator {
      /**
       * Compare two map feature.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         boolean b1 = BUILD_IN_MAPPING_ID.equals(obj1);
         boolean b2 = BUILD_IN_MAPPING_ID.equals(obj2);

         if(b1 && b2) {
            return 0;
         }
         else if(b1) {
            return -1;
         }
         else if(b2) {
            return 1;
         }

         if(obj1 instanceof String && obj2 instanceof String) {
            String val1 = (String) obj1;
            String val2 = (String) obj2;

            return val1.compareTo(val2);
         }

         return 0;
      }
   }

   /**
    * Map data value comparator.
    */
   private static class ValueComparator implements Comparator {
      /**
       * Compare two map feature.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         if(obj1 instanceof String && obj2 instanceof String) {
            String val1 = (String) obj1;
            String val2 = (String) obj2;

            return val1.compareTo(val2);
         }

         return 0;
      }
   }

   public static class LayerComparator implements Comparator<String> {
      public LayerComparator(Map<String,Integer> mapping) {
         this.mapping = mapping;
      }

      @Override
      public int compare(String v1, String v2) {
         return mapping.get(v1) - mapping.get(v2);
      }

      private Map<String,Integer> mapping;
   }

   /**
    * Get feature mapping.
    */
   public static String[][] getMappings(FeatureMapping fm) {
      if(fm == null) {
         return new String[0][0];
      }

      Map<String, String> map = fm.getMappings();
      String[][] mappings = new String[map.size()][2];
      List<String> values = new ArrayList<>(map.keySet());

      for(int i = 0; i < mappings.length; i++) {
         String value = values.get(i);
         String geoCode = map.get(value);

         mappings[i][0] = value;
         mappings[i][1] = geoCode;
      }

      return mappings;
   }

   /**
    * Geo ref comparator.
    */
   public static class GeoRefComparator implements Comparator {
      /**
       * Compare two geographic field.
       */
      @Override
      public int compare(Object obj1, Object obj2) {
         if(obj1 instanceof GeoRef && obj2 instanceof GeoRef) {
            GeoRef ref1 = (GeoRef) obj1;
            GeoRef ref2 = (GeoRef) obj2;
            int layer1 = ref1.getGeographicOption().getLayer();
            int layer2 = ref2.getGeographicOption().getLayer();

            return layer1 - layer2;
         }

         return 0;
      }
   }

   /**
    * Update saved mapping.
    */
   public static void updateFeatureMapping(DataSet source, int sourceColumn,
      FeatureMapping mapping)
   {
      FeatureMapping smapping = MapHelper.getSavedMapping(mapping.getID());

      if(smapping == null) {
         return;
      }

      trimFeatureMapping(source, sourceColumn, smapping);
      Map<String, String> mmappings = smapping.getMappings();
      Collection keys = mapping.getMappings().keySet();

      for(Map.Entry<String, String> entry : mmappings.entrySet()) {
         if(!keys.contains(entry.getKey())) {
            mapping.addMapping(entry.getKey(), entry.getValue());
         }
      }
   }

   /**
    * Get a named table. If the layer has no name in the name table, don't
    * use the name table. It should default to no mapping.
    */
   private static NameTable getNameTable(int layer, String type) {
      NameTable names = MapData.getNameTable(layer);

      if(type == null || type.length() == 0) {
         return names;
      }

      Map<String,Collection> codemap = getTypeMap().get(layer);

      if(names != null && codemap != null) {
         Collection<String> codes = codemap.get(type);

         if(codes == null) {
            return null;
         }

         for(String code : codes) {
            String name = getFeatureName0(names, code);

            if(name != null && name.length() > 0) {
               return names;
            }
         }
      }

      return null;
   }

   /**
    * Get the full name of GeoRef if it contains multi mappings.
    */
   public static String getGeoRefFullName(MapInfo minfo, String name) {
      ChartRef[] geoFlds = minfo.getRTGeoFields();

      for(ChartRef geofld:geoFlds) {
         if(geofld instanceof GeoRef && name.equals(geofld.getName()) &&
            isNamedGroupGeoRef((GeoRef) geofld))
         {
            return geofld.getFullName();
         }
      }

      return name;
   }

   /**
    * Check the gref is named group?
    */
   public static boolean isNamedGroupGeoRef(GeoRef gref) {
      Map map = gref.getGeographicOption().getMapping().getDupMapping();
      return map != null && map.size() > 0;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MapHelper.class);

   private static final String MAPPING_DIR = "map";
   private static final String MAP_FILE_EXTENSION = ".mapping";
   private static Map<Integer,Collection> layerMap;
   // layer -> (map-name -> shape/point-name)
   private static Map<Integer,Map<String,Collection>> typeMap;
   private static List<FeatureMapping> mappings;
   // duplicated feature names
   private static Map<String, ArrayList> dupNames = new HashMap<>();
}
