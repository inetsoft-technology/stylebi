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
package inetsoft.uql.tabular;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TabularQueryParamsSchemaBuilder#build}, against the same kind of purpose-built fixture
 * {@link TabularSchemaExtractorTest} uses -- see that class's own doc for why a fixture rather
 * than a real connector class.
 */
@Tag("core")
class TabularQueryParamsSchemaBuilderTest {
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

   // ─── scalar mapping ────────────────────────────────────────────────────

   @Test
   void scalarMinMaxMapToJsonSchemaMinimumMaximum() {
      JsonNode pageSize = properties().get("pageSize");

      assertEquals("integer", pageSize.get("type").asText());
      assertEquals(1, pageSize.get("minimum").asInt());
      assertEquals(500, pageSize.get("maximum").asInt());
   }

   @Test
   void singlePatternMapsToAPlainPatternField() {
      JsonNode namePattern = properties().get("namePattern");

      assertEquals("^[a-z]+$", namePattern.get("pattern").asText());
      assertFalse(namePattern.has("allOf"), "a single pattern must not use allOf");
   }

   @Test
   void twoPatternsMapToAnAllOfOfTwoPatternEntries() {
      JsonNode twoPattern = properties().get("twoPatternField");

      assertFalse(twoPattern.has("pattern"),
         "JSON Schema's own 'pattern' keyword takes exactly one regex");
      assertTrue(twoPattern.get("allOf").isArray());
      assertEquals(2, twoPattern.get("allOf").size());
      assertEquals("^[a-z]+$", twoPattern.get("allOf").get(0).get("pattern").asText());
      assertEquals("^.{1,10}$", twoPattern.get("allOf").get(1).get("pattern").asText());
   }

   @Test
   void fixedTagsWithNoTagsMethodInlineAsEnum() {
      JsonNode colorChoice = properties().get("colorChoice");

      assertEquals(List.of("RED", "GREEN"), textList(colorChoice.get("enum")));
      assertFalse(colorChoice.has("x-valueSource"),
         "a fixed, always-present tag list is not a tagsMethod value source");
   }

   // ─── root keywords (11.4) ──────────────────────────────────────────────

   @Test
   void rootCarriesUnevaluatedPropertiesNeverAdditionalProperties() {
      JsonNode root = build(false);

      assertFalse(root.has("additionalProperties"),
         "a root additionalProperties:false would reject every branch-introduced param");
      assertTrue(root.has("unevaluatedProperties"));
      assertFalse(root.get("unevaluatedProperties").asBoolean());
   }

   @Test
   void rootCarriesTheDraft2020SchemaDialect() {
      JsonNode root = build(false);

      assertEquals("https://json-schema.org/draft/2020-12/schema", root.get("$schema").asText());
   }

   // ─── dependencyMatrix -> allOf/if-then, both shapes ────────────────────

   @Test
   void gatedParamsAreAbsentFromTopLevelProperties() {
      JsonNode properties = properties();

      assertTrue(properties.has("paginationType"));
      assertFalse(properties.has("linkParamType"),
         "linkParamType is gated on paginationType and must only appear inside allOf");
      assertFalse(properties.has("linkRelation"),
         "linkRelation is gated on a combination and must only appear inside allOf");
   }

   @Test
   void singleAxisGateProducesAnIfThenBranch() {
      ArrayNode allOf = allOf();
      JsonNode branch = findBranchAdmitting(allOf, "linkParamType");

      assertNotNull(branch, "expected a branch whose 'then' admits linkParamType");
      assertEquals("LINK_ITERATION",
         branch.get("if").get("properties").get("paginationType").get("const").asText());
      assertEquals(List.of("paginationType"), textList(branch.get("if").get("required")));
   }

   @Test
   void combinationGateProducesACompoundIfWithBothAxisNames() {
      ArrayNode allOf = allOf();
      JsonNode branch = findBranchAdmitting(allOf, "linkRelation");

      assertNotNull(branch, "expected a branch whose 'then' admits linkRelation");
      JsonNode ifProps = branch.get("if").get("properties");
      assertEquals("LINK_ITERATION", ifProps.get("paginationType").get("const").asText());
      assertEquals("LINK_HEADER", ifProps.get("linkParamType").get("const").asText());
      assertEquals(Set.of("paginationType", "linkParamType"),
         new HashSet<>(textList(branch.get("if").get("required"))));
   }

   // ─── composite params, Kind A vs Kind B at schema-generation time ──────

