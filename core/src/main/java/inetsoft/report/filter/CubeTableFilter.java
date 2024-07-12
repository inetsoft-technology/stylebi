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
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.VSCubeTableLens;
import inetsoft.uql.XDataSource;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.xmla.*;
import inetsoft.util.DefaultComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * CubeTableFilter providers special column Comparator.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class CubeTableFilter extends DefaultTableFilter {
   /**
    * Constructor.
    * @param table the content table.
    * @param query the source this table is from.
    */
   public CubeTableFilter(TableLens table, XMLAQuery query) {
      super(table);
      this.query = query;
   }

   /**
    * Get Comparator of a column.
    * @param ref the specified column.
    * @return the Comparator of that column.
    */
   public Comparator getComparator(DataRef ref) {
      if(query == null || ref == null) {
         return null;
      }

      XDataSource xds = query.getDataSource();
      Dimension dim =
         XMLAUtil.getDimension(xds.getFullName(), query.getCube(), ref);

      if(dim != null && !dim.isOriginalOrder() ||
         XSchema.isDateType(ref.getDataType()))
      {
         return null;
      }

      try {
         DimMember level = XMLAUtil.getLevel(
            xds.getFullName(), query.getCube(), ref);

         if(dim == null || level == null) {
            return null;
         }

         String datasource = XMLAUtil.getSourceName(query);
         String key = XMLAUtil.getCacheKey(datasource,
            XMLAUtil.getLevelUName(dim.getIdentifier(), level.getUniqueName()));
         Comparator comp = map.get(key);

         if(comp == null) {
            XMLATableNode cached =
               (XMLATableNode) XMLAUtil.getCachedResult(key);

            if(cached == null) {
               return null;
            }

            cached.rewind();
            Map<Object, Integer> dataMap = new HashMap<>();
            int i = 0;

            while(cached.next()) {
               Object obj = VSCubeTableLens.getComparableObject(
                  cached.getObject(0), dim.getType());
               dataMap.put(obj, i);

               i++;
            }

            comp = new IndexedComparator(dataMap);
            map.put(key, comp);
         }

         return comp;
      }
      catch(Exception exc) {
         LOG.error("Failed to get comparator for column: " + ref, exc);
         return null;
      }
   }

   /**
    * Set Comparator of a column.
    * @param ref the specified column.
    * @param comp the specified Comparator.
    */
   @SuppressWarnings("UnusedParameters")
   public void setComparator(DataRef ref, Comparator comp) {
      // do nothing
   }

   /**
    * Indexed comparator compares objects by index.
    */
   private class IndexedComparator implements Comparator, Serializable {
      public IndexedComparator(Map<Object, Integer> map) {
         super();
         this.map = map;
      }

      @Override
      public int compare(Object a, Object b) {
         Integer aindex = map.get(a);
         Integer bindex = map.get(b);

         if(aindex == null || bindex == null) {
            return new DefaultComparator().compare(a, b);
         }

         return aindex - bindex;
      }

      private Map<Object, Integer> map;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(CubeTableFilter.class);

   private XMLAQuery query;
   private Map<String, Comparator> map = new HashMap<>();
}
