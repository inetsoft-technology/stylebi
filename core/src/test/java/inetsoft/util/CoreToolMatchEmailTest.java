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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An apostrophe is legal in the local part of an email address (o'brien@example.com), but
 * not in the domain (Bug #80247).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class CoreToolMatchEmailTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "o'brien@example.com",
      "o'brien.o'neil+x@example.com",
      "'leading@example.com",
      "trailing'@example.com",
      "  o'brien@example.com  ",
      "john.smith@example.com",
      "a-b_c+d@sub.example.com"
   })
   void acceptsValidAddresses(String address) {
      assertTrue(Tool.matchEmail(address), address);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "obrien@exam'ple.com",
      "obrien@example.co'm",
      "o'brien@'example.com",
      "@example.com",
      "'@",
      "o'brien",
      "o'brien@",
      "o''brien@@example.com",
      "o'brien@example.com;john@example.com",
      "o'brien@example..com",
      ".o'brien@example.com",
      "o b'rien@example.com",
      ""
   })
   void rejectsInvalidAddresses(String address) {
      assertFalse(Tool.matchEmail(address), address);
   }
}
