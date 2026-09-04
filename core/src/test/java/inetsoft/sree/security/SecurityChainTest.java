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
package inetsoft.sree.security;

import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers a TOCTOU race between {@code DataSpace.exists()} and {@code DataSpace.getInputStream()}
 * in {@code SecurityChain.loadConfiguration()}: if the config file is removed or rewritten
 * between the two calls, {@code getInputStream()} returns {@code null} (its documented
 * "soft miss" contract for a missing file) rather than throwing. Before the fix, that null was
 * passed straight into {@code ObjectMapper.readTree()}, which threw an uncaught
 * {@code IllegalArgumentException} that propagated out of the {@code AuthenticationChain}/
 * {@code AuthorizationChain} constructor entirely.
 */
@Tag("core")
class SecurityChainTest {
   @Test
   void authenticationChain_configReadRacesWithConcurrentRemoval_doesNotThrow() throws Exception {
      DataSpace space = mock(DataSpace.class);
      when(space.exists(null, "authc-chain.json")).thenReturn(true);
      when(space.getLastModified(null, "authc-chain.json")).thenReturn(100L);
      when(space.getInputStream(null, "authc-chain.json")).thenReturn(null);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         ds.when(DataSpace::getDataSpace).thenReturn(space);

         AuthenticationChain chain = assertDoesNotThrow(() -> {
            return new AuthenticationChain();
         });

         assertTrue(chain.getProviders().isEmpty());
      }
   }

   @Test
   void authorizationChain_configReadRacesWithConcurrentRemoval_doesNotThrow() throws Exception {
      DataSpace space = mock(DataSpace.class);
      when(space.exists(null, "authz-chain.json")).thenReturn(true);
      when(space.getLastModified(null, "authz-chain.json")).thenReturn(100L);
      when(space.getInputStream(null, "authz-chain.json")).thenReturn((InputStream) null);

      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         ds.when(DataSpace::getDataSpace).thenReturn(space);

         AuthorizationChain chain = assertDoesNotThrow(() -> {
            return new AuthorizationChain();
         });

         assertTrue(chain.getProviders().isEmpty());
      }
   }
}
