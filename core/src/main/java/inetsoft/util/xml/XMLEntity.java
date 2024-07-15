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
package inetsoft.util.xml;

import inetsoft.util.Tool;
import inetsoft.util.xml.XMLStorage.FileInfo;
import inetsoft.util.xml.XMLStorage.Filter;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * XMLEntity represents an XMLNode in XMLStorage. It's more like an identifier
 * rather than a concrete XMLNode. By using this object, users could get Java
 * objects from XMLStorage, and write down Java objects to XMLStorage.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XMLEntity {
   /**
    * Constructor.
    * @param name the element name.
    */
   public XMLEntity(String name, XMLStorage storage) {
      this.name = name;
      this.storage = storage;
      attributes = new HashMap<>();
      entities = new ArrayList<>();
   }

   /**
    * Get name.
    */
   public String getName() {
      return name;
   }

   /**
    * Get attribute.
    */
   public String getAttribute(String name) {
      return attributes.get(name);
   }

   /**
    * Set the attribute.
    */
   public void setAttribute(String name, String attribute) {
      attributes.put(name, attribute);
   }

   /**
    * Get java object.
    */
   public Object getObject() {
      if(obj != null) {
         return obj;
      }

      boolean cache = storage.isCache();
      Object res = ref == null ? null : ref.get();

      if(res == null) {
         res = storage.parseObject(this);
         storage.postParseObject(res);

         if(cache) {
            ref = new WeakReference<>(res);
         }
      }

      if(cache) {
         obj = res;
      }

      return res;
   }

   /**
    * Set java object.
    */
   public void setObject(Object obj) {
      this.obj = obj;
   }

   /**
    * Get the parent entity.
    */
   public XMLEntity getParentEntity() {
      return pentity;
   }

   /**
    * Check if this entity has the same name and attributes.
    */
   public boolean isValid(Filter filter) {
      if(name.equals(filter.name)) {
         for(String qname : filter.attributes.keySet()) {
            Object value = filter.attributes.get(qname);

            if(!Tool.equals(value, getAttribute(qname))) {
               return false;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Get the count of child entities.
    */
   public int getEntityCount() {
      return entities.size();
   }

   /**
    * Get the sub entities.
    */
   public Iterator<XMLEntity> getEntities() {
      return entities.iterator();
   }

   /**
    * Add child entity.
    */
   public void addEntity(XMLEntity entity) {
      entity.pentity = this;
      entities.add(entity);
      isparent = true;
   }

   /**
    * Remove child entity.
    */
   public void removeEntity(XMLEntity entity) {
      entities.remove(entity);
   }

   /**
    * Remove all child entities.
    */
   public void clear() {
      entities.clear();
   }

   /**
    * Check if java object has been loaded in memory.
    */
   public boolean isLoaded() {
      return obj != null || ref != null && ref.get() != null;
   }

   public String toString() {
      return name + attributes;
   }

   public boolean equals(Object obj) {
      if(obj instanceof XMLEntity) {
         XMLEntity entity = (XMLEntity) obj;

         if(name.equals(entity.name) && (getAttribute("name") != null &&
            Tool.equals(getAttribute("name"), entity.getAttribute("name")) ||
            getAttribute("name") == null &&
            Tool.equals(getAttribute("datasource"),
                        entity.getAttribute("datasource"))))
         {
            return true;
         }
      }

      return false;
   }

   public int hashCode() {
      return name.hashCode() + attributes.hashCode();
   }

   protected long startPosition;
   protected long endPosition;
   protected FileInfo info;
   protected List<XMLEntity> entities;
   protected Object obj;
   protected boolean virtual = false;
   protected WeakReference<Object> ref;
   protected String name;
   private HashMap<String, String> attributes;
   private XMLStorage storage;
   private XMLEntity pentity;
   protected boolean isparent = false;
}
