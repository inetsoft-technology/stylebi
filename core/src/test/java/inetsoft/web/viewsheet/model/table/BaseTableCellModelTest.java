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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.viewsheet.model.VSFormatModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * hyperlink.indicator's underline polarity must agree across BaseTableCellModel,
 * VSTableLens, and GraphUtil for any value, not just the two shipped ones ("true"/"false") --
 * BaseTableCellModel used to test !"false".equals(...) while the others test "true".equals(...),
 * which only agree for those two literal strings.
 */
@Tag("core")
class BaseTableCellModelTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @AfterEach
   void tearDown() {
      if(sreeEnvStatic != null) {
         sreeEnvStatic.close();
      }
   }

   @Test
   void underline_true_turnsUnderlineOn() throws Exception {
      mockHyperlinkIndicator("true");

      assertTrue(newRegularCell().isUnderline());
      assertTrue(newFormCell().isUnderline());
   }

   @Test
   void underline_false_turnsUnderlineOff() throws Exception {
      mockHyperlinkIndicator("false");

      assertFalse(newRegularCell().isUnderline());
      assertFalse(newFormCell().isUnderline());
   }

   @Test
   void underline_nonStandardValue_turnsUnderlineOff() throws Exception {
      mockHyperlinkIndicator("1");

      // Fails before the fix: BaseTableCellModel kept underline ON for anything that
      // wasn't the literal string "false".
      assertFalse(newRegularCell().isUnderline());
      assertFalse(newFormCell().isUnderline());
   }

   @Test
   void underline_nonStandardValue_matchesVSTableLens() throws Exception {
      mockHyperlinkIndicator("1");

      assertEquals(vsTableLensUnderline(), newRegularCell().isUnderline(),
                   "BaseTableCellModel and VSTableLens must agree on underline for a "
                   + "non-standard hyperlink.indicator value");
   }

   private void mockHyperlinkIndicator(String value) {
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("hyperlink.indicator")).thenReturn(value);
   }

   private static BaseTableCellModel newRegularCell() throws Exception {
      Constructor<BaseTableCellModel> ctor = BaseTableCellModel.class.getDeclaredConstructor(
         Object.class, Object.class, int.class, int.class, VSFormatModel.class, String.class,
         HyperlinkModel[].class, TableDataPath.class, String.class, boolean.class, int.class,
         int.class);
      ctor.setAccessible(true);
      return ctor.newInstance("data", "label", 0, 0, null, null, null, null, null, false, 0, 0);
   }

   private static BaseTableCellModel newFormCell() throws Exception {
      Constructor<BaseTableCellModel> ctor = BaseTableCellModel.class.getDeclaredConstructor(
         Object.class, Object.class, int.class, int.class, VSFormatModel.class,
         HyperlinkModel[].class, TableDataPath.class, String.class, String[].class, String.class,
         boolean.class);
      ctor.setAccessible(true);
      return ctor.newInstance("data", "label", 0, 0, null, null, null, null, null, null, false);
   }

   private static boolean vsTableLensUnderline() throws Exception {
      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][]{{"a"}}));
      Field field = VSTableLens.class.getDeclaredField("underline");
      field.setAccessible(true);
      return field.getBoolean(lens);
   }
}
