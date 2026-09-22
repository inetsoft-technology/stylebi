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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #76920's fix #1: {@link ViewsheetSandbox#processOnInit()} used to swap
 * the {@code ThreadContext} principal to a host-org principal before running the viewsheet's
 * onInit script and follow-up association/scope-save logic, then restore it -- but the restore
 * was not in a {@code try/finally}, so an exception from that follow-up logic left the swapped
 * principal on the thread. This forces exactly that failure by overriding {@code reset()} (the
 * follow-up call made when a script has set a selection's values, see the {@code scriptSelected}
 * branch in {@code processOnInit()}) to throw, and asserts the original principal is restored
 * even though the exception propagates.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxProcessOnInitTest {
   private static final String HOST_ORG_ID = Organization.getDefaultOrganizationID();
   private static final String OTHER_ORG_ID = "acme_id";

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void processOnInit_restoresPrincipalEvenWhenFollowUpLogicThrows() throws Exception {
      Worksheet ws = new Worksheet();
      Viewsheet vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // ViewsheetSandboxTemporaryBoxTest/RefreshVariableTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      // The viewsheet itself is host-org-owned, so the "expose default org to all" swap
      // condition in processOnInit() can fire for a non-host-org opener.
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxProcessOnInitTest", null, HOST_ORG_ID);
      vs.setEntry(entry);

      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnInit("var x = 1;");

      // An AbstractSelectionVSAssembly with script-selected values makes processOnInit() take
      // the "scriptSelected" branch and call reset() -- the follow-up logic this fix protects.
      SelectionListVSAssembly selection = new SelectionListVSAssembly(vs, "SelectionList1");
      selection.setScriptSelectedValues(new Object[] { "a" });
      vs.addAssembly(selection);

      // The opening user is in a different, non-host-org organization.
      IdentityID openerId = new IdentityID("acmeUser", OTHER_ORG_ID);
      XPrincipal opener = new XPrincipal(openerId, new IdentityID[0], new String[0], OTHER_ORG_ID);

      RuntimeException forcedFailure = new RuntimeException("forced failure from reset()");

      ViewsheetSandbox sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, opener,
                                                       false, entry)
      {
         @Override
         public void reset(ChangedAssemblyList clist) {
            throw forcedFailure;
         }
      };

      // ThreadContext.getContextPrincipal() is what processOnInit() captures as "oPrincipal" and
      // must restore. Set it to the opener (a non-host-org principal), matching what
      // MessageScopeInterceptor does for a real STOMP-driven open.
      ThreadContext.setContextPrincipal(opener);

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         // Forces processOnInit()'s swap condition: isDefaultVSGloballyVisible(user) is true,
         // the viewsheet's org (host-org) differs from the opener's current org (acme_id), and
         // the viewsheet's org equals the default org -- so it swaps in a host-org principal
         // before running onInit, then must restore "opener" afterward, even on exception.
         mocked.when(() -> SUtil.isDefaultVSGloballyVisible(Mockito.any())).thenReturn(true);

         RuntimeException thrown = assertThrows(RuntimeException.class, sandbox::processOnInit,
            "the forced reset() failure must propagate out of processOnInit() -- processOnInit() " +
            "must not swallow it");
         assertSame(forcedFailure, thrown);
      }

      Principal restored = ThreadContext.getContextPrincipal();
      assertSame(opener, restored,
         "processOnInit() swapped the ThreadContext principal before running onInit/follow-up " +
         "logic; when that logic throws, the original principal must still be restored (the bug " +
         "this test targets: the restore used to run only on the non-throwing path)");
   }
}
