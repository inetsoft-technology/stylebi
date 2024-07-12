/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security.user;

import inetsoft.sree.security.*;
import inetsoft.web.factory.DecodePathVariable;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class GroupController {
   @Autowired
   public GroupController(UserTreeService userTreeService) {
      this.userTreeService = userTreeService;
   }

   @PostMapping("/api/em/security/providers/{provider}/create-group")
   public EditGroupPaneModel createGroup(@DecodePathVariable("provider") String provider,
                                         @RequestBody CreateEntityRequest createRequest,
                                         Principal principal)
   {
      return userTreeService.createGroup(provider, createRequest.parentGroup(), principal);
   }

   @Secured(
      @RequiredPermission(resourceType = ResourceType.SECURITY_GROUP, actions = ResourceAction.ADMIN)
   )
   @PostMapping("/api/em/security/providers/{provider}/groups/{group}/")
   public void editGroup(@DecodePathVariable("provider") String provider,
                         @PermissionPath @DecodePathVariable("group") String group,
                         @RequestBody EditGroupPaneModel model,
                         Principal principal) throws Exception
   {
      IdentityID groupID = IdentityID.getIdentityIDFromKey(group);
      userTreeService.editGroup(provider, groupID, model, principal);
   }

   @GetMapping("/api/em/security/providers/{provider}/groups/{group}/")
   @Secured(
      @RequiredPermission(resourceType = ResourceType.SECURITY_GROUP, actions = ResourceAction.ADMIN)
   )
   public EditGroupPaneModel getGroupModel(@DecodePathVariable("provider") String provider,
                               @PermissionPath @DecodePathVariable(value = "group") String group,
                               Principal principal)
   {
      IdentityID groupID = IdentityID.getIdentityIDFromKey(group);
      return userTreeService.getGroupModel(provider, groupID, principal);
   }

   private final UserTreeService userTreeService;
}
