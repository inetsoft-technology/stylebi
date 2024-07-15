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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.ObjectWrapper;

import java.util.Objects;

/**
 * Object representation of XML text node.
 */
@JsonSerialize
public class ValueNode implements ParsedNode, ObjectWrapper {
   public ValueNode(Object value) {
      this.value = value;
   }

   @Override
   public Object unwrap() {
      return value;
   }

   @Override
   public String toString() {
      return "ValueNode{" +
         "value=" + value +
         '}';
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ValueNode valueNode = (ValueNode) o;
      return Objects.equals(value, valueNode.value);
   }

   private final Object value;
}
