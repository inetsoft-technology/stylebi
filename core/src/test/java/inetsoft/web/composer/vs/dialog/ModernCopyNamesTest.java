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
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.*;
import inetsoft.util.Catalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernCopyNamesTest {
   @Test
   void suggestPrefersTheModernSuffix() {
      assertEquals("Sales (modern)",
                   ModernCopyNames.suggest("Sales", n -> false, Catalog.getCatalog()));
   }

   @Test
   void suggestSkipsTakenNames() {
      Set<String> taken = Set.of("Sales (modern)", "Sales (modern 2)");
      assertEquals("Sales (modern 3)",
                   ModernCopyNames.suggest("Sales", taken::contains, Catalog.getCatalog()));
   }

   @Test
   void suggestGivesUpAfter999() {
      assertNull(ModernCopyNames.suggest("Sales", n -> true, Catalog.getCatalog()));
   }

   @Test
   void bannedCharactersAreRefused() {
      Catalog catalog = Catalog.getCatalog();

      for(String c : new String[] { "\\", "/", "\"", "<", "%", "^", "~" }) {
         assertNotNull(ModernCopyNames.invalidMessage("Sales" + c + "1", catalog), c);
      }
   }

   @Test
   void nameMustStartWithACharacterOrDigit() {
      Catalog catalog = Catalog.getCatalog();
      assertNotNull(ModernCopyNames.invalidMessage("_Sales", catalog));
      assertNotNull(ModernCopyNames.invalidMessage(" Sales", catalog));
      assertNull(ModernCopyNames.invalidMessage("1Sales", catalog));
      assertNull(ModernCopyNames.invalidMessage("销售", catalog));
   }

   @Test
   void validNamesPass() {
      assertNull(ModernCopyNames.invalidMessage("Sales (modern 2)", Catalog.getCatalog()));
   }
}
