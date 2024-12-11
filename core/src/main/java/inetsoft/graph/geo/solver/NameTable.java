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
package inetsoft.graph.geo.solver;

import inetsoft.util.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A table of names used to map source data to map features.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public final class NameTable implements Serializable {
   private static final Logger LOG = LoggerFactory.getLogger(NameTable.class);

   /**
    * Table of country names.
    */
   public static final NameTable COUNTRIES = new NameTable(
      "Countries", -1, "/inetsoft/graph/geo/data/countries.names.csv", 0);

   /**
    * Table of state names.
    */
   public static final NameTable STATES = new NameTable(
      "States", -1, "/inetsoft/graph/geo/data/states.names.csv", 0, 4);

   /**
    * Table of city names.
    */
   public static final NameTable CITIES = new NameTable(
      "Cities", 4, "/inetsoft/graph/geo/data/cities.names.csv", 0, 1, 2);

   /**
    * Creates a new instance of <tt>NameTable</tt>.
    * @param name         the name of the table.
    * @param capitalColumn
    * @param file         the CSV file containing the name data.
    * @param labelColumns the columns used to build labels for the map features.
    */
   public NameTable(String name, int capitalColumn, String file, int... labelColumns) {
      this.name = name;
      this.labelColumns = labelColumns;
      this.capitalColumn = capitalColumn;
      this.file = file;
      this.data = new HashMap<>();

      // find column names and column count
      try {
         InputStream input = openFile(file);

         if(input == null) {
            throw new FileNotFoundException("Map file not found:" + file);
         }

         try(BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8)))
         {
            String line;

            if((line = reader.readLine()) != null) {
               line = line.trim();

               columnNames = line.split("\\|", -1);
               columnCount = columnNames.length;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize map name table", ex);
      }
   }

   /**
    * Gets the name of this table.
    *
    * @return the table name.
    */
   public final String getName() {
      return name;
   }

   /**
    * Gets the number of name columns in this table.
    *
    * @return the column count.
    */
   public final int getColumnCount() {
      return columnCount;
   }

   /**
    * Gets the names of the columns in this table.
    *
    * @return the column names.
    */
   public final String[] getColumnNames() {
      return columnNames;
   }

   /**
    * Gets a display label for the specified map feature.
    *
    * @param id the ID of the map feature.
    *
    * @return the display label.
    */
   public final String getLabel(String id) {
      load();

      StringBuilder label = new StringBuilder();
      String[] names = data.get(id);

      if(names != null) {
         for(int i = 0; i < labelColumns.length; i++) {
            if(label.length() > 0) {
               label.append(", ");
            }

            label.append(names[labelColumns[i]]);
         }
      }

      return label.toString();
   }

   /**
    * Gets a display id for the specified map feature.
    *
    * @param label the label of the map feature.
    *
    * @return the display id.
    */
   public String getId(String label) {
      String id = null;

      if(label == null || label.isEmpty()) {
         return id;
      }

      for(String tempId : data.keySet()) {
         if(id != null) {
            break;
         }

         String[] names = data.get(tempId);

         for(String name : names) {
            if(label.equals(name)) {
               id = tempId;
               break;
            }
         }
      }

      return id;
   }

   /**
    * Gets name for the specified map feature.
    *
    * @param id the ID of the map feature.
    * @return the feature name.
    */
   public String getName(String id) {
      load();

      String[] names = data.get(id);
      return names == null ? null : names[0];
   }

   /**
    * Get the names of all shapes in this map.
    * @return a collection of shape names.
    */
   public Collection<String> getNames() {
      load();
      return data.keySet();
   }

   /**
    * Visits each map feature in this table.
    *
    * @param visitor the visitor to accept.
    */
   public final void accept(NameVisitor visitor) {
      load();

      CachedData cdata = cachedData.get();
      List<IdValues> entries;
      Map<String, String> name2Id;

      if(cdata == null) {
         entries = data.entrySet().stream()
            .map(e -> new IdValues(e)).collect(Collectors.toList());
         entries.sort(getEntryComparator());

         name2Id = new HashMap<>();

         for(IdValues entry : entries) {
            if(!name2Id.containsKey(entry.values[0])) {
               name2Id.put(entry.values[0], entry.id);
            }
         }

         cachedData = new SoftReference<>(new CachedData(entries, name2Id));
      }
      else {
         entries = cdata.sortedData;
         name2Id = cdata.name2Id;
      }

      if(visitor.getNameForId() != null) {
         visitor.addMatch(name2Id.get(visitor.getNameForId()));
         return;
      }

      for(IdValues entry : entries) {
         visitor.visit(entry.id, entry.values);
      }
   }

   // sort capitals to top
   private Comparator<IdValues> getEntryComparator() {
      return (a, b) -> {
         String[] names1 = a.values;
         String[] names2 = b.values;
         int rc = names1[0].compareTo(names2[0]);

         if(rc == 0) {
            String key1 = Arrays.toString(names1);
            String key2 = Arrays.toString(names2);
            Set<String>[] capitals = new Set[] { primary, admin, minor };

            for(Set<String> capital : capitals) {
               boolean primary1 = capital.contains(key1);
               boolean primary2 = capital.contains(key2);

               if(primary1 != primary2) {
                  return primary1 ? -1 : 1;
               }
            }
         }

         return rc;
      };
   }

   /**
    * Loads the name data from the CSV file.
    */
   private synchronized void load() {
      if(data.isEmpty()) {
         try {
            InputStream input = openFile(file);

            try(BufferedReader reader = new BufferedReader(
               new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
               String line;

               reader.readLine(); // skip header

               while((line = reader.readLine()) != null) {
                  line = line.trim();

                  if(line.length() == 0) {
                     continue;
                  }

                  String[] fields = line.split("\\|", -1);
                  String[] names = new String[columnCount];
                  System.arraycopy(fields, 0, names, 0, columnCount);
                  String id = fields[columnCount];

                  data.put(id, names);

                  if(capitalColumn >= 0) {
                     switch(fields[capitalColumn]) {
                     case "primary":
                        primary.add(Arrays.toString(names));
                        break;
                     case "admin":
                        admin.add(Arrays.toString(names));
                        break;
                     case "minor":
                        minor.add(Arrays.toString(names));
                        break;
                     case "":
                        break;
                     default:
                        LOG.warn("Unknown capital type: " + fields[capitalColumn]);
                     }
                  }
               }
            }
         }
         catch(IOException exc) {
            LOG.error("Failed to load name table from " + file, exc);
         }
      }
   }

   private InputStream openFile(String path) throws IOException {
      InputStream input = getClass().getResourceAsStream(file);

      if(input == null) {
         String file = path;

         if(file.startsWith("/")) {
            file = file.substring(1);
         }

         DataSpace space = DataSpace.getDataSpace();
         int index = file.lastIndexOf('/');
         String dir = index < 0 ? null : file.substring(0, index);
         file = index < 0 ? file : file.substring(index + 1);

         if(space.exists(dir, file)) {
            input = space.getInputStream(dir, file);
         }
      }

      return input;
   }

   private static class IdValues implements Serializable {
      IdValues(Map.Entry<String, String[]> entry) {
         this.id = entry.getKey();
         this.values = Arrays.stream(entry.getValue()).map(s -> s != null ? s.toLowerCase() : s)
            .toArray(String[]::new);
      }

      private String id;
      private String[] values;
   }

   private static class CachedData {
      List<IdValues> sortedData; // optimization
      Map<String, String> name2Id; // optimization

      public CachedData(List<IdValues> sortedData, Map<String, String> name2Id) {
         this.sortedData = sortedData;
         this.name2Id = name2Id;
      }
   }

   private final String name;
   private int columnCount = 0;
   private String[] columnNames;
   private final int[] labelColumns;
   private final String file;
   private final Map<String, String[]> data;
   private final int capitalColumn;
   private final Set<String> primary = new HashSet<>(); // national capital
   private final Set<String> admin = new HashSet<>(); // state/province capital
   private final Set<String> minor = new HashSet<>(); // state/province capital
   private SoftReference<CachedData> cachedData = new SoftReference<>(null);

   /**
    * Interface for classes that visit each map feature in a <tt>NameTable</tt>.
    *
    * @author InetSoft Technology
    * @since  10.1
    */
   public static interface NameVisitor {
      /**
       * Visits a map feature.
       *
       * @param id      the ID of the map feature.
       * @param columns the name columns for the map feature.
       */
      void visit(String id, String[] columns);

      /**
       * Add id to matched list.
       */
      void addMatch(String id);

      /**
       * Get the name for exact name match if this is matching exact name (from name to id).
       */
      default String getNameForId() {
         return null;
      }
   }
}
