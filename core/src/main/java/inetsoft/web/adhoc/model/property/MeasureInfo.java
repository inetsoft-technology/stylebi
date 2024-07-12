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
package inetsoft.web.adhoc.model.property;

public class MeasureInfo {

   public MeasureInfo(){
   }

   public MeasureInfo(String name, String label, boolean isDateField) {
      this(name, label, isDateField, false, false);
   }

   public MeasureInfo(String name, String label, boolean isDateField, boolean isTimeField,
                      boolean isGroupOthers)
   {
      this.name = name;
      this.label = label;
      this.isDateField = isDateField;
      this.isTimeField = isTimeField;
      this.isGroupOthers = isGroupOthers;
   }

   /**
    * Gets measure.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets measure.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the view representation of this measure.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Sets the view representation of this measure.
    */
   public void setLabel(String label) {
      this.label = label;
   }
   /**
    * Check whehter is date field.
    */
   public boolean isDateField() {
      return isDateField;
   }

   /**
    * Sets whether is date field.
    */
   public void setDateField(boolean isDateField) {
      this.isDateField = isDateField;
   }

   /**
    * Get whether it's a time field
    */
   public boolean isTimeField() {
      return isTimeField;
   }

   /**
    * Set whether it's a time field
    */
   public void setTimeField(boolean timeField) {
      isTimeField = timeField;
   }

   /**
    * Check if contains grouped others value.
    */
   public boolean isGroupOthers() {
      return isGroupOthers;
   }

   /**
    * Set the group others value.
    */
   public void setGroupOthers(boolean groupOthers) {
      isGroupOthers = groupOthers;
   }

   private String name;
   private String label;
   private boolean isDateField;
   private boolean isTimeField;
   private boolean isGroupOthers;
}