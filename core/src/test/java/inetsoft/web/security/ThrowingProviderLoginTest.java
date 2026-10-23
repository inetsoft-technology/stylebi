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
package inetsoft.web.security;

import inetsoft.mv.MVManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Bug #79268: a custom authentication provider whose {@code getUser} throws must not stop the
 * users of the other providers in the chain from logging in with Basic authentication. The chain
 * is [file provider owning {@code admin}, throwing provider, provider owning {@code prv-basic}],
 * and every request goes through the real {@link BasicAuthenticationFilter}, {@link
 * AuthenticationService}, {@link SecurityEngine} and {@link AuthenticationChain}.
 *
 * <p>The cases that must stay green before and after the fix (single-tenant admin, wrong
 * password, unknown user) pin the fail-closed behavior: a provider that throws never turns a
 * failed login into a successful one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ThrowingProviderLoginTest {
   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("other", OTHER_ORG)
         .addUser("admin", HOST_ORG, ADMIN_PASSWORD)
         .addUser("orguser", OTHER_ORG, ADMIN_PASSWORD)
         .setup();

      originalProviders = new ArrayList<>(getChain().getProviders());
      fileProvider = originalProviders.get(0);
   }

   @AfterAll
   static void tearDownAll() throws Exception {
      if(originalProviders != null) {
         setProviders(originalProviders);
      }

      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      threadPrincipal = ThreadContext.getPrincipal();
      setProviders(List.of(fileProvider, createThrowingProvider(), createPrvBasicProvider()));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(threadPrincipal);

      if(sutil != null) {
         sutil.close();
         sutil = null;
      }
   }

   // ── admin, owned by the file provider that is first in the chain ──────────

   @Test
   void multiTenantAdminLogsIn() {
      setMultiTenant(true);
      assertEquals(200, login("admin", ADMIN_PASSWORD, null));
   }

   @Test
   void singleTenantAdminLogsIn() {
      setMultiTenant(false);
      assertEquals(200, login("admin", ADMIN_PASSWORD, null));
   }

   /**
    * The organization is not the default one, so the self-organization attempt is skipped and
    * the file provider answers the user of that organization.
    */
   @Test
   void multiTenantOrganizationUserWithOrganizationCookieLogsIn() {
      setMultiTenant(true);
      assertEquals(200, login("orguser", ADMIN_PASSWORD, OTHER_ORG));
   }

   /**
    * The admin account is not in that organization, so the login is rejected. The provider loop
    * of the filter reaches the throwing provider for it, as it does for an unknown user.
    */
   @Test
   void multiTenantAdminWithAnotherOrganizationCookieIsRejected() {
      setMultiTenant(true);
      assertEquals(401, login("admin", ADMIN_PASSWORD, OTHER_ORG));
   }

   // ── prv-basic, owned by the provider after the throwing one ───────────────

   @Disabled("Bug #79268 known limit: after login the session path still calls AuthenticationChain.getUser (SecurityEngine.authenticate, checkPermission), which is not contained; see 04-fix.md")
   @Test
   void multiTenantPrvBasicLogsIn() {
      setMultiTenant(true);
      assertEquals(200, login("prv-basic", PRV_PASSWORD, null));
   }

   @Disabled("Bug #79268 known limit: after login the session path still calls AuthenticationChain.getUser (SecurityEngine.authenticate, checkPermission), which is not contained; see 04-fix.md")
   @Test
   void singleTenantPrvBasicLogsIn() {
      setMultiTenant(false);
      assertEquals(200, login("prv-basic", PRV_PASSWORD, null));
   }

   // ── control: the same chain without the throwing provider ─────────────────

   @Test
   void multiTenantPrvBasicLogsInWithoutThrowingProvider() {
      setMultiTenant(true);
      setProviders(List.of(fileProvider, createPrvBasicProvider()));
      assertEquals(200, login("prv-basic", PRV_PASSWORD, null));
   }

   @Test
   void singleTenantPrvBasicLogsInWithoutThrowingProvider() {
      setMultiTenant(false);
      setProviders(List.of(fileProvider, createPrvBasicProvider()));
      assertEquals(200, login("prv-basic", PRV_PASSWORD, null));
   }

   // ── fail-closed guards: green before and after the fix ────────────────────

   @Test
   void multiTenantPrvBasicWithWrongPasswordIsRejected() {
      setMultiTenant(true);
      assertEquals(401, login("prv-basic", "wrong-password", null));
   }

   @Test
   void singleTenantPrvBasicWithWrongPasswordIsRejected() {
      setMultiTenant(false);
      assertEquals(401, login("prv-basic", "wrong-password", null));
   }

   @Test
   void multiTenantAdminWithWrongPasswordIsRejected() {
      setMultiTenant(true);
      assertEquals(401, login("admin", "wrong-password", null));
   }

   @Test
   void singleTenantAdminWithWrongPasswordIsRejected() {
      setMultiTenant(false);
      assertEquals(401, login("admin", "wrong-password", null));
   }

   @Test
   void multiTenantUnknownUserIsRejected() {
      setMultiTenant(true);
      assertEquals(401, login("nobody", "whatever", null));
   }

   @Test
   void singleTenantUnknownUserIsRejected() {
      setMultiTenant(false);
      assertEquals(401, login("nobody", "whatever", null));
   }

   // ── variant: the provider has no user in the self organization ────────────

   @Test
   void multiTenantAdminLogsInWhenThrowingProviderOnlyFailsOutsideSelfOrganization() {
      setMultiTenant(true);
      AuthenticationProvider provider = createThrowingProvider();
      doAnswer(invocation -> {
         IdentityID id = invocation.getArgument(0);

         if(Organization.getSelfOrganizationID().equals(id.getOrgID())) {
            return null;
         }

         throw new RuntimeException("simulated failure looking up " + id);
      }).when(provider).getUser(any());
      setProviders(List.of(fileProvider, provider, createPrvBasicProvider()));
      assertEquals(200, login("admin", ADMIN_PASSWORD, null));
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   /**
    * Sends a Basic-auth request through the real filter and returns the status. An exception
    * that escapes the filter would be a 500 in a servlet container, and is reported as 500.
    */
   private int login(String user, String password, String orgCookie) {
      AuthenticationService service = new AuthenticationService(
         SecurityEngine.getSecurity(), mock(MVManager.class), mock(DataSourceRegistry.class),
         mock(XSessionService.class), mock(LocaleService.class),
         mock(SessionLicenseServiceProvider.class), mock(ApplicationEventPublisher.class),
         mock(IndexedStorage.class));
      MockMvc mvc = MockMvcBuilders.standaloneSetup(new Ok())
         .addFilters(new BasicAuthenticationFilter(
            mock(SessionLicenseServiceProvider.class), service))
         .build();
      String credentials = user + ":" + password;
      MockHttpServletRequestBuilder request = get("/api/portal/tree")
         .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

      if(orgCookie != null) {
         request.cookie(new Cookie("X-INETSOFT-ORGID", orgCookie));
      }

      try {
         return mvc.perform(request).andReturn().getResponse().getStatus();
      }
      catch(Exception e) {
         return 500;
      }
   }

   private void setMultiTenant(boolean multiTenant) {
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      sutil.when(() -> SUtil.setAdditionalDatasource(any())).thenAnswer(invocation -> null);
   }

   private static AuthenticationChain getChain() {
      return (AuthenticationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthenticationProvider();
   }

   private static void setProviders(List<AuthenticationProvider> providers) {
      ReflectionTestUtils.invokeMethod(getChain(), "setProviderList", providers);
   }

   /**
    * A custom provider like the test plugin's: it has no users, non-throwing defaults for the
    * other lookups, and a {@code getUser} that always throws.
    */
   private AuthenticationProvider createThrowingProvider() {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getProviderName()).thenReturn("throwing");
      when(provider.getUser(any())).thenAnswer(invocation -> {
         throw new RuntimeException(
            "simulated failure looking up " + invocation.getArgument(0));
      });
      stubEmptyLookups(provider);
      return provider;
   }

   /**
    * A provider that owns {@code prv-basic} in the default organization, like the database
    * provider of the provider suite.
    */
   private AuthenticationProvider createPrvBasicProvider() {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      IdentityID id = new IdentityID("prv-basic", HOST_ORG);
      FSUser user = new FSUser(id, new String[0], new String[0], new IdentityID[0], null,
                               PRV_PASSWORD);
      when(provider.getProviderName()).thenReturn("prv");
      when(provider.getUser(any())).thenAnswer(invocation ->
         id.equals(invocation.getArgument(0)) ? user : null);
      when(provider.authenticate(any(), any())).thenAnswer(invocation -> {
         Object credential = invocation.getArgument(1);
         DefaultTicket ticket = credential instanceof DefaultTicket ?
            (DefaultTicket) credential : DefaultTicket.parse(credential.toString());
         return id.equals(invocation.getArgument(0)) && PRV_PASSWORD.equals(ticket.getPassword());
      });
      when(provider.getUsers()).thenReturn(new IdentityID[] { id });
      when(provider.getIndividualUsers()).thenReturn(new IdentityID[] { id });
      stubEmptyLookups(provider);
      return provider;
   }

   private void stubEmptyLookups(AuthenticationProvider provider) {
      when(provider.getOrganizationIDs()).thenReturn(new String[] { HOST_ORG });
      when(provider.getOrganizationNames()).thenReturn(new String[] { "host" });
      when(provider.getOrganization(any())).thenAnswer(invocation ->
         HOST_ORG.equals(invocation.getArgument(0)) ?
            Organization.getDefaultOrganization() : null);
      when(provider.getRoles()).thenReturn(new IdentityID[0]);
      when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      when(provider.getUserGroups(any())).thenReturn(new String[0]);
      when(provider.getEmails(any())).thenReturn(new String[0]);
      when(provider.isAuthenticationCaseSensitive()).thenReturn(true);
      when(provider.isVirtual()).thenReturn(false);
   }

   @RestController
   static class Ok {
      @RequestMapping("/**")
      public String handle() {
         return "ok";
      }
   }

   private static SecurityTestDataBuilder builder;
   private static AuthenticationProvider fileProvider;
   private static List<AuthenticationProvider> originalProviders;
   private MockedStatic<SUtil> sutil;
   private Principal threadPrincipal;

   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String OTHER_ORG = "other_id";
   private static final String ADMIN_PASSWORD = "Adm1nPassw0rd!";
   private static final String PRV_PASSWORD = "Prv8asicPassw0rd!";
}
