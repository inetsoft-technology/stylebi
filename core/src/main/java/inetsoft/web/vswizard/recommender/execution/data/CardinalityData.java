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
package inetsoft.web.vswizard.recommender.execution.data;

/**
 * This class keeps the cardinality and cardinality percentage information for a binding field,
 * and these information will be used for the recommend logic of viewsheet wizard.
 * @version 13.2
 * @author InetSoft Technology Corp
 */

public class CardinalityData implements WizardData {
   public CardinalityData() {
   }

   public CardinalityData(String field, int cardinality, double cardinalityPercentage) {
      this.field = field;
      this.cardinality = cardinality;
      this.cardinalityPercentage = cardinalityPercentage;
   }

   /**
    * Create a CardinalityData.
    * @param  count         the dataset size.
    * @param  distinctCount the distinct count result for the a binding field.
    */
   public static CardinalityData create(String field, int count, int distinctCount) {
      if(count == 0) {
         return new CardinalityData(field, 0, 0);
      }

      return new CardinalityData(field, distinctCount, distinctCount * 100.0 / count);
   }

   /**
    * Getter for the target binding field.
    */
   public String getField() {
       return field;
   }

   /**
    * Getter for the cardinality for a binding field.
    */
   public int getCardinality() {
       return cardinality;
   }

   /**
    * Get the percent of distinct values in the dataset.
    */
   public double getCardinalityPercentage() {
       return cardinalityPercentage;
   }

   /**
    * Get the approximate sampling size.
    */
   public int getDataSetSize() {
      return (int) (cardinality * 100 / cardinalityPercentage);
   }

   @Override
   public String toString() {
      return "CardinalityData: { " + field + ", " + cardinality
         + ", " + cardinalityPercentage + " }";
   }

   private String field;
   private int cardinality;
   private double cardinalityPercentage;
}