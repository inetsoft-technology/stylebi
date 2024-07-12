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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonTypeName;

@SuppressWarnings({ "unused", "WeakerAccess" })
@JsonTypeName("clause")
public class Clause extends DataConditionItem {
   public Clause() {
      super("clause");
   }

   public Operation getOperation() {
      return operation;
   }

   public void setOperation(Operation operation) {
      this.operation = operation;
   }

   public boolean isNegated() {
      return negated;
   }

   public void setNegated(boolean negated) {
      this.negated = negated;
   }

   public ClauseValue getValue1() {
      return value1;
   }

   public void setValue1(ClauseValue value1) {
      this.value1 = value1;
   }

   public ClauseValue getValue2() {
      return value2;
   }

   public void setValue2(ClauseValue value2) {
      this.value2 = value2;
   }

   public ClauseValue getValue3() {
      return value3;
   }

   public void setValue3(ClauseValue value3) {
      this.value3 = value3;
   }

   private boolean negated;
   private Operation operation;
   private ClauseValue value1;
   private ClauseValue value2;
   private ClauseValue value3;
}
