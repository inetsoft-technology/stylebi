/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behaviour of {@link TabularSchemaExtractor}, against a fixture connector rather than a real one.
 *
 * <p>A fixture is used deliberately. Every real tabular query class lives in a connector plugin and
 * is not on core's classpath, so testing against one would mean testing whatever connector happened
 * to be built — and the cases worth pinning here are structural, not connector-specific: a gate on
 * one value, a gate needing two, and a setter whose side effects make it look like a gate when it
 * is not. The fixture states each of those in a few lines, where finding a real connector that
 * exhibits all three would not.</p>
 */
@Tag("core")
class TabularSchemaExtractorTest {
   /**
    * {@code LayoutCreator} resolves labels through the connector's resource bundle, which it reaches
    * via {@code Config.getConfig()} — a Spring bean. There is no context in a plain unit test, so a
    * stub is installed that answers no bundle; the raw {@code @Property} label is then used, which
    * is what these assertions are about anyway.
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

   @Test
   void everyAnnotatedPropertyIsReported() {
      TabularQuerySchema schema = extract();
      Set<String> names = new HashSet<>();
      schema.getParams().forEach(p -> names.add(p.getName()));

      assertTrue(names.containsAll(Set.of("mode", "pageSize", "cursorField", "linkField",
                                          "linkStyle", "relation", "scoped", "scope", "scopeName",
                                          "detailed", "note", "hidden")),
                 "expected every @Property to be reported, got " + names);
   }

   /**
    * A property the {@code @View} annotation never mentions is still settable, so leaving it out of
    * the contract would describe the query as narrower than it is.
    */
   @Test
   void aPropertyOutsideTheViewIsStillReported() {
      TabularQuerySchema schema = extract();

      assertNotNull(schema.getParam("hidden"), "a @Property outside @View is still settable");
      assertTrue(schema.getUnreferencedParams().contains("hidden"),
                 "it should be marked as one the layout does not place");
   }

   @Test
   void theLabelIsTheDescription() {
      assertEquals("Page Size", extract().getParam("pageSize").getLabel());
   }

   /**
    * A LABEL element carries the format example, and it is attached to the field it follows — which
    * is where it renders and what it is written to explain. Reflection over {@code @Property} alone
    * never sees it.
    */
   @Test
   void aLabelElementBecomesAHintOnTheFieldItFollows() {
      assertEquals(List.of("Example: id,name"), extract().getParam("cursorField").getHints());
   }

   @Test
   void theJavaTypeIsReportedRatherThanTheEditorType() {
      assertEquals("int", extract().getParam("pageSize").getJavaType());
      assertEquals(Mode.class.getName(), extract().getParam("mode").getJavaType());
   }

   @Test
   void aGateOnOneValueIsFound() {
      Map<String, List<String>> byMode = extract().getDependencyMatrix().get("mode");

      assertNotNull(byMode, "mode gates other parameters, so it should be an axis");
      assertEquals(List.of("cursorField"), byMode.get("CURSOR"));
      assertEquals(List.of("linkField", "linkStyle"), byMode.get("LINK"));
      assertEquals(List.of(), byMode.get("NONE"));
   }

   /**
    * A parameter visible under every value of an axis is visible for reasons of its own. Saying it
    * depends on the axis would be wrong, and it would bury the ones that do.
    */
   @Test
   void aParameterVisibleUnderEveryValueIsNotReportedAsGated() {
      extract().getDependencyMatrix().getOrDefault("mode", Map.of()).forEach(
         (value, names) -> assertFalse(names.contains("pageSize"),
                                       "pageSize applies to every mode and is not gated on " + value));
   }

   /**
    * The case a single-value sweep cannot reach: {@code relation} needs LINK mode AND a HEADER link
    * style. The second parameter is itself only relevant under the first, which is the shape the
    * pair pass looks for.
    */
   @Test
   void aGateNeedingTwoValuesIsFound() {
      Map<String, List<String>> pair =
         extract().getDependencyMatrix().get("mode & linkStyle");

      assertNotNull(pair, "a gate needing two values should be reported under the pair");
      assertEquals(List.of("relation"), pair.get("mode=LINK & linkStyle=HEADER"));
   }

   /**
    * {@code detailed}'s setter grows the list its own panel's visibility is computed from, so
    * probing it reports the panel turning on and reads as though it gated {@code note}. It does not
    * — the real gate is elsewhere — so the axis is dropped rather than described wrongly.
    */
   @Test
   void anAxisThatTurnsItselfOnIsNotReportedAsAGate() {
      assertNull(extract().getDependencyMatrix().get("detailed"),
                 "a parameter whose setter is what makes it visible is not a gate");
   }

   /**
    * The outer half of a pair can be a boolean, and the matrix is keyed by the value's TEXT because
    * it is published as JSON. Re-probing from that text has to recover the value itself: handed
    * "true", a boolean setter's reflective invocation fails, {@code PropertyMeta.setValue} swallows
    * it, and the probe would run with the outer axis still false — finding nothing, and looking
    * exactly like there was nothing to find.
    */
   @Test
   void aGateNeedingTwoValuesIsFoundWhenTheOuterOneIsABoolean() {
      Map<String, List<String>> pair = extract().getDependencyMatrix().get("scoped & scope");

      assertNotNull(pair, "scopeName is gated on scoped=true AND scope=NAMED");
      assertEquals(List.of("scopeName"), pair.get("scoped=true & scope=NAMED"));
   }

