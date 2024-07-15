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

import inetsoft.uql.asset.DependencyException;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;
import java.util.*;

/**
 * An XEntity represents a business logic object in a data model. Entities
 * contain a collection of attributes which describe that object.
 *
 * @author  InetSoft Technology Corp.
 * @since   4.4
 */
public class XEntity implements Cloneable, Serializable, Comparable<XEntity>, XMLSerializable {
   /**
    * Creates a new instance of XEntity. Default constructor that should only
    * be used when loading the entity from an XML file.
    */
   public XEntity() {
      super();
      attributes = new OrderedMap<>();
      listeners = new ArrayList<>();
      attributeListener = new AttributePropertyListener();
   }

   /**
    * Creates a new instance of XEntity with the specified name.
    *
    * @param name a human readable name for the entity. The name should allow
    *             a user to infer the entity's usage.
    */
   public XEntity(String name) {
      this();
      this.name = name;
   }

   /**
    * Creates a new instance of XEntity with the specified name.
    * @param base the base entity.
    */
   public XEntity(XEntity base) {
      this(base.getName());
      setBaseEntity(base);
   }

   /**
    * Sets the name of this entity. The name should be in a human-readable
    * format and allow a user to infer its usage.
    *
    * @param name the name of this entity.
    */
   public void setName(String name) {
      String oval = this.name;
      this.name = name;
      firePropertyChange(NAME, oval, name);
   }

   /**
    * Gets the name of this entity.
    *
    * @return the entity's name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the nameChanged of this entity.
    *
    * @param nameChanged the nameChanged of this entity.
    */
   public void setNameChanged(boolean nameChanged) {
      this.nameChanged = nameChanged;
   }

   /**
    * Gets the nameChanged of this entity.
    *
    * @return the entity's nameChanged.
    */
   public boolean isNameChanged() {
      return nameChanged;
   }

   /**
    * Sets the oldName of this entity.
    *
    * @param oldName the oldName of this entity.
    */
   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   /**
    * Gets the old name of this entity.
    *
    * @return the entity's oldName.
    */
   public String getOldName() {
      return oldName;
   }

   /**
    * Sets the description for this entity. The description should tell a user
    * the intended usage of this entity and the type of information it provides.
    *
    * @param description a description of this entity.
    */
   public void setDescription(String description) {
      String oval = this.description;
      this.description = description;
      firePropertyChange(DESCRIPTION, oval, description);
   }

   /**
    * Gets a description of this entity. The description contains information
    * on the intended usage of this entity and the type of data it provides.
    *
    * @return a description of this entity.
    */
   public String getDescription() {
      return base != null ? base.description : description;
   }

   /**
    * Adds the specified attribute to this entity.
    *
    * @param attribute the attribute object to add.
    */
   public void addAttribute(XAttribute attribute) {
      addAttribute(attributes.size(), attribute);
   }

   /**
    * Add the attribute at the specified position.
    */
   public void addAttribute(int idx, XAttribute attribute) {
      String name = attribute.getName();

      if(!isBaseAttribute(name) && !attributes.containsKey(name)) {
         if(base == null) {
            attributes.put(idx, name, attribute);
         }
         else {
            if(attributesOrders.size() == 0) {
               attributesOrders.addAll(base.attributes.keyList());
               attributesOrders.addAll(attributes.keyList());
            }

            attributes.put(name, attribute);
         }

         if(attributesOrders.size() > 0) {
            attributesOrders.add(idx, name);
         }

         attribute.addPropertyChangeListener(attributeListener);
         fireAttributeAdded(name);
      }
      else if(isBaseAttribute(name) && !attributes.containsKey(name)) {
         attributesOrders.add(idx, name);
      }
   }

   /**
    * Check if the specified attribute is from base entity.
    */
   public boolean isBaseAttribute(String name) {
      return base != null && base.attributes.containsKey(name);
   }

   /**
    * Update an attribute in this entity.
    *
    * @param oname the original name of the attribute.
    * @param attribute the attribute that has been modified.
    *
    * @since 6.0
    */
   public void updateAttribute(String oname, XAttribute attribute) {
      boolean exists = oname != null && attributes.containsKey(oname);

      if(exists) {
         String nname = attribute.getName();
         int idx = attributes.indexOf(oname);
         XAttribute attr = attributes.remove(idx);
         attr.removePropertyChangeListener(attributeListener);
         attribute.setBrowseable(attr.isBrowseable());
         attribute.setBrowseDataQuery(attr.getBrowseDataQuery());
         attribute.setXMetaInfo(attr.getXMetaInfo());
         attributes.put(idx, nname, attribute);
         attribute.addPropertyChangeListener(attributeListener);

         if(!nname.equals(oname)) {
            idx = invisibleAttributes.indexOf(oname);

            if(idx != -1) {
               invisibleAttributes.set(idx, nname);
            }

            idx = attributesOrders.indexOf(oname);

            if(idx != -1) {
               attributesOrders.set(idx, nname);
            }

            fireAttributeRenamed(oname, nname);
         }
      }
      else {
         addAttribute(attribute);
      }
   }

