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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Array data strings ({@code type~value^type~value}) must round-trip values that contain the
 * {@code ^} and {@code ~} delimiters, while every string a legacy writer produced keeps its
 * exact bytes and decodes to the same values as before (Bug #77417).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class CoreToolArrayDataStringTest {
   static Stream<Arguments> specialArrays() {
      return Stream.of(
         new Object[] { "a^b", "c" },
         new Object[] { "x~y" },
         new Object[] { "a\\^b" },
         new Object[] { "\\", "^", "~", "^~\\", "a\\" },
         new Object[] { "x~y", 5, "p^q" },
         new Object[] { new Object[] { "a", "b" }, "c" },
         new Object[] { new Object[] { new Object[] { "p^q" }, "r\\" }, 1 },
         new Object[] { new Object[] {}, "^" },
         new Object[] { new Object[] { "a" } }
      ).map(value -> Arguments.of((Object) value));
   }

   @ParameterizedTest
   @MethodSource("specialArrays")
   void arrayWithDelimitersRoundTrips(Object[] value) {
      String text = Tool.getDataString(value);
      Object loaded = Tool.getData(Tool.getDataType(value), text);

      assertArrayEquals(value, (Object[]) loaded, "encoded as " + text);
   }

   @Test
   void reportedCaretCaseRoundTrips() {
      Object loaded = Tool.getData(Tool.ARRAY, Tool.getDataString(new Object[] { "a^b", "c" }));

      assertArrayEquals(new Object[] { "a^b", "c" }, (Object[]) loaded);
   }

   @Test
   void reportedTildeCaseRoundTrips() {
      Object loaded = Tool.getData(Tool.ARRAY, Tool.getDataString(new Object[] { "x~y" }));

      assertArrayEquals(new Object[] { "x~y" }, (Object[]) loaded);
   }

   @Test
   void persistentArrayWithDelimitersRoundTrips() {
      Object[] value = { "a^b", null, "x~y" };
      String text = Tool.getPersistentDataString(value);

      assertArrayEquals(value, (Object[]) Tool.getData(Tool.ARRAY, text, true));
   }

   @Test
   void arrayWithoutDelimitersKeepsLegacyBytes() {
      assertEquals("string~a^string~C:\\d^string~50%",
                   Tool.getDataString(new Object[] { "a", "C:\\d", "50%" }));
      assertEquals("string~\\\\srv^integer~1^null~null^string~[005e]",
                   Tool.getDataString(new Object[] { "\\\\srv", 1, null, "[005e]" }));
      assertEquals("", Tool.getDataString(new Object[0]));
      assertEquals("array~", Tool.getDataString(new Object[] { new Object[0] }));
   }

   @Test
   void legacyArrayStringsDecodeAsBefore() {
      assertArrayEquals(new Object[] { "a", "C:\\d", "50%" },
                        (Object[]) Tool.getData(Tool.ARRAY, "string~a^string~C:\\d^string~50%"));
      assertArrayEquals(new Object[] { "\\\\srv", "a\\", "a\\", null },
                        (Object[]) Tool.getData(Tool.ARRAY, "string~\\\\srv^string~a\\^string~a\\^"));
      assertArrayEquals(
         new Object[] { "a", 1, Tool.getData(Tool.DATE, "2024-01-31") },
         (Object[]) Tool.getData(Tool.ARRAY, "string~a^integer~1^date~2024-01-31"));
   }

   @Test
   void legacyCorruptedStringsDecodeAsBefore() {
      // strings the legacy writer produced for delimiter/nested values keep their old reading
      assertArrayEquals(new Object[] { "a", null, "c" },
                        (Object[]) Tool.getData(Tool.ARRAY, "string~a^b^string~c"));
      assertArrayEquals(new Object[] { "x" },
                        (Object[]) Tool.getData(Tool.ARRAY, "string~x~y"));
      assertArrayEquals(new Object[] { new Object[] { null }, "b", "c" },
                        (Object[]) Tool.getData(Tool.ARRAY, "array~string~a^string~b^string~c"));
   }
}
