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
package inetsoft.mv;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * Bug #78390: an MV is filtered by the VPM of the identity it is built for. {@link MVManager}
 * must not serve an MV built for another identity (system user, no user, group, role, or a
 * default org MV seen from another org) to a user whose VPM gives a different query, and an
 * MV built for the user itself must win over such an MV.
 */
@Tag("core")
class MVManagerVPMIdentityTest {
   private static final String ORG = "host-org";
   private static final String WS_ID = "1^2^__NULL__^ws1^" + ORG;
   private static final String VS_ID = "1^128^__NULL__^vs1^" + ORG;
   private static final String TABLE = "T1";

   private MockedStatic<XSessionService> sessionServiceStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XUtil> xutilStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<DataSourceRegistry> registryStatic;
   private DataSourceRegistry registry;
   private Principal oldPrincipal;
   private Principal oldContextPrincipal;
   private XPrincipal viewer;
   private int defCount;

   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getPrincipal();
      oldContextPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setPrincipal(null);
      ThreadContext.setContextPrincipal(null);

      // XPrincipal's constructor asks the (Spring-managed) session service for a session ID
      sessionServiceStatic = mockStatic(XSessionService.class);
      sessionServiceStatic.when(XSessionService::getService)
         .thenReturn(mock(XSessionService.class));

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      xutilStatic = mockStatic(XUtil.class);
      xutilStatic.when(() -> XUtil.getUserGroups(any(), anyBoolean())).thenReturn(new String[0]);
      xutilStatic.when(() -> XUtil.getUserRoles(any(), anyBoolean()))
         .thenReturn(new IdentityID[0]);

      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(false);

      // a new registry per test also starts with an empty VPM check cache
      registry = mock(DataSourceRegistry.class);
      registryStatic = mockStatic(DataSourceRegistry.class);
      registryStatic.when(DataSourceRegistry::getRegistry).thenReturn(registry);

