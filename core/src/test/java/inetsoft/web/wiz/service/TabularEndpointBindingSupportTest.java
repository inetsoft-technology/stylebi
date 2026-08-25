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
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for {@link TabularEndpointBindingSupport}, the reflection-based helper shared by
 * {@code WorksheetTableService.buildTabularTable} (wiz-services) and
 * {@code WorksheetAgentController.addTabularTable} (composer plugin). Exercised against
 * {@link FakeNamedConnectorQuery}/{@link FakeCustomRestQuery} -- real (non-mock) {@code TabularQuery}
 * instances, since the class under test works entirely through {@code TabularUtil}'s bean-property
 * reflection, which a mock cannot stand in for.
 */
@Tag("core")
class TabularEndpointBindingSupportTest {

   // ─── applyEndpointContract (named connector) ──────────────────────────────

   @Test
   void applyEndpointContractSetsEndpointAndBuildsSuffix() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      String suffix = TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      assertEquals("/Repos", suffix);
      assertEquals("Repos", query.getEndpoint());
   }

   @Test
   void applyEndpointContractSubstitutesSuppliedParameterValues() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      RestParameter idParam = new RestParameter();
      idParam.setName("id");
      idParam.setRequired(true);
      query.getParameters().getParameters().add(idParam);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      String suffix = TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", Map.of("id", "42"), null, null, null, "myds");

      assertEquals("/Repos/42", suffix);
   }

   @Test
   void applyEndpointContractRejectsMissingRequiredParameter() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      RestParameter idParam = new RestParameter();
      idParam.setName("id");
      idParam.setRequired(true);
      query.getParameters().getParameters().add(idParam);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyEndpointContract(
            query, pmap, "Repos", null, null, null, null, "myds"));
      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
   }

   @Test
   void applyEndpointContractRejectsUnknownParameterName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      RestParameter idParam = new RestParameter();
      idParam.setName("id");
      query.getParameters().getParameters().add(idParam);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyEndpointContract(
            query, pmap, "Repos", Map.of("bogus", "1"), null, null, null, "myds"));
      assertTrue(ex.getMessage().contains("bogus"), ex.getMessage());
   }

   @Test
   void applyEndpointContractRejectsUnknownEndpointName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyEndpointContract(
            query, pmap, "Bogus", null, null, null, null, "myds"));
      assertTrue(ex.getMessage().contains("Bogus"), ex.getMessage());
   }

   @Test
   void applyEndpointContractRejectsPostEndpoint() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyEndpointContract(
            query, pmap, "PostEndpoint", null, null, null, null, "myds"));
      assertTrue(ex.getMessage().contains("POST endpoint"), ex.getMessage());
   }

   @Test
   void requireRowCapWhenPagedThrowsForAPagedEndpoint() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Paged", null, null, null, null, "myds");

      assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.requireRowCapWhenPaged(query, "Paged", "myds"));
   }

   @Test
   void requireRowCapWhenPagedAllowsAnUnpagedEndpoint() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      assertDoesNotThrow(
         () -> TabularEndpointBindingSupport.requireRowCapWhenPaged(query, "Repos", "myds"));
   }

   // ─── applyLookupChain (named connector) ───────────────────────────────────

   @Test
   void applyLookupChainSetsSingleLevel() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      TabularEndpointBindingSupport.applyLookupChain(
         query, pmap, List.of("Issues"), null, null, "Repos", "myds");

      assertEquals("Issues", query.getLookupEndpoint0());
   }

   @Test
   void applyLookupChainSetsTwoLevelsInOrder() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      TabularEndpointBindingSupport.applyLookupChain(
         query, pmap, List.of("Issues", "Comments"), null, null, "Repos", "myds");

      assertEquals("Issues", query.getLookupEndpoint0());
      assertEquals("Comments", query.getLookupEndpoint1());
   }

   @Test
   void applyLookupChainRejectsUnknownNameAtPositionZero() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyLookupChain(
            query, pmap, List.of("Bogus"), null, null, "Repos", "myds"));
      assertTrue(ex.getMessage().contains("Issues"),
         "should name Repos' actual lookup choices, got: " + ex.getMessage());
   }

   /**
    * Proves the parent-endpoint chaining, not just single-level validation: an unknown name at
    * position 1 must be checked against position 0's CHOSEN endpoint's ("Issues") own lookups
    * ("Comments"), not position 0's parent ("Repos")'s.
    */
   @Test
   void applyLookupChainRejectsUnknownNameAtPositionOneNamingPositionOnesChoices() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyLookupChain(
            query, pmap, List.of("Issues", "Bogus"), null, null, "Repos", "myds"));
      assertTrue(ex.getMessage().contains("Comments"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'Issues'"), ex.getMessage());
   }

   @Test
   void applyLookupChainRejectsChainLongerThanFive() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyLookupChain(query, pmap,
            List.of("a", "b", "c", "d", "e", "f"), null, null, "Repos", "myds"));
   }

   @Test
   void applyLookupChainDefaultsLeaveConnectorDefaultsUntouched() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      TabularEndpointBindingSupport.applyLookupChain(
         query, pmap, List.of("Issues"), null, null, "Repos", "myds");

      assertTrue(query.isLookupExpanded());
      assertTrue(query.isLookupTopLevelOnly());
   }

   @Test
   void applyLookupChainSuppliedFalseIsReadBack() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularEndpointBindingSupport.applyEndpointContract(
         query, pmap, "Repos", null, null, null, null, "myds");

      TabularEndpointBindingSupport.applyLookupChain(
         query, pmap, List.of("Issues"), false, false, "Repos", "myds");

      assertFalse(query.isLookupExpanded());
      assertFalse(query.isLookupTopLevelOnly());
   }

   // ─── applyCustomSuffix (generic/custom) ───────────────────────────────────

   @Test
   void applyCustomSuffixSetsSuffixAndJsonPath() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      String suffix = TabularEndpointBindingSupport.applyCustomSuffix(
         query, pmap, "/v1/widgets/{id}", "$.data[*]", "myds");

      assertEquals("/v1/widgets/{id}", suffix);
      assertEquals("$.data[*]", query.getJsonPath());
   }

   /**
    * The one test that specifically exercises the cross-class silent-failure boundary: calling
    * {@code applyCustomSuffix} against a named connector's query (whose {@code setSuffix} is a
    * documented no-op) must be caught by the read-back, not silently "succeed" while leaving the
    * table bound to whatever {@code endpoint} happens to default to.
    */
   @Test
   void applyCustomSuffixRejectsSilentNoOpOnNamedConnectorQuery() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> TabularEndpointBindingSupport.applyCustomSuffix(
            query, pmap, "/v1/widgets/{id}", null, "myds"));
      assertTrue(ex.getMessage().contains("endpoint"), ex.getMessage());
   }

   // ─── applyCustomLookupChain (generic/custom) ──────────────────────────────

   @Test
   void applyCustomLookupChainSetsFourFieldsPerLevel() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, List.of(
         new WorksheetMutationSupport.CustomLookupSpec(
            "/v1/repos/{param1}/issues", "$.data[*]", "id", null),
         new WorksheetMutationSupport.CustomLookupSpec(
            "/v1/issues/{param2}/comments", "$.[*]", "id", true)
      ), "myds");

      assertEquals("/v1/repos/{param1}/issues", query.getLookupUrl0());
      assertEquals("$.data[*]", query.getLookupJsonPath0());
      assertEquals("id", query.getLookupKey0());
      assertFalse(query.getLookupIgnoreBaseUrl0());

      assertEquals("/v1/issues/{param2}/comments", query.getLookupUrl1());
      assertEquals("$.[*]", query.getLookupJsonPath1());
      assertEquals("id", query.getLookupKey1());
      assertTrue(query.getLookupIgnoreBaseUrl1());
   }

   @Test
   void applyCustomLookupChainDefaultsIgnoreBaseUrlToFalse() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, List.of(
         new WorksheetMutationSupport.CustomLookupSpec(
            "/v1/repos/{param1}/issues", null, null, null)
      ), "myds");

      assertFalse(query.getLookupIgnoreBaseUrl0());
   }

   @Test
   void applyCustomLookupChainRejectsBlankUrl() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, List.of(
            new WorksheetMutationSupport.CustomLookupSpec("  ", null, null, null)
         ), "myds"));
      assertTrue(ex.getMessage().contains("customLookups[0].url"), ex.getMessage());
   }

   /**
    * The {@code {paramN}} convention is not enforced anywhere in the runtime substitution path
    * (per the design doc's §8.8 flagged decision) -- so this validation exists ONLY here, before
    * any property write, to refuse a URL guaranteed to malfunction rather than accept it silently.
    */
   @Test
   void applyCustomLookupChainRejectsUrlMissingPlaceholder() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, List.of(
            new WorksheetMutationSupport.CustomLookupSpec(
               "/v1/repos/issues", null, null, null)
         ), "myds"));
      assertTrue(ex.getMessage().contains("{param1}"), ex.getMessage());
      assertNull(query.getLookupUrl0(), "must refuse before writing anything");
   }

   @Test
   void applyCustomLookupChainRejectsPlaceholderAtWrongPosition() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());

      // Level 1 (0-indexed) must contain {param2}, not {param1}.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, List.of(
            new WorksheetMutationSupport.CustomLookupSpec(
               "/v1/repos/{param1}/issues", null, null, null),
            new WorksheetMutationSupport.CustomLookupSpec(
               "/v1/issues/{param1}/comments", null, null, null)
         ), "myds"));
      assertTrue(ex.getMessage().contains("{param2}"), ex.getMessage());
   }

   @Test
   void applyCustomLookupChainRejectsChainLongerThanFive() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      List<WorksheetMutationSupport.CustomLookupSpec> tooMany = List.of(
         new WorksheetMutationSupport.CustomLookupSpec("{param1}", null, null, null),
         new WorksheetMutationSupport.CustomLookupSpec("{param2}", null, null, null),
         new WorksheetMutationSupport.CustomLookupSpec("{param3}", null, null, null),
         new WorksheetMutationSupport.CustomLookupSpec("{param4}", null, null, null),
         new WorksheetMutationSupport.CustomLookupSpec("{param5}", null, null, null),
         new WorksheetMutationSupport.CustomLookupSpec("{param6}", null, null, null));

      assertThrows(IllegalArgumentException.class,
         () -> TabularEndpointBindingSupport.applyCustomLookupChain(query, pmap, tooMany, "myds"));
      assertNull(query.getLookupUrl0(), "must refuse before writing anything");
   }
}
