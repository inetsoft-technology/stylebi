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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82105: {@link SelectionListVSAssemblyInfo#getRowHeights()} must not fail on a
 * selection value that has no cell format (it is read by the PDF/PNG/HTML coordinate
 * helpers and by "expand selections" in every export format).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionListVSAssemblyInfoRowHeightsTest {
   @Test
   void unformattedValuesGetTheCellHeight() {
      SelectionListVSAssemblyInfo info = listInfo(value("a", null), value("b", null));

      List<Double> heights = assertDoesNotThrow(info::getRowHeights);

      assertEquals(List.of((double) info.getCellHeight(), (double) info.getCellHeight()),
                   heights);
   }

   @Test
   void wrappingListWithAnUnformattedValueUsesTheCellHeightForIt() {
      VSCompositeFormat wrap = new VSCompositeFormat();
      wrap.getUserDefinedFormat().setWrappingValue(true);
      wrap.getUserDefinedFormat().setFontValue(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
      SelectionListVSAssemblyInfo info = listInfo(value("a", wrap), value("b", null));

      List<Double> heights = assertDoesNotThrow(info::getRowHeights);

      assertEquals(2, heights.size());
      assertEquals(info.getCellHeight(), heights.get(1), 0.0);
   }

   private static SelectionListVSAssemblyInfo listInfo(SelectionValue... values) {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPixelSize(new Dimension(100, 200));
      info.setColumnCount(1);
      SelectionList list = new SelectionList();
      list.setSelectionValues(values);
      info.setSelectionList(list);
      return info;
   }

   private static SelectionValue value(String label, VSCompositeFormat format) {
      SelectionValue value = new SelectionValue(label, label);
      value.setFormat(format);
      return value;
   }
}
