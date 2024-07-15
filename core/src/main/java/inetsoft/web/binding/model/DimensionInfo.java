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
package inetsoft.web.binding.model;

import inetsoft.report.internal.binding.GroupField;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.handler.ChartHandler;

import java.util.Objects;

public class DimensionInfo {
   /**
    * Constructor.
    */
   public DimensionInfo() {
   }

   public DimensionInfo(String label, String data) {
      setLabel(label);
      setData(data);
   }

   /**
    * Constructor.
    */
   public DimensionInfo(XDimensionRef dim, ChartHandler handler, String desc) {
      String group = (dim instanceof VSDimensionRef)
         ? ((VSDimensionRef) dim).getGroupColumnValue() : "";
      this.fullName = VSUtil.isDynamicValue(group) ? group : dim.getFullName();
      this.dateTime = dim.isDateTime() && !dim.isNamedGroupAvailable();
      this.label = VSUtil.isDynamicValue(group) ? group :
         dim.isDateTime() ? handler.getDateTimeView(dim) : dim.getFullName();
      this.description = desc;
      this.dateLevel = dim.getDateLevel();
   }

   /**
    * Constructor.
    */
   public DimensionInfo(GroupField group, String desc) {
      this.fullName = group.getName();
      OrderInfo order = group.getOrderInfo();

      if(XSchema.isDateType(group.getDataType())) {
         this.fullName = DateRangeRef.getName(this.fullName, order.getOption());
      }

      this.dateTime = group.isDate() &&
         (!order.isSpecific() || order.getRealNamedGroupInfo() == null);
      this.label = group.toView();
      this.description = desc;
      this.dateLevel = group.getOrderInfo().getOption();
   }

   public String getData() {
      return fullName;
   }

   public void setData(String fullName) {
      this.fullName = fullName;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public boolean isDateTime() {
      return dateTime;
   }

   public void setDateTime(boolean dateTime) {
      this.dateTime = dateTime;
   }

   public int getDateLevel() {
      return dateLevel;
   }

   public void setDateLevel(int level) {
      this.dateLevel = level;
   }

   public int hashCode() {
      return (this.fullName != null) ? this.fullName.hashCode() : super.hashCode();
   }

   public boolean equals(Object obj) {
      return Objects.equals(fullName, ((DimensionInfo) obj).fullName);
   }

   private String fullName;
   private String label;
   private String description;
   private boolean dateTime;
   private int dateLevel;
}
