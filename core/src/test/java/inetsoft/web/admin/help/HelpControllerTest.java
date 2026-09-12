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
package inetsoft.web.admin.help;

/*
 * Test strategy
 *
 * HelpController has two endpoints and one @PostConstruct initializer:
 *
 *   loadLinks() — reads help-links.json from the classpath (relative to the class package),
 *     prepends Tool.getHelpBaseURL() + "#cshid=" to every link, and appends a default
 *     empty-route link pointing at "#cshid=EM".
 *
 *   getHelpLinks() — returns the HelpLinks object built by loadLinks().
 *   getHelpUrL()   — returns Encode.forUri(Tool.getHelpBaseURL()).
 *
 * Tool.getHelpBaseURL() reads "help.url" from SreeEnv, so the base URL is
 * controlled in tests via SreeEnv mock.
 *
 * A minimal help-links.json is placed in the test resources tree at the same
 * classpath location so that loadLinks() can find it without the web module.
 *
 * Behavioral guarantees covered:
 *
 * [G1] Every link returned by getHelpLinks() starts with the configured base URL + "#cshid=".
 * [G2] A default link with route="" and suffix "#cshid=EM" is always added.
 * [G3] Routes from the JSON file are present in the returned list with their cshid suffix.
 * [G4] getHelpUrL() returns the URI-encoded base URL.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class HelpControllerTest {

   private static final String BASE_URL = "https://example.com/docs";
   private static final String EXPECTED_PREFIX = BASE_URL + "#cshid=";

   private HelpController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("help.url")).thenReturn(BASE_URL);

      controller = new HelpController(new ObjectMapper());
      controller.loadLinks();
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   // [G1] every link starts with the configured base URL + "#cshid="
   @Test
   void getHelpLinks_allLinksHaveBaseUrlPrefix() {
      List<HelpLink> links = controller.getHelpLinks(null).links();

      assertFalse(links.isEmpty());
      for(HelpLink link : links) {
         assertTrue(link.link().startsWith(EXPECTED_PREFIX),
            "Expected link to start with prefix, but was: " + link.link());
      }
   }

   // [G2] default link for route "" with "#cshid=EM" is always appended
   @Test
   void getHelpLinks_defaultEmLinkAlwaysPresent() {
      List<HelpLink> links = controller.getHelpLinks(null).links();

      assertTrue(links.stream().anyMatch(l -> l.route().isEmpty() && l.link().equals(EXPECTED_PREFIX + "EM")),
         "Default EM link not found in: " + links);
   }

   // [G3] routes from the JSON are present, each with their cshid value appended
   @Test
   void getHelpLinks_routesFromJsonHaveCorrectCshidSuffix() {
      List<HelpLink> links = controller.getHelpLinks(null).links();

      assertTrue(links.stream().anyMatch(
         l -> l.route().equals("/monitoring/log") && l.link().equals(EXPECTED_PREFIX + "EMMonitoringLog")));
      assertTrue(links.stream().anyMatch(
         l -> l.route().equals("/settings/general") && l.link().equals(EXPECTED_PREFIX + "EMSettingsGeneral")));
   }

   // [G4] getHelpUrL() returns the URI-encoded base URL
   @Test
   void getHelpUrL_returnsEncodedBaseUrl() {
      String result = controller.getHelpUrL(null);

      assertEquals(BASE_URL, result);
   }
}
