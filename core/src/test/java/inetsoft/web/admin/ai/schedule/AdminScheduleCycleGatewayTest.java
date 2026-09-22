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
package inetsoft.web.admin.ai.schedule;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.schedule.DataCycleInfo;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.admin.schedule.ScheduleCycleService;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Direct unit coverage for {@link AdminScheduleCycleGateway} (bug #76848, design §8) --
 * exercises it against a REAL {@link ScheduleCycleService} (not mocked, per this package's own
 * {@code AdminScheduleGatewayTest} precedent of testing "against real community-tier services"),
 * with only that service's OWN lower-level collaborators mocked.
 *
 * <p>Covers: list/get permission-filtering (via the real {@code getCycleInfos}), and the
 * existence-check-before-permission-check ordering for {@code getCycle} -- a nonexistent name
 * must return {@code null} without ever attempting {@code getDialogModel} on it (which would
 * otherwise happily proceed on a name that was never checked against the storage layer at all).
 */
@Tag("core")
class AdminScheduleCycleGatewayTest {
   @BeforeEach
   void setUp() throws Exception {
      dataCycleManager = mock(DataCycleManager.class, withSettings().lenient());
      schedulerMonitoringService = mock(SchedulerMonitoringService.class, withSettings().lenient());
      // Unstubbed default is null (Mockito's array-return default), not empty -- ScheduleCycleService
      // #getCycleInfos iterates it directly with no null-guard, so a test that never overrides
      // this would otherwise NPE regardless of whether it cares about the cycle list at all
      // (e.g. removeCycles' own tail call back into getCycleInfos).
      when(schedulerMonitoringService.getDataCycleInfos()).thenReturn(new DataCycleInfo[0]);
      permissionService = mock(ResourcePermissionService.class, withSettings().lenient());
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      scheduleConditionService = mock(ScheduleConditionService.class, withSettings().lenient());

      scheduleCycleService = new ScheduleCycleService(
         dataCycleManager, scheduleConditionService, schedulerMonitoringService, permissionService,
         securityEngine);
      gateway = new AdminScheduleCycleGateway(
         dataCycleManager, scheduleCycleService, scheduleConditionService, securityEngine);

      user = mock(Principal.class, withSettings().lenient());
      when(user.getName()).thenReturn("admin:host-org");

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn("host-org");
      // The no-arg overload is what DataCycleManager#hasPregeneratedDependency itself consults
      // internally (design's own §10 finding) -- AdminScheduleCycleGateway#dependentMvNamesByCycle
      // mirrors that exact resolution, so it needs stubbing too.
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      mvManagerStatic = mockStatic(MVManager.class, withSettings().lenient());
      MVManager mvManager = mock(MVManager.class, withSettings().lenient());
      when(mvManager.list(false)).thenReturn(new MVDef[0]);
      mvManagerStatic.when(MVManager::getManager).thenReturn(mvManager);

      // ScheduleCycleService#getCyclePermissionID (called from hasCycleAccess/getDialogModel)
      // calls the real SUtil.isMultiTenant(), which -- outside a live Spring context -- reaches
      // for a static SecurityEngine.getSecurity() singleton lookup and throws ShutdownException.
      // Not a design concern (this repo's own multi-tenant-org-resolution risk, §10, is orthogonal
      // to this particular static lookup): mocked purely so this unit test can exercise real
      // ScheduleCycleService logic without a live server.
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);

      // ScheduleCycleService#deleteCycle builds an ActionRecord, whose constructor calls
      // Tool.getHost() -> SreeEnv.getProperty -- same live-Spring-context requirement as above,
      // mocked (unstubbed = null) purely so it falls through to its own local try/catch.
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      // ActionRecord's own constructor also calls the static SecurityEngine.getSecurity()
      // singleton lookup directly (distinct from the `securityEngine` instance injected into
      // ScheduleCycleService above) -- mocked so its getSecurityProvider().getUser(...) chain
      // returns null rather than reaching Spring, falling through to the constructor's own
      // OrganizationManager-based fallback for orgId.
      SecurityProvider noSuchUserProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(noSuchUserProvider.getUser(any())).thenReturn(null);
      SecurityEngine staticSecurityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(staticSecurityEngine.getSecurityProvider()).thenReturn(noSuchUserProvider);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(staticSecurityEngine);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      mvManagerStatic.close();
      sUtilStatic.close();
      sreeEnv.close();
      securityEngineStatic.close();
   }

   @Test
   void listCycles_filtersToPermittedCyclesOnly() throws Exception {
      when(schedulerMonitoringService.getDataCycleInfos()).thenReturn(new DataCycleInfo[]{
         new DataCycleInfo("Cycle1"), new DataCycleInfo("Cycle2")
      });

      // Only "Cycle1" is permitted -- matches on the resource string containing the cycle name,
      // sidestepping getCyclePermissionID's own org-resolution details (not this test's concern).
      SecurityProvider provider = mock(SecurityProvider.class, withSettings().lenient());
      when(provider.checkPermission(eq(user), eq(ResourceType.SCHEDULE_CYCLE), anyString(), any()))
         .thenAnswer(inv -> ((String) inv.getArgument(2)).contains("Cycle1"));
      when(securityEngine.getSecurityProvider()).thenReturn(provider);

      when(dataCycleManager.getConditions(eq("Cycle1"), eq("host-org")))
         .thenReturn(new Vector<>(List.of(TimeCondition.at(9, 0, 0))));

      List<ScheduleCycleView> views = gateway.listCycles(user);

      assertEquals(1, views.size());
      assertEquals("Cycle1", views.get(0).name());
      assertFalse(views.get(0).inUse());
      assertEquals(List.of(), views.get(0).dependentMvNames());
   }

   @Test
   void listCycles_marksInUseFromMvDependents() throws Exception {
      when(schedulerMonitoringService.getDataCycleInfos())
         .thenReturn(new DataCycleInfo[]{ new DataCycleInfo("Cycle1") });
      when(securityEngine.getSecurityProvider()).thenReturn(null); // no provider -> unfiltered
      when(dataCycleManager.getConditions(eq("Cycle1"), eq("host-org")))
         .thenReturn(new Vector<>(List.of(TimeCondition.at(9, 0, 0))));

      MVDef mv = mock(MVDef.class, withSettings().lenient());
      when(mv.getCycle()).thenReturn("Cycle1");
      when(mv.getName()).thenReturn("MV_Sales");
      AssetEntry entry = mock(AssetEntry.class, withSettings().lenient());
      when(entry.getOrgID()).thenReturn("host-org");
      when(mv.getEntry()).thenReturn(entry);
      when(MVManager.getManager().list(false)).thenReturn(new MVDef[]{ mv });

      List<ScheduleCycleView> views = gateway.listCycles(user);

      assertEquals(1, views.size());
      assertTrue(views.get(0).inUse());
      assertEquals(List.of("MV_Sales"), views.get(0).dependentMvNames());
   }

   @Test
   void getCycle_returnsNullForNonexistentNameWithoutTouchingSecurityEngine() throws Exception {
      when(dataCycleManager.getDataCycles("host-org"))
         .thenReturn(Collections.enumeration(List.of("Cycle1")));

      ScheduleCycleView view = gateway.getCycle("Ghost", user);

      assertNull(view);
      verifyNoInteractions(securityEngine);
   }

   @Test
   void getCycle_propagatesSecurityExceptionWhenCallerLacksAccessOnExistingCycle() throws Exception {
      when(dataCycleManager.getDataCycles("host-org"))
         .thenReturn(Collections.enumeration(List.of("Cycle1")));
      when(securityEngine.checkPermission(eq(user), eq(ResourceType.SCHEDULE_CYCLE), anyString(),
                                          eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> gateway.getCycle("Cycle1", user));
   }

   @Test
   void createCycle_writesConditionsAndGrantsPermission() throws Exception {
      ScheduleCycleChangeRequest.ScheduleCycleSpec spec =
         new ScheduleCycleChangeRequest.ScheduleCycleSpec("NewCycle",
            List.of(everyDayWire()));

      gateway.createCycle(spec, user);

      verify(dataCycleManager).setConditions(eq("NewCycle"), eq("host-org"), anyList());
      verify(dataCycleManager).setCycleInfo(eq("NewCycle"), eq("host-org"), any());
      verify(dataCycleManager).save();
      verify(securityEngine).setPermission(eq(ResourceType.SCHEDULE_CYCLE), anyString(), any());
   }

   @Test
   void deleteCycles_removesWhenNotInUse() throws Exception {
      gateway.deleteCycles(List.of("Cycle1"), user);

      verify(dataCycleManager).removeDataCycle(eq("Cycle1"), eq("host-org"));
      verify(dataCycleManager).save();
   }

   @Test
   void currentPermission_readsViaTheCyclePermissionResourceId() {
      String permissionId = ScheduleCycleService.getCyclePermissionID("Cycle1", "host-org");
      Permission stored = new Permission();
      when(securityEngine.getPermission(eq(ResourceType.SCHEDULE_CYCLE), eq(permissionId)))
         .thenReturn(stored);

      assertSame(stored, gateway.currentPermission("Cycle1", "host-org"));
   }

   /**
    * The regression this fix is for (review round 1, Finding 1): {@code createCycle} (as called
    * by delete-rollback) grants a FRESH default permission to whoever runs the rollback --
    * {@code restoreCycleState} must overwrite that default with the CAPTURED original
    * (non-default) permission and {@code CycleInfo}, not leave the fresh one in place. Without
    * {@code restoreCycleState} (pre-fix), nothing ever re-asserted the original permission after
    * {@code createCycle}'s own default grant, so a delete-rollback would silently narrow a
    * cycle's access to only the rollback-running principal.
    */
   @Test
   void restoreCycleState_overwritesCreateCyclesFreshDefaultWithTheCapturedOriginal() throws Exception {
      // Simulate rollbackDelete's own sequence: createCycle first (grants a fresh default
      // permission for "user" and stamps a fresh CycleInfo)...
      ScheduleCycleChangeRequest.ScheduleCycleSpec spec =
         new ScheduleCycleChangeRequest.ScheduleCycleSpec("Cycle1", List.of(everyDayWire()));
      gateway.createCycle(spec, user);

      // ...capture what createCycle actually granted, to prove restoreCycleState's own later
      // call is a genuinely DIFFERENT, non-default object, not a no-op re-assertion of the same
      // default grant.
      ArgumentCaptor<Permission> freshDefaultCaptor = ArgumentCaptor.forClass(Permission.class);
      verify(securityEngine).setPermission(eq(ResourceType.SCHEDULE_CYCLE), anyString(),
                                           freshDefaultCaptor.capture());
      Permission freshDefault = freshDefaultCaptor.getValue();

      DataCycleManager.CycleInfo originalInfo = new DataCycleManager.CycleInfo("Cycle1", "host-org");
      originalInfo.setCreatedBy("originalCreator");
      Permission originalPermission = new Permission();
      originalPermission.setUserGrantsForOrg(ResourceAction.READ, Set.of("secondUser"), "host-org");

      gateway.restoreCycleState("Cycle1", "host-org", originalInfo, originalPermission, true);

      verify(dataCycleManager).setCycleInfo(eq("Cycle1"), eq("host-org"), same(originalInfo));
      ArgumentCaptor<Permission> finalCaptor = ArgumentCaptor.forClass(Permission.class);
      verify(securityEngine, times(2)).setPermission(eq(ResourceType.SCHEDULE_CYCLE), anyString(),
                                                      finalCaptor.capture());
      Permission lastGrant = finalCaptor.getValue();
      assertSame(originalPermission, lastGrant,
                "the LAST permission grant must be the captured original, not createCycle's own fresh default");
      assertNotSame(freshDefault, lastGrant);
   }

   /**
    * bug #76919: {@code createCycle} (as called by delete-rollback) always leaves a FRESH cycle
    * {@code enabled=true} ({@code DataCycleManager#setConditions} builds a brand-new asset with
    * that default whenever the storage entry doesn't already exist -- exactly the state right
    * after the delete being rolled back). Without restoring the captured pre-delete {@code
    * enabled} bit, a delete-rollback silently re-enables a cycle an admin had explicitly disabled.
    */
   @Test
   void restoreCycleState_mustAlsoRestoreDisabledState() throws Exception {
      ScheduleCycleChangeRequest.ScheduleCycleSpec spec =
         new ScheduleCycleChangeRequest.ScheduleCycleSpec("Cycle1", List.of(everyDayWire()));
      gateway.createCycle(spec, user);

      DataCycleManager.CycleInfo originalInfo = new DataCycleManager.CycleInfo("Cycle1", "host-org");
      Permission originalPermission = new Permission();

      gateway.restoreCycleState("Cycle1", "host-org", originalInfo, originalPermission, false);

      verify(dataCycleManager).setEnable(eq("Cycle1"), eq("host-org"), eq(false));
   }

   private static inetsoft.web.api.schedule.TimeCondition everyDayWire() {
      inetsoft.web.api.schedule.TimeCondition condition = new inetsoft.web.api.schedule.TimeCondition();
      condition.setType(inetsoft.web.api.schedule.TimeCondition.Type.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setTimeZone("UTC");
      return condition;
   }

   private DataCycleManager dataCycleManager;
   private SchedulerMonitoringService schedulerMonitoringService;
   private ResourcePermissionService permissionService;
   private SecurityEngine securityEngine;
   private ScheduleConditionService scheduleConditionService;
   private ScheduleCycleService scheduleCycleService;
   private AdminScheduleCycleGateway gateway;
   private Principal user;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<MVManager> mvManagerStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<SecurityEngine> securityEngineStatic;
}
