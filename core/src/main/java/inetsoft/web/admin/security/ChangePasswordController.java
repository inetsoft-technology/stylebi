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
package inetsoft.web.admin.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ChangePasswordController {
   @Autowired
   public ChangePasswordController(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   @PutMapping("/api/em/currentuser/password/verify")
   public boolean verifyOldPassword(@RequestBody ChangePasswordRequest request,
                                    Principal principal)
   {
      AuthenticationProvider authc = getAuthenticationProvider(principal);
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      return authc.authenticate(pId,
         new DefaultTicket(pId, request.password()));
   }

   @PutMapping("/api/em/currentuser/password")
   public void changePassword(@RequestBody ChangePasswordRequest request, Principal principal) {
      AuthenticationProvider authc = getAuthenticationProvider(principal);

      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      User user = authc.getUser(pId);

      if(user instanceof FSUser) {
         SUtil.setPassword((FSUser) user, request.password());
         ((EditableAuthenticationProvider) authc).addUser(user);
      }
      else {
         throw new IllegalStateException("Unsupported user type");
      }
   }

   @NotNull
   private AuthenticationProvider getAuthenticationProvider(Principal principal) {
      AuthenticationProvider authc = securityProvider.getAuthenticationProvider();
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if(authc instanceof AuthenticationChain) {
         AuthenticationChain chain = (AuthenticationChain) authc;
         authc = chain.stream()
            .filter(p -> p instanceof EditableAuthenticationProvider)
            .filter(p -> p.getUser(pId) != null)
            .findFirst()
            .orElse(null);
      }

      if(!(authc instanceof EditableAuthenticationProvider)) {
         throw new IllegalStateException("The authentication provider is not editable");
      }

      return authc;
   }

   private final SecurityProvider securityProvider;
}
