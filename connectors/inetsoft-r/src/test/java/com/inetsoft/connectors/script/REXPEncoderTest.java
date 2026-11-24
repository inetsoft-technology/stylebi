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
package com.inetsoft.connectors.script;

import org.junit.jupiter.api.Test;
import org.rosuda.REngine.*;

import static org.junit.jupiter.api.Assertions.*;

class REXPEncoderTest {
   private final REXPEncoder encoder = new REXPEncoder();

   @Test
   void nullInIntegerArrayIsNA() throws Exception {
      REXP rexp = encoder.encode(new Object[] { 3, null, 5 });
      assertInstanceOf(REXPInteger.class, rexp);
      assertArrayEquals(new boolean[] { false, true, false }, rexp.isNA());
      assertArrayEquals(new int[] { 3, REXPInteger.NA, 5 }, rexp.asIntegers());
   }

   @Test
   void nullInDoubleArrayIsNA() throws Exception {
      REXP rexp = encoder.encode(new Object[] { 1.5, null });
      assertInstanceOf(REXPDouble.class, rexp);
      assertArrayEquals(new boolean[] { false, true }, rexp.isNA());
   }

   @Test
   void nullInStringArrayIsNA() throws Exception {
      REXP rexp = encoder.encode(new Object[] { "a", null });
      assertInstanceOf(REXPString.class, rexp);
      assertArrayEquals(new boolean[] { false, true }, rexp.isNA());
   }

   @Test
   void nullInBooleanArrayIsNA() throws Exception {
      REXP rexp = encoder.encode(new Object[] { true, null });
      assertInstanceOf(REXPLogical.class, rexp);
      assertArrayEquals(new boolean[] { false, true }, rexp.isNA());
   }
}
