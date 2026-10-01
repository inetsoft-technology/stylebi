/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.report.internal.Common;
import inetsoft.test.*;
import inetsoft.util.script.JSObject;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77500, an empty DecimalFormat pattern leaves the maximum fraction digits at
 * Integer.MAX_VALUE, so a later toPattern() builds a ~2^31 char string. Each product site that
 * builds a decimal format from a pattern must map exactly "" to the default pattern. The
 * fraction digits are checked before toPattern() is called so unfixed code fails fast.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class EmptyDecimalPatternTest {
   private static final String DEFAULT_PATTERN = "#,##0.###";

   @Test
   void normalizeEmptyPatternOnlyChangesEmptyString() {
      assertEquals(DEFAULT_PATTERN, DecimalPatternUtil.normalizeEmptyPattern(""));
      assertNull(DecimalPatternUtil.normalizeEmptyPattern(null));
      assertEquals(" ", DecimalPatternUtil.normalizeEmptyPattern(" "));
      assertEquals("0.00", DecimalPatternUtil.normalizeEmptyPattern("0.00"));
   }

   @Test
   void scriptFormatNumberEmptyPatternWithRounding() {
      // formatNumber with a rounding option uses RoundDecimalFormat, whose format() calls
      // toPattern(); check it is bounded first so a regression fails instead of exhausting the heap
      assertEquals(3, new RoundDecimalFormat("").getMaximumFractionDigits());
      assertEquals("1.5", JavaScriptEngine.formatNumber(1.5, "", "ROUND_HALF_UP"));
      assertEquals("1.235", JavaScriptEngine.formatNumber(1.23456, "", "ROUND_HALF_UP"));
      assertEquals("0.3", JavaScriptEngine.formatNumber(0.3, "", "ROUND_HALF_UP"));
   }

   /** Without rounding formatNumber uses a plain DecimalFormat, which is left unchanged. */
   @Test
   void scriptFormatNumberEmptyPatternWithoutRoundingIsUnchanged() {
      assertEquals("1.5", JavaScriptEngine.formatNumber(1.5, "", null));
      assertEquals("1.5", JavaScriptEngine.formatNumber(1.5, "", "ROUND_HALF_EVEN"));
      assertEquals("1.23456", JavaScriptEngine.formatNumber(1.23456, "", null));
   }

   @Test
   void commonGetFormatEmptyDecimalSpec() {
      DecimalFormat fmt = (DecimalFormat) Common.getFormat("DecimalFormat", "");
      assertEquals(3, fmt.getMaximumFractionDigits());
      assertEquals(DEFAULT_PATTERN, Common.getFormatPattern(fmt));
      assertEquals(new DecimalFormat("0.00").toPattern(),
                   Common.getFormatPattern(Common.getFormat("DecimalFormat", "0.00")));
   }

   @Test
   void jsObjectConvertEmptyStringToNumberFormat() {
      DecimalFormat fmt = (DecimalFormat) JSObject.convert("", NumberFormat.class);
      assertEquals(3, fmt.getMaximumFractionDigits());
      assertEquals(DEFAULT_PATTERN, fmt.toPattern());
   }
}
