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
package inetsoft.report.filter;

import java.util.ArrayList;
import java.util.List;

public class GroupTuple {
   public GroupTuple() {
   }

   public GroupTuple(List<Object> values) {
      this.values = values;
   }

   public void addValue(Object o) {
      if(values == null) {
         this.values = new ArrayList<>();
      }

      this.values.add(o);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof GroupTuple)) {
         return false;
      }

      if(((GroupTuple) obj).values == values) {
         return true;
      }

      if(values != null && ((GroupTuple) obj).values != null) {
         return values.equals(((GroupTuple) obj).values);
      }

      return false;
   }

   @Override
   public int hashCode() {
      return values == null ? 0 : values.hashCode();
   }

   private List<Object> values;
}
