/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.security.ldap.*;
import inetsoft.report.internal.Util;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AuthenticationProviderModelLdapCredentialTest {
   @Test void genericSecretModeCarriesSecretIdAndHidesResolvedAccount() {
      LdapAuthenticationProviderModel model = read(new GenericLdapAuthenticationProvider(), true);

      assertTrue(model.useCredential());
      assertEquals("vault-secret", model.secretId());
      assertNull(model.adminID());
      assertNull(model.password());
   }

   @Test void activeDirectorySecretModeCarriesSecretIdAndHidesResolvedAccount() {
      LdapAuthenticationProviderModel model = read(new ADAuthenticationProvider(), true);

      assertTrue(model.useCredential());
      assertEquals("vault-secret", model.secretId());
      assertNull(model.adminID());
      assertNull(model.password());
   }

   @Test void literalModeIsUnchanged() {
      LdapAuthenticationProviderModel model = read(new GenericLdapAuthenticationProvider(), false);

      assertFalse(model.useCredential());
      assertNull(model.secretId());
      assertEquals("cn=resolved", model.adminID());
      assertEquals(Util.PLACEHOLDER_PASSWORD, model.password());
   }

   private static LdapAuthenticationProviderModel read(LdapAuthenticationProvider provider,
                                                       boolean useCredential)
   {
      provider.setHost("ldap.example.com");
      provider.setProtocol("ldap");
      provider.setPort(389);
      provider.setRootDn("dc=example,dc=com");
      provider.setUseCredential(useCredential);
      provider.setSecretId(useCredential ? "vault-secret" : null);
      provider.setLdapAdministrator("cn=resolved");
      provider.setSystemAdministratorRoles(new String[0]);

      return AuthenticationProviderModel.builder()
         .providerName("p").providerType(provider instanceof ADAuthenticationProvider ?
            SecurityProviderType.ACTIVE_DIRECTORY :
            SecurityProviderType.LDAP)
         .ldapProviderModel(provider)
         .build().ldapProviderModel();
   }
}
