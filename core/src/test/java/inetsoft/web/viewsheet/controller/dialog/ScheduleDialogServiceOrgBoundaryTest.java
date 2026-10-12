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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.viewsheet.model.dialog.schedule.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #77530: {@code ScheduleDialogService.scheduleVS()} (the viewer's own "Schedule" dialog,
 * STOMP {@code /vs/schedule-dialog-model}) builds a {@code ViewsheetAction} directly and lets a
 * client-supplied {@code viewsheetActionModel.viewsheet()} string win over the caller's own
 * already-open, already-permission-checked viewsheet entry, with nothing checking it before
 * {@code ScheduleManager.setScheduleTask()} persists it. Uses the real {@code ScheduleManager}
 * bean (so the Bug #77530 fix in {@code ScheduleManager.setScheduleTask()} actually runs) and
 * mocks the service's other collaborators, the same pattern as
 * {@code ScheduleActionOrgBoundaryTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleDialogServiceOrgBoundaryTest {
   private static final String ORG_A = "sdsOrgA";
   private static final String ORG_B = "sdsOrgB";
   private static final String SCHEDULE_ROLE = "sdsSchedRole";

   private SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   private ViewsheetService viewsheetService;
   private SecurityEngine securityEngine;
   private ScheduleDialogService service;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("sdsA", ORG_A)
         .addOrg("sdsB", ORG_B)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addUser("sdsUser", ORG_A, "password")
         .addUserToRole("sdsUser", SCHEDULE_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A)
         // Bug #78129, a saved sheet must be readable by the saver and the run principal
         .grantPermission(ResourceType.REPORT, "Own/Dashboard", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
   }

   @AfterAll
   void teardownAll() {
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      viewsheetService = mock(ViewsheetService.class);
      securityEngine = mock(SecurityEngine.class);
      service = new ScheduleDialogService(viewsheetService, mock(SecurityProvider.class),
                                          securityEngine, mock(ScheduleService.class),
                                          mock(VSBookmarkService.class), scheduleManager);
   }

   @AfterEach
   void tearDown() {
      @SuppressWarnings("unchecked")
      java.util.Map<String, ScheduleTask> map = (java.util.Map<String, ScheduleTask>) (Object)
         scheduleManager.getOrgTaskMap(ORG_A);
      map.keySet().removeIf(k -> k != null && k.contains("sdsDialog"));
   }

   // (b) ScheduleDialogService.scheduleVS refuses a client-supplied foreign-org viewsheet id
   @Test
   void scheduleVS_foreignOrgViewsheet_isRefused() throws Exception {
      SRPrincipal caller = builder.principalOf("sdsUser", ORG_A);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");
      allowScheduling(caller);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry ownEntry = AssetEntry.createAssetEntry("1^128^__NULL__^Own/Dashboard^" + ORG_A);
      when(rvs.getEntry()).thenReturn(ownEntry);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet("vsid1", caller)).thenReturn(rvs);

      // the client substitutes a foreign-org identifier for the caller's own already-open entry
      ViewsheetActionModel actionModel = ViewsheetActionModel.builder()
         .viewsheet("1^128^__NULL__^Foreign/Dashboard^" + ORG_B)
         .build();
      ScheduleDialogModel model = dialogModel("sdsDialogForeign", actionModel);

      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> service.scheduleVS("vsid1", model, caller, mock(CommandDispatcher.class)));

      assertNull(scheduleManager.getScheduleTask(
         new IdentityID("sdsUser", ORG_A).convertToKey() + ":sdsDialogForeign", ORG_A),
         "not stored");
   }

   // regression: the caller's own already-open viewsheet (no substitution) is unaffected
   @Test
   void scheduleVS_ownViewsheet_isAllowed() throws Exception {
      SRPrincipal caller = builder.principalOf("sdsUser", ORG_A);
      allowScheduling(caller);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry ownEntry = AssetEntry.createAssetEntry("1^128^__NULL__^Own/Dashboard^" + ORG_A);
      when(rvs.getEntry()).thenReturn(ownEntry);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet("vsid2", caller)).thenReturn(rvs);

      // no client-supplied viewsheet string: falls back to entry.toIdentifier(), same org
      ViewsheetActionModel actionModel = ViewsheetActionModel.builder().build();
      ScheduleDialogModel model = dialogModel("sdsDialogOwn", actionModel);

      service.scheduleVS("vsid2", model, caller, mock(CommandDispatcher.class));

      ScheduleTask stored = scheduleManager.getScheduleTask(
         new IdentityID("sdsUser", ORG_A).convertToKey() + ":sdsDialogOwn", ORG_A);
      assertNotNull(stored, "stored");
      assertEquals(ownEntry.toIdentifier(), ((ViewsheetAction) stored.getAction(0)).getViewsheet());
   }

   // Bug #78129, the client substitutes another same-org user's private viewsheet for the
   // caller's own already-open entry, the org matches but the caller can't read it
   @Test
   void scheduleVS_otherUsersPrivateViewsheet_isRefused() throws Exception {
      SRPrincipal caller = builder.principalOf("sdsUser", ORG_A);
      allowScheduling(caller);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry ownEntry = AssetEntry.createAssetEntry("1^128^__NULL__^Own/Dashboard^" + ORG_A);
      when(rvs.getEntry()).thenReturn(ownEntry);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      when(viewsheetService.getViewsheet("vsid3", caller)).thenReturn(rvs);

      ViewsheetActionModel actionModel = ViewsheetActionModel.builder()
         .viewsheet("4^128^sdsOther~;~" + ORG_A + "^vs1^" + ORG_A)
         .build();
      ScheduleDialogModel model = dialogModel("sdsDialogPrivate", actionModel);

      inetsoft.sree.security.SecurityException ex =
         assertThrows(inetsoft.sree.security.SecurityException.class,
            () -> service.scheduleVS("vsid3", model, caller, mock(CommandDispatcher.class)));
      assertTrue(ex.getMessage().contains("isn't readable"), ex.getMessage());

      assertNull(scheduleManager.getScheduleTask(
         new IdentityID("sdsUser", ORG_A).convertToKey() + ":sdsDialogPrivate", ORG_A),
         "not stored");
   }

   private void allowScheduling(SRPrincipal caller) throws inetsoft.sree.security.SecurityException {
      when(securityEngine.checkPermission(eq(caller), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
                                          eq("Schedule"), eq(ResourceAction.READ)))
         .thenReturn(true);
      when(securityEngine.checkPermission(eq(caller), eq(ResourceType.SCHEDULER), eq("*"),
                                          eq(ResourceAction.ACCESS)))
         .thenReturn(true);
   }

   private ScheduleDialogModel dialogModel(String taskName, ViewsheetActionModel actionModel) {
      SimpleScheduleDialogModel simple = SimpleScheduleDialogModel.builder()
         .startTimeEnabled(false)
         .timeRangeEnabled(false)
         .taskName(taskName)
         .actionModel(actionModel)
         .build();
      return ScheduleDialogModel.builder().simpleScheduleDialogModel(simple).build();
   }
}
