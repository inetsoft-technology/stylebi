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

import inetsoft.sree.SreeEnv;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.*;
import java.util.*;

/**
 * A format class that supports different rounding options.
 */
public class ExtendedDecimalFormat extends DecimalFormat {
   // the implicit value before the rounding option was added; old streams load with no option
   private static final long serialVersionUID = -881877823559828940L;
   public static final String AUTO_FORMAT = "#.#B";

   /**
    * Create an empty format. The format pattern must be set before it's used.
    */
   public ExtendedDecimalFormat() {
      setRoundingMode2(RoundingMode.HALF_UP);
   }

   /**
    * Create a format with pattern.
    */
   public ExtendedDecimalFormat(String pattern) {
      applyPattern(pattern);
      setRoundingMode2(RoundingMode.HALF_UP);
   }

   /**
    * Create a format with pattern.
    */
   public ExtendedDecimalFormat(String pattern, DecimalFormatSymbols symbols) {
      setDecimalFormatSymbols(symbols);
      applyPattern(pattern);
      setRoundingMode2(RoundingMode.HALF_UP);
   }

   /**
    * If increment is set, and the increment is less than the unit (e.g. 1000 for K) specified
    * in the format, the unit is downgraded until it is less than the increment.
    * @return a new format with the increment set or self if the increment is the same as self.
    */
   public ExtendedDecimalFormat setIncrement(double increment) {
      if(this.increment == increment) {
         return this;
      }

      ExtendedDecimalFormat fmt = (ExtendedDecimalFormat) clone();
      fmt.increment = increment;
      return fmt;
   }

   public double getIncrement() {
      return increment;
   }

   /**
    * Set the rounding option. It rounds the number as displayed, i.e. after it's divided by the
    * K/M/B or user suffix unit and multiplied for % or per mille, at the last displayed digit.
    * Without an option (null), the JDK rounding mode (HALF_UP by default) is used as is.
    */
   public void setRounding(RoundingMode rounding) {
      setRoundingMode(rounding == null ? RoundingMode.HALF_UP : rounding);
      this.rounding = rounding;
   }

   /**
    * Get the rounding option, or null if it's not set.
    */
   public RoundingMode getRounding() {
      return rounding;
   }

   /**
    * Set the rounding option by using the string name of the options, e.g. "ROUND_DOWN".
    *
    * @throws RuntimeException if the name is not a rounding option. The option is not changed.
    */
   public void setRoundingByName(String round) {
      setRounding(RoundingMode.valueOf(RoundDecimalFormat.roundingByName(round)));
   }

   /**
    * Sets the {@link java.math.RoundingMode} used in this DecimalFormat.
    *
    * @param roundingMode The <code>RoundingMode</code> to be used
    */
   private void setRoundingMode2(RoundingMode roundingMode) {
      try {
         setRoundingMode(roundingMode);
      }
      catch(Exception ignore) {
      }
   }

   /**
    * Apply pattern.
    */
   @Override
   public void applyPattern(String pattern) {
      if(pattern != null && pattern.length() > 0) {
         int len = pattern.length();
         String lfmt = null;
         String sfmt = pattern.substring(len - 1);
         Set<String> keys = mapping.keySet();

         if(len > USER_FMT_PREFIX.length()) {
            lfmt = pattern.substring(USER_FMT_PREFIX.length());
         }

         if(lfmt != null && keys.contains(lfmt)) {
            pattern = pattern.substring(0, len - lfmt.length());
            userSymbol = lfmt;
         }
         else if(lfmt != null && decimalPlacesLength(lfmt) > 0) {
            int length = decimalPlacesLength(lfmt);
            pattern = pattern.substring(0, USER_FMT_PREFIX.length() + length);
            userSymbol = lfmt.substring(length);
         }
         else if(keys.contains(sfmt)) {
            pattern = pattern.substring(0, len - 1);
            userSymbol = sfmt;
         }
         else if(pattern.endsWith("\"%\"")) {
            pattern = pattern.substring(0, len - 3);
            userSymbol = "\"%\"";
         }
         else {
            char last = pattern.charAt(len - 1);

            if(last == 'K' || last == 'k' ||last == 'M' || last == 'm' ||
               last == 'B' || last == 'b')
            {
               pattern = pattern.substring(0, len - 1);
               this.symbol = last;
            }
         }
      }

      // an empty pattern (e.g. the pattern was only a K/M/B suffix) leaves DecimalFormat with
      // unlimited integer/fraction digits, and toPattern() would then exhaust the heap
      super.applyPattern(DecimalPatternUtil.normalizeEmptyPattern(pattern));
   }

