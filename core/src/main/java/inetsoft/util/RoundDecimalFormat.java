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
package inetsoft.util;

import java.math.BigDecimal;
import java.text.*;

/**
 * A format class that supports different rounding options.
 */
public class RoundDecimalFormat extends DecimalFormat {
   /**
    * Create an empty format. The format pattern must be set before it's used.
    */
   public RoundDecimalFormat() {
   }

   /**
    * Create a format with default rounding (ROUND_HALF_EVEN).
    */
   public RoundDecimalFormat(String fmt) {
      super(fmt);
   }

   /**
    * Create a format with default rounding (ROUND_HALF_EVEN).
    */
   public RoundDecimalFormat(String pattern, DecimalFormatSymbols symbols) {
      super(pattern, symbols);
   }

   /**
    * Format a number.
    */
   @Override
   public StringBuffer format(double num, StringBuffer result,
                              FieldPosition fieldPosition) {
      if(rounding != BigDecimal.ROUND_HALF_EVEN) {
         String fmtstr = toPattern();
         int idx = fmtstr.lastIndexOf(".");
         boolean hasDecimal = ("" + num).lastIndexOf(".") >= 0;

         if(idx >= 0 ||
            // @by yanie: bug1423240440182
            // Dealing format a decimal to an int like format("#,##0", etc)
            hasDecimal)
         {
            int scale = idx >= 0 ? fmtstr.length() - idx - 1 : 0;
            BigDecimal dec = new BigDecimal(Double.toString(num));
            dec = dec.setScale(scale, rounding);
            num = dec.doubleValue();
         }
      }

      return super.format(num, result, fieldPosition);
   }

   /**
    * Set the rounding option. The option can be any of the BigDecimal rounding
    * options.
    */
   public void setRounding(int rounding) {
      this.rounding = rounding;
   }

   /**
    * Get the current rounding option.
    */
   public int getRounding() {
      return rounding;
   }

   /**
    * Set the rounding option by using the string name of the options.
    */
   public void setRoundingByName(String round) {
      if(round.equals("ROUND_UP")) {
         this.rounding = BigDecimal.ROUND_UP;
      }
      else if(round.equals("ROUND_DOWN")) {
         this.rounding = BigDecimal.ROUND_DOWN;
      }
      else if(round.equals("ROUND_CEILING")) {
         this.rounding = BigDecimal.ROUND_CEILING;
      }
      else if(round.equals("ROUND_FLOOR")) {
         this.rounding = BigDecimal.ROUND_FLOOR;
      }
      else if(round.equals("ROUND_HALF_UP")) {
         this.rounding = BigDecimal.ROUND_HALF_UP;
      }
      else if(round.equals("ROUND_HALF_DOWN")) {
         this.rounding = BigDecimal.ROUND_HALF_DOWN;
      }
      else if(round.equals("ROUND_HALF_EVEN")) {
         this.rounding = BigDecimal.ROUND_HALF_EVEN;
      }
      else if(round.equals("ROUND_UNNECESSARY")) {
         this.rounding = BigDecimal.ROUND_UNNECESSARY;
      }
      else {
         throw new RuntimeException("Rounding option is not valid: " + round);
      }
   }

   private int rounding = BigDecimal.ROUND_HALF_EVEN;
}
