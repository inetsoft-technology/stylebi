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
package inetsoft.util.script.graal;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79847: CALC.sumif called from script must pair range and sum_range by
 * position when either array contains empty (null, undefined, "") or text cells.
 */
@Tag("core")
class CalcSumifScriptTest {
   private GraalJavaScriptEngine engine;

   @BeforeEach
   void setup() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new java.util.HashMap<>());
   }

   @AfterEach
   void teardown() {
      engine.close();
   }

   private double eval(String src) throws Exception {
      Object result = engine.exec(engine.compile(src), null, null);
      assertInstanceOf(Number.class, result, src);
      return ((Number) result).doubleValue();
   }

   @Test
   void holeInSumRange() throws Exception {
      assertEquals(70.0, eval("CALC.sumif([1,2,3,4], '>2', [10,null,30,40])"));
      assertEquals(70.0, eval("CALC.sumif([1,2,3,4], '>2', [10,undefined,30,40])"));
      assertEquals(70.0, eval("CALC.sumif([1,2,3,4], '>2', [10,'',30,40])"));
      assertEquals(70.0, eval("CALC.sumif([1,2,3,4], '>2', [10,'x',30,40])"));
      assertEquals(70.0, eval("sumif([1,2,3,4], '>2', [10,null,30,40])"));
      assertEquals(70.0, eval("CALC.sumif('1,2,3,4', '>2', '10,,30,40')"));
      // a hole at a matched position adds 0
      assertEquals(40.0, eval("CALC.sumif([1,2,3,4], '>2', [10,20,null,40])"));
   }

   @Test
   void holeInRange() throws Exception {
      assertEquals(70.0, eval("CALC.sumif([1,null,3,4], '>2', [10,20,30,40])"));
      assertEquals(70.0, eval("CALC.sumif([1,'',3,4], '>2', [10,20,30,40])"));
      assertEquals(70.0, eval("CALC.sumif([1,'x',3,4], '>2', [10,20,30,40])"));
      // an empty range cell never matches, even where null < 5 is true in script
      assertEquals(80.0, eval("CALC.sumif([1,null,3,4], '<5', [10,20,30,40])"));
   }

   @Test
   void omittedAndAllEmptySumRange() throws Exception {
      assertEquals(7.0, eval("CALC.sumif([1,2,3,4], '>2')"));
      assertEquals(7.0, eval("CALC.sumif([1,null,3,4], '>2')"));
      assertEquals(0.0, eval("CALC.sumif([1,2,3,4], '>2', [null,null,null,null])"));
      assertEquals(0.0, eval("CALC.sumif([1,2,3,4], '>2', ['a','b','c','d'])"));
   }

   @Test
   void unchangedBehavior() throws Exception {
      assertEquals(70.0, eval("CALC.sumif([1,2,3,4], '>2', [10,20,30,40])"));
      // numeric strings are still parsed
      assertEquals(70.0, eval("CALC.sumif(['1','2','3','4'], '>2', ['10','20','30','40'])"));
      // criteria given as script text, compared with a string
      assertEquals(30.0, eval("CALC.sumif([1,2,3,4], \"=='3'\", [10,20,30,40])"));
      // bug #71337: a matched index beyond sum_range adds nothing
      assertEquals(0.0, eval("CALC.sumif([1,2,3,4], '>2', [10,20])"));
   }
}
