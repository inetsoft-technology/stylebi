/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

/** One field that was successfully attached as a filter control, in {@link AddFiltersResponse}. */
public class AppliedFilter {
   public AppliedFilter() {
   }

   public AppliedFilter(String field, String assemblyName, String controlType, String label) {
      this.field = field;
      this.assemblyName = assemblyName;
      this.controlType = controlType;
      this.label = label;
   }

   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   public String getAssemblyName() {
      return assemblyName;
   }

   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   public String getControlType() {
      return controlType;
   }

   public void setControlType(String controlType) {
      this.controlType = controlType;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   private String field;
   private String assemblyName;
   private String controlType;
   private String label;
}
