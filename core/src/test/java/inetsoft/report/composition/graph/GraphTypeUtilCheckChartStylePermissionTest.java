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
package inetsoft.report.composition.graph;

/*
 * Test strategy
 *
 * Bug #76953: GraphTypeUtil.checkChartStylePermission(String, ResourceType, ResourceAction,
 * Principal) caught SecurityEngine.checkPermission()'s SecurityException (thrown only when the
 * principal is not logged in) the same way as any other Exception and returned false, making a
 * stale/expired session look identical to a genuine permission denial.
 *
 * Round 2 (review finding 1): the initial fix made checkChartStylePermission always propagate
 * that condition as a MessageException, but the method's ~20+ non-interactive callers (report
 * generation, the script engine, EM admin/monitoring, chart binding processors, reached through
 * GraphTypeUtil.getAvailableAutoChartType()/AbstractChartInfo.updateChartType()) have no
 * translation layer for an uncaught exception. Propagation is now gated behind the per-thread
 * GraphTypeUtil.setStrictLoginCheck() flag, opted into only by the interactive
 * STOMP/controller call sites that can show the resulting message to the user.
 *
 * Coverage (4 cases):
 *  [not logged in, strict]    SecurityEngine.checkPermission throws
 *                             inetsoft.sree.security.SecurityException, strict mode enabled
 *                             -> must propagate as MessageException (WARN, no stack dump, ERROR
 *                                level), not be swallowed into a false return.
 *  [not logged in, default]  same exception, strict mode NOT enabled (the default, used by every
 *                             caller that never calls setStrictLoginCheck(true))
 *                             -> must be swallowed into a false return, same as before the fix.
 *  [granted]                 checkPermission returns true -> pass-through true.
 *  [denied]                  checkPermission returns false -> pass-through false (genuine
 *                             no-permission, unaffected by the fix).
 *
 * SecurityEngine.getSecurity() is a static singleton accessor
 * (ConfigurationContext.getSpringBean); intercepted with Mockito.mockStatic(), matching the
 * existing precedent in SecurityConfigControllerTest.
 */

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
class GraphTypeUtilCheckChartStylePermissionTest {

   private SecurityEngine securityEngine;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private Principal principal;

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      securityEngineStatic = mockStatic(SecurityEngine.class);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      principal = mock(Principal.class);
   }

   @AfterEach
   void tearDown() {
      securityEngineStatic.close();
      // strict mode is a per-thread flag; make sure a failed assertion never leaks it into
      // another test running on the same thread.
      GraphTypeUtil.setStrictLoginCheck(false);
   }

   @Test
   void notLoggedIn_strictMode_propagatesAsMessageException() throws Exception {
      inetsoft.sree.security.SecurityException notLoggedIn =
         new inetsoft.sree.security.SecurityException("admin(host-org) did not log in.");
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenThrow(notLoggedIn);

      GraphTypeUtil.setStrictLoginCheck(true);

      MessageException thrown = assertThrows(MessageException.class, () ->
         GraphTypeUtil.checkChartStylePermission(
            "Chart Type/Bar", ResourceType.CHART_TYPE, ResourceAction.READ, principal));

      assertEquals("admin(host-org) did not log in.", thrown.getMessage());
      assertFalse(thrown.isDumpStack());
      assertEquals(ConfirmException.ERROR, thrown.getWarningLevel());
   }

   @Test
   void notLoggedIn_defaultMode_returnsFalse() throws Exception {
      inetsoft.sree.security.SecurityException notLoggedIn =
         new inetsoft.sree.security.SecurityException("admin(host-org) did not log in.");
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenThrow(notLoggedIn);

      // strict mode not enabled -- this is the contract every non-interactive caller (report
      // generation, script engine, EM admin/monitoring, chart binding processors) relies on.
      assertFalse(GraphTypeUtil.checkChartStylePermission(
         "Chart Type/Bar", ResourceType.CHART_TYPE, ResourceAction.READ, principal));
   }

   @Test
   void granted_returnsTrue() throws Exception {
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      assertTrue(GraphTypeUtil.checkChartStylePermission(
         "Chart Type/Bar", ResourceType.CHART_TYPE, ResourceAction.READ, principal));
   }

   @Test
   void denied_returnsFalse() throws Exception {
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      assertFalse(GraphTypeUtil.checkChartStylePermission(
         "Chart Type/Bar", ResourceType.CHART_TYPE, ResourceAction.READ, principal));
   }
}
