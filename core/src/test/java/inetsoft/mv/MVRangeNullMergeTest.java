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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78246: the range of a numeric MV column must survive a slice (dispatcher or block)
 * in which the column has only null values.
 * <ul>
 *    <li>{@code MVDispatcher.mergeMV} folds each dispatcher's MV into the MV file a sibling
 *    saved earlier, which holds a snapshot of the shared def's range that may still be
 *    {@code null..null}. {@code getMin} used to return null when either side was null, so the
 *    merged range was {@code null..max}, which {@code MVColumn.write} persists as no range.</li>
 *    <li>{@code MVScriptable} ({@code MV.<col>.Max/Min}) falls back to the block ranges when
 *    the def range is null. The max fallback kept the smaller value, and both fallbacks let
 *    an all-null block replace a known value.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVRangeNullMergeTest {
   /**
    * Parallel build sequence: the dispatcher whose slice is all null saves first, a sibling
    * with values merges into that stored MV afterwards.
    */
   @Test
   void mergeIntoStoredMVWithoutRangeKeepsTheRange(@TempDir Path dir) throws Exception {
      MVDef def = newDef();
      Set<MVColumn> resetColumns =
         Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

      MV stored = build(newTable(null, null, null), def, resetColumns);
      // the stored MV file holds a snapshot of the shared def taken when it was saved
      stored.setDef(roundTrip(def, dir.resolve("stored.def")));
      assertNull(measure(stored.getDef()).getOriginalMin());
      assertNull(measure(stored.getDef()).getOriginalMax());

      MV mv = build(newTable(1.0, 5.0, 10.0), def, resetColumns);
      MV merged = MVDispatcher.mergeMV(mv, stored);

      assertRange(measure(merged.getDef()), 1, 10);
      // MVColumn.write drops a half-null range, so check what the saved MV file would hold
      assertRange(measure(roundTrip(merged.getDef(), dir.resolve("merged.def"))), 1, 10);
   }

   /**
    * The other argument order: a new MV without a range must not wipe the stored range.
    */
   @Test
   void mergeOfMVWithoutRangeKeepsTheStoredRange() {
      MV stored = build(newTable(1.0, 5.0, 10.0), newDef(), null);
      MV mv = build(newTable(null, null, null), newDef(), null);
      MV merged = MVDispatcher.mergeMV(mv, stored);

      assertRange(measure(merged.getDef()), 1, 10);
   }

   /**
    * With no range in the def, MV.col.Max/Min come from the block ranges: the largest block
    * max and the smallest block min.
    */
   @Test
   void scriptableFallsBackToTheBlockRanges() {
      assertScriptableRange(new Double[][] {
         { 1.0, 5.0, 10.0 }, { 50.0, 55.0, 60.0 }
      });
   }

   /**
    * An all-null block, first, in the middle or last, must not replace a known block value.
    */
   @Test
   void scriptableFallbackIgnoresAllNullBlocks() {
      assertScriptableRange(new Double[][] {
         { null, null, null }, { 1.0, 5.0, 10.0 }, { null, null, null },
         { 50.0, 55.0, 60.0 }, { null, null, null }
      });
   }

   private static void assertScriptableRange(Double[][] slices) {
      MV merged = null;

      for(Double[] slice : slices) {
         merged = MVDispatcher.mergeMV(build(newTable(slice), newDef(), null), merged);
      }

      assertEquals(slices.length, merged.getBlockSize());
      MVDef def = merged.getDef();
      MVColumn col = measure(def);
      // the range persisted in the MV file was lost (e.g. by the getMin bug above)
      col.setRange(null, null);
      MVScriptable scriptable = new MVScriptable(def, col, merged);

      assertEquals(60.0, ((Number) scriptable.getMember("MaxValue")).doubleValue());
      assertEquals(1.0, ((Number) scriptable.getMember("MinValue")).doubleValue());
   }

   private static MV build(DefaultTableLens table, MVDef def, Set<MVColumn> resetColumns) {
      MVBuilder builder = new MVBuilder(table, def, false, null, resetColumns);
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         it.next().dispose();
      }

      return builder.getMV();
   }

   private static MVDef roundTrip(MVDef def, Path file) throws Exception {
      try(FileChannel out = FileChannel.open(file, StandardOpenOption.CREATE,
                                             StandardOpenOption.WRITE))
      {
         def.write(out);
      }

      MVDef copy = new MVDef();

      try(FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
         copy.read(in);
      }

      return copy;
   }

   private static void assertRange(MVColumn col, double min, double max) {
      assertNotNull(col.getOriginalMin(), "min");
      assertNotNull(col.getOriginalMax(), "max");
      assertEquals(min, col.getOriginalMin().doubleValue(), "min");
      assertEquals(max, col.getOriginalMax().doubleValue(), "max");
   }

   private static DefaultTableLens newTable(Double... values) {
      return new DefaultTableLens(new Object[][] {
         { "dim", "num" },
         { "a", values[0] },
         { "b", values[1] },
         { "c", values[2] }
      });
   }

   private static MVColumn measure(MVDef def) {
      return def.getColumns().get(1);
   }

   private static MVDef newDef() {
      ColumnRef dimRef = new ColumnRef(new AttributeRef(null, "dim"));
      dimRef.setDataType(XSchema.STRING);
      ColumnRef numRef = new ColumnRef(new AttributeRef(null, "num"));
      numRef.setDataType(XSchema.DOUBLE);

      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(dimRef, true));
      columns.add(new MVColumn(numRef, false));

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }
}
