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
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}