   /**
    * Format a number of double type.
    */
   @Override
   public StringBuffer format(double num, StringBuffer result,
                              FieldPosition fieldPosition)
   {
      return format(num, result, fieldPosition, true);
   }

   /**
    * Format a number of long type.DateComparisonUtil
    */
   @Override
   public StringBuffer format(long num, StringBuffer result,
                              FieldPosition fieldPosition)
   {
      return format(num, result, fieldPosition, false);
   }

   /**
    * Format a number.
    */
   private StringBuffer format(Number num, StringBuffer result,
      FieldPosition fieldPosition, boolean isDouble)
   {
      long multiple = 1;
      char symbol = this.symbol;
      final String opattern = getSimplePattern();
      String npattern = null;

      // special format for MM (month) W (week-of-month). formats into "Jan w1"
      if("MMW#".equals(opattern)) {
         int n = num.intValue();
         return new StringBuffer(ExtendedDateFormat.applyMonthInfo("MM", n / 10) + " w" + (n % 10));
      }

      if(!"".equals(userSymbol)) {
         multiple = getMultiple(userSymbol);
      }
      else if(symbol != ' ') {
         switch(symbol) {
         case 'k':
         case 'K':
            multiple = 1000;
            break;
         case 'm':
         case 'M':
            multiple = 1000000;
            break;
         case 'b':
         case 'B':
            multiple = 1000000000L;
            break;
         }

         // abs since scale could be reversed
         double increment = Math.abs(this.increment);
         double nval = Math.abs(num.doubleValue());

         // avoid 0B even if not used on axis
         if(increment == 0 && nval > 0) {
            int dot = opattern.lastIndexOf('.');
            int precision = dot > 0 ? opattern.length() - dot - 1 : 0;
            increment = Math.pow(10, Math.log10(nval) - precision);
         }

         // if increment is set, auto downgrade to make sure the unit is less than the increment
         if(increment > 0 && isAutoDowngrade()) {
            // if increment is set, only adjust if it's fraction and has decimal
            if(opattern.contains(".") && (this.increment == 0 || increment - (int) increment > 0)) {
               int dot = opattern.indexOf('.');

               for(int decimalPlace = opattern.length() - dot - 1; decimalPlace > 0;
                   decimalPlace--)
               {
                  increment *= 10;
               }
            }

            // if the increment is less than the unit, downgrade the unit so we don't have 1K, 1K
            // as axis labels
            while(multiple > 1 && multiple > increment) {
               multiple /= 1000;

               switch((int) multiple) {
               case 1:
                  symbol = ' ';
                  break;
               case 1000:
                  symbol = 'K';
                  break;
               case 1000000:
                  symbol = 'M';
                  break;
               }
            }

            // if pattern has no decimal point, and the increment is less that 1, add
            // decimal points so the increments don't show up as identical numbers.
            if(increment < 1 && multiple == 1 && opattern.indexOf('.') < 0) {
               boolean percent = opattern.endsWith("%");
               npattern = (percent ? opattern.substring(0, opattern.length() - 1) : opattern)
                  + ".#";

               for(double incr = increment * 10; incr < 1; incr *= 10) {
                  npattern += "#";
               }

               if(percent) {
                  npattern += "%";
               }

               super.applyPattern(npattern);
            }
            // try to keep three decimal places when increment is less than 1 and
            // apply the auto downgrade the unit
            else if(Math.abs(this.increment) < 1 && multiple == 1 && symbol != this.symbol) {
               String incrementStr = this.increment + "";
               int incrementDecimalCount = incrementStr.length() -
                  incrementStr.lastIndexOf(".") - 1;
               incrementDecimalCount = Math.min(incrementDecimalCount, 3);
               int addNum = 0;

               if(opattern.contains(".")) {
                  int patternDecimalCount = opattern.length() - opattern.lastIndexOf(".") - 1;

                  if(patternDecimalCount < incrementDecimalCount) {
                     addNum = incrementDecimalCount - patternDecimalCount;
                     npattern = opattern;
                  }
               }
               else {
                  addNum = incrementDecimalCount;
                  npattern = opattern + ".";
               }

               if(addNum > 0) {
                  npattern += StringUtils.repeat("#", addNum);
                  super.applyPattern(npattern);
               }
            }
         }
      }

      if(Double.isNaN(num.doubleValue())) {
         return new StringBuffer("NaN");
      }

      // keep fraction if double, or having fraction in pattern and k/m/b (54105).
      boolean fractional = isDouble || multiple > 1 && opattern.indexOf('.') >= 0;

      // avoid "-0". A rounding option never gives -0 (see below), and must not zero a value it
      // rounds away from zero
      if(rounding == null && fractional && num.doubleValue() < 0) {
         int fraction = getMaximumFractionDigits();
         double min = Math.pow(0.1, fraction + 2);

         if(num.doubleValue() > -min) {
            num = 0;
         }
      }

      // a rounding option can throw (ROUND_UNNECESSARY), so always restore the pattern
      try {
         StringBuffer sb = fractional ?
            formatFraction(num.doubleValue() / multiple, opattern, result, fieldPosition) :
            super.format(num.longValue() / multiple, result, fieldPosition);

         if(!"".equals(userSymbol)) {
            sb.append(userSymbol.equals("\"%\"") ? "%" : userSymbol);
         }
         // append K/M/B, don't show 0B
         else if(symbol != ' ' &&
            (num.doubleValue() != 0 || symbol != 'B' || this.increment != 0))
         {
            sb.append(symbol);
         }

         return sb;
      }
      finally {
         // if pattern modified, restore old pattern
         if(npattern != null) {
            super.applyPattern(opattern);
         }
      }
   }

