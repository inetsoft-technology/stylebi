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

import inetsoft.test.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ExtendedDecimalFormatTest {
   @Test
   public void doublePrecisionRounding() {
      final ExtendedDecimalFormat format = new ExtendedDecimalFormat("##0.00");
      Assertions.assertEquals("40.29", format.format(40.285));
   }

   // Bug #77411, a pattern that is only a suffix letter must not exhaust the heap
   @Test
   public void suffixOnlyPatternUsesDefaultNumberPattern() {
      final ExtendedDecimalFormat format = new ExtendedDecimalFormat("m");
      Assertions.assertEquals("#,##0.###m", format.toPattern());
      Assertions.assertEquals("5m", format.format(5000000L));
      Assertions.assertEquals("1.5m", format.format(1500000.0));
   }

   // Bug #77803, a rounding option rounds the number as displayed, after the K/M/B division
   @ParameterizedTest
   @CsvSource({
      "'#,##0.0K', ROUND_DOWN, 1299, 1.2K",
      "'#,##0.0K', ROUND_UP, 1201, 1.3K",
      "'#,##0.0K', ROUND_HALF_EVEN, 1250, 1.2K",
      "#.#B, ROUND_DOWN, 1234, 1.2K",
      "#.#B, ROUND_DOWN, 1234567, 1.2M",
      "#.#B, ROUND_FLOOR, -1234567890, -1.3B",
      "'#,##0.00%', ROUND_DOWN, 0.12345, 12.34%",
      "0.00, ROUND_UP, 0.0004, 0.01",
      "0.00, ROUND_DOWN, -0.004, 0.00",
      "0.00, ROUND_HALF_UP, 1.005, 1.01",
      "0.00, ROUND_HALF_UP, 0.285, 0.29",
      "0.00E0, ROUND_DOWN, 12399, 1.23E4"
   })
   public void roundingOptionRoundsDisplayedNumber(String pattern, String mode, double value,
                                                   String expected)
   {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat(pattern, US);
      format.setRoundingByName(mode);
      Assertions.assertEquals(expected, format.format(value));
   }

   @Test
   public void withoutRoundingOptionOutputIsUnchanged() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.0K", US);
      Assertions.assertNull(format.getRounding());
      Assertions.assertEquals(RoundingMode.HALF_UP, format.getRoundingMode());
      Assertions.assertEquals("1.3K", format.format(1299.0));
      Assertions.assertEquals("1.2K", format.format(1249.0));
   }

   @Test
   public void roundingOptionFormatsInfinity() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0", US);
      format.setRoundingByName("ROUND_UP");
      Assertions.assertEquals(new DecimalFormat("#,##0", US).format(Double.POSITIVE_INFINITY),
                              format.format(Double.POSITIVE_INFINITY));
      Assertions.assertEquals("NaN", format.format(Double.NaN));
   }

   @Test
   public void invalidRoundingNameKeepsOption() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("0.00", US);
      format.setRoundingByName("ROUND_DOWN");
      Assertions.assertThrows(RuntimeException.class, () -> format.setRoundingByName("BOGUS"));
      Assertions.assertEquals(RoundingMode.DOWN, format.getRounding());
      Assertions.assertEquals(RoundingMode.DOWN, format.getRoundingMode());
   }

   @Test
   public void incrementCopyKeepsRoundingOption() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.0K", US);
      format.setRoundingByName("ROUND_DOWN");
      ExtendedDecimalFormat copy = format.setIncrement(2000);
      Assertions.assertNotSame(format, copy);
      Assertions.assertEquals(RoundingMode.DOWN, copy.getRounding());
      Assertions.assertEquals("1.2K", copy.format(1299.0));
   }

   @Test
   public void failedRoundingRestoresDowngradedPattern() {
      // 0.55 downgrades the unit and formats with the temporary pattern "#,##0.#"
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0K", US);
      format.setRoundingByName("ROUND_UNNECESSARY");
      Assertions.assertThrows(ArithmeticException.class, () -> format.format(0.55));
      Assertions.assertEquals("#,##0K", format.toPattern());
      Assertions.assertEquals("2K", format.format(2000.0));
   }

   @Test
   public void userSuffixIsRoundedAfterDivision() throws Exception {
      Map<String, Long> mapping = userFormatMapping();
      mapping.put("wan", 10000L);

      try {
         ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,###.00wan", US);
         format.setRoundingByName("ROUND_DOWN");
         Assertions.assertEquals("12.34wan", format.format(123459.0));
         format.setRoundingByName("ROUND_UP");
         Assertions.assertEquals("12.35wan", format.format(123459.0));
      }
      finally {
         mapping.remove("wan");
      }
   }

   @Test
   public void serializationKeepsRoundingOption() throws Exception {
      // the implicit UID before the rounding option was added, so old streams still load
      Assertions.assertEquals(-881877823559828940L,
                              ObjectStreamClass.lookup(ExtendedDecimalFormat.class).getSerialVersionUID());

      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.0K", US);
      format.setRoundingByName("ROUND_DOWN");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(format);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         ExtendedDecimalFormat copy = (ExtendedDecimalFormat) in.readObject();
         Assertions.assertEquals(RoundingMode.DOWN, copy.getRounding());
         Assertions.assertEquals("1.2K", copy.format(1299.0));
      }
   }

   @SuppressWarnings("unchecked")
   static Map<String, Long> userFormatMapping() throws Exception {
      Field field = ExtendedDecimalFormat.class.getDeclaredField("mapping");
      field.setAccessible(true);
      return (Map<String, Long>) field.get(null);
   }

   private static final DecimalFormatSymbols US = new DecimalFormatSymbols(Locale.US);
}
