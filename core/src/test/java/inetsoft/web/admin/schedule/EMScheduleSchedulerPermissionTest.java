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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79866, the EM schedule task, folder, cycle and distribution endpoints must require the
 * SCHEDULER permission as their Portal twins and the EM edit/new/save endpoints do. The EM
 * component permission alone only hides the schedule pages in the UI.
 *
 * <p>Reads the {@code @Secured} annotations that the security aspect enforces, so no Spring
 * context is needed.
 */
@Tag("core")
class EMScheduleSchedulerPermissionTest {
   private static final List<Class<?>> CONTROLLERS = List.of(
      EMScheduleController.class, EMScheduleTaskFolderController.class,
      EMScheduleBatchActionController.class, ScheduleCycleController.class,
      EMScheduleTaskActionController.class, EMScheduleTaskController.class);

   // getUsersModel: not a task operation, the user/group list also backs the General and
   // Security settings pages, so it is deliberately OR-gated and must not require SCHEDULER.
   // getEmailTree: left on the EM permission (all EM users), it only lists the identities the
   // caller administers, for the email pickers.
   private static final Set<String> EXEMPT = Set.of("getUsersModel", "getEmailTree");

   @Test
   void everyEmScheduleEndpointRequiresScheduler() {
      List<String> missing = new ArrayList<>();
      int checked = 0;

      for(Class<?> cls : CONTROLLERS) {
         for(Method method : cls.getDeclaredMethods()) {
            if(!isEndpoint(method) || EXEMPT.contains(method.getName())) {
               continue;
            }

            checked++;
            Secured secured = method.getAnnotation(Secured.class);

            if(secured == null || !"AND".equals(secured.operator()) || !requiresScheduler(secured)) {
               missing.add(cls.getSimpleName() + "." + method.getName());
            }
         }
      }

      assertTrue(checked > 40, "expected to inspect the EM schedule endpoints, got " + checked);
      assertTrue(missing.isEmpty(), "EM endpoints without SCHEDULER ACCESS: " + missing);
   }

   @Test
   void usersModelStaysOrGatedWithoutScheduler() throws Exception {
      Method method = EMScheduleController.class.getDeclaredMethod(
         "getUsersModel", java.security.Principal.class);
      Secured secured = method.getAnnotation(Secured.class);

      assertEquals("OR", secured.operator());

      for(RequiredPermission permission : secured.value()) {
         assertNotEquals(ResourceType.SCHEDULER, permission.resourceType());
      }
   }

   private static boolean isEndpoint(Method method) {
      return method.isAnnotationPresent(GetMapping.class) ||
         method.isAnnotationPresent(PostMapping.class) ||
         method.isAnnotationPresent(RequestMapping.class);
   }

   private static boolean requiresScheduler(Secured secured) {
      for(RequiredPermission permission : secured.value()) {
         if(permission.resourceType() == ResourceType.SCHEDULER &&
            "*".equals(permission.resource()) &&
            Arrays.asList(permission.actions()).contains(ResourceAction.ACCESS))
         {
            return true;
         }
      }

      return false;
   }
}
