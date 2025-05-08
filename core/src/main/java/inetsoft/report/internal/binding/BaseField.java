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
package inetsoft.report.internal.binding;

import inetsoft.report.StyleConstants;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.*;
import java.security.Principal;

/**
 * Base field represents a column bind to the table.
 *
 * @version 6.0, 9/30/2003
 * @author mikec
 */
public class BaseField extends AttributeRef implements Field, SourceField {
   /**
    * Create a blank base field.
    */
   public BaseField() {
      super();
   }

   /**
    * Create a base field with attribute.
    *
    * @param attr the specified attribute
    */
   public BaseField(String attr) {
      super(null, attr);
   }

   /**
    * Find the base name of column when it was defined date grouping.
    */
   private static String[] findBaseColumnName(String field) {
      if(field == null || field.length() == 0) {
         return new String[0];
      }

      int lastIndex = field.lastIndexOf('.');

      if(lastIndex < 0) {
         return new String[0];
      }

      String suf = field.substring(lastIndex + 1);
      boolean digit = suf.length() > 0;

      for(int j = 0; j < suf.length(); j++) {
         if(!Character.isDigit(suf.charAt(j))) {
            digit = false;
            break;
         }
      }

      return digit ? new String[] {field.substring(0, lastIndex),
         field.substring(lastIndex + 1)} : new String[0];
   }

   /**
    * Get self.
    */
   @Override
   public Field getField() {
      return this;
   }

   /**
    * Create a base field with entity and attribute.
    *
    * @param entity the specified entity
    * @param attr the specified attribute
    */
   public BaseField(String entity, String attr) {
      super(entity, attr);
   }

   /**
    * Create a base field with entity and attribute.
    *
    * @param entity the specified entity
    * @param attr the specified attribute
    * @param edesc the specified entity's description
    * @param adesc the specified attribute's description
    */
   public BaseField(String entity, String attr, String edesc, String adesc) {
      super(entity, attr);
   }

   /**
    * Create a base field from a attribute ref.
    *
    * @param ref the specified attribute ref
    */
   public BaseField(DataRef ref) {
      super(ref.getEntity(), ref.getAttribute());

      if(ref instanceof BaseField) {
         BaseField field = (BaseField) ref;

         this.source = field.source;
         this.dtype = field.dtype;
      }
   }

   /**
    * Set the visibility of the field.
    *
    * @param visible true if is visible, false otherwise
    */
   @Override
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check the visibility of the field.
    *
    * @return true if is visible, false otherwise
    */
   @Override
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the description of the entity.
    *
    * @param desc the specified entity's description.
    */
   public void setEntityDescription(String desc) {
      this.entityDesc = desc;
   }

   /**
    * Get the description of the entity.
    *
    * @return the entity's description.
    */
   public String getEntityDescription() {
      return entityDesc == null ? "" : entityDesc;
   }

   /**
    * Set the description of the attribute.
    *
    * @param desc the specified attribute's description.
    */
   public void setDescription(String desc) {
      this.attributeDesc = desc;
   }

   /**
    * Get the description of the attribute.
    *
    * @return the attribute's description.
    */
   public String getDescription() {
      return attributeDesc == null ? "" : attributeDesc;
   }

   /**
    * Set the sorting order of the field.
    *
    * @param order the specified sorting order defined in StyleConstants
    */
   @Override
   public void setOrder(int order) {
      if(order < Byte.MIN_VALUE || order > Byte.MAX_VALUE) {
         throw new IllegalArgumentException("Value out of byte range: " + order);
      }

      this.order = (byte) order;
   }

   /**
    * Get the sorting order of the field.
    *
    * @return the sorting order defined in StyleConstants
    */
   @Override
   public int getOrder() {
      return order;
   }

   /**
    * Set the data type of the field.
    *
    * @param type the specified data type defined in XSchema
    */
   @Override
   public void setDataType(String type) {
      this.dtype = type;
   }

   /**
    * Get the data type of the field.
    *
    * @return the data type defined in XSchema
    */
   @Override
   public String getDataType() {
      return dtype;
   }

