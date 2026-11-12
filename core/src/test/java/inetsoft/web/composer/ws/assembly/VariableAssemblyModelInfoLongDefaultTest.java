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

   @Disabled("mismatch V01 #80154")
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
}
