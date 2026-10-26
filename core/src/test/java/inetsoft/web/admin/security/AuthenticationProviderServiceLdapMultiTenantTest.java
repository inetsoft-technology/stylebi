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

import inetsoft.sree.security.*;
import inetsoft.sree.security.ldap.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79234: LDAP and Active Directory providers cannot be used with multi-tenancy, so adding
 * one, copying one or turning another provider into one is refused on the server while
 * multi-tenancy is on. An existing LDAP provider stays editable and removable.
 *
 *  [add LDAP, MT on]            refused before the model is validated, chain unchanged
 *  [add LDAP, MT off]           passes the guard
 *  [add FILE, MT on]            allowed
 *  [edit other -> LDAP, MT on]  refused, chain unchanged
 *  [edit LDAP -> LDAP, MT on]   allowed (legacy state), also a rename
 *  [edit unknown name, MT on]   the existing "does not exist" error
 *  [remove LDAP, MT on]         allowed
 */
@Tag("core")
class AuthenticationProviderServiceLdapMultiTenantTest {
   private static final String NOT_ALLOWED = "em.security.multitenant.ldapProviderNotAllowed";

   private AuthenticationChain chain;
   private List<AuthenticationProvider> providers;
   private AuthenticationProviderService service;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      chain = mock(AuthenticationChain.class);
      providers = new ArrayList<>();
      when(securityEngine.getAuthenticationChain()).thenReturn(java.util.Optional.of(chain));
      when(chain.getProviders()).thenReturn(providers);
      // the chain is a mock: run the edit the way SecurityChain.editProviders() does (Bug #79127)
      doAnswer(invocation -> {
         invocation.<SecurityChain.ChainEdit<?>>getArgument(0).run();
         return null;
      }).when(chain).editProviders(any());

      service = spy(new AuthenticationProviderService(securityEngine, null, null, null));
      service.setMultiTenantSupplier(() -> true);
   }

   @Test
   void addGenericLdapIsRefusedUnderMultiTenancy() {
      // no ldapProviderModel: the guard must run before the model is read
      assertRefused(() -> service.addAuthenticationProvider(
         model("ldap", SecurityProviderType.LDAP), "ldap", principal));
      verify(chain, never()).setProviders(any());
   }

   @Test
   void addActiveDirectoryIsRefusedUnderMultiTenancy() {
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("ad")
         .providerType(SecurityProviderType.LDAP)
         .ldapProviderModel(LdapAuthenticationProviderModel.builder()
                               .ldapServer(SecurityProviderType.ACTIVE_DIRECTORY)
                               .hostName("h").protocol("ldap").hostPort(389)
                               .rootDN("dc=x").build())
         .build();

      assertRefused(() -> service.addAuthenticationProvider(model, "ad", principal));
      verify(chain, never()).setProviders(any());
   }

   @Test
   void copyOfLdapProviderIsRefusedUnderMultiTenancy() {
      // the copy endpoint adds the model of an existing LDAP provider under a new name
      AuthenticationProviderModel copy = AuthenticationProviderModel.builder()
         .providerName("ldap-copy")
         .oldName("ldap")
         .providerType(SecurityProviderType.LDAP)
         .build();

      assertRefused(() -> service.addAuthenticationProvider(copy, "ldap-copy", principal));
      verify(chain, never()).setProviders(any());
   }

   @Test
   void addLdapPassesTheGuardWhenMultiTenancyIsOff() {
      service.setMultiTenantSupplier(() -> false);

      // the model is incomplete, so it fails later; it must not fail on the guard
      Exception e = assertThrows(Exception.class, () -> service.addAuthenticationProvider(
         model("ldap", SecurityProviderType.LDAP), "ldap", principal));
      assertNotEquals(message(NOT_ALLOWED), e.getMessage());
   }

   @Test
   void addFileProviderIsAllowedUnderMultiTenancy() throws Exception {
      service.addAuthenticationProvider(model("file2", SecurityProviderType.FILE), "file2",
                                        principal);

      assertEquals(1, providers.size());
      verify(chain).setProviders(any());
   }

   @Test
   void editOtherProviderIntoLdapIsRefusedUnderMultiTenancy() {
      doReturn(new FileAuthenticationProvider()).when(service).getProviderByName("file");

      assertRefused(() -> service.editAuthenticationProvider(
         "file", model("file", SecurityProviderType.LDAP), principal));
      verify(chain, never()).writeLock();
      verify(chain, never()).setProviders(any());
   }

   @Test
   void editExistingLdapProviderIsAllowedUnderMultiTenancy() {
      doReturn(mock(GenericLdapAuthenticationProvider.class)).when(service).getProviderByName("ldap");

      // the incomplete model fails inside getProviderFromModel, past the guard
      Exception e = assertThrows(Exception.class, () -> service.editAuthenticationProvider(
         "ldap", model("ldap", SecurityProviderType.LDAP), principal));
      assertNotEquals(message(NOT_ALLOWED), e.getMessage());
   }

   @Test
   void renameOfExistingAdProviderIsAllowedUnderMultiTenancy() {
      doReturn(mock(ADAuthenticationProvider.class)).when(service).getProviderByName("ad");

      Exception e = assertThrows(Exception.class, () -> service.editAuthenticationProvider(
         "ad", model("ad2", SecurityProviderType.LDAP), principal));
      assertNotEquals(message(NOT_ALLOWED), e.getMessage());
   }

   @Test
   void editOfUnknownProviderReportsItDoesNotExist() {
      doReturn(null).when(service).getProviderByName("nope");

      MessageException e = assertThrows(MessageException.class, () ->
         service.editAuthenticationProvider("nope", model("nope", SecurityProviderType.LDAP),
                                            principal));
      assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
   }

   @Test
   void removeOfLdapProviderIsAllowedUnderMultiTenancy() throws Exception {
      AuthenticationProvider ldap = mock(GenericLdapAuthenticationProvider.class);
      when(ldap.getProviderName()).thenReturn("ldap");
      AuthenticationProvider file = mock(FileAuthenticationProvider.class);
      IdentityID admin = new IdentityID("admin", "host-org");
      when(file.getRoles()).thenReturn(new IdentityID[] { admin });
      when(file.isSystemAdministratorRole(admin)).thenReturn(true);
      when(file.getRoleMembers(admin)).thenReturn(new inetsoft.uql.util.Identity[] { new User(admin) });
      providers.add(ldap);
      providers.add(file);

      service.removeAuthenticationProvider(0, "ldap", principal);

      assertEquals(List.of(file), providers);
      verify(chain).setProviders(any());
   }

   private void assertRefused(org.junit.jupiter.api.function.Executable action) {
      MessageException e = assertThrows(MessageException.class, action);
      assertEquals(message(NOT_ALLOWED), e.getMessage());
   }

   private static String message(String key) {
      return Catalog.getCatalog().getString(key);
   }

   private static AuthenticationProviderModel model(String name, SecurityProviderType type) {
      return AuthenticationProviderModel.builder().providerName(name).providerType(type).build();
   }
}
