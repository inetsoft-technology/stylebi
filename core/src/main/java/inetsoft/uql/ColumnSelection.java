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
package inetsoft.uql;

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * A Column represents a selection of query column, data model
 * attributes or formula expressions.
 * To use ColumnSelection with query columns or formula expressions,
 * use DataRef objects where the attribute property is the column name
 * and the entity property is <code>null</code>.
 * @see inetsoft.uql.erm.DataRef
 *
 * @version 6.0, 10/20/2003
 * @author InetSoft Technology Corp
 */
public class ColumnSelection implements XMLSerializable, Cloneable, Serializable {
   /**
    * Construct a new instance of ColumnSelection.
    */
   public ColumnSelection() {
      attrs = new ListWithFastLookup<>();
   }

   /**
    * Create a column selection of the attributes.
    */
   public ColumnSelection(List<DataRef> refs) {
      attrs = new ListWithFastLookup<>(refs);
   }

   /**
    * Construct a new instance of ColumnSelection.
    */
   private ColumnSelection(int size) {
      attrs = new ListWithFastLookup<>(size);
   }

   /**
    * Remove all the attributes come from another column selection.
    * @param columns the specified column selection.
    */
   public void removeAttributes(ColumnSelection columns) {
      try {
         lock.writeLock().lock();
         attrs.removeAll(columns.attrs);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Clear all attributes from this selection.
    */
   public void clear() {
      try {
         lock.writeLock().lock();
         attrs.clear();
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Add a data model attribute or expression to the selection.
    * @param attribute an DataRef object describing a data model attribute
    * or expression.
    */
   public void addAttribute(DataRef attribute) {
      addAttribute(attribute, true);
   }

   /**
    * Add a data model attribute or expression to the selection.
    * @param attribute an DataRef object describing a data model attribute
    * or expression.
    * @param exclusive <tt>true</tt> if exclusive.
    */
   public void addAttribute(DataRef attribute, boolean exclusive) {
      try {
         lock.writeLock().lock();
         if(!exclusive || !attrs.contains(attribute)) {
            attrs.add(attribute);
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Add an attribute or expression to the selection at specified index.
    * @param index the specified index.
    * @param attribute the DataRef object to be added.
    * @param exclusive <tt>true</tt> if exclusive.
    */
   public void addAttribute(int index, DataRef attribute, boolean exclusive) {
      try {
         lock.writeLock().lock();
         if(!exclusive || !attrs.contains(attribute)) {
            if(index < attrs.size()) {
               attrs.add(index, attribute);
            }
            else {
               attrs.add(attribute);
            }
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Add an attribute or expression to the selection at specified index.
    * @param index the specified index.
    * @param attribute the DataRef object to be added.
    */
   public void addAttribute(int index, DataRef attribute) {
      try {
         lock.writeLock().lock();
         if(!attrs.contains(attribute)) {
            if(index < attrs.size()) {
               attrs.add(index, attribute);
            }
            else {
               attrs.add(attribute);
            }
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Check if an attribute or expression is already defined in the selection.
    */
   public boolean containsAttribute(DataRef attribute) {
      try {
         lock.readLock().lock();
         return attrs.contains(attribute);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Find the contained attribute equals to an attribute.
    * @param attribute the specified attribute.
    * @return the contained attribute equals to the attribute, <tt>null</tt>
    * not found.
    */
   public DataRef findAttribute(DataRef attribute) {
      if(attribute == null) {
         return null;
      }

      int index = indexOfAttribute(attribute);
      return (index < 0) ? null : getAttribute(index);
   }

   /**
    * Get index of an attribute.
    *
    * @param attribute the specified attribute
    * @return index of the attribute
    */
   public int indexOfAttribute(DataRef attribute) {
      try {
         lock.readLock().lock();
         return attrs.indexOf(attribute);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Remove the specified attribute or expression from the selection.
    * @param attribute an DataRef object describing a data model attribute
    * or expression.
    */
   public void removeAttribute(DataRef attribute) {
      try {
         lock.writeLock().lock();
         attrs.remove(attribute);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Remove the specified attribute or expression from the selection.
    * @param idx the index of the attribute to remove.
    */
   public void removeAttribute(int idx) {
      try {
         lock.writeLock().lock();
         attrs.remove(idx);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Get a list of all attributes and expressions in this selection.
    * @return a collection of DataRef objects
    */
   public final Enumeration getAttributes() {
      return new IteratorEnumeration(attrs.iterator());
   }

   /**
    * Set the data ref at the specified index.
    * @param idx the attribute index.
    * @param attribute an DataRef object will be set to the index.
    */
   public final void setAttribute(int idx, DataRef attribute) {
      try {
         lock.writeLock().lock();
         if(!attrs.contains(attribute)) {
            attrs.set(idx, attribute);
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Get an attribute or an expression.
    * @param idx attribute index.
    */
   public final DataRef getAttribute(int idx) {
      try {
         lock.readLock().lock();
         return attrs.get(idx);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Sort a list of all attributes and expressions in this selection.
    */
   public void sortBy(Comparator<DataRef> comparator) {
      try {
         lock.writeLock().lock();
         attrs.sort(comparator);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Get an attribute or an expression.
    * @param name the name of the attribute whose DataRef object is desired.
    * @return either DataRef object for a given attribute
    * or <code>null</code>.
    */
   public DataRef getAttribute(String name) {
      return getAttribute(name, true);
   }

   /**
    * Get an attribute or an expression.
    * @param name the name of the attribute whose DataRef object is desired.
    * @return either DataRef object for a given attribute
    * or <code>null</code>.
    */
   public DataRef getAttribute(String name, boolean fuzz) {
      if(name == null) {
         return null;
      }

      int index = name.lastIndexOf('.');
      String entity = index >= 0 ? name.substring(0, index) : null;
      DataRef attribute = getAttribute(name, entity, fuzz);

      while(attribute == null && entity != null && entity.lastIndexOf('.') != -1) {
         entity = entity.substring(0, entity.lastIndexOf('.'));
         attribute = getAttribute(name, entity, fuzz);
      }

      return attribute;
   }

   /**
    * Get an attribute or an expression.
    * @param name the name of the attribute whose DataRef object is desired.
    * @param entity the name of the entity.
    * @return either DataRef object for a given attribute
    * or <code>null</code>.
    */
   public DataRef getAttribute(String name, String entity) {
      return getAttribute(name, entity, true);
   }

   /**
    * Get an attribute or an expression.
    * @param name the name of the attribute whose DataRef object is desired.
    * @param entityName the name of the entity.
    * @return either DataRef object for a given attribute
    * or <code>null</code>.
    */
   public DataRef getAttribute(String name, String entityName, boolean fuzz) {
      if(name == null) {
         return null;
      }

      try {
         lock.readLock().lock();

         for(DataRef dr : attrs) {
            if(dr.getName().equals(name)) {
               return dr;
            }
         }

         for(DataRef dr : attrs) {
            String entity = dr.getEntity();
            String attr = dr.getAttribute();
            String oname = entity == null || entity.length() == 0 ? attr : entity + "." + attr;

            if(oname.equals(dr.getName()) && dr.getAttribute().equals(name)) {
               return dr;
            }

            if((dr.getRefType() & DataRef.CUBE) == DataRef.CUBE) {
               attr = Tool.replaceAll(attr, "[", "");
               attr = Tool.replaceAll(attr, "]", "");

               if(attr.equals(name)) {
                  return dr;
               }

               // fix bug1304592646321, caption
               if(dr instanceof ColumnRef) {
                  String caption = ((ColumnRef) dr).getCaption();

                  if(name.equals(caption)) {
                     return dr;
                  }
               }
            }
         }

         if(fuzz && entityName != null && !entityName.isEmpty() && name.startsWith(entityName)) {
            name = name.substring(entityName.length() + 1);

            for(DataRef dr : attrs) {
               if(dr.getAttribute().equals(name)) {
                  return dr;
               }
            }
         }
      }
      finally {
         lock.readLock().unlock();
      }

      return null;
   }

   /**
    * Get the number of attributes anr expressions in this selection.
    * @return the number of attributes and expressions in this selection.
    */
   public int getAttributeCount() {
      return attrs.size();
   }

   /**
    * Remove all attributes and expressions from the selection.
    */
   public void removeAllAttributes() {
      try {
         lock.writeLock().lock();
         attrs.clear();
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   /**
    * Check if the column selection is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return attrs.isEmpty();
   }

   /**
    * Get the attributes as a stream.
    */
   public Stream<DataRef> stream() {
      // avoid concurrent modification
      return new ArrayList<>(attrs).stream();
   }

   /*
    * Write this data selection to XML.
    * @param writer the stream to output the XML text to
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<ColumnSelection>");

      try {
         lock.readLock().lock();
         for(DataRef dref : attrs) {
            dref.writeXML(writer);
         }
      }
      finally {
         lock.readLock().unlock();
      }

      Enumeration keys = prop.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();
         Object value = prop.get(key);
         writer.println("<property><name><![CDATA[" + key + "]]></name>" +
            "<value><![CDATA[" + value + "]]></value></property>");
      }

      writer.println("</ColumnSelection>");
   }

   /**
    * Read in the XML representation of this object.
    * @param tag the XML element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      clear();
      NodeList nlist = Tool.getChildNodesByTagName(tag, "dataRef");

      for(int i = 0; i < nlist.getLength(); i++) {
         if(!(nlist.item(i) instanceof Element)) {
            continue;
         }

         Element atag = (Element) nlist.item(i);
         DataRef dref = AbstractDataRef.createDataRef(atag);
         attrs.add(dref);
      }

      nlist = Tool.getChildNodesByTagName(tag, "property");

      for(int i = 0; i < nlist.getLength(); i++) {
         Element tag2 = (Element) nlist.item(i);
         Element nnode = Tool.getChildNodeByTagName(tag2, "name");
         Element vnode = Tool.getChildNodeByTagName(tag2, "value");
         String name = Tool.getValue(nnode);
         String value = Tool.getValue(vnode);

         if(name != null && value != null) {
            setProperty(name, value);
         }
      }
   }

   public int getHiddenColumnCount() {
      int count = 0;

      try {
         lock.readLock().lock();

         for(int i = 0; i < attrs.size(); i++) {
            if(attrs.get(i) instanceof ColumnRef && !((ColumnRef) attrs.get(i)).isVisible()) {
               count++;
            }
         }
      }
      finally {
         lock.readLock().unlock();
      }

      return count;
   }

   public String toString() {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < attrs.size(); i++) {
         if(i > 0) {
            buf.append(",");
         }

         buf.append(attrs.get(i).toString()).append("(").append(attrs.get(i).getDataType());

         if(attrs.get(i) instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) attrs.get(i);

            if(!col.isVisible()) {
               buf.append(",hidden");
            }

            if(col.isHiddenParameter()) {
               buf.append(",hiddenParam");
            }

            if(col.getClass() != ColumnRef.class) {
               String cls = col.getClass().getName();
               buf.append("," + cls.substring(cls.lastIndexOf('.') + 1));
            }
         }

         buf.append(")");
      }

      return buf.toString();
   }

   /**
    * Make a deep copy of this column selection.
    */
   @Override
   public ColumnSelection clone() {
      return clone(false);
   }

   /**
    * Clone the column selection.
    * @param shallow <tt>true</tt> if the attributes should not be cloned,
    * <tt>false</tt> otherwise.
    * @return the cloned column selection.
    */
   public ColumnSelection clone(boolean shallow) {
      int cnt = attrs.size();
      ColumnSelection sel = new ColumnSelection(cnt);

      try {
         lock.readLock().lock();
         for(DataRef drefin : attrs) {
            DataRef drefout = shallow || drefin == null ? drefin : (DataRef) drefin.clone();
            sel.attrs.add(drefout);
         }
      }
      finally {
         lock.readLock().unlock();
      }

      sel.prop = (Properties) prop.clone();
      return sel;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      final ColumnSelection that = (ColumnSelection) o;
      return attrs.equals(that.attrs);
   }

   /**
    * Compare two column selection list.
    * @param strict false to check only the entity/attribute of attributes.
    * Otherwise other properties are compared, such as alias, width.
    */
   public boolean equals(Object obj, boolean strict) {
      if(!strict) {
         return equals(obj);
      }

      try {
         lock.readLock().lock();
         ColumnSelection sel = (ColumnSelection) obj;

         if(attrs.size() != sel.attrs.size()) {
            return false;
         }

         for(int i = 0; i < attrs.size(); i++) {
            DataRef ref = attrs.get(i);

            if(!ref.equals(sel.attrs.get(i), strict)) {
               return false;
            }
         }

         return true;
      }
      catch(Exception ex) {
         return false;
      }
      finally {
         lock.readLock().unlock();
      }
   }

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   public Object getProperty(String key) {
      return prop.get(key);
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   public void setProperty(String key, Object value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * Get all the property keys.
    * @return all the property keys.
    */
   public Enumeration getProperties() {
      return prop.keys();
   }

   /**
    * Copy properties to target columns.
    */
   public void copyPropertiesTo(ColumnSelection columns) {
      columns.prop = (Properties) prop.clone();
   }

   @Override
   public int hashCode() {
      return attrs.hashCode();
   }

   private final List<DataRef> attrs; // keep the order of the attributes
   private Properties prop = new Properties(); // properties
   private ReadWriteLock lock = new ReentrantReadWriteLock();
}
