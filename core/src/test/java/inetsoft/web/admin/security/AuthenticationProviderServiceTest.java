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
package inetsoft.web.admin.security;

/*
 * Test strategy
 *
 * Regression coverage for bug #76358: getAuthenticationProvider(name) threw a raw
 * NullPointerException on an unknown provider name instead of a clean MessageException,
 * because the null-checking pattern-matching switch's "case null, default ->" arm still passed
 * the null selectedProvider into AuthenticationProviderModel.Builder.customProviderModel(),
 * which unconditionally dereferences it.
 *
 * Regression coverage for bug #76359: getProviderFromModel's DATABASE/CUSTOM branches had no
 * license check at all, even though getAuthenticationProvider's own advisory
 * dbProviderEnabled/customProviderEnabled flags show the product intends those types to be
 * enterprise-only. checkProviderTypeLicensed/isSameProviderType gate addAuthenticationProvider
 * (always a new provider) and editAuthenticationProvider (only when the edit changes the
 * provider's type into DATABASE/CUSTOM), while leaving same-type edits and the read-path
 * methods (getUsers, testConnection, etc., which share getProviderFromModel via
 * withProviderFromModel) untouched. Tests that need a DATABASE/CUSTOM provider to actually be
 * constructed successfully use CUSTOM with a trivial in-test AuthenticationProvider, since
 * createDatabaseProvider always attempts a real DatabaseAuthenticationProvider.testConnection()
 * that would require a live database.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.ldap.LdapAuthenticationProvider;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AuthenticationProviderServiceTest {

   @Mock private SecurityEngine securityEngine;
   @Mock private SimpMessagingTemplate messageTemplate;
   @Mock private Cluster cluster;
   @Mock private AuthenticationChain authenticationChain;
   @Mock private FileAuthenticationProvider fileAuthenticationProvider;
   @Mock private LdapAuthenticationProvider ldapAuthenticationProvider;
   @Mock private Principal principal;

   private AuthenticationProviderService service;
   private MockedStatic<LicenseManager> licenseManager;

   /** A minimal, real (non-mock) CUSTOM provider so createCustomProvider's reflective
    *  instantiation succeeds without any external I/O, unlike DATABASE which always attempts a
    *  real connection. */
   public static class TestCustomAuthenticationProvider extends AbstractAuthenticationProvider {
      @Override
      public void tearDown() {
      }

      @Override
      public boolean authenticate(IdentityID identityID, Object credential) {
         return false;
      }

      @Override
      public Organization getOrganization(String id) {
         return null;
      }

      @Override
      public String getOrgIdFromName(String name) {
         return null;
      }

      @Override
      public String getOrgNameFromID(String id) {
         return null;
      }

      @Override
      public String[] getOrganizationIDs() {
         return new String[0];
      }

      @Override
      public String[] getOrganizationNames() {
         return new String[0];
      }
   }

   @BeforeEach
   void setUp() {
      service = new AuthenticationProviderService(securityEngine, new ObjectMapper(),
                                                   messageTemplate, cluster);
   }

   @AfterEach
   void tearDown() {
      if(licenseManager != null) {
         licenseManager.close();
      }
   }

   private void mockLicense(boolean enterprise) {
      licenseManager = mockStatic(LicenseManager.class);
      licenseManager.when(LicenseManager::isEnterprise).thenReturn(enterprise);
   }

   private CustomProviderModel customModel() {
      return CustomProviderModel.builder()
         .className(TestCustomAuthenticationProvider.class.getName())
         .build();
   }

   // [addAuthenticationProvider: DATABASE, not licensed] a raw client can no longer create a
   // DATABASE provider under a non-enterprise license.
   @Test
   void addAuthenticationProvider_databaseType_notLicensed_throws() {
      mockLicense(false);
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("newDb")
         .providerType(SecurityProviderType.DATABASE)
         .build();

      MessageException exception = assertThrows(MessageException.class,
         () -> service.addAuthenticationProvider(model, "newDb", principal));

      assertTrue(exception.getMessage().toLowerCase().contains("license"));
      verify(authenticationChain, never()).setProviders(any());
   }

   // [addAuthenticationProvider: CUSTOM, not licensed] same gate applies to CUSTOM.
   @Test
   void addAuthenticationProvider_customType_notLicensed_throws() {
      mockLicense(false);
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("newCustom")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();

      MessageException exception = assertThrows(MessageException.class,
         () -> service.addAuthenticationProvider(model, "newCustom", principal));

      assertTrue(exception.getMessage().toLowerCase().contains("license"));
      verify(authenticationChain, never()).setProviders(any());
   }

   // [addAuthenticationProvider: copy scenario] copyAuthenticationProvider (controller) reads an
   // existing DATABASE/CUSTOM provider's model and calls addAuthenticationProvider under a new
   // name -- structurally identical to a fresh create (existingProvider is null), so it is
   // blocked the same way.
   @Test
   void addAuthenticationProvider_copyOfExistingDatabaseProvider_notLicensed_throws() {
      mockLicense(false);
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      AuthenticationProviderModel copyModel = AuthenticationProviderModel.builder()
         .providerName("existingDb (copy)")
         .providerType(SecurityProviderType.DATABASE)
         .build();

      assertThrows(MessageException.class,
         () -> service.addAuthenticationProvider(copyModel, "existingDb (copy)", principal));
   }

   // [editAuthenticationProvider: same-type edit, grandfathered] editing an unrelated field on an
   // already-existing CUSTOM provider, keeping its type unchanged, must NOT be blocked by the
   // license gate even under a non-enterprise license -- the gate is about introducing new
   // unlicensed exposure, not about revoking access to already-configured providers.
   @Test
   void editAuthenticationProvider_sameTypeUnrelatedFieldEdit_notLicensed_doesNotThrow()
      throws Exception
   {
      mockLicense(false);
      TestCustomAuthenticationProvider existing = new TestCustomAuthenticationProvider();
      existing.setProviderName("existingCustom");
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      // not a rename, so the "name already exists" pre-check short-circuits without consulting
      // authenticationChain.stream()
      when(authenticationChain.getProviders())
         .thenReturn(new ArrayList<>(List.of(existing)));

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("existingCustom")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();

      assertDoesNotThrow(() -> service.editAuthenticationProvider("existingCustom", model, principal));
      verify(authenticationChain).setProviders(any());
   }

   // [editAuthenticationProvider: rename, grandfathered] renaming an existing CUSTOM provider
   // (type unchanged) must NOT be blocked under a non-enterprise license either.
   @Test
   void editAuthenticationProvider_rename_sameType_notLicensed_doesNotThrow() throws Exception {
      mockLicense(false);
      TestCustomAuthenticationProvider existing = new TestCustomAuthenticationProvider();
      existing.setProviderName("oldName");
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.getProviders())
         .thenReturn(new ArrayList<>(List.of(existing)));
      when(authenticationChain.stream()).thenReturn(Stream.of(existing));

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("newName")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();

      assertDoesNotThrow(() -> service.editAuthenticationProvider("oldName", model, principal));
      verify(authenticationChain).setProviders(any());
   }

   // [editAuthenticationProvider: type conversion into DATABASE] converting an existing LDAP
   // provider's type to DATABASE is genuine new unlicensed exposure and must be blocked, just
   // like a fresh create.
   @Test
   void editAuthenticationProvider_typeConversionToDatabase_notLicensed_throws() {
      mockLicense(false);
      when(ldapAuthenticationProvider.getProviderName()).thenReturn("existingLdap");
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.getProviders())
         .thenReturn(new ArrayList<>(List.of(ldapAuthenticationProvider)));

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("existingLdap")
         .providerType(SecurityProviderType.DATABASE)
         .build();

      MessageException exception = assertThrows(MessageException.class,
         () -> service.editAuthenticationProvider("existingLdap", model, principal));

      assertTrue(exception.getMessage().toLowerCase().contains("license"));
      verify(authenticationChain, never()).setProviders(any());
   }

   // [read path: not gated] getUsers goes through the same getProviderFromModel/
   // withProviderFromModel path but is deliberately left ungated -- locks in that a future change
   // doesn't accidentally widen the create/edit gate back into the shared read path.
   @Test
   void getUsers_customType_notLicensed_doesNotThrow() throws Exception {
      mockLicense(false);

      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("readPathCustom")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();

      assertDoesNotThrow(() -> service.getUsers(model));
   }

   // [licensed: everything proceeds] under an enterprise license, add, same-type edit, and
   // type-conversion edits all succeed with no gating.
   @Test
   void addAndEditAuthenticationProvider_enterpriseLicensed_doesNotThrow() throws Exception {
      mockLicense(true);
      when(ldapAuthenticationProvider.getProviderName()).thenReturn("existingLdap");
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      AuthenticationProviderModel addModel = AuthenticationProviderModel.builder()
         .providerName("newCustom")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();
      assertDoesNotThrow(() -> service.addAuthenticationProvider(addModel, "newCustom", principal));

      when(authenticationChain.getProviders())
         .thenReturn(new ArrayList<>(List.of(ldapAuthenticationProvider)));
      AuthenticationProviderModel conversionModel = AuthenticationProviderModel.builder()
         .providerName("existingLdap")
         .providerType(SecurityProviderType.CUSTOM)
         .customProviderModel(customModel())
         .build();
      assertDoesNotThrow(
         () -> service.editAuthenticationProvider("existingLdap", conversionModel, principal));
   }

   // [getAuthenticationProvider: unknown name] no provider in the chain matches the requested
   // name -> a clean, named MessageException is thrown, not a NullPointerException.
   @Test
   void getAuthenticationProvider_unknownName_throwsMessageException() {
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      MessageException exception = assertThrows(MessageException.class,
                                                 () -> service.getAuthenticationProvider("missing"));

      assertTrue(exception.getMessage().contains("missing"));
   }

   // [getAuthenticationProvider: known provider] existing behavior is unaffected by the added
   // null guard.
   @Test
   void getAuthenticationProvider_knownFileProvider_returnsModel() {
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.of(fileAuthenticationProvider));
      when(fileAuthenticationProvider.getProviderName()).thenReturn("myProvider");

      AuthenticationProviderModel result = service.getAuthenticationProvider("myProvider");

      assertEquals(SecurityProviderType.FILE, result.providerType());
      assertEquals("myProvider", result.providerName());
   }
}