   /**
    * Removes the attribute with the specified name from this entity.
    *
    * @param name the name of the attribute to remove.
    */
   public void removeAttribute(String name) {
      int index = getAttributeIndex(name);
      XAttribute attr = attributes.remove(name);
      invisibleAttributes.remove(name);
      attributesOrders.remove(name);
      attr.removePropertyChangeListener(attributeListener);
      fireAttributeRemoved(name, index);
   }

   /**
    * Gets the attribute of this entity with the specified name.
    *
    * @param name the name of the attribute to find.
    * @return the attribute object or <code>null</code> if this entity does not
    *         contain an attribute with the specified name.
    */
   public XAttribute getAttribute(String name) {
      return isBaseAttribute(name) ? base.attributes.get(name) : attributes.get(name);
   }

   public XAttribute getAttributeByOldName(String oname) {
      XAttribute attr = null;

      if(base != null && base.attributes != null) {
         attr = getAttributeByOldName(oname, base.attributes.elements());
      }

      if(attr == null) {
         attr = getAttributeByOldName(oname, attributes.elements());
      }

      return attr;
   }

   private XAttribute getAttributeByOldName(String oname, Enumeration<XAttribute> enu) {
      enu = attributes.elements();

      while(enu.hasMoreElements()) {
         XAttribute attr = enu.nextElement();

         if(attr != null && Tool.equals(oname, attr.getOldName())) {
            return attr;
         }
      }

      return null;
   }

   /**
    * Get the position of the attribute within the entity.
    */
   public int getAttributeIndex(String name) {
      if(attributesOrders.size() != 0) {
         return attributesOrders.indexOf(name);
      }

      return isBaseAttribute(name) ? base.attributes.indexOf(name) :
         (base == null ? 0 : base.attributes.size()) + attributes.indexOf(name);
   }

   /**
    * Gets all attributes contained in this entity.
    *
    * @return an Enumeration of XAttribute objects.
    */
   public Enumeration<XAttribute> getAttributes() {
      final int count = getAttributeCount();

      return new Enumeration<XAttribute>() {
         int i = -1;
         XAttribute attribute;

         @Override
         public boolean hasMoreElements() {
            i++;

            while(i < count) {
               attribute = getAttributeAt(i);

               if(attribute == null ||
                  runtime && invisibleAttributes.contains(attribute.getName()))
               {
                  i++;
                  continue;
               }

               return true;
            }

            return false;
         }

         @Override
         public XAttribute nextElement() {
            return attribute;
         }
      };
   }

   /**
    * Check if contains an attribute.
    * @param name the specified attribute name.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsAttribute(String name) {
      return isBaseAttribute(name) || attributes.containsKey(name);
   }

   /**
    * Get the number of attributes contained in this entity.
    *
    * @return the number of attributes contained in this entity.
    */
   public int getAttributeCount() {
      return attributes.size() + (base == null ? 0 : base.attributes.size());
   }

   /**
    * Get the attribute at the specified index.
    *
    * @param idx the index of the attribute.
    * @return the requested attribute.
    */
   public XAttribute getAttributeAt(int idx) {
      if(attributesOrders.size() != 0) {
         String name = attributesOrders.get(idx);
         return isBaseAttribute(name) ? base.attributes.get(name) : attributes.get(name);
      }
      else if(base != null && idx < base.attributes.size()) {
         return base.attributes.getValue(idx);
      }
      else {
         idx = idx - (base == null ? 0 : base.attributes.size());
         return attributes.getValue(idx);
      }
   }

