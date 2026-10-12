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
package inetsoft.uql.asset;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77910: a sub-query whose table's swap file is lost mid-read never caches the rows read
 * so far as its values. A later evaluation of the condition, e.g. the condition filter's retry
 * of the same row, fails again instead of matching against the partial list, and once the
 * table is readable again the values are read in full.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SubQueryValueSwapLostTest {
   @BeforeEach
   void setUp() {
      lost = new LostSwapFile();
   }

   @AfterEach
   void tearDown() {
      lost.close();
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void lostReadIsNeverCachedAsTheValues(boolean optimized) throws Exception {
      AssetCondition condition = condition(subTable(), optimized);

      SwapFileReadException first =
         assertThrows(SwapFileReadException.class, () -> condition.evaluate(4));
      assertEquals(lost.getFile(), first.getFile());
      // still lost: a later evaluation fails again rather than match the rows read so far
      assertThrows(SwapFileReadException.class, () -> condition.evaluate(4),
                   "a later evaluation must not use the partial values");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void evaluationAfterTheTableIsReadableReadsTheWholeSubQuery(boolean optimized)
      throws Exception
   {
      LostSwapFile.Table sub = subTable();
      AssetCondition condition = condition(sub, optimized);
      assertThrows(SwapFileReadException.class, () -> condition.evaluate(4));

      sub.lost = false;

      assertTrue(condition.evaluate(4), "4 is a value of the sub-query");
      assertTrue(condition.evaluate(1));
      assertFalse(condition.evaluate(9));
   }

   /**
    * Sub-query rows {@code f1 = 1..5}; rows from 3 on are in the lost swap file.
    */
   private LostSwapFile.Table subTable() {
      return new LostSwapFile.Table(
         new Object[][] { { "f1" }, { 1 }, { 2 }, { 3 }, { 4 }, { 5 } }, 3, true, lost);
   }

   private static AssetCondition condition(LostSwapFile.Table sub, boolean optimized)
      throws Exception
   {
      SubQueryValue subQuery = new SubQueryValue();
      subQuery.setAttribute(new AttributeRef(null, "f1"));
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.ONE_OF);
      condition.setType(XSchema.INTEGER);
      condition.addValue(subQuery);
      condition.setOptimized(optimized);
      condition.init();
      condition.initSubTable(sub);
      condition.initMainTable(new DefaultTableLens(new Object[][] { { "v" }, { 1 } }), 0);
      return condition;
   }

   private LostSwapFile lost;
}