   /**
    * Format a number that has been divided by the unit (K/M/B or user suffix).
    */
   private StringBuffer formatFraction(double num, String opattern, StringBuffer result,
                                       FieldPosition fieldPosition)
   {
      if(rounding == null) {
         return super.format(new BigDecimal(Double.toString(num)), result, fieldPosition);
      }

      // infinity has no digits to round, and no BigDecimal
      if(Double.isInfinite(num)) {
         return super.format(num, result, fieldPosition);
      }

      // an exponent pattern shows significant digits, so the digit to round at depends on the
      // value, and the JDK rounding mode is the rounding option
      if(usesExponent(opattern)) {
         return super.format(new BigDecimal(Double.toString(num)), result, fieldPosition);
      }

      // round at the last displayed digit of the pattern in use (an auto downgrade may have
      // added fraction digits). The JDK rounds 0.0004 to 0.00 for ROUND_UP, so it can't be
      // left to the JDK rounding mode, and a rounded BigDecimal has no negative zero.
      return super.format(RoundDecimalFormat.roundAtDisplayedDigit(
         num, getMaximumFractionDigits(), getMultiplier(), rounding), result, fieldPosition);
   }

   /**
    * Check if the pattern shows the number in scientific notation. A quoted 'E' in a prefix or
    * suffix is not an exponent, but toPattern() drops the quotes, so ask the formatter itself.
    */
   private boolean usesExponent(String opattern) {
      if(opattern.indexOf('E') < 0) {
         return false;
      }

      FieldPosition exponent = new FieldPosition(NumberFormat.Field.EXPONENT);
      super.format(1.0, new StringBuffer(), exponent);
      return exponent.getEndIndex() > exponent.getBeginIndex();
   }

   @Override
   public Number parse(String text, ParsePosition pos) {
      long multiple = 1;

      if(text.endsWith(userSymbol)) {
         multiple = getMultiple(userSymbol);
      }
      else {
         if(text.endsWith("k") || text.endsWith("K")) {
            multiple = 1000;
         }
         else if(text.endsWith("m") || text.endsWith("M")) {
            multiple = 1000000;
         }
         else if(text.endsWith("b") || text.endsWith("B")) {
            multiple = 1000000000L;
         }
      }

      Number val = super.parse(text, pos);

      return val == null ? null : val.doubleValue() * multiple;
   }

   /**
    * get the Suffix.
    */
   public static Set<String> getSuffix() {
      return mapping.keySet();
   }

   /**
    * Get extended data format.
    */
   public char[] getExtendedDataFormat() {
      return EXT_DATA_FMT;
   }

   /**
    * Get extended format's symbols.
    */
   public String[] getExtendedSymbols() {
      return new String[] {symbol + "", userSymbol};
   }

   /**
    * Gets the extended data symbol.
    */
   public char getExtendedDataSymbol() {
      return symbol;
   }

