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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Companion to {@link TableCellPaddingSeedTest} and to
 * {@code inetsoft.web.composer.vs.dialog.TableViewPropertyDialogServiceTest}'s
 * {@code unmarkedTableWithUntouchedPaneStaysUnopinionated} case, which proves the apply guard
 * leaves an unmarked table with an untouched pane at {@code isUserCellPadding() == false} and
 * {@code getCellPadding() == null}. seedChromeDefaults is protected and only reachable from this
 * package, which is why that regression is split across two classes rather than chained in one.
 *
 * Before the fix, that guard compared the edited 0/0/0/0 against a null stored padding with
 * Insets.equals(), which is always false, so every apply on an unmarked table pinned the USER
 * tier - even one where the author never touched a padding field. Once pinned, seedChromeDefaults
 * never seeds again (TableDataVSAssemblyInfo.seedChromeDefaults only seeds when
 * !isUserCellPadding()), so a later-modernized table stayed at zero instead of the density value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingApplyRegressionTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void unmarkedTableLeftUnopinionatedByApplyStillSeedsDensityOnceMarked() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");

      // the exact state the apply guard's fix leaves an unmarked table in after an untouched,
      // all-zero pane is saved (see TableViewPropertyDialogServiceTest)
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      assertFalse(info.isUserCellPadding(), "precondition: no user opinion recorded");

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(6, 8, 6, 8), info.getCellPadding());
   }
}
