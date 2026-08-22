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
