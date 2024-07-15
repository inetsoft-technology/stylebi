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

/**
 * DataRefWrapper wraps a data ref.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface DataRefWrapper extends DataRef {
   /**
    * Get the contained data ref.
    */
   public DataRef getDataRef();

   /**
    * Set the wrapped data ref.
    */
   public void setDataRef(DataRef ref);

   /**
    * Get the base attibute of an attribute.
    * @param attr the specified attribute.
    * @return the base attibute of the attibute.
    */
   public static DataRef getBaseDataRef(DataRef attr) {
      while(attr instanceof DataRefWrapper) {
         attr = ((DataRefWrapper) attr).getDataRef();
      }

      return attr;
   }
}
