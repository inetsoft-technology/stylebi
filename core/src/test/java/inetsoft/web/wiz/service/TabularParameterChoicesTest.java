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

import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Closed-choice enforcement of a REST endpoint parameter's placeholder (bug #82261) through each
 * entry point that fills the parameter contract: {@code applyEndpointContract} (the
 * {@code add_table} and {@code edit_table} endpoint forms) and {@code applyQueryContract}
 * ({@code queryParams.parameters}, also the wiz-services {@code /ws/table} path).
 *
 * <p>The "sideways" and "cash" cases are the control: with the pre-fix code both were accepted and
 * sent to the vendor as-is.</p>
 */
@Tag("core")
class TabularParameterChoicesTest {
   @BeforeAll
   static void installContext() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(org.mockito.ArgumentMatchers.any())).thenReturn(null);
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void clearContext() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   private static ConfigurationContext previous;

   // ---- helpers -------------------------------------------------------------------------

   private static Map<String, String> params(String... kv) {
      Map<String, String> m = new LinkedHashMap<>();
      m.put("Agency ID", "1");

      for(int i = 0; i < kv.length; i += 2) {
         m.put(kv[i], kv[i + 1]);
      }

      return m;
   }

   /** The endpoint form: what add_table and edit_table call. */
   private static String endpointForm(FakeChoiceEndpointQuery query, Map<String, String> p)
      throws Exception
   {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      return TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Report", p, null, null, null, null, "myds");
   }

   /** The queryParams form. */
   private static String queryForm(FakeChoiceEndpointQuery query, Object endpoint, Object p)
      throws Exception
   {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, query.getType());
      Map<String, Object> queryParams = new LinkedHashMap<>();
      queryParams.put("endpoint", endpoint);
      queryParams.put("parameters", p);
      return TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, "myds");
   }

   private static IllegalArgumentException refusal(org.junit.jupiter.api.function.Executable e) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, e);
      assertFalse(ex.getMessage().contains("\n"), "message must be single-line: " + ex.getMessage());
      return ex;
   }

   // ---- applyEndpointContract (add_table / edit_table endpoint form) ---------------------

   @Test
   void endpointFormRefusesAValueOutsideTheClosedList() {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      IllegalArgumentException ex = refusal(
         () -> endpointForm(query, params("Accounting Method", "sideways")));

      String m = ex.getMessage();
      assertTrue(m.contains("Accounting Method = 'sideways'"), m);
      assertTrue(m.contains("Cash | Accrual"), m);
      assertTrue(m.contains("Accounting Method [one of: Cash | Accrual]"), m);
   }

   @Test
   void endpointFormWritesTheDeclaredSpellingForAWrongCaseValue() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String suffix = endpointForm(query, params("Accounting Method", "cash"));

      assertTrue(suffix.contains("/Cash"), suffix);
      assertFalse(suffix.contains("/cash"), suffix);
   }

   @Test
   void endpointFormTrimsAndNormalisesAPaddedList() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String suffix = endpointForm(query, params("Flag", "TRUE"));

      assertTrue(suffix.contains("/true"), suffix);
   }

   @Test
   void endpointFormAcceptsACommaJoinedSubsetOfTheList() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String suffix = endpointForm(query, params("Include", "snapshots, METADATA"));

      assertTrue(suffix.contains("/snapshots,metadata"), suffix);
      assertNotNull(
         refusal(() -> endpointForm(new FakeChoiceEndpointQuery(),
                                    params("Include", "snapshots,bogus"))));
   }

   @Test
   void endpointFormKeepsNonEnforceableValuesAccepted() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String suffix = endpointForm(query, params(
         "Start Date", "2026-01-31",        // alternative date formats: hint only
         "Language", "xx",                  // open list ("...")
         "Utm", "utm-campaigns",            // bracket group
         "Since", "2026-01-31",             // plain format hint
         "Note", "anything",                // no placeholder
         "Accounting Method", "$(acct)"));  // variable reference

      assertTrue(suffix.contains("2026-01-31"), suffix);
      assertTrue(suffix.contains("/xx"), suffix);
      assertTrue(suffix.contains("$(acct)"), suffix);
   }

   @Test
   void endpointFormReportsInvalidMissingAndUnknownTogether() {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();
      Map<String, String> p = new LinkedHashMap<>();
      p.put("Accounting Method", "sideways");
      p.put("Bogus", "1");

      String m = refusal(() -> endpointForm(query, p)).getMessage();

      assertTrue(m.contains("requires parameter(s) with no value supplied: Agency ID"), m);
      assertTrue(m.contains("no parameter(s) named: Bogus"), m);
      assertTrue(m.contains("Accounting Method = 'sideways'"), m);
   }

   @Test
   void endpointFormRefusesANumericValueOutsideTheList() {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String m = refusal(() -> endpointForm(query, params("Agency ID", "3"))).getMessage();

      assertTrue(m.contains("Agency ID = '3'"), m);
      assertTrue(m.contains("1 | 2"), m);
   }

   @Test
   void endpointFormBlankOptionalStillResets() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();
      endpointForm(query, params("Accounting Method", "Cash"));

      String suffix = endpointForm(query, params("Accounting Method", "  "));

      assertFalse(suffix.contains("Cash"), suffix);
   }

   @Test
   void endpointFormEditOfAnExistingQueryEnforcesToo() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();
      endpointForm(query, params("Accounting Method", "Accrual"));

      refusal(() -> endpointForm(query, params("Accounting Method", "sideways")));
      assertTrue(endpointForm(query, params("Accounting Method", "cash")).contains("/Cash"));
   }

   // ---- applyQueryContract (queryParams.parameters, /ws/table) ---------------------------

   @Test
   void queryFormRefusesAValueOutsideTheClosedList() {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String m = refusal(() -> queryForm(query, "Report",
         params("Accounting Method", "sideways"))).getMessage();

      assertTrue(m.contains("Accounting Method = 'sideways'"), m);
      assertTrue(m.contains("Cash | Accrual"), m);
   }

   @Test
   void queryFormWritesTheDeclaredSpelling() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      String suffix = queryForm(query, "Report", params("Accounting Method", "cash"));

      assertTrue(suffix == null || !suffix.contains("/cash"), String.valueOf(suffix));
      assertEquals("Cash", query.getParameters().findParameter("Accounting Method").getValue());
   }

   @Test
   void queryFormKeepsNonEnforceableValuesAccepted() throws Exception {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();

      queryForm(query, "Report", params(
         "Start Date", "2026-01-31", "Language", "xx", "Include", "copy,metadata",
         "Accounting Method", "$(acct)"));

      assertEquals("$(acct)",
                   query.getParameters().findParameter("Accounting Method").getValue());
      assertEquals("copy,metadata", query.getParameters().findParameter("Include").getValue());
   }

   @Test
   void choicesAreEnforcedOnlyForACatalogueEndpoint() {
      FakeChoiceEndpointQuery query = new FakeChoiceEndpointQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      assertFalse(TabularQueryContractSupport.hasCatalogueEndpoint(query, pmap));
      query.setEndpoint("Report");
      assertTrue(TabularQueryContractSupport.hasCatalogueEndpoint(query, pmap));
      query.setEndpoint("CUSTOM");
      assertFalse(TabularQueryContractSupport.hasCatalogueEndpoint(query, pmap));
      assertFalse(TabularQueryContractSupport.hasCatalogueEndpoint(query, Map.of()));
   }
}
