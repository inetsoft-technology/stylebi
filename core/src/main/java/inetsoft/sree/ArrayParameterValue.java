/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree;

import java.io.Serializable;

public class ArrayParameterValue implements Serializable, Cloneable {
   public ArrayParameterValue() {
   }

   public ArrayParameterValue(Object[] value, String type) {
      this.value = value;
      this.type = type;
   }

   public ArrayParameterValue convertModel() {
      return new ArrayParameterValue(this.value, this.type);
   }

   public Object[] getValue() {
      return value;
   }

   public void setValue(Object[] value) {
      this.value = value;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   private Object[] value;
   private String type;
}
