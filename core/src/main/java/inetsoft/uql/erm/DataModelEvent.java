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
package inetsoft.uql.erm;

import java.util.EventObject;

/**
 * Event signaling a change to a data model.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.0
 */
public class DataModelEvent extends EventObject {
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute) {
      this(model, entity, attribute, -1, null, null);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    * @param attributeIndex the index of the attribute in the entity.
    *
    * @since 8.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute,
                         int attributeIndex)
   {
      this(model, entity, attribute, attributeIndex, null, null);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    * @param oentity the original name of the entity that was changed.
    * @param oattribute the original name of the attribute that was changed.
    *
    * @since 6.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute,
                         String oentity, String oattribute)
   {
      this(model, entity, attribute, -1, oentity, oattribute);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param property the name of the property that was modified.
    * @param oldValue the oldValue of the property.
    * @param newValue the newValue of the property.
    *
    * @since 8.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String property,
                         Object oldValue, Object newValue)
   {
      this(model, entity, null, -1, null, null, property, oldValue, newValue);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    * @param property the name of the property that was modified.
    * @param oldValue the oldValue of the property.
    * @param newValue the newValue of the property.
    *
    * @since 8.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute,
                         String property, Object oldValue, Object newValue)
   {
      this(model, entity, attribute, -1, null, null, property,
           oldValue, newValue);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    * @param attributeIndex the index of the attribute in the entity.
    * @param oentity the original name of the entity that was changed.
    * @param oattribute the original name of the attribute that was changed.
    *
    * @since 8.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute,
                         int attributeIndex, String oentity,
                         String oattribute)
   {
      this(model, entity, attribute, attributeIndex, oentity, oattribute,
           null, null, null);
   }
   
   /**
    * Create a new instance of DataModelEvent.
    *
    * @param model the data model that was changed.
    * @param entity the name of the entity that was changed.
    * @param attribute the name of the attribute that was changed.
    * @param attributeIndex the index of the attribute in the entity.
    * @param oentity the original name of the entity that was changed.
    * @param oattribute the original name of the attribute that was changed.
    * @param property the name of the property that was modified.
    * @param oldValue the oldValue of the property.
    * @param newValue the newValue of the property.
    *
    * @since 8.0
    */
   public DataModelEvent(XLogicalModel model, String entity, String attribute,
                         int attributeIndex, String oentity,
                         String oattribute, String property, Object oldValue,
                         Object newValue)
   {
      super(model);
      this.entity = entity;
      this.attribute = attribute;
      this.attributeIndex = attributeIndex;
      this.oentity = oentity;
      this.oattribute = oattribute;
      this.property = property;
      this.oldValue = oldValue;
      this.newValue = newValue;
   }

   /**
    * Get the data model that was changed.
    *
    * @return the data model.
    */
   public XLogicalModel getDataModel() {
      return (XLogicalModel) getSource();
   }

   /**
    * Get the entity that was changed.
    *
    * @return the name of the entity.
    */
   public String getEntity() {
      return entity;
   }

   /**
    * Get the attribute that was changed.
    *
    * @return the name of the attribute.
    */
   public String getAttribute() {
      return attribute;
   }
   
   /**
    * Get the index of the attribute that was changed.
    *
    * @return the index of the attribute.
    *
    * @since 8.0
    */
   public int getAttributeIndex() {
      return attributeIndex;
   }
   
   /**
    * Get the original name of the entity that was changed.
    *
    * @return the original name of the entity.
    *
    * @since 6.0
    */
   public String getOriginalEntity() {
      return oentity;
   }
   
   /**
    * Get the original name of the attribute that was changed.
    *
    * @return the original name of the attribute.
    *
    * @since 6.0
    */
   public String getOriginalAttribute() {
      return oattribute;
   }
   
   /**
    * Get the name of the property that was modified.
    *
    * @return the name of the property.
    *
    * @since 8.0
    */
   public String getPropertyName() {
      return property;
   }
   
   /**
    * Get the old value of the property that was modified.
    *
    * @return the old value of the property.
    *
    * @since 8.0
    */
   public Object getOldValue() {
      return oldValue;
   }
   
   /**
    * Get the new value of the property that was modified.
    *
    * @return the new value of the property.
    *
    * @since 8.0
    */
   public Object getNewValue() {
      return newValue;
   }

   private String entity = null;
   private String attribute = null;
   private String oentity = null;
   private String oattribute = null;
   private int attributeIndex = -1;
   private String property;
   private Object oldValue;
   private Object newValue;
}

