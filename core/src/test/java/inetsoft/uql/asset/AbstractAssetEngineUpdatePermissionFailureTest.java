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
package inetsoft.uql.asset;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77798: the permission writers throw when the storage write fails. The permission move in
 * AbstractAssetEngine.updatePermission runs inside the folder move (changeFolder0), so it stays
 * best-effort: a failed write must not stop the rest of the tree from being moved.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AbstractAssetEngineUpdatePermissionFailureTest {
   @Test
   void permissionWritesFail_moveIsNotInterrupted() throws Exception {
      AbstractAssetEngine engine = mock(AbstractAssetEngine.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      AssetEntry oentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "a", null);
      AssetEntry nentry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "b", null);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.getPermission(ResourceType.ASSET, "a")).thenReturn(new Permission());
      doThrow(new MessageException("simulated, may not have been saved"))
         .when(security).removePermission(any(ResourceType.class), anyString());
      doThrow(new MessageException("simulated, may not have been saved"))
         .when(security).setPermission(any(ResourceType.class), anyString(), any());

      Method method = AbstractAssetEngine.class.getDeclaredMethod(
         "updatePermission", AssetEntry.class, AssetEntry.class);
      method.setAccessible(true);

      try(MockedStatic<SecurityEngine> statics = mockStatic(SecurityEngine.class)) {
         statics.when(SecurityEngine::getSecurity).thenReturn(security);
         assertDoesNotThrow(() -> {
            try {
               method.invoke(engine, oentry, nentry);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }
         });
      }

      // both steps are still attempted, as before the writers threw
      verify(security).removePermission(ResourceType.ASSET, "a");
      verify(security).setPermission(eq(ResourceType.ASSET), eq("b"), any());
   }
}