   /**
    * Get the type node presentation of this field.
    *
    * @return the type node
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Set the source of this field. It could be the name of the query or data
    * model.
    */
   @Override
   public void setSource(String source) {
      this.source = source;
   }

   /**
    * Get the source of this field.
    */
   @Override
   public String getSource() {
      return getSource(false);
   }

   /**
    * Get source.
    * @param force, to get the really source any way, because from 11.0, we
    * not support join source, so the source is really useless except for cal
    * table.
    */
   @Override
   public String getSource(boolean force) {
      return force ? source : null;
   }

   /**
    * Set the prefix of source.
    */
   @Override
   public void setSourcePrefix(String prefix) {
      this.prefix = prefix;
   }

   /**
    * Get the prefix of the source.
    */
   @Override
   public String getSourcePrefix() {
      return prefix;
   }

   /**
    * Set the type of this source.
    */
   @Override
   public void setSourceType(int type) {
      this.type = type;
   }

   /**
    * Get the type of the source.
    */
   @Override
   public int getSourceType() {
      return type;
   }

   /**
    * Get the source descrition of this field.
    */
   public String getSourceDescription() {
      return getSourceDescription(false);
   }

   public String getSourceDescription(boolean force) {
      if(!force) {
         return null;
      }

      try {
         AssetEntry entry = AssetEntry.createAssetEntry(getSource(force));

         return entry.getDescription();
      }
      catch(Exception ex) {
      }

      return getSource(force);
   }

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isDate() {
      XTypeNode node = getTypeNode();
      return node != null && node.isDate();
   }

   /**
    * Check if is empty.
    *
    * @return true if is, false otherwise
    */
   @Override
   public boolean isEmpty() {
      return getEntity() == null &&
         (getAttribute() == null || getAttribute().length() == 0);
   }

   /**
    * Set whether the field is processed in query generator.
    */
   @Override
   public void setProcessed(boolean processed) {
      this.processed = processed;
   }

   /**
    * Check if the field is processed in query generator.
    */
   @Override
   public boolean isProcessed() {
      return processed;
   }

   /**
    * Get the string presentation of the field.
    *
    * @return the string presentation
    */
   public String toString() {
      // @by larryl, if used in multi-source join, show the query name as part
      // of the name
      StringBuilder buf = new StringBuilder();

      if(getSource() != null) {
         buf.append(getSourceDescription());
         buf.append('.');
      }

      if(getEntity() != null) {
         buf.append(getEntity());
         buf.append('.');
      }

      buf.append(getAttribute());
      return buf.toString();
   }