   @Test
   void kindAWithDependsOnCarriesXSkeletonAndIsNeverRequired() {
      JsonNode fragment = properties().get("compositeGated");

      assertEquals("object", fragment.get("type").asText());
      assertEquals("string",
         fragment.get("additionalProperties").get("type").asText());
      assertEquals("base", fragment.get("x-skeleton").asText());
      assertFalse(requiredNames().contains("compositeGated"),
         "a Kind A composite must never be in required, regardless of @Property.required");
   }

   @Test
   void kindAWithNoDependsOnAndAnAlreadyNamedSkeletonHasNoXSkeleton() {
      JsonNode fragment = properties().get("compositeImmediate");

      assertEquals("object", fragment.get("type").asText());
      assertFalse(fragment.has("x-skeleton"));
      assertTrue(fragment.get("description").asText().contains("key"),
         "expected the live skeleton's element name ('key') to be named in the description");
   }

   @Test
   void kindBIsOmittedFromTheSchemaEntirelyByDefault() {
      JsonNode properties = properties();

      assertFalse(properties.has("manualColumns"),
         "a Kind B composite (no dependsOn, empty/unnamed on a blank query) must be omitted");
      assertFalse(requiredNames().contains("manualColumns"),
         "must never be required even though the fixture declares @Property(required = true)");
   }

   // ─── x-valueSource default (resolveTags = false) ───────────────────────

   @Test
   void tagsMethodParamDefaultsToExternalWithNoInlinedEnum() {
      JsonNode independentChoice = properties().get("independentChoice");

      assertEquals("external", independentChoice.get("x-valueSource").asText());
      assertFalse(independentChoice.has("enum"));

      // The connector method behind the value set is deliberately NOT published. No consumer can
      // act on it: an agent cannot invoke a Java method, and resolveTags is a boolean the server
      // resolves itself, so publishing the name only spent context and invited a caller to try
      // something it cannot do.
      assertFalse(independentChoice.has("x-tagsMethod"));
   }

   @Test
   void everyXKeyIsPairedWithANonEmptyDescriptionNamingTheConcreteOrigin() {
      JsonNode properties = properties();
      Iterator<String> names = properties.fieldNames();
      boolean sawAnXKey = false;

      while(names.hasNext()) {
         String name = names.next();
         JsonNode fragment = properties.get(name);
         Iterator<String> fieldNames = fragment.fieldNames();
         boolean hasXKey = false;

         while(fieldNames.hasNext()) {
            if(fieldNames.next().startsWith("x-")) {
               hasXKey = true;
               break;
            }
         }

         if(!hasXKey) {
            continue;
         }

         sawAnXKey = true;
         String description = fragment.path("description").asText("");
         assertFalse(description.isEmpty(), name + " carries an x- key but no description");
         assertFalse(description.toLowerCase(Locale.ROOT).contains("catalog"),
            name + "'s description must name the concrete origin, never the word 'catalog'");
      }

      assertTrue(sawAnXKey, "fixture setup bug: expected at least one property with an x- key");
   }

   // ─── helpers ────────────────────────────────────────────────────────────


   // --- role formats, and the wire a consumer actually receives ----------

   /**
    * A file property and a sheet selector are matched by NAME, the same way every parse option
    * already is, so no property carries a role marker.
    *
    * <p>The marker this replaces was inferred rather than declared: "String with a tagsMethod that
    * dependsOn the file property" is a description of an editor that recomputes its choices when the
    * file changes, which is equally true of a charset picker. Both file connectors shipped declare
    * two such properties (excelSheet and encoding), and one of them names its file with a String
    * rather than a java.io.File, so neither half of the inference held on either connector.</p>
    */
   @Test
   void noPropertyCarriesARoleFormat() {
      JsonNode fileProps = excelSchema().get("properties");

      assertEquals("string", fileProps.get("fileFolder").get("type").asText());
      assertFalse(fileProps.get("fileFolder").has("format"),
                  "the file property is matched by name, not by a role marker");
      assertFalse(fileProps.get("excelSheet").has("format"),
                  "and so is the sheet selector");

      JsonNode props = properties();

      assertFalse(props.get("pageSize").has("format"));
      assertFalse(props.get("namePattern").has("format"));
   }

   /**
    * What a caller matches on instead: the property's own name, the label the connector gave it, and
    * the pattern it declares. Publishing these is what lets `binding.path` and `binding.sheet` be
    * placed without a marker.
    */
   @Test
   void aFilePropertyStaysIdentifiableByItsLabelAndPattern() {
      JsonNode props = excelSchema().get("properties");

      assertTrue(props.get("fileFolder").get("description").asText().contains("File Folder"));
      assertEquals("^.*\\.(txt|csv|xls|xlsx)$", props.get("fileFolder").get("pattern").asText());
      assertTrue(props.get("excelSheet").get("description").asText().contains("Sheet"));
   }

