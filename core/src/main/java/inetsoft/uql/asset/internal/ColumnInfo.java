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
package inetsoft.uql.asset.internal;

import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

/**
 * Column information.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ColumnInfo implements AssetObject, DataSerializable {
   /**
    * Constructor.
    */
   public ColumnInfo() {
      super();
   }

   /**
    * Constructor.
    */
   public ColumnInfo(ColumnRef column, SortRef sref, GroupRef gref,
                     AggregateRef aref, String assembly, String header) {
      this(column, sref, gref, aref, assembly, header, false);
   }

   /**
    * Constructor.
    */
   public ColumnInfo(ColumnRef column, SortRef sref, GroupRef gref,
      AggregateRef aref, String assembly, String header, boolean crosstab)
   {
      this();

      this.assembly = assembly;
      this.header = header;
      this.crosstab = crosstab;
      this.column = column;
      this.sref = sref;
      this.gref = gref;
      this.aref = aref;

      if(XSchema.DATE.equals(column.getDataType())) {
         fmt = AssetUtil.getDateFormat().toPattern();
      }
      else if(XSchema.TIME_INSTANT.equals(column.getDataType())) {
         fmt = AssetUtil.getDateTimeFormat().toPattern();
      }
      else if(XSchema.TIME.equals(column.getDataType())) {
         fmt = AssetUtil.getTimeFormat().toPattern();
      }
      else {
         fmt = "";
      }
   }

   /**
    * Get the name of the column.
    * @return the name of the column.
    */
   public String getName() {
      return column.getName();
   }

   /**
    * Get the header of the column.
    * @return the header of the column.
    */
   public String getHeader() {
      return header;
   }

   /**
    * Get the alias.
    * @return the alias of the column.
    */
   public String getAlias() {
      return column.getAlias();
   }

   /**
    * Set the alias.
    * @param alias the specified alias.
    */
   public void setAlias(String alias) {
      column.setAlias(alias);
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if is visible, <tt>false</tt> otherwise.
    */
   public boolean isVisible() {
      return column.isVisible();
   }

   /**
    * Set the visibility option.
    * @param visible <tt>true</tt> if is visible, <tt>false</tt> otherwise.
    */
   public void setVisible(boolean visible) {
      column.setVisible(visible);
   }

   /**
    * Get the width.
    * @return the width of the column.
    */
   public int getWidth() {
      return column.getWidth();
   }

   /**
    * Set the width.
    * @param width the specified width.
    */
   public void setWidth(int width) {
      column.setWidth(width);
   }

   /**
    * Get the pixel width.
    * @return the width of the column.
    */
   public int getPixelWidth() {
      return column.getPixelWidth();
   }

   /**
    * Set the pixel width.
    * @param width the specified width.
    */
   public void setPixelWidth(int width) {
      column.setPixelWidth(width);
   }

   /**
    * Get the column ref.
    * @return the column ref.
    */
   public ColumnRef getColumnRef() {
      return column != null ? column : new ColumnRef();
   }

   /**
    * Get the table assembly.
    * @return the table assembly.
    */
   public String getAssembly() {
      return assembly;
   }

   /**
    * Get the format.
    * @return the format.
    */
   public String getFormat() {
      return fmt;
   }

   /**
    * Get the sort type.
    * @return the sort type.
    */
   public int getSortType() {
      return sref != null ? sref.getOrder() : XConstants.SORT_NONE;
   }

   /**
    * Set the sort type.
    * @param stype the specified sort type.
    */
   public void setSortType(int stype) {
      if(stype == XConstants.SORT_NONE) {
         sref = null;
      }
      else {
         sref = sref == null ? new SortRef(column) : sref;
         sref.setOrder(stype);
      }
   }

   /**
    * Check if is a group.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isGroup() {
      return gref != null;
   }

   public boolean isTimeSeries() {
      return gref != null && gref.isDate() && gref.isTimeSeries();
   }

   /**
    * Check if is an aggregate.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isAggregate() {
      return aref != null;
   }

   /**
    * Check if is crosstab.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isCrosstab() {
      return crosstab;
   }

   /**
    * Write XML.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<columnInfo ");
      writeAttributes(writer);
      writer.print(">");
      writeContents(writer);
      writer.println("</columnInfo>");
   }

   /**
    * Write DataOutputStream data.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeUTF(assembly);
         dos.writeUTF(header);
         dos.writeBoolean(crosstab);

         dos.writeBoolean(column == null);

         if(column != null) {
            column.writeData(dos);
         }

         dos.writeBoolean(sref == null);

         if(sref != null) {
            sref.writeData(dos);
         }

         dos.writeBoolean(gref == null);

         if(gref != null) {
            gref.writeData(dos);
         }

         dos.writeBoolean(aref == null);

         if(aref != null) {
            aref.writeData(dos);
         }

         dos.writeUTF(fmt);
      }
      catch(IOException e) {
         LOG.error("Failed to serialize data", e);
      }
   }

   /**
    * Write attributes.
    */
   public void writeAttributes(PrintWriter writer) {
      writer.print(" assembly=\"" + assembly + "\"");
      writer.print(" header=\"" + Tool.escape(header) + "\"");
      writer.print(" crosstab=\"" + crosstab + "\"");
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   protected void writeContents(PrintWriter writer) {
      column.writeXML(writer);

      if(sref != null) {
         sref.writeXML(writer);
      }

      if(gref != null) {
         gref.writeXML(writer);
      }

      if(aref != null) {
         aref.writeXML(writer);
      }

      writer.print("<format>");
      writer.print("<![CDATA[" + fmt + "]]>");
      writer.println("</format>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    * @param tag the xml element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   protected void parseAttributes(Element tag) throws Exception {
      assembly = Tool.getAttribute(tag, "assembly");
      header = Tool.getAttribute(tag, "header");
      crosstab = "true".equals(Tool.getAttribute(tag, "crosstab"));
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(tag, "dataRef");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         String cls = Tool.getAttribute(node, "class");

         if("inetsoft.uql.asset.ColumnRef".equals(cls)) {
            column = new ColumnRef();
            column.parseXML(node);
         }
         else if("inetsoft.uql.asset.SortRef".equals(cls)) {
            sref = new SortRef();
            sref.parseXML(node);
         }
         else if("inetsoft.uql.asset.GroupRef".equals(cls)) {
            gref = new GroupRef();
            gref.parseXML(node);
         }
         else if("inetsoft.uql.asset.AggregateRef".equals(cls)) {
            aref = new AggregateRef();
            aref.parseXML(node);
         }
      }

      fmt = Tool.getChildValueByTagName(tag, "format");
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ColumnInfo)) {
         return false;
      }

      ColumnInfo info2 = (ColumnInfo) obj;
      return info2.column.equals(column);
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return getColumnRef().hashCode();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   public String toString() {
      return super.toString() + "[" + assembly + "," + column + "]";
   }

   private String assembly; // table name
   private String header; // column header
   private boolean crosstab; // crosstab
   private ColumnRef column; // column
   private SortRef sref; // sort
   private GroupRef gref; // group
   private String fmt; // format
   private AggregateRef aref; // aggregate

   private static final Logger LOG = LoggerFactory.getLogger(ColumnInfo.class);
}
