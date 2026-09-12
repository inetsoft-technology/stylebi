/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class InetsoftUserDocumentationTest {

   private static final String FALLBACK_BASE =
      "https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.1.0/index.html";

   @BeforeEach
   void resetCache() throws Exception {
      Field f = InetsoftUserDocumentation.class.getDeclaredField("indexBaseUrl");
      f.setAccessible(true);
      f.set(null, null);
   }

   // -------------------------------------------------------------------------
   // stripPreReleaseSuffix
   // -------------------------------------------------------------------------

   @ParameterizedTest(name = "''{0}'' -> ''{1}''")
   @CsvSource({
      "1.1.0,           1.1.0",
      "1.1,             1.1",
      "1.1.0-SNAPSHOT,  1.1.0",
      "1.1.0-alpha,     1.1.0",
      "1.1.0-RC1,       1.1.0",
      "v1.1.0,          1.1.0",
      "V1.1.0,          1.1.0",
      "v1.1.0-beta-1,   1.1.0",
      "v1.0.0-beta-2,   1.0.0",
      "v1.1.0-RC1,      1.1.0",
   })
   void stripPreReleaseSuffix(String input, String expected) {
      assertEquals(expected, InetsoftUserDocumentation.stripPreReleaseSuffix(input.trim()));
   }

   // -------------------------------------------------------------------------
   // getUserDocumentationIndexBaseUrl
   // -------------------------------------------------------------------------

   @Test
   void indexBaseUrlFallsBackToHardcodedVersion() {
      // Test JVM has no Specification-Version in its manifest, so the hardcoded "1.1.0" is used.
      assertEquals(FALLBACK_BASE, InetsoftUserDocumentation.getUserDocumentationIndexBaseUrl());
   }

   @Test
   void indexBaseUrlIsCached() {
      String first = InetsoftUserDocumentation.getUserDocumentationIndexBaseUrl();
      String second = InetsoftUserDocumentation.getUserDocumentationIndexBaseUrl();
      assertSame(first, second);
   }

   // -------------------------------------------------------------------------
   // contextSensitiveHelpUrl
   // -------------------------------------------------------------------------

   @Test
   void contextSensitiveHelpUrlAppendsCshid() {
      assertEquals(
         FALLBACK_BASE + "#cshid=CreatingaChart",
         InetsoftUserDocumentation.contextSensitiveHelpUrl("CreatingaChart"));
   }

   // -------------------------------------------------------------------------
   // normalizeStyleBiScriptDocUrl
   // -------------------------------------------------------------------------

   @Test
   void normalizeScriptDocUrlNull() {
      assertNull(InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(null));
   }

   @Test
   void normalizeScriptDocUrlNoCshidFragment() {
      String url = "https://www.inetsoft.com/docs/stylebi/index.html";
      assertEquals(url, InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(url));
   }

   @Test
   void normalizeScriptDocUrlExternalSiteIgnored() {
      String url = "https://example.com/docs#cshid=foo";
      assertEquals(url, InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(url));
   }

   @Test
   void normalizeScriptDocUrlLegacyUnversioned() {
      String url = "https://www.inetsoft.com/docs/stylebi/index.html#cshid=CreatingaChart";
      assertEquals(
         FALLBACK_BASE + "#cshid=CreatingaChart",
         InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(url));
   }

   @Test
   void normalizeScriptDocUrlStaleVersionedUrl() {
      String url = "https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.1.0-SNAPSHOT/index.html#cshid=dateAdd";
      assertEquals(
         FALLBACK_BASE + "#cshid=dateAdd",
         InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(url));
   }

   @Test
   void normalizeScriptDocUrlAlreadyCorrect() {
      String url = FALLBACK_BASE + "#cshid=dateAdd";
      assertEquals(url, InetsoftUserDocumentation.normalizeStyleBiScriptDocUrl(url));
   }

   // -------------------------------------------------------------------------
   // normalizeStyleBiDocHtmlContent
   // -------------------------------------------------------------------------

   @Test
   void normalizeHtmlNull() {
      assertNull(InetsoftUserDocumentation.normalizeStyleBiDocHtmlContent(null));
   }

   @Test
   void normalizeHtmlNoDocUrl() {
      String html = "<p>Hello world</p>";
      assertEquals(html, InetsoftUserDocumentation.normalizeStyleBiDocHtmlContent(html));
   }

   @Test
   void normalizeHtmlLegacyUnversionedUrl() {
      String html = "<a href='https://www.inetsoft.com/docs/stylebi/index.html#cshid=CreatingaChart'>docs</a>";
      String expected = "<a href='" + FALLBACK_BASE + "#cshid=CreatingaChart'>docs</a>";
      assertEquals(expected, InetsoftUserDocumentation.normalizeStyleBiDocHtmlContent(html));
   }

   @Test
   void normalizeHtmlStaleVersionedUrl() {
      String html = "<a href='https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/index.html'>docs</a>";
      String expected = "<a href='" + FALLBACK_BASE + "'>docs</a>";
      assertEquals(expected, InetsoftUserDocumentation.normalizeStyleBiDocHtmlContent(html));
   }

   @Test
   void normalizeHtmlMultipleUrlsInOneLine() {
      String old = "https://www.inetsoft.com/docs/stylebi/InetSoftUserDocumentation/1.0.0/index.html";
      String html = "<a href='" + old + "'>a</a> and <a href='" + old + "'>b</a>";
      String expected = "<a href='" + FALLBACK_BASE + "'>a</a> and <a href='" + FALLBACK_BASE + "'>b</a>";
      assertEquals(expected, InetsoftUserDocumentation.normalizeStyleBiDocHtmlContent(html));
   }

   // -------------------------------------------------------------------------
   // rewriteScriptApiDocUrls
   // -------------------------------------------------------------------------

   @Test
   void rewriteNullIsNoOp() {
      assertDoesNotThrow(() -> InetsoftUserDocumentation.rewriteScriptApiDocUrls(null));
   }

   @Test
   void rewriteReplacesUrlFieldInObject() {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode node = mapper.createObjectNode();
      node.put("!url", "https://www.inetsoft.com/docs/stylebi/index.html#cshid=dateAdd");
      node.put("!doc", "returns the date");

      InetsoftUserDocumentation.rewriteScriptApiDocUrls(node);

      assertEquals(FALLBACK_BASE + "#cshid=dateAdd", node.get("!url").asText());
      assertEquals("returns the date", node.get("!doc").asText());
   }

   @Test
   void rewriteRecursesIntoNestedObject() {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode root = mapper.createObjectNode();
      ObjectNode child = mapper.createObjectNode();
      child.put("!url", "https://www.inetsoft.com/docs/stylebi/index.html#cshid=CreatingaChart");
      root.set("dateAdd", child);

      InetsoftUserDocumentation.rewriteScriptApiDocUrls(root);

      assertEquals(
         FALLBACK_BASE + "#cshid=CreatingaChart",
         root.get("dateAdd").get("!url").asText());
   }

   @Test
   void rewriteRecursesIntoArray() {
      ObjectMapper mapper = new ObjectMapper();
      ArrayNode array = mapper.createArrayNode();
      ObjectNode item = mapper.createObjectNode();
      item.put("!url", "https://www.inetsoft.com/docs/stylebi/index.html#cshid=Highlights");
      array.add(item);

      InetsoftUserDocumentation.rewriteScriptApiDocUrls(array);

      assertEquals(FALLBACK_BASE + "#cshid=Highlights", array.get(0).get("!url").asText());
   }

   @Test
   void rewriteLeavesNonDocUrlUntouched() {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode node = mapper.createObjectNode();
      node.put("!url", "https://developer.mozilla.org/en-US/docs/Web/JavaScript");

      InetsoftUserDocumentation.rewriteScriptApiDocUrls(node);

      assertEquals("https://developer.mozilla.org/en-US/docs/Web/JavaScript", node.get("!url").asText());
   }
}
