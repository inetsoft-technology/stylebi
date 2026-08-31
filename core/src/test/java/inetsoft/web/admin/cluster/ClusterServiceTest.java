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
package inetsoft.web.admin.cluster;

/*
 * Test strategy
 *
 * Bug #76342: cluster.pause.enabled gated only a UI button, not the server-side
 * pauseServers/resumeServers methods, so a direct REST call could pause/resume a cluster
 * even when the property was unset/"false" (the shipped default).
 *
 * Behavioral guarantees covered:
 *
 * [G1] pauseServers throws SecurityException and never calls ServerClusterClient.pauseServer
 *      when cluster.pause.enabled is "false" (the default).
 * [G2] pauseServers proceeds and still calls ServerClusterClient.pauseServer when
 *      cluster.pause.enabled is "true" (no regression to the enabled path).
 * [G3] resumeServers throws SecurityException and never calls ServerClusterClient.resumeServer
 *      when cluster.pause.enabled is "false" (the default).
 * [G4] resumeServers proceeds and still calls ServerClusterClient.resumeServer when
 *      cluster.pause.enabled is "true" (no regression to the enabled path).
 *
 * Bug #76343: pauseServers/resumeServers were void, discarding each server's per-call
 * success/failure, so a caller could never tell whether a pause/resume actually took effect.
 *
 * [G5] pauseServers returns a map with false for a server whose pauseServer(...) call fails
 *      and true for one that succeeds.
 * [G6] pauseServers reports true (not omitted) for a server already paused, without calling
 *      pauseServer for it.
 * [G7] resumeServers returns a map with false for a server whose resumeServer(...) call fails
 *      and true for one that succeeds.
 * [G8] resumeServers reports true (not omitted) for a server already resumed, without calling
 *      resumeServer for it.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
class ClusterServiceTest {

   private ClusterService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      service = new ClusterService(mock(ServerClusterClient.class));
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   // [G1] pause disabled by default → refuse, never call pauseServer
   @Test
   void pauseServers_propertyFalse_throwsAndNeverPauses() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("false");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(false);
                when(mockClient.getStatus(anyString())).thenReturn(status);
             }))
      {
         assertThrows(SecurityException.class,
            () -> service.pauseServers(new String[]{ "node1" }));

         assertTrue(construction.constructed().isEmpty());
      }
   }

   // [G2] pause enabled → proceeds, still calls pauseServer (no regression)
   @Test
   void pauseServers_propertyTrue_pausesServer() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(false);
                when(mockClient.getStatus(anyString())).thenReturn(status);
                when(mockClient.pauseServer(anyString())).thenReturn(true);
             }))
      {
         service.pauseServers(new String[]{ "node1" });

         verify(construction.constructed().get(0)).pauseServer("node1");
      }
   }

   // [G3] resume disabled by default → refuse, never call resumeServer
   @Test
   void resumeServers_propertyFalse_throwsAndNeverResumes() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("false");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(true);
                when(mockClient.getStatus(anyString())).thenReturn(status);
             }))
      {
         assertThrows(SecurityException.class,
            () -> service.resumeServers(new String[]{ "node1" }));

         assertTrue(construction.constructed().isEmpty());
      }
   }

   // [G4] resume enabled → proceeds, still calls resumeServer (no regression)
   @Test
   void resumeServers_propertyTrue_resumesServer() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(true);
                when(mockClient.getStatus(anyString())).thenReturn(status);
                when(mockClient.resumeServer(anyString())).thenReturn(true);
             }))
      {
         service.resumeServers(new String[]{ "node1" });

         verify(construction.constructed().get(0)).resumeServer("node1");
      }
   }

   // [G5] pauseServers reports false for a server the client fails to pause, true for one it pauses
   @Test
   void pauseServers_returnsFalseForFailedServerAndTrueForSucceeded() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(false);
                when(mockClient.getStatus(anyString())).thenReturn(status);
                when(mockClient.pauseServer("node1")).thenReturn(true);
                when(mockClient.pauseServer("node2")).thenReturn(false);
             }))
      {
         Map<String, Boolean> result = service.pauseServers(new String[]{ "node1", "node2" });

         assertEquals(Map.of("node1", true, "node2", false), result);
      }
   }

   // [G6] pauseServers reports true (not omitted) for an already-paused server, without calling pauseServer
   @Test
   void pauseServers_reportsTrueForAlreadyPausedServerWithoutCallingClient() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(true);
                when(mockClient.getStatus(anyString())).thenReturn(status);
             }))
      {
         Map<String, Boolean> result = service.pauseServers(new String[]{ "node1" });

         assertEquals(Map.of("node1", true), result);
         verify(construction.constructed().get(0), never()).pauseServer(anyString());
      }
   }

   // [G7] resumeServers reports false for a server the client fails to resume, true for one it resumes
   @Test
   void resumeServers_returnsFalseForFailedServerAndTrueForSucceeded() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(true);
                when(mockClient.getStatus(anyString())).thenReturn(status);
                when(mockClient.resumeServer("node1")).thenReturn(true);
                when(mockClient.resumeServer("node2")).thenReturn(false);
             }))
      {
         Map<String, Boolean> result = service.resumeServers(new String[]{ "node1", "node2" });

         assertEquals(Map.of("node1", true, "node2", false), result);
      }
   }

   // [G8] resumeServers reports true (not omitted) for an already-resumed server, without calling resumeServer
   @Test
   void resumeServers_reportsTrueForAlreadyResumedServerWithoutCallingClient() throws SecurityException {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("true");

      try(MockedConstruction<ServerClusterClient> construction =
             mockConstruction(ServerClusterClient.class, (mockClient, context) -> {
                ServerClusterStatus status = mock(ServerClusterStatus.class);
                when(status.isPaused()).thenReturn(false);
                when(mockClient.getStatus(anyString())).thenReturn(status);
             }))
      {
         Map<String, Boolean> result = service.resumeServers(new String[]{ "node1" });

         assertEquals(Map.of("node1", true), result);
         verify(construction.constructed().get(0), never()).resumeServer(anyString());
      }
   }
}
