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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.mv.comm.*;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.util.Tool;
import org.roaringbitmap.IntIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.HashSet;
import java.util.List;

/**
 * SubMVQuery, by executing the query, a new XTableBlock will be return.
 * The returned table is a GroupedTableBlock, which should be transported to
 * server, and then merged into one table.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class SubMVQuery implements XTransferable, Cloneable {
   /**
    * Create an instance of SubMVQuery.
    */
   public SubMVQuery() {
      super();
   }

   /**
    * Create an instance of SubMVQuery.
    */
   public SubMVQuery(GroupRef[] groups, boolean[] order,
                     AggregateRef[] aggregates, List<XNode> conds)
   {
      super();

      this.groups = groups;
      this.aggregates = aggregates;
      this.order = order;
      this.conds = conds;
   }

   public SubMVQuery(String s) throws Exception {
      super();
      readXML(s);
   }

   /**
    * Execute the query.
    */
   public SubTableBlock execute(final SubMV mv) throws IOException {
      DefaultTableBlock block = mv.getData();
      boolean donly = aggregates.length == 0;
      BitSet rows = mv.getRows(cond);
      int maxrows = this.maxrows;
      boolean dim1 = donly && groups.length == 1; // a single group and dim only

      block.init(this);

      if(maxrows <= 0) {
         maxrows = Integer.MAX_VALUE;
      }

      if(timezoneOffset != null) {
         MVDateColumnWrapper.setServerTimezoneOffset(timezoneOffset);
      }

      SubTableBlock group = detail ? new DetailTableBlock() : new GroupedTableBlock(order);

      group.init(this);

      if(rows == null && cond == null) {
         final int rcnt = block.getRowCount();

         if(dim1) {
            for(int r = 0; r < rcnt && !cancelled; r++) {
               group.addDRow(block.getDRow1(r));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
         else if(donly) {
            for(int r = 0; r < rcnt && !cancelled; r++) {
               group.addDRow(block.getDRow(r));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
         else {
            for(int r = 0; r < rcnt && !cancelled; r++) {
               group.addRow(block.getRow(r));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
      }
      else if(rows != null) {
         IntIterator iter = rows.intIterator();

         if(dim1) {
            while(iter.hasNext() && !cancelled) {
               group.addDRow(block.getDRow1(iter.next()));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
         else if(donly) {
            while(iter.hasNext() && !cancelled) {
               group.addDRow(block.getDRow(iter.next()));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
         else {
            while(iter.hasNext() && !cancelled) {
               group.addRow(block.getRow(iter.next()));

               if(checkMaxrows(group.getRowCount(), maxrows)) {
                  break;
               }
            }
         }
      }

      group.complete();
      return group;
   }

   // check if rows exceeded
   private boolean checkMaxrows(int rowcnt, int maxrows) {
      if(rowcnt < 0) {
         rowcnt = -rowcnt + 1;
      }

      return rowcnt >= maxrows;
   }

   /**
    * Get all aggregate column names of default table block.
    */
   public String[] getAggregateColumns() {
      if(cols == null) {
         cols = getAggregates(aggregates);
      }

      return cols;
   }

   /**
    * Get the aggregate definision.
    * @param idx aggregate index (not column index).
    */
   public AggregateRef getAggregate(int idx) {
      return aggregates[idx];
   }

   /**
    * Get aggregate names.
    */
   public static String[] getAggregates(AggregateRef[] aggregates) {
      HashSet<String> set = new HashSet<>();

      for(AggregateRef aref : aggregates) {
         DataRef ref = GroupedTableBlock.getDataRef(aref.getDataRef());
         set.add(MVDef.getMVHeader(ref));

         if(aref.getFormula() != null && aref.getFormula().isTwoColumns()) {
            ref = GroupedTableBlock.getDataRef(aref.getSecondaryColumn());

            if(ref != null) {
               set.add(MVDef.getMVHeader(ref));
            }
         }
      }

      String[] tcols = new String[set.size()];
      set.toArray(tcols);
      return tcols;
   }

   /**
    * Set wether is detail query.
    */
   public void setDetail(boolean detail) {
      this.detail = detail;
   }

   /**
    * Check if is detail query.
    */
   public boolean isDetail() {
      return detail;
   }

   /**
    * Get the block index of the query.
    */
   public int getBlockIndex() {
      return blockIndex;
   }

   /**
    * Set the block index of the query.
    */
   public void setBlockIndex(int blockIndex) {
      this.blockIndex = blockIndex;

      if(conds != null) {
         cond = (XFilterNode) conds.get(blockIndex);
      }
   }

   /**
    * Cancel the task.
    */
   public void cancel() {
      cancelled = true;
   }

   /**
    * Check if the task is cancelled.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Set the max rows for result data.
    */
   public void setMaxRows(int max) {
      this.maxrows = max;
   }

   /**
    * Get the max rows for result data.
    */
   public int getMaxRows() {
      return maxrows;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SubMVQuery subquery = (SubMVQuery) super.clone();
         subquery.groups = groups;
         subquery.aggregates = aggregates;
         subquery.detail = detail;
         subquery.order = order;
         subquery.cols = cols;
         return subquery;
      }
      catch(Exception ex) {
         return null;
      }
   }

   private void readXML(String s) throws Exception {
      Document doc = Tool.parseXML(new StringReader(s));
      Element root = doc.getDocumentElement();
      String str;
      detail = "true".equals(Tool.getAttribute(root, "detail"));
      blockIndex = Integer.parseInt(Tool.getAttribute(root, "blockIndex"));
      maxrows = Integer.parseInt(Tool.getAttribute(root, "maxrows"));

      if((str = Tool.getAttribute(root, "timezoneOffset")) != null) {
         timezoneOffset = Integer.valueOf(str);
      }

      Element node = Tool.getChildNodeByTagName(root, "gs");
      NodeList list = Tool.getChildNodesByTagName(node, "dataRef");
      int dataRefCount = list.getLength();
      groups = new GroupRef[dataRefCount];

      for(int i = 0; i < dataRefCount; i++) {
         GroupRef ref = new GroupRef();
         ref.parseXML((Element) list.item(i));
         groups[i] = ref;
      }

      order = new boolean[dataRefCount];
      node = Tool.getChildNodeByTagName(root, "o");
      String oval = Tool.getValue(node);
      String[] val = oval == null ? new String[0] : oval.split(",");

      for(int i = 0; i < dataRefCount; i++) {
         order[i] = "1".equals(val[i]);
      }

      node = Tool.getChildNodeByTagName(root, "as");
      list = Tool.getChildNodesByTagName(node, "dataRef");
      aggregates = new AggregateRef[list.getLength()];

      for(int i = 0; i < aggregates.length; i++) {
         aggregates[i] = new AggregateRef();
         aggregates[i].parseXML((Element) list.item(i));
      }

      node = Tool.getChildNodeByTagName(root, XSet.XML_TAG);

      if(node != null) {
         cond = new XSet();
         cond.parseXML(node);
      }

      if(cond == null) {
         node = Tool.getChildNodeByTagName(root, XBinaryCondition.XML_TAG);

         if(node != null) {
            cond = new XBinaryCondition();
            cond.parseXML(node);
         }
      }
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      final int len = buf.readInt();
      final byte[] bytes = new byte[len];

      for(int i = 0; i < len; i++) {
         bytes[i] = buf.readByte();
      }

      String str = new String(bytes, "UTF-8");

      try {
         readXML(str);
      }
      catch(Exception ex) {
         throw new IOException(ex.getMessage());
      }
   }

   private void writeXML(OutputStream output) throws IOException {
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"));
      writer.format("<s detail=\"%b\" blockIndex=\"%d\" maxrows=\"%d\"",
                    detail, blockIndex, maxrows);

      if(timezoneOffset != null) {
         writer.format(" \"timezoneOffset=\"%d\"", timezoneOffset);
      }

      writer.println("><gs>");

      for(GroupRef group : groups) {
         group.writeXML(writer);
      }

      writer.println("</gs>");
      writer.print("<o>");

      for(int i = 0; i < order.length; i++) {
         if(i > 0) {
            writer.print(',');
         }

         writer.print(order[i] ? 1 : 0);
      }

      writer.println("</o>");
      writer.println("<as>");

      for(AggregateRef ref : aggregates) {
         ref.writeXML(writer);
      }

      writer.println("</as>");

      if(cond != null) {
         cond.writeXML(writer);
      }

      writer.println("</s>");
      writer.flush();
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      writeXML(out);
      byte[] res = out.toByteArray();
      buf.writeInt(res.length);

      for(byte b : res) {
         buf.writeByte(b);
      }
   }

   public String toString() {
      String s = null;

      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         writeXML(out);
         s = new String(out.toByteArray(), "UTF-8");
      }
      catch(Exception ex) {
         LOG.warn("Error encoding object", ex);
      }

      return s;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SubMVQuery.class);
   GroupRef[] groups;
   AggregateRef[] aggregates;
   XFilterNode cond;
   boolean[] order;
   private boolean detail;
   private boolean cancelled;
   private int blockIndex = -1;
   private int maxrows = 0;
   private Integer timezoneOffset; // timezone offset of server
   private transient String[] cols;
   private transient List<XNode> conds;
}
