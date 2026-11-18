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
package inetsoft.uql;

import inetsoft.test.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Case-insensitive CONTAINS / STARTING_WITH must give the same result in every default locale.
 * Under tr-TR a default-locale toLowerCase() turns "I" into a dotless "i" so "TITLE" no longer
 * matched "title".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionIgnoreCaseLocaleTest {
   private Locale saved;

   @BeforeEach
   void saveLocale() {
      saved = Locale.getDefault();
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(saved);
   }

   private static boolean eval(int op, String data, String pattern) {
      Condition cond = new Condition(inetsoft.uql.schema.XSchema.STRING);
      cond.setOperation(op);
      cond.setCaseSensitive(false);
      cond.addValue(pattern);
      return cond.evaluate(data);
   }

   @ParameterizedTest
   @ValueSource(strings = { "tr-TR", "en-US" })
   void containsIgnoreCase(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      assertEquals(true, eval(Condition.CONTAINS, "TITLE", "title"));
      assertEquals(true, eval(Condition.CONTAINS, "title", "TITLE"));
      assertEquals(true, eval(Condition.CONTAINS, "My TITLE here", "itl"));
      assertEquals(false, eval(Condition.CONTAINS, "TITLE", "xyz"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "tr-TR", "en-US" })
   void startingWithIgnoreCase(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      assertEquals(true, eval(Condition.STARTING_WITH, "TITLE", "title"));
      assertEquals(true, eval(Condition.STARTING_WITH, "title", "TIT"));
      assertEquals(true, eval(Condition.STARTING_WITH, "INDEX", "in"));
      assertEquals(false, eval(Condition.STARTING_WITH, "TITLE", "itle"));
   }
}
