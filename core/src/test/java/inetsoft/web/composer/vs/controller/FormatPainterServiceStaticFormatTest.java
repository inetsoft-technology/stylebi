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
package inetsoft.web.composer.vs.controller;

import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import inetsoft.uql.viewsheet.graph.TextFormat;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.TextLayoutItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the static-text-item format bridge (issue 1): the chart Format panel must read from
 * and write to the matching STATIC TextLayoutItem's inline styling fields, which the renderer
 * (LayoutTextFrame.buildStaticTextSpec) already consumes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormatPainterServiceStaticFormatTest {
   @Test
   void applyFormatToStaticItemWritesInlineFields() {
      TextLayoutItem item = TextLayoutItem.ofStatic("Total:");
      TextFormat user = new TextFormat();
      user.setFont(new Font("Roboto", Font.BOLD, 16), true);
      user.setColor(Color.CYAN, true);

      FormatPainterService.applyFormatToStaticItem(item, user);

      assertEquals("Roboto", item.getFontFamily());
      assertEquals(16, item.getFontSize());
      assertTrue(item.isBold());
      assertFalse(item.isItalic());
      assertEquals(Color.CYAN, item.getColor());
   }

   @Test
   void buildFormatFromStaticItemReflectsInlineFields() {
      TextLayoutItem item = TextLayoutItem.ofStatic("Total:");
      item.setFontFamily("Roboto");
      item.setFontSize(16);
      item.setBold(true);
      item.setColor(Color.CYAN);

      CompositeTextFormat fmt = FormatPainterService.buildFormatFromStaticItem(item);
      TextFormat user = fmt.getUserDefinedFormat();

      assertNotNull(user.getFont());
      assertEquals("Roboto", user.getFont().getFamily());
      assertEquals(16, user.getFont().getSize());
      assertTrue((user.getFont().getStyle() & Font.BOLD) != 0);
      assertEquals(Color.CYAN, user.getColor());
   }

   @Test
   void roundTripsThroughItem() {
      TextLayoutItem item = TextLayoutItem.ofStatic("Total:");
      TextFormat user = new TextFormat();
      user.setFont(new Font("Arial", Font.ITALIC, 20), true);
      user.setColor(Color.RED, true);

      FormatPainterService.applyFormatToStaticItem(item, user);
      TextFormat readBack = FormatPainterService.buildFormatFromStaticItem(item).getUserDefinedFormat();

      assertEquals("Arial", readBack.getFont().getFamily());
      assertEquals(20, readBack.getFont().getSize());
      assertTrue((readBack.getFont().getStyle() & Font.ITALIC) != 0);
      assertEquals(Color.RED, readBack.getColor());
   }
}