   /**
    * move up or down the attribute with the specified name.
    *
    * @param name the name of the attribute to move.
    * @param up the direction true: up.
    */
   public boolean moveAttribute(String name, boolean up) {
      if(base == null) {
         int oidx = attributes.indexOf(name);
         int idx = oidx + (up ? -1 : 1);

         if(idx >= 0 && idx < attributes.size()) {
            fireAttributeRemoved(name, oidx);
            final XAttribute val = attributes.remove(oidx);
            attributes.put(idx, name, val);
            fireAttributeAdded(name);
            return true;
         }
      }
      else {
         if(attributesOrders.size() == 0) {
            attributesOrders.addAll(base.attributes.keyList());
            attributesOrders.addAll(attributes.keyList());
         }

         int oidx = attributesOrders.indexOf(name);
         int idx = oidx + (up ? -1 : 1);

         if(idx >= 0 && idx < attributesOrders.size()) {
            fireAttributeRemoved(name, oidx);
            attributesOrders.remove(oidx);
            attributesOrders.add(idx, name);
            fireAttributeAdded(name);
            return true;
         }
      }

      return false;
   }

   /**
    * Sorts the attributes by their names in alphabetical order.
    *
    * @since 8.0
    */
   public void sortAttributes() {
      if(base == null) {
         String[] onames = new String[attributes.size()];
         String[] names = new String[attributes.size()];
         Iterator<String> it = attributes.keySet().iterator();

         for(int i = 0; it.hasNext() && i < names.length; i++) {
            onames[i] = names[i] = it.next();
         }

         Arrays.sort(names);

         for(int i = 0; i < names.length; i++) {
            if(!names[i].equals(onames[i])) {
               XAttribute attr = getAttribute(names[i]);
               removeAttribute(names[i]);
               addAttribute(i, attr);
            }
         }
      }
      else {
         if(attributesOrders.size() == 0) {
            attributesOrders.addAll(base.attributes.keyList());
            attributesOrders.addAll(attributes.keyList());
         }

         String[] names = new String[attributesOrders.size()];
         attributesOrders.toArray(names);
         Arrays.sort(names);

         for(int i = 0; i < names.length; i++) {
            String name = names[i];

            if(!name.equals(attributesOrders.get(i))) {
               int oidx = attributesOrders.indexOf(name);
               fireAttributeRemoved(name, oidx);
               attributesOrders.remove(oidx);
               attributesOrders.add(i, name);
               fireAttributeAdded(name);
            }
         }
      }
   }

   /**
    * Determines if this entity contains an attribute that is mapped to the
    * specified column.
    *
    * @param table the name of the table.
    * @param column the name of the column.
    */
   public boolean containsColumn(String table, String column) {
      boolean result = false;
      Enumeration<XAttribute> e = getAttributes();

      while(e.hasMoreElements()) {
         XAttribute attr = e.nextElement();

         if(attr.getTable().equals(table) && attr.getColumn().equals(column)) {
            result = true;
            break;
         }
      }

      return result;
   }

   /**
    * Check if is base entity.
    */
   public boolean isBaseEntity() {
      if(entity_type == BASE_ENTITY) {
         return true;
      }

      return base != null;
   }

   /**
    * Gets the location at which this entity should be rendered in the data
    * model designer.
    *
    * @return the upper-left corner of this entity.
    */
   public Point getLocation() {
      return base != null ? base.location : location;
   }

   /**
    * Sets the location at which this entity should be rendered in the data
    * model designer.
    *
    * @param location the upper-left corner of this entity.
    */
   public void setLocation(Point location) {
      Point oval = this.location;
      this.location = location;
      firePropertyChange(LOCATION, oval, location);
   }

   /**
    * Get all dependencies for the attributes.
    */
   public Object[] getOuterDepencies() {
      Iterator<XAttribute> attrs = attributes.values().iterator();
      Set<Object> allDependencies = new HashSet<>();

      while(attrs.hasNext()) {
         XAttribute attr = attrs.next();
         allDependencies.addAll(Arrays.asList(attr.getOuterDependencies()));
      }

      return allDependencies.toArray(new Object[0]);
   }

   /**
    * Return xEntity's all outer dependencies.
    */
   public DependencyException getDependencyException() {
      Object[] depencies = getOuterDepencies();

      if(depencies.length == 0) {
         return null;
      }

      DependencyException ex = new DependencyException(this);
      ex.addDependencies(depencies);

      return ex;
   }

