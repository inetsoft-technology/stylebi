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
 * Coverage (3 cases):
 *  [not logged in]  SecurityEngine.checkPermission throws inetsoft.sree.security.SecurityException
 *                    -> must now propagate as MessageException (WARN, no stack dump, ERROR level),
 *                       not be swallowed into a false return.
 *  [granted]        checkPermission returns true -> pass-through true.
 *  [denied]         checkPermission returns false -> pass-through false (genuine no-permission,
 *                    unaffected by the fix).
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
   }

   @Test
   void notLoggedIn_propagatesAsMessageException() throws Exception {
      inetsoft.sree.security.SecurityException notLoggedIn =
         new inetsoft.sree.security.SecurityException("admin(host-org) did not log in.");
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenThrow(notLoggedIn);

      MessageException thrown = assertThrows(MessageException.class, () ->
         GraphTypeUtil.checkChartStylePermission(
            "Chart Type/Bar", ResourceType.CHART_TYPE, ResourceAction.READ, principal));

      assertEquals("admin(host-org) did not log in.", thrown.getMessage());
      assertFalse(thrown.isDumpStack());
      assertEquals(ConfirmException.ERROR, thrown.getWarningLevel());
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
