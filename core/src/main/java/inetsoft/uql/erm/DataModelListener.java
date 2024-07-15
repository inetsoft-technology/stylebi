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

import java.util.EventListener;

/**
 * Listens for changes to a data model.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.0
 */
public interface DataModelListener extends EventListener {
   /**
    * Fired when an entity is added to a data model.
    *
    * @param evt the change event.
    */
   public void entityAdded(DataModelEvent evt);
   
   /**
    * Fired when an entity is removed from a data model.
    *
    * @param evt the change event.
    */
   public void entityRemoved(DataModelEvent evt);
   
   /**
    * Fired when an entity is renamed in a data model.
    *
    * @param evt the change event.
    */
   public void entityRenamed(DataModelEvent evt);
   
   /**
    * Fired when a property of an entity has been changed other than it's
    * name.
    *
    * @param evt the change event.
    *
    * @since 8.0
    */
   public void entityChanged(DataModelEvent evt);
   
   /**
    * Fired when an attribute is added to an entity in a data model.
    *
    * @param evt the change event.
    */
   public void attributeAdded(DataModelEvent evt);
   
   /**
    * Fired when an attribute is removed from an entity in a data model.
    *
    * @param evt the change event.
    */
   public void attributeRemoved(DataModelEvent evt);
   
   /**
    * Fired when an attribute is renamed.
    *
    * @param evt the change event.
    */
   public void attributeRenamed(DataModelEvent evt);
   
   /**
    * Fired when a property of an attribute has been changed other than it's
    * name.
    *
    * @param evt the change event.
    *
    * @since 8.0
    */
   public void attributeChanged(DataModelEvent evt);
}

