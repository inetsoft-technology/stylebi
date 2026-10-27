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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVBuilder;
import inetsoft.mv.data.SubMV;
import inetsoft.mv.data.XDimDictionary;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79400: a real multi-block {@link MVBuilder} build must give every block a string measure
 * dictionary holding only that block's rows. The measure loop of
 * {@code MVCreatorUtil.addRowToDictionaries} used to start at row 0, so block N's dictionary also
 * held the strings of blocks 0..N-1.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVBuilderMultiBlockDictionaryTest {
   private static final int BLOCK = 4;
   private static final int ROWS = 12;
   private String oldPreferred;

   @BeforeEach
   void setUp() {
      oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      SreeEnv.setProperty("mv.preferred.block", String.valueOf(BLOCK));
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.preferred.block", oldPreferred);
   }

   @Test
   void eachBlockStringMeasureDictionaryHoldsOnlyItsRows() {
      MVBuilder builder = new MVBuilder(newTable(), newDef(), false);
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         it.next().dispose();
      }

      MV mv = builder.getMV();
      assertEquals(ROWS / BLOCK, mv.getBlockSize());

      Map<Integer, Set<Object>> actual = new TreeMap<>();
      Map<Integer, Set<Object>> expectedAll = new TreeMap<>();

      for(int b = 0; b < mv.getBlockSize(); b++) {
         // dimension column 0, string measure column 1
         XDimDictionary dict = mv.getDictionaries(b)[1];
         Set<Object> values = new TreeSet<>();

         for(int i = 0; i < dict.size(); i++) {
            values.add(dict.getValue(i));
         }

         Set<Object> expected = new TreeSet<>();

         for(int r = b * BLOCK; r < (b + 1) * BLOCK; r++) {
            expected.add("m" + r);
         }

         actual.put(b, values);
         expectedAll.put(b, expected);
      }

      assertEquals(expectedAll, actual, "string measure dictionary per block");
   }

   private static DefaultTableLens newTable() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "dim", "str" };

      for(int r = 0; r < ROWS; r++) {
         data[r + 1] = new Object[] { "d" + (r % 3), "m" + r };
      }

      return new DefaultTableLens(data);
   }

   private static MVDef newDef() {
      ColumnRef dimRef = new ColumnRef(new AttributeRef(null, "dim"));
      dimRef.setDataType(XSchema.STRING);
      ColumnRef strRef = new ColumnRef(new AttributeRef(null, "str"));
      strRef.setDataType(XSchema.STRING);

      List<MVColumn> columns = new ArrayList<>();
      columns.add(new MVColumn(dimRef, true));
      columns.add(new MVColumn(strRef, false));

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }
}
