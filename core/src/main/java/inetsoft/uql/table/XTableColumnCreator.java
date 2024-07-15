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
package inetsoft.uql.table;

import java.io.Serializable;

/**
 * XTableColumnCreator creates a table column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public abstract class XTableColumnCreator implements Serializable {
   /**
    * Create a table column.
    * @param isize the specified initial size of the table column.
    * @param size the specified capabity of the table column.
    */
   public abstract XTableColumn createColumn(char isize, char size);

   /**
    * Get the column data type if it's known.
    */
   public abstract Class getColType();

   /**
    * Check if cache option is used.
    * @return <tt>true</tt> if used, <tt>false</tt> otherwise.
    */
   public final boolean isCache() {
      return coption;
   }

   /**
    * Set whether cache option is used.
    * @param coption the specified cache option.
    */
   public final void setCache(boolean coption) {
      this.coption = coption;
   }

   /**
    * Check if the created column is dynamic.
    * @return <tt>true</tt> if dynamic.
    */
   public final boolean isDynamic() {
      return dynamic;
   }

   /**
    * Set whether the created column is dynamic.
    * @param dynamic <tt>true</tt> if dynamic.
    */
   public final void setDynamic(boolean dynamic) {
      this.dynamic = dynamic;
   }

   private boolean coption = true;
   private boolean dynamic = true;
}
