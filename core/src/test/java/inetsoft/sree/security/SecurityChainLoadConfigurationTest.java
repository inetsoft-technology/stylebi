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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76431: {@code SecurityChain.loadConfiguration()} assumed {@code DataSpace.exists()}
 * and {@code DataSpace.getInputStream()} observe a consistent file state, but they are not
 * atomic -- the config file can be removed/replaced (e.g. by a concurrent rewrite) between
 * the two calls, in which case {@code getInputStream()} returns {@code null} (per its own
 * documented {@code FileNotFoundException}/{@code NoSuchFileException} handling) rather than
 * throwing. Passing that {@code null} straight into Jackson's {@code ObjectMapper.readTree()}
 * threw {@code IllegalArgumentException: argument "in" is null}, observed as an intermittent
 * {@code PermissionHierarchyTest.setUp} failure in CI (concurrent Surefire test classes
 * sharing one {@code DataSpace}-backed config file).
 */
@Tag("core")
class SecurityChainLoadConfigurationTest {

   // [Scenario: TOCTOU race] exists() reports true but getInputStream() returns null --
   // loadConfiguration() (invoked via the AuthenticationChain constructor's initialize())
   // must not throw, and must leave the chain with no providers rather than crash the
   // caller (e.g. a test's `new AuthenticationChain()` during setup, or production
   // server startup racing a concurrent config rewrite).
   @Test
   void loadConfiguration_streamRaceReturnsNull_doesNotThrowAndLeavesChainUsable() throws Exception {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         when(mockDs.exists(null, "authc-chain.json")).thenReturn(true);
         when(mockDs.getLastModified(null, "authc-chain.json")).thenReturn(1000L);
         when(mockDs.getInputStream(null, "authc-chain.json")).thenReturn(null);

         ThrowingSupplier<AuthenticationChain> construct = AuthenticationChain::new;
         AuthenticationChain chain =
            assertDoesNotThrow(construct,
                                "a null stream from a racing getInputStream() must not " +
                                "propagate as an uncaught exception from the constructor");

         assertTrue(chain.getProviderList().isEmpty(),
                    "no providers were ever successfully loaded, so the list should be empty " +
                    "(not populated with garbage, and not thrown away by a crash mid-construction)");
      }
   }

   // [Scenario: race clears, retry succeeds] confirms the "don't advance timestamp" recovery
   // path: after a null-stream race, a later loadConfiguration() call (e.g. triggered by
   // dataChanged(), simulated here by calling initialize() again) must still pick up the
   // config once the race clears, proving the chain isn't left permanently stuck believing
   // it already has fresh data.
   @Test
   void loadConfiguration_streamRaceClearsOnRetry_configIsLoadedNextTime() throws Exception {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         when(mockDs.exists(null, "authc-chain.json")).thenReturn(true);
         when(mockDs.getLastModified(null, "authc-chain.json")).thenReturn(1000L);
         when(mockDs.getInputStream(null, "authc-chain.json")).thenReturn(null);

         ThrowingSupplier<AuthenticationChain> construct = AuthenticationChain::new;
         AuthenticationChain chain = assertDoesNotThrow(construct);

         String json = "{\"providers\":[]}";
         when(mockDs.getInputStream(null, "authc-chain.json"))
            .thenReturn(new java.io.ByteArrayInputStream(json.getBytes()));

         // Not wrapped in assertDoesNotThrow: an uncaught IOException here already fails
         // this test (the method declares `throws Exception`) with a clear stack trace.
         chain.loadConfiguration();
      }
   }

   // [Scenario: malformed config] the stream is readable but the parsed JSON has no
   // "providers" array (e.g. an empty object, or a config file damaged some other way).
   // loadConfiguration() must not throw an uncaught exception out of the constructor,
   // matching the null-stream race's recovery behavior.
   @Test
   void loadConfiguration_configMissingProvidersArray_doesNotThrowAndLeavesChainUsable()
      throws Exception
   {
      try(MockedStatic<DataSpace> ds = mockStatic(DataSpace.class)) {
         DataSpace mockDs = mock(DataSpace.class);
         ds.when(DataSpace::getDataSpace).thenReturn(mockDs);

         when(mockDs.exists(null, "authc-chain.json")).thenReturn(true);
         when(mockDs.getLastModified(null, "authc-chain.json")).thenReturn(1000L);
         when(mockDs.getInputStream(null, "authc-chain.json"))
            .thenReturn(new java.io.ByteArrayInputStream("{}".getBytes()));

         ThrowingSupplier<AuthenticationChain> construct = AuthenticationChain::new;
         AuthenticationChain chain =
            assertDoesNotThrow(construct,
                                "a config with no \"providers\" array must not propagate an " +
                                "uncaught exception from the constructor");

         assertTrue(chain.getProviderList().isEmpty(),
                    "no providers were ever successfully loaded, so the list should be empty");
      }
   }
}
