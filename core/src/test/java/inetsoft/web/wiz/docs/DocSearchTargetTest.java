/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.wiz.docs;

import inetsoft.web.wiz.docs.DocSearchTarget.Source;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** One test per branch of the resolution order (Feature #80898). */
@Tag("core")
class DocSearchTargetTest {
   private static final String HOSTED = "https://docs.hosted.example.com";

   private static DocSearchTarget resolve(String docs, String internal, String server, String hosted) {
      return DocSearchTarget.resolve(docs, internal, server, hosted).orElse(null);
   }

   @Test
   void docsUrlWinsOverEverything() {
      DocSearchTarget t = resolve(" https://docs.example.com ", "https://i", "https://s", HOSTED);
      assertEquals("https://docs.example.com", t.baseUrl());
      assertEquals(Source.DOCS_URL, t.source());
   }

   @Test
   void aBlankDocsUrlDisablesSearchRegardlessOfTheOtherUrls() {
      for(String blank : new String[] { "", "   " }) {
         assertEquals(Optional.empty(),
                      DocSearchTarget.resolve(blank, "https://i", "https://s", HOSTED));
      }
   }

   @Test
   void internalUrlIsUsedWhenDocsUrlIsUnset() {
      DocSearchTarget t = resolve(null, "https://i", "https://s", HOSTED);
      assertEquals("https://i", t.baseUrl());
      assertEquals(Source.INTERNAL_URL, t.source());
   }

   @Test
   void serverUrlIsUsedWhenOnlyItIsSet() {
      for(String blankInternal : new String[] { null, "", " " }) {
         DocSearchTarget t = resolve(null, blankInternal, "https://s", HOSTED);
         assertEquals("https://s", t.baseUrl());
         assertEquals(Source.SERVER_URL, t.source());
      }
   }

   @Test
   void hostedDefaultIsUsedWhenNothingIsConfigured() {
      DocSearchTarget t = resolve(null, null, "", HOSTED);
      assertEquals(HOSTED, t.baseUrl());
      assertEquals(Source.HOSTED_DEFAULT, t.source());
   }

   @Test
   void nothingIsResolvedWhenNoHostedDefaultExistsYet() {
      assertEquals(Optional.empty(), DocSearchTarget.resolve(null, null, null, ""));
   }

   @Test
   void onlyTheOwnAssistantGetsTheCallerTokenAndRelaxedTls() {
      assertTrue(Source.INTERNAL_URL.forwardsCallerToken());
      assertTrue(Source.SERVER_URL.forwardsCallerToken());
      assertFalse(Source.DOCS_URL.forwardsCallerToken());
      assertFalse(Source.HOSTED_DEFAULT.forwardsCallerToken());
      assertFalse(Source.DOCS_URL.usesAssistantTls());
      assertFalse(Source.HOSTED_DEFAULT.usesAssistantTls());
   }
}
