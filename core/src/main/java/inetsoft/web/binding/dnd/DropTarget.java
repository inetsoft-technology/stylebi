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
package inetsoft.web.binding.dnd;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = BindingDropTarget.class,
                      name = "BindingDropTarget"),
   @JsonSubTypes.Type(value = CalcDropTarget.class,
                      name = "CalcDropTarget"),
   @JsonSubTypes.Type(value = ChartViewDropTarget.class,
                      name = "ChartViewDropTarget"),
   @JsonSubTypes.Type(value = ChartAestheticDropTarget.class,
                      name = "ChartAestheticDropTarget")
})
public class DropTarget {
   public void setObjectType(String objectType) {
      this.objectType = objectType;
   }

   public String getObjectType() {
      return objectType;
   }

   public void setAssembly(String assembly) {
      this.assembly = assembly;
   }

   public String getAssembly() {
      return assembly;
   }

   private String objectType;
   private String assembly;
}