   /**
    * Writes the XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<entity name=\"" + Tool.escape(getName()) + "\" ");

      if(!visible) {
         writer.print(" visible=\"" + visible + "\" ");
      }

      writer.print(" base=\"" + (base != null) + "\" ");

      if(base == null) {
         writer.print(" x=\"" + location.x + "\" y=\"" + location.y + "\"");
      }

      writer.println(">");

      if(description != null) {
         writer.print("<description>");
         writer.print("<![CDATA[");
         writer.print(description);
         writer.print("]]>");
         writer.println("</description>");
      }

      StringBuilder buffer = new StringBuilder();

      for(String name : invisibleAttributes) {
         buffer.append(name + ",");
      }

      if(buffer.length() > 0) {
         writer.println("<invisible names=\"" +
            Tool.escape(buffer.substring(0, buffer.length() - 1)) + "\" />");
      }

      buffer.setLength(0);

      for(String name : attributesOrders) {
         buffer.append(name + ",");
      }

      if(buffer.length() > 0) {
         writer.println("<orders names=\"" +
            Tool.escape(buffer.substring(0, buffer.length() - 1)) + "\" />");
      }

      Iterator<XAttribute> e = attributes.values().iterator();

      while(e.hasNext()) {
         e.next().writeXML(writer);
      }

      writer.println("</entity>");
   }

   /**
    * Reads in an entity definition from its XML representation.
    *
    * @param tag the XML Element for this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      this.attributeListener = new AttributePropertyListener();

      String attr = null;
      int x = -1;
      int y = -1;

      if((attr = Tool.getAttribute(tag, "name")) != null) {
         setName(attr);
      }

      if((attr = Tool.getAttribute(tag, "x")) != null) {
         x = Integer.parseInt(attr);
      }
      else {
         entity_type = BASE_ENTITY;
      }

      if((attr = Tool.getAttribute(tag, "y")) != null) {
         y = Integer.parseInt(attr);
      }

      if(x >= 0 && y >= 0) {
         setLocation(new Point(x, y));
      }

      setVisible(!"false".equals(Tool.getAttribute(tag, "visible")));
      NodeList nl = Tool.getChildNodesByTagName(tag, "description");

      if(nl != null && nl.getLength() > 0) {
         setDescription(Tool.getValue(nl.item(0)));
      }

      Element node = Tool.getChildNodeByTagName(tag, "invisible");

      if(node != null) {
         String[] names = Tool.getAttribute(node, "names").split(",");

         for(String name : names) {
            invisibleAttributes.add(name);
         }
      }

      node = Tool.getChildNodeByTagName(tag, "orders");

      if(node != null) {
         String[] names = Tool.getAttribute(node, "names").split(",");

         for(String name : names) {
            attributesOrders.add(name);
         }
      }

      nl = Tool.getChildNodesByTagName(tag, "attribute");

      for(int i = 0; nl != null && i < nl.getLength(); i++) {
         Element elem = (Element) nl.item(i);
         XAttribute xattr = null;

         if((attr = Tool.getAttribute(elem, "class")) != null) {
            try {
               xattr = (XAttribute) Class.forName(attr).newInstance();
            }
            catch(Exception exc) {
               LOG.error("Failed to create attribute class: " + attr, exc);
               xattr = new XAttribute();
            }
         }
         else {
            xattr = new XAttribute();
         }

         xattr.parseXML((Element) nl.item(i));
         attributes.put(xattr.getName(), xattr);
      }
   }

   /**
    * Gets a textual representation of this entity. For user interface
    * purposes use the {@link #getName()} method.
    *
    * @return a string representation of this object. This value will have the
    *         format <CODE>XEntity: <I>entity name</I></CODE>.
    */
   public String toString() {
      return "XEntity: " + name;
   }

   /**
    * Creates and returns a copy of this entity object.
    * @return a clone of this instance.
    */
   @Override
   public Object clone() {
      try {
         XEntity copy = (XEntity) super.clone();
         copy.createAttributeListener();
         copy.location = (Point) location.clone();
         copy.setID(id);
         copy.listeners = new ArrayList<>();
         copy.attributes = new OrderedMap<>();
         copy.attributesOrders = Tool.deepCloneCollection(attributesOrders);
         copy.invisibleAttributes = Tool.deepCloneCollection(invisibleAttributes);
         Iterator<String> keys = attributes.keySet().iterator();

         while(keys.hasNext()) {
            String name = keys.next();
            XAttribute attr = attributes.get(name);;
            attr = (XAttribute) attr.clone();
            attr.addPropertyChangeListener(copy.attributeListener);
            copy.attributes.put(name, attr);
         }

         copy.setBaseEntity(base);

         return copy;
      }
      catch(CloneNotSupportedException cnse) {
         LOG.error("Failed to clone object", cnse);
      }

      return null;
   }

   /**
    * Returns a hash code value for the object.
    *
    * @return a hash code value for this object.
    */
   public int hashCode() {
      return name == null ? super.hashCode() : name.hashCode();
   }

