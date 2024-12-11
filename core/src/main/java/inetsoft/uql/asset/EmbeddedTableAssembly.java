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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded table assembly, contains an embedded table.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class EmbeddedTableAssembly extends AbstractTableAssembly {
   /**
    * Constructor.
    */
   public EmbeddedTableAssembly() {
      super();

      setEmbeddedData(new XEmbeddedTable(), false);
   }

   /**
    * Constructor.
    */
   public EmbeddedTableAssembly(Worksheet ws, String name) {
      super(ws, name);

      setEmbeddedData(new XEmbeddedTable(), false);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new EmbeddedTableAssemblyInfo();
   }

   /**
    * Get the embedded data.
    * @return the embedded data.
    */
   public XEmbeddedTable getEmbeddedData() {
      loadOnDemand();
      return xdata;
   }

   /**
    * Set the embedded data.
    * @param data the specified embedded table.
    */
   public void setEmbeddedData(XEmbeddedTable data) {
      setEmbeddedData(data, true);
   }

   /**
    * Sets the embedded data.
    *
    * @param data          the embedded data table.
    * @param updateColumns <tt>true</tt> to update the column selection if it is
    *                      empty.
    */
   protected void setEmbeddedData(XEmbeddedTable data, boolean updateColumns) {
      this.xdata = data;
      this.xdata0 = data.clone();

      if(updateColumns) {
         ColumnSelection columns = getColumnSelection(false);

         if(columns.isEmpty()) {
            for(int i = 0; i < data.getColCount(); i++) {
               String header = AssetUtil.format(XUtil.getHeader(data, i));
               AttributeRef attr = new AttributeRef(null, header);
               ColumnRef column = new ColumnRef(attr);
               column.setDataType(Tool.getDataType(data.getColType(i)));
               columns.addAttribute(column, false);
            }

            setColumnSelection(columns, false);
         }
      }
   }

   public void refreshColumnType(XEmbeddedTable data) {
      ColumnSelection columns = getColumnSelection(false);

      if(columns == null || data == null || columns.getAttributeCount() != data.getColCount()) {
         return;
      }

      for(int i = 0; i < data.getColCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         column.setDataType(Tool.getDataType(data.getColType(i)));
      }
   }

   /**
    * Get the original embedded data.
    * @return the original embedded data.
    */
   public XEmbeddedTable getOriginalEmbeddedData() {
      loadOnDemand();
      return xdata0;
   }

   /**
    * Set the original embedded data.
    */
   protected void setOriginalEmbeddedData(XEmbeddedTable data) {
      this.xdata0 = data;
   }

   /**
    * Get the source of the table assembly.
    * @return the source of the table assembly.
    */
   @Override
   public String getSource() {
      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" strictNull=\"true\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writeEmbeddedData(writer);
   }

   /**
    * Write embedded data.
    * @param writer the specified writer.
    */
   protected void writeEmbeddedData(PrintWriter writer) {
      XEmbeddedTable xdata = getEmbeddedData();
      xdata.reset();

      byte[] arr = null;

      while(xdata.hasNextBlock()) {
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         DataOutputStream out = new DataOutputStream(buf);
         xdata.writeData(out, true);
         arr = buf.toByteArray();
         arr = Base64.getEncoder().encode(arr);
         writer.println("<embeddedDatas><![CDATA[");
         writer.println(new String(arr));
         writer.println("]]></embeddedDatas>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(elem, "strictNull"));
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      parseEmbeddedData(elem);
   }

   /**
    * Parse embedded data.
    * @param elem the specified xml element.
    */
   protected void parseEmbeddedData(Element elem) throws Exception {
      XEmbeddedTable data = new XEmbeddedTable();
      Element tnode = Tool.getChildNodeByTagName(elem, "embeddedData");
      NodeList nodes = Tool.getChildNodesByTagName(elem, "embeddedDatas");

      if(tnode != null) {
         String val = Tool.getValue(tnode);
         removeAllChildren(tnode); // avoid out of memory

         if(val != null) {
            // optimization, delay parsing of data in case the embedded
            // table is never used
            encodedFragments = new XSwappableObjectList<>(String.class);
            encodedFragments.add(val);
            encodedFragments.complete();
            fullFragment = true;
         }
      }
      else if(nodes != null && nodes.getLength() > 0) {
         // delayed parsing
         encodedFragments = new XSwappableObjectList<>(String.class);

         for(int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String val = Tool.getValue(node);
            removeAllChildren(node); // avoid out of memory

            if(val != null) {
               encodedFragments.add(val);
            }
         }

         encodedFragments.complete();
         fullFragment = false;
      }
      else {
         tnode = Tool.getChildNodeByTagName(elem, "xEmbeddedTable");

         if(tnode != null) {
            data.parseXML(tnode);
         }
      }

      setEmbeddedData(data);
   }

   // Load data from cached data
   private synchronized void loadOnDemand() {
      if(encodedFragments != null) {
         XEmbeddedTable data = null;

         if(sharedDatas == null) {
            sharedDatas = new ConcurrentHashMap<>();
         }

         data = sharedDatas.get(isForMetadata());

         if(data != null) {
            data = data.clone();
         }
         else {
            data = new XEmbeddedTable();
            data.setStrictNull(strictNull);

            for(int i = 0; i < encodedFragments.size(); i++) {
               boolean last = metadata || i == encodedFragments.size() - 1;
               String fragment = encodedFragments.get(i);

               if(fragment != null) {
                  parseFragment(data, fragment, !fullFragment, last);
               }
               else {
                  LOG.debug("Failed to load fragment {} / {}, disposed: {}. Assembly name: {}", i,
                            encodedFragments.size(), encodedFragments.isDisposed(i),
                            getAbsoluteName());
               }

               // no need to load full data for metadata
               if(metadata) {
                  break;
               }
            }

            sharedDatas.put(isForMetadata(), data.clone());
         }

         // this list may be shared so we can't dispose it here,
         // just leave it to the finalizer
         //encodedFragments.dispose();
         encodedFragments = null;

         setEmbeddedData(data);
      }
   }

   /**
    * Parse the data and append to table.
    */
   private void parseFragment(XEmbeddedTable data, String fragment, boolean piece, boolean last) {
      byte[] arr = fragment.getBytes();
      arr = Base64.getDecoder().decode(arr);
      ByteArrayInputStream buf = new ByteArrayInputStream(arr);
      DataInputStream inp = new DataInputStream(buf);
      data.parseData(inp, piece, last);
   }

   /**
    * Remove the children of a node.
    */
   private void removeAllChildren(Node node) {
      Tool.freeNode(node);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         EmbeddedTableAssembly table2 = (EmbeddedTableAssembly) super.clone();

         if(xdata != null && !shareData) {
            table2.xdata = xdata.clone();
            table2.xdata0 = xdata0.clone();
         }

         return table2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      int hash = super.getContentCode();
      XEmbeddedTable xdata = getEmbeddedData();

      hash = hash ^ xdata.hashCode();
      return hash;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      if(!super.printKey(writer)) {
         return false;
      }

      return printEmbeddedDataKey(writer);
   }

   /**
    * Print key to identify embedded data.
    */
   protected boolean printEmbeddedDataKey(PrintWriter writer) throws Exception {
      XEmbeddedTable data = this.xdata;

      // don't share embedded data based on contents since it's not
      // accurate and the benefit seems limited
      /*
      if(data == null) {
         data = new XEmbeddedTable();

         if(encodedFragments != null && encodedFragments.size() > 0) {
            parseFragment(data, encodedFragments.get(0), true);
         }
      }
      */

      // include data in case it has been editted
      if(data != null) {
         xdata.printKey(writer);
      }

      writer.print("," + getWorksheet().printKey(writer) + ":" + getName());

      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof EmbeddedTableAssembly)) {
         return false;
      }

      EmbeddedTableAssembly table = (EmbeddedTableAssembly) obj;

      return getEmbeddedData().equals(table.getEmbeddedData());
   }

   /**
    * update properties of table.
    */
   @Override
   public void updateTable(TableAssembly table) {
      super.updateTable(table);

      if(!(table instanceof EmbeddedTableAssembly)) {
         return;
      }

      EmbeddedTableAssembly etable = (EmbeddedTableAssembly) table;

      if(!getEmbeddedData().equals(etable.getEmbeddedData())) {
         setEmbeddedData(etable.getEmbeddedData());
      }
   }

   /**
    * Check if used to sync metadata only.
    */
   public boolean isForMetadata() {
      return metadata;
   }

   /**
    * Set whether used to sync metadata only.
    */
   public void setForMetadata(boolean metadata) {
      this.metadata = metadata;
   }

   private XEmbeddedTable xdata; // runtime data
   private boolean strictNull = true; // for bc
   private transient XEmbeddedTable xdata0; // original data
   // this map is shared across cloned copies and hold the raw data so it doesn't need
   // to be parsed again and again.
   private transient Map<Boolean, XEmbeddedTable> sharedDatas = new ConcurrentHashMap<>();
   protected boolean shareData = false;
   private XSwappableObjectList<String> encodedFragments;
   private boolean fullFragment;
   private boolean metadata = false;

   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedTableAssembly.class);
}
