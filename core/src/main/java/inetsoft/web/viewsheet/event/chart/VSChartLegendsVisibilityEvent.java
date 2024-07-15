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
package inetsoft.web.viewsheet.event.chart;

import java.util.List;

/**
 * Class that encapsulates the parameters for showing/hiding a chart's legends.
 *
 * @since 12.3
 */
public class VSChartLegendsVisibilityEvent extends VSChartEvent {
   /**
    * Gets if the event it to hide a legend.
    *
    * @return true if should hide.
    */
   public boolean isHide() {
      return hide;
   }

   /**
    * Gets the axis field.
    *
    * @return the axis field.
    */
   public String getField() {
      return field;
   }

   /**
    * Gets the axis target fields.
    *
    * @return the axis target fields.
    */
   public List<String> getTargetFields() {
      return targetFields;
   }

   /**
    * Gets the axis aesthetic type.
    *
    * @return the axis aesthetic type.
    */
   public String getAestheticType() {
      return aestheticType;
   }

   /**
    * Gets if the event from vsWizard.
    */
   public boolean isWizard() {
      return wizard;
   }

   /**
    * Gets if the color legend is merged into another legend
    */
   public boolean isColorMerged() {
      return colorMerged;
   }

   public boolean isNodeAesthetic() {
      return nodeAesthetic;
   }

   public void setNodeAesthetic(boolean nodeAesthetic) {
      this.nodeAesthetic = nodeAesthetic;
   }

   private boolean hide;
   private String field;
   private List<String> targetFields;
   private String aestheticType;
   private boolean wizard;
   private boolean colorMerged;
   private boolean nodeAesthetic;
}
