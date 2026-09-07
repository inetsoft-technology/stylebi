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

package inetsoft.uql.asset.internal;

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
public class AssetUtilTest {
   private Locale originalDefault;

   @BeforeEach
   void saveDefaultLocale() {
      originalDefault = Locale.getDefault();
   }

   @AfterEach
   void restoreDefaultLocale() {
      Locale.setDefault(originalDefault);
   }

   /**
    * NFMT/PFMT/CFMT/C2FMT are private static final, built once at whatever
    * locale is the JVM default the moment AssetUtil is first class-loaded.
    * Which test triggers that class-load first is Surefire run-order
    * dependent and cannot be controlled from this test, so asserting via an
    * end-to-end parse (as getTypeDetectsNumericValuesUnderNonUsDefaultLocale
    * below does) cannot reliably re-exercise the class-load path in a full
    * suite run. Inspect the already-constructed formatters' embedded
    * symbols directly instead, which is independent of load order.
    */
   @ParameterizedTest
   @ValueSource(strings = { "NFMT", "PFMT", "CFMT", "C2FMT" })
   void formattersAreLocaleInvariant(String fieldName) throws Exception {
      Field field = AssetUtil.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      DecimalFormat fmt = (DecimalFormat) field.get(null);

      assertEquals(DecimalFormatSymbols.getInstance(Locale.US), fmt.getDecimalFormatSymbols());
   }

   /**
    * AssetUtil's numeric/currency/percent formatters are used to recognize
    * StyleBI's own canonical text representations during import type
    * sniffing, regardless of the JVM default locale in effect at the time.
    */
   @Test
   void getTypeDetectsNumericValuesUnderNonUsDefaultLocale() {
      // France's grouping separator (a narrow no-break space) differs from
      // "." used in these values' literal text, unlike e.g. Germany's,
      // where "." happens to also be the grouping separator and would
      // silently parse to a wrong-but-non-throwing value instead.
      Locale.setDefault(Locale.FRANCE);

      Map<String, Format> fmtMap = new HashMap<>();
      String plainNumberType =
         AssetUtil.getType("price", XSchema.INTEGER, "299.99", fmtMap, null);
      String currencyType =
         AssetUtil.getType("price", XSchema.INTEGER, "$499.99", fmtMap, null);

      assertEquals(XSchema.DOUBLE, plainNumberType);
      assertEquals(XSchema.DOUBLE, currencyType);
   }
}