   /**
    * Set view.
    */
   public void setView(String view) {
      this.view = view;
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field
    */
   @Override
   public String toView() {
      // @by davyc, used for chart, to make sure the "Sum of xxx" will localize
      // correct, see GraphUtil.createViewColumnSelection()
      if(view != null) {
         return view;
      }

      // @by larryl, if used in multi-source join, show the query name as part
      // of the name
      StringBuilder buf = new StringBuilder();

      if(getSource() != null) {
         buf.append(getSourceDescription());
         buf.append('.');
      }

      boolean model = getEntity() != null && getEntity().length() > 0;
      Principal user = ThreadContext.getContextPrincipal();
      Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
      String column = getAttribute();

      if(model) {
         buf.append(catalog.getString(getEntity()));
         buf.append('.');
         buf.append(catalog.getString(getAttribute()));
      }
      else if(column != null) {
         String[] origColumn = findBaseColumnName(column);
         column = origColumn.length == 2 ? origColumn[0] : column;
         String digit = origColumn.length == 2 ? origColumn[1] : null;
         int idx = column.lastIndexOf(".");

         if(idx > 0) {
            buf.append(catalog.getString(column.substring(0, idx)));
            buf.append('.');
            buf.append(catalog.getString(column.substring(idx + 1)));
         }
         else {
            buf.append(catalog.getString(column));
         }

         if(digit != null) {
            buf.append('.');
            buf.append(digit);
         }
      }

      return buf.toString();
   }

   /**
    * Write the contents of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
   }

   /**
    * Write the attributes of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print("description=\"" + Tool.escape(getDescription()) + "\" ");
      writer.print("visible=\"" + isVisible() + "\" ");
      writer.print("groupField=\"" + isGroupField() + "\" ");
      writer.print("dataType=\"" + getDataType() + "\" ");
      writer.print("order=\"" + getOrder() + "\" ");

      if(view != null) {
         writer.print("view=\"" + Tool.escape(view) + "\" ");
      }

      if(source != null) {
         writer.print("source=\"" + Tool.escape(source) + "\" ");
         writer.print("prefix=\"" + Tool.escape(prefix) + "\" ");
         writer.print("type=\"" + (type) + "\" ");
      }
   }

   protected void writeDataType(PrintWriter writer) {
      //do nothing
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      super.writeAttributes2(dos);

      try {
         dos.writeBoolean(isVisible());
         dos.writeBoolean(isGroupField());
         dos.writeUTF(getDataType());
         dos.writeInt(getOrder());

         dos.writeBoolean(source == null);

         if(source != null) {
            dos.writeUTF(source);
            dos.writeUTF(prefix);
            dos.writeInt(type);
         }
      }
      catch (IOException e) {
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String str;

      if((str = Tool.getAttribute(tag, "description")) != null) {
         setDescription(str);
      }

      if((str = Tool.getAttribute(tag, "view")) != null) {
         setView(str);
      }

      if((str = Tool.getAttribute(tag, "visible")) != null) {
         setVisible(str.equals("true"));
      }

      if((str = Tool.getAttribute(tag, "groupField")) != null) {
         setGroupField(str.equals("true"));
      }

      if((str = Tool.getAttribute(tag, "dataType")) != null) {
         setDataType(str);
      }

      if((str = Tool.getAttribute(tag, "order")) != null) {
         setOrder(Integer.parseInt(str));
      }

      if(Tool.getAttribute(tag, "source") != null) {
         source = Tool.getAttribute(tag, "source");
         prefix = Tool.getAttribute(tag, "prefix");

         if(Tool.getAttribute(tag, "type") != null) {
            type = Integer.parseInt(Tool.getAttribute(tag, "type"));
         }
      }
   }

   protected void parseDataType(Element tag) {
      // do nothing
   }

   /**
    * Compare two base fields.
    */
   public boolean equals(Object obj) {
      return equals(obj, true);
   }

   /**
    * Compare two base fields.
    */
   @Override
   public boolean equals(Object obj, boolean total) {
      if(!(obj instanceof DataRef)) {
         return false;
      }

      DataRef ref = (DataRef) obj;

      if(super.equals(ref) || (!total && ref.getAttribute().equals(getAttribute()))) {
         if(ref instanceof BaseField) {
            BaseField field = (BaseField) ref;

            if(getSource(true) != null && field.getSource(true) != null) {
               return getSource(true).equals(field.getSource(true));
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return toString().hashCode();
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      return cloneObject();
   }

   /**
    * Create an embedded field.
    * @return the created embedded field.
    */
   @Override
   public Field createEmbeddedField() {
      String attr = getAttribute();
      String ent = getEntity();
      String descentity = (getSourceDescription() == null ?
         "" : getSourceDescription() + ".") + (ent == null ? "" : ent + ".");
      attr = descentity + attr;
      BaseField fld = new BaseField(null, attr);
      fld.visible = visible;
      fld.gfld = gfld;
      fld.dtype = dtype;
      fld.order = order;
      fld.processed = processed;
      fld.prefix = prefix;
      fld.type = type;

      return fld;
   }

   /**
    * Check if is group field.
    */
   @Override
   public boolean isGroupField() {
      return gfld;
   }

   /**
    * Set whether is a group field.
    */
   @Override
   public void setGroupField(boolean gfld) {
      this.gfld = gfld;
   }

   public int getOption() {
      return option;
   }

   public void setOption(int option) {
      this.option = option;
   }

   private boolean visible = true;
   // name of the source of the field, could be null if there is no join
   private String source = null;
   private String dtype = XSchema.STRING;
   private byte order = StyleConstants.SORT_NONE;
   private String prefix;
   private int type;
   private String entityDesc;
   private String attributeDesc;
   private transient boolean processed = false;
   private boolean gfld = false;
   private transient String view = null;
   private int option;
}
