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
package inetsoft.sree.security.ldap;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] readConfiguration(...)
 *             intent: missing AD-specific configuration fields should fall back
 *                     to the built-in AD defaults
 *             actual: config.get(...) may return null, so the subsequent
 *                     asText()/asBoolean()/asInt() dereferences can throw before
 *                     any default is applied
 *             see   : readConfiguration_missingAdSpecificFields_fallsBackToDefaults
 */

/*
 * Cases deferred - require integration context:
 *
 * createContext() -> needs a real LDAP endpoint or constructor interception for
 * InitialLdapContext to verify the generated JNDI environment.
 *
 * searchRoles(...) / searchPrimaryRole(...) -> depend on live LDAP responses via
 * LdapAuthenticationClient and provider cache initialization.
 *
 * checkParameters() -> exercises network/authentication failure translation and
 * belongs in LDAP integration tests alongside GenericLdapAuthenticationProviderTest.
 */

import inetsoft.uql.util.Identity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for AD-specific configuration and helper logic in
 * {@link ADAuthenticationProvider}. These tests intentionally avoid LDAP runtime
 * behavior and focus on branches owned by the AD subclass itself.
 */
@Tag("core")
class ADAuthenticationProviderTest {
   private TestableADAuthenticationProvider provider;

   @BeforeEach
   void setUp() {
      provider = new TestableADAuthenticationProvider();
      provider.setRootDn("dc=example,dc=com");
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @Nested
   class DefaultConfiguration {
      @Test
      void constructor_setsAdSpecificDefaults() {
         assertAll(
            () -> assertEquals("(objectclass=user)", provider.getUserSearch(),
               "AD provider must default to the AD user object filter"),
            () -> assertEquals("(&(objectclass=user)(sAMAccountName={0}))",
               provider.getUserRolesSearch(),
               "AD provider must default to the AD user-to-role lookup filter"),
            () -> assertEquals("cn=Administrator,cn=Users", provider.getLdapAdministrator(),
               "AD provider must default to the standard AD administrator DN")
         );
      }

      @Test
      void exposesAdSpecificAttributeNames() {
         assertAll(
            () -> assertEquals("sAMAccountName", provider.getUserAttribute(),
               "AD provider must use sAMAccountName as the user identifier"),
            () -> assertEquals("cn", provider.getRoleAttribute(),
               "AD provider must use cn as the role name attribute"),
            () -> assertEquals("ou", provider.getGroupAttribute(),
               "AD provider must use ou as the group name attribute"),
            () -> assertEquals("mail", provider.getMailAttribute(),
               "AD provider must use mail as the email attribute"),
            () -> assertEquals("distinguishedName", provider.getEntryDnAttributeForTest(),
               "AD provider must expose distinguishedName as the entry DN attribute")
         );
      }

      @Test
      void exposesAdSpecificSearchFilters() {
         assertAll(
            () -> assertEquals("(objectclass=group)", provider.getRoleSearch(),
               "AD role search must target group objects"),
            () -> assertEquals("(objectclass=organizationalunit)", provider.getGroupSearch(),
               "AD group search must target organizational units")
         );
      }

      @Test
      void isVirtual_returnsFalse() {
         assertFalse(provider.isVirtual(),
            "ADAuthenticationProvider is a real directory, not a virtual provider");
      }

      @Test
      void supportsNamingListener_returnsFalse() {
         assertFalse(provider.supportsNamingListenerForTest(),
            "AD provider must not advertise naming-listener support");
      }
   }

   @Nested
   class RolesSearchDispatch {
      static Stream<Arguments> rolesSearchCases() {
         return Stream.of(
            Arguments.of(Identity.USER, "(&(objectclass=user)(sAMAccountName={0}))"),
            Arguments.of(Identity.ROLE, "(&(objectclass=group)(sAMAccountName={0}))"),
            Arguments.of(Identity.GROUP, "(&(objectclass=organizationalunit)(sAMAccountName={0}))"),
            Arguments.of(-1, null)
         );
      }

      @ParameterizedTest
      @MethodSource("rolesSearchCases")
      void getRolesSearch_dispatchesByIdentityType(int identityType, String expectedFilter) {
         assertEquals(expectedFilter, provider.getRolesSearchForTest(identityType),
            "AD provider must map each identity type to the expected role lookup filter");
      }
   }

   @Nested
   class BaseNormalization {
      @Test
      void setUserBase_emptyValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setUserBase("");

         assertEquals("cn=Users,dc=example,dc=com", provider.getUserBase(),
            "Empty user base must fall back to the default Users container under the root DN");
      }

      @Test
      void setUserBase_nullValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setUserBase(null);

         assertEquals("cn=Users,dc=example,dc=com", provider.getUserBase(),
            "Null user base must fall back to the default Users container under the root DN");
      }

