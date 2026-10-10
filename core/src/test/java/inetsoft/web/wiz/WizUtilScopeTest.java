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
package inetsoft.web.wiz;

import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #78192 (S6): scope strings are trimmed/case-folded; anything else is refused. */
@Tag("core")
class WizUtilScopeTest {
   @Test
   void nullMeansGlobal() throws Exception {
      assertEquals(AssetRepository.GLOBAL_SCOPE, WizUtil.resolveAssetScope(null));
   }

   @ParameterizedTest
   @ValueSource(strings = {"user", "User ", "USER", " user"})
   void userVariantsResolveToUser(String scope) throws Exception {
      assertEquals(AssetRepository.USER_SCOPE, WizUtil.resolveAssetScope(scope));
   }

   @ParameterizedTest
   @ValueSource(strings = {"global", "Global", " GLOBAL "})
   void globalVariantsResolveToGlobal(String scope) throws Exception {
      assertEquals(AssetRepository.GLOBAL_SCOPE, WizUtil.resolveAssetScope(scope));
   }

   @ParameterizedTest
   @ValueSource(strings = {"private", "bogus", "", "  "})
   void anythingElseIsRefusedNamingScope(String scope) {
      PairingException e =
         assertThrows(PairingException.class, () -> WizUtil.resolveAssetScope(scope));
      assertTrue(e.getMessage().contains("scope"), e.getMessage());
   }
}
