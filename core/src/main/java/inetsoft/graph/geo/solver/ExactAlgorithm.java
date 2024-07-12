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
package inetsoft.graph.geo.solver;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.geo.NameMatcher;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * <tt>MatchingAlgorithm</tt> that only returns exact matches.
 *
 * @author InetSoft Technology
 * @since  10.1
 */
public class ExactAlgorithm extends AbstractMatchingAlgorithm {
   /**
    * Creates a new instance of <tt>ExactAlgorithm</tt>.
    */
   public ExactAlgorithm() {
   }

   /**
    * {@inheritDoc}
    */
   public String findBestMatchID(DataSet source, int[] sourceColumns,
      NameTable[] names, int[] nameColumns, int row, NameMatcher[] matchers)
   {
      List<String> matches =
         findMatchIDs(source, sourceColumns, names, nameColumns, row, matchers);
      return matches.isEmpty() ? null : matches.get(0);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<String> findMatchIDs(DataSet source, int[] sourceColumns,
                                    NameTable names, int[] nameColumns, int row)
   {
      return findMatchIDs(source, sourceColumns, new NameTable[] {names},
         nameColumns, row, null);
   }

   /**
    * {@inheritDoc}
    */
   public List<String> findMatchIDs(DataSet source, int[] sourceColumns,
      NameTable[] names, int[] nameColumns, int row, NameMatcher[] matchers)
   {
      Object[] data = new Object[sourceColumns.length];

      for(int i = 0; i < data.length; i++) {
         data[i] = source.getData(sourceColumns[i], row);

         NameMatcher matcher = matchers == null ? null : matchers[i];

         if(matcher != null) {
            String id = matcher.getFeatureId(source, row);

            if(id != null && names[i] != null) {
               data[i] = names[i].getName(id);
            }
         }
      }

      // if name table is not defined by a name column is specified in mapdata,
      // we assume it's a one-to-one mapping (e.g. zipcode)
      if(names == null || names[0] == null) {
         List<String> list = new ArrayList<>();

         for(Object val : data) {
            if(val != null) {
               list.add(val.toString());
            }
         }

         return list;
      }

      Object key = getDataKey(names[0], data, nameColumns);
      List<String> matches = cache.get(key);

      if(matches == null) {
         Visitor visitor = new Visitor(data, nameColumns);
         names[0].accept(visitor);
         cache.put(key, matches = visitor.matches);
      }

      return matches;
   }

   private Object getDataKey(NameTable names, Object[] data, int[] cols) {
      return names.getName() + ":" + Arrays.toString(data) + Arrays.toString(cols);
   }

   static final class Visitor implements NameTable.NameVisitor {
      public Visitor(Object[] data, int[] idxs) {
         this.data = Arrays.stream(data)
            .map(a -> a instanceof String ? ((String) a).toLowerCase() : a).toArray();
         this.idxs = idxs;
      }

      @Override
      public void visit(String id, String[] columns) {
         boolean equal = true;

         for(int i = 0; i < data.length; i++) {
            if(idxs[i] >= columns.length || !equals(data[i], columns[idxs[i]])) {
               equal = false;
               break;
            }
         }

         if(equal) {
            addMatch(id);
         }
      }

      @Override
      public String getNameForId() {
         return idxs.length == 1 && idxs[0] == 0 ? data[0] + "" : null;
      }

      @Override
      public void addMatch(String id) {
         matches.add(id);
      }

      private boolean equals(Object o1, Object o2) {
         if(o1 == null) {
            return o2 == null;
         }
         else {
            return o1.equals(o2);
         }
      }

      private final Object[] data;
      private final int[] idxs;
      private final List<String> matches = new ArrayList<>();
   }

   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      cache = new WeakHashMap<>();
   }

   private transient Map<Object, List<String>> cache = new WeakHashMap<>();
}
