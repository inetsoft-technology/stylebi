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
import inetsoft.util.Catalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorksheetEngine.getLockOwnerMessage (Bug #76998): the lock owner is an identity key
 * and must be shown by name, and the viewer's own identity in another session gets the self
 * message instead of an ambiguous "locked by &lt;your name&gt;".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("core")
@SreeHome()
class WorksheetEngineLockMessageTest {
   private static final String KEY = "common.AssetLockBy";
   private static final String SELF_KEY = "common.AssetLockBySelf";

   @Test
   void otherUserIsShownByName() {
      Principal viewer = () -> "alice~;~host-org";
      String message = WorksheetEngine.getLockOwnerMessage(KEY, SELF_KEY, "bob~;~host-org", viewer);

      assertEquals(Catalog.getCatalog().getString(KEY, "bob"), message);
      assertFalse(message.contains("~;~"));
   }

   @Test
   void sameUserInAnotherSessionGetsSelfMessage() {
      Principal viewer = () -> "alice~;~host-org";
      String message = WorksheetEngine.getLockOwnerMessage(KEY, SELF_KEY, "alice~;~host-org", viewer);

      assertEquals(Catalog.getCatalog().getString(SELF_KEY), message);
   }

   @Test
   void sameNameInDifferentOrgIsAnotherUser() {
      Principal viewer = () -> "alice~;~orgA";
      String message = WorksheetEngine.getLockOwnerMessage(KEY, SELF_KEY, "alice~;~orgB", viewer);

      assertEquals(Catalog.getCatalog().getString(KEY, "alice"), message);
   }

   @Test
   void nullViewerIsShownByName() {
      String message = WorksheetEngine.getLockOwnerMessage(KEY, SELF_KEY, "bob~;~host-org", null);

      assertEquals(Catalog.getCatalog().getString(KEY, "bob"), message);
   }
}
