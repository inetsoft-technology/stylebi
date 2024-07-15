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
package inetsoft.web.adhoc.model.property;

import inetsoft.util.Catalog;

public class StrategyInfo{

  public StrategyInfo() {}

  public StrategyInfo(String name, String value) {
     this.name = name;
     this.value = value;
     this.label = Catalog.getCatalog().getString(name);
  }

   /**
    * Gets target strategy label.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Sets target strategy label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Gets target strategy name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets target strategy name.
    */
   public void setName(String name) {
      this.name = name;
      this.label = Catalog.getCatalog().getString(name);
   }

   /**
    * Gets the param value of the strategy
    */
   public String getValue() {
      return value;
   }

   /**
    * Sets the param value of the strategy
    */
   public void setValue(String value) {
      this.value = value;
   }

   public String getPercentageAggregateVal(){
     return percentageAggregateVal;
   }

   public void setPercentageAggregateVal(String agg) {
      this.percentageAggregateVal = agg;
   }

   public String getStandardIsSample() {
      return standardIsSample;
   }

   public void setStandardIsSample(String isSample) {
      this.standardIsSample = isSample;
   }

   private String name;
   private String label;
   private String value = "";
   private String percentageAggregateVal = "Max";
   private String standardIsSample = "true";
}