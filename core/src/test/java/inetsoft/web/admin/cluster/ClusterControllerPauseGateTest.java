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
package inetsoft.web.admin.cluster;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.cluster.ServerClusterClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The raw EM pause/resume endpoints refuse with 403 while {@code cluster.pause.enabled} is off
 * (#76342). Bug #78676 rewrote the admin-chat refusal message, the plugin tool description and the
 * property doc to state this, so it is exercised here end to end through the real
 * {@link ClusterService} and {@link AdminExceptionHandler}.
 */
@Tag("core")
class ClusterControllerPauseGateTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedConstruction<ServerClusterClient> clientConstruction;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("cluster.pause.enabled", "false"))
         .thenReturn("false");
      // pauseServers/resumeServers create their own client; a refused call must not reach it
      clientConstruction = mockConstruction(ServerClusterClient.class);

      ClusterService service = new ClusterService(mock(ServerClusterClient.class));
      ClusterController controller = new ClusterController(
         service, mock(MonitoringDataService.class), mock(SecurityEngine.class));
      mvc = MockMvcBuilders.standaloneSetup(controller)
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
   }

   @AfterEach
   void tearDown() {
      clientConstruction.close();
      sreeEnvStatic.close();
   }

   @ParameterizedTest
   @ValueSource(strings = { "pause-server", "resume-server" })
   void pauseAndResumeAreForbiddenWhilePauseIsDisabled(String endpoint) throws Exception {
      mvc.perform(post("/api/em/monitoring/cluster/" + endpoint)
                     .contentType(MediaType.APPLICATION_JSON)
                     .accept(MediaType.APPLICATION_JSON)
                     .content("[\"node1\"]"))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.type").value("SecurityException"));

      // the gate refuses before pauseServers/resumeServers create a client or touch any node
      assertTrue(clientConstruction.constructed().isEmpty());
   }
}
