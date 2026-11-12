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
package inetsoft.web.wiz.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.PasswordColumnOption;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Test strategy
 *
 * storedForm() answers "what would this input hold after this request" without touching the
 * assembly. The plugin compares it with the value held after the write to tell a request the input
 * converted to the value it already held (a date input drops the time of day) from a write a
 * refresh reverted (Bug #81221, sibling of #80864). Each case checks that the stored form is
 * what the input itself would store: a request and the converted request must land on the same
 * stored value, and a different request must not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class InputValueServiceStoredFormTest {
   private static ComboBoxVSAssemblyInfo combo(String dataType) {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setDataType(dataType);
      info.setTextEditable(true);
      return info;
   }

   private static String stored(List<Object> values) {
      assertNotNull(values);
      assertEquals(1, values.size());
      return String.valueOf(values.get(0));
   }

   @Test
   void dateInputDropsTheTimeOfDay() {
      ComboBoxVSAssemblyInfo info = combo(XSchema.DATE);
      String day = stored(InputValueService.storedForm(info, XSchema.DATE, "2022-03-13"));
      String withTime = stored(InputValueService.storedForm(info, XSchema.DATE, "2022-03-13 05:00:00"));

      assertEquals(day, withTime);
      assertNotEquals(day, stored(InputValueService.storedForm(info, XSchema.DATE, "2022-03-14")));
   }

   @Test
   void timeInputDropsAFraction() {
      ComboBoxVSAssemblyInfo info = combo(XSchema.TIME);
      String whole = stored(InputValueService.storedForm(info, XSchema.TIME, "05:00:00"));
      String fraction = stored(InputValueService.storedForm(info, XSchema.TIME, "05:00:00.500"));

      assertEquals(whole, fraction);
      assertNotEquals(whole, stored(InputValueService.storedForm(info, XSchema.TIME, "06:00:00")));
   }

   @Test
   void timeInstantKeepsTheTimeOfDay() {
      ComboBoxVSAssemblyInfo info = combo(XSchema.TIME_INSTANT);
      String midnight = stored(InputValueService.storedForm(info, XSchema.TIME_INSTANT, "2022-03-13 00:00:00"));
      String morning = stored(InputValueService.storedForm(info, XSchema.TIME_INSTANT, "2022-03-13 05:00:00"));

      // a timeInstant write that landed on midnight is not the same as one asked for 05:00
      assertNotEquals(midnight, morning);
   }

   @Test
   void sliderSnapsToItsIncrement() {
      SliderVSAssemblyInfo info = new SliderVSAssemblyInfo();
      info.setDataType(XSchema.INTEGER);
      info.setMin("0");
      info.setMax("100");
      info.setIncrement("20");
      info.setSnap(true);

      assertEquals("40", stored(InputValueService.storedForm(info, XSchema.INTEGER, 43)));
   }

   @Test
   void assemblyIsLeftUntouched() {
      ComboBoxVSAssemblyInfo info = combo(XSchema.DATE);
      info.setSelectedObject(Tool.getData(XSchema.DATE, "2022-03-14"));
      String before = String.valueOf(info.getSelectedObject());

      InputValueService.storedForm(info, XSchema.DATE, "2022-03-13 05:00:00");

      assertEquals(before, String.valueOf(info.getSelectedObject()));
   }

   @Test
   void passwordInputHasNoStoredForm() {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      info.setColumnOption(new PasswordColumnOption());

      assertNull(InputValueService.storedForm(info, XSchema.STRING, "secret"));
   }

   @Test
   void emptyRequestStoresNothing() {
      ComboBoxVSAssemblyInfo info = combo(XSchema.DATE);

      assertEquals(List.of(), InputValueService.storedForm(info, XSchema.DATE, null));
   }
}
