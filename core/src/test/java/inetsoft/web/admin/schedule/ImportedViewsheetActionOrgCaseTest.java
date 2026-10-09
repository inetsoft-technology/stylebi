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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77530 (review round 1, found by the independent reviewer): a non-site-admin EM schedule
 * task import of a task containing a {@code ViewsheetAction}, into/within a mixed-case
 * organization id, was wrongly refused by the new {@code ScheduleManager.checkViewsheetOrgBoundary}
 * check (added for Bug #77530 itself).
 *
 * <p>Root cause: {@code ScheduleTaskIdentityChecker.parseImportedTask} calls
 * {@code task.parseXML(elem, true)}, which rewrites both the owner's org and the
 * {@code ViewsheetAction}'s embedded org via the no-arg, context-principal-based
 * {@code OrganizationManager.getCurrentOrgID()} -- which lower-cases its result in the plain
 * community {@code OrganizationManager}. Immediately after, {@code parseImportedTask} calls
 * {@code useCurrentOrgID(task, ...)} (the principal-based, case-preserving overload) to fix up
 * the re-cased org -- but that method only re-cases {@code task.getOwner()}/{@code
 * task.getIdentity()}, never the {@code ViewsheetAction}'s embedded sheet string (Bug #77259's
 * own fix, scoped to owner/identity only). So after import, the owner's org is correctly cased
 * but the {@code ViewsheetAction}'s org can remain lower-cased, and {@code
 * ImportTaskController.importScheduleTask} then calls {@code ScheduleManager.setScheduleTask}
 * with the real (correctly-cased) importing principal -- a same-org import, wrongly flagged as
 * cross-org by a case-sensitive comparison.
 *
 * <p>Fix: {@code ScheduleManager.checkViewsheetOrgBoundary} now compares organizations
 * case-insensitively ({@code Tool.equals(a, b, false)}), consistent with how every other org-id
 * comparison in this class already works (e.g. the Bug #77379 parent-folder-org check, and
 * {@code checkOwnerOrganization}'s stored-owner-org check, both use {@code equalsIgnoreCase}) and
 * with {@code AbstractAssetEngine.checkAssetPermission0}'s existing runtime cross-org guard.
 *
 * <p>This test reproduces the exact call sequence {@code ImportTaskController.importScheduleTask}
 * makes -- {@code ScheduleTaskIdentityChecker.parseImportedTask} then
 * {@code ScheduleManager.setScheduleTask} -- against a real, Spring-wired {@code ScheduleManager}
 * and a real {@code SecurityEngine}/{@code FileAuthenticationProvider}, the same pattern as
 * {@code ScheduleActionOrgBoundaryTest}. {@code ImportTaskControllerTest}/{@code
 * ImportTaskCrossOrgTest} cannot catch this: both mock {@code ScheduleManager} entirely, so they
 * never exercise the Bug #77530 check at all.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportedViewsheetActionOrgCaseTest {
   // mixed case on purpose -- this is exactly what triggers the no-arg
   // OrganizationManager.getCurrentOrgID()'s lower-casing to disagree with the principal-based,
   // case-preserving overload.
   private static final String MIXED_ORG = "OrgM77530";
   private static final String SCHEDULE_ROLE = "ivaocSchedRole";

   private SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("ivaocOrg", MIXED_ORG)
         .addRole(SCHEDULE_ROLE, MIXED_ORG)
         .addUser("ivaocUser", MIXED_ORG, "password")
         .addUserToRole("ivaocUser", SCHEDULE_ROLE, MIXED_ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, inetsoft.uql.util.Identity.ROLE, MIXED_ORG)
         // Bug #78129, a saved sheet must be readable by the saver and the run principal
         .grantPermission(ResourceType.REPORT, "Reports/Dashboard", ResourceAction.READ,
                          SCHEDULE_ROLE, inetsoft.uql.util.Identity.ROLE, MIXED_ORG);
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

   @AfterEach
   void tearDown() {
      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
         scheduleManager.getOrgTaskMap(MIXED_ORG);
      map.keySet().removeIf(k -> k != null && k.contains("ivaocImported"));
   }

   @Test
   void sameOrgImport_withMixedCaseOrg_isNotWronglyRefused() throws Exception {
      SRPrincipal caller = builder.principalOf("ivaocUser", MIXED_ORG);
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "test setup: not a site admin");

      // the full 5-segment identifier form, same org as the owner, mixed case
      String sheetId = "1^128^__NULL__^Reports/Dashboard^" + MIXED_ORG;
      ScheduleTask exported = new ScheduleTask("ivaocImported");
      exported.setOwner(new IdentityID("ivaocUser", MIXED_ORG));
      exported.addCondition(TimeCondition.at(1, 30, 0));
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheetId);
      exported.addAction(action);

      StringWriter xml = new StringWriter();
      exported.writeXML(new PrintWriter(xml));

      Document doc = Tool.parseXML(new StringReader(xml.toString()));
      Element taskElem = (Element) doc.getElementsByTagName("Task").item(0);

      // the exact call ImportTaskController.importScheduleTask makes to parse the uploaded file
      ScheduleTask imported = new ScheduleTaskIdentityChecker(SecurityEngine.getSecurity())
         .parseImportedTask(taskElem, caller);

      assertEquals(MIXED_ORG, imported.getOwner().getOrgID(),
                   "owner org is re-cased correctly (Bug #77259)");

      ViewsheetAction importedAction = (ViewsheetAction) imported.getAction(0);
      // demonstrates the root cause: the ViewsheetAction's org is left lower-cased, unlike the
      // owner's, by parseImportedTask -- not asserting this is desirable, just documenting why
      // a case-sensitive comparison downstream would misfire on a legitimate same-org import.
      assertEquals(MIXED_ORG.toLowerCase(),
                   inetsoft.uql.asset.AssetEntry.createAssetEntry(importedAction.getViewsheet())
                      .getOrgID(),
                   "test setup: reproduces the known org-case disagreement root cause");

      // the exact call ImportTaskController.importScheduleTask makes next -- must not throw
      assertDoesNotThrow(
         () -> scheduleManager.setScheduleTask(imported.getTaskId(), imported, caller),
         "a legitimate same-org import must not be refused merely because of an org-id case " +
         "difference between the re-cased owner and the (not re-cased) ViewsheetAction");

      ScheduleTask stored = scheduleManager.getScheduleTask(imported.getTaskId(), MIXED_ORG);
      assertNotNull(stored, "the imported task was actually stored");
   }
}
