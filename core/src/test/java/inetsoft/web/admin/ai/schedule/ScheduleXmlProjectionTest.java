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

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.ServerPathInfo;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

@Tag("core")
class ScheduleXmlProjectionTest {
   @Test void nullTaskProjectsToNull() {
      assertNull(ScheduleXmlProjection.project(null));
   }

   @Test void projectionIncludesSemanticFields() {
      ScheduleTask task = task("nightly-refresh", "admin");

      String projection = ScheduleXmlProjection.project(task);

      assertTrue(projection.contains("name=\"nightly-refresh\""));
      // owner is written as IdentityID#convertToKey(), "name~;~orgID"-shaped -- not a bare name.
      assertTrue(projection.contains("owner=\"admin" + IdentityID.KEY_DELIMITER + "host-org\""));
   }

   // The one attribute CONFIRMED unstable across a save (ScheduleManager#setScheduleTask stamps it
   // unconditionally) -- without this exclusion, the apply-time verify would report every
   // successful apply as FAILED and roll it straight back.
   @Test void lastModifiedIsAlwaysStripped() {
      ScheduleTask task = task("t", "admin");
      task.setLastModified(1_787_724_000_000L);

      String projection = ScheduleXmlProjection.project(task);

      assertFalse(projection.contains("lastModified"));
   }

   // The two projections a plan-hash collision would look like: same single condition vs. an
   // extra one added. Closes the exact collision SpikeHashProbe found when projecting off
   // getCondition(0) instead of a total serialization (docs/teams/2026-08-26-track-c0-spike).
   @Test void projectionDiffersWhenAConditionIsAdded() {
      ScheduleTask approved = task("nightly-refresh", "admin");
      approved.addCondition(timeCondition(9, 0));

      ScheduleTask drifted = task("nightly-refresh", "admin");
      drifted.addCondition(timeCondition(9, 0));
      drifted.addCondition(timeCondition(23, 0));

      assertNotEquals(ScheduleXmlProjection.project(approved), ScheduleXmlProjection.project(drifted));
   }

   @Test void projectionIsStableForIdenticalTasks() {
      ScheduleTask a = task("t", "admin");
      a.addCondition(timeCondition(9, 0));
      ScheduleTask b = task("t", "admin");
      b.addCondition(timeCondition(9, 0));

      assertEquals(ScheduleXmlProjection.project(a), ScheduleXmlProjection.project(b));
   }

   // Bug 76726: ServerPathInfo#writeXML re-encrypts the stored password with a fresh IV on every
   // call (Tool#encryptPassword -> the JCE cipher), so a delete-plan hash built off the raw
   // projection would differ on every call even though the task itself never changed. Stubs
   // Tool.encryptPassword to return a genuinely varying ciphertext per call for the same plaintext
   // -- simulating two real writeXML calls -- rather than a fixed deterministic mock, or this test
   // would pass without the fix. Round 2 (sentinel-value refinement): the password attribute is no
   // longer stripped, it is normalized to a fixed "SET" value, so this asserts the projections
   // are equal AND that the presence of a saved password still survives into the projection.
   @Test void projectionIsStableAcrossPasswordReencryption() {
      AtomicInteger callCount = new AtomicInteger();

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, withSettings()
         .strictness(Strictness.LENIENT).defaultAnswer(Answers.CALLS_REAL_METHODS))) {
         tool.when(() -> Tool.encryptPassword(anyString()))
            .thenAnswer(inv -> "IV" + callCount.incrementAndGet() + ":" + inv.getArgument(0));

         ScheduleTask a = task("t", "admin");
         a.addAction(viewsheetActionWithSaveToServerPassword("s3cret"));
         ScheduleTask b = task("t", "admin");
         b.addAction(viewsheetActionWithSaveToServerPassword("s3cret"));

         String projectionA = ScheduleXmlProjection.project(a);
         String projectionB = ScheduleXmlProjection.project(b);

         // Sanity check that this fixture actually exercises the bug: two independent writeXML
         // calls over the identical plaintext password must produce different ciphertext.
         assertNotEquals(rawWriteXml(a), rawWriteXml(b));
         assertEquals(projectionA, projectionB);
         assertTrue(projectionA.contains("password=\"SET\""));
      }
   }

   // Round 2: a task with no saved password must project with no password attribute at all --
   // the dual-state (present/absent) distinction the sentinel-value normalization relies on is
   // unaffected by this change, since ServerPathInfo/EmailInfo only ever emit the attribute when
   // non-blank.
   @Test void projectionHasNoPasswordAttributeWhenNoPasswordIsSaved() {
      ScheduleTask task = task("t", "admin");
      task.addAction(viewsheetActionWithSaveToServerPassword(""));

      String projection = ScheduleXmlProjection.project(task);

      assertFalse(projection.contains("password="));
   }

   @Test void normalizeStripsEveryExcludedAttributeByName() {
      String xml = "<Task name=\"t\" lastModified=\"5\" path=\"/\" editable=\"true\" " +
         "removable=\"false\" enabled=\"true\">";

      String normalized = ScheduleXmlProjection.normalize(xml);

      for(String attr : ScheduleXmlProjection.EXCLUDED_ATTRIBUTES) {
         assertFalse(normalized.contains(attr + "="), "expected " + attr + " to be stripped");
      }

      assertTrue(normalized.contains("name=\"t\""));
      assertTrue(normalized.contains("enabled=\"true\""));
   }

   @Test void normalizeRewritesEveryNormalizedAttributeValueToSet() {
      String xml = "<Action password=\"cipherA\"><Nested password=\"cipherB\"/></Action>";

      String normalized = ScheduleXmlProjection.normalize(xml);

      for(String attr : ScheduleXmlProjection.NORMALIZED_ATTRIBUTES) {
         assertFalse(normalized.contains(attr + "=\"cipher"),
            "expected " + attr + " value to be rewritten");
         assertTrue(normalized.contains(attr + "=\"SET\""),
            "expected " + attr + " to be normalized to SET");
      }
   }

   private static ScheduleTask task(String name, String owner) {
      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID(owner, "host-org"));
      task.setEnabled(true);
      return task;
   }

   private static TimeCondition timeCondition(int hour, int minute) {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setHour(hour);
      condition.setMinute(minute);
      return condition;
   }

   private static ViewsheetAction viewsheetActionWithSaveToServerPassword(String password) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_PDF,
         new ServerPathInfo("/exports/report.pdf", "scheduler", password));
      return action;
   }

   private static String rawWriteXml(ScheduleTask task) {
      java.io.StringWriter sw = new java.io.StringWriter();

      try(java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
         task.writeXML(pw);
      }

      return sw.toString();
   }
}
