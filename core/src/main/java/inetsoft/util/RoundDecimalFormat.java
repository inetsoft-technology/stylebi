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
package inetsoft.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.*;

/**
 * A format class that supports different rounding options.
 */
public class RoundDecimalFormat extends DecimalFormat {
   // the implicit value before the pattern overrides were added; the serialized fields are unchanged
   private static final long serialVersionUID = 3176001257543027570L;

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
      // the DecimalFormat constructor does not call the overridden applyPattern()
      boundEmptyPattern(fmt);
   }

   /**
    * Create a format with default rounding (ROUND_HALF_EVEN).
    */
   public RoundDecimalFormat(String pattern, DecimalFormatSymbols symbols) {
      super(pattern, symbols);
      boundEmptyPattern(pattern);
   }

   /**
    * Apply a pattern.
    */
   @Override
   public void applyPattern(String pattern) {
      super.applyPattern(pattern);
      boundEmptyPattern(pattern);
   }

   /**
    * Apply a localized pattern.
    */
   @Override
   public void applyLocalizedPattern(String pattern) {
      super.applyLocalizedPattern(pattern);
      boundEmptyPattern(pattern);
   }

   /**
    * An empty pattern leaves Integer.MAX_VALUE maximum fraction digits, and toPattern() (called
    * by format() for any rounding other than ROUND_HALF_EVEN) would then build a string of about
    * 2^31 chars. Keep the empty pattern's formatting but cap the fraction digits at the most a
    * double can show. Unlike substituting a "#,##0.###" pattern, this keeps the JDK's
    * DecimalFormat fast path (which ignores the rounding option) from applying, and gives the
    * same output as a plain DecimalFormat("").
    */
   private void boundEmptyPattern(String pattern) {
      if(pattern != null && pattern.isEmpty()) {
         setMaximumFractionDigits(DOUBLE_FRACTION_DIGITS);
      }
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

         // the JDK rounding mode follows the rounding option (so the JDK fast path in
         // format(double) and the BigDecimal/long paths cannot skip it), but the value is
         // already rounded here, so round any remaining digits HALF_EVEN as before
         RoundingMode mode = getRoundingMode();
         // format(double) passes the JDK's DontCareFieldPosition, for which the JDK would try
         // its HALF_EVEN fast path again; a plain FieldPosition gives the same text without it
         FieldPosition pos = "java.text.DontCareFieldPosition".equals(
            fieldPosition.getClass().getName()) ? new FieldPosition(0) : fieldPosition;
         setRoundingMode(RoundingMode.HALF_EVEN);

         try {
            return super.format(num, result, pos);
         }
         finally {
            setRoundingMode(mode);
         }
      }

      return super.format(num, result, fieldPosition);
   }

   /**
    * Set the rounding option. The option can be any of the BigDecimal rounding
    * options.
    */
   public void setRounding(int rounding) {
      // throws IllegalArgumentException for a value that is not a rounding option
      RoundingMode mode = RoundingMode.valueOf(rounding);
      this.rounding = rounding;
      setRoundingMode(mode);
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

      setRoundingMode(RoundingMode.valueOf(rounding));
   }

   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();

      // a format serialized before the JDK rounding mode followed the rounding option
      if(rounding != BigDecimal.ROUND_HALF_EVEN && getRoundingMode() == RoundingMode.HALF_EVEN) {
         try {
            setRoundingMode(RoundingMode.valueOf(rounding));
         }
         catch(IllegalArgumentException ignore) {
            // not a rounding option, format() fails as it did before
         }
      }
   }

   // the most fraction digits DecimalFormat can show for a double
   private static final int DOUBLE_FRACTION_DIGITS = 340;
   private int rounding = BigDecimal.ROUND_HALF_EVEN;
}
