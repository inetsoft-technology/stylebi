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

package inetsoft.util.credential;

import inetsoft.util.XMLSerializable;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsType;

import java.io.Serializable;

public interface Credential extends Serializable, XMLSerializable, Cloneable {
   /**
    * @return the Credential id.
    */
   default String getId() {
      return null;
   }

   /**
    * Set the Credential id.
    * @param id
    */
   default void setId(String id) {
   }

   /**
    * Set db type.
    */
   default void setDBType(String dbType) {
   }

   /**
    * Get db type.
    */
   default String getDBType() {
      return null;
   }

   default void reset() {
      resetId();
   }

   /**
    * Reset id, then write xml will create a new id, and not override the old secret.
    */
   default void resetId() {
      setId(null);
   }

   boolean isEmpty();

   public Object clone() throws CloneNotSupportedException;
}
