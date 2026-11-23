/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.util;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81898: CoreTool.equals (used by the distinct pass behind a Selection List) must tell
 * integral values above 2^53 apart. Mixed integral/floating pairs keep the double comparison.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CoreToolEqualsIntegralTest {
   private static final long P = 9007199254740992L; // 2^53

   @Test
   void longsAbove2Pow53DifferingByOneAreNotEqual() {
      assertFalse(CoreTool.equals(P, P + 1));
      assertFalse(CoreTool.equals(P + 1, P));
   }

   @Test
   void sameLongsAreEqual() {
      assertTrue(CoreTool.equals(P + 1, P + 1));
   }

   @Test
   void mixedIntegralTypesCompareByValue() {
      assertTrue(CoreTool.equals(5, 5L));
      assertTrue(CoreTool.equals((short) 5, (byte) 5));
      assertFalse(CoreTool.equals(5, 6L));
   }

   @Test
   void integralAndFloatingKeepDoubleComparison() {
      assertTrue(CoreTool.equals(1, 1.0));
      assertTrue(CoreTool.equals(P, (double) P));
   }
}
