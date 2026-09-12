/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ScheduleTaskShowType {
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/change-show-type")
   public boolean getScheduleTaskShowType(Principal principal) {
      return getShowTasksAsList(principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PutMapping("/api/portal/schedule/change-show-type")
   public void setConfiguration(@RequestParam("showTasksAsList") String showTasksAsList,
                                Principal principal)
   {
      setShowTasksAsList(principal, showTasksAsList);
   }

   /**
    * Gets the list/folder view preference of the given user. The view is a per-user
    * preference; the property is only the installation-wide default used until the
    * user toggles the view themselves. That default is folder view, since
    * getBooleanProperty() yields false for a property that is not set.
    */
   private boolean getShowTasksAsList(Principal principal) {
      Object userSetting = UserEnv.getProperty(principal, SHOW_TYPE_USER_PROPERTY, null);

      return userSetting != null ? Boolean.parseBoolean(String.valueOf(userSetting))
         : SreeEnv.getBooleanProperty(SHOW_TYPE_PROPERTY);
   }

   /**
    * Saves the list/folder view preference of the given user. Anonymous users only get a
    * persisted preference if anonymous.userdata.save is enabled; otherwise UserEnv
    * discards the write and the installation-wide default keeps applying.
    */
   private void setShowTasksAsList(Principal principal, String showTasksAsList) {
      // normalize so that only "true"/"false" is stored, and so that an empty value is
      // never passed to UserEnv.setProperty(), which treats it as a removal
      UserEnv.setProperty(principal, SHOW_TYPE_USER_PROPERTY,
                          Boolean.parseBoolean(showTasksAsList) + "");
   }

   private static final String SHOW_TYPE_PROPERTY = "schedule.show.tasks.as.list";
   private static final String SHOW_TYPE_USER_PROPERTY = "portal.schedule.showTasksAsList";
}