   /**
    * get the Multiple.
    */
   private long getMultiple(String str) {
      if(str == null || !mapping.containsKey(str)) {
         return 1;
      }

      return mapping.get(str) == 0 ? 1 : mapping.get(str);
   }

   private static void initUserFormatFile() {
      String home = ConfigurationContext.getContext().getHome();

      if(home == null) {
         return;
      }

      DataSpace space;

      try {
         space = DataSpace.getDataSpace();
      }
      catch(Exception ex) {
         LOG.error("Failed to get dataspace", ex);
         return;
      }

      String file = "userformat.xml";

      try(InputStream fis = ExtendedDecimalFormat.class.getResourceAsStream(file)) {
         if(fis != null) {
            space.withOutputStream(home, file, out -> IOUtils.copy(fis, out));
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to write user format file", ex);
      }
   }

   private static void initUserFormat() {
      InputStream input;
      String file = "userformat.xml";
      DataSpace space = DataSpace.getDataSpace();
      space.removeChangeListener(null, file, changeListener);
      mapping.clear();

      if(space.exists(null, file)) {
         try {
            input = space.getInputStream(null, file);
         }
         catch(Exception ex) {
            LOG.error("Failed to get input stream", ex);
            return;
         }
      }
      else {
         initUserFormatFile();
         input = ExtendedDecimalFormat.class.getResourceAsStream(file);
      }

      space.addChangeListener(null, file, changeListener);
      Document doc;

      try {
         doc = Tool.parseXML(input);
      }
      catch(Exception ex) {
         LOG.error("Failed to parse user format", ex);
         return;
      }
      finally {
         IOUtils.closeQuietly(input);
      }

      Element em = doc.getDocumentElement();
      NodeList list = em.getChildNodes();
      int length = list.getLength();

      for(int i = 0; i < length; i++) {
         Node node = list.item(i);

         if(!(node instanceof Element)) {
            continue;
         }

         Element elem = (Element) node;
         Attr attr = elem.getAttributeNode("suffix");
         Attr val = elem.getAttributeNode("multiplier");

         if(attr == null || val == null) {
            continue;
         }

         String key = attr.getValue();
         long value;

         try {
            value = Long.parseLong(val.getValue());
         }
         catch(Exception ex) {
            continue;
         }

         if(value <= 0) {
            continue;
         }

         mapping.put(key, value);
      }

      try {
         if(input != null) {
            input.close();
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to release input stream", ex);
      }
   }

   public String getSimplePattern() {
      return super.toPattern();
   }

   private boolean isAutoDowngrade() {
      return !"false".equals(SreeEnv.getProperty("format.auto.downgrade"));
   }

   @Override
   public String toPattern() {
      String opattern = getSimplePattern();

      if(symbol != ' ') {
         opattern += symbol;
      }

      if(userSymbol != null && !userSymbol.isEmpty()) {
         opattern += userSymbol;
      }

      return opattern;
   }

   @Override
   public String toString() {
      return super.toString() + "[" + toPattern() + "]";
   }

   /**
    * Check if the format contains extended rounding options.
    */
   public static boolean isExtendedFormat(String pattern) {
      if(pattern.length() > 0) {
         char last = pattern.charAt(pattern.length() - 1);

         for(char c : EXT_DATA_FMT) {
            if(c == last) {
               return true;
            }
         }
      }

      return false;
   }

   private int decimalPlacesLength(String lfmt) {
      Set<String> keys = mapping.keySet();

      for(String key : keys) {
         if(lfmt.endsWith(key)) {
            String decimalPlaceStr = lfmt.substring(0, lfmt.lastIndexOf(key));

            if(decimalPlaceStr.matches("\\.(0+)")) {
               return decimalPlaceStr.length();
            }
         }
      }

      return 0;
   }

   private char symbol = ' ';
   private String userSymbol = "";
   private double increment = 0;
   // the rounding option, null if not set
   private RoundingMode rounding;
   private static Map<String, Long> mapping = new HashMap<>();
   private static final char[] EXT_DATA_FMT = {'B', 'K', 'M', 'b', 'k', 'm'};
   public static final String USER_FMT_PREFIX = "#,###";
   private static final Logger LOG =
      LoggerFactory.getLogger(ExtendedDecimalFormat.class);

   private static final DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());
      initUserFormat();
   };

   static {
      initUserFormat();
   }
}
