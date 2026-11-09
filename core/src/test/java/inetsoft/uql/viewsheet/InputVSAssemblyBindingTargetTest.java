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
package inetsoft.uql.viewsheet;

import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80864: the one rule for what an input writes to. A null and an empty table name both mean
 * unbound; a {@code $(var)} variable binding resolves to the bare variable name.
 */
@WizAgentTestSupport
class InputVSAssemblyBindingTargetTest {
   @ParameterizedTest
   @NullAndEmptySource
   void nullAndEmptyTableNamesAreUnbound(String tableName) {
      SliderVSAssembly input = input(tableName, false);

      assertTrue(input.isUnbound());
      assertNull(input.getBindingTargetName());
      assertNull(input.getVariableTableKey());
   }

   @Test
   void variableBindingResolvesToTheVariableName() {
      SliderVSAssembly input = input("$(MinDiscount)", true);

      assertFalse(input.isUnbound());
      assertEquals("MinDiscount", input.getBindingTargetName());
      assertEquals("MinDiscount", input.getVariableTableKey());
   }

   @Test
   void plainTableNameIsKept() {
      SliderVSAssembly input = input("Orders", false);

      assertFalse(input.isUnbound());
      assertEquals("Orders", input.getBindingTargetName());
      assertNull(input.getVariableTableKey(), "not a variable binding");
   }

   @Test
   void variableNamedWithoutReferenceSyntaxIsKept() {
      SliderVSAssembly input = input("MinDiscount", true);

      assertEquals("MinDiscount", input.getBindingTargetName());
      assertEquals("MinDiscount", input.getVariableTableKey());
   }

   @Test
   void referenceSyntaxIsLiteralWhenNotAVariableBinding() {
      SliderVSAssembly input = input("$(MinDiscount)", false);

      assertEquals("$(MinDiscount)", input.getBindingTargetName());
   }

   private static SliderVSAssembly input(String tableName, boolean variable) {
      SliderVSAssembly input = new SliderVSAssembly(new Viewsheet(), "Slider1");
      input.setTableName(tableName);
      input.setVariable(variable);
      return input;
   }
}
