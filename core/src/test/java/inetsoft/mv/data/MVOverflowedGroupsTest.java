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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link MV#getOverflowedGroups}, which reports the group columns whose dimension
 * dictionary overflowed {@code mv.dim.max.size}. Grouping on such a column silently returns
 * incomplete results, because {@code XDimDictionary.indexOf} then returns the row position
 * rather than a value index -- so every row becomes its own group.
 *
 * <p>This replaced the never-called {@code MV.checkValidity(GroupRef[])}, which additionally
 * dereferenced {@code palette[idx]} before bounds-checking {@code idx}, so it would have thrown
 * {@link ArrayIndexOutOfBoundsException} on any group column absent from the MV headers.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVOverflowedGroupsTest {
   @Test
   void reportsOverflowedDimensionColumn() {
      MV mv = createMV();
      overflow(mv, 0);

      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")));
   }

   @Test
   void ignoresColumnThatDidNotOverflow() {
      MV mv = createMV();
      // "State" has a dictionary, but it is within mv.dim.max.size
      mv.getDictionaryIndex(1, new XDimDictionary());

      assertTrue(mv.getOverflowedGroups(groups("State")).isEmpty());
   }

   /**
    * A column overflowed across several blocks holds one overflowed dictionary per block in its
    * palette. The operator should be told about the column once, not once per block.
    */
   @Test
   void reportsColumnOnceEvenWhenSeveralBlocksOverflowed() {
      MV mv = createMV();
      // distinct state per dictionary: XDimDictionary.equals compares overflow,
      // size, hashCode, cnull and cls, so identical dicts would be deduped by
      // DictionaryPalette and the test would not exercise the per-block loop.
      // the ascending palette indexes prove three separate dictionaries were stored,
      // so the per-block loop really is exercised
      assertEquals(0, overflow(mv, 0, "a").getIndex());
      assertEquals(1, overflow(mv, 0, "b").getIndex());
      assertEquals(2, overflow(mv, 0, "c").getIndex());

      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")));
   }

   @Test
   void reportsEveryOverflowedColumn() {
      MV mv = createMV();
      overflow(mv, 0);
      overflow(mv, 1);

      assertEquals(List.of("City", "State"), mv.getOverflowedGroups(groups("City", "State")));
   }

   /**
    * Regression guard for the bounds-check ordering bug in the method this replaced: a group on a
    * column that is not an MV header makes {@code indexOfHeader} return -1, which used to index
    * the palette array before the guard ran.
    */
   @Test
   void skipsGroupColumnMissingFromTheMV() {
      MV mv = createMV();
      overflow(mv, 0);

      assertDoesNotThrow(() -> mv.getOverflowedGroups(groups("NotAnMVColumn")));
      assertTrue(mv.getOverflowedGroups(groups("NotAnMVColumn")).isEmpty());
   }

   /**
    * The per-column result is memoized so that repeated queries do not re-read every block's
    * dictionary off storage. That memo must not outlive a change to the palette.
    */
   @Test
   void memoizedResultIsInvalidatedWhenThePaletteChanges() {
      MV mv = createMV();

      // prime the memo with a negative answer
      assertTrue(mv.getOverflowedGroups(groups("City")).isEmpty());

      overflow(mv, 0, "a");

      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")),
                   "a dictionary added after the first check must still be seen");
   }

   /** Repeated checks are stable, and the memo returns the same answer. */
   @Test
   void repeatedChecksAreStable() {
      MV mv = createMV();
      overflow(mv, 0);

      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")));
      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")));
      assertEquals(List.of("City"), mv.getOverflowedGroups(groups("City")));
   }

   @Test
   void returnsEmptyForNoGroups() {
      assertTrue(createMV().getOverflowedGroups(new GroupRef[0]).isEmpty());
   }

   /**
    * An in-memory MV with two dimensions ("City", "State") and one measure ("Total").
    * {@code contentPos} defaults to -1, so {@code loadContent()} is a no-op here.
    */
   private static MV createMV() {
      return new MV(2, 1, new String[]{ "City", "State", "Total" }, new String[3],
                    new Class[3], null, null);
   }

   /** Add an overflowed dictionary to the given column's palette. */
   private static void overflow(MV mv, int column) {
      overflow(mv, column, null);
   }

   /**
    * Add an overflowed dictionary carrying {@code value}, which distinguishes it from other
    * overflowed dictionaries -- an overflown dict folds each added value into its hashCode.
    */
   private static XDimDictionaryIndex overflow(MV mv, int column, Object value) {
      XDimDictionary dict = new XDimDictionary();
      dict.setOverflow(true);

      if(value != null) {
         dict.addValue(value);
      }

      return mv.getDictionaryIndex(column, dict);
   }

   private static GroupRef[] groups(String... names) {
      GroupRef[] groups = new GroupRef[names.length];

      for(int i = 0; i < names.length; i++) {
         groups[i] = new GroupRef(new ColumnRef(new AttributeRef(null, names[i])));
      }

      return groups;
   }
}
