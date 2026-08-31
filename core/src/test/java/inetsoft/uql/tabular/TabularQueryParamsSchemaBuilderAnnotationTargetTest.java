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
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The {@code x-annotationTarget} marker {@link TabularQueryParamsSchemaBuilder#applyTagsMethod}
 * adds for an {@link AnnotatableQuery} connector (charter B3's "queryParams keys are real" --
 * per the P2 reconcile's decision (03-reconcile.md, sec. 2), this marker and {@code queryContracts}
 * read the same reflection pass, so a key-subset assertion against this response is sufficient;
 * no separate manual source-cross-check is needed).
 */
@Tag("core")
class TabularQueryParamsSchemaBuilderAnnotationTargetTest {
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

   @Test
   void marksOnlyTheDeclaredAnnotationTargetProperty() {
      JsonNode properties = properties(new Fixture(), true);

      assertTrue(properties.get("entity").get("x-annotationTarget").asBoolean(),
         "the property AnnotatableQuery.getAnnotationTargetProperty() names must carry the marker");
      assertFalse(properties.get("other").has("x-annotationTarget"),
         "an ordinary tagsMethod property that is NOT the declared target must not carry it");
   }

   @Test
   void isSetEvenWhenResolveTagsIsFalse() {
      // Design note (01-design.md sec. 3.3): the marker has to survive a resolveTags=false
      // response too, because a caller needs the property NAME before it can make the follow-up
      // resolveTags=true request that actually resolves candidate values.
      JsonNode properties = properties(new Fixture(), false);

      assertTrue(properties.get("entity").get("x-annotationTarget").asBoolean());
      assertEquals("external", properties.get("entity").get("x-valueSource").asText());
      assertFalse(properties.get("entity").has("enum"));
   }

   @Test
   void isAbsentEntirelyForAConnectorThatDoesNotImplementAnnotatableQuery() {
      JsonNode properties = properties(new NonAnnotatableFixture(), true);

      assertFalse(properties.get("entity").has("x-annotationTarget"),
         "SharePoint-shaped connectors deliberately do not implement AnnotatableQuery -- see " +
         "AnnotatableQuery's javadoc -- so no property of theirs should ever carry this marker");
   }

   // ─── helpers ────────────────────────────────────────────────────────────

   private static JsonNode properties(TabularQuery query, boolean resolveTags) {
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, "Test.AnnotationTargetFixture");
      return TabularQueryParamsSchemaBuilder.build(query, schema, resolveTags).get("properties");
   }

   private static ConfigurationContext previous;

   @View(vertical = true, value = { @View1("entity"), @View1("other") })
   public static class Fixture extends TabularQuery implements AnnotatableQuery {
      public Fixture() {
         super(TYPE);
      }

      @Property(label = "Entity")
      @PropertyEditor(tagsMethod = "getEntityChoices")
      public String getEntity() {
         return entity;
      }

      public void setEntity(String entity) {
         this.entity = entity;
      }

      public String[][] getEntityChoices() {
         return new String[][] { { "Customers", "Customers" } };
      }

      @Property(label = "Other")
      @PropertyEditor(tagsMethod = "getOtherChoices")
      public String getOther() {
         return other;
      }

      public void setOther(String other) {
         this.other = other;
      }

      public String[][] getOtherChoices() {
         return new String[][] { { "X", "x" } };
      }

      @Override
      public String getAnnotationTargetProperty() {
         return "entity";
      }

      static final String TYPE = "Test.AnnotationTargetFixture";

      private String entity;
      private String other;
   }

   /** Same shape as {@link Fixture}, minus the AnnotatableQuery implementation. */
   @View(vertical = true, value = { @View1("entity") })
   public static class NonAnnotatableFixture extends TabularQuery {
      public NonAnnotatableFixture() {
         super("Test.NonAnnotatableFixture");
      }

      @Property(label = "Entity")
      @PropertyEditor(tagsMethod = "getEntityChoices")
      public String getEntity() {
         return entity;
      }

      public void setEntity(String entity) {
         this.entity = entity;
      }

      public String[][] getEntityChoices() {
         return new String[][] { { "Customers", "Customers" } };
      }

      private String entity;
   }
}
