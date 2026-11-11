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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bug #81025: getIDString must report an untranslated '.*' id as not found (null), and must not
 * run MessageFormat on a translation when no parameters are given (it strips apostrophes).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CatalogIdStringTest {
   private Catalog catalog;

   @BeforeEach
   void createCatalog() {
      catalog = Catalog.getResCatalog("inetsoft/util/Catalog81025Bundle", Locale.ENGLISH);
   }

   @Test
   void untranslatedPrefixIdIsNotFound() {
      assertNull(catalog.getIDString("axis.*"));
   }

   @Test
   void untranslatedPrefixIdStillReturnsIdFromGetString() {
      assertEquals("axis.*", catalog.getString("axis.*"));
      assertEquals("axis.*", catalog.getString("axis.*", false));
   }

   @Test
   void untranslatedNormalIdIsNotFound() {
      assertNull(catalog.getIDString("no.such.id"));
   }

   @Test
   void apostropheKeptWithoutParams() {
      assertEquals("J'accepte", catalog.getIDString("accept.apos"));
      assertEquals("J'accepte", catalog.getString("accept.apos"));
   }

   @Test
   void doubledApostropheShownAsWrittenWithoutParams() {
      assertEquals("J''accepte", catalog.getIDString("doubled.apos"));
   }

   @Test
   void messageFormatStillAppliedWithParams() {
      assertEquals("Hello Bob's", catalog.getIDString("with.param", "Bob"));
      assertEquals("Hello Bob's", catalog.getString("with.param", "Bob"));
   }
}
