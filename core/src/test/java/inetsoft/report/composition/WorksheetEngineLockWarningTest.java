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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorksheetEngine.isLockWarningNeeded (Bug #81480): the open-time "changes will not be
 * saved" lock warning applies to a design mode open only, not to the viewer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("core")
@SreeHome()
class WorksheetEngineLockWarningTest {
   private static final int DESIGN = RuntimeViewsheet.VIEWSHEET_DESIGN_MODE;
   private static final int RUNTIME = RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE;

   @Test
   void designOpenLockedByAnotherUserWarns() {
      assertTrue(WorksheetEngine.isLockWarningNeeded("admin", "bob", DESIGN));
   }

   @Test
   void viewerOpenLockedByAnotherUserDoesNotWarn() {
      assertFalse(WorksheetEngine.isLockWarningNeeded("admin", "anonymous", RUNTIME));
   }

   @Test
   void noWarningWhenNotLockedOrLockedBySelf() {
      assertFalse(WorksheetEngine.isLockWarningNeeded(null, "bob", DESIGN));
      assertFalse(WorksheetEngine.isLockWarningNeeded("bob", "bob", DESIGN));
   }
}
