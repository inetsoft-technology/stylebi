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
package inetsoft.web.wiz.controller;

import inetsoft.report.StyleFont;
import inetsoft.util.gui.GuiTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

@Tag("core")
class WizFontControllerTest {
   @Test
   void list_keepsLiteralDefaultFirstAndMatchesGuiToolList() {
      try(MockedStatic<GuiTool> gui = mockStatic(GuiTool.class);
          MockedStatic<StyleFont> style = mockStatic(StyleFont.class))
      {
         gui.when(GuiTool::getAllFonts)
            .thenReturn(new String[] { "Default", "Brand Sans", "Arial" });
         style.when(StyleFont::getDefaultFontFamily).thenReturn("Roboto");

         WizFontController.FontList result = new WizFontController().list();

         assertEquals("Roboto", result.defaultFamily());
         assertEquals(java.util.List.of("Default", "Brand Sans", "Arial"), result.families());
      }
   }
}