   @Test
   void aParameterThatDoesNotApplyIsNamed() {
      FixtureQuery query = new FixtureQuery();
      query.setMode(Mode.LINK);

      assertEquals(Set.of("cursorField"),
                   new TabularSchemaExtractor().findInapplicable(
                      query, List.of("pageSize", "linkField", "cursorField")));
   }

   @Test
   void anApplicableParameterIsNotNamed() {
      FixtureQuery query = new FixtureQuery();
      query.setMode(Mode.CURSOR);

      assertTrue(new TabularSchemaExtractor().findInapplicable(
         query, List.of("pageSize", "cursorField")).isEmpty());
   }

   /**
    * A name the layout never places has no condition to evaluate, so there is no ground to call it
    * inapplicable — that question belongs to the schema, which says whether it exists at all.
    */
   @Test
   void aParameterOutsideTheLayoutIsNotCalledInapplicable() {
      assertTrue(new TabularSchemaExtractor().findInapplicable(
         new FixtureQuery(), List.of("hidden")).isEmpty());
   }

   private TabularQuerySchema extract() {
      return new TabularSchemaExtractor().extract(new FixtureQuery(), FixtureQuery.TYPE);
   }

   private static ConfigurationContext previous;

   public enum Mode { NONE, CURSOR, LINK }

   public enum LinkStyle { BODY, HEADER }

   public enum Scope { ALL, NAMED }

   /**
    * A connector reduced to the structures under test.
    */
   @View(vertical = true, value = {
      @View1(vertical = true, type = ViewType.PANEL, elements = {
         @View2("mode"),
         @View2("pageSize"),
         @View2(value = "cursorField", visibleMethod = "isCursorMode"),
         @View2(type = ViewType.LABEL, text = "Example: id,name"),
         @View2(value = "linkField", visibleMethod = "isLinkMode"),
         @View2(value = "linkStyle", visibleMethod = "isLinkMode"),
         @View2(value = "relation", visibleMethod = "isHeaderLink"),
         @View2(value = "scope", visibleMethod = "isScoped"),
         @View2(value = "scopeName", visibleMethod = "isNamedScope"),
      }),
      @View1(value = "scoped"),
      @View1(value = "detailed"),
      @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isDetailVisible", elements = {
         @View2("note"),
      }),
   })
   public static class FixtureQuery extends TabularQuery {
      public FixtureQuery() {
         super(TYPE);
      }

      @Property(label = "Mode")
      @PropertyEditor(tags = { "NONE", "CURSOR", "LINK" })
      public Mode getMode() {
         return mode;
      }

      public void setMode(Mode mode) {
         this.mode = mode;
      }

      @Property(label = "Page Size")
      public int getPageSize() {
         return pageSize;
      }

      public void setPageSize(int pageSize) {
         this.pageSize = pageSize;
      }

      @Property(label = "Cursor Field")
      public String getCursorField() {
         return cursorField;
      }

      public void setCursorField(String cursorField) {
         this.cursorField = cursorField;
      }

      @Property(label = "Link Field")
      public String getLinkField() {
         return linkField;
      }

      public void setLinkField(String linkField) {
         this.linkField = linkField;
      }

      @Property(label = "Link Style")
      @PropertyEditor(tags = { "BODY", "HEADER" })
      public LinkStyle getLinkStyle() {
         return linkStyle;
      }

      public void setLinkStyle(LinkStyle linkStyle) {
         this.linkStyle = linkStyle;
      }

      @Property(label = "Relation")
      public String getRelation() {
         return relation;
      }

      public void setRelation(String relation) {
         this.relation = relation;
      }

      @Property(label = "Scoped")
      public boolean isScoped() {
         return scoped;
      }

      public void setScoped(boolean scoped) {
         this.scoped = scoped;
      }

      @Property(label = "Scope")
      @PropertyEditor(tags = { "ALL", "NAMED" })
      public Scope getScope() {
         return scope;
      }

      public void setScope(Scope scope) {
         this.scope = scope;
      }

      @Property(label = "Scope Name")
      public String getScopeName() {
         return scopeName;
      }

      public void setScopeName(String scopeName) {
         this.scopeName = scopeName;
      }

      /** Its setter is what makes its own panel appear -- see the test that pins this. */
      @Property(label = "Detailed")
      public boolean isDetailed() {
         return detailed;
      }

      public void setDetailed(boolean detailed) {
         this.detailed = detailed;
         this.notes.add("note");
      }

      @Property(label = "Note")
      public String getNote() {
         return note;
      }

      public void setNote(String note) {
         this.note = note;
      }

      /** Annotated but never placed by {@code @View}. */
      @Property(label = "Hidden")
      public String getHidden() {
         return hidden;
      }

      public void setHidden(String hidden) {
         this.hidden = hidden;
      }

      public boolean isCursorMode() {
         return mode == Mode.CURSOR;
      }

      public boolean isLinkMode() {
         return mode == Mode.LINK;
      }

      public boolean isHeaderLink() {
         return isLinkMode() && linkStyle == LinkStyle.HEADER;
      }

      public boolean isNamedScope() {
         return scoped && scope == Scope.NAMED;
      }

      public boolean isDetailVisible() {
         return !notes.isEmpty();
      }

      static final String TYPE = "Test.Fixture";

      private Mode mode = Mode.NONE;
      private int pageSize = 100;
      private String cursorField;
      private String linkField;
      private LinkStyle linkStyle = LinkStyle.BODY;
      private String relation;
      private boolean scoped;
      private Scope scope = Scope.ALL;
      private String scopeName;
      private boolean detailed;
      private String note;
      private String hidden;
      private final List<String> notes = new ArrayList<>();
   }
}
