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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MV.MaxValue and MV.MinValue ({@link MVScriptable}) of a dimension column, whose values are in
 * a dictionary per block: the value is the largest / smallest over all the blocks, not of the
 * first or last block. An incremental update puts these values into its append conditions
 * (e.g. {@code region > MV.MaxValue}), so the wrong block gives a wrong update.
 * <p>
 * Ported from the 13.8 {@code QueryAdvancedFeatureTest.mvUpdateVariablesInSql} (two rows per
 * block, the largest value in a middle block). The merge of the append conditions and
 * MV.LastUpdateTime are covered by {@link MVIncrementalTest}; the cached original max/min and the
 * block statistics of a column without a dictionary by {@code MVScriptableTest} and
 * {@link MVRangeNullMergeTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVScriptableMultiBlockTest {
   private String oldPreferred;

   @BeforeEach
   void setUp() {
      oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      // two rows per block
      SreeEnv.setProperty("mv.preferred.block", "2");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.preferred.block", oldPreferred);
   }

   // a string dimension: the smallest value is in the second block, the largest in the third of
   // four, so neither the first nor the last block has them
   @Test
   void stringDimensionMaxAndMinComeFromMiddleBlocks() {
      Built built = build(XSchema.STRING, "m", "n", "c", "b", "y", "z", "k", "p");

      assertEquals(4, built.mv.getBlockSize());
      assertEquals("z", built.scriptable().getMember("MaxValue"));
      assertEquals("b", built.scriptable().getMember("MinValue"));
   }

   // the same for an integer dimension
   @Test
   void integerDimensionMaxAndMinComeFromMiddleBlocks() {
      Built built = build(XSchema.INTEGER, 50, 60, 3, 7, 99, 80, 20, 40);

      assertEquals(4, built.mv.getBlockSize());
      assertEquals(99, ((Number) built.scriptable().getMember("MaxValue")).intValue());
      assertEquals(3, ((Number) built.scriptable().getMember("MinValue")).intValue());
   }

   // MaxValue ignores NULLs, as SQL MAX does.
   // Pinned, looks unintended (Bug #82219): MinValue is NULL as soon as one block has a NULL in
   // the column.
   // The dictionary of a block keeps NULL as its first (smallest) value and the blocks are
   // compared with NULL lowest. SQL MIN ignores NULLs, and so does the path of a column without a
   // dictionary since Bug #78246 ("an all-null block has no min").
   @Test
   void nullValueMakesMinValueNull() {
      Built built = build(XSchema.STRING, null, "m", "c", "b", "y", "z", "k", "p");

      assertEquals("z", built.scriptable().getMember("MaxValue"));
      assertNull(built.scriptable().getMember("MinValue"));

      // without the NULL the smallest value is found
      assertEquals("b", build(XSchema.STRING, "n", "m", "c", "b", "y", "z", "k", "p")
         .scriptable().getMember("MinValue"));
   }

   // builds an MV with one dimension column "region" of the values, two rows per block
   private static Built build(String type, Object... values) {
      Object[][] data = new Object[values.length + 1][];
      data[0] = new Object[] { "region" };

      for(int i = 0; i < values.length; i++) {
         data[i + 1] = new Object[] { values[i] };
      }

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "region"));
      ref.setDataType(type);
      MVColumn column = new MVColumn(ref, true);
      MVDef def = new MVDef();
      def.container = new MVContainer(new ArrayList<>(List.of(column)), null);

      MVBuilder builder = new MVBuilder(new DefaultTableLens(data), def, false);
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         it.next().dispose();
      }

      MV mv = builder.getMV();
      // the column has a dictionary in every block, so MVScriptable takes the dictionary path
      assertNotNull(mv.getDictionary(0, 0));
      return new Built(def, column, mv);
   }

   private record Built(MVDef def, MVColumn column, MV mv) {
      MVScriptable scriptable() {
         return new MVScriptable(def, column, mv);
      }
   }
}
