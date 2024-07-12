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
package inetsoft.web.adhoc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.StyleConstants;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlignmentInfo {
   public AlignmentInfo() {
   }

   public AlignmentInfo(int align) {
      fixAlignment(align);
   }

   public void fixAlignment(int align) {
      if(align == 0) {
         return;
      }

      if((align & StyleConstants.H_LEFT) == StyleConstants.H_LEFT) {
         setHalign(LEFT);
      }
      else if((align & StyleConstants.H_CENTER) == StyleConstants.H_CENTER) {
         setHalign(CENTER);
      }
      else if((align & StyleConstants.H_RIGHT) == StyleConstants.H_RIGHT
              || (align & StyleConstants.H_CURRENCY) == StyleConstants.H_CURRENCY)
      {
         setHalign(RIGHT);
      }

      if((align & StyleConstants.V_TOP) == StyleConstants.V_TOP) {
         setValign(TOP);
      }
      else if((align & StyleConstants.V_CENTER) == StyleConstants.V_CENTER) {
         setValign(MIDDLE);
      }
      else if((align & StyleConstants.V_BOTTOM) == StyleConstants.V_BOTTOM) {
         setValign(BOTTOM);
      }
      else if((align & StyleConstants.V_BASELINE) == StyleConstants.V_BASELINE) {
         setValign(BASELINE);
      }
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getHalign() {
      return halign;
   }

   public void setHalign(String align) {
      this.halign = align;
   }

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   public String getValign() {
      return valign;
   }

   public void setValign(String align) {
      this.valign = align;
   }

   public int toAlign() {
      int nalign = 0;

      if(LEFT.equals(halign)) {
         nalign |= StyleConstants.H_LEFT;
      }
      else if(CENTER.equals(halign)) {
         nalign |= StyleConstants.H_CENTER;
      }
      else if(RIGHT.equals(halign)) {
         nalign |= StyleConstants.H_RIGHT;
      }

      if(TOP.equals(valign)) {
         nalign |= StyleConstants.V_TOP;
      }
      else if(MIDDLE.equals(valign)) {
         nalign |= StyleConstants.V_CENTER;
      }
      else if(BOTTOM.equals(valign)) {
         nalign |= StyleConstants.V_BOTTOM;
      }

      return nalign;
   }

   public void convertToValign() {
      if(LEFT.equals(halign)) {
         valign = BOTTOM;
      }
      else if(CENTER.equals(halign)) {
         valign = MIDDLE;
      }
      else if(RIGHT.equals(halign)) {
         valign = TOP;
      }

      halign = null;
   }

   public void convertToHalign() {
      if(TOP.equals(valign)) {
         halign = RIGHT;
      }
      else if(MIDDLE.equals(valign)) {
         halign = CENTER;
      }
      else if(BOTTOM.equals(valign)) {
         halign = LEFT;
      }

      valign = null;
   }

   @JsonInclude(JsonInclude.Include.NON_DEFAULT)
   public boolean isAuto() {
      return halign == null && valign == null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof AlignmentInfo)) {
         return false;
      }

      return toAlign() == ((AlignmentInfo) obj).toAlign();
   }

   @Override
   public int hashCode() {
      return toAlign();
   }

   @Override
   public String toString() {
      return super.toString() + "[" + halign + "," + valign + "]";
   }

   private static final String LEFT = "Left";
   private static final String CENTER = "Center";
   private static final String RIGHT = "Right";
   private static final String TOP = "Top";
   private static final String MIDDLE = "Middle";
   private static final String BOTTOM = "Bottom";
   private static final String BASELINE = "Baseline";
   private String halign;
   private String valign;
}
