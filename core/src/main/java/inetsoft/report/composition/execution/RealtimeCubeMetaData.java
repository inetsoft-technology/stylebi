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
package inetsoft.report.composition.execution;

import inetsoft.uql.XTable;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import inetsoft.uql.xmla.MemberObject;

import java.util.*;

/**
 * This class tracks cube information such as column items and association.
 *
 * @version 11.0
 * @author InetSoft Technology Corp
 */
public class RealtimeCubeMetaData extends TableMetaData {
   public RealtimeCubeMetaData(String name) {
      super(name);
      tdata = new RealtimeTableMetaData(name);
   }

   /**
    * Constructor.
    */
   public RealtimeCubeMetaData(String name, XTable table, String[] columns,
                               List<AggregateRef> aggrs)
   {
      super(name);

      tdata = new RealtimeTableMetaData(name);
      tdata.process(table, columns, aggrs);
   }

   /**
    * Load data to extract column items and association information.
    */
   @Override
   public void process(XTable table, String[] columns, List<AggregateRef> aggrs)
   {
      tdata.process(table, columns, aggrs);
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   @Override
   public XTable getColumnTable(String vname, String[] columns) {
      return tdata.getColumnTable(vname, columns);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public SelectionSet getAssociatedValues(String vname, Map<String, Collection<Object>> selections,
                                           DataRef[] refs, String measure,
                                           SelectionMeasureAggregation measureAggregation)
      throws Exception
   {
      SelectionSet set =
         tdata.getAssociatedValues(vname, selections, refs, measure, measureAggregation);

      if(refs.length == 0) {
         return set;
      }

      Set<String> selected = new HashSet<>();

      for(String col : selections.keySet()) {
         String hier = getHierarchy(col);
         selected.add(hier);
      }

      String column = refs[0].getName();
      String hier = getHierarchy(column);

      if(selections.size() == 0 || selected.contains(hier)) {
         return set;
      }

      selections = new HashMap<>();
      return tdata.getAssociatedValues(vname, selections, refs, measure, measureAggregation);
   }

   /**
    * Get the column type.
    */
   @Override
   public String getType(String column) {
      return tdata.getType(column);
   }

   /**
    * Dispose the table meta data.
    */
   @Override
   public void dispose() {
      if(tdata != null) {
         tdata.dispose();
      }
   }

   /**
    * Get the column hierarchy.
    */
   private synchronized String getHierarchy(String column) {
      ColumnMetaData col = tdata.getColumnMetaData(column);

      if(col == null || col.getValueCount() == 0) {
         return null;
      }

      Object obj = col.getValue(0);

      return obj instanceof MemberObject ?
         ((MemberObject) obj).getHierarchy() : null;
   }

   private RealtimeTableMetaData tdata;
}