      viewer = new XPrincipal(new IdentityID("b78390u", ORG));
   }

   @AfterEach
   void tearDown() {
      registryStatic.close();
      sutilStatic.close();
      xutilStatic.close();
      orgManagerStatic.close();
      sessionServiceStatic.close();
      ThreadContext.setPrincipal(oldPrincipal);
      ThreadContext.setContextPrincipal(oldContextPrincipal);
   }

   @Test
   void systemMVIsRejectedWhenTheViewerPlanDiffers() throws Exception {
      MVDef system = wsDef(false, systemUser());
      doReturn(false).when(system).isPlanEqual(viewer);

      assertNull(findWSMV(viewer, system));
      verify(system).isPlanEqual(viewer);
   }

   @Test
   void systemMVIsUsedWhenTheViewerPlanIsEqual() throws Exception {
      MVDef system = wsDef(false, systemUser());
      doReturn(true).when(system).isPlanEqual(viewer);

      assertSame(system, findWSMV(viewer, system));
   }

   @Test
   void userMVWinsOverASystemMVListedFirst() throws Exception {
      MVDef system = wsDef(false, systemUser());
      doReturn(true).when(system).isPlanEqual(any());
      MVDef own = wsDef(false, user(viewer));

      assertSame(own, findWSMV(viewer, system, own));
      verify(system, never()).isPlanEqual(any());
      verify(own, never()).isPlanEqual(any());
   }

   @Test
   void rejectedSystemMVDoesNotShadowACompatibleRoleMV() throws Exception {
      IdentityID role = new IdentityID("Organization Administrator", ORG);
      xutilStatic.when(() -> XUtil.getUserRoles(any(), anyBoolean()))
         .thenReturn(new IdentityID[] { role });
      MVDef system = wsDef(false, systemUser());
      doReturn(false).when(system).isPlanEqual(viewer);
      MVDef roleMV = wsDef(false, new DefaultIdentity(role, Identity.ROLE));
      doReturn(true).when(roleMV).isPlanEqual(viewer);

      assertSame(roleMV, findWSMV(viewer, system, roleMV));
   }

   @Test
   void roleMVIsRejectedWhenTheViewerPlanDiffers() throws Exception {
      IdentityID role = new IdentityID("Organization Administrator", ORG);
      xutilStatic.when(() -> XUtil.getUserRoles(any(), anyBoolean()))
         .thenReturn(new IdentityID[] { role });
      MVDef roleMV = wsDef(false, new DefaultIdentity(role, Identity.ROLE));
      doReturn(false).when(roleMV).isPlanEqual(viewer);

      assertNull(findWSMV(viewer, roleMV));
   }

   @Test
   void groupMVIsRejectedWhenTheViewerPlanDiffers() throws Exception {
      xutilStatic.when(() -> XUtil.getUserGroups(any(), anyBoolean()))
         .thenReturn(new String[] { "g1" });
      MVDef group = wsDef(false, new DefaultIdentity("g1", Identity.GROUP));
      doReturn(false).when(group).isPlanEqual(viewer);

      assertNull(findWSMV(viewer, group));
      verify(group).isPlanEqual(viewer);
   }

   @Test
   void mvWithoutUsersIsCheckedLikeASystemMV() throws Exception {
      MVDef anyUser = wsDef(false, (Identity[]) null);
      doReturn(false).when(anyUser).isPlanEqual(viewer);

      assertNull(findWSMV(viewer, anyUser));
      verify(anyUser).isPlanEqual(viewer);
   }

   @Test
   void bypassVPMMVIsUsedWithoutAPlanCheck() throws Exception {
      MVDef system = wsDef(true, systemUser());

      assertSame(system, findWSMV(viewer, system));
      verify(system, never()).isPlanEqual(any());
   }

   @Test
   void defaultOrgMVSeenFromAnotherOrgIsChecked() throws Exception {
      sutilStatic.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(true);
      XPrincipal other = new XPrincipal(new IdentityID("u2", "orgB"));
      MVDef first = wsDef(false, systemUser());
      doReturn(false).when(first).isPlanEqual(other);
      MVDef second = wsDef(false, systemUser());
      doReturn(true).when(second).isPlanEqual(other);

      assertSame(second, findWSMV(other, first, second));

      doReturn(false).when(second).isPlanEqual(other);
      // the cached verdicts are keyed on the mv update time
      when(second.getLastUpdateTime()).thenReturn(2L);
      assertNull(findWSMV(other, first, second));
   }

   @Test
   void subMVIsCheckedTheSameWay() throws Exception {
      MVDef system = vsSubDef(systemUser());
      doReturn(false).when(system).isPlanEqual(viewer);

      assertNull(getSubMV(viewer, system));

      MVDef own = vsSubDef(user(viewer));
      assertSame(own, getSubMV(viewer, system, own));
      verify(own, never()).isPlanEqual(any());
   }

   @Test
   void verdictIsCachedUntilADataSourceOrModelChanges() throws Exception {
      MVDef system = wsDef(false, systemUser());
      doReturn(true).when(system).isPlanEqual(viewer);

      // the first check registers the listeners and is not cached
      assertSame(system, findWSMV(viewer, system));
      assertSame(system, findWSMV(viewer, system));
      assertSame(system, findWSMV(viewer, system));
      verify(system, times(2)).isPlanEqual(viewer);

      // a VPM is changed: the cached "equal" must not be served any more
      ArgumentCaptor<PropertyChangeListener> listener =
         ArgumentCaptor.forClass(PropertyChangeListener.class);
      verify(registry).addModifiedListener(listener.capture());
      verify(registry).addRefreshedListener(any());
      doReturn(false).when(system).isPlanEqual(viewer);
      listener.getValue().propertyChange(
         new PropertyChangeEvent(registry, "DataSourceRegistry", null, null));

      assertNull(findWSMV(viewer, system));
      verify(system, times(3)).isPlanEqual(viewer);
   }

   @Test
   void verdictIsNotSharedBetweenUsersWithDifferentRoles() throws Exception {
      MVDef system = wsDef(false, systemUser());
      doReturn(true).when(system).isPlanEqual(any());
      findWSMV(viewer, system);
      findWSMV(viewer, system);

      xutilStatic.when(() -> XUtil.getUserRoles(any(), anyBoolean()))
         .thenReturn(new IdentityID[] { new IdentityID("r2", ORG) });
      doReturn(false).when(system).isPlanEqual(any());

      assertNull(findWSMV(viewer, system));
   }

   @Test
   void planCheckFailsClosed() throws Exception {
      XPrincipal user = viewer;
      assertFalse(planEqual(null, "plan", user), "no stored plan");
      assertFalse(planEqual("", "", user), "empty stored plan");
      assertFalse(planEqual("plan", "", user), "empty user plan");
      assertFalse(planEqual("plan", null, user), "failed user plan");
      assertFalse(planEqual("plan", "other plan", user), "different plan");
      assertTrue(planEqual("plan", "plan", user), "same plan");

      MVDef def = planDef("plan");
      doThrow(new java.lang.SecurityException("vpm")).when(def).getPlan(user);
      assertFalse(def.isPlanEqual(user), "error");
   }

   @Test
   void cubeTableHasNoPlanToCheck() throws Exception {
      MVDef def = planDef("");
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly(nullable(String.class))).thenReturn(mock(CubeTableAssembly.class));
      doReturn(ws).when(def).getWorksheet();

      assertTrue(def.isPlanEqual(viewer));
      verify(def, never()).getPlan(any());
   }

   private boolean planEqual(String stored, String user, XPrincipal principal) throws Exception {
      MVDef def = planDef(stored);
      doReturn(user).when(def).getPlan(principal);
      return def.isPlanEqual(principal);
   }

   private MVDef planDef(String stored) {
      MVDef def = spy(new MVDef());
      MVMetaData data = mock(MVMetaData.class);
      when(data.getPlan()).thenReturn(stored);
      doReturn(data).when(def).getMetaData();
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly(nullable(String.class))).thenReturn(mock(TableAssembly.class));
      doReturn(ws).when(def).getWorksheet();
      return def;
   }

   private static Identity systemUser() {
      return new DefaultIdentity(new IdentityID(XPrincipal.SYSTEM, ORG), Identity.USER);
   }

   private static Identity user(XPrincipal principal) {
      return new DefaultIdentity(IdentityID.getIdentityIDFromKey(principal.getName()),
                                 Identity.USER);
   }

   private MVDef wsDef(boolean bypass, Identity... users) {
      MVDef def = def(bypass, users);
      doReturn(true).when(def).isWSMV();
      when(def.getMetaData().isRegistered(WS_ID, TABLE)).thenReturn(true);
      return def;
   }

   private MVDef vsSubDef(Identity... users) {
      MVDef def = def(false, users);
      doReturn(false).when(def).isWSMV();
      doReturn(true).when(def).isSub();
      doReturn(TABLE).when(def).getBoundTable();
      doReturn(null).when(def).getParentVsIds();
      when(def.getMetaData().isRegistered(VS_ID)).thenReturn(true);
      return def;
   }

   private MVDef def(boolean bypass, Identity... users) {
      // real containsUser/getUsers, mocked everything that needs storage
      MVDef def = mock(MVDef.class, CALLS_REAL_METHODS);
      def.setUsers(users);
      MVMetaData data = mock(MVMetaData.class);
      when(data.isBypassVPM()).thenReturn(bypass);
      when(data.getPlan()).thenReturn("plan");
      doReturn(data).when(def).getMetaData();
      doReturn(true).when(def).isValidMV();
      doReturn(null).when(def).getVsId();
      doReturn("mv" + (++defCount) + "-" + System.nanoTime()).when(def).getName();
      doReturn(1L).when(def).getLastUpdateTime();
      return def;
   }

   private static AssetEntry entry(String id, boolean worksheet) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn(id);
      when(entry.isWorksheet()).thenReturn(worksheet);
      when(entry.getOrgID()).thenReturn(ORG);
      return entry;
   }

   private static MVDef findWSMV(XPrincipal user, MVDef... defs) throws Exception {
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      Method findMV0 = MVManager.class.getDeclaredMethod(
         "findMV0", MVDef[].class, AssetEntry.class, String.class, XPrincipal.class,
         String.class, List.class);
      findMV0.setAccessible(true);
      return (MVDef) invoke(findMV0, manager, defs, entry(WS_ID, true), TABLE, user, null, null);
   }

   private static MVDef getSubMV(XPrincipal user, MVDef... defs) throws Exception {
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      Method getSubMV0 = MVManager.class.getDeclaredMethod(
         "getSubMV0", MVDef[].class, AssetEntry.class, String.class, XPrincipal.class,
         List.class);
      getSubMV0.setAccessible(true);
      return (MVDef) invoke(getSubMV0, manager, defs, entry(VS_ID, false), TABLE, user, null);
   }

   private static Object invoke(Method method, Object target, Object... args) throws Exception {
      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException ex) {
         if(ex.getCause() instanceof Exception) {
            throw (Exception) ex.getCause();
         }

         throw ex;
      }
   }
}
