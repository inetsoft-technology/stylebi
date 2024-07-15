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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.NameMatcher;
import inetsoft.util.CoreTool;

import java.io.*;
import java.util.*;

/**
 * <tt>NameMatcher</tt> that matches map features based on a predefined mapping.
 *
 * <p>A mapping contains one row per map feature. Each row contains the values
 * of the input data columns and the map feature ID. The input data values must
 * be in the same order as specified by the <tt>columns</tt> parameter in the
 * constructors.</p>
 *
 * <p>When stored in a file, the each row must be separated by a new line and
 * each field in the row separated by a pipe (<tt>|</tt>) character.</p>
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class StaticNameMatcher implements NameMatcher {
   /**
    * Creates a new instance of <tt>StaticNameMatcher</tt>.
    *
    * @param columns the data columns that are mapped to map features.
    */
   private StaticNameMatcher(int[] columns) {
      this.columns = columns;
      this.map = new HashMap();
   }

   /**
    * Creates a new instance of <tt>StaticNameMatcher</tt>. The caller is
    * responsible for closing the input stream.
    *
    * @param columns the data columns that are mapped to map features.
    * @param input   the input stream from which the mapping file can be read.
    *
    * @throws IOException if an I/O error occurs.
    */
   public StaticNameMatcher(int[] columns, InputStream input) throws IOException
   {
      this(columns);

      BufferedReader reader =
         new BufferedReader(new InputStreamReader(input, "UTF-8"));
      String line = null;

      while((line = reader.readLine()) != null) {
         addMapping(CoreTool.split(line.trim(), '|'));
      }
   }

   /**
    * Creates a new instance of <tt>StaticNameMatcher</tt>.
    *
    * @param columns the data columns that are mapped to map features.
    * @param mapping the predefined mapping.
    */
   public StaticNameMatcher(int[] columns, String[][] mapping) {
      this(columns);

      for(String[] row : mapping) {
         addMapping(row);
      }
   }

   /**
    * Adds a column tuple to feature ID mapping.
    *
    * @param mapping a row from a mapping file.
    */
   private void addMapping(String[] mapping) {
      if(mapping.length != columns.length + 1) {
         throw new IllegalArgumentException(
            "Number of columns does not match mapping file: " + mapping.length +
            " " + (columns.length + 1));
      }

      Object[] tuple = new Object[columns.length];

      for(int i = 0; i < tuple.length; i++) {
         tuple[i] = mapping[i];
      }

      NameKey key = new NameKey(tuple);
      map.put(key, mapping[columns.length]);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getFeatureId(DataSet input, int row) {
      Object[] tuple = new Object[columns.length];

      for(int i = 0; i < columns.length; i++) {
         tuple[i] = CoreTool.toString(input.getData(columns[i], row));
      }

      return map.get(new NameKey(tuple));
   }

   private final int[] columns;
   private final Map<NameKey, String> map;

   /**
    * Key used to find a matching feature ID from a tuple of columns.
    */
   private static final class NameKey implements Serializable {
      /**
       * Creates a new instance of <tt>GeocodeKey</tt>.
       *
       * @param tuple the tuple of columns that identifies the feature.
       */
      public NameKey(Object[] tuple) {
         this.tuple = tuple;
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof NameKey)) {
            return false;
         }

         return Arrays.equals(this.tuple, ((NameKey) obj).tuple);
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(tuple);
      }

      private final Object[] tuple;
   }
}
