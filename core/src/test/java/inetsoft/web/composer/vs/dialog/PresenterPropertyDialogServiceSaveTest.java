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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// VizContext reads the density setting, which needs the Spring context
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
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
   void aTableCellOfAModernTableIsModern() {
      assertTrue(PresenterPropertyDialogService.isModernTableCell(
         assembly(mock(TableDataVSAssembly.class), VizMark.MODERN_LIGHT)));
      assertTrue(PresenterPropertyDialogService.isModernTableCell(
         assembly(mock(TableDataVSAssembly.class), VizMark.MODERN_DARK)));
   }

   @Test
   void aTableCellOfAClassicTableIsNot() {
      assertFalse(PresenterPropertyDialogService.isModernTableCell(
         assembly(mock(TableDataVSAssembly.class), null)));
   }

   @Test
   void aTextWithAVizMarkIsNotAModernTableCell() {
      // the text shows the raw value, so a ticked box there is the author's and must be saved
      assertFalse(PresenterPropertyDialogService.isModernTableCell(
         assembly(mock(TextVSAssembly.class), VizMark.MODERN_LIGHT)));
      assertFalse(PresenterPropertyDialogService.isModernTableCell(null));
   }

   private static <T extends VSAssembly> T assembly(T assembly, VizMark mark) {
      VSAssemblyInfo info = mock(VSAssemblyInfo.class);
      when(info.getVizMark()).thenReturn(mark);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }

   @Test
   void otherPropertiesAndPresentersAreSavedAsTheyCame() {
      assertEquals(Boolean.TRUE,
                   PresenterPropertyDialogService.valueToSave(IMAGE, "auto", Boolean.TRUE, true));
      assertEquals(Boolean.TRUE, PresenterPropertyDialogService.valueToSave(
         "inetsoft.report.painter.BooleanPresenter", RATIO, Boolean.TRUE, true));
   }
}
