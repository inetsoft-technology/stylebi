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
package inetsoft.report.io.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79774: the height of an auto-size text ignored its padding, so a padded text
 * was sized as if it had none and its last wrapped line was clipped.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CoordinateHelperAutoSizeTextTest {
   private static final String TEXT =
      "The quick brown fox jumps over the lazy dog while the dashboard keeps loading its "
      + "data. Refresh the page if the numbers do not update after the latest completed load.";

   private static TextVSAssemblyInfo text(Insets padding) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setText(TEXT);
      info.setAutoSize(true);
      info.getFormat().getUserDefinedFormat().setWrapping(true);
      info.getFormat().getUserDefinedFormat().setFont(new Font("Dialog", Font.PLAIN, 12));
      info.setPadding(padding);
      return info;
   }

   private static int fit(TextVSAssemblyInfo info, double scale) {
      Dimension size = new Dimension(240, 60);
      CoordinateHelper.fixTextSize(size, new Point(0, 0), info, scale);
      return size.height;
   }

   @Test
   void paddingAddsToHeight() {
      int none = fit(text(new Insets(0, 0, 0, 0)), 1.0);
      int padded = fit(text(new Insets(10, 0, 10, 0)), 1.0);
      assertEquals(none + 20, padded);
   }

   @Test
   void horizontalPaddingNarrowsWrapWidth() {
      int none = fit(text(new Insets(0, 0, 0, 0)), 1.0);
      int padded = fit(text(new Insets(0, 60, 0, 60)), 1.0);
      assertTrue(padded > none, "padded " + padded + " not taller than " + none);
   }

   @Test
   void heightIsDerivedNotKept() {
      TextVSAssemblyInfo info = text(new Insets(10, 10, 10, 10));
      Dimension size = new Dimension(240, 400);
      CoordinateHelper.fixTextSize(size, new Point(0, 0), info);
      assertEquals(fit(info, 1.0), size.height);
   }

   @Test
   void idempotent() {
      TextVSAssemblyInfo info = text(new Insets(10, 10, 10, 10));
      Dimension size = new Dimension(240, 60);
      CoordinateHelper.fixTextSize(size, new Point(0, 0), info);
      int first = size.height;
      CoordinateHelper.fixTextSize(size, new Point(0, 0), info);
      assertEquals(first, size.height);
   }

   @Test
   void paddingFollowsOutputScale() {
      int none = fit(text(new Insets(0, 0, 0, 0)), 2.0);
      int padded = fit(text(new Insets(10, 0, 10, 0)), 2.0);
      assertEquals(none + 40, padded);
   }

   @Test
   void notAutoSizeKeepsHeight() {
      TextVSAssemblyInfo info = text(new Insets(10, 10, 10, 10));
      info.setAutoSize(false);
      assertEquals(60, fit(info, 1.0));
   }
}
