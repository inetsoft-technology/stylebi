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
package inetsoft.uql.rest.auth;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.rest.json.RestJsonDataSource;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76658: with kerberos constrained delegation the identity that is impersonated when the
 * request is made is derived from the calling user, and the endpoint applies its own per-user
 * authorization. The query cache is process wide, so the data source must report a cache
 * discriminator for exactly those configurations, and none of the others.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class KerberosCacheDiscriminatorTest {
   @BeforeEach
   void saveContextPrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restoreContextPrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void noDiscriminatorWhenAuthenticationIsNotKerberos() {
      RestJsonDataSource ds = dataSource(AuthType.BASIC, true, KerberosImpersonationType.PRINCIPAL);
      ThreadContext.setContextPrincipal(principal("user1", null));

      assertNull(ds.getCacheDiscriminator(),
         "credentials are data source level for every non-kerberos authentication type, so the " +
         "result is not user dependent");
   }

   @Test
   void noDiscriminatorWithoutConstrainedDelegation() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, false, KerberosImpersonationType.PRINCIPAL);
      ThreadContext.setContextPrincipal(principal("user1", null));

      assertNull(ds.getCacheDiscriminator(),
         "without constrained delegation no identity is impersonated, so the result is not user " +
         "dependent");
   }

   @Test
   void noDiscriminatorForStaticImpersonation() {
      RestJsonDataSource ds = dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.STATIC);
      ds.setImpersonatePrincipal("svc");
      ThreadContext.setContextPrincipal(principal("user1", null));

      assertNull(ds.getCacheDiscriminator(),
         "a static identity is the same for every user, so those results stay shared");
   }

   @Test
   void principalImpersonationDiscriminatesByUser() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PRINCIPAL);

      ThreadContext.setContextPrincipal(principal("user1", null));
      String user1 = ds.getCacheDiscriminator();

      ThreadContext.setContextPrincipal(principal("user2", null));
      String user2 = ds.getCacheDiscriminator();

      assertNotNull(user1);
      assertNotEquals(user1, user2,
         "each user impersonates their own identity, so their results must not be shared");
   }

   @Test
   void propertyImpersonationDiscriminatesByPropertyValue() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PROPERTY);
      ds.setImpersonatePrincipal("kerberosId");

      ThreadContext.setContextPrincipal(principal("user1", "id1"));
      String user1 = ds.getCacheDiscriminator();

      ThreadContext.setContextPrincipal(principal("user2", "id2"));
      String user2 = ds.getCacheDiscriminator();

      assertNotNull(user1);
      assertNotEquals(user1, user2);
   }

   @Test
   void propertyImpersonationDiscriminatesByUserWhenThePropertyIsMissing() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PROPERTY);
      ds.setImpersonatePrincipal("kerberosId");

      // neither principal has the property, so the identity cannot be resolved. The
      // discriminator still has to keep the two users apart
      ThreadContext.setContextPrincipal(principal("user1", null));
      String user1 = ds.getCacheDiscriminator();

      ThreadContext.setContextPrincipal(principal("user2", null));
      String user2 = ds.getCacheDiscriminator();

      assertNotNull(user1);
      assertNotEquals(user1, user2);
   }

   @Test
   void theSameUserAlwaysGetsTheSameDiscriminator() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PRINCIPAL);

      ThreadContext.setContextPrincipal(principal("user1", null));
      String first = ds.getCacheDiscriminator();
      ThreadContext.setContextPrincipal(principal("user1", null));
      String second = ds.getCacheDiscriminator();

      assertEquals(first, second, "the same user re-running the query must still hit the cache");
   }

   @Test
   void impersonatedIdentityMatchesTheImpersonationType() {
      RestJsonDataSource ds = dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.STATIC);
      ds.setImpersonatePrincipal("svc");
      Principal user = principal("user1", "id1");
      ThreadContext.setContextPrincipal(user);

      assertEquals("svc", ds.getImpersonatedIdentity());

      ds.setImpersonationType(KerberosImpersonationType.PRINCIPAL);
      assertEquals(user.getName(), ds.getImpersonatedIdentity());

      ds.setImpersonationType(KerberosImpersonationType.PROPERTY);
      ds.setImpersonatePrincipal("kerberosId");
      assertEquals("id1", ds.getImpersonatedIdentity());
   }

   @Test
   void impersonatedIdentityIsNullWithoutAPrincipal() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PRINCIPAL);
      ThreadContext.setContextPrincipal(null);

      assertNull(ds.getImpersonatedIdentity(),
         "a missing principal must not fail the request with an unhandled exception");
   }

   @Test
   void impersonatedIdentityIsNullForAnUnsetImpersonationType() {
      RestJsonDataSource ds = dataSource(AuthType.KERBEROS, true, null);
      ThreadContext.setContextPrincipal(principal("user1", "id1"));

      assertNull(ds.getImpersonatedIdentity(),
         "an unset impersonation type must be reported, not fail with an unhandled exception");
      assertNull(ds.getCacheDiscriminator(),
         "nothing is impersonated, so there is nothing to discriminate by");
   }

   @Test
   void propertyImpersonationStillDiscriminatesForANonSRPrincipal() {
      RestJsonDataSource ds =
         dataSource(AuthType.KERBEROS, true, KerberosImpersonationType.PROPERTY);
      ds.setImpersonatePrincipal("kerberosId");

      // a principal that carries no properties at all
      ThreadContext.setContextPrincipal((Principal) () -> "user1");
      String user1Identity = ds.getImpersonatedIdentity();
      String user1 = ds.getCacheDiscriminator();

      ThreadContext.setContextPrincipal((Principal) () -> "user2");
      String user2 = ds.getCacheDiscriminator();

      assertNull(user1Identity,
         "a principal that cannot carry the property must not fail with a class cast");
      assertNotNull(user1);
      assertNotEquals(user1, user2,
         "the calling user must still keep the cache entries apart");
   }

   private static RestJsonDataSource dataSource(AuthType authType, boolean constrainedDelegation,
                                                KerberosImpersonationType impersonationType)
   {
      RestJsonDataSource ds = new RestJsonDataSource();
      ds.setName("Rest1");
      ds.setAuthType(authType);
      ds.setConstrainedDelegation(constrainedDelegation);
      ds.setImpersonationType(impersonationType);
      return ds;
   }

   /**
    * @param kerberosId the value of the "kerberosId" property, or null to leave it unset.
    */
   private static Principal principal(String name, String kerberosId) {
      SRPrincipal principal = new SRPrincipal(
         new IdentityID(name, "orgA"), new IdentityID[0], new String[0], "orgA", 0L);

      if(kerberosId != null) {
         principal.setProperty("kerberosId", kerberosId);
      }

      return principal;
   }

   private Principal oldPrincipal;
}
