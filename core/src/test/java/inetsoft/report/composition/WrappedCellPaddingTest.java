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
package inetsoft.report.composition;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wrapped row's height is estimated from how many lines its text takes at the column's width.
 * The browser and every pixel exporter inset the text by the assembly's cell padding, so the
 * estimate has to measure the same narrower box or the last line is clipped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WrappedCellPaddingTest {
   @Test
   void aCellPaddingNarrowsTheWrappedTextBox() {
      int unpadded = wrappedHeight(null);
      int padded = wrappedHeight(new Insets(0, 30, 0, 30));

      assertTrue(padded > unpadded, "padded " + padded + " vs unpadded " + unpadded);
   }

   @Test
   void onlyTheHorizontalEdgesNarrowTheTextBox() {
      // against a zero padding, not none: a defined padding replaces the stylesheet's 2px gutter
      assertEquals(wrappedHeight(new Insets(0, 0, 0, 0)), wrappedHeight(new Insets(6, 0, 6, 0)));
   }

   private int wrappedHeight(Insets padding) {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "header" },
         { "one two three four five six seven eight nine ten" }
      });
      data.setLineWrap(1, 0, true);
      data.setFont(1, 0, new Font("Dialog", Font.PLAIN, 12));

      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setViewsheet(new Viewsheet());
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(80, 400));
      info.setCellPadding(padding, CompositeValue.Type.DEFAULT);

      VSTableLens lens = new VSTableLens(data);
      lens.initTableGrid(info);
      return lens.getWrappedHeight(1, true);
   }
}
