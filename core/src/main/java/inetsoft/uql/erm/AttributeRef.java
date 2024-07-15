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
package inetsoft.uql.erm;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.Types;

/**
 * Class holding a reference to an attribute and its parent entity.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public class AttributeRef extends AbstractDataRef {
   /**
    * Create a new instance of AttributeRef. This constructor is for internal
    * use only.
    */
   public AttributeRef() {
      super();
   }

   /**
    * Create a reference to the specified attribute.
    *
    * @param attribute the name of the attribute
    */
   public AttributeRef(String attribute) {
      this(null, attribute);
   }

   /**
    * Create a reference to the specified attribute.
    *
    * @param entity the name of the attribute's parent entity
    * @param attribute the name of the attribute
    */
   public AttributeRef(String entity, String attribute) {
      this.entity = entity;
      this.attr = attribute;
   }

   /**
    * Get create an attribute ref using the entity and attribute of the ref.
    */
   public AttributeRef(DataRef ref) {
      this(ref.getEntity(), ref.getAttribute());
   }

   /**
    * Setter of ref type.
    *
    * @param rtype the type of the ref, NONE, DIMENSION or MEASURE.
    */
   public void setRefType(int rtype) {
      this.refType = (byte) rtype;
   }

   /**
    * Getter of ref type.
    */
   @Override
   public int getRefType() {
      return this.refType;
   }

   /**
    * Get the default formula.
    */
   @Override
   public String getDefaultFormula() {
      return formula;
   }

   /**
    * Set the default formula.
    */
   public void setDefaultFormula(String formula) {
      this.formula = formula;
   }

   /**
    * Check if the attribute is an expression.
    *
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise
    */
   @Override
   public boolean isExpression() {
      return false;
   }

   /**
    * Get the attribute's parent entity.
    *
    * @return the name of the entity
    */
   @Override
   public String getEntity() {
      return entity;
   }

   /**
    * Get the caption.
    * @return the caption.
    */
   public String getCaption() {
      return caption;
   }

   /**
    * Set the caption.
    * @param caption the specified caption.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Get the referenced attribute.
    *
    * @return the name of the attribute
    */
   @Override
   public String getAttribute() {
      return attr;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return dtype != null ? dtype : XSchema.STRING;
   }

   /**
    * Set the data type.
    * @param dtype the data type defined in XSchema.
    */
   public void setDataType(String dtype) {
      this.dtype = dtype;
   }

   @Override
   public boolean hasDataType() {
      return dtype != null;
   }

   /**
    * Get the sql type.
    */
   public int getSqlType() {
      return sqlType;
   }

   /**
    * Get the sql type.
    */
   public void setSqlType(int type) {
      sqlType = type;
   }

   /**
    * Get a textual representation of this object.
    *
    * @return a String representation of this object
    */
   public String toString() {
      if(caption != null) {
         return caption;
      }

      if(entity == null) {
         return "[" + attr + "]";
      }

      return "[" + entity + "].[" + attr + "]";
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      String ent = getEntity();
      boolean compact = Tool.isCompact();

      if(ent != null) {
         writer.print((compact ? "en=\"" : "entity=\"") + Tool.escape(ent) + "\" ");
      }

      if(getCaption() != null) {
         writer.print("caption=\"" + Tool.escape(getCaption()) + "\" ");
      }

      String attr = getAttribute();
      writer.print((compact ? "at=\"" : "attribute=\"") + Tool.escape(attr) + "\" ");

      if(getRefType() != NONE) {
         writer.print("refType=\"" + getRefType() + "\" ");
      }

      if(getDefaultFormula() != null) {
         writer.print("formula=\"" + Tool.escape(getDefaultFormula()) + "\" ");
      }

      writer.print("sqlType=\"" + sqlType + "\" ");
      writeDataType(writer);
   }

   protected void writeDataType(PrintWriter writer) {
      if(dtype != null && !XSchema.STRING.equals(dtype)) {
         writer.print(" dataType=\"");
         writer.print(dtype);
         writer.print("\" ");
      }
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeBoolean(getEntity() == null);

         if(getEntity() != null) {
            dos.writeUTF(getEntity());
         }

         dos.writeUTF(getAttribute());
         dos.writeInt(refType);
         dos.writeInt(sqlType);
         dos.writeBoolean(getCaption() == null);

         if(getCaption() != null) {
            dos.writeUTF(getCaption());
         }

         dos.writeBoolean(formula == null);

         if(formula != null) {
            dos.writeUTF(formula);
         }
      }
      catch (IOException e) {
      }
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String ent = Tool.getAttribute(tag, "entity");
      ent = ent == null ? Tool.getAttribute(tag, "en") : ent;

      if(ent != null && !ent.equals("")) {
         entity = ent;
      }

      String val = Tool.getAttribute(tag, "attribute");
      val = val == null ? Tool.getAttribute(tag, "at") : val;

      if(val != null) {
         attr = val;
      }
      else {
         attr = "";
      }

      caption = Tool.getAttribute(tag, "caption");
      String rtype = Tool.getAttribute(tag, "refType");

      if(rtype != null && rtype.length() > 0) {
         refType = (byte) Math.max(0, Integer.parseInt(rtype));
      }

      String sqlType = Tool.getAttribute(tag, "sqlType");

      if(!StringUtils.isEmpty(sqlType)) {
         this.sqlType = Integer.parseInt(sqlType);
      }

      formula = Tool.getAttribute(tag, "formula");
      parseDataType(tag);
   }

   protected void parseDataType(Element tag) {
      String val = Tool.getAttribute(tag, "dataType");

      if(val != null) {
         dtype = val;
      }
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      // optimization, this is an immutable object and don't need to be cloned.
      // subclass should call cloneObject to make a copy if it's mutable.
      return this;
   }

   /**
    * Get the view representation of this field.
    *
    * @return the view representation of this field
    */
   @Override
   public String toView() {
      if(caption != null) {
         return caption;
      }

      return Tool.localize(super.getName());
   }

   /**
    * Make a copy of this object in deep clone.
    */
   protected Object cloneObject() {
      return super.clone();
   }

   private String caption;
   private String entity;
   private String attr;
   private byte refType = NONE;
   private String formula = null;
   private String dtype = null;
   private int sqlType = Types.VARCHAR;

   private static final Logger LOG = LoggerFactory.getLogger(AttributeRef.class);
}
