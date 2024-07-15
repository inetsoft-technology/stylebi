/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.presentation;

import inetsoft.sree.security.*;
import inetsoft.web.admin.presentation.model.GetAllIdentitiesResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Define viewsheet css file as session attribute.
 *
 * Creates a map of username -> file to be referenced later in the session
 */
@RestController
public class LookAndFeelSettingsController<K extends String, V extends MultipartFile> {
   @Autowired
   public LookAndFeelSettingsController(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   @GetMapping("/api/em/presentation/look-and-feel/identities")
   public GetAllIdentitiesResponse getIdentities(Principal principal) {
      return GetAllIdentitiesResponse.builder()
         .users(getUsers(principal))
         .groups(getGroups(principal))
         .roles(getRoles(principal))
         .build();
   }

   private List<IdentityID> getUsers(Principal principal) {
      return Arrays.stream(securityProvider.getUsers())
         .filter(u -> checkPermission(u.convertToKey(), ResourceType.SECURITY_USER, principal))
         .collect(Collectors.toList());
   }

   private List<IdentityID> getGroups(Principal principal) {
      return Arrays.stream(securityProvider.getGroups())
         .filter(u -> checkPermission(u.convertToKey(), ResourceType.SECURITY_GROUP, principal))
         .collect(Collectors.toList());
   }

   private List<IdentityID> getRoles(Principal principal) {
      return Arrays.stream(securityProvider.getRoles())
         .filter(u -> checkPermission(u.convertToKey(), ResourceType.SECURITY_USER, principal))
         .collect(Collectors.toList());
   }

   private boolean checkPermission(String identity, ResourceType type, Principal principal) {
      return securityProvider.checkPermission(principal, type, identity, ResourceAction.ADMIN);
   }

   private final SecurityProvider securityProvider;
}
