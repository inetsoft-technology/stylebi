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
package inetsoft.web.viewsheet.model;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

public class DrillFilterAction {
   public boolean isDrillUp() {
      return drillUp;
   }

   public DrillFilterAction setDrillUp(boolean drillUp) {
      this.drillUp = drillUp;

      return this;
   }

   public List<String> getFields() {
      return fields;
   }

   public DrillFilterAction setFields(List<String> fields) {
      this.fields = fields;

      return this;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public DrillFilterAction setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;

      return this;
   }

   public boolean isInvalid() {
      if(StringUtils.isEmpty(assemblyName)) {
         return true;
      }

      if(CollectionUtils.isEmpty(fields)) {
         return true;
      }

      return false;
   }

   private boolean drillUp;
   private List<String> fields;
   private String assemblyName;
}
