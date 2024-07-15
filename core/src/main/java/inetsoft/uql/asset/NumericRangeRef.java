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

import inetsoft.uql.XConstants;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Vector;

/**
 * NumericRangeRef represents a numeric range data ref.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class NumericRangeRef extends ExpressionRef implements AssetObject,
   XConstants, SQLExpressionRef, RangeRef
{
   /**
    * Constructor.
    */
   public NumericRangeRef() {
      super();

      dbtype = "";
   }

   /**
    * Constructor.
    */
   public NumericRangeRef(String attr) {
      this();

      this.attr = attr;
   }

   /**
    * Constructor.
    */
   public NumericRangeRef(String attr, DataRef ref) {
      this(attr);

      this.ref = ref;
   }

   /**
    * Get the value range infomation.
    * @return the value range infomation.
    */
   public ValueRangeInfo getValueRangeInfo() {
      return vinfo;
   }

   /**
    * Set the value range infomation.
    * @param info the specified value range infomation.
    */
   public void setValueRangeInfo(ValueRangeInfo info) {
      this.vinfo = info;
   }

   /**
    * Get the attribute's parent entity.
    * @return the name of the entity.
    */
   @Override
   public String getEntity() {
      return null;
   }

   /**
    * Get the referenced attribute.
    * @return the name of the attribute.
    */
   @Override
   public String getAttribute() {
      return attr;
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
    * Set the data ref.
    * @param ref the specified data ref.
    */
   @Override
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Get the data type.
    * @return the data type defined in XSchema.
    */
   @Override
   public String getDataType() {
      return XSchema.STRING;
   }

   /**
    * Get a String representation of this object.
    *
    * @return a String representation of this object
    */
   public String toString() {
      return "[" + attr + "]";
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      // do nothing
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      ref.writeXML(writer);

      writer.print("<attribute>");
      writer.print("<![CDATA[" + attr + "]]>");
      writer.println("</attribute>");

      if(vinfo != null) {
         vinfo.writeXML(writer);
      }
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      super.writeContents2(dos);

      try {
         dos.writeUTF(ref.getClass().getName());
         ref.writeData(dos);
         dos.writeUTF(attr);
         dos.writeBoolean(vinfo == null);

         if(vinfo != null) {
            vinfo.writeData(dos);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to serialize content", ex);
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

      Element anode = Tool.getChildNodeByTagName(tag, "attribute");
      attr = Tool.getValue(anode);

      Element vnode = Tool.getChildNodeByTagName(tag, "valueRangeInfo");

      if(vnode != null) {
         vinfo = new ValueRangeInfo();
         vinfo.parseXML(vnode);
      }
   }

   /**
    * Get the name of this reference.
    * @return the reference name.
    */
   @Override
   public String getName() {
      return attr;
   }

   /**
    * Set the name of the field.
    * @param name the name of the field
    */
   @Override
   public void setName(String name) {
      this.attr = name;
      super.setName(name);
   }

   /**
    * Get the database type.
    * @return the database type.
    */
   @Override
   public String getDBType() {
      return dbtype;
   }

   /**
    * Set the database type.
    * @param dbtype the specified database type.
    */
   @Override
   public void setDBType(String dbtype) {
      this.dbtype = dbtype == null ? "" : dbtype;
   }

   /**
    * Get the database version.
    */
   @Override
   public String getDBVersion() {
      return dbversion;
   }

   /**
    * Set the database version.
    */
   @Override
   public void setDBVersion(String version) {
      this.dbversion = version;
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Check if this date range ref is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMergeable() {
      return !dbtype.equals("access");
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   @Override
   public String getExpression() {
      double[] arr = vinfo == null ? new double[0] : vinfo.getValues();

      if(arr.length == 0) {
         return "0";
      }

      String nkey = ref.getName() + "__" + vinfo;

      if(sqlKey != null && sqlKey.equals(nkey) && sqlCache != null) {
         return sqlCache;
      }

      String[] labels = vinfo.getLabels();
      String label = "";
      int j = 0;
      boolean useLabel = labels.length > 0;
      String field = "field['" + ref.getName() + "']";
      boolean showingBottomValue = vinfo.isShowBottomValue();
      boolean showingTopValue = vinfo.isShowTopValue();
      InclusiveType inclusiveType = vinfo.getInclusiveType();
      String upperOp = "<";
      String lowerOp = ">";

      if(inclusiveType != InclusiveType.NONE) {
         upperOp = inclusiveType == InclusiveType.UPPER ? "<=" : "<";
         lowerOp = inclusiveType == InclusiveType.UPPER ? ">" : ">=";
      }

      StringBuilder sb = new StringBuilder();
      sb.append("(CASE \n");

      if(showingBottomValue) {
         label = useLabel && labels[0] != null ?
            labels[0] : upperOp + toString(arr[0]);
         sb.append("WHEN ").append(field).append(" ").append(upperOp).append(" ").append(arr[0]).append(" THEN '");
         sb.append(Tool.escapeJavascript(label)).append("' \n");
         j++;
      }

      String upperOp0 = upperOp;
      String lowerOp0 = lowerOp;

      for(int i = 0; i < arr.length - 1; i++) {
         if(inclusiveType == InclusiveType.NONE && i == 0) {
            upperOp0 = "<=";
            lowerOp0 = ">=";
         }

         label = useLabel && labels[j + i] != null ?
            labels[j + i] : toString(arr[i]) + "-" + toString(arr[i + 1]);
         sb.append("WHEN ").append(field).append(" ").append(lowerOp0).append(" ")
            .append(arr[i]).append(" AND ").append(field).append(" ").append(upperOp0)
            .append(" ").append(arr[i + 1]).append(" THEN '")
            .append(Tool.escapeJavascript(label)).append("' \n");
      }

      if(showingTopValue) {
         label = useLabel && labels[labels.length - 1] != null ?
            labels[labels.length - 1] : lowerOp + toString(arr[arr.length - 1]);
         sb.append("WHEN ").append(field).append(" ").append(lowerOp).append(" ").append(arr[arr.length - 1]).append(" THEN '");
         sb.append(Tool.escapeJavascript(label)).append("' \n");
      }

      if(arr.length == 1 && !showingBottomValue && !showingTopValue) {
         label = useLabel && labels[0] != null ?
            labels[0] : toString(arr[0]) + "-" + toString(arr[0]);
         sb.append("WHEN ").append(field).append(" BETWEEN ").append(arr[0]).append(" AND ");
         sb.append(arr[0]).append(" THEN '").append(Tool.escapeJavascript(label)).append("' \n");
      }

      sb.append("ELSE '" + Catalog.getCatalog().getString("Others") + "'");
      sb.append(" END)");

      sqlCache = sb.toString();
      sqlKey = nkey;
      return sqlCache;
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   @Override
   public String getScriptExpression() {
      double[] arr = vinfo == null ? new double[0] : vinfo.getValues();

      if(arr.length == 0) {
         return "0";
      }

      String nkey = ref.getName() + "__" + vinfo;

      if(jsKey != null && jsKey.equals(nkey) && jsCache != null) {
         return jsCache;
      }

      String[] labels = vinfo.getLabels();
      String label = "";
      int j = 0;
      boolean useLabel = labels.length > 0;
      String field = "field['" + Tool.escapeJavascript(ref.getName()) + "']";
      boolean showingBottomValue = vinfo.isShowBottomValue();
      boolean showingTopValue = vinfo.isShowTopValue();
      InclusiveType inclusiveType = vinfo.getInclusiveType();
      String upperOp = "<";
      String lowerOp = ">";

      if(inclusiveType != InclusiveType.NONE) {
         upperOp = inclusiveType == InclusiveType.UPPER ? "<=" : "<";
         lowerOp = inclusiveType == InclusiveType.UPPER ? ">" : ">=";
      }

      StringBuilder sb = new StringBuilder();

      if(showingBottomValue) {
         label = useLabel && labels[0] != null ?
            labels[0] : upperOp + toString(arr[0]);
         sb.append("if(").append(field).append(" ").append(upperOp).append(" ").append(arr[0]).append(" && ").append(field).append(" != null) {\n");
         sb.append("   \"").append(Tool.escapeJavascript(label)).append("\";\n");
         sb.append("}\n");
         j++;
      }

      String upperOp0 = upperOp;
      String lowerOp0 = lowerOp;

      for(int i = 1; i < arr.length; i++) {
         if(inclusiveType == InclusiveType.NONE) {
            if(i == 1) {
               upperOp0 = "<=";
            }

            lowerOp0 = ">=";
         }

         label = useLabel && labels[j + i - 1] != null ?
            labels[j + i - 1] : toString(arr[i - 1]) + "-" + toString(arr[i]);

         if(sb.length() > 0) {
            sb.append("else ");
         }

         sb.append("if(").append(field).append(" ").append(lowerOp0).append(" ")
            .append(arr[i - 1]).append(" && ").append(field).append(" ").append(upperOp0)
            .append(" ").append(arr[i]).append(" && ").append(field)
            .append(" != null) {\n").append("   \"").append(Tool.escapeJavascript(label))
            .append("\";\n").append("}\n");
      }

      if(showingTopValue) {
         label = useLabel && labels[labels.length - 1] != null ?
            labels[labels.length - 1] : lowerOp + toString(arr[arr.length - 1]);

         if(sb.length() > 0) {
            sb.append("else ");
         }

         sb.append("if(").append(field).append(" ").append(lowerOp).append(" ").append(arr[arr.length - 1]).append(") {\n");
         sb.append("   \"").append(Tool.escapeJavascript(label)).append("\";\n");
         sb.append("}\n");
      }

      if(arr.length == 1 && !showingBottomValue && !showingTopValue) {
         label = useLabel && labels[0] != null ?
            labels[0] : toString(arr[0]) + "-" + toString(arr[0]);

         if(sb.length() > 0) {
            sb.append("else ");
         }

         sb.append("if(").append(field).append(" >= ").append(arr[0]).append(" && ").append(field).append(" <= ").append(arr[0]).append(" && ").append(field).append(" != null) {\n");
         sb.append("   \"").append(Tool.escapeJavascript(label)).append("\";\n");
         sb.append("}\n");
      }

      sb.append("else {\n");
      sb.append("   '").append(Catalog.getCatalog().getString("Others")).append("';\n");
      sb.append("}\n");

      jsCache = sb.toString();
      jsKey = nkey;
      return jsCache;
   }

   /**
    * Convert double to string.
    */
   private static String toString(double val) {
      if(val == (int) val) {
         return Integer.toString((int) val);
      }

      return Double.toString(val);
   }

   /**
    * Check if this expression is sql expression.
    * @return true if is, false otherwise.
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      Vector list = new Vector();
      list.add(ref);
      return list.elements();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         NumericRangeRef ref = (NumericRangeRef) super.clone();
         ref.vinfo = vinfo == null ? null : (ValueRangeInfo) vinfo.clone();

         return ref;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private DataRef ref;
   private String attr;
   private ValueRangeInfo vinfo;

   private transient String dbtype;
   private transient String dbversion;
   // optimization, cache create expressions
   private transient String sqlKey;
   private transient String jsKey;
   private transient String sqlCache;
   private transient String jsCache;

   private static final Logger LOG =
      LoggerFactory.getLogger(NumericRangeRef.class);
}
