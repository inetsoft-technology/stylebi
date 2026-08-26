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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code resolveTags=true} (design doc section 12): the per-parameter degradation states and
 * the one case that never resolves regardless -- a {@code dependsOn}-gated param.
 *
 * <p>The timeout case genuinely waits out {@link TabularQueryParamsSchemaBuilder}'s fixed 5
 * second budget (section 12.3 -- not exposed as a tunable, so there is nothing shorter to
 * substitute here); that is the one slow test in this class.</p>
 */
@Tag("core")
class TabularQueryParamsSchemaBuilderResolveTagsTest {
   @BeforeAll
   static void installContext() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(org.mockito.ArgumentMatchers.any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);

      // Built ONCE and shared: build() resolves every tagsMethod-bearing param in one pass, so
      // the 'slow' fixture's 5 second timeout is paid once here rather than once per test method
      // that happens to also read a DIFFERENT property out of the same resolveTags=true schema.
      resolveTagsTrueProperties = properties(true);
   }

   @AfterAll
   static void clearContext() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   @Test
   void dependentParamIsSkippedEvenWithResolveTagsTrue() {
      JsonNode dependent = resolveTagsTrueProperties.get("dependent");

      assertEquals("external", dependent.get("x-valueSource").asText());
      assertFalse(dependent.has("enum"),
         "a dependsOn-gated tagsMethod is never invoked here -- its own prerequisite is unset " +
         "on this blank query, so an empty enum would read as 'no legal value exists' instead " +
         "of 'set the prerequisite first'");
   }

   @Test
   void noDependsOnParamInlinesEnumAndLabelsInIndexOrder() {
      JsonNode normal = resolveTagsTrueProperties.get("normal");

      assertFalse(normal.has("x-valueSource"),
         "resolution succeeded -- 'enum' itself is the signal the value set is present");
      assertEquals(List.of("alpha", "beta"), textList(normal.get("enum")));
      assertEquals(List.of("Alpha", "Beta"), textList(normal.get("x-enumLabels")));
   }

   @Test
   void timeoutYieldsUnavailableWithNoEnumAndDoesNotHangTheRequest() {
      JsonNode slow = resolveTagsTrueProperties.get("slow");

      assertEquals("unavailable", slow.get("x-valueSource").asText());
      assertFalse(slow.has("enum"));
      assertTrue(slow.get("description").asText().toLowerCase(Locale.ROOT).contains("retry"),
         "the agent's only next action on 'unavailable' is to retry");
   }

   @Test
   void exceptionYieldsTheSameUnavailableOutcomeAsATimeout() {
      JsonNode throwing = resolveTagsTrueProperties.get("throwing");

      assertEquals("unavailable", throwing.get("x-valueSource").asText());
      assertFalse(throwing.has("enum"));
   }

   @Test
   void overCapYieldsTooLargeWithCandidateCountAndNoEnum() {
      JsonNode huge = resolveTagsTrueProperties.get("huge");

      assertEquals("too-large", huge.get("x-valueSource").asText());
      assertEquals(250, huge.get("x-candidateCount").asInt());
      assertFalse(huge.has("enum"));
      assertTrue(huge.get("description").asText().toLowerCase(Locale.ROOT)
            .contains("ask the user"),
         "the agent's only next action on 'too-large' is to narrow the request (ask the user " +
         "to name the value directly), not retry blindly");
   }

   @Test
   void resolveTagsFalseNeverInvokesAnyTagsMethodRegardless() {
      JsonNode normal = properties(false).get("normal");

      assertEquals("external", normal.get("x-valueSource").asText());
      assertFalse(normal.has("enum"));
   }

   // ─── helpers ────────────────────────────────────────────────────────────

   private static JsonNode properties(boolean resolveTags) {
      Fixture query = new Fixture();
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, Fixture.TYPE);
      return TabularQueryParamsSchemaBuilder.build(query, schema, resolveTags).get("properties");
   }

   private static List<String> textList(JsonNode arrayNode) {
      List<String> result = new ArrayList<>();

      if(arrayNode != null) {
         arrayNode.forEach(n -> result.add(n.asText()));
      }

      return result;
   }

   private static ConfigurationContext previous;
   private static JsonNode resolveTagsTrueProperties;

   @View(vertical = true, value = {
      @View1("base"),
      @View1("dependent"),
      @View1("normal"),
      @View1("slow"),
      @View1("huge"),
      @View1("throwing"),
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

      @Property(label = "Dependent")
      @PropertyEditor(dependsOn = "base", tagsMethod = "getDependentChoices")
      public String getDependent() {
         return dependent;
      }

      public void setDependent(String dependent) {
         this.dependent = dependent;
      }

      public String[][] getDependentChoices() {
         return new String[][] { { "Would", "would" } };
      }

      @Property(label = "Normal")
      @PropertyEditor(tagsMethod = "getNormalChoices")
      public String getNormal() {
         return normal;
      }

      public void setNormal(String normal) {
         this.normal = normal;
      }

      public String[][] getNormalChoices() {
         return new String[][] { { "Alpha", "alpha" }, { "Beta", "beta" } };
      }

      @Property(label = "Slow")
      @PropertyEditor(tagsMethod = "getSlowChoices")
      public String getSlow() {
         return slow;
      }

      public void setSlow(String slow) {
         this.slow = slow;
      }

      /** Sleeps past the builder's fixed 5 second per-call budget (section 12.3). */
      public String[][] getSlowChoices() throws InterruptedException {
         Thread.sleep(30_000);
         return new String[][] { { "Too", "slow" } };
      }

      @Property(label = "Huge")
      @PropertyEditor(tagsMethod = "getHugeChoices")
      public String getHuge() {
         return huge;
      }

      public void setHuge(String huge) {
         this.huge = huge;
      }

      /** 250 candidates -- past the builder's fixed 200 candidate cap (section 12.3). */
      public String[][] getHugeChoices() {
         String[][] result = new String[250][2];

         for(int i = 0; i < result.length; i++) {
            result[i] = new String[] { "Candidate " + i, "c" + i };
         }

         return result;
      }

      @Property(label = "Throwing")
      @PropertyEditor(tagsMethod = "getThrowingChoices")
      public String getThrowing() {
         return throwing;
      }

      public void setThrowing(String throwing) {
         this.throwing = throwing;
      }

      public String[][] getThrowingChoices() {
         throw new IllegalStateException("connector error, on purpose, for this test");
      }

      static final String TYPE = "Test.ResolveTagsFixture";

      private String base;
      private String dependent;
      private String normal;
      private String slow;
      private String huge;
      private String throwing;
   }
}
