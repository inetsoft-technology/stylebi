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

import inetsoft.util.CoreTool;
import inetsoft.util.DataSpace;
import org.apache.commons.io.IOUtils;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A GeoPoints instance contains map point features.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class GeoPoints implements Serializable {
   /**
    * United States cities.
    */
   public static final GeoPoints US;
   /**
    * Canada cities.
    */
   public static final GeoPoints CANADA;
   /**
    * Mexico cities.
    */
   public static final GeoPoints MEXICO;
   /**
    * European cities.
    */
   public static final GeoPoints EUROPE;
   /**
    * Asian cities.
    */
   public static final GeoPoints ASIA;
   /**
    * Cities for the entire world.
    */
   public static final GeoPoints WORLD;
   /**
    * 5-digit zip code in U.S.
    */
   public static final GeoPoints ZIP;

   /**
    * Creates a new instance of <tt>GeoPoints</tt>.
    */
   protected GeoPoints() {
   }

   /**
    * Loads the point data from a file.
    * @param path the file to load.
    * @throws IOException if an I/O error occurs.
    */
   protected void load(String path) throws IOException {
      InputStream input = getClass().getResourceAsStream(path);

      if(input == null) {
         String file = path;

         if(file.startsWith("/")) {
            file = file.substring(1);
         }

         int index = file.lastIndexOf('/');
         String dir = index < 0 ? null : file.substring(0, index);
         file = index < 0 ? file : file.substring(index + 1);
         DataSpace space = DataSpace.getDataSpace();

         if(space.exists(dir, file)) {
            input = space.getInputStream(dir, file);
         }
         else {
           throw new IOException("Point file not found: " + path);
         }
      }

      BufferedReader reader =
         new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      String line;

      try {
         while((line = reader.readLine()) != null) {
            line = line.trim();

            if(line.length() == 0) {
               continue;
            }

            String[] fields = CoreTool.split(line, '|');

            String id = fields[0];
            double lat = Double.parseDouble(fields[1]);
            double lon = Double.parseDouble(fields[2]);

            this.geodata.put(id, new Point2D.Double(lon, lat));
         }
      }
      finally {
         IOUtils.closeQuietly(reader);
      }
   }

   /**
    * Check if the shape data is loaded.
    */
   protected boolean isLoaded() {
      return geodata.size() > 0;
   }

   /**
    * Make sure the shapes are loaded.
    */
   private void checkLoad() throws Exception {
      if(!isLoaded()) {
         synchronized(this) {
            if(!isLoaded()) {
               try {
                  for(String file : files) {
                     load(file);
                  }
               }
               catch(Exception ex) {
                  throw ex;
               }
            }
         }
      }
   }

   /**
    * Get the unique names of all the point features in this set.
    * @return the feature names.
    */
   public Collection<String> getNames() throws Exception {
      checkLoad();
      return geodata.keySet();
   }

   /**
    * Get the point for the named feature.
    * @param name the feature name.
    * @return the coordinates of the feature or <tt>null</tt> if it does not
    *         exist.
    */
   public Point2D getPoint(String name) throws Exception {
      checkLoad();
      return geodata.get(name);
   }

   /**
    * Get the points with the specified name.
    * @param names the name of the point set.
    * @return the named point set.
    * @throws IOException if an I/O error occurs while loading the point data.
    */
   public static synchronized GeoPoints getPoints(String... names) {
      String key = CoreTool.arrayToString(names);
      GeoPoints gp = points.get(key);

      if(gp == null) {
         gp = new GeoPoints();
         gp.files = names;
         points.put(key, gp);
      }

      return gp;
   }

   private Map<String,Point2D> geodata = new HashMap<>();
   private String[] files;

   private static final Map<String,GeoPoints> points = new HashMap<>();

   static {
      US = getPoints(new String[] {
         "data/us-cities.csv", "data/us-inset-cities.csv"
      });
      CANADA = getPoints("data/ca-cities.csv");
      MEXICO = getPoints("data/mx-cities.csv");
      EUROPE = getPoints("data/europe-cities.csv");
      ASIA = getPoints("data/asia-cities.csv");
      ZIP = getPoints("data/zipcodes-pt.csv");
      WORLD = getPoints(new String[] {
         "data/world-cities.csv", "data/us-cities.csv",
         "data/us-noinset-cities.csv", "data/ca-cities.csv",
         "data/mx-cities.csv", "data/europe-cities.csv",
         "data/asia-cities.csv"
      });
   }
}
