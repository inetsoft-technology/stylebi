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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for {@link TableDataVSAssemblyInfo#clone(boolean)} omitting
 * {@code cellPadding}: a clone shared the original's {@code CompositeValue} instance, so editing
 * the clone's padding - or undo, which clones the assembly to snapshot it - mutated the live
 * assembly's padding too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingCloneTest {
   @Test
   void deepCloneDoesNotShareCellPaddingWithTheOriginal() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(new Insets(1, 2, 3, 4), CompositeValue.Type.USER);

      TableVSAssemblyInfo clone = (TableVSAssemblyInfo) info.clone(false);
      clone.setCellPadding(new Insets(9, 9, 9, 9), CompositeValue.Type.USER);

      assertEquals(new Insets(1, 2, 3, 4), info.getCellPadding(), "original must be unaffected by the clone's edit");
      assertEquals(new Insets(9, 9, 9, 9), clone.getCellPadding());
   }
}
