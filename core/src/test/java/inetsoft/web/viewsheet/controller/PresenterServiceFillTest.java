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
package inetsoft.web.viewsheet.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A presenter's image in a modern table is not filled with the cell's background. The cell paints
 * that itself, and filling the image too laid the translucent tint on twice, which showed as a
 * darker box a little smaller than the cell.
 */
@Tag("core")
class PresenterServiceFillTest {
   private static final Color TINT = new Color(170, 170, 170, 38);

   @Test
   void aModernCellsImageIsNotFilled() {
      assertNull(PresenterService.presenterFill(TINT, true));
      assertNull(PresenterService.presenterFill(null, true));
   }

   @Test
   void aClassicCellsImageIsFilledWithTheCellBackground() {
      assertEquals(TINT, PresenterService.presenterFill(TINT, false));
   }

   @Test
   void aClassicCellWithNoBackgroundGetsAClearFill() {
      Color fill = PresenterService.presenterFill(null, false);

      assertNotNull(fill);
      assertEquals(0, fill.getAlpha());
   }
}
