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
package inetsoft.report.composition.execution;

import inetsoft.uql.XTable;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class tracks table information such as column items and association.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class TableMetaData implements Cloneable {
   /**
    * Name for the min measure value in the mvalues.
    */
   public static final String MEASURE_MIN = "__MIN_VALUE__";
   /**
    * Name for the max measure value in the mvalues.
    */
   public static final String MEASURE_MAX = "__MAX_VALUE__";

   /**
    * Create a table meta data object.
    */
   public TableMetaData(String name) {
      this.name = name;
   }

   /**
    * Get the table name. The table is the table in worksheet.
    */
   public String getName() {
      return name;
   }

   /**
    * Get a distinct column(s) table from the meta data.
    */
   public XTable getColumnTable(String vname, DataRef[] refs) {
      return getColumnTable(vname, getColumns(refs));
   }

   /**
    * Get columns name.
    */
   protected String[] getColumns(DataRef[] refs) {
      String[] columns = new String[refs.length];

      for(int i = 0; i < columns.length; i++) {
         columns[i] = refs[i].getName();
      }

      return columns;
   }

   /**
    * Load data to extract column items and association information.
    */
   public abstract void process(XTable table, String[] columns,
                                List<AggregateRef> aggrs);

   /**
    * Get a distinct column(s) table from the meta data.
    */
   public abstract XTable getColumnTable(String vname, String[] columns);

   /**
    * Get associated values on the named columns from the current selections.
    *
    * @param vname              the viewsheet assembly name.
    * @param selections         column name to Collection (of selected values) mapping.
    * @param refs               target columns.
    * @param measure            target measure
    * @param measureAggregation selection measure aggregation.
    *
    * @return values on the target column that is associated with the column
    * selection.
    */
   public abstract SelectionSet getAssociatedValues(
      String vname,
      Map<String, Collection<Object>> selections,
      DataRef[] refs, String measure,
      SelectionMeasureAggregation measureAggregation) throws Exception;

   /**
    * Get the column type.
    */
   public abstract String getType(String column);

   /**
    * Dispose the table meta data.
    */
   public void dispose() {
      // do nothing
   }

   /**
    * Clone the meta data.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table meta data", ex);
      }

      return null;
   }

   private String name; // table name
   private static final Logger LOG =
      LoggerFactory.getLogger(TableMetaData.class);
}
