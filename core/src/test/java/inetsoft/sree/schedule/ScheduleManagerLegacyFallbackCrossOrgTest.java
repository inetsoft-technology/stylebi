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
package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77356: the legacy (pre 13.1) fallback of
 * {@link ScheduleManager#getScheduleTask(String, String)} strips the owner prefix of a task id
 * to find a task stored with an owner-less id. It must not resolve an owner prefix of another
 * organization ("bob~;~orgB:Nightly" looked up in org A) to org A's legacy "Nightly".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleManagerLegacyFallbackCrossOrgTest {
   private static final String ORG_A = "lfOrgA";
   private static final String ORG_B = "lfOrgB";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final IdentityID HOST_SYSTEM =
      new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID());

   @Autowired
   ScheduleManager scheduleManager;

   private ScheduleTask legacy;
   private ScheduleTask legacySystem;
   private ScheduleTask bobs;
   private ScheduleTask colon;

   @BeforeEach
   void setUp() throws Exception {
      SRPrincipal alice = new SRPrincipal(ALICE, new IdentityID[0], new String[0], ORG_A,
                                          Tool.getSecureRandom().nextLong());
      alice.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(alice);

      // org A: a pre 13.1 task stored with the owner-less id "Nightly" and parsed with an owner,
      // its own id is "alice~;~lfOrgA:Nightly"
      legacy = new ScheduleTask("Nightly");
      legacy.setOwner(ALICE);
      putLegacy(legacy, ORG_A);

      // org A: a legacy owner="null" row copied into a non-host org, parsed with the host org
      // system owner, its own id names the host org
      legacySystem = new ScheduleTask("Weekly");
      legacySystem.setOwner(HOST_SYSTEM);
      putLegacy(legacySystem, ORG_A);

      // org A: an ordinary task whose name contains ':'
      colon = new ScheduleTask("My:Task");
      colon.setOwner(ALICE);
      scheduleManager.save(List.of(colon), ORG_A);

      // org B: bob's ordinary task "bob~;~lfOrgB:Nightly"
      bobs = new ScheduleTask("Nightly");
      bobs.setOwner(BOB);
      scheduleManager.save(List.of(bobs), ORG_B);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.clear();
      }
   }

   @Test
   void otherOrgPrefix_doesNotResolveOwnOrgLegacyTask() {
      assertNull(scheduleManager.getScheduleTask(BOB.convertToKey() + ":Nightly", ORG_A));
      assertNull(scheduleManager.getScheduleTask(BOB.convertToKey() + ":Weekly", ORG_A));
      assertNull(scheduleManager.getScheduleTask("x~;~y~;~" + ORG_B + ":Nightly", ORG_A));
   }

   @Test
   void prefixWithoutOrganization_doesNotResolve() {
      assertNull(scheduleManager.getScheduleTask("bob~;~:Nightly", ORG_A));
      assertNull(scheduleManager.getScheduleTask("bob~;~__GLOBAL__:Nightly", ORG_A));
   }

   @Test
   void legacyBareId_resolvesInOwnOrg() {
      assertTaskId(legacy, scheduleManager.getScheduleTask("Nightly", ORG_A));
   }

   @Test
   void preV131BareOwnerPrefix_resolvesInOwnOrg() {
      assertTaskId(legacy, scheduleManager.getScheduleTask("bob:Nightly", ORG_A));
   }

   @Test
   void sameOrgPrefix_resolves() {
      assertTaskId(legacy, scheduleManager.getScheduleTask(legacy.getTaskId(), ORG_A));
      assertTaskId(legacy, scheduleManager.getScheduleTask("carol~;~" + ORG_A + ":Nightly", ORG_A));
      // organization ids are compared ignoring case, like ScheduleManager.isOtherOrgTaskId()
      assertTaskId(legacy, scheduleManager.getScheduleTask(
         "alice~;~" + ORG_A.toUpperCase() + ":Nightly", ORG_A));
   }

   @Test
   void legacyHostOrgSystemOwner_inNonHostOrg_resolvesByItsOwnId() {
      assertEquals(HOST_SYSTEM.convertToKey() + ":Weekly", legacySystem.getTaskId());
      assertTaskId(legacySystem, scheduleManager.getScheduleTask(legacySystem.getTaskId(), ORG_A));
   }

   @Test
   void exactIds_stillResolve() {
      assertTaskId(bobs, scheduleManager.getScheduleTask(bobs.getTaskId(), ORG_B));
      // Bug #77883, the stored copy is re-parsed with the same id
      assertTaskId(colon, scheduleManager.getScheduleTask(colon.getTaskId(), ORG_A));
   }

   private static void assertTaskId(ScheduleTask expected, ScheduleTask actual) {
      assertNotNull(actual);
      assertEquals(expected.getTaskId(), actual.getTaskId());
   }

   // store the task under its owner-less id, like a task saved before 13.1
   private void putLegacy(ScheduleTask task, String orgID) {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + task.getName(), null, orgID).toIdentifier();
      scheduleManager.getOrgTaskMap(orgID).put(key, task, orgID);
   }
}
