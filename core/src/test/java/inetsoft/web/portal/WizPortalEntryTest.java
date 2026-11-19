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
package inetsoft.web.portal;

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class WizPortalEntryTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private void configure(String visible, String url) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty(WizPortalEntry.WIZ_PORTAL_VISIBLE, "false"))
         .thenReturn(visible == null ? "false" : visible);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(WizPortalEntry.WIZ_PORTAL_URL)).thenReturn(url);
   }

   @ParameterizedTest
   @ValueSource(strings = { "false", "FALSE", "no", "1", "" })
   void notVisibleWhenFlagIsNotTrue(String flag) {
      configure(flag, "https://wiz.example.com/");
      assertFalse(WizPortalEntry.isVisible(),
                  "a configured URL must not make the entry visible while the flag is off");
   }

   @ParameterizedTest
   @ValueSource(strings = { "true", "TRUE", "True" })
   void flagCheckIsCaseInsensitive(String flag) {
      configure(flag, "https://wiz.example.com/");
      assertTrue(WizPortalEntry.isVisible());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "", "   ", "javascript:alert(1)", "ftp://x", "wiz.example.com", "data:text/html,x",
      "//evil.example/x", "/\\evil.example", "\\\\evil.example", "https://x\n.evil",
      "https://x\t.evil", "https://exa mple.com", "/wiz /x", "/wi\u0000z", "https:///",
      "https:evil", "https://", "https://user@evil.example/", "https://x\\.evil"
   })
   void notVisibleWhenUrlIsBlankOrUnsafe(String url) {
      configure("true", url);
      assertFalse(WizPortalEntry.isVisible());
      assertNull(WizPortalEntry.getUrl());
   }

   @ParameterizedTest
   @ValueSource(strings = { "HTTP://localhost:8000/", "HTTPS://wiz.example.com/", "/", "/wiz/",
                            "/wiz/?a=b#c" })
   void acceptsLegitimateForms(String url) {
      configure("true", url);
      assertEquals(url, WizPortalEntry.getUrl());
      assertTrue(WizPortalEntry.isVisible());
   }

   @Test
   void notVisibleWhenUrlIsNull() {
      configure("true", null);
      assertFalse(WizPortalEntry.isVisible());
   }

   @Test
   void acceptsHttpsHttpAndRootRelativeUrls() {
      configure("true", "  https://wiz.example.com/  ");
      assertEquals("https://wiz.example.com/", WizPortalEntry.getUrl());

      configure("true", "http://localhost:8000/");
      assertEquals("http://localhost:8000/", WizPortalEntry.getUrl());

      configure("true", "/");
      assertEquals("/", WizPortalEntry.getUrl());
   }
}
