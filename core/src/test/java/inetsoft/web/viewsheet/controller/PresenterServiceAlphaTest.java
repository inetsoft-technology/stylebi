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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A presenter in a modern table is painted opaque. The cell alpha there is the translucent card
 * tint of the background, and applying it to the presenter too made every bar nearly invisible.
 */
@Tag("core")
class PresenterServiceAlphaTest {
   // the card tint of a modern cell: 15 percent, as VSFormatTableLens.getAlpha derives it
   private static final Color TINT = new Color(255, 255, 255, 38);
   private static final int TINT_ALPHA = (int) ((TINT.getAlpha() / 255.0) * 100);

   @Test
   void aModernCellTintDoesNotFadeThePresenter() {
      assertEquals(100, PresenterService.presenterAlpha(TINT_ALPHA, TINT, true));
   }

   @Test
   void aClassicCellKeepsItsAlpha() {
      assertEquals(TINT_ALPHA, PresenterService.presenterAlpha(TINT_ALPHA, TINT, false));
   }

   @Test
   void aModernAlphaTheAuthorSetIsKept() {
      assertEquals(50, PresenterService.presenterAlpha(50, new Color(255, 255, 255), true));
      assertEquals(50, PresenterService.presenterAlpha(50, null, true));
   }

   @Test
   void anOpaqueCellIsUnchanged() {
      assertEquals(100, PresenterService.presenterAlpha(100, new Color(255, 255, 255), true));
   }
}
