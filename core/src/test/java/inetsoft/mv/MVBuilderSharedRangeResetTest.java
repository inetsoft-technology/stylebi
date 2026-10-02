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
import inetsoft.mv.data.MVBuilder;
import inetsoft.mv.data.SubMV;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Bug #77154 (review finding F1).
 * <p>
 * The dispatchers of a parallel MV build share one {@code MVDef} and its column instances. Every
 * {@code MVBuilder} clears the range of its columns before it accumulates into them, and it used
 * to do so unconditionally, so a builder created after a sibling builder had already accumulated
 * the numeric range of a shared measure (through {@code MVCreatorUtil.resetNumRange()}) wiped
 * that range, and the MV file ended up with a too narrow {@code getOriginalMin()/Max()}. The
 * builders of a parallel build now share a set of already reset columns, so each shared column is
 * reset only once per build. A builder without that set (single dispatcher, incremental update)
 * still always resets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderSharedRangeResetTest {
   @Test
   void laterBuilderKeepsTheRangeASiblingBuilderAccumulated() {
      MVDef def = newDef();
      MVColumn measure = measureColumn(def);
      // stale range from a previous build, the first builder must still clear it
      measure.setRange(-1000, 1000);
      Set<MVColumn> resetColumns =
         Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

      MVBuilder first = new MVBuilder(newTable(1, 10), def, false, null, resetColumns);
      buildAll(first);
      assertRange(measure, 1, 10);

      MVBuilder second = new MVBuilder(newTable(50, 60), def, false, null, resetColumns);
      // the second builder must not wipe the range the first one accumulated
      assertRange(measure, 1, 10);

      buildAll(second);
      assertRange(measure, 1, 60);
   }

   @Test
   void builderWithoutASharedSetAlwaysResets() {
      MVDef def = newDef();
      MVColumn measure = measureColumn(def);

      buildAll(new MVBuilder(newTable(1, 10), def, false));
      assertRange(measure, 1, 10);

      // single dispatcher / incremental update semantics: a new builder starts from no range
      MVBuilder second = new MVBuilder(newTable(50, 60), def, false, null, null);
      assertNull(measure.getOriginalMin());
      assertNull(measure.getOriginalMax());

      buildAll(second);
      assertRange(measure, 50, 60);
   }

   private static void buildAll(MVBuilder builder) {
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         it.next().dispose();
      }
   }

   private static void assertRange(MVColumn col, double min, double max) {
      assertNotNull(col.getOriginalMin(), "min");
      assertNotNull(col.getOriginalMax(), "max");
      assertEquals(min, col.getOriginalMin().doubleValue(), "min");
      assertEquals(max, col.getOriginalMax().doubleValue(), "max");
   }

   private static DefaultTableLens newTable(double lo, double hi) {
      return new DefaultTableLens(new Object[][] {
         { "dim", "num" },
         { "a", lo },
         { "b", (lo + hi) / 2 },
         { "c", hi }
      });
   }

   private static MVColumn measureColumn(MVDef def) {
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
