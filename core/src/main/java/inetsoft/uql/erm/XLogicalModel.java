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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XFactory;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.XVariable;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.xml.XMLStorage.XMLFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A business logic view of a relational database model.
 *
 * @author  InetSoft Technology Corp.
 * @since   6.0
 */
public class XLogicalModel
   implements Cloneable, Serializable, Comparable<XLogicalModel>, XMLSerializable,
   XMLFragment
{
   /**
    * Create a new instance of XLogicalModel.
    */
   public XLogicalModel() {
      super();
      init();
   }

   /**
    * Create a new instance of XLogicalModel.
    *
    * @param name the name of the model.
    */
   public XLogicalModel(String name) {
      init();
      this.name = name;
   }

   /**
    * Init variables.
    */
   private void init() {
      this.entities = new OrderedMap<>();
      this.listeners = new ArrayList<>();
      this.createEntityListener();
      this.createEntityPropertyListener();
      this.varmap = new OrderedMap<>();
   }

   /**
    * Create a new instance of XLogicalModel.
    *
    * @param name the name of the model.
    * @param base the base model
    */
   public XLogicalModel(String name, XLogicalModel base) {
      this(name);
      setBaseModel(base);
   }

   private XLogicalModel getModel() {
      return base != null ? base : this;
   }

   /**
    * Set the datasource name of this logical model.
    * @deprecated
    * @param datasource the datasource name.
    */
   @Deprecated
   public void setDataSource(String datasource) {
   }

   /**
    * Get the datasource name of this logical model.
    *
    * @return datasource the datasource name.
    */
   public String getDataSource() {
      return getDataModel().getDataSource();
   }

   /**
    * Get the count of entity.
    *
    * @return the count of entity.
    */
   public int getEntityCount() {
      return entities.size();
   }

   /**
    * Add an entity to this model.
    *
    * @param entity the entity to add.
    */
   public void addEntity(XEntity entity) {
      addEntity(-1, entity);
   }

   /**
    * Add an entity to this model.
    *
    * @param entity the entity to add.
    */
   public void addEntity(int index, XEntity entity) {
      String name = entity.getName();

      if(!entities.containsKey(name)) {
         if(index == -1) {
            entities.put(name, entity);
         }
         else {
            entities.put(index, name, entity);
         }

         entity.addPropertyChangeListener(entityPropertyListener);
         fireEntityAdded(name);
         entity.addEntityListener(entityListener);
      }
   }

   /**
    * Update an entity to this model.
    *
    * @param oname old entity name.
    * @param entity the entity to add.
    */
   public void updateEntity(String oname, XEntity entity) {
      boolean exists = oname != null && entities.containsKey(oname);

      if(exists) {
         int idx = entities.indexOf(oname);
         XEntity ent = entities.remove(idx);
         ent.removeEntityListener(entityListener);
         ent.removePropertyChangeListener(entityPropertyListener);
         entities.put(idx, entity.getName(), entity);
         entity.addPropertyChangeListener(entityPropertyListener);

         if(!entity.getName().equals(oname)) {
            fireEntityRenamed(entity.getName(), oname);
         }

         entity.addEntityListener(entityListener);
      }
      else {
         addEntity(entity);
      }
   }

   /**
    * Get the entity with the specified name.
    *
    * @param name the name of the entity.
    *
    * @return an entity or <code>null</code> if no entity with the specified
    *         name exists.
    */
   public XEntity getEntity(String name) {
      return entities.get(name);
   }

   /**
    * Determine if the entity is from the base model.
    */
   public boolean isBaseEntity(String name) {
      return base != null && base.entities.containsKey(name);
   }

   /**
    * Get the base entity with the specified name.
    *
    * @param name the name of the entity.
    *
    * @return an base entity or <code>null</code> if no base entity with the specified
    *         name exists.
    */
   public XEntity getBaseEntity(String name) {
      XEntity xEntity = null;

      if(base != null) {
         xEntity = base.entities.get(name);
      }

      return xEntity;
   }

   /**
    * Get a list of all entities in this model.
    *
    * @return an Enumeration that contains all the entities in this model.
    */
   public Enumeration<XEntity> getEntities() {
      final Iterator<XEntity> ite = entities.values().iterator();

      return new Enumeration<XEntity>() {
         @Override
         public boolean hasMoreElements() {
            while(ite.hasNext()) {
               entity = ite.next();

               if(entity == null || runtime && !entity.isVisible()) {
                  continue;
               }

               return true;
            }

            return false;
         }

         @Override
         public XEntity nextElement() {
            return entity;
         }

         XEntity entity;
      };
   }

   /**
    * Get specified entity by index from this model.
    *
    * @param idx the index of the entity to get.
    */
   public XEntity getEntityAt(int idx) {
      return entities.getValue(idx);
   }

   /**
    * Remove the entity with the specified name from this model.
    *
    * @param name the name of the entity to remove.
    */
   public void removeEntity(String name) {
      XEntity entity = entities.remove(name);

      if(entity != null) {
         entity.removeEntityListener(entityListener);
         entity.removePropertyChangeListener(entityPropertyListener);
         fireEntityRemoved(name);
      }
   }

   public String getName() {
      return name;
   }

   /**
    * Set the name of this model.
    *
    * @param name the model name.
    */
   public void setName(String name) {
      this.name = name;

      Enumeration<XVariable> vs = varmap.elements();

      while(vs.hasMoreElements()) {
         XVariable var = vs.nextElement();

         var.setSource(name);
      }
   }

   /**
    * Get a connection of this model.
    *
    * @return a connection.
    */
   public String getConnection() {
      return connection;
   }

   /**
    * Set the connection of this model.
    *
    * @param connection connection of this model.
    */
   public void setConnection(String connection) {
      this.connection = connection;
   }

   /**
    * Get a description of this model.
    *
    * @return a description.
    */
   public String getDescription() {
      return getModel().description;
   }

   /**
    * Set the description of this model.
    *
    * @param description a description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Add a listener that is notified when this model has been changed.
    *
    * @param l the listener to add.
    */
   public void addDataModelListener(DataModelListener l) {
      String name = l.getClass().getName();

      for(int i = 0; i < listeners.size(); i++) {
         if(name.equals(listeners.get(i).getClass().getName())) {
            listeners.set(i, l);
            return;
         }
      }

      listeners.add(l);
   }

   /**
    * Remove a DataModelListener from the notification list.
    *
    * @param l the listener to remove.
    */
   public void removeDataModelListener(DataModelListener l) {
      listeners.remove(l);
   }

   /**
    * Get the priority of this model.
    *
    * @return the priority of this model.
    */
   public int getPriority() {
      return 0;
   }

   /**
    * Set the priority of this model.
    *
    * @param priority the priority of this model.
    */
   public void setPriority(int priority) {
   }

   /**
    * Get a list of all entities in this model that are mapped to the specified
    * table.
    *
    * @param table the name of the table.
    *
    * @return an Enumeration of XEntity objects.
    */
   public Enumeration<XEntity> getEntitiesForTable(String table) {
      return new TableEntityEnumeration(table);
   }

   /**
    * Get the entity in this model that is mapped to the specified column.
    *
    * @param table the name of the table.
    * @param column the name of the column.
    *
    * @return the entity or <code>null</code> if no entity in this model is
    *         mapped to the specified column.
    */
   public XEntity getEntityForColumn(String table, String column) {
      Enumeration<XEntity> e = getEntities();

      while(e.hasMoreElements()) {
         XEntity entity = e.nextElement();

         if(entity.isMappedToColumn(table, column)) {
            return entity;
         }
      }

      return null;
   }

   /**
    * Get the names of all variables need to be defined in this query.
    */
   public Enumeration<String> getDefinedVariables() {
      try {
         return getDefinedVariables(
            XFactory.getRepository().getDataModel(getDataSource()));
      }
      catch(Exception exc) {
         LOG.error("Failed to get defined variables, unable to " +
            "get parent data model", exc);
         return null;
      }
   }

   /**
    * Get the names of all variables need to be defined in this query.
    */
   public Enumeration<String> getDefinedVariables(XDataModel parent) {
      return getVariableNames(parent);
   }

   /**
    * Get the names of all variables used in this query. The variables
    * are either UserVariable or QueryVariable.
    */
   public Enumeration<String> getVariableNames() {
      try {
         return getVariableNames(
            XFactory.getRepository().getDataModel(getDataSource()));
      }
      catch(Exception exc) {
         LOG.error("Failed to get variable names, unable to " +
            "get parent data model", exc);
         return null;
      }
   }

   /**
    * Get the names of all variables used in this query. The variables
    * are either UserVariable or QueryVariable.
    */
   private Enumeration<String> getVariableNames(XDataModel parent) {
      return getModel().varmap.keys();
   }

   /**
    * Get a variable defined in this query.
    * @param name variable name.
    * @return variable definition.
    */
   public XVariable getVariable(String name) {
      return getModel().varmap.get(name);
   }

   /**
    * Add a variable to this query.
    * @param var variable definition.
    */
   public void addVariable(XVariable var) {
      var.setSource(getName());
      varmap.put(var.getName(), var);
   }

   /**
    * Remove a variable from this query.
    * @param name variable name.
    */
   public void removeVariable(String name) {
      varmap.remove(name);
   }


   /**
    * Notifies all registered listeners that an entity has been added.
    *
    * @param entity the name of the entity.
    */
   private void fireEntityAdded(String entity) {
      DataModelEvent evt = null;

      for(int i = listeners.size() - 1; i >= 0; i--) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, null);
         }

         listeners.get(i).entityAdded(evt);
      }
   }

   /**
    * Notifies all registered listeners that an entity has been removed.
    *
    * @param entity the name of the entity.
    */
   private void fireEntityRemoved(String entity) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, null);
         }

         listeners.get(i).entityRemoved(evt);
      }
   }

   /**
    * Notifies all registered listeners that an entity has been renamed.
    *
    * @param entity the new name of the entity.
    * @param oentity the original name of the entity.
    */
   private void fireEntityRenamed(String entity, String oentity) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, null, oentity, null);
         }

         listeners.get(i).entityRenamed(evt);
      }
   }

   /**
    * Notifies all registered listeners that a property of an entity has
    * been changed.
    *
    * @param entity the name of the entity.
    * @param property the name of the property.
    * @param oldValue the old value of the property.
    * @param newValue the new value of the property.
    */
   private void fireEntityChanged(String entity, String property,
                                  Object oldValue, Object newValue) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, property, oldValue,
                                     newValue);
         }

         listeners.get(i).attributeChanged(evt);
      }
   }

   /**
    * Notifies all registered listeners that an attribute has been added.
    *
    * @param entity the name of the entity.
    * @param attribute the name of the attribute.
    */
   private void fireAttributeAdded(String entity, String attribute) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, attribute);
         }

         listeners.get(i).attributeAdded(evt);
      }
   }

   /**
    * Notifies all registered listeners that an attribute has been removed.
    *
    * @param entity the name of the entity.
    * @param attribute the name of the attribute.
    * @param attributeIndex the index of the attribute.
    */
   private void fireAttributeRemoved(String entity, String attribute,
                                     int attributeIndex) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, attribute, attributeIndex);
         }

         listeners.get(i).attributeRemoved(evt);
      }
   }

   /**
    * Notifies all registered listeners that an attribute has been renamed.
    *
    * @param entity the name of the entity.
    * @param attribute the new name of the attribute.
    * @param oattribute the original name of the attribute.
    */
   private void fireAttributeRenamed(String entity, String attribute,
                                     String oattribute) {
      DataModelEvent evt = null;

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, attribute, null, oattribute);
         }

         listeners.get(i).attributeRenamed(evt);
      }
   }

   /**
    * Notifies all registered listeners that a property of an attribute has
    * been changed.
    *
    * @param xentity the name of the entity.
    * @param attribute the new name of the attribute.
    * @param property the name of the property.
    * @param oldValue the old value of the property.
    * @param newValue the new value of the property.
    */
   private void fireAttributeChanged(XEntity xentity, String attribute,
                                     String property, Object oldValue,
                                     Object newValue) {
      DataModelEvent evt = null;

      XAttribute attr = xentity.getAttribute(attribute);
      String type = attr.getDataType();
      String entity = xentity.getName();

      for(int i = 0; i < listeners.size(); i++) {
         if(evt == null) {
            evt = new DataModelEvent(this, entity, attribute, property,
                                     oldValue, newValue);
         }

         listeners.get(i).attributeChanged(evt);
      }
   }

   /**
    * Create the entity listener. This method should only be called from the
    * clone() method.
    */
   private void createEntityListener() {
      entityListener = new EntityListenerImpl();
   }

   /**
    * Create the entity property change listener. This method should only be
    * called from the clone() method.
    */
   private void createEntityPropertyListener() {
      entityPropertyListener = new EntityPropertyListener();
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @SuppressWarnings("unchecked")
   @Override
   public Object clone() {
      try {
         XLogicalModel copy = (XLogicalModel) super.clone();

         if(entities != null) {
            copy.createEntityListener();
            copy.createEntityPropertyListener();
            copy.entities = new OrderedMap<>();

            for(String name : entities.keySet()) {
               XEntity entity = entities.get(name);
               entity = (XEntity) entity.clone();
               entity.removeAllEntityListeners();
               entity.addEntityListener(copy.entityListener);
               entity.removeAllPropertyChangeListeners();
               entity.addPropertyChangeListener(copy.entityPropertyListener);
               copy.entities.put(name, entity);
            }

            copy.varmap = (OrderedMap<String, XVariable>) varmap.clone();
            copy.setBaseModel(base);
            copy.dependencies = ConcurrentHashMap.newKeySet();
            copy.dependencies.addAll(dependencies);
         }

         return copy;
      }
      catch(CloneNotSupportedException cnse) {
         LOG.error("Failed to clone object", cnse);
      }

      return null;
   }

   /**
    * Determine if the specified object is equivalent to this object.
    *
    * @param obj the object to compare.
    *
    * @return <code>true</code> if the objects are equivalent.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof XLogicalModel)) {
         return false;
      }

      XLogicalModel lmodel = (XLogicalModel) obj;
      return Tool.equals(getDataSource(), lmodel.getDataSource()) &&
             Tool.equals(this.connection, lmodel.connection) &&
             Tool.equals(this.name, lmodel.name);
   }

   /**
    * Compares this object with the specified object for order. Returns a
    * negative integer, zero, or a positive integer as this object is less
    * than, equal to, or greater than the specified object.
    *
    * @param obj the Object to be compared.
    *
    * @return a negative integer, zero, or a positive integer as this object is
    *         less than, equal to, or greater than the specified object.
    */
   @Override
   public int compareTo(XLogicalModel obj) {
      if(getPriority() < obj.getPriority()) {
         return -1;
      }
      else if(getPriority() > obj.getPriority()) {
         return 1;
      }

      return 0;
   }

   /**
    * Get the physical model which this logical model represents.
    *
    * @return the name of the partition.
    */
   public String getPartition() {
      return base != null ? base.partition : partition;
   }

   /**
    * Set the physical model which this logical model represents.
    *
    * @param partition the name of the partition.
    */
   public void setPartition(String partition) {
      if(base == null) {
         this.partition = partition;
      }
   }

   /**
    * Get the logical model order.
    *
    * @return if entities sort default order.
    */
   public boolean getEntityOrder() {
      return entityOrder;
   }

   /**
    * Set the logical model order.
    *
    * @param entityOrder the value of the entityOrder.
    */
   public void setEntityOrder(boolean entityOrder) {
      this.entityOrder = entityOrder;
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   public Object[] getOuterDependencies() {
      return dependencies.toArray(new Object[0]);
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addOuterDependency(Object entry) {
      return dependencies.add(entry);
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeOuterDependency(Object entry) {
      return dependencies.remove(entry);
   }

   /**
    * Remove all the outer dependencies.
    */
   public void removeOuterDependencies() {
      dependencies.clear();
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeStart(writer);
      writeEnd(writer);
   }

   @Override
   public void writeStart(PrintWriter writer) {
      writer.print("<LogicalModel name=\"" + Tool.escape(getName()) + "\"");

      if(folder != null) {
         writer.print(" folder=\"" + Tool.escape(folder) + "\"");
      }

      if(partition != null) {
         writer.print(" partition=\"" + Tool.escape(partition) + "\"");
      }

      if(connection != null) {
         writer.print(" connection=\"" + Tool.escape(connection) + "\"");
      }

      writer.print(" defaultOrder=\"" + getEntityOrder() + "\"");
      writer.println(">");

      if(createdBy != null) {
         writer.println("<createdBy>");
         writer.print("<![CDATA[");
         writer.print(createdBy);
         writer.println("]]>");
         writer.println("</createdBy>");
      }

      if(modifiedBy != null) {
         writer.println("<modifiedBy>");
         writer.print("<![CDATA[");
         writer.print(modifiedBy);
         writer.println("]]>");
         writer.println("</modifiedBy>");
      }

      if(created != 0) {
         writer.println("<created>");
         writer.print("<![CDATA[");
         writer.print(created);
         writer.println("]]>");
         writer.println("</created>");
      }

      if(modified != 0) {
         writer.println("<modified>");
         writer.print("<![CDATA[");
         writer.print(modified);
         writer.println("]]>");
         writer.println("</modified>");
      }

      if(description != null) {
         writer.println("<Description>");
         writer.print("<![CDATA[");
         writer.print(description);
         writer.println("]]>");
         writer.println("</Description>");
      }

      for(XEntity entity : entities.values()) {
         entity.writeXML(writer);
      }

      for(String vname : varmap.keySet()) {
         try {
            XVariable var = getVariable(vname);
            var.writeXML(writer);
         }
         catch(Exception ex) {
            LOG.error("Unable to write variable: " + vname, ex);
         }
      }

      Object[] entries = getOuterDependencies();

      if(entries.length > 0) {
         writer.println("<dependencies>");

         for(Object entry : entries) {
            if(entry instanceof AssetEntry) {
               ((AssetEntry) entry).writeXML(writer);
            }
         }

         writer.println("</dependencies>");
      }
   }

   @Override
   public void writeEnd(PrintWriter writer) {
      writer.println("</LogicalModel>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      // @by jasons 2003-09-24
      // instantiate these class members because this instance was probably
      // created with the default constructor
      init();
      String val = null;

      if((val = Tool.getAttribute(tag, "name")) != null) {
         setName(val);
      }

      if((val = Tool.getAttribute(tag, "folder")) != null) {
         setFolder(val);
      }

      connection = Tool.getAttribute(tag, "connection");

      if((val = Tool.getAttribute(tag, "partition")) != null) {
         setPartition(val);
      }

      setEntityOrder(!"false".equals(Tool.getAttribute(tag, "defaultOrder")));

      NodeList list = Tool.getChildNodesByTagName(tag, "createdBy");

      if(list != null && list.getLength() > 0) {
         createdBy = Tool.getValue(list.item(0));
      }

      list = Tool.getChildNodesByTagName(tag, "modifiedBy");

      if(list != null && list.getLength() > 0) {
         modifiedBy = Tool.getValue(list.item(0));
      }

      list = Tool.getChildNodesByTagName(tag, "created");

      if(list != null && list.getLength() > 0) {
         created = Long.parseLong(Tool.getValue(list.item(0)));
      }

      list = Tool.getChildNodesByTagName(tag, "modified");

      if(list != null && list.getLength() > 0) {
         modified = Long.parseLong(Tool.getValue(list.item(0)));
      }

      list = Tool.getChildNodesByTagName(tag, "Description");

      if(list != null && list.getLength() > 0) {
         list = list.item(0).getChildNodes();
         StringBuilder sb = new StringBuilder();

         for(int i = 0; list != null && i < list.getLength(); i++) {
            sb.append(list.item(i).getNodeValue());
         }

         setDescription(sb.toString().trim());
      }

      list = Tool.getChildNodesByTagName(tag, "entity");

      for(int i = 0; list != null && i < list.getLength(); i++) {
         XEntity entity = new XEntity();
         entity.parseXML((Element) list.item(i));
         addEntity(entity);
      }

      NodeList nlist = Tool.getChildNodesByTagName(tag, "variable");

      for(int i = 0; i < nlist.getLength(); i++) {
         try {
            XVariable var = XVariable.parse((Element) nlist.item(i));

            if(var != null && var.getName() != null) {
               varmap.put(var.getName(), var);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to parse variable", ex);
         }
      }

      Element dsnode = Tool.getChildNodeByTagName(tag, "dependencies");

      if(dsnode != null) {
         list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

         for(int i = 0; list != null && i < list.getLength(); i++) {
            Element anode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(anode);
            this.dependencies.add(entry);
         }
      }
   }

   /**
    * Implementation of EntityListener used to forward attribute-related events
    * to the registered DataModelListeners. An anonymous inner class is not
    * used because an instance should not be created for VirtualPrivateModels.
    */
   private class EntityListenerImpl implements EntityListener, Serializable {
      @Override
      public void attributeAdded(EntityEvent evt) {
         fireAttributeAdded(evt.getEntity().getName(), evt.getAttribute());
      }

      @Override
      public void attributeRemoved(EntityEvent evt) {
         fireAttributeRemoved(evt.getEntity().getName(), evt.getAttribute(),
                              evt.getAttributeIndex());
      }

      @Override
      public void attributeRenamed(EntityEvent evt) {
         fireAttributeRenamed(evt.getEntity().getName(), evt.getAttribute(),
                              evt.getOriginalAttribute());
      }

      @Override
      public void attributeChanged(EntityEvent evt) {
         fireAttributeChanged(evt.getEntity(), evt.getAttribute(),
                              evt.getPropertyName(), evt.getOldValue(),
                              evt.getNewValue());
      }
   }

   private class EntityPropertyListener implements PropertyChangeListener,
                                                   Serializable {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
         if(evt.getPropertyName().equals(XEntity.NAME)) {
            // handled by the entityRenamed event
            return;
         }

         fireEntityChanged(((XEntity) evt.getSource()).getName(),
                           evt.getPropertyName(), evt.getOldValue(),
                           evt.getNewValue());
      }
   }

   private class TableEntityEnumeration implements Enumeration<XEntity> {
      public TableEntityEnumeration(String table) {
         e = getEntities();
         entities = new HashSet<>();
         this.table = table;

         // @by jasons 2003-09-23, look-ahead to the first value
         while(e.hasMoreElements()) {
            XEntity entity = e.nextElement();

            if(entity.isMappedToTable(table)) {
               current = entity;
               entities.add(current);
            }
         }
      }

      @Override
      public boolean hasMoreElements() {
         return current != null;
      }

      @Override
      public XEntity nextElement() {
         if(current == null) {
            return null;
         }

         XEntity result = current;
         current = null;

         // @by jasons 2003-09-23
         // look-ahead for the next entity that has not already been returned.
         // if all remaining entities have already been returned, set current to
         // null
         while(e.hasMoreElements()) {
            current = e.nextElement();

            if(current.isMappedToTable(table)) {
               entities.add(current);
               break;
            }

            current = null;
         }

         return result;
      }

      private XEntity current = null;
      private Enumeration<XEntity> e = null;
      private HashSet<XEntity> entities = null;
      private String table = null;
   }

   /**
    * Get the position of the entity.
    */
   public int getEntityIndex(String name) {
      return entities.indexOf(name);
   }

   public XEntity getEntityByOldName(String oname) {
      Enumeration<XEntity> enu = getEntities();

      while(enu.hasMoreElements()) {
         XEntity entity = enu.nextElement();

         if(entity != null && Tool.equals(oname, entity.getOldName())) {
            return entity;
         }
      }

      return null;
   }

   /**
    * Set the base model.
    */
   public void setBaseModel(XLogicalModel base) {
      setBaseModel(base, true);
   }

   public void setBaseModel(XLogicalModel base, boolean copyBase) {
      if(this.base == base) {
         return;
      }

      this.base = base;

      if(base != null && copyBase) {
         int cnt = entities.size();

         // clear base first
         for(int i = cnt - 1; i >= 0; i--) {
            XEntity entity = entities.getValue(i);
            entity.setBaseEntity(null);
         }

         Enumeration<XEntity> enu = base.getEntities();

         // copy entities from base model
         while(enu.hasMoreElements()) {
            XEntity entity = enu.nextElement();
            String name = entity.getName();
            XEntity tmp = entities.get(name);

            if(tmp == null) {
               addEntity(new XEntity(entity));
            }
            else {
               tmp.setBaseEntity(entity);
            }
         }
      }

      int cnt = entities.size();

      for(int i = cnt - 1; i >= 0; i--) {
         XEntity entity = entities.getValue(i);

         if(entity == null) {
            continue;
         }

         // entity might be removed from parent logical model
         if(entity.isBaseEntity() && entity.getBaseEntity() == null) {
            entities.remove(i);
         }
         else {
            entity.validate();
         }
      }
   }

   /**
    * Get the base model.
    */
   public XLogicalModel getBaseModel() {
      return base;
   }

   /**
    * Set is runtime mode or not.
    * @param runtime apply visible of entities and attributes.
    */
   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
      Enumeration<XEntity> enu = getEntities();

      while(enu.hasMoreElements()) {
         enu.nextElement().setRuntime(runtime);
      }
   }

   /**
    * Set visibility  of the entity.
    * @param name the name of specified entity.
    */
   public void setEntityVisible(String name, boolean visible) {
      getEntity(name).setVisible(visible);
   }

   /**
    * Check the specified entity is visible or not.
    * @param name the name of the specified entity.
    */
   public boolean isEntityVisible(String name) {
      return getEntity(name).isVisible();
   }

   /**
    * Add a child XLogicalModel.
    */
   public void addLogicalModel(XLogicalModel child, boolean lmchange) {
      String path = getDataSource() + "/" + getName() + "/" + child.getName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_LOGIC_MODEL, path, null);

      if(lmchange && getRegistry().getObject(entry, true, false) == null) {
         child.setCreated(System.currentTimeMillis());
      }

      child.setLastModified(System.currentTimeMillis());
      getRegistry().setObject(entry, child);
      child.setBaseModel(this);
   }

   /**
    * Check if child XLogicalModel exists.
    */
   public boolean containLogicalModel(String name) {
      String path = getDataSource() + "/" + getName() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_LOGIC_MODEL, path, null);
      return getRegistry().containObject(entry);
   }

   /**
    * Get child XLogicalModel with specified name.
    */
   public XLogicalModel getLogicalModel(String name) {
      XLogicalModel result = null;

      if(name != null) {
         String path = getDataSource() + "/" + getName() + "/" + name;
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                 AssetEntry.Type.EXTENDED_LOGIC_MODEL, path, null);
         result = (XLogicalModel) getRegistry().getObject(entry, true);

         if(result != null) {
            result.setBaseModel(this);
            getRegistry().setCache(entry, result);
         }
      }

      return result;
   }

   /**
    * Get the extended view which used this extended partition.
    */
   public String getPartitionUsed(XPartition partition) {
      String conn1 = partition.getConnection();
      String[] names = getLogicalModelNames();

      for(String name : names) {
         XLogicalModel model = getLogicalModel(name);
         String conn2 = model.getConnection();

         if(!partition.getBasePartition().getName().equals(
            model.getBaseModel().getPartition()))
         {
            continue;
         }

         if(Tool.equals(conn1, conn2) || conn2 != null && conn1 == null &&
            partition.getBasePartition().getPartition(conn2) == null)
         {
            return model.getName();
         }
      }

      return null;
   }

   /**
    * Get child XLogicalModel with specified connection.
    */
   public XLogicalModel getLogicalModelByConnection(String connection,
                                                    boolean strict,
                                                    JDBCDataSource datasource,
                                                    Principal principal)
   {
      if(connection != null) {
         XLogicalModel lm = getLogicalModel(connection);

         if(lm != null && lm.getConnection() != null || strict) {
            return lm;
         }
      }

      String[] names = getLogicalModelNames();

      for(String name : names) {
         try{
            XLogicalModel lm = getLogicalModel(name);

            if(lm != null && lm.getConnection() == null) {
               if (datasource != null && datasource.isUnasgn() &&
                       name.equals("(Default Connection)") && !(OrganizationManager.getInstance().isSiteAdmin(principal) || OrganizationManager.getInstance().isOrgAdmin(principal))) {
                  throw new Exception();
               }

               return lm;
            }
         }
         catch(Exception e) {
            LOG.error("Data source is not available. Please contact your administrator.");

            XLogicalModel nmodel = (XLogicalModel) this.clone();
            nmodel.setConnection(null);

            for(int i = 0; i < getEntityCount(); i++) {
               nmodel.removeEntity(entities.getValue(i).getName());
            }

            return nmodel;
         }
      }

      return null;
   }

   /*
    * Return its datasource if its datasource is a JDBCDataSource,
    * otherwise return null.
    */
   private JDBCDataSource getJDBCDataSource() {
      XDataSource xDataSource = getRegistry().getDataSource(getDataSource());

      if(xDataSource instanceof JDBCDataSource) {
         return (JDBCDataSource) xDataSource;
      }

      return null;
   }

   /**
    * Get the names of child models.
    */
   public String[] getLogicalModelNames() {
      String[] result = null;

      try {
         String path = getDataSource() + "/" + getName() + "/";
         AssetEntry[] entries =
                 getRegistry().getEntries(path, AssetEntry.Type.EXTENDED_LOGIC_MODEL);
         result = new String[entries.length];

         for(int i = 0; i < entries.length; i++) {
            result[i] = entries[i].getPath().substring(path.length());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get extended logical model "
                 + "names of model: " + getName(), e);
      }

      return result;
   }

   /**
    * remove a child XLogicalModel.
    */
   public void removeLogicalModel(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
              AssetEntry.Type.EXTENDED_LOGIC_MODEL,
              getDataSource() + "/" + getName() + "/" + name, null);
      getRegistry().removeObject(entry);
   }

   /**
    * rename a child XLogicalModel.
    */
   public void renameLogicalModel(String oname, XLogicalModel extend) {
      XLogicalModel clone = (XLogicalModel) extend.clone();
      clone.setName(oname);
      DependencyHandler.getInstance().updateModelDependencies(clone, false);
      String oldPath = getDataSource() + "/" + getName() + "/" + oname;
      String newPath = getDataSource() + "/" + getName() +
              "/" + extend.getName();
      getRegistry().updateObject(oldPath, newPath,
              AssetEntry.Type.EXTENDED_LOGIC_MODEL, extend);
      DependencyHandler.getInstance().updateModelDependencies(extend, true);
      int type = RenameInfo.LOGIC_MODEL | RenameInfo.SOURCE;
      RenameInfo rinfo = new RenameInfo(oname, extend.getName(), type);
      rinfo.setPrefix(getDataSource());
      RenameTransformHandler.getTransformHandler().addTransformTask(rinfo);
   }

   /**
    * Create a new logical model, a clone of extended model or self, hide
    * invisible entities and attributes.
    */
   protected XLogicalModel applyRuntime(Principal principal, boolean hideAttributes) {
      String ds = XUtil.getAdditionalDatasource(principal, getDataSource());
      JDBCDataSource jdbcDataSource = getJDBCDataSource();
      XLogicalModel model = getLogicalModelByConnection(ds, false, jdbcDataSource, principal);

      if(model != null) {
         if(hideAttributes) {
            model = (XLogicalModel) model.clone();
            model.setRuntime(hideAttributes);
         }

         return model;
      }
      else {
         if (jdbcDataSource != null && jdbcDataSource.isUnasgn() &&
                 isContainExtendedModel(jdbcDataSource) &&
            principal != null && !(OrganizationManager.getInstance().isSiteAdmin(principal) || OrganizationManager.getInstance().isOrgAdmin(principal))) {
            LOG.error("Data source is not available. Please contact your administrator.");

            XLogicalModel nmodel = (XLogicalModel) this.clone();
            nmodel.setConnection(null);

            for (int i = 0; i < getEntityCount(); i++) {
               nmodel.removeEntity(entities.getValue(i).getName());
            }

            return nmodel;
         }
      }

      return this;
   }

   /*
    * To check whether has extendedmodel in this logical model.
    */
   private boolean isContainExtendedModel(JDBCDataSource jdbcDataSource) {
      if(jdbcDataSource == null) {
         return false;
      }

      String[] names = jdbcDataSource.getDataSourceNames();

      for(String name : names) {
         XLogicalModel lm = getLogicalModel(name);

         if(lm != null && lm.getConnection() != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Move entity up or down.
    */
   public boolean moveEntity(String name, boolean up) {
      int oidx = getEntityIndex(name);
      int idx = oidx + (up ? -1 : 1);

      if(idx >= 0 && idx < entities.size()) {
         fireEntityRemoved(name);
         entities.put(idx, name, entities.remove(name));
         fireEntityAdded(name);

         return true;
      }

      return false;
   }

   /**
    * Update child model reference.
    */
   public void updateReference() {
      String[] names = getLogicalModelNames();

      for(String name : names) {
         getLogicalModel(name).setBaseModel(this);
      }
   }

   /**
    * Get data model.
    */
   public XDataModel getDataModel() {
      return base != null ? base.model : model;
   }

   /**
    * Set data model.
    */
   public void setDataModel(XDataModel model) {
      this.model = model;
   }

   public String getFolder() {
      return folder;
   }

   public void setFolder(String folder) {
      this.folder = Tool.isEmptyString(folder) ? null : folder;
   }

   private DataSourceRegistry getRegistry() {
      return DataSourceRegistry.getRegistry();
   }

   private boolean entityOrder = true;
   private String connection = null;
   private String partition = null;
   private String description = null;
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
   private boolean runtime;
   private OrderedMap<String, XEntity> entities = null;
   private String name = null;
   private String folder;
   private XLogicalModel base;
   private XDataModel model;
   private transient ArrayList<DataModelListener> listeners = null;
   private EntityListenerImpl entityListener = null;
   private EntityPropertyListener entityPropertyListener = null;
   private OrderedMap<String, XVariable> varmap = null; // var name -> XVariable
   private Set<Object> dependencies = ConcurrentHashMap.newKeySet(); // outer dependencies

   private static final Logger LOG = LoggerFactory.getLogger(XLogicalModel.class);
}
