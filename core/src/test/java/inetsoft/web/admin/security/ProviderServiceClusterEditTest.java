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
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79127: the EM add, edit, remove and reorder of a security provider read the list of this
 * node's runtime chain, changed it and saved it, so an edit saved by another node that this node
 * had not processed yet was overwritten. They now take the cluster lock and reload the chain file
 * before they read the list. In each test this node's chain is stale (it holds only the first
 * provider) while another node saved more providers, and the edit is only valid against the
 * providers of the file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ProviderServiceClusterEditTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final String AUTHZ_FILE = "authz-chain.json";

   private final List<AuthenticationChain> authcChains = new ArrayList<>();
   private final List<AuthorizationChain> authzChains = new ArrayList<>();
   private final Principal principal = mock(Principal.class);

   @AfterEach
   void tearDown() {
      authcChains.forEach(AuthenticationChain::tearDown);
      authzChains.forEach(AuthorizationChain::tearDown);
   }

   // authentication

   @Test
   void authenticationAddKeepsTheProvidersOfAnotherNode() throws Exception {
      AuthenticationChain local = staleAuthc("Adm", "X", "Z");

      authcService(local).addAuthenticationProvider(authcModel("B"), "B", principal);

      assertEquals(List.of("Adm", "X", "Z", "B"), authcFileNames());
      assertEquals(List.of("Adm", "X", "Z", "B"), names(local.getProviders()));
   }

   @Test
   void authenticationAddRejectsANameAnotherNodeAdded() throws Exception {
      AuthenticationChain local = staleAuthc("Adm", "X");
      AuthenticationProviderService service = authcService(local);

      // the early check on the stale chain passes, the check under the lock sees X
      assertThrows(MessageException.class,
                   () -> service.addAuthenticationProvider(authcModel("X"), "X", principal));

      assertEquals(List.of("Adm", "X"), authcFileNames());
   }

   @Test
   void authenticationEditStartsFromTheFile() throws Exception {
      AuthenticationChain local = staleAuthc("Adm", "X");

      // X is unknown to this node's runtime chain until the file is reloaded
      authcService(local).editAuthenticationProvider("X", authcModel("X"), principal);

      assertEquals(List.of("Adm", "X"), authcFileNames());
   }

   @Test
   void authenticationEditOfARemovedProviderIsRejected() throws Exception {
      AuthenticationChain local = staleAuthc("Adm");
      AuthenticationProviderService service = authcService(local);

      assertThrows(MessageException.class,
                   () -> service.editAuthenticationProvider("Gone", authcModel("Gone"), principal));

      assertEquals(List.of("Adm"), authcFileNames());
   }

   @Test
   void authenticationRemoveUsesTheIndexOfTheFile() throws Exception {
      AuthenticationChain local = staleAuthc("Adm", "X", "Z");

      authcService(local).removeAuthenticationProvider(1, "X", principal);

      assertEquals(List.of("Adm", "Z"), authcFileNames());
      assertEquals(List.of("Adm", "Z"), names(local.getProviders()));
   }

   @Test
   void authenticationReorderUsesTheListOfTheFile() throws Exception {
      AuthenticationChain local = staleAuthc("Adm", "X", "Z");

      // index 2 does not exist in this node's stale list
      authcService(local).reorderAuthenticationProviders(
         ProviderListReorderModel.builder().source(2).destination(0).build());

      assertEquals(List.of("Z", "Adm", "X"), authcFileNames());
   }

   // authorization

   @Test
   void authorizationAddKeepsTheProvidersOfAnotherNode() throws Exception {
      AuthorizationChain local = staleAuthz("Adm", "X", "Z");

      authzService(local).addAuthorizationProvider(authzModel("B"), "B", principal);

      assertEquals(List.of("Adm", "X", "Z", "B"), authzFileNames());
   }

   @Test
   void authorizationAddRejectsANameAnotherNodeAdded() throws Exception {
      AuthorizationChain local = staleAuthz("Adm", "X");
      AuthorizationProviderService service = authzService(local);

      assertThrows(MessageException.class,
                   () -> service.addAuthorizationProvider(authzModel("X"), "X", principal));

      assertEquals(List.of("Adm", "X"), authzFileNames());
   }

   @Test
   void authorizationEditStartsFromTheFile() throws Exception {
      AuthorizationChain local = staleAuthz("Adm", "X");

      authzService(local).editAuthorizationProvider("X", authzModel("X"), principal);

      assertEquals(List.of("Adm", "X"), authzFileNames());
   }

   @Test
   void authorizationRemoveUsesTheIndexOfTheFile() throws Exception {
      AuthorizationChain local = staleAuthz("Adm", "X", "Z");

      authzService(local).removeAuthorizationProvider(1, "X", principal);

      assertEquals(List.of("Adm", "Z"), authzFileNames());
   }

   @Test
   void authorizationReorderUsesTheListOfTheFile() throws Exception {
      AuthorizationChain local = staleAuthz("Adm", "X", "Z");

      authzService(local).reorderAuthorizationProviders(
         ProviderListReorderModel.builder().source(2).destination(0).build());

      assertEquals(List.of("Z", "Adm", "X"), authzFileNames());
   }

   // helpers

   /**
    * A chain that holds only the first provider while another node saved all of them.
    */
   private AuthenticationChain staleAuthc(String first, String... others) {
      AuthenticationChain local = new AuthenticationChain();
      authcChains.add(local);
      removeListener(AUTHC_FILE, local);
      local.setProviders(authc(first));

      AuthenticationChain other = new AuthenticationChain();
      authcChains.add(other);
      removeListener(AUTHC_FILE, other);
      List<String> all = new ArrayList<>(List.of(first));
      all.addAll(List.of(others));
      other.setProviders(authc(all.toArray(new String[0])));

      assertEquals(List.of(first), names(local.getProviders()), "the chain is not stale");
      return local;
   }

   private AuthorizationChain staleAuthz(String first, String... others) {
      AuthorizationChain local = new AuthorizationChain();
      authzChains.add(local);
      removeListener(AUTHZ_FILE, local);
      local.setProviders(authz(first));

      AuthorizationChain other = new AuthorizationChain();
      authzChains.add(other);
      removeListener(AUTHZ_FILE, other);
      List<String> all = new ArrayList<>(List.of(first));
      all.addAll(List.of(others));
      other.setProviders(authz(all.toArray(new String[0])));

      assertEquals(List.of(first), names(local.getProviders()), "the chain is not stale");
      return local;
   }

   private AuthenticationProviderService authcService(AuthenticationChain chain) {
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getAuthenticationChain()).thenReturn(Optional.of(chain));
      return new AuthenticationProviderService(engine, null, null, null);
   }

   private AuthorizationProviderService authzService(AuthorizationChain chain) {
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getAuthorizationChain()).thenReturn(Optional.of(chain));
      return new AuthorizationProviderService(engine, null);
   }

   private static AuthenticationProviderModel authcModel(String name) {
      return AuthenticationProviderModel.builder()
         .providerName(name)
         .providerType(SecurityProviderType.FILE)
         .build();
   }

   private static AuthorizationProviderModel authzModel(String name) {
      return AuthorizationProviderModel.builder()
         .providerName(name)
         .providerType(SecurityProviderType.FILE)
         .build();
   }

   /** What another node, or this one after a restart, would load from the file. */
   private List<String> authcFileNames() {
      AuthenticationChain fresh = new AuthenticationChain();
      authcChains.add(fresh);
      removeListener(AUTHC_FILE, fresh);
      return names(fresh.getProviders());
   }

   private List<String> authzFileNames() {
      AuthorizationChain fresh = new AuthorizationChain();
      authzChains.add(fresh);
      removeListener(AUTHZ_FILE, fresh);
      return names(fresh.getProviders());
   }

   private static void removeListener(String file, DataChangeListener listener) {
      DataSpace.getDataSpace().removeChangeListener(null, file, listener);
   }

   /** The first provider has a system administrator role, so a removal of the others is allowed. */
   private static List<AuthenticationProvider> authc(String... names) {
      List<AuthenticationProvider> list = new ArrayList<>();

      for(int i = 0; i < names.length; i++) {
         FileAuthenticationProvider provider =
            i == 0 ? new AdminProvider() : new FileAuthenticationProvider();
         provider.setProviderName(names[i]);
         list.add(provider);
      }

      return list;
   }

   private static List<AuthorizationProvider> authz(String... names) {
      List<AuthorizationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthorizationProvider provider = new FileAuthorizationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<String> names(List<? extends JsonConfigurableProvider> list) {
      return list.stream().map(JsonConfigurableProvider::getProviderName)
         .collect(Collectors.toList());
   }

   /** A file provider with a system administrator role that has a member. */
   public static class AdminProvider extends FileAuthenticationProvider {
      public AdminProvider() {
      }

      @Override
      public IdentityID[] getRoles() {
         return new IdentityID[] { new IdentityID("Admins", null) };
      }

      @Override
      public boolean isSystemAdministratorRole(IdentityID roleIdentity) {
         return true;
      }

      @Override
      public Identity[] getRoleMembers(IdentityID roleIdentity) {
         return new Identity[] { mock(Identity.class) };
      }
   }
}
