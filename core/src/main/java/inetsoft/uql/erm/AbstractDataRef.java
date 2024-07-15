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

import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.util.Tool;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;

/**
 * Abstract class holding a reference to a SQL expression or an attribute.
 *
 * @version 10.0
 * @author  InetSoft Technology Corp
 */
public abstract class AbstractDataRef implements DataRef {
   /**
    * Get the class name.
    */
   private static String getClass(Class cobj, boolean compact) {
      String name = cobj.getName();
      String cls = compact ? CLASS_CLS.get(name) : null;
      return cls != null ? cls : name;
   }

   /**
    * Create a <tt>DataRef</tt> from an xml element.
    * @param elem the specified xml element.
    * @return the created <tt>DataRef</tt>.
    */
   public static DataRef createDataRef(Element elem) throws Exception {
      String name = Tool.getAttribute(elem, "class");
      name = name == null ? Tool.getAttribute(elem, "c") : name;
      name = name == null ? "inetsoft.uql.erm.AttributeRef" : name;
      DataRef ref = null;

      switch(name) {
      case "C": case "inetsoft.uql.asset.ColumnRef":
         ref = new ColumnRef();
         break;
      case "A": case "inetsoft.uql.erm.AttributeRef":
         ref = new AttributeRef();
         break;
      case "G": case "inetsoft.uql.asset.GroupRef":
         ref = new GroupRef();
         break;
      case "T": case "inetsoft.uql.asset.AggregateRef":
         ref = new AggregateRef();
         break;
      default:
         ref = (DataRef)
            Class.forName(Tool.convertUserClassName(name)).getConstructor().newInstance();
      }

      ref.parseXML(elem);
      return ref;
   }

   /**
    * Get the attribute's parent entity.
    * @return an Enumeration with the name of the entity.
    */
   @Override
   public Enumeration getEntities() {
      return new EntityEnumeration();
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    * @return an Enumeration containing AttributeRef objects.
    */
   @Override
   public Enumeration getAttributes() {
      return new AttributeEnumeration();
   }

   /**
    * Determine if the entity is blank.
    * @return <code>true</code> if entity is <code>null</code> or entity is
    *         equal to empty string ("").
    */
   @Override
   public boolean isEntityBlank() {
      return getEntity() == null || getEntity().length() == 0;
   }

   /**
    * Check if the data ref is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return getAttribute() == null || getAttribute().length() == 0;
   }

   /**
    * Check if data ref is visible.  Used mainly for drilling operations for
    * ChartRefs
    * @return Whether or not the ref should be included in output
    */
   public boolean isDrillVisible() {
      return drillVisible;
   }

   /**
    * Set data ref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param isVisible The visibility of the data ref.
    */
   public void setDrillVisible(boolean isVisible) {
      this.drillVisible = isVisible;
   }

   /**
    * Get internal name.
    * @return the internal name.
    */
   private String getName0() {
      if(cname == null) {
         String ent = getEntity();
         String attr = getAttribute();
         cname = ent == null || ent.length() == 0 ?
            (attr == null ? "" : attr) : ent + "." + attr;
         chash = Integer.MIN_VALUE;
      }

      return cname;
   }

   /**
    * Get the name of the field.
    * @return the name of the field.
    */
   @Override
   public String getName() {
      return getName0();
   }

   /**
    * Write an xml element representation of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      boolean compact = Tool.isCompact();

      if(compact) {
         writer.print("<dataRef c=\"");
      }
      else {
         writer.print("<dataRef class=\"");
      }

      writer.print(AbstractDataRef.getClass(getClass(), compact));
      writer.print("\" ");

      writeAttributes(writer);
      writer.print(">");
      writeCDATA(writer);
      writeContents(writer);
      writer.print("</dataRef>");
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   protected abstract void writeAttributes(PrintWriter writer);

   /**
    * Write the attributes of this object.
    * @param dos the output stream to which to write the XML data.
    */
   protected void writeAttributes2(DataOutputStream dos) {
   }

   /**
    * Write the CDATA of this object.
    * @param writer the output stream to which to write the XML data.
    */
   protected void writeCDATA(PrintWriter writer) {
   }

   /**
    * Write the CDATA of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   protected void writeCDATA2(DataOutputStream dos) {
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   protected void writeContents(PrintWriter writer) {
      // for flash side
      if(!Tool.isCompact()) {
         writer.print("<view>");
         writer.print("<![CDATA[" + Encode.forCDATA(toView()) + "]]>");
         writer.println("</view>");
      }
   }

   /**
    * Write the contents of this object.
    * @param dos the output stream to which to write the OutputStream data.
    */
   protected void writeContents2(DataOutputStream dos) {
      // for flash side
      try {
         dos.writeUTF(toView());
      }
      catch (IOException e) {
         // ignore
      }
   }

   /**
    * Read in the definition of this object from an XML tag.
    * @param tag the xml element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseCDATA(tag);
      parseContents(tag);
   }

   /**
    * Write data to a DataOutputStream.
    * @param dos the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      writeAttributes2(dos);
      writeCDATA2(dos);
      writeContents2(dos);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      //do nothing
      return true;
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the xml element representing this object.
    */
   protected abstract void parseAttributes(Element tag) throws Exception;

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the xml element representing this object.
    */
   protected void parseCDATA(Element tag) throws Exception {
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
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
    * Get the type node.
    * @return the type node of the column ref.
    */
   @Override
   public XTypeNode getTypeNode() {
      return XSchema.createPrimitiveType(getDataType());
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AbstractDataRef)) {
         return false;
      }

      AbstractDataRef attr2 = (AbstractDataRef) obj;

      if(hashCode0() != attr2.hashCode0()) {
         return false;
      }

      String name = getName0();
      String name2 = attr2.getName0();

      return Objects.equals(name, name2);
   }

