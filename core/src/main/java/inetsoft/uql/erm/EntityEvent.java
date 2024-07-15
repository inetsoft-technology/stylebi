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

import java.util.EventObject;

/**
 * Event signaling a change to an entity.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.0
 */
class EntityEvent extends EventObject {
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the attribute that was changed.
    */
   public EntityEvent(XEntity entity, String attribute) {
      this(entity, attribute, -1, null);
   }
   
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the attribute that was changed.
    * @param attributeIndex the index of the changed attribute.
    *
    * @since 8.0
    */
   public EntityEvent(XEntity entity, String attribute, int attributeIndex) {
      this(entity, attribute, attributeIndex, null);
   }
   
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the name of the modified attribute.
    * @param oattribute the original name of the attribute.
    *
    * @since 6.0
    */
   public EntityEvent(XEntity entity, String attribute, String oattribute) {
      this(entity, attribute, -1, oattribute);
   }
   
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the name of the modified attribute.
    * @param property the name of the property that was modified.
    * @param oldValue the oldValue of the property.
    * @param newValue the newValue of the property.
    *
    * @since 8.0
    */
   public EntityEvent(XEntity entity, String attribute, String property,
                      Object oldValue, Object newValue) {
      this(entity, attribute, -1, null, property, oldValue, newValue);
   }
   
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the name of the modified attribute.
    * @param attributeIndex the index of the changed attribute.
    * @param oattribute the original name of the attribute.
    *
    * @since 8.0
    */
   public EntityEvent(XEntity entity, String attribute, int attributeIndex,
                      String oattribute)
   {
      this(entity, attribute, attributeIndex, oattribute, null, null, null);
   }
   
   /**
    * Create a new instance of EntityEvent.
    *
    * @param entity the source of this event.
    * @param attribute the name of the modified attribute.
    * @param attributeIndex the index of the changed attribute.
    * @param oattribute the original name of the attribute.
    * @param property the name of the property that was modified.
    * @param oldValue the oldValue of the property.
    * @param newValue the newValue of the property.
    *
    * @since 8.0
    */
   public EntityEvent(XEntity entity, String attribute, int attributeIndex,
                      String oattribute, String property, Object oldValue,
                      Object newValue)
   {
      super(entity);
      this.attribute = attribute;
      this.attributeIndex = attributeIndex;
      this.oattribute = oattribute;
      this.property = property;
      this.oldValue = oldValue;
      this.newValue = newValue;
   }

   /**
    * Get the entity that is the source of this event.
    *
    * @return the entity.
    */
   public XEntity getEntity() {
      return (XEntity) getSource();
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
    * Get the original name of the modified attribute.
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

   private String attribute;
   private String oattribute;
   private int attributeIndex = -1;
   private String property;
   private Object oldValue;
   private Object newValue;
}

