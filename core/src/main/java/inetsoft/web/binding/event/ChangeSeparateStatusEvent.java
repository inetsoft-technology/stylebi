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
package inetsoft.web.binding.event;

/**
 * Class that encapsulates the parameters for change chart type event.
 *
 * @since 12.3
 */
public class ChangeSeparateStatusEvent {
   public ChangeSeparateStatusEvent() {
   }

   public ChangeSeparateStatusEvent(String name, boolean multi, boolean separate) {
      this.name = name;
      this.multi = multi;
      this.separate = separate;
   }

   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Whether if multi chart.
    * @return true if multi chart, otherwise false.
    */
   public boolean isMulti() {
      return multi;
   }

   /**
    * Set if multi chart.
    * @param multi the multi chart.
    */
   public void setMulti(boolean multi) {
      this.multi = multi;
   }

   /**
    * Whether if separate chart.
    * @return true if separate chart, otherwise false.
    */
   public boolean isSeparate() {
      return separate;
   }

   /**
    * Set if separate chart.
    * @param separate the separate chart.
    */
   public void setSeparate(boolean separate) {
      this.separate = separate;
   }

   private String name;
   private boolean multi;
   private boolean separate;
}
