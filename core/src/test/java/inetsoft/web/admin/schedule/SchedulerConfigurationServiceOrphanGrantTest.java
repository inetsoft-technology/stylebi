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

/*
 * Test strategy
 *
 * Bug #80027 (Gap 1): creating a time range under a name that previously had a permission grant
 * (left behind by an earlier rename/delete of that same name) must clear the orphaned grant, the
 * way EM's own create always does, without touching the org-wide permission.andCondition setting.
 *
 * clearsOrphanedGrant_keepsAndConditionUnchanged exercises setTimeRanges end to end via
 * reflection (the method is private) for a genuinely new name with modified()==null.
 *
 * builderFromThenPermissions_resultsInEmptyPermissions is the order-sensitive regression guard
 * called for in the adversarial review: ImmutableResourcePermissionModel's generated from(...)
 * uses addAllPermissions (append, not replace), so .permissions(emptyList()) only clears the list
 * when it runs after .from(base) in the builder chain. Reversing the order would silently
 * re-append base's old grant entries on top of the empty list, defeating the fix with no error.
 */

import inetsoft.sree.schedule.TaskBalancer;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SchedulerConfigurationServiceOrphanGrantTest {
   private ResourcePermissionService permissionService;
   private SchedulerConfigurationService service;
   private Principal principal;

   @BeforeEach
   void setUp() {
      permissionService = mock(ResourcePermissionService.class);
      service = new SchedulerConfigurationService(null, permissionService, null, null);
      principal = mock(Principal.class);
   }

   private static ResourcePermissionModel orphanedGrant() {
      ResourcePermissionTableModel entry = ResourcePermissionTableModel.builder()
         .identityID(new IdentityID("Advanced", "orga"))
         .type(Identity.Type.ROLE)
         .actions(EnumSet.of(ResourceAction.ACCESS))
         .build();

      return ResourcePermissionModel.builder()
         .permissions(Collections.singletonList(entry))
         .displayActions(EnumSet.of(ResourceAction.ACCESS))
         .securityEnabled(true)
         .requiresBoth(true) // the org's live permission.andCondition value, must survive unchanged
         .derivePermissionLabel("Use Parent Permissions")
         .grantReadToAllVisible(false)
         .build();
   }

   @Test
   void builderFromThenPermissions_resultsInEmptyPermissions() {
      ResourcePermissionModel base = orphanedGrant();

      ResourcePermissionModel cleared = ResourcePermissionModel.builder()
         .from(base)
         .permissions(Collections.emptyList())
         .build();

      assertTrue(cleared.permissions().isEmpty());
      assertEquals(base.requiresBoth(), cleared.requiresBoth());
   }

   @Test
   void clearsOrphanedGrant_keepsAndConditionUnchanged() throws Exception {
      ResourcePermissionModel base = orphanedGrant();
      when(permissionService.getTableModel(
         eq("QA-TR-orphanA"), eq(ResourceType.SCHEDULE_TIME_RANGE),
         eq(EnumSet.of(ResourceAction.ACCESS)), eq(principal)))
         .thenReturn(base);

      TimeRangeModel recreate = TimeRangeModel.builder()
         .name("QA-TR-orphanA")
         .startTime("08:00:00")
         .endTime("17:00:00")
         .defaultRange(false)
         .build(); // modified() and permissions() both omitted -> null, the plugin's plain-create shape

      try(MockedStatic<TimeRange> timeRange = mockStatic(TimeRange.class);
          var ignored = mockConstruction(TaskBalancer.class))
      {
         timeRange.when(TimeRange::getTimeRanges).thenReturn(new ArrayList<>());

         Method setTimeRanges = SchedulerConfigurationService.class
            .getDeclaredMethod("setTimeRanges", List.class, Principal.class);
         setTimeRanges.setAccessible(true);
         setTimeRanges.invoke(service, Collections.singletonList(recreate), principal);
      }

      ArgumentCaptor<ResourcePermissionModel> written = ArgumentCaptor.forClass(ResourcePermissionModel.class);
      verify(permissionService).setResourcePermissions(
         eq("QA-TR-orphanA"), eq(ResourceType.SCHEDULE_TIME_RANGE), written.capture(), eq(principal));

      assertTrue(written.getValue().permissions().isEmpty());
      assertEquals(base.requiresBoth(), written.getValue().requiresBoth());
   }
}
