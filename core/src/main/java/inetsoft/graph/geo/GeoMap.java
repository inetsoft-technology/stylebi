/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.geo;

import inetsoft.graph.geo.parser.WKTParser;
import inetsoft.util.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;

/**
 * A GeoMap contains all shapes for a geographical region. It represents one map defined
 * in mapdata.xml, e.g. US, CANADA.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class GeoMap implements Serializable {
   /**
    * United States state shapes, keyed on two-character state code, with Alaska and
    * Hawaii scaled and moved to the lower left corner.
    */
   public static final GeoMap US;

   /**
    * United States state shapes, keyed on two-character state code, with Alaska and
    * Hawaii at their real locations.
    */
   public static final GeoMap US_WEB;

   /**
    * Canada providence shapes, keyed on two-character providence code.
    */
   public static final GeoMap CANADA;

   /**
    * Mexico state shapes, keyed on three-character state code.
    */
   public static final GeoMap MEXICO;

   /**
    * European country shapes, keyed on ISO three character code.
    */
   public static final GeoMap EUROPE;

   /**
    * Asian country shapes, keyed on ISO three character code.
    */
   public static final GeoMap ASIA;

   /**
    * Country shapes, keyed on ISO three character code.
    */
   public static final GeoMap WORLD;

   /**
    * Create a shape frame.
    */
   public GeoMap() {
   }

   /**
    * Reads the shape from a line of text. The line is a | separated fields:
    * ID
    * x for primary anchor
    * y for primary anchor
    * x for secondary anchor (optional)
    * y for secondary anchor (optional)
    *
    * @param line the text to read.
    * @param map the map to which the shape will be added.
    * @param style shape style class.
    * @return the read shape.
    * @throws IOException if an I/O error occurs.
    */
   protected GeoShape readShape(String line, Map<String, GeoShape> map, String style)
      throws IOException
   {
      String id;
      GeoShape shape;
      Point2D primary = null;
      Point2D secondary = null;

      int i1 = 0;
      int i2 = line.indexOf('|', i1);
      id = line.substring(i1, i2);

      i1 = i2 + 1;
      i2 = line.indexOf('|', i1);

      Double x = Double.NaN;
      Double y = Double.NaN;

      if(i2 > i1) {
         x = Double.parseDouble(line.substring(i1, i2));
      }

      i1 = i2 + 1;
      i2 = line.indexOf('|', i1);

      if(i2 > i1) {
         y = Double.parseDouble(line.substring(i1, i2));
      }

      if(!Double.isNaN(x) && !Double.isNaN(y)) {
         primary = new Point2D.Double(x, y);
      }

      x = Double.NaN;
      y = Double.NaN;

      i1 = i2 + 1;
      i2 = line.indexOf('|', i1);

      if(i2 > i1) {
         x = Double.parseDouble(line.substring(i1, i2));
      }

      i1 = i2 + 1;
      i2 = line.indexOf('|', i1);

      if(i2 > i1) {
         y = Double.parseDouble(line.substring(i1, i2));
      }

      if(x != Double.NaN && y != Double.NaN) {
         secondary = new Point2D.Double(x, y);
      }

      i1 = i2 + 1;

      try {
         shape = WKTParser.parse(line.substring(i1), rawBounds);
         shape.setPrimaryAnchor(primary);
         shape.setSecondaryAnchor(secondary);
         shape.setStyle(style);
      }
      catch(Exception exc) {
         throw new IOException("Invalid shape file: " + exc);
      }

      map.put(id, shape);
      return shape;
   }

   /**
    * Loads a shape file. The file could be the name of one of the built-in
    * shape, such as us-states.csv, or a user supplied shape file.
    *
    * @param file built-in shape file name or the full resource path of a
    *             custom shape file.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected void load(String file) throws IOException {
      loadShapes(file, null);
   }

   /**
    * Get the input stream of a file.
    */
   private InputStream getDataInputStream(String path) throws IOException {
      InputStream inp = getClass().getResourceAsStream(path);

      if(inp == null) {
         String file = path;

         if(file.startsWith("/")) {
            file = file.substring(1);
         }

         int index = file.lastIndexOf('/');
         String dir = index < 0 ? null : file.substring(0, index);
         file = index < 0 ? file : file.substring(index + 1);
         DataSpace space = DataSpace.getDataSpace();

         if(space.exists(dir, file)) {
            inp = space.getInputStream(dir, file);
         }
      }

      if(inp == null) {
         throw new IOException("Map file not found: " + path);
      }

      if(path.endsWith(".gz")) {
         inp = new GZIPInputStream(inp);
      }
      else {
         inp = new BufferedInputStream(inp);
      }

      return inp;
   }

   /**
    * Load a shape file into the internal cache.
    * @param file shape file name or resource path.
    * @param style the style class for all the shapes.
    */
   public void loadShapes(String file, String style) throws IOException {
      InputStream inp = getDataInputStream(file);

      try(BufferedReader reader =
             new BufferedReader(new InputStreamReader(inp, StandardCharsets.UTF_8)))
      {
         String line;
         // extra shapes are loaded first (regular shapes loaded on demand later)
         Map<String, GeoShape> shapeFeatures = (style == null) ? this.features : features2;

         while((line = reader.readLine()) != null) {
            readShape(line, shapeFeatures, style);
         }
      }
   }

   /**
    * Load a label file into the internal cache. Each line consists of
    * id|label
    * @param file label file name or resource path.
    */
   public void loadLabels(String file) throws IOException {
      InputStream inp = getDataInputStream(file);

      try(BufferedReader reader =
             new BufferedReader(new InputStreamReader(inp, StandardCharsets.UTF_8)))
      {
         String line;

         while((line = reader.readLine()) != null) {
            int idx = line.indexOf('|');

            if(idx > 0) {
               String id = line.substring(0, idx).trim();
               String label = line.substring(idx + 1).trim();
               GeoShape shape = features.get(id);

               if(shape == null) {
                  shape = features2.get(id);
               }

               if(shape != null) {
                  shape.setLabel(label);
               }
            }
         }
      }
   }

   /**
    * Make sure the shapes are loaded.
    */
   private void checkLoad() throws Exception {
      lock.readLock().lock();

      if(!loaded) {
         lock.readLock().unlock();
         lock.writeLock().lock();

         try {
            load(file);
         }
         // for io exception, it should not block user action
         catch(IOException ex) {
            LOG.warn("Failed to load shape file", ex);
         }
         finally {
            loaded = true;
            lock.readLock().lock();
            lock.writeLock().unlock();
         }
      }

      lock.readLock().unlock();
   }

   /**
    * Gets the shape with the specified name.
    * @param name the name of the feature.
    * @return the named feature or <tt>null</tt> if it does not exist.
    */
   public GeoShape getShape(String name) throws Exception {
      checkLoad();

      GeoShape geoshape = features.get(name);

      if(geoshape == null) {
         geoshape = features2.get(name);
      }

      return geoshape;
   }

   /**
    * Get the names of all shapes in this map.
    * @return a collection of shape names.
    */
   public Collection<String> getNames() throws Exception {
      checkLoad();
      // the order of the rendering should be the regular shapes first
      // and then the extra shapes
      List<String> names = new ArrayList<>(features.keySet());
      names.addAll(features2.keySet());

      return names;
   }

   /**
    * Gets the map with the specified name.
    * @param name the name of the map.
    * @param rawBounds true if using shape bounds as-is without checking for bounds
    * spanning date line/
    * @return the named map.
    */
   public static synchronized GeoMap getMap(String name, boolean rawBounds) {
      GeoMap map = maps.get(name);

      if(map == null) {
         if(name.endsWith(".gz")) {
            map = new BufferedGeoMap();
         }
         else {
            map = new GeoMap();
         }

         // file is loaded ondemand later (checkLoad)
         map.file = name;
         map.rawBounds = rawBounds;
         maps.put(name, map);
      }

      return map;
   }

   /**
    * Check if the geoshape should overlay others.
    */
   public boolean isOverlayShape(String name) {
      return features2.get(name) != null;
   }

   /**
    * Get the shape file path.
    */
   public String getFile() {
      return file;
   }

   // regular shapes
   private Map<String,GeoShape> features = new LinkedHashMap<>();
   // extra shapes
   private Map<String,GeoShape> features2 = new LinkedHashMap<>();
   private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private String file;
   private boolean loaded = false;
   private boolean rawBounds = false;

   private static final Map<String,GeoMap> maps = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(GeoMap.class);

   static {
      US = getMap("data/us-states.csv", false);
      US_WEB = getMap("data/us-states_web.csv", false);
      CANADA = getMap("data/ca-states.csv", false);
      MEXICO = getMap("data/mx-states.csv", false);
      EUROPE = getMap("data/europe.csv", false);
      ASIA = getMap("data/asia.csv", false);
      WORLD = getMap("data/world.csv", true);
   }
}
