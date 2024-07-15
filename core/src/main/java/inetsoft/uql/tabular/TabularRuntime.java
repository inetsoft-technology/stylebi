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
package inetsoft.uql.tabular;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.function.Supplier;

/**
 * This is the base class for defining a tabular query runtime.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public abstract class TabularRuntime {
   /**
    * Execute a tabular query.
    * @param query a tabular query.
    * @param params parameters for query.
    * @return the result of the query.
    */
   public abstract XTableNode runQuery(TabularQuery query, VariableTable params);

   /**
    * Test if the data source is correct (connection). Throws an exception
    * if the data source can be connected.
    */
   public abstract void testDataSource(TabularDataSource<?> ds,
				       VariableTable params) throws Exception;

   /**
    * Gets data source specific meta data.
    *
    * @param mtype                the meta data type.
    * @param dataSource           the data source.
    * @param connectionParameters the connection parameters.
    *
    * @return the requested meta data. The default implementation returns {@code null}.
    *
    * @throws Exception if the meta data could not be obtained.
    */
   @SuppressWarnings("unused")
   public XNode getMetaData(XNode mtype, TabularDataSource<?> dataSource,
                            VariableTable connectionParameters) throws Exception
   {
      return null;
   }

   protected Object transform(TabularQuery query, String header, Object oldValue) {
      final String columnType = query.getColumnType(header);

      if(columnType == null) {
         return oldValue;
      }

      final String formatType = query.getColumnFormat(header);
      final String formatExtent = query.getColumnFormatExtent(header);

      Format fmt = null;

      if(formatType != null) {
         fmt = TableFormat.getFormat(formatType, formatExtent);
      }

      try {
         return Tool.transform(oldValue, columnType, fmt, false);
      }
      catch(Exception e) {
         LOG.debug("Failed to parse number: {}", oldValue, e);
         return null;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(TabularRuntime.class);

   protected XTableNode handleError(VariableTable vars, Throwable e,
                                    Supplier<XTableNode> supplier)
   {
      boolean isScheduler = false;

      try {
         isScheduler = "true".equals(vars.get("__is_scheduler__"));
      }
      catch(Exception schedEx) {
         LOG.warn("Cannot check scheduler: " + schedEx);
      }

      if(isScheduler) {
         throw new RuntimeException("Query failed", e);
      }

      LOG.error("Query failed", e);
      return supplier.get();
   }
}
