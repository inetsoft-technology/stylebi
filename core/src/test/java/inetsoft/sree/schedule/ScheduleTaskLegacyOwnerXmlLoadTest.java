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
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77359: the same legacy task xml, as written by old versions (owner "null" or no owner),
 * stored in two organizations and loaded from storage gets a distinct task id (scheduler job key)
 * in each organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskLegacyOwnerXmlLoadTest {
   private static final String ORG_A = "lxorga";
   private static final String ORG_B = "lxorgb";

   @Autowired
   ScheduleManager scheduleManager;

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

   @ParameterizedTest
   @ValueSource(strings = { "<Task name=\"lxNightly\" owner=\"null\" enabled=\"true\"></Task>",
                            "<Task name=\"lxNightly\" enabled=\"true\"></Task>" })
   void legacyXml_storedInTwoOrgs_hasDistinctTaskIds(String xml) throws Exception {
      ScheduleTask a = load(ORG_A, putRaw(ORG_A, xml));
      ScheduleTask b = load(ORG_B, putRaw(ORG_B, xml));

      assertEquals(new IdentityID(XPrincipal.SYSTEM, ORG_A), a.getOwner());
      assertEquals(new IdentityID(XPrincipal.SYSTEM, ORG_B), b.getOwner());
      assertNotEquals(a.getTaskId(), b.getTaskId());
      assertEquals(b.getTaskId(),
                   scheduleManager.getScheduleTask(b.getTaskId(), ORG_B).getTaskId());
   }

   // writes the raw legacy xml, it's read back as a ScheduleTask (a subclass)
   public static class RawLegacyTask extends ScheduleTask {
      static String xml;

      public RawLegacyTask() {
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.print(xml);
      }
   }

   // stored under the owner-less id, like a task saved before 13.1
   private String putRaw(String orgID, String xml) throws Exception {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/lxNightly", null, orgID).toIdentifier();
      SRPrincipal principal = new SRPrincipal(new IdentityID("admin", orgID), new IdentityID[0],
                                              new String[0], orgID,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(principal);

      try {
         RawLegacyTask.xml = xml;
         IndexedStorage.getIndexedStorage().putXMLSerializable(key, new RawLegacyTask());
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }

      return key;
   }

   private ScheduleTask load(String orgID, String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(orgID);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, key);
      return task;
   }
}
