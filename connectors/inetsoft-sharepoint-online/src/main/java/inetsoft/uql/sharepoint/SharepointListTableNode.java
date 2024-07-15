/*
 * inetsoft-sharepoint-online - StyleBI is a business intelligence web application.
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
package inetsoft.uql.sharepoint;

import com.google.gson.JsonElement;
import com.microsoft.graph.models.FieldValueSet;
import com.microsoft.graph.models.ListItem;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.ListItemCollectionPage;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SharepointListTableNode extends XTableNode {
   public SharepointListTableNode(GraphServiceClient client, String siteId, String listId,
                                  XTypeNode[] columnTypes)
   {
      this.client = client;
      this.siteId = siteId;
      this.listId = listId;
      this.columnTypes = columnTypes == null ? new XTypeNode[0] : columnTypes;

      this.table = new XSwappableTable();
      this.creators = Arrays.stream(this.columnTypes)
         .map(this::getColumnCreator)
         .toArray(XTableColumnCreator[]::new);

      this.table.init(creators);
      String[] columnNames = Arrays.stream(this.columnTypes)
         .map(XTypeNode::getName)
         .toArray(String[]::new);
      this.table.addRow(columnNames);
   }

   private void dispose(boolean disposeTable) {
      lock.lock();

      try {
         if(!table.isCompleted()) {
            table.complete();
         }

         if(disposeTable) {
            if(!table.isDisposed()) {
               table.dispose();
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return creators[col];
   }

   @Override
   public XTableColumnCreator[] getColumnCreators() {
      return creators;
   }

   @Override
   public void cancel() {
      dispose(true);
      super.cancel();
   }

   @Override
   public void close() {
      dispose(true);
      super.close();
   }

   @Override
   public int getColCount() {
      return columnTypes.length;
   }

   @Override
   public String getName(int col) {
      return (String) table.getObject(0, col);
   }

   @Override
   public Object getObject(int col) {
      lock.lock();

      try {
         if(currentRow < 0) {
            throw new IllegalStateException(
               "Before the beginning of the table");
         }

         if(currentRow >= getRowCount()) {
            throw new IllegalStateException("Past the end of the table");
         }

         return table.getObject(currentRow, col);
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public Class getType(int col) {
      return Tool.getDataClass(columnTypes[col].getType());
   }

   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   @Override
   public boolean isRewindable() {
      return true;
   }

   @Override
   public boolean next() {
      lock.lock();

      try {
         if(++currentRow < getRowCount()) {
            return true;
         }
         else if(!table.isCompleted()) {
            try {
               return nextPage();
            }
            catch(Exception e) {
               LOG.error("Failed to execute query", e);
               dispose(false);
            }
         }

         return false;
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public boolean rewind() {
      lock.lock();

      try {
         currentRow = 0;
         return true;
      }
      finally {
         lock.unlock();
      }
   }

   private int getRowCount() {
      int nrows = table.getRowCount();

      if(nrows < 0) {
         nrows = (-1 * nrows) - 1;
      }

      return nrows;
   }

   private boolean nextPage() {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

      try {
         return doNextPage();
      }
      finally {
         Thread.currentThread().setContextClassLoader(loader);
      }
   }

   private boolean doNextPage() {
      if(items == null) {
         items = client.sites(siteId).lists(listId).items().buildRequest().expand("fields").get();
      }
      else if(items.getNextPage() == null) {
         items = null;
         table.complete();
         return false;
      }
      else {
         items = items.getNextPage().buildRequest().expand("fields").get();
      }

      if(items.getCurrentPage().isEmpty()) {
         items = null;
         table.complete();
         return false;
      }

      for(ListItem item : items.getCurrentPage()) {
         FieldValueSet fields = item.fields;
         Object[] row = Arrays.stream(columnTypes)
            .map(t -> getValue(fields, t))
            .toArray(Object[]::new);
         table.addRow(row);
      }

      return true;
   }

   private XTableColumnCreator getColumnCreator(XTypeNode type) {
      switch(type.getType()) {
      case XSchema.BOOLEAN:
         return XBooleanColumn.getCreator();
      case XSchema.DATE:
         return XDateColumn.getCreator();
      case XSchema.DOUBLE:
         return XDoubleColumn.getCreator();
      case XSchema.LONG:
         return XLongColumn.getCreator();
      case XSchema.STRING:
         return XStringColumn.getCreator();
      case XSchema.TIME_INSTANT:
         return XTimestampColumn.getCreator();
      default:
         return XObjectColumn.getCreator();
      }
   }

   private Object getValue(FieldValueSet fields, XTypeNode type) {
      JsonElement field = fields.additionalDataManager().get(type.getName());

      if(field == null) {
         return null;
      }

      switch(type.getType()) {
      case XSchema.BOOLEAN:
         return "Yes".equals(field.getAsString());
      case XSchema.DATE:
         return new Date(OffsetDateTime.parse(field.getAsString()).toInstant().toEpochMilli());
      case XSchema.DOUBLE:
         return field.getAsDouble();
      case XSchema.LONG:
         return field.getAsLong();
      case XSchema.TIME_INSTANT:
         return new Timestamp(OffsetDateTime.parse(field.getAsString()).toInstant().toEpochMilli());
      default:
         return field.getAsString();
      }
   }

   private final GraphServiceClient client;
   private final String siteId;
   private final String listId;
   private final XTypeNode[] columnTypes;
   private XSwappableTable table;
   private XTableColumnCreator[] creators;
   private int currentRow = 0;
   private ListItemCollectionPage items;
   private final Lock lock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(SharepointListTableNode.class);
}
