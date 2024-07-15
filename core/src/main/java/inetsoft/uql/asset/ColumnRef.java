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
package inetsoft.uql.asset;

import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.Types;
import java.util.Enumeration;

/**
 * Column represents a column in <tt>TableAssembly</tt>. It stores
 * information including data ref, alias, width, order, visibility,
 * data type, etc.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ColumnRef extends AbstractDataRef implements AssetObject, DataRefWrapper {
   /**
    * Rename a column.
    * @param column the specified column.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public static void renameColumn(ColumnRef column, String oname, String nname) {
      if(column == null) {
         return;
      }

      if(!column.isExpression()) {
        if(Tool.equals(oname, column.getEntity())) {
           AttributeRef attr = (AttributeRef) column.getDataRef();
           AttributeRef nattr = new AttributeRef(nname, attr.getAttribute());
           // keep caption, necessary for cube
           nattr.setCaption(attr.getCaption());
           column.setDataRef(nattr);
           column.setView(nattr.getName());
        }
      }
      else {
         DataRef ref = column.getDataRef();

         if(ref instanceof AliasDataRef) {
            AliasDataRef aref = (AliasDataRef) ref;
            ref = aref.getDataRef();

            if(ref instanceof ColumnRef) {
               renameColumn((ColumnRef) ref, oname, nname);
            }
            else if(ref instanceof AttributeRef) {
               AttributeRef nref = renameAttributeRef((AttributeRef) ref, oname, nname);
               aref.setDataRef(nref);
            }
         }

         if(ref instanceof DateRangeRef) {
            DataRef dref = ((DateRangeRef) ref).getDataRef();

            if(dref instanceof AttributeRef) {
               AttributeRef nref = renameAttributeRef((AttributeRef) dref, oname, nname);
               ((DateRangeRef) ref).setDataRef(nref);
            }
         }
      }
   }

   private static AttributeRef renameAttributeRef(AttributeRef ref, String oname, String nname) {
      if(ref != null && Tool.equals(oname, ref.getEntity())) {
         AttributeRef nref = new AttributeRef(nname, ref.getAttribute());

         // keep caption, necessary for cube
         nref.setCaption(ref.getCaption());
         return nref;
      }

      return ref;
   }

   public static String getTooltip(ColumnRef ref) {
      String attributeView = ColumnRef.getAttributeView(ref);
      String tooltip = "";
      DataRef dataRef = ref.getDataRef();

      if(attributeView == null) {
         return tooltip;
      }

      if(dataRef instanceof DateRangeRef || dataRef instanceof NumericRangeRef) {
         tooltip = ref.getName() + " (" + attributeView + ")"
            + (!StringUtils.isEmpty(ref.getDescription()) ? "\n" + ref.getDescription() : "");
      }
      else if(!Tool.equals(ref.getName(), attributeView)) {
         tooltip = "Alias: " + ref.getName() + " (" + attributeView + ")"
            + (!StringUtils.isEmpty(ref.getDescription()) ?
            "\nDescription: " + ref.getDescription() : "");

      }
      else {
         tooltip = ref.getName() + (!StringUtils.isEmpty(ref.getDescription()) ?
            "\n" + ref.getDescription() : "");
      }

      return tooltip;
   }

   public static String getAttributeView(ColumnRef ref) {
      AttributeRef attributeRef = getAttributeRef(ref.getDataRef());

      return attributeRef == null ? null : attributeRef.toView();
   }

   public static AttributeRef getAttributeRef(DataRef dataRef) {
      AttributeRef attributeRef = null;

      if(dataRef instanceof AttributeRef) {
         attributeRef = (AttributeRef) dataRef;
      }
      else if(dataRef instanceof DateRangeRef) {
         attributeRef = getAttributeRef(((DateRangeRef) dataRef).getDataRef());
      }
      else if(dataRef instanceof NumericRangeRef) {
         attributeRef = (AttributeRef) ((NumericRangeRef) dataRef).getDataRef();
      }

      return attributeRef;
   }

   /**
    * Constructor.
    */
   public ColumnRef() {
      super();
   }

   /**
    * Constructor.
    */
   public ColumnRef(DataRef ref) {
      setDataRef(ref);
   }

   /**
    * Check if the attribute is an expression.
    * @return <tt>true</tt> if is an expression, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpression() {
      return ref.isExpression();
   }

   /**
    * Get the type of the field.
    * @return the type of the field.
    */
   @Override
   public int getRefType() {
      return ref.getRefType();
   }

   /**
    * Get the default formula.
    */
   @Override
   public String getDefaultFormula() {
      return ref.getDefaultFormula();
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return ref.getEntity();
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return ref.getEntities();
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return ref.getAttribute();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return ref.getAttributes();
   }

   /**
    * Get a list of all attributes that are referenced by contained expression.
    * @return an Enumeration containing AttributeRef objects.
    */
   public Enumeration getExpAttributes() {
      if(!isExpression()) {
         return null;
      }

      DataRef ref0 = AssetUtil.getBaseAttribute(ref);

      return ref0.getAttributes();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or blank.
    */
   @Override
   public boolean isEntityBlank() {
      return ref.isEntityBlank();
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return alias != null && aalias ? alias : ref.getName();
   }

   /**
    * Get the header name of the field.
    * @return the header name of the field.
    */
   public String getHeaderName() {
      return getCaption() != null ? getCaption()
         : alias != null && aalias ? alias : ref.getName();
   }

   /**
    * Get the contained data ref.
    * @return the contained data ref.
    */
   @Override
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the description of the attribute.
    *
    * @param desc the specified attribute's description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the description of the attribute.
    *
    * @return the attribute's description.
    */
   public String getDescription() {
      return desc == null ? "" : desc;
   }

   /**
    * Set the base data ref.
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Get the alias.
    * @return the alias of the column ref.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set the alias.
    * @param alias the specified alias.
    */
   public void setAlias(String alias) {
      alias = alias != null && !alias.trim().isEmpty() ? alias : null;

      if(ref != null) {
         alias = getAttribute() != null && getAttribute().equals(alias) ? null : alias;
      }

      this.alias = alias;
      cname = null;
      chash = Integer.MIN_VALUE;
   }

   /**
    * Set whether to apply alias.
    * @param aalias <tt>true</tt> to apply alias.
    */
   public void setApplyingAlias(boolean aalias) {
      this.aalias = aalias;
   }

   /**
    * Check if apply alias.
    * @return <tt>true</tt> to apply alias.
    */
   public boolean isApplyingAlias() {
      return this.aalias;
   }

   /**
    * Check if is visible.
    * @return true if is visible, false otherwise.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the visibility option.
    * @param visible true if is visible, false otherwise.
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check if is hiddenParameter.
    * @return true if is hiddenParameter, false otherwise.
    */
   public boolean isHiddenParameter() {
      return hiddenParameter;
   }

   /**
    * Set the hiddenParameter option.
    * @param hiddenParameter true if is hiddenParameter, false otherwise.
    */
   public void setHiddenParameter(boolean hiddenParameter) {
      this.hiddenParameter = hiddenParameter;
   }

   /**
    * Check if is valid.
    * @return true if is valid, false otherwise.
    */
   public boolean isValid() {
      return valid;
   }

   /**
    * Set the valid option.
    * @param valid true if is valid, false otherwise.
    */
   public void setValid(boolean valid) {
      this.valid = valid;
   }

   /**
    * Check if is sql.
    * @return true if is sql, false otherwise.
    */
   public boolean isSQL() {
      return sql;
   }

   /**
    * Set the sql option.
    * @param sql true if is sql, false otherwise.
    */
   public void setSQL(boolean sql) {
      this.sql = sql;
   }

   /**
    * Get the width.
    * @return the width of the column ref.
    */
   public int getWidth() {
      return width;
   }

   /**
    * Set the width.
    * @param width the specified width.
    */
   public void setWidth(int width) {
      if(width >= 1) {
         this.width = (byte) width;
      }
   }

   /**
    * Get the pixel width.
    * @return the width of the column ref.
    */
   public int getPixelWidth() {
      return pixelWidth;
   }

   /**
    * Set the pixel width.
    * @param width the specified width.
    */
   public void setPixelWidth(int width) {
      if(width >= 1) {
         this.pixelWidth = width;
      }
   }

   /**
    * Set the data type.
    * @param type the specified data type defined in XSchema.
    */
   public void setDataType(String type) {
      this.dtype = XSchema.isPrimitiveType(type) ? type : XSchema.STRING;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return dtype != null ? dtype : ref != null ? ref.getDataType() : XSchema.STRING;
   }

   @Override
   public boolean isDataTypeSet() {
      return dtype != null;
   }

   /**
    * Get the sql type.
    */
   public int getSqlType() {
      if(ref instanceof AttributeRef) {
         return ((AttributeRef) ref).getSqlType();
      }

      return sqlType;
   }

   /**
    * Get the sql type.
    */
   public void setSqlType(int type) {
      sqlType = type;
   }

   /**
    * Get original data type.
    */
   public String getOriginalType() {
      return originalType;
   }

   /**
    * Set original data type.
    */
   public void setOriginalType(String originalType) {
      this.originalType = originalType;
   }

   /**
    * Get the type node.
    * @return the type node of the column ref.
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      if(width != 1) {
         writer.print(" width=\"");
         writer.print(width);
         writer.print("\"");
      }

      if(pixelWidth != 0) {
         writer.print(" pixelwidth=\"");
         writer.print(pixelWidth);
         writer.print("\"");
      }

      if(!visible) {
         writer.print(" visible=\"");
         writer.print(visible);
         writer.print("\"");
      }

      if(!valid) {
         writer.print(" valid=\"");
         writer.print(valid);
         writer.print("\"");
      }

      if(!sql) {
         writer.print(" sql=\"");
         writer.print(sql);
         writer.print("\"");
      }

      if(dtype != null && !XSchema.STRING.equals(dtype)) {
         writer.print(" dataType=\"");
         writer.print(dtype);
         writer.print("\"");
      }

      writer.print(" sqlType=\"");
      writer.print(sqlType);
      writer.print("\"");

      if(!aalias) {
         writer.print(" applyingAlias=\"");
         writer.print(aalias);
         writer.print("\"");
      }
   }

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeAttributes2(DataOutputStream dos) {
      try {
         dos.writeInt(width);
         dos.writeBoolean(visible);
         dos.writeBoolean(valid);
         dos.writeBoolean(sql);
         dos.writeUTF(dtype != null ? dtype : XSchema.STRING);
         dos.writeInt(sqlType);
         // transient
         //dos.writeBoolean(aalias);
      }
      catch (IOException e) {
         LOG.error("Failed to serialize attributes", e);
      }
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "width");

      if(val != null) {
         width = (byte) Integer.parseInt(val);
      }

      val = Tool.getAttribute(tag, "pixelwidth");

      if(val != null) {
         pixelWidth = Integer.parseInt(val);
      }

      val = Tool.getAttribute(tag, "visible");

      if(val != null) {
         visible = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "valid");

      if(val != null) {
         valid = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "sql");

      if(val != null) {
         sql = "true".equals(val);
      }

      val = Tool.getAttribute(tag, "dataType");

      if(val != null) {
         dtype = val;
      }

      val = Tool.getAttribute(tag, "sqlType");

      if(val != null) {
         try {
            sqlType = Integer.parseInt(val);
         }
         catch(NumberFormatException ex) {
            sqlType = Types.VARCHAR;
         }
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);

      if(alias != null) {
         writer.print("<alias>");
         writer.print("<![CDATA[" + alias + "]]>");
         writer.println("</alias>");
      }

      if(caption != null) {
         writer.print("<caption>");
         writer.print("<![CDATA[" + caption + "]]>");
         writer.println("</caption>");
      }

      if(desc != null) {
         writer.print("<description>");
         writer.print("<![CDATA[" + desc + "]]>");
         writer.println("</description>");
      }

      if(view != null) {
         writer.print("<view>");
         writer.print("<![CDATA[" + view + "]]>");
         writer.println("</view>");
      }
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);
         dos.writeBoolean(alias == null);

         if(alias != null) {
            dos.writeUTF(alias);
         }

         dos.writeBoolean(caption == null);

         if(caption != null) {
            dos.writeUTF(caption);
         }

         dos.writeBoolean(view == null);

         if(view != null) {
            dos.writeUTF(view);
         }
      }
      catch(IOException e) {
         LOG.error("Failed to serialize contents", e);
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      Element dnode = Tool.getChildNodeByTagName(tag, "dataRef");
      ref = createDataRef(dnode);
      cname = null;
      chash = Integer.MIN_VALUE;
      alias = Tool.getChildValueByTagName(tag, "alias");
      desc = Tool.getChildValueByTagName(tag, "description");
      caption = Tool.getChildValueByTagName(tag, "caption");
      view = Tool.getChildValueByTagName(tag, "view");
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return getName();
   }

   /**
    * Get the view representation of this field.
    * @return the view representation of this field.
    */
   @Override
   public String toView() {
      if(view != null) {
         return view;
      }

      return alias != null && alias.length() > 0 && aalias ? alias : ref.toView();
   }

   /**
    * Check if the column ref is processed.
    */
   public boolean isProcessed() {
      return processed;
   }

   /**
    * Set the processed flag.
    * @param processed <tt>true</tt> if processed, <tt>false</tt> otherwise.
    */
   public void setProcessed(boolean processed) {
      this.processed = processed;
   }

   /**
    * Set the caption.
    * @param caption the specified caption.
    */
   public void setCaption(String caption) {
      this.caption = caption;
   }

   /**
    * Get the caption.
    * @return the caption of the column ref.
    */
   public String getCaption() {
      return caption == null && ref instanceof AttributeRef ?
         ((AttributeRef) ref).getCaption() : caption;
   }

   /**
    * Set the view.
    * @param view the specified view.
    */
   public void setView(String view) {
      this.view = view;
   }

   /**
    * Get the view.
    * @return the view of the column ref.
    */
   public String getView() {
      return view == null && ref instanceof AttributeRef ? ref.getName() : view;
   }

   /**
    * Clone the object.
    */
   @Override
   public ColumnRef clone() {
      try {
         ColumnRef col2 = (ColumnRef) super.clone();

         if(ref != null) {
            col2.ref = (DataRef) ref.clone();
            col2.cname = null;
            col2.oldName = oldName;
            col2.lastOldName = lastOldName;
            col2.chash = Integer.MIN_VALUE;
         }

         return col2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Copy the attributes.
    * @param from the specified column ref to copy from.
    */
   public void copyAttributes(ColumnRef from) {
      this.alias = from.alias;
      this.desc = from.desc;
      this.caption = from.caption;
      this.width = from.width;
      this.visible = from.visible;
      this.valid = from.valid;
      this.sql = from.sql;
      this.dtype = from.dtype;
      this.sqlType = from.sqlType;
      this.processed = from.processed;
   }

   /**
    * Compare two column refs.
    * @param strict true to compare all properties of ColumnRef. Otherwise
    * only entity and attribute are compared.
    */
   @Override
   public boolean equals(Object obj, boolean strict) {
      if(!strict) {
         return super.equals(obj);
      }

      try {
         ColumnRef cref = (ColumnRef) obj;

         if(!super.equals(obj)) {
            return false;
         }

         return Tool.equals(ref, cref.ref) && Tool.equals(alias, cref.alias) &&
            width == cref.width && visible == cref.visible &&
            valid == cref.valid && sql == cref.sql && Tool.equals(desc, cref.desc) &&
            Tool.equals(dtype, cref.dtype);
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   /**
    * Get ref real name without alias.
    */
   public String getRealName() {
      boolean cube = (getRefType() & DataRef.CUBE) == DataRef.CUBE;
      String name = !cube && alias != null && ref instanceof AttributeRef ?
         ((AttributeRef) ref).getCaption() : getName();

      return name == null ? getName() : name;
   }

   /**
    * Get the name which ends up displayed in the table.
    *
    * @return the alias if one exists; otherwise the attribute.
    */
   public String getDisplayName() {
      final String alias = getAlias();
      return alias == null ? getAttribute() : alias;
   }

   public void setOldName(String name) {
      this.oldName = name;
   }

   /**
    * Get last old name for undo column to some state before save.
    *
    * @return lastOldName last old name for undo column to some state before save.
    */
   public String getLastOldName() {
      return lastOldName;
   }

   /**
    * Set last old name for undo column to some state before save.
    *
    * @param name last old name for undo column to some state before save.
    */
   public void setLastOldName(String name) {
      this.lastOldName = name;
   }

   public String getOldName() {
      return oldName;
   }

   private DataRef ref = null;
   private String alias = null;
   private byte width = 1;
   private int pixelWidth;
   private boolean visible = true;
   private boolean valid = true;
   private boolean sql = true;
   private String dtype = null;
   private int sqlType = Types.VARCHAR;
   private String desc = null;
   private String oldName = null;
   private String lastOldName = null; // using last old name to undo to next action before save.

   private transient boolean processed = false;
   private transient boolean aalias = true;
   private transient boolean hiddenParameter = false;
   private transient String caption;
   private transient String view;
   private transient String originalType;

   private static final Logger LOG = LoggerFactory.getLogger(ColumnRef.class);
}
