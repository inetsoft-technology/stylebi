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
package inetsoft.report.composition.graph;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class ModernLabelRuleTest {
   @Test
   void datesStayFlatAtAnyStep() {
      assertEquals(0, ModernLabelRule.rotation(true, 60, 3));
   }

   @Test
   void textStaysFlatAtEveryOtherLabel() {
      assertEquals(0, ModernLabelRule.rotation(false, 66, 44));
   }

   @Test
   void textTurnsTo45PastEveryOtherLabel() {
      assertEquals(45, ModernLabelRule.rotation(false, 45, 12));
   }

   @Test
   void theLimitScalesWithTheFont() {
      assertEquals(66, ModernLabelRule.limitPx(new Font("Roboto", Font.PLAIN, 10)), 0.01);
      assertEquals(79.2, ModernLabelRule.limitPx(new Font("Roboto", Font.PLAIN, 12)), 0.01);
   }

   @Test
   void theColumnLimitNeverDropsBelowTheMinimum() {
      Font f = new Font("Roboto", Font.PLAIN, 10);
      assertEquals(110, ModernLabelRule.columnLimitPx(f, 800), 0.01);
      assertEquals(90, ModernLabelRule.columnLimitPx(f, 300), 0.01);
      assertEquals(66, ModernLabelRule.columnLimitPx(f, 150), 0.01);
   }
}
