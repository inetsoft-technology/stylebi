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
package inetsoft.web.admin.ai.providers;

/*
 * Test strategy (bug 76655, F2)
 *
 * ProviderProjection.projectAuthenticationProvider used to fall through to
 * projectFileProvider(name) for anything that wasn't LDAP-with-a-non-null-model -- silently
 * mislabeling DATABASE/CUSTOM (and, as a bonus case found during refute, LDAP with a null
 * ldapProviderModel()) as "type=FILE" and discarding every type-specific field. Executed-confirmed
 * during diagnosis/refute via a throwaway javac/JUnit probe against this exact class; these are
 * the permanent regression tests for that fix. projectAuthorizationProvider had the identical bug
 * for CUSTOM authorization providers (found while fixing the sibling method), covered here too.
 */

import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ProviderProjectionTest {
   @Test void projectAuthenticationProvider_database_projectsOwnTypeAndFields() {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("dbProvider")
         .providerType(SecurityProviderType.DATABASE)
         .dbProviderModel(DatabaseAuthenticationProviderModel.builder()
            .driver("org.postgresql.Driver")
            .url("jdbc:postgresql://host/db")
            .hashAlgorithm("NONE")
            .build())
         .build();

      String projection = ProviderProjection.projectAuthenticationProvider(model);

      assertTrue(projection.contains("type=DATABASE;"));
      assertTrue(projection.contains("driver=org.postgresql.Driver;"));
      assertTrue(projection.contains("url=jdbc:postgresql://host/db;"));
      assertFalse(projection.contains("type=FILE;"));
   }

   @Test void projectAuthenticationProvider_custom_projectsOwnTypeAndFields() {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("customProvider")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(CustomProviderModel.builder()
            .className("com.example.MyAuthenticationProvider")
            .jsonConfiguration("{}")
            .build())
         .build();

      String projection = ProviderProjection.projectAuthenticationProvider(model);

      assertTrue(projection.contains("type=CUSTOM;"));
      assertTrue(projection.contains("className=com.example.MyAuthenticationProvider;"));
      assertFalse(projection.contains("type=FILE;"));
   }

   // Bonus case found during refute: a null ldapProviderModel() on an LDAP-typed model also fell
   // through to the FILE catch-all under the old code -- same root cause, different trigger.
   @Test void projectAuthenticationProvider_ldapWithNullModel_reportsLdapNotFile() {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("ldapNoSpec")
         .providerType(SecurityProviderType.LDAP)
         .build();

      String projection = ProviderProjection.projectAuthenticationProvider(model);

      assertTrue(projection.contains("type=LDAP;"));
      assertFalse(projection.contains("type=FILE;"));
   }

   @Test void projectAuthenticationProvider_file_stillProjectsAsFile() {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("fileProvider")
         .providerType(SecurityProviderType.FILE)
         .build();

      String projection = ProviderProjection.projectAuthenticationProvider(model);

      assertTrue(projection.contains("type=FILE;"));
   }

   @Test void projectAuthenticationProvider_null_returnsNull() {
      assertNull(ProviderProjection.projectAuthenticationProvider(null));
   }

   // Same root cause, sibling method: AuthorizationProviderModel can be CUSTOM too, and
   // projectAuthorizationProvider unconditionally fell through to FILE before this fix.
   @Test void projectAuthorizationProvider_custom_projectsOwnTypeAndFields() {
      AuthorizationProviderModel model = AuthorizationProviderModel.builder()
         .providerName("customAuthz")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(CustomProviderModel.builder()
            .className("com.example.MyAuthorizationProvider")
            .build())
         .build();

      String projection = ProviderProjection.projectAuthorizationProvider(model);

      assertTrue(projection.contains("type=CUSTOM;"));
      assertTrue(projection.contains("className=com.example.MyAuthorizationProvider;"));
      assertFalse(projection.contains("type=FILE;"));
   }

   @Test void projectAuthorizationProvider_file_stillProjectsAsFile() {
      AuthorizationProviderModel model = AuthorizationProviderModel.builder()
         .providerName("fileAuthz")
         .providerType(SecurityProviderType.FILE)
         .build();

      String projection = ProviderProjection.projectAuthorizationProvider(model);

      assertTrue(projection.contains("type=FILE;"));
   }
}
