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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82219: MV.MinValue of a dimension column must be the smallest non-null value, not NULL as
 * soon as one block dictionary holds a NULL (string/double), and not min - 1 for an integer
 * (number-range) dictionary whose block holds a NULL. A block of only NULLs has no min or max.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVScriptableNullMinTest {
   private String oldPreferred;

   @BeforeEach
   void setUp() {
      oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      SreeEnv.setProperty("mv.preferred.block", "2");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.preferred.block", oldPreferred);
   }

   @Test
   void stringNullInFirstBlockIsNotTheMin() {
      assertMinMax(XSchema.STRING, "b", "z", null, "m", "c", "b", "y", "z", "k", "p");
   }

   @Test
   void stringNullInMiddleBlockIsNotTheMin() {
      assertMinMax(XSchema.STRING, "b", "z", "m", "n", "c", "b", null, "z", "k", "p");
   }

   @Test
   void stringAllNullBlockDoesNotWin() {
      assertMinMax(XSchema.STRING, "b", "z", null, null, "c", "b", "y", "z", "k", "p");
   }

   @Test
   void doubleNullIsNotTheMin() {
      assertMinMax(XSchema.DOUBLE, 3.5, 9.5, null, 6.5, 3.5, 7.5, 9.5, 8.5);
   }

   @Test
   void integerNullDoesNotLowerTheMin() {
      assertMinMax(XSchema.INTEGER, 3, 99, null, 3, 60, 7, 99, 80);
   }

   @Test
   void integerAllNullBlockDoesNotWin() {
      assertMinMax(XSchema.INTEGER, 20, 99, 50, 60, null, null, 20, 99);
   }

   @Test
   void allNullColumnHasNoMinOrMax() {
      assertMinMax(XSchema.STRING, null, null, null, null, null, null);
      assertMinMax(XSchema.INTEGER, null, null, null, null, null, null);
   }

   @Test
   void appendAfterNullBearingLastBlockKeepsTheMin() {
      MVDef def = newDef(XSchema.STRING);
      MV base = build(def, null, "m", "c", "b", null);
      MV mv = build(def, base, "x", "y", "w");

      MVScriptable scriptable = new MVScriptable(def, def.getColumns().get(0), mv);
      assertEquals("b", scriptable.getMember("MinValue"));
      assertEquals("y", scriptable.getMember("MaxValue"));
   }

   @Test
   void dictionaryMinSkipsTheNullSlot() {
      MV mv = build(newDef(XSchema.STRING), null, null, "m", "c", "b");
      XDimDictionary dict = mv.getDictionaries(0)[0];

      assertTrue(dict.containsNull());
      assertNull(dict.getValue(0), "index 0 is still the null slot");
      assertEquals("m", dict.min());
      assertEquals("m", dict.max());

      MV imv = build(newDef(XSchema.INTEGER), null, null, 3, 60, 7);
      XDimDictionary idict = imv.getDictionaries(0)[0];

      assertTrue(idict.containsNull());
      assertNull(idict.getValue(0), "index 0 is still the null slot");
      assertEquals(3, idict.getValue(idict.indexOf(3, 0)), "encoding is unchanged");
      assertEquals(3, idict.min());
      assertEquals(3, idict.max());
   }

   private void assertMinMax(String type, Object min, Object max, Object... data) {
      MVDef def = newDef(type);
      MV mv = build(def, null, data);
      MVScriptable scriptable = new MVScriptable(def, def.getColumns().get(0), mv);

      assertEquals(min, scriptable.getMember("MinValue"), "MinValue of " + Arrays.toString(data));
      assertEquals(max, scriptable.getMember("MaxValue"), "MaxValue of " + Arrays.toString(data));
   }

   private static MV build(MVDef def, MV omv, Object... data) {
      Object[][] rows = new Object[data.length + 1][];
      rows[0] = new Object[] { "dim" };

      for(int r = 0; r < data.length; r++) {
         rows[r + 1] = new Object[] { data[r] };
      }

      MVBuilder builder = new MVBuilder(new DefaultTableLens(rows), def, false, omv);
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         it.next().dispose();
      }

      return builder.getMV();
   }

   private static MVDef newDef(String type) {
      ColumnRef dimRef = new ColumnRef(new AttributeRef(null, "dim"));
      dimRef.setDataType(type);

      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(dimRef, true));

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }
}
