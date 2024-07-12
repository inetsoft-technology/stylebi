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
package inetsoft.graph.guide.axis;

import java.util.Objects;

/**
 * Label value and anchor if the label is abbreviated. Anchor is the string that should be
 * displayed if there is no previous year label showing the year. For example, if '2021 Jan' is
 * abbreviated to '2021', anchor is '2021'. If '2021 Feb' is abbreviated to 'Feb',
 * anchor is '2021'. For labels that keeps year, for example, '2021 1st, 2021 2nd', the
 * first abbreviation would be ('2021 1st, '2021 1st') and the second ('2nd', '2021 2nd').
 * If the first name is removed, second label would be changed from '2nd' to '2021 2nd' so
 * the year part is not lost.
 */
class Abbreviation {
   public Abbreviation(Object label, String anchor) {
      this.label = label;
      this.anchor = anchor;
   }

   public Object getLabel() {
      return label;
   }

   public void setLabel(Object label) {
      this.label = label;
   }

   public String getAnchor() {
      return anchor;
   }

   public void setAnchor(String anchor) {
      this.anchor = anchor;
   }

   public static boolean isAnchor(Object label, String anchor) {
      if(Objects.equals(label, anchor)) {
         return true;
      }

      return false;
   }

   public static void setAnchor(VDimensionLabel vlabel, String anchor) {
      vlabel.setLabel(anchor);
   }

   private Object label;
   private String anchor;
}
