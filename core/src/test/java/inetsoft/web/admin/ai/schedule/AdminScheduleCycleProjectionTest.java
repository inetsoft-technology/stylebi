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

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.schedule.ScheduleCondition;
import inetsoft.sree.schedule.TimeCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Bug #80838: CycleInfo created/modified stamps must not reach the plan-hash projection.
@Tag("core")
class AdminScheduleCycleProjectionTest {
   @Test void cyclesDifferingOnlyInCycleInfoStampsProjectEqual() {
      DataCycleManager.DataCycleAsset a = cycle("Cycle1", true, 2, stamped("admin", 1_000L));
      DataCycleManager.DataCycleAsset b = cycle("Cycle1", true, 2, stamped("other", 9_999_000L));

      assertEquals(AdminScheduleCycleGateway.projectXml(a), AdminScheduleCycleGateway.projectXml(b));
   }

   @Test void stampAttributesAreAbsentButIdentityIsKept() {
      String xml = AdminScheduleCycleGateway.projectXml(
         cycle("Cycle1", true, 2, stamped("admin", 1_000L)));

      assertFalse(xml.contains("createdBy"));
      assertFalse(xml.contains("modifiedBy"));
      assertFalse(xml.contains(" created="));
      assertFalse(xml.contains(" modified="));
      assertTrue(xml.contains("<CycleInfo name=\"Cycle1\""));
      assertTrue(xml.contains("threshold=\"5\""));
   }

   @Test void conditionChangeStillChangesTheProjection() {
      assertNotEquals(
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 2, stamped("a", 1L))),
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 3, stamped("a", 1L))));
   }

   @Test void enabledChangeStillChangesTheProjection() {
      assertNotEquals(
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 2, stamped("a", 1L))),
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", false, 2, stamped("a", 1L))));
   }

   @Test void nameChangeStillChangesTheProjection() {
      assertNotEquals(
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 2, stamped("a", 1L))),
         AdminScheduleCycleGateway.projectXml(cycle("Cycle2", true, 2, stamped("a", 1L))));
   }

   @Test void infoContentChangeStillChangesTheProjection() {
      DataCycleManager.CycleInfo changed = stamped("a", 1L);
      changed.setThreshold(9);

      assertNotEquals(
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 2, stamped("a", 1L))),
         AdminScheduleCycleGateway.projectXml(cycle("Cycle1", true, 2, changed)));
   }

   private static DataCycleManager.CycleInfo stamped(String user, long stamp) {
      DataCycleManager.CycleInfo info = new DataCycleManager.CycleInfo("Cycle1", "host-org");
      info.setThreshold(5);
      info.setCreatedBy(user);
      info.setCreated(stamp);
      info.setLastModifiedBy(user);
      info.setLastModified(stamp + 7);
      return info;
   }

   private static DataCycleManager.DataCycleAsset cycle(
      String name, boolean enabled, int hour, DataCycleManager.CycleInfo info)
   {
      DataCycleManager.DataCycleAsset asset = new DataCycleManager.DataCycleAsset();
      asset.setName(name);
      asset.setOrgId("host-org");
      asset.setEnabled(enabled);
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_DAY);
      condition.setHour(hour);
      List<ScheduleCondition> conditions = new ArrayList<>();
      conditions.add(condition);
      asset.setConditions(conditions);
      asset.setInfo(info);
      return asset;
   }
}
