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

import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.geo.*;
import inetsoft.graph.geo.solver.NameTable;
import inetsoft.report.StyleFont;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class holds information about a map.
 * A custom map can be used by adding a map tag.
 *
 * <style name="disputed">
 *   <line width="1" dash="1"/>
 *   <fill color="#FF0000" alpha="1"/>
 *   <font color="#FFFF00" font="Arial-BOLD-11"/>
 * </style>
 *
 * <map type="WHO">
 *   <shapedata data="/detailed_2008.csv" layer="Country">
 *     <shapes style="disputed" data="/who_disputed.csv"/>
 *     <shapes data="/inetsoft/graph/geo/data/world.csv"/>
 *     <labels data="/fixed_labels.csv"/>
 *     <sampledata>China</sampledata>
 *     <sampledata>United States</sampledata>
 *   </shapedata>
 * </map>
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public class MapData {
   /**
    * The city layer.
    */
   public static final int LAYER_CITY = 1001;

   /**
    * The zip layer.
    */
   public static final int LAYER_ZIP = 1002;

   /**
    * Get supported map types.
    */
   public static String[] getMapTypes() {
      String[] arr = mapdata.keySet().toArray(new String[mapdata.size()]);
      Arrays.sort(arr, new MapNameComparator());
      return arr;
   }

   /**
    * Get supported map types. localize the labels.
    */
   public static Map<String,String> getMapTypesLabel() {
      String[] arr = getMapTypes();
      Map<String,String> map = new LinkedHashMap<>();

      for(int i = 0; i < arr.length; i++) {
         map.put(Catalog.getCatalog().getString(arr[i]), arr[i]);
      }

      return map;
   }

   /**
    * Get layers supported by the specified map type .
    * @param type the specified map type.
    */
   public static Map<String,Integer> getLayers(String type) {
      Map<String,Integer> map = new LinkedHashMap<>();
      MapData mdata = mapdata.get(type);

      if(mdata == null) {
         return map;
      }

      Map<String, String> layers = mdata.getLayers();

      for(String layer : layers.keySet()) {
         Integer layerCode = maplayers.get(layer);

         if(layerCode == null) {
            throw new RuntimeException("Layer not defined in mapdata.xml: " + layer);
         }

         map.put(Catalog.getCatalog().getString(layers.get(layer)), layerCode);
      }

      return map;
   }

   /**
    * Get layer value by name.
    */
   public static int getLayer(String name) {
      return maplayers.get(name);
   }

   /**
    * Get layer name by id.
    */
   public static String getLayerName(int id) {
      Iterator<String> it = maplayers.keySet().iterator();

      while(it.hasNext()) {
         String name = it.next();
         Integer id0 = maplayers.get(name);

         if(id0 == id) {
            return name;
         }
      }

      return null;
   }

   /**
    * Check if a layer is a point (non-polygon) layer.
    */
   public static boolean isPointLayer(int id) {
      return id >= 1000;
   }

   /**
    * Get all supported layers.
    */
   public static int[] getLayerIds() {
      return maplayers.values().stream().mapToInt(n -> n).toArray();
   }

   /**
    * Get all supported layers.
    */
   public static String[] getLayerNames() {
      String[] arr = new String[maplayers.size()];
      int i = 0;

      for(String n : maplayers.keySet()) {
         arr[i++] = n;
      }

      return arr;
   }

   /**
    * Get geo map.
    * @param type map type.
    */
   public static GeoMap getGeoMap(String type, int layer, boolean webmap) {
      try {
         if(type != null && !"null".equals(type)) {
            return mapdata.get(type).getGeoMap(layer, webmap);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get geography map: " + type + "(" + layer + ")", ex);
      }

      return null;
   }

   /**
    * Get GeoPoints.
    * @param type map type.
    */
   public static GeoPoints getGeoPoints(String type, int layer) {
      try {
         return mapdata.get(type).getGeoPoints(layer);
      }
      catch(Exception ex) {
         LOG.error("Failed to get geography points", ex);
      }

      return null;
   }

   /**
    * Get the sample data for shapes.
    */
   public static String[] getShapeSampleData(String type, int layer) {
      return mapdata.get(type).getShapeSampleData(layer);
   }

   /**
    * Get the sample data for points.
    */
   public static String[][] getPointSampleData(String type, int layer) {
      return mapdata.get(type).getPointSampleData(layer);
   }

   /**
    * Get name table of specified layer.
    * @param layer map layer.
    */
   public static NameTable getNameTable(int layer) {
      return nametables.get(layernametables.get(layer));
   }

   /**
    * Get name columns of specified layer.
    * @param layer the map layer.
    */
   public static int[] getNameColumns(int layer) {
      int[] cols = matchcols.get(layer + "");
      return (cols == null) ? new int[0] : cols;
   }

   /**
    * Get name columns of specified layer.
    * @param childLayer the map child layer.
    * @param parentLayer the map parrnt layer.
    */
   public static int[] getNameColumns(int childLayer, int parentLayer) {
      int[] cols = matchcols.get(parentLayer + "," + childLayer);
      return (cols == null) ? new int[0] : cols;
   }

   /**
    * Check if the specifed layer is valid.
    */
   public static boolean isValidLayer(int layer) {
      return maplayers.values().contains(layer);
   }

   /**
    * Check if is empty.
    */
   public static boolean isEmpty() {
      return false;
   }

   /**
    * Create a map shape frame.
    */
   public static GeoShapeFrame createShapeFrame(String fld) {
      GeoShapeFrame frame = new GeoShapeFrame(fld);

      for(String name : linestyles.keySet()) {
         frame.setLine(name, linestyles.get(name));
      }

      for(String name : fillstyles.keySet()) {
         frame.setFill(name, fillstyles.get(name));
      }

      for(String name : fontstyles.keySet()) {
         frame.setFont(name, fontstyles.get(name));
      }

      for(String name : colorstyles.keySet()) {
         frame.setFontColor(name, colorstyles.get(name));
      }

      return frame;
   }

   /**
    * Parse a map data definition.
    */
   private void parseXML(Element elem) throws Exception {
      type = Tool.getAttribute(elem, "type");

      NodeList list = Tool.getChildNodesByTagName(elem, "shapedata");

      for(int i = 0; i < list.getLength(); i++) {
         Element sdata = (Element) list.item(i);
         String data = Tool.getAttribute(sdata, "data");
         String layer = Tool.getAttribute(sdata, "layer");
         String alias = Tool.getAttribute(sdata, "alias");
         alias = alias == null || "".equals(alias) ? layer : alias;
         ShapeData shapeData = new ShapeData(data);

         layers.put(layer, alias);
         shapedata.put(getLayerKey(maplayers.get(layer), false), shapeData);

         if(ShapeData.exists(data + "_WEB")) {
            shapedata.put(getLayerKey(maplayers.get(layer), true), new ShapeData(data + "_WEB"));
         }

         NodeList shapes = Tool.getChildNodesByTagName(sdata, "shapes");

         for(int k = 0; k < shapes.getLength(); k++) {
            Element snode = (Element) shapes.item(k);
            String name = Tool.getAttribute(snode, "data");
            String style = Tool.getAttribute(snode, "style");

            shapeData.addData(name, style);
         }

         NodeList labels = Tool.getChildNodesByTagName(sdata, "labels");

         for(int k = 0; k < labels.getLength(); k++) {
            Element snode = (Element) labels.item(k);
            String name = Tool.getAttribute(snode, "data");

            shapeData.addLabel(name);
         }

         NodeList samples = Tool.getChildNodesByTagName(sdata, "sampledata");
         String[] arr = new String[samples.getLength()];

         for(int k = 0; k < samples.getLength(); k++) {
            arr[k] = Tool.getValue(samples.item(k));
         }

         sampleshapes.put(maplayers.get(layer), arr);
      }

      list = Tool.getChildNodesByTagName(elem, "pointdata");

      for(int i = 0; i < list.getLength(); i++) {
         Element pdata = (Element) list.item(i);
         String data = Tool.getAttribute(pdata, "data");
         String layer = Tool.getAttribute(pdata, "layer");
         String alias = Tool.getAttribute(pdata, "alias");
         alias = alias == null || "".equals(alias) ? layer : alias;

         layers.put(layer, alias);
         pointdata.put(maplayers.get(layer), data);

         NodeList samples = Tool.getChildNodesByTagName(pdata, "sampledata");
         String[][] arr = new String[3][2];

         for(int k = 0, r = 0, c = 0; k < samples.getLength(); k++) {
            arr[r][c++] = Tool.getValue(samples.item(k));

            if(c >= arr[r].length) {
               r++;
               c = 0;
            }
         }

         samplepoints.put(maplayers.get(layer), arr);
      }
   }

   /**
    * Get the constant of a class.
    */
   private static Object getConstant(Class cls, String fld) throws Exception {
      Field field = cls.getField(fld);
      return field.get(null);
   }

   /**
    * Get the map name.
    */
   private String getType() {
      return type;
   }

   /**
    * Get the layers in this map.
    * @return a map from layer name to layer id.
    */
   private Map<String, String> getLayers() {
      return layers;
   }

   /**
    * Get the map shape data.
    */
   private synchronized GeoMap getGeoMap(int layer, boolean webmap) throws Exception {
      GeoMap map = null;

      if(webmap) {
         String key = getLayerKey(layer, true);

         if(!shapes.containsKey(key)) {
            ShapeData data = shapedata.get(key);

            if(data != null) {
               map = data.createMap();
               shapes.put(key, map);
            }
            else {
               shapes.put(key, NULLMAP);
            }
         }

         map = shapes.get(key);
      }

      if(map == null || map == NULLMAP) {
         String key = getLayerKey(layer, false);
         map = shapes.get(key);

         if(map == null) {
            ShapeData data = shapedata.get(key);

            if(data != null) {
               map = data.createMap();
               shapes.put(key, map);
            }
         }
      }

      return map;
   }

   /**
    * Get the map point data.
    */
   private synchronized GeoPoints getGeoPoints(int layer) throws Exception {
      GeoPoints map = points.get(layer);

      if(map == null) {
         String data = pointdata.get(layer);

         if(data != null) {
            try {
               map = (GeoPoints) getConstant(GeoPoints.class, data);
            }
            catch(Exception ex) {
               map = GeoPoints.getPoints(data);
            }

            points.put(layer, map);
         }
      }

      return map;
   }

   /**
    * Get the sample data for shapes.
    */
   private String[] getShapeSampleData(int layer) {
      return sampleshapes.get(layer);
   }

   /**
    * Get the sample data for points.
    */
   private String[][] getPointSampleData(int layer) {
      return samplepoints.get(layer);
   }

   /**
    * Load map meta data.
    */
   private static void loadMapData(InputStream input) throws Exception {
      Document doc = Tool.parseXML(input);
      NodeList list = doc.getElementsByTagName("nametable");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = (Element) list.item(i);
         String name = Tool.getAttribute(node, "name");
         String file = Tool.getAttribute(node, "file");
         String capital = Tool.getAttribute(node, "capital");
         String labels = Tool.getAttribute(node, "labels");
         String[] labelsArr = Tool.split(labels, ',');
         int capitalColumn = capital != null && !capital.isEmpty() ? Integer.parseInt(capital) : -1;

         try {
            nametables.put(name, new NameTable(name, capitalColumn, file, toIntegers(labelsArr)));
         }
         catch(Exception ex) {
            LOG.error("Failed to load name table", ex);
         }
      }

      list = doc.getElementsByTagName("matching");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = (Element) list.item(i);
         String layers = Tool.getAttribute(node, "layers");
         String columns = Tool.getAttribute(node, "columns");
         String[] arr = Tool.split(columns, ',');
         int[] idxes = toIntegers(arr);

         matchcols.put(layers, idxes);
      }

      list = doc.getElementsByTagName("style");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = (Element) list.item(i);
         String name = Tool.getAttribute(node, "name");
         Element line = Tool.getChildNodeByTagName(node, "line");
         Element fill = Tool.getChildNodeByTagName(node, "fill");
         Element font = Tool.getChildNodeByTagName(node, "font");

         if(line != null) {
            String wstr = Tool.getAttribute(line, "width");
            String dstr = Tool.getAttribute(line, "dash");
            double width = (wstr == null) ? 1 : Double.parseDouble(wstr);
            double dash = (dstr == null) ? 0 : Double.parseDouble(dstr);

            linestyles.put(name, new GLine(dash, width));
         }

         if(fill != null) {
            String cstr = Tool.getAttribute(fill, "color");
            String astr = Tool.getAttribute(fill, "alpha");
            int rgb = (cstr == null) ? 0xFFFFFF : Integer.decode(cstr);

            if(astr != null) {
               int alpha = (int) (Double.parseDouble(astr) * 255);
               rgb = rgb | (alpha << 24);
            }

            fillstyles.put(name, new Color(rgb, true));
         }

         if(font != null) {
            String fstr = Tool.getAttribute(font, "font");
            String cstr = Tool.getAttribute(font, "color");

            if(fstr != null) {
               fontstyles.put(name, StyleFont.decode(fstr));
            }

            if(cstr != null) {
               colorstyles.put(name, new Color(Integer.decode(cstr)));
            }
         }
      }

      list = doc.getElementsByTagName("layer");

      for(int i = 0; i < list.getLength(); i++) {
         Element layer = (Element) list.item(i);
         int id = Integer.parseInt(Tool.getAttribute(layer, "id"));
         String nametable = Tool.getAttribute(layer, "nametable");

         maplayers.put(Tool.getAttribute(layer, "name"), id);
         layernametables.put(id, nametable);
      }

      list = doc.getElementsByTagName("map");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = (Element) list.item(i);
         String type = Tool.getAttribute(node, "type");
         MapData mdata = mapdata.get(type);

         if(mdata == null) {
            mapdata.put(type, mdata = new MapData());
         }

         mdata.parseXML(node);
      }
   }

   /**
    * Convert to integers.
    */
   private static int[] toIntegers(String[] arr) {
      int[] idxes = new int[arr.length];

      for(int k = 0; k < arr.length; k++) {
         idxes[k] = Integer.parseInt(arr[k]);
      }

      return idxes;
   }

   private static String getLayerKey(int layer, boolean webmap) {
      return layer + (webmap ? "_web" : "");
   }

   private static Map<String,MapData> mapdata;
   private static Map<String,Integer> maplayers;
   private static Map<String,NameTable> nametables; // name table name -> ...
   private static Map<Integer,String> layernametables; // id -> name table name
   private static Map<String,int[]> matchcols; // ids -> columns
   private static Map<String,GLine> linestyles = new LinkedHashMap<>();
   private static Map<String,Color> fillstyles = new LinkedHashMap<>();
   private static Map<String,Font> fontstyles = new LinkedHashMap<>();
   private static Map<String,Color> colorstyles = new LinkedHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(MapData.class);

   static {
      try {
         try(InputStream input = MapData.class.
            getResourceAsStream("/inetsoft/report/internal/graph/mapdata.xml"))
         {
            maplayers = new LinkedHashMap<>();
            mapdata = new LinkedHashMap<>();
            nametables = new LinkedHashMap<>();
            matchcols = new LinkedHashMap<>();
            layernametables = new LinkedHashMap<>();
            loadMapData(input);
         }

         // load extended map data
         DataSpace space = DataSpace.getDataSpace();

         if(space.exists(null, "mapdata.xml")) {
            try(InputStream input = space.getInputStream(null, "mapdata.xml")) {
               loadMapData(input);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize map data", ex);
      }
   }

   private static class MapNameComparator implements Comparator<String> {
      @Override
      public int compare(String v1, String v2) {
         if("U.S.".equals(v1)) {
            return -1;
         }
         else if("U.S.".equals(v2)) {
            return 1;
         }

         return v1.compareTo(v2);
      }
   }

   /**
    * This class captures the shape data information.
    */
   private static class ShapeData {
      public ShapeData(String data) {
         this.data = data;
      }

      private static boolean exists(String data) {
         try {
            getConstant(GeoMap.class, data);
            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }

      public GeoMap createMap() throws Exception {
         GeoMap map = null;

         try {
            // if data is a builtin map (e.g. US)
            map = (GeoMap) getConstant(GeoMap.class, data);
         }
         catch(Exception ex) {
            // otherwise load from the file pointed to by data
            map = GeoMap.getMap(data, false);
         }

         // load additional shapes. if style is defined (<shapes style='...'), then
         // the shapes are treated as overlay, which means the show will always be
         // drawn without data to back it
         for(int i = 0; i < datas.size(); i++) {
            map.loadShapes(datas.get(i), styles.get(i));
         }

         for(int i = 0; i < labels.size(); i++) {
            map.loadLabels(labels.get(i));
         }

         return map;
      }

      public void addData(String data, String style) {
         datas.add(data);
         styles.add(style);
      }

      public void addLabel(String label) {
         labels.add(label);
      }

      private String data;
      private List<String> datas = new ArrayList<>();
      private List<String> styles = new ArrayList<>();
      private List<String> labels = new ArrayList<>();
   }

   private static GeoMap NULLMAP = new GeoMap();
   private String type;

   //layer->alias, layers need to be stored in order
   private Map<String, String> layers = Collections.synchronizedMap(new LinkedHashMap<>());
   private Map<String,GeoMap> shapes = new ConcurrentHashMap<>();
   private Map<Integer,GeoPoints> points = new ConcurrentHashMap<>();
   private Map<String,ShapeData> shapedata = new ConcurrentHashMap<>();
   private Map<Integer,String> pointdata = new ConcurrentHashMap<>();
   private Map<Integer,String[]> sampleshapes = new ConcurrentHashMap<>();
   private Map<Integer,String[][]> samplepoints = new ConcurrentHashMap<>();
}
