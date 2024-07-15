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
package inetsoft.web.viewsheet.model;

/**
 * Specifies this class as a target for prototype creation
 */
public interface PrototypeModel<T extends ModelPrototype> {
   /**
    * Index stored on this prototype model to reference after serialization
    */
   int getProtoIdx();

   /**
    * This method should set the prototype index and clears the fields captured in prototype.
    */
   void setModelPrototypeIndex(int index);

   /**
    * Get a prototype factory for this model.
    */
   T createModelPrototype();
}
