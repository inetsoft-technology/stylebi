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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #76350 PSD-001: search_schema("category") missed a table literally named "categories" —
 * "categories".contains("category") is false (they diverge at the plural -y -&gt; -ies spelling
 * change), not a datasource-scoping bug. These drive {@code tableNameMatches}/{@code stem}
 * directly, the way {@link MetadataApiServiceStructureTest} drives other package-private
 * extraction helpers, rather than standing up a full {@code XRepository}-backed searchSchema()
 * round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MetadataApiServiceSchemaSearchTest {
   @Test
   void singularQueryMatchesPluralTableName() {
      assertTrue(MetadataApiService.tableNameMatches("categories", "category"));
   }

   @Test
   void pluralQueryStillMatchesPluralTableName() {
      // The opposite direction was already correct before the fix (plain substring), kept as a
      // regression guard that the new stemmed fallback doesn't disturb it.
      assertTrue(MetadataApiService.tableNameMatches("categories", "categories"));
   }

   @Test
   void esSuffixRequiresASibilantBaseToAvoidOvercorrecting() {
      // A prior candidate for this guard used "wines"/"win" as a plain tableNameMatches() check
      // (a bad choice caught in review: "win" is a literal prefix of "wines", so that pair is
      // already true via plain substring regardless of stemming and proves nothing about the
      // stemmer). The genuine risk is in stem()'s own output: a naive rule that always strips a
      // trailing "es" would reduce "wines" to "win" -- a completely unrelated word (victory,
      // not the plural of wine) -- and that wrong stem, unlike "wine"/"wines" themselves, is not
      // just a truncation of the input, so it is not automatically caught by the plain substring
      // check either time it is reused elsewhere. Requiring the "es" to follow a sibilant sound
      // (box/glass/church-style plurals) keeps "wines" reducing to the correct "wine", not "win".
      assertEquals("wine", MetadataApiService.stem("wines"));
      assertFalse(MetadataApiService.stem("wines").equals(MetadataApiService.stem("win")));
   }

   @Test
   void sibilantEsPluralsStillStem() {
      assertTrue(MetadataApiService.tableNameMatches("boxes", "box"));
      assertTrue(MetadataApiService.tableNameMatches("glasses", "glass"));
   }

   @Test
   void stemHandlesIesEsAndSSuffixes() {
      assertEquals("category", MetadataApiService.stem("categories"));
      assertEquals("box", MetadataApiService.stem("boxes"));
      assertEquals("wine", MetadataApiService.stem("wines"));
      assertEquals("order", MetadataApiService.stem("orders"));
      assertEquals("glass", MetadataApiService.stem("glass"));
   }
}
