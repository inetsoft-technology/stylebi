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

/*
 * Covers AbstractSecurityFilter.getSSODefaultRoleID() -- the mapping from a name in an SSO
 * default-role property to the IdentityID actually put on the session principal.
 *
 * Why this method is worth its own test class: the org stamping used to be an inline .map() inside
 * createSSOSession() that hardcoded Organization.getDefaultOrganizationID() for every name and
 * IdentityID(name, null) -- the GLOBAL system administrator role -- for the literal
 * "Administrator". That was written on the assumption that multi-tenant installs never reach it,
 * because getSSODefaultRole() returns an empty array under multi-tenancy (see community commit
 * fceb855f7, "this property currently only takes effect in non-multi-tenant environments").
 *
 * StyleBIGoogleSSOFilter (enterprise) overrides getSSODefaultRole() without that guard -- see the
 * javadoc there for why that is deliberate -- so the multi-tenant path IS reachable, and the
 * hardcoded default org / null org became a cross-tenant role grant and a server-wide admin grant
 * respectively. The stamping was extracted into getSSODefaultRoleID() so both branches can be
 * pinned here directly, without standing up a servlet filter chain.
 *
 * The single-tenant assertions are regression guards, not new behaviour: on a single-tenant install
 * the built-in Administrator exists ONLY as the global (null-org) role
 * (FileAuthenticationProvider seeds it as new IdentityID("Administrator", null)), so the null-org
 * special case is load-bearing for the existing "make all SSO users admin" configuration and must
 * keep working.
 *
 * Mocking notes:
 *  - SUtil.isMultiTenant() is a static reading SecurityEngine + LicenseManager + a SreeEnv
 *    property, so it is stubbed via mockStatic(SUtil.class, CALLS_REAL_METHODS) -- the same approach
 *    StyleBIGoogleSSOFilterTest uses -- leaving the rest of SUtil real.
 *  - getSecurityProvider() resolves through the STATIC SecurityEngine.getSecurity(), not a
 *    constructor-injected field, so SecurityEngine is mocked statically too.
 *  - isSystemAdministratorRole()/isOrgAdministratorRole() must be stubbed explicitly: they are
 *    interface DEFAULT methods and Mockito does not run default bodies on a mock. They are stubbed
 *    to model AuthenticationChain's override (exact name+org key must resolve), NOT the name-only
 *    interface default -- see stubRoleStore() for why that distinction decides the outcome.
 *  - SRPrincipal's constructor calls XSessionService.getService().createSessionID(), hence the
 *    third static mock.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.util.XSessionService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class AbstractSecurityFilterTest {

   private static final String TENANT_ORG = "acme";
   private static final String ROLE = "Analyst";

   @Mock
   private SecurityEngine securityEngine;
   @Mock
   private SecurityProvider securityProvider;

   private TestSecurityFilter filter;
   private MockedStatic<SecurityEngine> securityEngineMock;
   private MockedStatic<SUtil> sUtilMock;
   private MockedStatic<XSessionService> xSessionServiceMock;

   /**
    * AbstractSecurityFilter is abstract only because of Filter.doFilter(); everything this test
    * exercises is concrete on the base class.
    */
   private static final class TestSecurityFilter extends AbstractSecurityFilter {
      TestSecurityFilter(SessionLicenseServiceProvider provider, AuthenticationService service) {
         super(provider, service);
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
         throws IOException, ServletException
      {
         chain.doFilter(request, response);
      }
   }

   /**
    * Mimics StyleBIGoogleSSOFilter.getSSODefaultRole()'s override: no isMultiTenant() guard, just
    * a fixed configured list of role names. Used to exercise createSSOSession()'s integration with
    * getSSODefaultRoleID() (P1 scenarios 2/3 of the SSO test plan) without depending on
    * StyleBIGoogleSSOFilter's own package (inetsoft.enterprise.sso) or its JWT/JOSE machinery --
    * createSSOSession() is declared protected on AbstractSecurityFilter (inetsoft.web.security), so
    * a real StyleBIGoogleSSOFilter instance could not call it from a test in the enterprise package
    * anyway; the base-class integration point is what matters here, not the Google subclass itself.
    */
   private static final class GoogleLikeFilter extends AbstractSecurityFilter {
      private final String[] defaultRoles;

      GoogleLikeFilter(SessionLicenseServiceProvider provider, AuthenticationService service,
                        String[] defaultRoles)
      {
         super(provider, service);
         this.defaultRoles = defaultRoles;
      }

      @Override
      protected String[] getSSODefaultRole() {
         return defaultRoles;
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
         throws IOException, ServletException
      {
         chain.doFilter(request, response);
      }
   }

   private static HttpServletRequest requestWithSession(HttpSession session) {
      HttpServletRequest request = mock(HttpServletRequest.class);
      lenient().when(request.getSession(true)).thenReturn(session);
      lenient().when(request.getHeader(anyString())).thenReturn(null);
      lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
      lenient().when(request.getLocale()).thenReturn(Locale.US);
      return request;
   }

   @BeforeEach
   void setUp() {
      filter = new TestSecurityFilter(
         mock(SessionLicenseServiceProvider.class), mock(AuthenticationService.class));

      securityEngineMock = mockStatic(SecurityEngine.class);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      sUtilMock = mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);

      XSessionService mockSessionService = mock(XSessionService.class);
      lenient().when(mockSessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceMock = mockStatic(XSessionService.class);
      xSessionServiceMock.when(XSessionService::getService).thenReturn(mockSessionService);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
      sUtilMock.close();
      xSessionServiceMock.close();
   }

   // ── fixtures ─────────────────────────────────────────────────────────────

   private void stubMultiTenant(boolean multiTenant) {
      sUtilMock.when(SUtil::isMultiTenant).thenReturn(multiTenant);
   }

   private static SRPrincipal principal(String orgId) {
      SRPrincipal principal = new SRPrincipal(new IdentityID("alice@example.com", orgId));
      principal.setOrgId(orgId);
      return principal;
   }

   /**
    * Models how the real provider answers isSystemAdministratorRole/isOrgAdministratorRole, which is
    * NOT the name-only interface default: AbstractSecurityProvider delegates to AuthenticationChain,
    * whose override is
    * {@code stream().filter(p -> p.getRole(roleId) != null).findFirst()...} -- so the role must
    * exist at that EXACT name+org key (FileAuthenticationProvider.getRole does
    * roleStorage.get(id.convertToKey())) before its sysAdmin/orgAdmin flag is even consulted.
    *
    * <p>That distinction is the whole point of these tests: the built-in admin roles are seeded
    * org-less (FileAuthenticationProvider seeds IdentityID("Administrator", null) and
    * IdentityID("Organization Administrator", null)), so an org-scoped key never resolves to them
    * and a guard that only asks about the org-scoped form silently never fires. Stubbing this
    * name-only would make the admin-refusal tests pass against an implementation that does nothing.
    *
    * @param sysAdminRoles roles that exist AND are flagged system administrator.
    * @param orgAdminRoles roles that exist AND are flagged organization administrator.
    */
   private void stubRoleStore(Set<IdentityID> sysAdminRoles, Set<IdentityID> orgAdminRoles) {
      lenient().when(securityProvider.isSystemAdministratorRole(any(IdentityID.class)))
         .thenAnswer(inv -> sysAdminRoles.contains(inv.getArgument(0, IdentityID.class)));
      lenient().when(securityProvider.isOrgAdministratorRole(any(IdentityID.class)))
         .thenAnswer(inv -> orgAdminRoles.contains(inv.getArgument(0, IdentityID.class)));
   }

   /** The out-of-the-box role store: both admin roles exist only in org-less (global) form. */
   private void stubBuiltInRoleStore() {
      stubRoleStore(Set.of(new IdentityID("Administrator", null)),
                    Set.of(new IdentityID("Organization Administrator", null)));
   }

   // ── single tenant: unchanged behaviour ───────────────────────────────────

   @Test
   void singleTenant_ordinaryRole_stampedWithDefaultOrg() {
      stubMultiTenant(false);

      assertEquals(new IdentityID(ROLE, Organization.getDefaultOrganizationID()),
                   filter.getSSODefaultRoleID(ROLE, principal(
                      Organization.getDefaultOrganizationID())));
   }

   @Test
   void singleTenant_administrator_stampedWithNullOrg() {
      stubMultiTenant(false);

      IdentityID roleID = filter.getSSODefaultRoleID(
         "Administrator", principal(Organization.getDefaultOrganizationID()));

      assertNotNull(roleID, "single-tenant sso.default.roles=Administrator must still grant admin");
      assertNull(roleID.orgID,
                 "the built-in Administrator role only exists as the global (null-org) role");
      assertEquals("Administrator", roleID.name);
   }

   @Test
   void singleTenant_neverConsultsSecurityProvider() {
      stubMultiTenant(false);

      filter.getSSODefaultRoleID(ROLE, principal(Organization.getDefaultOrganizationID()));

      // the single-tenant branch must stay a pure mapping -- no provider lookup, so it cannot
      // start failing on installs where security is not fully initialized yet
      verify(securityProvider, never()).isSystemAdministratorRole(any());
      verify(securityProvider, never()).isOrgAdministratorRole(any());
   }

   // ── multi tenant: scoped to the signing-in org ───────────────────────────

   @Test
   void multiTenant_ordinaryRole_scopedToSigningInOrg_notDefaultOrg() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();

      IdentityID roleID = filter.getSSODefaultRoleID(ROLE, principal(TENANT_ORG));

      assertEquals(new IdentityID(ROLE, TENANT_ORG), roleID);
      assertNotEquals(Organization.getDefaultOrganizationID(), roleID.orgID,
                      "a tenant user must not be granted a role owned by the default organization");
   }

   @Test
   void multiTenant_selfSignupOrg_scopedToSelfOrg() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();

      // Google self-signup lands brand new users in the SELF org; the starting role has to follow
      assertEquals(new IdentityID(ROLE, Organization.getSelfOrganizationID()),
                   filter.getSSODefaultRoleID(
                      ROLE, principal(Organization.getSelfOrganizationID())));
   }

   /*
    * These two are the regression guards for the review finding that the first cut of this fix got
    * wrong: the guard asked the provider only about IdentityID(name, org). The built-in admin roles
    * are seeded org-less, that key never resolves, AuthenticationChain's override therefore answered
    * false, and both names sailed through un-dropped. Both tests fail against that version.
    */

   @Test
   void multiTenant_builtInAdministrator_isDropped_despiteBeingSeededOrgLess() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();

      assertNull(filter.getSSODefaultRoleID("Administrator", principal(TENANT_ORG)),
                 "the global system administrator role must never be grantable to a tenant");
   }

   @Test
   void multiTenant_builtInOrganizationAdministrator_isDropped_despiteBeingSeededOrgLess() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();

      assertNull(filter.getSSODefaultRoleID("Organization Administrator", principal(TENANT_ORG)));
   }

   @Test
   void multiTenant_orgScopedSysAdminRole_isDropped() {
      stubMultiTenant(true);
      // an operator-created role that exists in the tenant's own org but carries the sysAdmin flag:
      // DefaultCheckPermissionStrategy short-circuits on ANY resolved sysadmin role, so this one
      // would bypass permission checks in every organization, not just this tenant's
      stubRoleStore(Set.of(new IdentityID(ROLE, TENANT_ORG)), Set.of());

      assertNull(filter.getSSODefaultRoleID(ROLE, principal(TENANT_ORG)));
   }

   @Test
   void multiTenant_orgScopedOrgAdminRole_isAllowed() {
      stubMultiTenant(true);
      // deliberate policy, not an oversight: an organization-administrator role scoped to the user's
      // OWN org confers admin rights only inside that org, which is what a self-service signup flow
      // wants for the first user of a new organization. Only the org-less built-in is refused.
      stubRoleStore(Set.of(), Set.of(new IdentityID(ROLE, TENANT_ORG)));

      assertEquals(new IdentityID(ROLE, TENANT_ORG),
                   filter.getSSODefaultRoleID(ROLE, principal(TENANT_ORG)));
   }

   @Test
   void multiTenant_unknownRole_passedThroughUnresolved() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();

      // matches the base-class contract: a name that does not resolve is not an error. It survives
      // into XPrincipal.getAllRoles() but AuthenticationProvider.getRole() returns null for it, so
      // it carries no permissions.
      assertEquals(new IdentityID("NoSuchRole", TENANT_ORG),
                   filter.getSSODefaultRoleID("NoSuchRole", principal(TENANT_ORG)));
   }

   @Test
   void multiTenant_principalWithNoOrg_isDropped() {
      stubMultiTenant(true);
      stubBuiltInRoleStore();
      SRPrincipal noOrg = new SRPrincipal(new IdentityID("alice@example.com", null));

      // neither available guess is safe -- a null org is the global scope, and the default
      // organization is the most privileged tenant -- so this degrades to granting nothing. The
      // Google path always supplies an org, so this is the defensive path only.
      assertNull(filter.getSSODefaultRoleID(ROLE, noOrg));
   }

   @Test
   void multiTenant_noSecurityProvider_stillScopesToSigningInOrg() {
      stubMultiTenant(true);
      when(securityEngine.getSecurityProvider()).thenReturn(null);

      // the admin check is skipped when there is no provider, but the org scoping must not be
      assertEquals(new IdentityID(ROLE, TENANT_ORG),
                   filter.getSSODefaultRoleID(ROLE, principal(TENANT_ORG)));
   }

   // ── cross-filter integration matrix (SSO test plan P1): createSSOSession() end to end ──────
   //
   // getSSODefaultRoleID() above is proven correct in isolation. These two tests prove that
   // createSSOSession() -- the method that actually merges its result onto the login principal --
   // wires it up correctly for a filter whose getSSODefaultRole() override has NO isMultiTenant()
   // guard, which is exactly StyleBIGoogleSSOFilter's shape (see its javadoc for why that absence
   // is deliberate). This is Bug #76161's original end-to-end scenario, not just the unit-level
   // guard tested above.

   @Test
   void multiTenant_googleStyleFilter_administratorPropertyValue_isRejected() throws Exception {
      stubMultiTenant(true);
      stubBuiltInRoleStore();
      GoogleLikeFilter googleFilter = new GoogleLikeFilter(
         mock(SessionLicenseServiceProvider.class), mock(AuthenticationService.class),
         new String[] { "Administrator" });
      HttpSession session = mock(HttpSession.class);
      HttpServletRequest request = requestWithSession(session);

      SRPrincipal result = googleFilter.createSSOSession(request, principal(TENANT_ORG));

      assertFalse(Arrays.asList(result.getRoles()).contains(new IdentityID("Administrator", null)),
         "a Google-style filter without the isMultiTenant() guard must still have the " +
            "\"Administrator\" property value rejected by getSSODefaultRoleID() end to end -- this " +
            "is Bug #76161's original self-registration-to-server-admin scenario");
   }

   @Test
   void multiTenant_googleStyleFilter_selfOrgSignup_getsOrgScopedOrganizationAdministrator()
      throws Exception
   {
      stubMultiTenant(true);
      // NOTE (found while writing this test, not assumed up front): the built-in
      // "Organization Administrator" role is seeded GLOBALLY (org-less, IdentityID(name, null)) --
      // see isAssignableSSODefaultRole()'s first guard -- so it is refused by name for EVERY org,
      // including the user's own new SELF org; there is no special case for self-signup there.
      // What the class javadoc's "org-scoped ... is deliberately still allowed" comment (and the
      // existing multiTenant_orgScopedOrgAdminRole_isAllowed test above) actually describes is an
      // ORG-SCOPED role -- e.g. one an operator created specifically inside that org, or (as here)
      // one the security provider resolves as existing only within the org being signed into, not
      // the global built-in role sharing its name. So the role store below flags the org-admin bit
      // on the SELF-org-scoped identity, not the null-org one.
      stubRoleStore(Set.of(),
         Set.of(new IdentityID("Organization Administrator", Organization.getSelfOrganizationID())));
      GoogleLikeFilter googleFilter = new GoogleLikeFilter(
         mock(SessionLicenseServiceProvider.class), mock(AuthenticationService.class),
         new String[] { "Organization Administrator" });
      HttpSession session = mock(HttpSession.class);
      HttpServletRequest request = requestWithSession(session);

      SRPrincipal result = googleFilter.createSSOSession(
         request, principal(Organization.getSelfOrganizationID()));

      assertTrue(Arrays.asList(result.getRoles()).contains(new IdentityID(
            "Organization Administrator", Organization.getSelfOrganizationID())),
         "an org-scoped Organization Administrator role resolved within the user's own SELF org " +
            "must still be granted end to end -- this must not be collateral damage from the " +
            "Administrator-name rejection fix");
   }
}
