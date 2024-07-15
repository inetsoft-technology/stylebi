/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.xml.parse;

import inetsoft.uql.schema.XTypeNode;

import java.util.*;

/**
 * Type map which goes alongside a parsed document. Provides data types for fields.
 */
public class TypeMap {
   void register(XTypeNode attrType, List<String> path) {
      if(attrType == null || registeredTypes.contains(attrType)) {
         return;
      }

      registeredTypes.add(attrType);
      types.putIfAbsent(path, attrType.getType());
   }

   public Map<List<String>, String> getTypes() {
      return types;
   }

   private final HashSet<XTypeNode> registeredTypes = new HashSet<>();
   private final Map<List<String>, String> types = new HashMap<>();
}
