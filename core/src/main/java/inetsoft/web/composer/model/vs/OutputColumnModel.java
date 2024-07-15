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
package inetsoft.web.composer.model.vs;

public class OutputColumnModel {
   public String getDefaultFormula() {
      return defaultFormula;
   }

   public void setDefaultFormula(String defaultFormula) {
      this.defaultFormula = defaultFormula;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getTooltip() {
      return tooltip;
   }

   public void setTooltip(String tooltip) {
      this.tooltip = tooltip;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public boolean isAggregateCalcField() {
      return aggregate;
   }

   public void setAggregateCalcField(boolean aggregate) {
      this.aggregate = aggregate;
   }

   private String name;
   private String type;
   private String label;
   private String tooltip;
   private boolean aggregate;
   private String defaultFormula;
}
