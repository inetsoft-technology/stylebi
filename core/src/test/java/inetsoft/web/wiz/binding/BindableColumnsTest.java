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

   /** No listing means the tree could not be read — a read failure, not a write one. */
   @Test
   void establishesNothingWhenNothingCouldBeListed() {
      assertNull(BindableColumns.requireSource(List.of(), "Chart1", null,
                                              List.of(field("ANYTHING"))));
      assertNull(BindableColumns.requireSource(null, "Chart1", null, List.of(field("ANYTHING"))));
   }

   /** Nothing being written means nothing to decide a source from. */
   @Test
   void establishesNothingWhenClearingAShelf() {
      assertNull(BindableColumns.requireSource(NO_SOURCE, "Chart1", null, List.of()));
   }

   /**
    * An empty listing means the binding tree could not be read — a different failure. Refusing
    * every column on the strength of it would turn a read problem into a write problem.
    */
   @Test
   void staysOutOfTheWayWhenNothingCouldBeListed() {
      assertDoesNotThrow(() -> BindableColumns.require(List.of(), "Crosstab1", dim("ANYTHING")));
      assertDoesNotThrow(() -> BindableColumns.require(null, "Crosstab1", dim("ANYTHING")));
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
}
