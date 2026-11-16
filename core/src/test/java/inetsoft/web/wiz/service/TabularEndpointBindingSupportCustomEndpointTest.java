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

import inetsoft.uql.tabular.HttpParameter;
import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81478: the {@code CUSTOM} endpoint and {@code additionalParameters} of an endpoint
 * connector, applied by {@link TabularEndpointBindingSupport#applyEndpointContract} before the
 * suffix is read. Axis: every connector whose query has an {@code endpoint} property.
 */
@WizAgentTestSupport
class TabularEndpointBindingSupportCustomEndpointTest {
   private final FakeCustomEndpointQuery query = new FakeCustomEndpointQuery();
   private final Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

   private String apply(String endpoint, Map<String, Object> extras) throws Exception {
      return TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, endpoint, null, null, null, null, extras, "myds");
   }

   private static Map<String, Object> extras(Object... kv) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         map.put((String) kv[i], kv[i + 1]);
      }

      return map;
   }

   @Test
   void customEndpointBuildsTheSuffixFromCustomEndpt() throws Exception {
      String suffix = apply("CUSTOM", extras("customEndpt", " /v0/app/Orders?maxRecords=3 "));

      assertEquals("/v0/app/Orders?maxRecords=3", suffix);
      assertEquals("/v0/app/Orders?maxRecords=3", query.getCustomEndpt());
   }

   @Test
   void customEndpointWithoutCustomEndptIsARefusalNamingCustom() {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> apply("CUSTOM", extras()));

      assertTrue(ex.getMessage().contains("CUSTOM"), ex.getMessage());
      assertTrue(ex.getMessage().contains("customEndpt"), ex.getMessage());
   }

   @Test
   void customEndpointRefusesBlankCustomEndpt() {
      assertThrows(IllegalArgumentException.class,
         () -> apply("CUSTOM", extras("customEndpt", "   ")));
   }

   @Test
   void customEndpointRefusesPlaceholderInCustomEndpt() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply("CUSTOM", extras("customEndpt", "/v0/{base}/Orders")));

      assertTrue(ex.getMessage().contains("placeholder"), ex.getMessage());
   }

   @Test
   void customEndpointRefusesNonStringCustomEndpt() {
      assertThrows(IllegalArgumentException.class,
         () -> apply("CUSTOM", extras("customEndpt", List.of("/a"))));
   }

   @Test
   void editRestatingCustomWithoutCustomEndptKeepsTheSavedSuffix() throws Exception {
      query.setCustomEndpt("/saved/path");

      assertEquals("/saved/path", apply("CUSTOM", extras()));
   }

   @Test
   void customEndptOnANamedEndpointIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply("Paged", extras("customEndpt", "/x")));

      assertTrue(ex.getMessage().contains("CUSTOM"), ex.getMessage());
   }

   @Test
   void additionalParametersQueryStringIsAppendedToTheSuffix() throws Exception {
      String suffix = apply("Paged", extras("additionalParameters", "a=1&b=two"));

      assertEquals("/Paged?a=1&b=two", suffix);
      HttpParameter[] stored = query.getAdditionalParameters();
      assertEquals(2, stored.length);
      assertEquals(HttpParameter.ParameterType.QUERY, stored[0].getType());
   }

   @Test
   void additionalParametersListWorksOnCustomEndpoint() throws Exception {
      String suffix = apply("CUSTOM", extras(
         "customEndpt", "/v0/app/Orders",
         "additionalParameters", List.of(Map.of("name", "maxRecords", "value", "3"))));

      assertEquals("/v0/app/Orders?maxRecords=3", suffix);
   }

   @Test
   void additionalParametersHeaderTypeIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply("Paged", extras("additionalParameters",
            List.of(Map.of("name", "X-Key", "value", "v", "type", "HEADER")))));

      assertTrue(ex.getMessage().contains("HEADER"), ex.getMessage());
   }

   @Test
   void additionalParametersMalformedInputIsRefused() {
      assertThrows(IllegalArgumentException.class,
         () -> apply("Paged", extras("additionalParameters", "noequals")));
      assertThrows(IllegalArgumentException.class,
         () -> apply("Paged", extras("additionalParameters", 5)));
      assertThrows(IllegalArgumentException.class,
         () -> apply("Paged", extras("additionalParameters", List.of(Map.of("nme", "a")))));
   }

   @Test
   void omittedAdditionalParametersClearTheSavedOnesOnEdit() throws Exception {
      apply("Paged", extras("additionalParameters", "a=1"));

      assertEquals("/Paged", apply("Paged", extras()));
      assertNull(query.getAdditionalParameters());
   }

   @Test
   void nullExtrasLeavesAdditionalParametersUntouched() throws Exception {
      apply("Paged", extras("additionalParameters", "a=1"));

      assertEquals("/Paged?a=1", apply("Paged", null));
   }

   @Test
   void customIsAcceptedButNotInTheUnknownEndpointSample() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.assertKnownEndpoint(query, "Nope", "myds"));

      String sample = ex.getMessage().substring(ex.getMessage().indexOf("for example:"),
         ex.getMessage().indexOf(". To call"));
      assertFalse(sample.contains("CUSTOM"), ex.getMessage());
      assertTrue(ex.getMessage().contains("endpoint 'CUSTOM'"), ex.getMessage());
      assertDoesNotThrow(
         () -> TabularEndpointBindingSupport.assertKnownEndpoint(query, "CUSTOM", "myds"));
   }

   @Test
   void withoutEndpointSettingsDropsOnlyTheTwoOwnedKeys() {
      Map<String, Object> rest = TabularEndpointBindingSupport.withoutEndpointSettings(
         extras("customEndpt", "/x", "additionalParameters", "a=1", "jsonPath", "$.r"));

      assertEquals(Map.of("jsonPath", "$.r"), rest);
   }
}
