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
package inetsoft.uql.rest;

import com.fasterxml.jackson.core.JsonParseException;
import com.jayway.jsonpath.InvalidJsonException;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.util.BaseJsonTable;
import inetsoft.uql.util.ExpandedJsonTable;
import inetsoft.uql.util.HeadersAndRowsJsonTable;
import inetsoft.uql.util.JsonTable;
import inetsoft.util.CoreTool;
import inetsoft.util.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ResourceBundle;

/**
 * A class which holds common methods for classes which implement QueryRunner.
 *
 * @param <T> the tabular query type
 */
public abstract class AbstractQueryRunner<T extends AbstractRestQuery> implements QueryRunner {
   protected AbstractQueryRunner(T query) {
      this.query = query;
   }

   /**
    * @return the appropriate table for the query.
    */
   protected BaseJsonTable getTable() {
      final BaseJsonTable table = createTable();
      table.applyQueryColumnTypes(query);
      table.setMaxRows(query.getMaxRows());
      return table;
   }

   private BaseJsonTable createTable() {
      if(!isEmpty(query.getHeadersPath()) && !isEmpty(query.getRowsPath())) {
         return new HeadersAndRowsJsonTable(query.getHeadersPath(), query.getRowsPath());
      }

      if(query.isExpanded() || (query instanceof EndpointJsonQuery<?> &&
                                ((EndpointJsonQuery<?>) query).isLookupExpanded()))
      {
         String path = query.isExpandTop() ? null : query.getExpandedPath();
         ExpandedJsonTable tbl = new ExpandedJsonTable(path);

         if(!query.isExpanded() || query.isExpandTop()) {
            tbl.setExpandLevels(1);
         }

         return tbl;
      }

      return new JsonTable();
   }

   protected boolean isEmpty(PaginationParameter param) {
      if(param == null) {
         return false;
      }

      return isEmpty(param.getValue());
   }

   protected boolean isEmpty(String s) {
      return s == null || s.isEmpty();
   }

   void setExecutionThread(Thread thread) {
      this.executionThread = thread;
   }

   @SuppressWarnings("unused")
   public void cancel() {
      this.cancelled = true;

      if(executionThread != null) {
         executionThread.interrupt();
      }
   }

   public boolean isCancelled() {
      return cancelled;
   }

   @Override
   public boolean isLiveMode() {
      return livemode;
   }

   public void setLiveMode(boolean livemode) {
      this.livemode = livemode;
   }

   protected void logException(Exception ex) {
      if(ex instanceof InvalidJsonException || ex instanceof JsonParseException) {
         CoreTool.addUserMessage(ResourceBundle.getBundle("inetsoft.uql.rest.json.Bundle",
                                                          ThreadContext.getLocale())
                                 .getString("inetsoft.uql.rest.json.parseException"));
      }
      else {
         CoreTool.addUserMessage("Error executing Rest query: " + ex.getMessage());
      }

      LOG.error("Failed to run query.", ex);
   }

   protected Object selectData(Object input, String path, InputTransformer transformer) {
      Object data;

      if(input instanceof InputStream) {
         data = transformer.transform((InputStream) input, path);
      }
      else {
         data = transformer.transform(input, path);
      }

      return data;
   }

   private volatile boolean cancelled = false;
   private volatile boolean livemode = false;
   private Thread executionThread;

   protected final T query;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