   @Test
   void viewLevelNotesSurviveOnTheRootDescription() {
      // A @View LABEL belonging to no single property has no fragment to ride on. Folded into the
      // root description it is still delivered, which is what lets the schema be the whole
      // contract now that the notes array is no longer published.
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(new Fixture(), Fixture.TYPE);
      JsonNode root = TabularQueryParamsSchemaBuilder.build(new Fixture(), schema, false);

      for(String note : schema.getNotes()) {
         if(note != null && !note.isBlank()) {
            assertTrue(root.get("description").asText().contains(note.trim()),
                       "root description must carry the @View note: " + note);
         }
      }
   }

   @Test
   void theContractCarriesOnlyWhatAConsumerCanActOn() {
      // TabularQuerySchema is the extractor's working view and is not serialized anywhere; what a
      // caller receives is this record, whose component list IS the wire. Kept as a test because
      // the wire is a promise: a field added here is a field every consumer must then handle.
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(new Fixture(), Fixture.TYPE);
      TabularQueryContract contract = new TabularQueryContract(
         schema.getDataSourceType(),
         TabularQueryParamsSchemaBuilder.build(new Fixture(), schema, false));

      JsonNode wire = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(contract);
      List<String> fields = new ArrayList<>();
      wire.fieldNames().forEachRemaining(fields::add);
      Collections.sort(fields);

      assertEquals(List.of("dataSourceType", "queryParamsSchema"), fields);
      assertEquals(schema.getDataSourceType(), wire.get("dataSourceType").asText());
   }

