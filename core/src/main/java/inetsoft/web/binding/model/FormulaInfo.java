/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.binding.model;

import inetsoft.uql.asset.AggregateFormula;

public class FormulaInfo {
   /**
    * Constructor.
    */
   public FormulaInfo() {
   }

   /**
    * Constructor.
    */
   public FormulaInfo(AggregateFormula aggFormula) {
      setName(aggFormula.getName());
      setLabel(aggFormula.getLabel());
      setTwoColumns(aggFormula.isTwoColumns());
      setFormulaName(aggFormula.getFormulaName());
   }

   /**
    * Get the display name.
    * @return the name of the formula info.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the display name.
    * @param name the name of the formula info.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Check if the formula requires two columns.
    */
   public boolean isTwoColumns() {
      return twoColumns;
   }

   /**
    * Set the formula requires two columns.
    */
   public void setTwoColumns(boolean twoColumns) {
      this.twoColumns = twoColumns;
   }

   /**
    * Get the label of the formula info.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Set the label of the formula info.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Get a formula that can be used to create a formula calculation object.
    * This is an internal method used during runtime for post processing.
    */
   public String getFormulaName() {
      return formulaName;
   }

   /**
    * Set the formula name.
    */
   public void setFormulaName(String formulaName) {
      this.formulaName = formulaName;
   }

   private String name;
   private boolean twoColumns;
   private String label;
   private String formulaName;
}
