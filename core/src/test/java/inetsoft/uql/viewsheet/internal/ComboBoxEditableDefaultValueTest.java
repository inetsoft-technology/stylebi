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
import inetsoft.uql.viewsheet.ListData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79780: on an editable combo box, selecting the default value must keep the default
 * value selected instead of falling back to the first list item.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComboBoxEditableDefaultValueTest {
   private static ComboBoxVSAssemblyInfo createInfo(boolean editable, String defaultValue) {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      ListData data = new ListData();
      data.setValues(new Object[] { "01923", "23421", "29183" });
      data.setLabels(new String[] { "AMG", "Bob", "Ubermeyer" });
      info.setListData(data);
      // validate() while not editable leaves the first item as the fallback value
      info.setValues(data.getValues());
      info.setDefaultValue(defaultValue);
      info.setTextEditable(editable);
      return info;
   }

   @Test
   void editableSelectingDefaultValueKeepsDefault() {
      ComboBoxVSAssemblyInfo info = createInfo(true, "29183");

      info.setSelectedObject("23421");
      assertEquals("23421", info.getSelectedObject());

      info.setSelectedObject("29183");
      assertEquals("29183", info.getSelectedObject());
   }

   @Test
   void editableClearedSelectionFallsBackToDefault() {
      ComboBoxVSAssemblyInfo info = createInfo(true, "29183");

      info.setSelectedObject("23421");
      info.setSelectedObject(null);
      assertEquals("29183", info.getSelectedObject());
   }

   @Test
   void editableValidateRefreshesFallbackToDefault() {
      ComboBoxVSAssemblyInfo info = createInfo(true, "29183");

      info.validate();
      assertEquals("29183", info.getSelectedObject());
   }

   @Test
   void editableWithoutDefaultDoesNotAutoSelectFirstItem() {
      ComboBoxVSAssemblyInfo info = createInfo(true, null);

      info.setSelectedObject("23421");
      info.setSelectedObject(null);
      assertNull(info.getSelectedObject());
   }

   @Test
   void nonEditableStillAutoSelectsFirstItem() {
      ComboBoxVSAssemblyInfo info = createInfo(false, "29183");
      info.setSelectedObject("23421");
      assertEquals("23421", info.getSelectedObject());

      info = new ComboBoxVSAssemblyInfo();
      ListData data = new ListData();
      data.setValues(new Object[] { "01923", "23421" });
      data.setLabels(new String[] { "AMG", "Bob" });
      info.setListData(data);
      info.setValues(data.getValues());
      assertEquals("01923", info.getSelectedObject());
   }
}
