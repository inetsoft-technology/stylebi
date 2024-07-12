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
package inetsoft.uql.erm;

import java.util.EventListener;

/**
 * Listener that is notified when an entity is changed.
 *
 * @author  InetSoft Technology Corp.
 * @since   5.0
 */
interface EntityListener extends EventListener {
   /**
    * Fired when an attribute is added.
    *
    * @param evt the change event.
    */
   public void attributeAdded(EntityEvent evt);
   
   /**
    * Fired when an attribute is removed.
    *
    * @param evt the change event.
    */
   public void attributeRemoved(EntityEvent evt);
   
   /**
    * Fired when an attribute has been renamed.
    *
    * @param evt the change event.
    *
    * @since 6.0
    */
   public void attributeRenamed(EntityEvent evt);
   
   /**
    * Fired when a property of an attribute has been changed other than it's
    * name.
    *
    * @param evt the change event.
    *
    * @since 8.0
    */
   public void attributeChanged(EntityEvent evt);
}

