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
package inetsoft.report.event;

import java.io.Serializable;

/**
 * TableChangeEvent is used to notify interested parties that table data has
 * changed.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class TableChangeEvent implements Serializable {
   /**
    * Construct a TableChangeEvent object.
    * @param source the Object that is the source of the event, typically
    * <code>this</code>
    */
   public TableChangeEvent(Object source) {
      this.source = source;
   }

   /**
    * Get the Object that is the source of the event.
    * @return the source of the event.
    */
   public Object getSource() {
      return source;
   }

   private Object source;
}
