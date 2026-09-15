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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A column the source does not have used to bind cleanly. The shelf stored it, the call reported
 * success, and the crosstab rendered with no rows at all — which reads as "the data is empty"
 * rather than "that column does not exist". Found live on local-1200 by binding a column from a
 * different worksheet than the one the assembly pointed at, and confirmed with a nonsense name.
 */
@Tag("core")
class BindableColumnsTest {
   /** No table marked current — the shape an unscoped listing produces. */
   private static final List<BindableTable> TABLES = List.of(
      new BindableTable("Query1", null, List.of(new BindableField("PRICE", null, null),
                                                new BindableField("QUANTITY", null, null))),
      new BindableTable("Products", null,
                        List.of(new BindableField("PRODUCT_NAME", null, null))));

   /** The same tables, with the assembly known to be bound to Query1. */
   private static final List<BindableTable> BOUND_TO_QUERY1 = List.of(
      new BindableTable("Query1", true, List.of(new BindableField("PRICE", null, null),
                                                new BindableField("QUANTITY", null, null))),
      new BindableTable("Products", false,
                        List.of(new BindableField("PRODUCT_NAME", null, null))));

   /**
    * A scoped listing for an assembly with no source yet — every table marked not-current.
    *
    * <p>{@code QUANTITY} deliberately appears in two of them: a worksheet that holds a join
    * alongside its base tables duplicates nearly every column, which is the shape that makes
    * inference ambiguous in practice rather than in theory.
    */
   private static final List<BindableTable> NO_SOURCE = List.of(
      new BindableTable("Query1", false, List.of(new BindableField("PRICE", null, null),
                                                 new BindableField("QUANTITY", null, null))),
      new BindableTable("Details", false, List.of(new BindableField("QUANTITY", null, null))),
      new BindableTable("Products", false,
                        List.of(new BindableField("PRODUCT_NAME", null, null))));

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   @Test
   void refusesAColumnThatAppearsInNoBindableTable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("NO_SUCH_COLUMN_XYZ")));

      assertTrue(thrown.getMessage().contains("NO_SUCH_COLUMN_XYZ"));
      assertTrue(thrown.getMessage().contains("PRICE"), "the message must list what is available");
   }

   /**
    * With no table marked current, a column only has to exist in one of them.
    *
    * <p>This stays the right answer for the case it was written for — an assembly whose source is
    * not known — and the leniency is load-bearing there: refusing on the strength of a guess would
    * block legitimate columns. What changed is that the source often *is* knowable; see below.
    */
   @Test
   void acceptsAColumnFromAnyBindableTableWhenNoneIsMarkedCurrent() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("PRODUCT_NAME")));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("PRICE")));
   }

   /**
    * Once the live table is known, a column from a different one is refused.
    *
    * <p>An assembly binds fields from exactly one source, and the Composer enforces it by
    * <em>deleting</em> bound fields absent from a newly chosen source
    * ({@code VSAssemblyInfoHandler.validateChartColumns}). The agent write path never runs that
    * validation, so a column from a second table used to land as a ref resolving to nothing —
    * indistinguishable from empty data, which is the same failure this class was built to stop, one
    * step further in.
    *
    * <p>The name-exists-somewhere check could not catch it: {@code PRODUCT_NAME} is a real column
    * of a real table, just not of the one this assembly is bound to. Narrowing to the current table
    * is what makes the difference, and it is only possible now that the listing says which that is.
    */
   @Test
   void refusesAColumnFromATableTheAssemblyIsNotBoundTo() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(BOUND_TO_QUERY1, "Chart1", dim("PRODUCT_NAME")));

      assertTrue(thrown.getMessage().contains("PRODUCT_NAME"));
      assertTrue(thrown.getMessage().contains("Query1"),
                 "the message must name the source the assembly is actually bound to");
   }

   @Test
   void acceptsAColumnFromTheTableTheAssemblyIsBoundTo() {
      assertDoesNotThrow(() -> BindableColumns.require(BOUND_TO_QUERY1, "Chart1", dim("QUANTITY")));
      assertDoesNotThrow(() -> BindableColumns.require(BOUND_TO_QUERY1, "Chart1", dim("price")),
                         "narrowing must not cost the case-insensitive match");
   }

   @Test
   void isCaseInsensitiveRatherThanRejectingAKnownColumnOnItsSpelling() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("price")));
   }

   // ── which table the fields come from ──────────────────────────────────────
   //
   // The Composer sets an assembly's source as a side effect of the drag: the drag event carries
   // event.getTable(), so dropping a column says both "bind this" and "from here" at once. The agent
   // path lost the second half — list_bindable_fields groups columns BY TABLE, so the caller always
   // knew which table it picked from, and the write vocabulary had nowhere to say it. So the source
   // was never established, and a chart with a perfectly correct binding rendered nothing.
   //
   // requireSource is that missing half: it decides which table a write is against, and returns the
   // one to establish (null when the assembly already has a source, or when nothing can be told).

   /** Only a name is needed here; the listing is what these tests vary. */
   private static FieldRef field(String column) {
      return dim(column);
   }

   @Test
   void establishesTheStatedTableWhenTheAssemblyHasNoSource() {
      assertEquals("Query1",
                   BindableColumns.requireSource(NO_SOURCE, "Chart1", "Query1",
                                                 List.of(field("PRICE"))));
   }

   /** Case is normalized to how the listing spells it, not stored as typed. */
   @Test
   void establishesTheStatedTableInItsCanonicalSpelling() {
      assertEquals("Query1",
                   BindableColumns.requireSource(NO_SOURCE, "Chart1", "query1",
                                                 List.of(field("PRICE"))));
   }

   /**
    * With no table stated and the column in exactly one, there is nothing to guess — infer it.
    *
    * <p>This is the forgiving half of the rule the plugin is held to: unambiguous intent should not
    * cost an extra call. A single-table worksheet — the common shape — always lands here.
    */
   @Test
   void infersTheTableWhenOnlyOneHasEveryColumn() {
      assertEquals("Products",
                   BindableColumns.requireSource(NO_SOURCE, "Chart1", null,
                                                 List.of(field("PRODUCT_NAME"))));
   }

   /**
    * And the loud half: when several tables have the column, refuse and ask.
    *
    * <p>Picking one would be a coin flip that renders something plausible, which is worse than
    * refusing. This is not a rare shape — a worksheet holding a join alongside its base tables
    * duplicates nearly every column, so most names are ambiguous there.
    */
   @Test
   void refusesToGuessWhenSeveralTablesHaveTheColumn() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Chart1", null,
                                             List.of(field("QUANTITY"))));

      assertTrue(thrown.getMessage().contains("QUANTITY"));
      assertTrue(thrown.getMessage().contains("Query1"), "name the candidates");
      assertTrue(thrown.getMessage().contains("Details"), "name the candidates");
      assertTrue(thrown.getMessage().contains("table"), "say how to resolve it");
   }

   /** All fields in one write share one table, so the inference is over their intersection. */
   @Test
   void infersFromTheIntersectionOfEveryFieldNotTheFirst() {
      assertEquals("Query1",
                   BindableColumns.requireSource(NO_SOURCE, "Chart1", null,
                                                 List.of(field("PRICE"), field("QUANTITY"))),
                   "only Query1 has both; QUANTITY alone would have been ambiguous");
   }

   @Test
   void refusesWhenNoSingleTableHasAllTheFields() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Chart1", null,
                                             List.of(field("PRICE"), field("PRODUCT_NAME"))));

      assertTrue(thrown.getMessage().contains("one source"),
                 "explain the constraint, not just the failure");
   }

   @Test
   void refusesAStatedTableThatDoesNotHaveOneOfTheColumns() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Chart1", "Products",
                                             List.of(field("PRICE"))));

      assertTrue(thrown.getMessage().contains("PRICE"));
      assertTrue(thrown.getMessage().contains("Products"));
   }

   @Test
   void refusesAStatedTableThatIsNotListedAtAll() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Chart1", "NOPE",
                                            List.of(field("PRICE"))));

      assertTrue(thrown.getMessage().contains("NOPE"));
      assertTrue(thrown.getMessage().contains("Query1"), "list what it can bind to");
   }

   /** Already sourced: nothing to establish, and the column check stays narrowed to it. */
   @Test
   void establishesNothingWhenTheAssemblyAlreadyHasASource() {
      assertNull(BindableColumns.requireSource(BOUND_TO_QUERY1, "Chart1", null,
                                              List.of(field("QUANTITY"))));
      assertNull(BindableColumns.requireSource(BOUND_TO_QUERY1, "Chart1", "Query1",
                                              List.of(field("QUANTITY"))),
                 "restating the source it already has is not a repoint");
   }

   /**
    * Naming a different table for an already-bound chart is the one case that must not be resolved
    * silently either way.
    *
    * <p>Repointing deletes every field already bound — the Composer's own
    * {@code validateChartColumns} does exactly that — so doing it because a column looked like it
    * came from elsewhere would destroy work nobody asked to lose. Binding the column into the
    * current source instead is equally wrong: it is not there. Both readings are plausible, which
    * is precisely when a tool has to ask.
    */
   @Test
   void refusesToRepointAnAlreadyBoundAssemblyImplicitly() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(BOUND_TO_QUERY1, "Chart1", "Products",
                                             List.of(field("PRODUCT_NAME"))));

      assertTrue(thrown.getMessage().contains("Query1"), "name the source it has");
      assertTrue(thrown.getMessage().contains("Products"), "name the source that was asked for");
      assertTrue(thrown.getMessage().contains("set_chart_source"), "name the way through");
   }

   /**
    * An empty {@code tables} reaching {@code requireSource} does NOT mean "the tree could not be
    * read" (bug #76350, PCB-002). A genuine read failure is already caught one layer up, in
    * {@code BindingAgentController.resolveSourceTable}'s own try/catch, before this method ever
    * runs — so by the time it sees an empty list, the listing succeeded and genuinely found
    * nothing bindable (e.g. a table that was never saved). That case must refuse exactly the way
    * set_chart_source already does, not silently accept the bind and crash later on render.
    */
   @Test
   void refusesWhenNothingIsListedAndNoTableIsNamed() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(List.of(), "Chart1", null,
                                             List.of(field("ANYTHING"))));
      assertTrue(thrown.getMessage().contains("ANYTHING"));

      thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(null, "Chart1", null, List.of(field("ANYTHING"))));
      assertTrue(thrown.getMessage().contains("ANYTHING"));
   }

   /** The same case, with a table named — refuses the same way set_chart_source already does. */
   @Test
   void refusesAStatedTableWhenNothingIsListed() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(List.of(), "Chart1", "Sales",
                                             List.of(field("ANYTHING"))));

      assertTrue(thrown.getMessage().contains("Sales"));
      assertTrue(thrown.getMessage().contains("Available"));
   }

   /** Nothing being written means nothing to decide a source from. */
   @Test
   void establishesNothingWhenClearingAShelf() {
      assertNull(BindableColumns.requireSource(NO_SOURCE, "Chart1", null, List.of()));
   }

   /**
    * An empty {@code tables} reaching {@code require} does NOT mean "the tree could not be read"
    * (bug #76350, PCB-002's own reasoning for {@code requireSource}, which applies identically
    * here — see {@code requireBindableColumns}/{@code resolveSourceTable}'s shared try/catch). A
    * genuine read failure is already caught one layer up, before this method ever runs — so by the
    * time it sees an empty list, the listing succeeded and genuinely found nothing bindable. That
    * case must refuse, not silently accept the bind and crash later on render.
    */
   @Test
   void refusesWhenNothingCouldBeListed() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(List.of(), "Crosstab1", dim("ANYTHING")));
      assertTrue(thrown.getMessage().contains("ANYTHING"));

      thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(null, "Crosstab1", dim("ANYTHING")));
      assertTrue(thrown.getMessage().contains("ANYTHING"));
   }

   /**
    * Worse than an empty listing overall: the assembly's current table is present but its own
    * column list came back empty, while a different, non-current table in the same listing still
    * has columns. The old bypass fired on {@code available} being empty regardless of why, so it
    * waved through a column belonging to that other table — a cross-table silent bind, not just a
    * "nothing could be listed" false accept.
    */
   @Test
   void refusesAColumnFromAnotherTableWhenTheCurrentTablesOwnListingIsEmpty() {
      List<BindableTable> currentTableWithNoColumns = List.of(
         new BindableTable("Query1", true, List.of()),
         new BindableTable("Products", false,
                           List.of(new BindableField("PRODUCT_NAME", null, null))));

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(currentTableWithNoColumns, "Chart1", dim("PRODUCT_NAME")));

      assertTrue(thrown.getMessage().contains("PRODUCT_NAME"));
      assertTrue(thrown.getMessage().contains("Query1"),
                 "the message must name the source the assembly is actually bound to");
   }

   @Test
   void ignoresAnAbsentOrBlankColumn() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim(null)));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("  ")));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", List.of()));
   }

   @Test
   void checksEveryFieldNotJustTheFirst() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("PRICE"), dim("MADE_UP")));

      assertTrue(thrown.getMessage().contains("MADE_UP"));
   }

   // ── "$(ComponentName)" dynamic references (#76641) ─────────────────────────
   //
   // A shelf field's column can be a dynamic reference to a form component instead of a real
   // column name -- e.g. set_chart_shelf(Chart1, y, "$(RadioButton2)"). It is resolved against a
   // real value only at render time (VSUtil.isVariableValue/DynamicValue), so checking it here
   // against the source's schema refused a legitimate binding as if it were a typo'd or
   // wrong-table column. This must not weaken the existence check for anything that ISN'T
   // "$(...)"-shaped: a genuine typo or wrong-table column must still refuse exactly as before.

   @Test
   void acceptsADynamicReferenceRegardlessOfWhetherItIsAnAvailableColumn() {
      assertDoesNotThrow(
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("$(RadioButton2)")));
      assertDoesNotThrow(
         () -> BindableColumns.require(BOUND_TO_QUERY1, "Chart1", dim("$(RadioButton2)")),
         "must bypass even when a table is marked current and narrows the check");
   }

   @Test
   void acceptsADynamicReferenceEvenWhenNothingIsListedAtAll() {
      assertDoesNotThrow(
         () -> BindableColumns.require(List.of(), "Crosstab1", dim("$(RadioButton2)")));
      assertDoesNotThrow(
         () -> BindableColumns.require(null, "Crosstab1", dim("$(RadioButton2)")));
   }

   @Test
   void stillRefusesANonExistentLiteralColumnAlongsideADynamicReference() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("$(RadioButton2)"),
                                       dim("NO_SUCH_COLUMN_XYZ")));

      assertTrue(thrown.getMessage().contains("NO_SUCH_COLUMN_XYZ"),
                 "the dynamic reference must not blanket-bypass every other field in the same call");
   }

   @Test
   void stillRefusesAColumnFromATableTheAssemblyIsNotBoundToAlongsideADynamicReference() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(BOUND_TO_QUERY1, "Chart1", dim("$(RadioButton2)"),
                                       dim("PRODUCT_NAME")));

      assertTrue(thrown.getMessage().contains("PRODUCT_NAME"));
      assertTrue(thrown.getMessage().contains("Query1"));
   }

   @Test
   void doesNotTreatAPlainStringStartingWithDollarAsADynamicReference() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("$PRICE")));

      assertTrue(thrown.getMessage().contains("$PRICE"),
                 "only the \"$(...)\" shape is a dynamic reference, not any string starting with $");
   }

   // ── "$(ComponentName)" dynamic references when establishing a NEW source (#76641) ─────────
   //
   // require()'s bypass above only helps once an assembly already has a source: it narrows the
   // check to the current table, and a dynamic reference skips that narrowed check. A brand-new
   // assembly with nothing bound yet never reaches require() at all -- it goes through
   // requireSource(), which decides the table itself via hasAll (inference) or requireHasAll
   // (an explicitly named table), and neither of those knew about the same exception. So
   // set_table_fields on a fresh Crosstab with a "$(ComboBox1)" field failed exactly the way
   // set_chart_shelf used to before require() was fixed, just one call path over.

   @Test
   void establishesAnExplicitlyNamedTableWhenAFieldIsADynamicReference() {
      assertEquals("Query1",
                   BindableColumns.requireSource(NO_SOURCE, "Crosstab1", "Query1",
                                                 List.of(field("PRICE"), dim("$(ComboBox1)"))),
                   "the dynamic field must not be checked against Query1's columns");
   }

   @Test
   void infersTheTableWhenAFieldIsADynamicReference() {
      assertEquals("Products",
                   BindableColumns.requireSource(NO_SOURCE, "Crosstab1", null,
                                                 List.of(field("PRODUCT_NAME"), dim("$(ComboBox1)"))),
                   "the dynamic field carries no table information, so the literal field alone " +
                   "must decide the match");
   }

   /**
    * A dynamic reference must not turn into a wildcard that makes every table "match": with no
    * literal field to decide, {@code hasAll} trivially passes for every candidate, so more than one
    * table still qualifies and the ambiguity refusal fires — it just does not, by itself, prove the
    * bypass is wired in (a real regression could look identical). The literal-plus-dynamic tests
    * above are what actually exercise the fix; this one only confirms the bypass has not gone so far
    * as to manufacture a single winner out of nothing.
    */
   @Test
   void aSoloDynamicReferenceDoesNotSilentlyPickATable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Crosstab1", null,
                                             List.of(dim("$(ComboBox1)"))));

      assertTrue(thrown.getMessage().contains("more than one table"));
   }

   @Test
   void stillRefusesAGenuinelyWrongLiteralColumnWhenEstablishingANewSourceWithAnExplicitTable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Crosstab1", "Products",
                                             List.of(dim("$(ComboBox1)"), field("PRICE"))));

      assertTrue(thrown.getMessage().contains("PRICE"),
                 "the dynamic field ahead of it must not swallow the real refusal");
      assertTrue(thrown.getMessage().contains("Products"));
   }

   @Test
   void stillRefusesWhenNoSingleTableHasTheLiteralFieldsAlongsideADynamicReference() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.requireSource(NO_SOURCE, "Crosstab1", null,
                                             List.of(dim("$(ComboBox1)"), field("PRICE"),
                                                    field("PRODUCT_NAME"))));

      assertTrue(thrown.getMessage().contains("one source"),
                 "PRICE and PRODUCT_NAME still do not share a table; the dynamic field must not " +
                 "manufacture a match that is not there");
   }
}