   /**
    * Compare two column refs.
    * @param strict true to compare all properties of ColumnRef. Otherwise
    * only entity and attribute are compared.
    */
   @Override
   public boolean equals(Object obj, boolean strict) {
      return equals(obj);
   }

   /**
    * Get the hash code value for the object.
    * @return the hash code value for this object.
    */
   public int hashCode() {
      return hashCode0();
   }

   /**
    * Get a hash code value for the object.
    * @return a hash code value for this object.
    */
   private int hashCode0() {
      if(chash == Integer.MIN_VALUE) {
         chash = getName0().hashCode();
      }

      return chash;
   }

   /**
    * Get the original hash code.
    * @return the original hash code.
    */
   @Override
   public int addr() {
      return System.identityHashCode(this);
   }

   /**
    * Get the string presentation of a data ref.
    */
   public String toString() {
      return getName();
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         AbstractDataRef ref = (AbstractDataRef) super.clone();
         ref.cname = null;
         ref.chash = Integer.MIN_VALUE;

         return ref;
      }
      catch(CloneNotSupportedException cnse) {
         LOG.error("Failed to clone object", cnse);
      }

      return null;
   }

   /**
    * Enumeration that contains the only attribute referred to by the reference.
    */
   private class AttributeEnumeration implements Enumeration<String> {
      @Override
      public boolean hasMoreElements() {
         return flag;
      }

      @Override
      public String nextElement() {
         if(flag) {
            flag = false;
            return getAttribute();
         }

         return null;
      }

      private boolean flag = true;
   }

   /**
    * Enumeration that contains the only entity referred to by the reference.
    */
   private class EntityEnumeration implements Enumeration<String> {
      @Override
      public boolean hasMoreElements() {
         return flag;
      }

      @Override
      public String nextElement() {
         if(flag) {
            flag = false;
            return getEntity();
         }

         return null;
      }

      private boolean flag = true;
   }

   /**
    * Compare to another object.
    * @param obj the specified object to compare.
    * @return the comparison result.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof DataRef)) {
         return 1;
      }

      DataRef ref2 = (DataRef) obj;
      String entity = getEntity();
      String entity2 = ref2.getEntity();
      int val = compare(entity, entity2);

      if(val != 0) {
         return val;
      }

      String attr = getAttribute();
      String attr2 = ref2.getAttribute();
      return compare(attr, attr2);
   }

   /**
    * Compare two strings.
    * @param a the specified string a.
    * @param b the specified string b.
    * @return the comparison result.
    */
   private int compare(String a, String b) {
      if(a == null) {
         return b == null ? 0 : -1;
      }
      else if(b == null) {
         return 1;
      }

      return a.compareTo(b);
   }

   /**
    * Get the default formula.
    */
   @Override
   public String getDefaultFormula() {
      return null;
   }

   private static Map<String, String> CLASS_CLS= new HashMap<>();

   static {
      CLASS_CLS.put("inetsoft.uql.asset.ColumnRef", "C");
      CLASS_CLS.put("inetsoft.uql.erm.AttributeRef", "A");
      CLASS_CLS.put("inetsoft.uql.asset.GroupRef", "G");
      CLASS_CLS.put("inetsoft.uql.asset.AggregateRef", "T");
   }

   private boolean drillVisible = true;
   protected String cname = null; // cached name
   protected int chash = Integer.MIN_VALUE; // cached hash code

   private static final Logger LOG = LoggerFactory.getLogger(AbstractDataRef.class);
}
