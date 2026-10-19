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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The font families the Composer's font pane offers ({@code /api/format/fonts}), under wiz's own
 * JWT-authenticated {@code /api/wiz} namespace so a bearer-token client can list them. Same source
 * of truth as the Composer: {@link GuiTool#getAllFonts()}.
 */
@RestController
@RequestMapping("/api/wiz/v1/fonts")
public class WizFontController {
   /**
    * @param defaultFamily the real family the "Default" entry stands for.
    * @param families      "Default" first, then portal-uploaded fonts, then system families.
    *                      Unlike {@code /api/format/fonts}, element 0 stays the literal "Default",
    *                      because that is what a font written with the default family carries.
    */
   public record FontList(String defaultFamily, List<String> families) {}

   @GetMapping
   public FontList list() {
      return new FontList(StyleFont.getDefaultFontFamily(), List.of(GuiTool.getAllFonts()));
   }
}