      @Test
      void setGroupBase_emptyValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setGroupBase("");

         assertEquals("cn=Users,dc=example,dc=com", provider.getGroupBase(),
            "Empty group base must fall back to the default Users container under the root DN");
      }

      @Test
      void setGroupBase_nullValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setGroupBase(null);

         assertEquals("cn=Users,dc=example,dc=com", provider.getGroupBase(),
            "Null group base must fall back to the default Users container under the root DN");
      }

      @Test
      void setRoleBase_emptyValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setRoleBase("");

         assertEquals("cn=Users,dc=example,dc=com", provider.getRoleBase(),
            "Empty role base must fall back to the default Users container under the root DN");
      }

      @Test
      void setRoleBase_nullValue_fallsBackToUsersContainerUnderRootDn() {
         provider.setRoleBase(null);

         assertEquals("cn=Users,dc=example,dc=com", provider.getRoleBase(),
            "Null role base must fall back to the default Users container under the root DN");
      }

      @Test
      void setUserBase_mergesRootDnWhenMissing() {
         provider.setUserBase("ou=people");

         assertEquals("ou=people,dc=example,dc=com", provider.getUserBase(),
            "User base must append the root DN when the caller passes a relative branch");
      }

      @Test
      void setGroupBase_preservesExistingRootDn() {
         provider.setGroupBase("ou=groups,dc=example,dc=com");

         assertEquals("ou=groups,dc=example,dc=com", provider.getGroupBase(),
            "Group base must not duplicate the root DN when it is already present");
      }

      @Test
      void setRoleBase_mergesEachSegmentInMultiBaseInput() {
         provider.setRoleBase("ou=groups;ou=admins,dc=example,dc=com");

         assertEquals("ou=groups,dc=example,dc=com;ou=admins,dc=example,dc=com",
            provider.getRoleBase(),
            "Role base must merge the root DN into each semicolon-delimited segment");
      }
   }

   @Nested
   class SearchFallbacks {
      @Test
      void setUserSearch_emptyValue_restoresDefaultUserSearch() {
         provider.setUserSearch("");

         assertEquals("(objectclass=user)", provider.getUserSearch(),
            "Empty user search must restore the AD default user filter");
      }

      @Test
      void setUserSearch_nullValue_restoresDefaultUserSearch() {
         provider.setUserSearch(null);

         assertEquals("(objectclass=user)", provider.getUserSearch(),
            "Null user search must restore the AD default user filter");
      }

      @Test
      void setUserSearch_customValue_preservesCallerValue() {
         provider.setUserSearch("(objectClass=person)");

         assertEquals("(objectClass=person)", provider.getUserSearch(),
            "Custom user search must not be overwritten by the AD default");
      }

      @Test
      void setUserRolesSearch_emptyValue_restoresDefaultRolesSearch() {
         provider.setUserRolesSearch("");

         assertEquals("(&(objectclass=user)(sAMAccountName={0}))",
            provider.getUserRolesSearch(),
            "Empty user-roles search must restore the AD default");
      }

      @Test
      void setUserRolesSearch_nullValue_restoresDefaultRolesSearch() {
         provider.setUserRolesSearch(null);

         assertEquals("(&(objectclass=user)(sAMAccountName={0}))",
            provider.getUserRolesSearch(),
            "Null user-roles search must restore the AD default");
      }

      @Test
      void setUserRolesSearch_customValue_preservesCallerValue() {
         provider.setUserRolesSearch("(&(objectClass=group)(member={1}))");

         assertEquals("(&(objectClass=group)(member={1}))", provider.getUserRolesSearch(),
            "Custom user-roles search must not be overwritten by the AD default");
      }
   }

   @Nested
   class DnHelpers {
      static Stream<Arguments> dnStringCases() {
         return Stream.of(
            Arguments.of("cn=Developers,dc=example,dc=com", "cn=Developers,dc=example,dc=com"),
            Arguments.of("CN=Developers,DC=example,DC=com", "CN=Developers,DC=example,DC=com"),
            Arguments.of("cn=Developers,ou=groups", null),
            Arguments.of(null, null)
         );
      }

      @ParameterizedTest
      @MethodSource("dnStringCases")
      void getDNString_returnsOnlyDirectoryStyleDnsWithDcSuffix(String inputDn, String expectedDn) {
         assertEquals(expectedDn, provider.getDnStringForTest(inputDn),
            "AD provider must only accept DNs that contain a dc= suffix");
      }
   }

   @Nested
   class ReadConfigurationBoundaries {
      private final ObjectMapper mapper = new ObjectMapper();

      @Test
      void readConfiguration_emptyStrings_restoreAdDefaultsAndRootDnBasedBases() {
         ObjectNode config = createBaseConfiguration();
         config.put("rootDn", "dc=example,dc=com");
         config.put("userSearch", "");
         config.put("userRolesSearch", "");
         config.put("userBase", "");
         config.put("groupBase", "");
         config.put("roleBase", "");

         provider.readConfiguration(config);

         assertAll(
            () -> assertEquals("(objectclass=user)", provider.getUserSearch(),
               "Empty configured user search must restore the AD default filter"),
            () -> assertEquals("(&(objectclass=user)(sAMAccountName={0}))",
               provider.getUserRolesSearch(),
               "Empty configured user-roles search must restore the AD default filter"),
            () -> assertEquals("cn=Users,dc=example,dc=com", provider.getUserBase(),
               "Empty configured user base must resolve to the default Users container"),
            () -> assertEquals("cn=Users,dc=example,dc=com", provider.getGroupBase(),
               "Empty configured group base must resolve to the default Users container"),
            () -> assertEquals("cn=Users,dc=example,dc=com", provider.getRoleBase(),
               "Empty configured role base must resolve to the default Users container")
         );
      }

      @Test
      void readConfiguration_customValues_preserveExplicitAdOverrides() {
         ObjectNode config = createBaseConfiguration();
         config.put("rootDn", "dc=example,dc=com");
         config.put("userSearch", "(objectClass=person)");
         config.put("userRolesSearch", "(&(objectClass=group)(member={1}))");
         config.put("userBase", "ou=people");
         config.put("groupBase", "ou=groups,dc=example,dc=com");
         config.put("roleBase", "ou=roles");

         provider.readConfiguration(config);

         assertAll(
            () -> assertEquals("(objectClass=person)", provider.getUserSearch(),
               "Explicit user search must be preserved"),
            () -> assertEquals("(&(objectClass=group)(member={1}))", provider.getUserRolesSearch(),
               "Explicit user-roles search must be preserved"),
            () -> assertEquals("ou=people,dc=example,dc=com", provider.getUserBase(),
               "Relative user base must merge the configured root DN"),
            () -> assertEquals("ou=groups,dc=example,dc=com", provider.getGroupBase(),
               "Absolute group base must not duplicate the root DN"),
            () -> assertEquals("ou=roles,dc=example,dc=com", provider.getRoleBase(),
               "Relative role base must merge the configured root DN")
         );
      }

      @Test
      @Disabled("Suspect 1: readConfiguration assumes required fields are present and dereferences null JsonNodes; Fix: use path() or null-safe defaults before asText()/asBoolean()/asInt()")
      void readConfiguration_missingAdSpecificFields_fallsBackToDefaults() {
         ObjectNode config = createBaseConfiguration();
         config.remove("userSearch");
         config.remove("userRolesSearch");
         config.remove("userBase");
         config.remove("groupBase");
         config.remove("roleBase");

         assertDoesNotThrow(() -> provider.readConfiguration(config),
            "Missing AD-specific fields should fall back to defaults instead of throwing");
      }

      private ObjectNode createBaseConfiguration() {
         ObjectNode config = mapper.createObjectNode();
         config.put("protocol", "ldap");
         config.put("searchSubtree", false);
         config.put("validateContextAttr", "objectclass");
         config.put("validateContextBaseDn", "");
         config.put("validateContextSearch", "(objectclass=*)");
         config.put("rootDn", "");
         config.put("host", "localhost");
         config.put("port", 389);
         config.put("useCredential", false);
         config.put("credential", "");
         ArrayNode sysAdminRoles = mapper.createArrayNode();
         config.set("sysAdminRoles", sysAdminRoles);
         config.put("userSearch", "(objectclass=user)");
         config.put("userRolesSearch", "(&(objectclass=user)(sAMAccountName={0}))");
         config.put("userBase", "ou=people");
         config.put("groupBase", "ou=groups");
         config.put("roleBase", "ou=roles");
         return config;
      }
   }

   private static class TestableADAuthenticationProvider extends ADAuthenticationProvider {
      String getRolesSearchForTest(int identityType) {
         return getRolesSearch(identityType);
      }

      String getDnStringForTest(String dn) {
         return getDNString(dn);
      }

      String getEntryDnAttributeForTest() {
         return getEntryDNAttribute();
      }

      boolean supportsNamingListenerForTest() {
         return supportsNamingListener();
      }
   }
}
