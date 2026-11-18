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
package inetsoft.web.composer.ws.assembly;

import inetsoft.test.*;
import inetsoft.uql.schema.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V01: the default value of a Long worksheet variable is shown as the number, not as a
 * double with an exponent (Redmine #80154).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableAssemblyModelInfoLongDefaultTest {
   private static UserVariable longVariable(XValueNode node) {
      UserVariable variable = new UserVariable("v");
      variable.setTypeNode(new LongType());
      variable.setValueNode(node);
      return variable;
   }

   @DisplayName("V01 a Long variable with a Double 3.0E9 default shows 3000000000")
   @Test
   void v01_doubleNode() {
      VariableAssemblyModelInfo info =
         new VariableAssemblyModelInfo(longVariable(new DoubleValue("v", 3.0E9)));

      assertArrayEquals(new Object[] { "3000000000" }, info.getValue());
      assertFalse(info.getValue2().contains("E"), "value2 " + info.getValue2());
   }

   @DisplayName("V01 a Long variable with a Long 3000000000 default shows 3000000000")
   @Test
   void v01_longNode() {
      VariableAssemblyModelInfo info =
         new VariableAssemblyModelInfo(longVariable(new LongValue("v", 3000000000L)));

      assertArrayEquals(new Object[] { "3000000000" }, info.getValue());
      assertFalse(info.getValue2().contains("E"), "value2 " + info.getValue2());
   }

   private static UserVariable variable(XTypeNode type, XValueNode node) {
      UserVariable variable = new UserVariable("v");
      variable.setTypeNode(type);
      variable.setValueNode(node);
      return variable;
   }

   private static Object[] valueOf(UserVariable variable) {
      return new VariableAssemblyModelInfo(variable).getValue();
   }

   @DisplayName("V01 negative, small and just-over-int Double defaults under long")
   @Test
   void v01_longDoubleRange() {
      assertArrayEquals(new Object[] { "-3000000000" },
                        valueOf(longVariable(new DoubleValue("v", -3.0E9))));
      assertArrayEquals(new Object[] { "42" },
                        valueOf(longVariable(new DoubleValue("v", 42.0))));
      assertArrayEquals(new Object[] { "2147483648" },
                        valueOf(longVariable(new DoubleValue("v", 2147483648.0))));
   }

   @DisplayName("V01 a fractional or overflowing Double under long is not truncated")
   @Test
   void v01_longNotMangled() {
      assertArrayEquals(new Object[] { "3.5" },
                        valueOf(longVariable(new DoubleValue("v", 3.5))));
      assertArrayEquals(new Object[] { "1.0E30" },
                        valueOf(longVariable(new DoubleValue("v", 1e30))));
   }

   @DisplayName("V01 integer and double typed variables")
   @Test
   void v01_otherTypes() {
      assertArrayEquals(new Object[] { "42" },
                        valueOf(variable(new IntegerType(), new DoubleValue("v", 42.0))));
      assertArrayEquals(new Object[] { "3.5" },
                        valueOf(variable(new DoubleType(), new DoubleValue("v", 3.5))));
   }

   @DisplayName("V01 the valueslist match branch uses the declared type")
   @Test
   void v01_valuesList() {
      UserVariable variable = longVariable(new DoubleValue("v", 3.0E9));
      variable.setValues(new Object[] { 3000000000.0 });
      variable.setChoices(new Object[] { "x" });
      assertArrayEquals(new Object[] { "3000000000" }, valueOf(variable));
   }

   @DisplayName("V01 an Object[] default is formatted per element")
   @Test
   void v01_array() {
      LongValue node = new LongValue("v");
      node.setValue(new Object[] { 3.0E9, 42.0 });
      UserVariable variable = longVariable(node);
      assertArrayEquals(new Object[] { "3000000000", "42" }, valueOf(variable));
   }
}
