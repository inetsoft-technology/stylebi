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
package inetsoft.web.composer;

import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Bug #82430: the Composer asset tree resolves permission identity once per request. */
@Tag("core")
class AssetTreeControllerPermissionScopeTest {
   @AfterEach
   void clear() {
      PermissionRequestScope.clear();
   }

   @Test
   void getNodes_opensPermissionRequestScopeAndClosesIt() throws Exception {
      AssetRepository repo = mock(AssetRepository.class);
      AtomicBoolean openDuring = new AtomicBoolean();
      when(repo.checkPermission(any(), any(ResourceType.class), anyString(), any()))
         .thenAnswer(inv -> {
            openDuring.set(PermissionRequestScope.isOpen());
            throw new IllegalStateException("stop");
         });
      AssetTreeController controller =
         new AssetTreeController(repo, null, mock(SecurityEngine.class), null, null, null);

      assertThrows(IllegalStateException.class, () -> controller.getNodes(
         false, false, true, false, false, false, false, false, false, true, null,
         mock(Principal.class)));

      assertTrue(openDuring.get());
      assertFalse(PermissionRequestScope.isOpen());
   }
}
