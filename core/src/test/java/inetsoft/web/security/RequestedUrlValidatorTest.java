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
package inetsoft.web.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure string logic: no Spring bean is reached, so no application context is needed.
 */
@Tag("core")
class RequestedUrlValidatorTest {
   private static final String LINK = "https://bi.example.com/sree/";
   private static final String DEF = "app/portal";

   @ParameterizedTest
   @ValueSource(strings = {
      "app/portal",
      "app/portal?x=1&y=a:b",
      "/sree/em",
      "/",
      "https://bi.example.com/sree/app/portal",
      "https://bi.example.com/sree/app/portal?x=1",
      "https://bi.example.com/sree/sso/token?a=b%20c",
      "index.html"
   })
   void legitValuesPassThroughUnchanged(String url) {
      assertEquals(url, RequestedUrlValidator.sanitize(url, LINK, DEF));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "https://evil.example/c",
      "http://evil.example/c",
      "//evil.example/c",
      "///evil.example/c",
      " //evil.example",
      "\u0001//evil.example",
      "/\t/evil.example",
      "/\n/evil.example",
      "//evil.example\r\n",
      "javascript:alert(1)",
      "JavaScript:alert(1)",
      "data:text/html,x",
      "http:evil.example",
      "evil.example:80/x",
      "https://bi.example.com.evil.example/sree/",
      "https://bi.example.com/sreeevil/x",
      "https://bi.example.com@evil.example/sree/",
      "https://bi.example.com/sree/\t/x",
      ""
   })
   void foreignOrMalformedValuesFallBack(String url) {
      assertEquals(DEF, RequestedUrlValidator.sanitize(url, LINK, DEF));
   }

   @Test
   void backslashVariantsFallBack() {
      String bs = String.valueOf((char) 92);

      for(String url : new String[] { "/" + bs + "evil.example", bs + bs + "evil.example",
                                      bs + "evil.example", "app/portal" + bs + "x" })
      {
         assertEquals(DEF, RequestedUrlValidator.sanitize(url, LINK, DEF), url);
      }
   }

   @Test
   void nullFallsBack() {
      assertEquals(DEF, RequestedUrlValidator.sanitize(null, LINK, DEF));
      assertNull(RequestedUrlValidator.sanitize(null, LINK, null));
   }

   @Test
   void absoluteUrlOfAnotherDeploymentIsRejected() {
      assertFalse(RequestedUrlValidator.isSafe("https://bi.example.com/other/app/portal", LINK));
   }

   @Test
   void nullLinkUriOnlyAllowsRelative() {
      assertTrue(RequestedUrlValidator.isSafe("app/portal", null));
      assertFalse(RequestedUrlValidator.isSafe("https://bi.example.com/sree/x", null));
   }
}