   /*
    * Determines if this object is equivilent to another object.
    *
    * @param obj the reference object with which to compare.
    * @return <code>true</code> if the objects are are equivalent,
    *         <code>false</code> otherwise.
    */
   public boolean equals(Object obj) {
      return this == obj || name != null && obj instanceof XEntity &&
             name.equals(((XEntity) obj).name);
   }

   /**
    * Compare the entities by name.
    */
   @Override
   public int compareTo(XEntity obj) {
      return Tool.compare(name, obj.name);
   }

   /**
    * Add a listener that is notified of changes to this entity.
    *
    * @param l the listener.
    */
   void addEntityListener(EntityListener l) {
      if(!listeners.contains(l)) {
         listeners.add(l);
      }
   }

   /**
    * Remove a listener from the notification list.
    *
    * @param l the listener.
    */
   void removeEntityListener(EntityListener l) {
      listeners.remove(l);
   }

   /**
    * Remove all listeners from the notification list.
    */
   void removeAllEntityListeners() {
      listeners.clear();
   }

   /**
    * Determine if any of the attributes in this entity are mapped to the
    * specified table.
    *
    * @param table the name of the table.
    * @return <code>true</code> if this entity contains a mapping to the
    *         specified table; <code>false</code> otherwise.
    */
   public boolean isMappedToTable(String table) {
      Enumeration<XAttribute> e = getAttributes();

      while(e.hasMoreElements()) {
         XAttribute attr = e.nextElement();

         if(attr.getTable() != null && attr.getTable().equals(table)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determine if any of the attributes in this entity are mapped to the
    * specified column.
    *
    * @param table the name of the table.
    * @param column the name of the column.
    * @return <code>true</code> if this entity contains a mapping to the
    *         specified column; <code>false</code> otherwise.
    */
   public boolean isMappedToColumn(String table, String column) {
      Enumeration<XAttribute> e = getAttributes();

      while(e.hasMoreElements()) {
         XAttribute attr = e.nextElement();

         if(Tool.equals(attr.getTable(), table) &&
            Tool.equals(attr.getColumn(), column))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Add a listener that is notified when a property of this entity has
    * been changed.
    *
    * @param l the listener to add.
    */
   public void addPropertyChangeListener(PropertyChangeListener l) {
      propertyListeners.add(l);
   }

   /**
    * Remove a property change listener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removePropertyChangeListener(PropertyChangeListener l) {
      propertyListeners.remove(l);
   }

   /**
    * Removes all registered property change listeners from the notification
    * list.
    */
   public void removeAllPropertyChangeListeners() {
      propertyListeners.clear();
   }

   /**
    * Create the attribute property change listener. This method should only be
    * called from the clone() method.
    */
   private void createAttributeListener() {
      attributeListener = new AttributePropertyListener();
   }

   /**
    * Notify all registered listeners that a property has been changed.
    *
    * @param name the name of the property.
    * @param ovalue the old value.
    * @param nvalue the new value.
    */
   private void firePropertyChange(String name, Object ovalue, Object nvalue) {
      PropertyChangeEvent evt = null;
      Iterator<PropertyChangeListener> it = propertyListeners.iterator();

      while(it.hasNext()) {
         if(evt == null) {
            evt = new PropertyChangeEvent(this, name, ovalue, nvalue);
         }

         it.next().propertyChange(evt);
      }
   }

   /**
    * Notify all registered listeners that an attribute has been added.
    *
    * @param attribute the name of the attribute that was added.
    */
   private void fireAttributeAdded(String attribute) {
      EntityEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new EntityEvent(this, attribute);
         }

         (listeners.get(i)).attributeAdded(evt);
      }
   }


   /**
    * Notify all registered listeners that an attribute has been removed.
    *
    * @param attribute the name of the attribute that was removed.
    * @param attributeIndex the index of the attribute.
    */
   private void fireAttributeRemoved(String attribute, int attributeIndex) {
      EntityEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new EntityEvent(this, attribute, attributeIndex);
         }

         (listeners.get(i)).attributeRemoved(evt);
      }
   }

   /**
    * Notify all registered listeners that an attribute has been changed.
    *
    * @param attribute the name of the attribute that was changed.
    * @param property the name of the property.
    * @param oldValue the old value of the property.
    * @param newValue the new value of the property.
    */
   private void fireAttributeChanged(String attribute, String property,
                                     Object oldValue, Object newValue) {
      EntityEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new EntityEvent(this, attribute, property,
                                  oldValue, newValue);
         }

         (listeners.get(i)).attributeChanged(evt);
      }
   }

   /**
    * Notify all registered listeners that an attribute has been renamed.
    *
    * @param oattribute the original name of the attribute.
    * @param attribute the new name of the attribute.
    */
   private void fireAttributeRenamed(String oattribute, String attribute) {
      EntityEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new EntityEvent(this, attribute, oattribute);
         }

         (listeners.get(i)).attributeRenamed(evt);
      }
   }

   /**
    * Set base entity.
    */
   public void setBaseEntity(XEntity base) {
      this.base = base;
   }

   /**
    * Get base entity.
    */
   public XEntity getBaseEntity() {
      return base;
   }

   /**
    * validate.
    */
   public void validate() {
      if(base != null) {
         Iterator<XAttribute> attrs = base.attributes.values().iterator();

         while(attrs.hasNext()) {
            String name = attrs.next().getName();

            if(attributesOrders.size() != 0 && !attributesOrders.contains(name))
            {
               attributesOrders.add(name);
            }
         }

         StringBuilder buffer = new StringBuilder();

         for(int i = attributes.size() - 1; i >= 0; i--) {
            String name = attributes.getKey(i);

            if(base.attributes.containsKey(name)) {
               attributes.remove(i);
               buffer.append(name + ",");
            }
         }

         if(buffer.length() > 0) {
            LOG.warn(buffer + " exists in parent and has been removed");
         }
      }

      for(int i = invisibleAttributes.size() - 1; i >= 0; i--) {
         String name = invisibleAttributes.get(i);

         if(!containsAttribute(name)) {
            invisibleAttributes.remove(i);
         }
      }

      for(int i = attributesOrders.size() - 1; i >= 0; i--) {
         String name = attributesOrders.get(i);

         if(!containsAttribute(name)) {
            attributesOrders.remove(i);
         }
      }
   }

   /**
    * Set is runtime mode or not.
    */
   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   /**
    * Check the specified attribute is visible or not.
    * @param name the name of the specified attribute.
    */
   public boolean isAttributeVisible(String name) {
      return !invisibleAttributes.contains(name);
   }

   /**
    * Set the specified attribute is visible or not.
    * @param name the name of the specified attribute.
    */
   public void setAttributeVisible(String name, boolean visible) {
      if(visible) {
         invisibleAttributes.remove(name);
      }
      else if(!invisibleAttributes.contains(name)) {
         invisibleAttributes.add(name);
      }

      firePropertyChange("ENTITY_ATTRIBUTE_VISIBLE", !visible, visible);
   }

   /**
    * Check this entity is visible or not.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set this entity is visible or not.
    */
   public void setVisible(boolean visible) {
      boolean ovisible = this.visible;
      this.visible = visible;
      firePropertyChange("ENTITY_VISIBLE", ovisible, visible);
   }

   // Get the id for attribute so to update dependency.
   public String getID() {
      return id;
   }

   // Set the id for attribute so to update dependency.
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Property name of the name of an entity.
    */
   public static final String NAME = "ENTITY_NAME";

   /**
    * Property name of the description of an entity.
    */
   public static final String DESCRIPTION = "ENTITY_DESCRIPTION";

   /**
    * Property name of the location of an entity.
    */
   public static final String LOCATION = "ENTITY_LOCATION";

   private static final int BASE_ENTITY = 1;
   private static final int UNKNOWN_ENTITY = 2;

   private boolean visible = true;
   private int entity_type = UNKNOWN_ENTITY;
   private String name;
   private String id;
   private String oldName = "";
   private boolean nameChanged = false;
   private XEntity base;
   private String description;
   private List<String> invisibleAttributes = new ArrayList<>();
   private List<String> attributesOrders = new ArrayList<>();
   private boolean runtime;
   private OrderedMap<String, XAttribute> attributes;
   private Point location = new Point(-1, -1);
   private AttributePropertyListener attributeListener = null;
   private transient ArrayList<EntityListener> listeners;
   private transient Set<PropertyChangeListener> propertyListeners = new HashSet<>();

   private class AttributePropertyListener implements PropertyChangeListener,
                                                      Serializable {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
         if(evt.getPropertyName().equals(XAttribute.NAME)) {
            // attribute name is handled with attributeRenamed
            return;
         }

         XAttribute attr = (XAttribute) evt.getSource();
         fireAttributeChanged(attr.getName(), evt.getPropertyName(),
                              evt.getOldValue(), evt.getNewValue());
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XEntity.class);
}
