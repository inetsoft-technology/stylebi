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
package inetsoft.web.binding.controller;

import inetsoft.uql.viewsheet.graph.TextLayout;
import inetsoft.uql.viewsheet.graph.TextLayoutItem;
import inetsoft.uql.viewsheet.graph.TextLayoutRow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the static-format clobber fix (issue 1): a layout round-trip from the frontend (whose
 * model never carries static styling) must NOT wipe the panel-set inline format of static items.
 */
@Tag("core")
class ChangeChartTextLayoutStaticFormatTest {
   private static TextLayout layoutWithStatic(String text, Color color, String family,
                                              int size, boolean bold)
   {
      TextLayout tl = new TextLayout();
      TextLayoutRow row = new TextLayoutRow();
      TextLayoutItem item = TextLayoutItem.ofStatic(text);
      item.setColor(color);
      item.setFontFamily(family);
      item.setFontSize(size);
      item.setBold(bold);
      row.addItem(item);
      tl.addRow(row);
      return tl;
   }

   @Test
   void preservesStaticStylingWhenIncomingLayoutHasNone() {
      TextLayout oldLayout = layoutWithStatic("Total:", Color.CYAN, "Roboto", 24, true);
      // Incoming (frontend) layout: same static text, no styling (the clobber input).
      TextLayout newLayout = layoutWithStatic("Total:", null, null, 10, false);

      ChangeChartAestheticService.preserveStaticFormatting(oldLayout, newLayout);

      TextLayoutItem merged = newLayout.getAllItems().get(0);
      assertEquals(Color.CYAN, merged.getColor());
      assertEquals("Roboto", merged.getFontFamily());
      assertEquals(24, merged.getFontSize());
      assertTrue(merged.isBold());
   }

   @Test
   void doesNotCarryStylingToADifferentStaticText() {
      TextLayout oldLayout = layoutWithStatic("Total:", Color.CYAN, "Roboto", 24, true);
      TextLayout newLayout = layoutWithStatic("Sum:", null, null, 10, false);

      ChangeChartAestheticService.preserveStaticFormatting(oldLayout, newLayout);

      TextLayoutItem merged = newLayout.getAllItems().get(0);
      assertNull(merged.getColor());
      assertNull(merged.getFontFamily());
   }

   @Test
   void nullLayoutsAreNoOps() {
      TextLayout newLayout = layoutWithStatic("Total:", null, null, 10, false);
      assertDoesNotThrow(() -> ChangeChartAestheticService.preserveStaticFormatting(null, newLayout));
      assertDoesNotThrow(() -> ChangeChartAestheticService.preserveStaticFormatting(newLayout, null));
   }
}