   private JsonNode excelSchema() {
      inetsoft.web.wiz.service.FakeExcelLikeQuery query =
         new inetsoft.web.wiz.service.FakeExcelLikeQuery();
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, "FakeExcelLike");
      return TabularQueryParamsSchemaBuilder.build(query, schema, false);
   }

   private JsonNode properties() {
      return build(false).get("properties");
   }

   private ArrayNode allOf() {
      return (ArrayNode) build(false).get("allOf");
   }

   private List<String> requiredNames() {
      return textList(build(false).get("required"));
   }

   private JsonNode build(boolean resolveTags) {
      Fixture query = new Fixture();
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, Fixture.TYPE);
      return TabularQueryParamsSchemaBuilder.build(query, schema, resolveTags);
   }

   private static JsonNode findBranchAdmitting(ArrayNode allOf, String gatedName) {
      for(JsonNode branch : allOf) {
         if(branch.get("then").get("properties").has(gatedName)) {
            return branch;
         }
      }

      return null;
   }

   private static List<String> textList(JsonNode arrayNode) {
      List<String> result = new ArrayList<>();
      arrayNode.forEach(n -> result.add(n.asText()));
      return result;
   }

   private static ConfigurationContext previous;

   /**
    * A connector reduced to the structures {@link TabularQueryParamsSchemaBuilder} maps, modeled
    * on the same proven single-axis/combination-gate shape {@link TabularSchemaExtractorTest}'s
    * own fixture uses (paginationType/linkParamType/linkRelation here, mirroring
    * ActiveCampaignQuery's real paginationType/linkParamType/linkRelation shape the design doc
    * cites -- section 11.4).
    */
   @View(vertical = true, value = {
      @View1("base"),
      @View1("pageSize"),
      @View1("namePattern"),
      @View1("twoPatternField"),
      @View1("colorChoice"),
      @View1("independentChoice"),
      @View1("dependentChoice"),
      @View1("compositeGated"),
      @View1("compositeImmediate"),
      @View1("manualColumns"),
      @View1("paginationType"),
      @View1(value = "linkParamType", visibleMethod = "isLinkIteration"),
      @View1(value = "linkRelation", visibleMethod = "isLinkHeader"),
   })
   public static class Fixture extends TabularQuery {
      public Fixture() {
         super(TYPE);
      }

      @Property(label = "Base")
      public String getBase() {
         return base;
      }

      public void setBase(String base) {
         this.base = base;
      }

      @Property(label = "Page Size", min = 1, max = 500)
      public int getPageSize() {
         return pageSize;
      }

      public void setPageSize(int pageSize) {
         this.pageSize = pageSize;
      }

      @Property(label = "Name Pattern", pattern = "^[a-z]+$")
      public String getNamePattern() {
         return namePattern;
      }

      public void setNamePattern(String namePattern) {
         this.namePattern = namePattern;
      }

      @Property(label = "Two Pattern Field", pattern = { "^[a-z]+$", "^.{1,10}$" })
      public String getTwoPatternField() {
         return twoPatternField;
      }

      public void setTwoPatternField(String twoPatternField) {
         this.twoPatternField = twoPatternField;
      }

      @Property(label = "Color Choice")
      @PropertyEditor(tags = { "RED", "GREEN" })
      public String getColorChoice() {
         return colorChoice;
      }

      public void setColorChoice(String colorChoice) {
         this.colorChoice = colorChoice;
      }

      /** No dependsOn -- exercises the un-gated tagsMethod path (resolveTags default/true). */
      @Property(label = "Independent Choice")
      @PropertyEditor(tagsMethod = "getIndependentChoices")
      public String getIndependentChoice() {
         return independentChoice;
      }

      public void setIndependentChoice(String independentChoice) {
         this.independentChoice = independentChoice;
      }

      public String[][] getIndependentChoices() {
         return new String[][] { { "Alpha", "alpha" }, { "Beta", "beta" } };
      }

      /** dependsOn = "base" -- must stay x-valueSource: external even under resolveTags=true. */
      @Property(label = "Dependent Choice")
      @PropertyEditor(dependsOn = "base", tagsMethod = "getDependentChoices")
      public String getDependentChoice() {
         return dependentChoice;
      }

      public void setDependentChoice(String dependentChoice) {
         this.dependentChoice = dependentChoice;
      }

      public String[][] getDependentChoices() {
         return new String[][] { { "X", "x" } };
      }

      /**
       * Kind A, dependsOn-gated -- like {@code EndpointJsonQuery.parameters}. Declared
       * {@code required = true} to pin A2/A4: a Kind A composite must never appear in
       * {@code required} regardless of what {@code @Property} says.
       */
      @Property(label = "Composite Gated", required = true)
      @PropertyEditor(dependsOn = "base")
      public RestParameters getCompositeGated() {
         RestParameters rp = new RestParameters();
         List<RestParameter> list = new ArrayList<>();

         if(base != null) {
            RestParameter p = new RestParameter();
            p.setName("id");
            list.add(p);
         }

         rp.setParameters(list);
         return rp;
      }

      public void setCompositeGated(RestParameters compositeGated) {
      }

      /** Kind A, NO dependsOn, already resolves to a named skeleton on a blank query. */
      @Property(label = "Composite Immediate")
      public RestParameters getCompositeImmediate() {
         RestParameters rp = new RestParameters();
         RestParameter p = new RestParameter();
         p.setName("key");
         rp.setParameters(List.of(p));
         return rp;
      }

      public void setCompositeImmediate(RestParameters compositeImmediate) {
      }

      /**
       * Kind B, schema-time detectable -- like {@code ServerFileQuery.columns}: no dependsOn,
       * always null on a blank query. {@code required = true} pins A2's deadlock case.
       */
      @Property(label = "Manual Columns", required = true)
      public HttpParameter[] getManualColumns() {
         return null;
      }

      public void setManualColumns(HttpParameter[] manualColumns) {
      }

      @Property(label = "Pagination Type")
      @PropertyEditor(tags = { "NONE", "LINK_ITERATION" })
      public String getPaginationType() {
         return paginationType;
      }

      public void setPaginationType(String paginationType) {
         this.paginationType = paginationType;
      }

      @Property(label = "Link Param Type")
      @PropertyEditor(tags = { "LINK_HEADER", "LINK_BODY" })
      public String getLinkParamType() {
         return linkParamType;
      }

      public void setLinkParamType(String linkParamType) {
         this.linkParamType = linkParamType;
      }

      @Property(label = "Link Relation")
      public String getLinkRelation() {
         return linkRelation;
      }

      public void setLinkRelation(String linkRelation) {
         this.linkRelation = linkRelation;
      }

      public boolean isLinkIteration() {
         return "LINK_ITERATION".equals(paginationType);
      }

      public boolean isLinkHeader() {
         return isLinkIteration() && "LINK_HEADER".equals(linkParamType);
      }

      static final String TYPE = "Test.SchemaFixture";

      private String base;
      private int pageSize = 100;
      private String namePattern;
      private String twoPatternField;
      private String colorChoice;
      private String independentChoice;
      private String dependentChoice;
      private String paginationType;
      private String linkParamType;
      private String linkRelation;
   }
}
