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
package inetsoft.web.composer.vs.dialog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PresenterPropertyDialogServiceSaveTest {
   private static final String IMAGE = "inetsoft.report.painter.ImagePresenter";
   private static final String RATIO = "maintainAspectRatio";

   @Test
   void aTickedAspectRatioOnAModernTableIsNotSavedAsTheAuthorsChoice() {
      assertNull(PresenterPropertyDialogService.valueToSave(IMAGE, RATIO, Boolean.TRUE, true));
   }

   @Test
   void anUntickedAspectRatioOnAModernTableIsSaved() {
      assertEquals(Boolean.FALSE,
                   PresenterPropertyDialogService.valueToSave(IMAGE, RATIO, Boolean.FALSE, true));
   }

   @Test
   void aTickedAspectRatioOnAClassicTableIsSaved() {
      assertEquals(Boolean.TRUE,
                   PresenterPropertyDialogService.valueToSave(IMAGE, RATIO, Boolean.TRUE, false));
   }

   @Test
   void otherPropertiesAndPresentersAreSavedAsTheyCame() {
      assertEquals(Boolean.TRUE,
                   PresenterPropertyDialogService.valueToSave(IMAGE, "auto", Boolean.TRUE, true));
      assertEquals(Boolean.TRUE, PresenterPropertyDialogService.valueToSave(
         "inetsoft.report.painter.BooleanPresenter", RATIO, Boolean.TRUE, true));
   }
}
