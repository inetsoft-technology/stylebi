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
package inetsoft.sree.internal;

/*
 * Bug #77088: updateCycleInfoNotify() renamed the notification recipients on the CycleInfo copy
 * returned by getCycleInfo() and never wrote it back, so a user or group rename never reached the
 * data cycles. It also threw a NullPointerException on a cycle without a CycleInfo, which aborted
 * the rest of the user rename.
 */

import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class,
                                  DataCycleManagerOrgLifecycleTest.DataCycleManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerUpdateCycleInfoNotifyTest {
   @Autowired
   private DataCycleManager dataCycleManager;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void groupRename_persistsRenamedRecipients() throws Exception {
      String orgId = "notify_group_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifyGroupOrg", orgId).setup();
      DataCycleManager.CycleInfo info = info("GroupCycle", orgId);
      info.setEndNotify(true);
      info.setEndEmail("sales(Group),sales(User),a@b.com");
      info.setFailureNotify(true);
      info.setFailureEmail("sales(Group)");
      seed(orgId, info);

      OrganizationManager.runInOrgScope(orgId, () -> {
         dataCycleManager.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      });

      DataCycleManager.CycleInfo saved = dataCycleManager.getCycleInfo("GroupCycle", orgId);
      assertEquals("sales2(Group),sales(User),a@b.com", saved.getEndEmail());
      assertEquals("sales2(Group)", saved.getFailureEmail());
   }

   @Test
   void userRename_persistsRenamedRecipients() throws Exception {
      String orgId = "notify_user_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifyUserOrg", orgId).setup();
      DataCycleManager.CycleInfo info = info("UserCycle", orgId);
      info.setStartNotify(true);
      info.setStartEmail("bob(User),bob(Group)");
      info.setExceedNotify(true);
      info.setExceedEmail("bob(User)");
      seed(orgId, info);

      OrganizationManager.runInOrgScope(orgId, () -> {
         dataCycleManager.updateCycleInfoNotify("bob", "bob2", true);
         return null;
      });

      DataCycleManager.CycleInfo saved = dataCycleManager.getCycleInfo("UserCycle", orgId);
      assertEquals("bob2(User),bob(Group)", saved.getStartEmail());
      assertEquals("bob2(User)", saved.getExceedEmail());
   }

   @Test
   void renameInScopedOrg_leavesOtherOrgUnchanged() throws Exception {
      String orgA = "notify_scope_a";
      String orgB = "notify_scope_b";
      builder = SecurityTestDataBuilder.create()
         .addOrg("NotifyScopeA", orgA)
         .addOrg("NotifyScopeB", orgB)
         .setup();
      seed(orgA, endNotify(info("CycA", orgA), "sales(Group)"));
      seed(orgB, endNotify(info("CycB", orgB), "sales(Group)"));

      OrganizationManager.runInOrgScope(orgB, () -> {
         dataCycleManager.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      });

      assertEquals("sales(Group)", dataCycleManager.getCycleInfo("CycA", orgA).getEndEmail());
      assertEquals("sales2(Group)", dataCycleManager.getCycleInfo("CycB", orgB).getEndEmail());
   }

   @Test
   void cycleWithoutInfo_skipped() throws Exception {
      String orgId = "notify_null_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifyNullOrg", orgId).setup();
      // a cycle with only a condition has no CycleInfo
      dataCycleManager.addCondition("NoInfoCycle", orgId, TimeCondition.at(1, 0, 0));
      assertNull(dataCycleManager.getCycleInfo("NoInfoCycle", orgId), "precondition");
      seed(orgId, endNotify(info("InfoCycle", orgId), "sales(Group)"));

      assertDoesNotThrow(() -> OrganizationManager.runInOrgScope(orgId, () -> {
         dataCycleManager.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      }));

      assertEquals("sales2(Group)", dataCycleManager.getCycleInfo("InfoCycle", orgId).getEndEmail());
      assertNull(dataCycleManager.getCycleInfo("NoInfoCycle", orgId));
   }

   @Test
   void unchangedCycles_notWritten() throws Exception {
      String orgId = "notify_unchanged_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifyUnchangedOrg", orgId).setup();
      seed(orgId, endNotify(info("Matching", orgId), "sales(Group)"));
      seed(orgId, endNotify(info("Other", orgId), "other(Group),sales(User)"));
      // the recipient is only used when the notification is on
      DataCycleManager.CycleInfo off = info("Off", orgId);
      off.setEndEmail("sales(Group)");
      seed(orgId, off);
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(orgId, () -> {
         spy.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      });

      verify(spy).setCycleInfo(eq("Matching"), eq(orgId), any());
      verify(spy, never()).setCycleInfo(eq("Other"), any(), any());
      verify(spy, never()).setCycleInfo(eq("Off"), any(), any());
      assertEquals("sales(Group)", dataCycleManager.getCycleInfo("Off", orgId).getEndEmail());
   }

   @Test
   void noMatchingRecipient_nothingWritten() throws Exception {
      String orgId = "notify_nomatch_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifyNoMatchOrg", orgId).setup();
      seed(orgId, endNotify(info("Other", orgId), "other(Group)"));
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(orgId, () -> {
         spy.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      });

      verify(spy, never()).setCycleInfo(any(), any(), any());
      verify(spy, never()).save();
   }

   @Test
   void changedCycles_regenerateTasksOnce() throws Exception {
      String orgId = "notify_save_org";
      builder = SecurityTestDataBuilder.create().addOrg("NotifySaveOrg", orgId).setup();
      seed(orgId, endNotify(info("First", orgId), "sales(Group)"));
      seed(orgId, endNotify(info("Second", orgId), "sales(Group)"));
      DataCycleManager spy = spy(dataCycleManager);

      OrganizationManager.runInOrgScope(orgId, () -> {
         spy.updateCycleInfoNotify("sales", "sales2", false);
         return null;
      });

      // the pregenerated cycle tasks hold a copy of the CycleInfo, so they must be regenerated
      verify(spy).setCycleInfo(eq("First"), eq(orgId), any());
      verify(spy).setCycleInfo(eq("Second"), eq(orgId), any());
      verify(spy, times(1)).save();
   }

   private void seed(String orgId, DataCycleManager.CycleInfo info) {
      dataCycleManager.addCondition(info.getName(), orgId, TimeCondition.at(1, 0, 0));
      dataCycleManager.setCycleInfo(info.getName(), orgId, info);
   }

   private static DataCycleManager.CycleInfo info(String name, String orgId) {
      return new DataCycleManager.CycleInfo(name, orgId);
   }

   private static DataCycleManager.CycleInfo endNotify(DataCycleManager.CycleInfo info,
                                                       String email)
   {
      info.setEndNotify(true);
      info.setEndEmail(email);
      return info;
   }
}
