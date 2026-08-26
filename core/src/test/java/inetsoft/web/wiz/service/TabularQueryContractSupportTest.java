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
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link TabularQueryContractSupport}, the general reflection-based routine
 * shared by {@code WorksheetTableService.buildTabularTable} (wiz-services) and
 * {@code WorksheetAgentController.addTabularTable} (composer plugin). Exercised against real
 * (non-mock) {@code TabularQuery} fixtures, since the class under test works entirely through
 * {@code TabularUtil}'s bean-property reflection, which a mock cannot stand in for.
 */
@Tag("core")
class TabularQueryContractSupportTest {
   /**
    * {@code TabularSchemaExtractor.extract} builds a layout via {@code LayoutCreator}, which
    * resolves labels through the connector's resource bundle via {@code Config.getConfig()} --
    * a Spring bean. There is no context in a plain unit test, so a stub is installed that
    * answers no bundle; the raw {@code @Property} label is used instead, same as
    * {@code TabularSchemaExtractorTest}.
    */
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

   private static TabularQuerySchema schemaFor(TabularQuery query) {
      return new TabularSchemaExtractor().extract(query, query.getType());
   }

   private static String apply(TabularQuery query, Map<String, Object> queryParams)
      throws Exception
   {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQuerySchema schema = schemaFor(query);
      return TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, "myds");
   }

   private static Map<String, Object> params(Object... kv) {
      Map<String, Object> m = new LinkedHashMap<>();

      for(int i = 0; i < kv.length; i += 2) {
         m.put((String) kv[i], kv[i + 1]);
      }

      return m;
   }

   // ─── top-level validation (steps 1/2) ─────────────────────────────────────

   @Test
   void queryParamsRequired() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, null));
      assertTrue(ex.getMessage().contains("queryParams"), ex.getMessage());
   }

   @Test
   void unknownTopLevelNameListsWhatIsAccepted() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("bogusTopLevel", "x")));
      assertTrue(ex.getMessage().contains("bogusTopLevel"), ex.getMessage());
      assertTrue(ex.getMessage().contains("endpoint"), ex.getMessage());
   }

   // ─── endpoint selection + tagsMethod validation (named connector) ─────────

   @Test
   void setsEndpointAndBuildsSuffix() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      String applied = apply(query, params("endpoint", "Repos"));

      assertEquals("Repos", query.getEndpoint());
      assertTrue(applied.contains("endpoint"), applied);
   }

   @Test
   void rejectsUnknownEndpointName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Bogus")));
      assertTrue(ex.getMessage().contains("Bogus"), ex.getMessage());
   }

   @Test
   void rejectsPostEndpoint() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "PostEndpoint")));
      assertTrue(ex.getMessage().contains("POST endpoint"), ex.getMessage());
   }

   // ─── Kind A composite fill (RestParameters) ───────────────────────────────

   @Test
   void substitutesSuppliedParameterValues() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      apply(query, params("endpoint", "Repos", "parameters", Map.of("id", "42")));

      assertEquals("42", query.getParameters().findParameter("id").getValue());
   }

   @Test
   void rejectsMissingRequiredParameter() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Repos", "parameters", Map.of())));
      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
   }

   @Test
   void rejectsUnknownNestedParameterName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Repos",
            "parameters", Map.of("id", "1", "bogus", "1"))));
      assertTrue(ex.getMessage().contains("bogus"), ex.getMessage());
   }

   @Test
   void compositeValueMustBeAnObject() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Repos", "parameters", "not-an-object")));
      assertTrue(ex.getMessage().contains("parameters"), ex.getMessage());
   }

   // ─── Kind B refusal by name ────────────────────────────────────────────────

   @Test
   void refusesKindBCompositeByName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("additionalParameters", Map.of("x", "1"))));
      assertTrue(ex.getMessage().contains("additionalParameters"), ex.getMessage());
      assertTrue(ex.getMessage().contains("not supported yet"), ex.getMessage());
   }

   // ─── dependsOn topological ordering + tagsMethod validation (lookup chain) ─

   @Test
   void lookupChainSetsSingleLevel() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      apply(query, params("endpoint", "Repos", "lookupEndpoint0", "Issues"));

      assertEquals("Issues", query.getLookupEndpoint0());
   }

   @Test
   void lookupChainSetsTwoLevelsInOrder() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      // Deliberately out of dependency order in the map -- the topological sort, not map
      // iteration order, must still write endpoint, then lookupEndpoint0, then lookupEndpoint1.
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("lookupEndpoint1", "Comments");
      p.put("lookupEndpoint0", "Issues");
      p.put("endpoint", "Repos");

      apply(query, p);

      assertEquals("Issues", query.getLookupEndpoint0());
      assertEquals("Comments", query.getLookupEndpoint1());
   }

   @Test
   void lookupChainRejectsUnknownNameAtPositionZero() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Repos", "lookupEndpoint0", "Bogus")));
      assertTrue(ex.getMessage().contains("Issues"),
         "should name Repos' actual lookup choices, got: " + ex.getMessage());
   }

   /**
    * Proves the parent-endpoint chaining, not just single-level validation: an unknown name at
    * position 1 must be checked against position 0's CHOSEN endpoint's ("Issues") own lookups
    * ("Comments"), not position 0's parent ("Repos")'s.
    */
   @Test
   void lookupChainRejectsUnknownNameAtPositionOneNamingPositionOnesChoices() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params(
            "endpoint", "Repos", "lookupEndpoint0", "Issues", "lookupEndpoint1", "Bogus")));
      assertTrue(ex.getMessage().contains("Comments"), ex.getMessage());
   }

   @Test
   void lookupChainDefaultsLeaveConnectorDefaultsUntouched() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      apply(query, params("endpoint", "Repos", "lookupEndpoint0", "Issues"));

      assertTrue(query.isLookupExpanded());
      assertTrue(query.isLookupTopLevelOnly());
   }

   @Test
   void lookupChainSuppliedFalseIsReadBack() throws Exception {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      apply(query, params("endpoint", "Repos", "lookupEndpoint0", "Issues",
         "lookupExpanded", false, "lookupTopLevelOnly", false));

      assertFalse(query.isLookupExpanded());
      assertFalse(query.isLookupTopLevelOnly());
   }

   @Test
   void sixthLookupLevelIsAnUnknownTopLevelName() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("endpoint", "Repos", "lookupEndpoint5", "Whatever")));
      assertTrue(ex.getMessage().contains("lookupEndpoint5"), ex.getMessage());
   }

   // ─── general read-back-equality check (3.6) ───────────────────────────────

   @Test
   void customSuffixSetsSuffixAndJsonPath() throws Exception {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      apply(query, params("suffix", "/v1/widgets/{id}", "jsonPath", "$.data[*]"));

      assertEquals("/v1/widgets/{id}", query.getSuffix());
      assertEquals("$.data[*]", query.getJsonPath());
   }

   /**
    * The one test that specifically exercises the general read-back-equality check's silent-
    * failure boundary: writing {@code suffix} on a named connector's query (whose
    * {@code setSuffix} is a documented no-op) must be caught by the read-back, not silently
    * "succeed" while leaving the table bound to whatever {@code endpoint} happens to default to.
    */
   @Test
   void rejectsSilentNoOpOnNamedConnectorQuery() {
      FakeNamedConnectorQuery query = new FakeNamedConnectorQuery();

      IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> apply(query, params("suffix", "/v1/widgets/{id}")));
      assertTrue(ex.getMessage().contains("suffix"), ex.getMessage());
   }

   // ─── custom lookup URL placeholder validation (capability 6) ──────────────

   @Test
   void customLookupChainSetsFourFieldsPerLevel() throws Exception {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      apply(query, params(
         "lookupUrl0", "/v1/repos/{param1}/issues",
         "lookupJsonPath0", "$.data[*]",
         "lookupKey0", "id",
         "lookupUrl1", "/v1/issues/{param2}/comments",
         "lookupJsonPath1", "$.[*]",
         "lookupKey1", "id",
         "lookupIgnoreBaseUrl1", true));

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
   void customLookupChainDefaultsIgnoreBaseUrlToFalse() throws Exception {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      apply(query, params("lookupUrl0", "/v1/repos/{param1}/issues"));

      assertFalse(query.getLookupIgnoreBaseUrl0());
   }

   @Test
   void customLookupChainRejectsBlankUrl() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("lookupUrl0", "  ")));
      assertTrue(ex.getMessage().contains("lookupUrl0"), ex.getMessage());
   }

   /**
    * The {@code {paramN}} convention is not enforced anywhere in the runtime substitution path
    * -- so this validation exists ONLY here, before any property write, to refuse a URL
    * guaranteed to malfunction rather than accept it silently.
    */
   @Test
   void customLookupChainRejectsUrlMissingPlaceholder() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("lookupUrl0", "/v1/repos/issues")));
      assertTrue(ex.getMessage().contains("{param1}"), ex.getMessage());
      assertNull(query.getLookupUrl0(), "must refuse before writing anything");
   }

   @Test
   void customLookupChainRejectsPlaceholderAtWrongPosition() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      // Level 1 (0-indexed) must contain {param2}, not {param1}.
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params(
            "lookupUrl0", "/v1/repos/{param1}/issues",
            "lookupUrl1", "/v1/issues/{param1}/comments")));
      assertTrue(ex.getMessage().contains("{param2}"), ex.getMessage());
   }

   @Test
   void seventhCustomLookupLevelIsAnUnknownTopLevelName() {
      FakeCustomRestQuery query = new FakeCustomRestQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("lookupUrl5", "{param6}")));
      assertTrue(ex.getMessage().contains("lookupUrl5"), ex.getMessage());
   }

   // ─── cycle detection (capability 2) ───────────────────────────────────────

   @Test
   void dependsOnCycleThrowsNamingBothProperties() {
      FakeCyclicQuery query = new FakeCyclicQuery();

      IllegalStateException ex = assertThrows(IllegalStateException.class,
         () -> apply(query, params("a", "1", "b", "2")));
      assertTrue(ex.getMessage().contains("cycle"), ex.getMessage());
      assertTrue(ex.getMessage().contains("'a'") || ex.getMessage().contains("a,"),
         ex.getMessage());
      assertTrue(ex.getMessage().contains("'b'") || ex.getMessage().contains("b)") ||
         ex.getMessage().contains(", b"), ex.getMessage());
   }

   // ─── java.io.File string-path resolution (capability 5) ───────────────────

   @Test
   void filePathResolvesAndReadsBack(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);
      Files.writeString(root.resolve("q1.csv"), "a,b\n1,2\n");

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());

      apply(query, params("fileFolder", "q1.csv"));

      assertNotNull(query.getFileFolder());
      assertEquals("q1.csv", new File(root.toFile(), "q1.csv").getName());
      assertTrue(query.getFileFolder().getCanonicalPath()
         .startsWith(root.toFile().getCanonicalPath()));
   }

   @Test
   void filePathRejectsDotDot(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("fileFolder", "../outside.csv")));
      assertTrue(ex.getMessage().contains(".."), ex.getMessage());
   }

   @Test
   void filePathRejectsAbsolutePath(@TempDir Path tempDir) throws Exception {
      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(tempDir.toString());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("fileFolder", tempDir.resolve("q1.csv").toString())));
      assertTrue(ex.getMessage().contains("absolute"), ex.getMessage());
   }

   @Test
   void filePathRejectsNonexistentFileNamingThePath(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("fileFolder", "missing.csv")));
      assertTrue(ex.getMessage().contains("missing.csv"), ex.getMessage());
   }

   // ─── Excel multi-sheet-ambiguity refusal (capability 5b) ──────────────────

   @Test
   void excelAmbiguityRefusalVerbatimMessage(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);
      Files.writeString(root.resolve("sales.xlsx"), "not a real workbook, just needs to exist");

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());
      query.setExcelForTest(true);
      query.setSheetNamesForTest(new String[] { "Q1", "Q2", "Q3" });

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("fileFolder", "sales.xlsx")));
      assertTrue(ex.getMessage().contains(
         "has 3 sheets, so one has to be named: Q1, Q2, Q3."), ex.getMessage());
      assertTrue(ex.getMessage().contains("Supply it as queryParams.excelSheet."), ex.getMessage());
   }

   @Test
   void excelAmbiguitySupplyingValidSheetProceeds(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);
      Files.writeString(root.resolve("sales.xlsx"), "not a real workbook, just needs to exist");

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());
      query.setExcelForTest(true);
      query.setSheetNamesForTest(new String[] { "Q1", "Q2", "Q3" });

      apply(query, params("fileFolder", "sales.xlsx", "excelSheet", "Q2"));

      assertEquals("Q2", query.getExcelSheet());
   }

   @Test
   void excelAmbiguityInvalidSheetIsCaughtByTagsMethodValidation(@TempDir Path tempDir)
      throws Exception
   {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);
      Files.writeString(root.resolve("sales.xlsx"), "not a real workbook, just needs to exist");

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());
      query.setExcelForTest(true);
      query.setSheetNamesForTest(new String[] { "Q1", "Q2", "Q3" });

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, params("fileFolder", "sales.xlsx", "excelSheet", "Bogus")));
      assertTrue(ex.getMessage().contains("excelSheet"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Q1"), ex.getMessage());
   }

   @Test
   void singleSheetWorkbookIsNotAmbiguous(@TempDir Path tempDir) throws Exception {
      Path root = tempDir.resolve("root");
      Files.createDirectories(root);
      Files.writeString(root.resolve("sales.xlsx"), "not a real workbook, just needs to exist");

      FakeExcelLikeQuery query = new FakeExcelLikeQuery();
      query.setRootFolderForTest(root.toString());
      query.setExcelForTest(true);
      query.setSheetNamesForTest(new String[] { "Sheet1" });

      assertDoesNotThrow(() -> apply(query, params("fileFolder", "sales.xlsx")));
   }
}
