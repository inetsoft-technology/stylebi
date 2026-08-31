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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
// Two pieces of process-wide state are written here: the ConfigurationContext the @BeforeAll
// installs, and the probe deadline and poison record restoreProbeDefaults() hands back. Neither can
// be made per-test -- the poison record is process-wide BY DESIGN, since what it bounds is threads
// this process cannot reclaim -- so the class is serialized against anything else claiming them
// instead.
@ResourceLock("TabularSchemaExtractor-process-state")
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

   /**
    * Both the deadline and the record of which setters have hung are per-process, so a case that
    * changes either has to hand them back -- otherwise it decides the next one.
    */
   @AfterEach
   void restoreProbeDefaults() {
      TabularSchemaExtractor.setProbeTimeout(2000);
      TabularSchemaExtractor.clearPoisoned();
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

   /**
    * The case that motivates writing only what is on screen: a boolean whose editor sits in a panel
    * nothing has turned on yet. Its setter is never entered, because a value written there puts the
    * bean in a state no dialog can produce -- and on a real REST connector, the state one such
    * setter loops in forever.
    */
   @Test
   void aValueTheLayoutDoesNotShowIsNeverWritten() {
      OffScreenFixtureQuery.writes.set(0);

      TabularQuerySchema schema = new TabularSchemaExtractor()
         .extract(new OffScreenFixtureQuery(), OffScreenFixtureQuery.TYPE);

      assertEquals(0, OffScreenFixtureQuery.writes.get(),
                   "a parameter the layout does not show should not be written by a probe");
      assertNotNull(schema.getParam("offScreen"), "it is still reported as a parameter");
      assertNull(schema.getDependencyMatrix().get("offScreen"),
                 "and it gates nothing, having never been varied");
      assertNotNull(schema.getDependencyMatrix().get("mode"),
                    "the axes that CAN be set are still probed");
   }

   /**
    * A setter that does not return costs the axis it was probing and nothing else. The rest of the
    * matrix is still built, and the property is not handed to a probe a second time -- the thread
    * an abandoned probe strands cannot be reclaimed, so a caller that retries must not be able to
    * strand another.
    */
   @Test
   void aSetterThatDoesNotReturnIsAbandonedAndNotProbedAgain() {
      TabularSchemaExtractor.setProbeTimeout(150);
      StuckFixtureQuery.entered.set(0);

      TabularQuerySchema schema = new TabularSchemaExtractor()
         .extract(new StuckFixtureQuery(), StuckFixtureQuery.TYPE);

      assertEquals(1, StuckFixtureQuery.entered.get(), "the hanging setter is entered once");
      assertNull(schema.getDependencyMatrix().get("stuck"),
                 "an axis whose setter did not return is dropped rather than described");
      assertNotNull(schema.getDependencyMatrix().get("mode"),
                    "and the axes that do return are still probed");

      new TabularSchemaExtractor().extract(new StuckFixtureQuery(), StuckFixtureQuery.TYPE);

      assertEquals(1, StuckFixtureQuery.entered.get(),
                   "a second extract must not enter it again");
   }

   /**
    * The bound {@link TabularSchemaExtractor#poison} claims -- one stranded thread per broken setter
    * for the life of the process -- has to hold WITHIN one extract as well as across calls.
    *
    * <p>The axis that hangs here is not reachable in the first pass at all: its panel is hidden until
    * an outer value opens it, so the write is skipped and the setter is never entered. It is reached
    * in the pair pass instead, as the inner half of a pair -- and it is reachable that way TWICE,
    * once under each outer axis. Filtering the axis list on entry cannot help: the poison is recorded
    * after that filter has already run.
    */
   @Test
   void anAxisPoisonedInTheFirstPassIsNotProbedAgainAsAnInnerAxis() {
      TabularSchemaExtractor.setProbeTimeout(150);
      PairStuckFixtureQuery.entered.set(0);

      TabularQuerySchema schema = new TabularSchemaExtractor()
         .extract(new PairStuckFixtureQuery(), PairStuckFixtureQuery.TYPE);

      assertEquals(1, PairStuckFixtureQuery.entered.get(),
                   "the hanging setter is entered once across the whole extract, not once per " +
                   "outer axis that reveals it");
      assertNotNull(schema.getDependencyMatrix().get("outerA"),
                    "the axes that do return are still described");
      assertNotNull(schema.getDependencyMatrix().get("outerB"));
   }

   private TabularQuerySchema extract() {
      return new TabularSchemaExtractor().extract(new FixtureQuery(), FixtureQuery.TYPE);
   }

   private static ConfigurationContext previous;

   public enum Mode { NONE, CURSOR, LINK }

   public enum LinkStyle { BODY, HEADER }

   public enum Scope { ALL, NAMED }

   public enum Level { NONE, DETAIL }

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

   /**
    * One axis that can be set and one that cannot: {@code offScreen} sits in a panel whose
    * visibility is computed from a list only a button grows, which is the shape a REST connector's
    * lookup fields have.
    */
   @View(vertical = true, value = {
      @View1(value = "mode"),
      @View1(value = "detail", visibleMethod = "isDetailMode"),
      @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isRowsAdded", elements = {
         @View2("offScreen"),
      }),
   })
   public static class OffScreenFixtureQuery extends TabularQuery {
      public OffScreenFixtureQuery() {
         super(TYPE);
      }

      @Property(label = "Mode")
      @PropertyEditor(tags = { "NONE", "DETAIL" })
      public Level getMode() {
         return mode;
      }

      public void setMode(Level mode) {
         this.mode = mode;
      }

      @Property(label = "Detail")
      public String getDetail() {
         return detail;
      }

      public void setDetail(String detail) {
         this.detail = detail;
      }

      @Property(label = "Off Screen")
      public boolean isOffScreen() {
         return offScreen;
      }

      public void setOffScreen(boolean offScreen) {
         writes.incrementAndGet();
         this.offScreen = offScreen;
      }

      public boolean isDetailMode() {
         return mode == Level.DETAIL;
      }

      /** Nothing a probe can set turns this on -- only the button that grows the list. */
      public boolean isRowsAdded() {
         return !rows.isEmpty();
      }

      static final String TYPE = "Test.OffScreen";
      static final AtomicInteger writes = new AtomicInteger();

      private Level mode = Level.NONE;
      private String detail;
      private boolean offScreen;
      private final List<String> rows = new ArrayList<>();
   }

   /**
    * A connector whose setter does not return. It sleeps rather than spins so the abandoned probe
    * releases its thread when cancelled -- a real one has no such courtesy, which is the whole
    * reason the deadline is paired with a record of what has already hung.
    */
   @View(vertical = true, value = {
      @View1(value = "mode"),
      @View1(value = "detail", visibleMethod = "isDetailMode"),
      @View1(value = "stuck"),
   })
   public static class StuckFixtureQuery extends TabularQuery {
      public StuckFixtureQuery() {
         super(TYPE);
      }

      @Property(label = "Mode")
      @PropertyEditor(tags = { "NONE", "DETAIL" })
      public Level getMode() {
         return mode;
      }

      public void setMode(Level mode) {
         this.mode = mode;
      }

      @Property(label = "Detail")
      public String getDetail() {
         return detail;
      }

      public void setDetail(String detail) {
         this.detail = detail;
      }

      @Property(label = "Stuck")
      public boolean isStuck() {
         return stuck;
      }

      public void setStuck(boolean stuck) {
         if(stuck) {
            entered.incrementAndGet();

            try {
               Thread.sleep(60000);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         this.stuck = stuck;
      }

      public boolean isDetailMode() {
         return mode == Level.DETAIL;
      }

      static final String TYPE = "Test.Stuck";
      static final AtomicInteger entered = new AtomicInteger();

      private Level mode = Level.NONE;
      private String detail;
      private boolean stuck;
   }

   /**
    * A connector whose hanging setter is not reachable on a blank query: {@code stuck} sits in a
    * panel that opens once EITHER outer axis is set, so the pair pass reaches it twice. {@code deep}
    * is gated on two values at once and so is never reached in the first pass, which is what makes
    * the pair pass run at all.
    */
   @View(vertical = true, value = {
      @View1(value = "outerA"),
      @View1(value = "outerB"),
      @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isEitherOuter", elements = {
         @View2("stuck"),
      }),
      @View1(value = "deep", visibleMethod = "isOuterAAndStuck"),
   })
   public static class PairStuckFixtureQuery extends TabularQuery {
      public PairStuckFixtureQuery() {
         super(TYPE);
      }

      @Property(label = "Outer A")
      public boolean isOuterA() {
         return outerA;
      }

      public void setOuterA(boolean outerA) {
         this.outerA = outerA;
      }

      @Property(label = "Outer B")
      public boolean isOuterB() {
         return outerB;
      }

      public void setOuterB(boolean outerB) {
         this.outerB = outerB;
      }

      @Property(label = "Stuck")
      public boolean isStuck() {
         return stuck;
      }

      public void setStuck(boolean stuck) {
         if(stuck) {
            entered.incrementAndGet();

            try {
               Thread.sleep(60000);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         this.stuck = stuck;
      }

      @Property(label = "Deep")
      public String getDeep() {
         return deep;
      }

      public void setDeep(String deep) {
         this.deep = deep;
      }

      public boolean isEitherOuter() {
         return outerA || outerB;
      }

      public boolean isOuterAAndStuck() {
         return outerA && stuck;
      }

      static final String TYPE = "Test.PairStuck";
      static final AtomicInteger entered = new AtomicInteger();

      private boolean outerA;
      private boolean outerB;
      private boolean stuck;
      private String deep;
   }
}
