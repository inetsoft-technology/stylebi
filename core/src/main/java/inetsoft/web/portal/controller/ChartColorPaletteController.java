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
package inetsoft.web.portal.controller;

import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.util.Tool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.util.Arrays;

/**
 * Serves the categorical chart palette a color picker should offer, already resolved for the
 * org's modern and dark gates. Not under /api/composer so portal viewer widgets can use it.
 */
@RestController
public class ChartColorPaletteController {
   /**
    * Resolves the palette for the caller's current org (via thread-context principal), same as
    * every other unswitched portal endpoint. Does not follow a cross-org asset switch — the
    * client fetches this once at bootstrap with no org parameter to switch on.
    */
   @GetMapping("/api/portal/chart-color-palette")
   public String[] getChartColorPalette() {
      Color[] colors = VSChartPaletteDefaults.pickerPalette(VizContext.ofGate());

      // pickerPalette() does not cap length - a customer format.css can declare a palette past
      // index 40 and the renderer honors all of it - but the picker grid is a fixed 5x8, so this
      // is the seam that keeps the two from silently diverging.
      return Arrays.stream(colors)
         .limit(MAX_COLORS)
         .map(Tool::toString)
         .toArray(String[]::new);
   }

   private static final int MAX_COLORS = 40;
}
