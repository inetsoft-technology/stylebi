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
package inetsoft.report.internal.table;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.text.Format;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77803: with format.number.round set, a decimal format must keep the extended patterns
 * (K/M/B, user suffixes, "%" literal, MMW) and round the number as displayed.
 * Bug #77802: the rounding option rounds at the last displayed digit, % multiplier included.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableFormatRoundingTest {
   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      TableFormat.invalidateTableFormatCache();
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(NUMBER_ROUND);
      SreeEnv.remove(PERCENT_ROUND);
      Tool.clearUserMessage();
      TableFormat.invalidateTableFormatCache();
   }

   @ParameterizedTest
   @ValueSource(strings = { "ROUND_DOWN", "ROUND_HALF_EVEN", "ROUND_HALF_UP", "BOGUS" })
   void extendedPatternsAreKept(String rounding) {
      SreeEnv.setProperty(NUMBER_ROUND, rounding);

      assertInstanceOf(ExtendedDecimalFormat.class, decimal("#,##0.0K"));
      assertEquals("1.2K", decimal("#,##0.0K").format(1234.0));
      assertEquals("1.2K", decimal("#.#B").format(1234.0));
      assertEquals("1.2M", decimal("#.#B").format(1234567.0));
      assertEquals("12%", decimal("#,##0\"%\"").format(12L));
      assertEquals("12%", decimal("#,##0\"%\"").format(12.0));
      assertEquals("Jan w2", decimal("MMW").format(12L));
   }

   @ParameterizedTest
   @CsvSource({
      "ROUND_DOWN, '#,##0.0K', 1299, 1.2K",
      "ROUND_UP, '#,##0.0K', 1201, 1.3K",
      "ROUND_DOWN, '#,##0.00M', 1234567, 1.23M",
      "ROUND_DOWN, #.#B, -1234567890, -1.2B",
      "ROUND_FLOOR, #.#B, -1234567890, -1.3B",
      // Bug #77802 on the format.number.round path
      "ROUND_DOWN, '#,##0.00%', 0.12345, 12.34%",
      "ROUND_UP, '#,##0.00%', 0.12341, 12.35%",
      "ROUND_DOWN, '0.00;(0.00)', -1.019, (1.01)",
      // small values the JDK would round to zero, and no -0
      "ROUND_UP, 0.00, 0.0004, 0.01",
      "ROUND_CEILING, 0.00, 0.0004, 0.01",
      "ROUND_UP, '#,##0', 0.04, 1",
      "ROUND_UP, 0.00, -0.00004, -0.01",
      "ROUND_FLOOR, 0.00, -0.00004, -0.01",
      "ROUND_UP, '#,##0', -0.004, -1",
      "ROUND_UP, 0.0, -0.0004, -0.1",
      "ROUND_UP, 0%, -0.004, -1%",
      "ROUND_HALF_UP, '#,##0.00%', -0.00005, -0.01%",
      "ROUND_DOWN, '#,##0.###‰', -0.0000024588, -0.002‰",
      "ROUND_DOWN, 0.00, -0.00004, 0.00",
      "ROUND_DOWN, 0.00, -0.0, 0.00",
      "ROUND_DOWN, 0.00, -0.004, 0.00",
      "ROUND_HALF_UP, 0.00, -0.001, 0.00",
      "ROUND_HALF_UP, 0.00, 1.005, 1.01"
   })
   void numberIsRoundedAsDisplayed(String rounding, String spec, double value, String expected) {
      SreeEnv.setProperty(NUMBER_ROUND, rounding);
      assertEquals(expected, decimal(spec).format(value));
   }

   @Test
   void userSuffixIsKeptAndRounded() throws Exception {
      Map<String, Long> mapping = userFormatMapping();
      mapping.put("wan", 10000L);

      try {
         SreeEnv.setProperty(NUMBER_ROUND, "ROUND_DOWN");
         assertEquals("12.34wan", decimal("#,###.00wan").format(123459.0));
         SreeEnv.setProperty(NUMBER_ROUND, "ROUND_UP");
         TableFormat.invalidateTableFormatCache();
         assertEquals("12.35wan", decimal("#,###.00wan").format(123459.0));
      }
      finally {
         mapping.remove("wan");
      }
   }

   @Test
   void infinityIsFormatted() {
      SreeEnv.setProperty(NUMBER_ROUND, "ROUND_UP");
      assertEquals("∞", decimal("#,##0").format(Double.POSITIVE_INFINITY));
   }

   @Test
   void invalidRoundingKeepsWorkingHalfEvenFormat() {
      SreeEnv.setProperty(NUMBER_ROUND, "BOGUS");
      Format fmt = decimal("0.00");

      assertInstanceOf(ExtendedDecimalFormat.class, fmt);
      assertEquals(RoundingMode.HALF_EVEN, ((ExtendedDecimalFormat) fmt).getRounding());
      assertEquals("1.50", fmt.format(1.5));
      assertEquals("0.12", fmt.format(0.125));
      assertTrue(Tool.existUserMessage("Failed to get format \"" + TableFormat.DECIMAL_FORMAT +
                                          "\" for specification \"0.00\" in locale " + Locale.US));
   }

   @Test
   void withoutRoundingPropertyFormatIsUnchanged() {
      Format fmt = decimal("#,##0.0K");
      assertInstanceOf(ExtendedDecimalFormat.class, fmt);
      assertNull(((ExtendedDecimalFormat) fmt).getRounding());
      assertEquals("1.3K", fmt.format(1299.0));
      assertEquals("40.29", decimal("##0.00").format(40.285));
   }

   // format.percent.round rounds to a whole percent (Bug #6026), ROUND_HALF_EVEN keeps a decimal
   @ParameterizedTest
   @CsvSource({
      "ROUND_DOWN, 0.123456, 12%",
      "ROUND_UP, 0.12341, 13%",
      "ROUND_UP, 0.0049, 1%",
      "ROUND_HALF_UP, 0.12341, 12%",
      "ROUND_HALF_EVEN, 0.12345, 12.3%",
      "ROUND_HALF_EVEN, 3.084, 308.4%",
      "BOGUS, 0.12345, 12.3%"
   })
   void percentRoundingIsUnchanged(String rounding, double value, String expected) {
      SreeEnv.setProperty(PERCENT_ROUND, rounding);
      Format fmt = TableFormat.getFormat(TableFormat.PERCENT_FORMAT, null, Locale.US);
      assertEquals(expected, fmt.format(value));
   }

   private static Format decimal(String spec) {
      return TableFormat.getFormat(TableFormat.DECIMAL_FORMAT, spec, Locale.US);
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Long> userFormatMapping() throws Exception {
      Field field = ExtendedDecimalFormat.class.getDeclaredField("mapping");
      field.setAccessible(true);
      return (Map<String, Long>) field.get(null);
   }

   private static final String NUMBER_ROUND = "format.number.round";
   private static final String PERCENT_ROUND = "format.percent.round";
}
