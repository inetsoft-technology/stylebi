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
package inetsoft.web.portal.controller;

/*
 * Test strategy
 *
 * Class type: behavioral - which organization the unauthenticated login page is rendered for.
 *
 * showLoginPage() picks the welcome page/banner and the "isNotTenantServer" flag (which, together
 * with selfSignUpEnabled, shows the self-signup link) from the organization recorded for the
 * request: the X-INETSOFT-ORGID cookie, else the organization named by the host
 * (SUtil.getLoginOrganization), else the default organization.
 *
 * Bug #78476: a request with no Cookie header at all (getCookies() == null) skipped the host
 * fallback, so a direct first request to an org's /login.html got the global banner and the
 * self-signup link, while the same request with any unrelated cookie got the org's page.
 *
 *  ├─ [A] org sub-domain, no cookies             -> org banner, signup link hidden (Bug #78476)
 *  ├─ [B] org sub-domain, unrelated cookie       -> org banner, signup link hidden (unchanged)
 *  ├─ [C] org sub-domain, org cookie = host-org  -> cookie wins over the host
 *  ├─ [D] host-org sub-domain, no cookies        -> global page, signup link shown
 *  ├─ [E] unknown sub-domain, no cookies         -> global page, signup link shown
 *  ├─ [F] single-tenant, org sub-domain, no/unrelated cookies -> global page, signup link shown
 *  ├─ [G] org with no welcome page of its own, no cookies -> global page, signup link hidden
 *  └─ [H] orgLocation=path, org in the path, no cookies -> org banner, signup link hidden
 *
 * Real LoginController and real SUtil.getLoginOrganization; SUtil is a CALLS_REAL_METHODS static
 * mock with only isMultiTenant stubbed, SecurityEngine.getSecurity() returns a mock that lists the
 * organizations, and LicenseManager.isEnterprise() is stubbed so selfSignUpEnabled is true.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.portal.model.LoginBannerModel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LoginControllerRecordedOrgTest {
   private static final String HOST_ORG = "host-org";
   private static final String TENANT_ORG = "org1";
   private static final String NO_PAGE_ORG = "org3";
   private static final String GLOBAL_BANNER = "GLOBAL banner";
   private static final String TENANT_BANNER = "org1 banner";
   private static final String ORG_COOKIE = "X-INETSOFT-ORGID";

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;
   private Principal savedPrincipal;
   private LoginController controller;

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(null);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(securityEngine.getOrganizations()).thenReturn(new String[] { HOST_ORG, TENANT_ORG, NO_PAGE_ORG });
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.isSelfSignupEnabled()).thenReturn(true);
      when(provider.getOrgNameFromID(HOST_ORG))
         .thenReturn(Organization.getDefaultOrganizationName());
      when(provider.getOrgNameFromID(TENANT_ORG)).thenReturn("Org One");
      when(provider.getOrgNameFromID(NO_PAGE_ORG)).thenReturn("Org Three");

      securityEngineStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      licenseManagerStatic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(true);
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      PortalThemesManager themes = mock(PortalThemesManager.class);
      when(themes.getWelcomePage()).thenReturn(welcomePage(GLOBAL_BANNER));
      when(themes.getWelcomePage(TENANT_ORG)).thenReturn(welcomePage(TENANT_BANNER));

      controller = new LoginController(securityEngine, mock(CustomThemesManager.class), themes);
   }

   @AfterEach
   void tearDown() {
      if(sUtilStatic != null) {
         sUtilStatic.close();
      }

      if(licenseManagerStatic != null) {
         licenseManagerStatic.close();
      }

      if(securityEngineStatic != null) {
         securityEngineStatic.close();
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // [A] Bug #78476: no Cookie header must resolve the org from the host, like [B]
   @Test
   void orgSubdomain_noCookies_showsOrgBannerAndHidesSignup() throws Exception {
      ModelAndView model = showLoginPage("org1.example.com");

      assertEquals(TENANT_BANNER, banner(model));
      assertSignupLink(model, false);
   }

   // [A] same with a non-default port in the host
   @Test
   void orgSubdomainWithPort_noCookies_showsOrgBannerAndHidesSignup() throws Exception {
      MockHttpServletRequest request = request("org1.example.com");
      request.setServerPort(8080);
      ModelAndView model = showLoginPage(request);

      assertEquals(TENANT_BANNER, banner(model));
      assertSignupLink(model, false);
   }

   // [B] unrelated cookie: already resolved from the host before the fix
   @Test
   void orgSubdomain_unrelatedCookie_showsOrgBannerAndHidesSignup() throws Exception {
      ModelAndView model = showLoginPage("org1.example.com", new Cookie("foo", "bar"));

      assertEquals(TENANT_BANNER, banner(model));
      assertSignupLink(model, false);
   }

   // [C] the org cookie still wins over the host
   @Test
   void orgSubdomain_hostOrgCookie_cookieWinsOverHost() throws Exception {
      ModelAndView model = showLoginPage("org1.example.com", new Cookie(ORG_COOKIE, HOST_ORG));

      assertEquals(GLOBAL_BANNER, banner(model));
      assertSignupLink(model, true);
   }

   // [C] the org cookie still wins over a host that names no org
   @Test
   void unknownSubdomain_tenantOrgCookie_cookieWinsOverHost() throws Exception {
      ModelAndView model = showLoginPage("orgx.example.com", new Cookie(ORG_COOKIE, TENANT_ORG));

      assertEquals(TENANT_BANNER, banner(model));
      assertSignupLink(model, false);
   }

   // [D] host-org sub-domain without cookies: default page, signup link shown
   @Test
   void hostOrgSubdomain_noCookies_showsGlobalBannerAndSignup() throws Exception {
      ModelAndView model = showLoginPage("host-org.example.com");

      assertEquals(GLOBAL_BANNER, banner(model));
      assertSignupLink(model, true);
   }

   // [E] unknown sub-domain without cookies: default page, signup link shown
   @Test
   void unknownSubdomain_noCookies_showsGlobalBannerAndSignup() throws Exception {
      ModelAndView model = showLoginPage("orgx.example.com");

      assertEquals(GLOBAL_BANNER, banner(model));
      assertSignupLink(model, true);
   }

   // [F] single-tenant: the host is never consulted, with or without cookies
   @Test
   void singleTenant_orgSubdomain_showsGlobalBannerAndSignup() throws Exception {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);

      ModelAndView noCookies = showLoginPage("org1.example.com");
      ModelAndView unrelatedCookie = showLoginPage("org1.example.com", new Cookie("foo", "bar"));

      assertEquals(GLOBAL_BANNER, banner(noCookies));
      assertSignupLink(noCookies, true);
      assertEquals(GLOBAL_BANNER, banner(unrelatedCookie));
      assertSignupLink(unrelatedCookie, true);
   }

   // [G] the signup flag alone: the banner is global either way, but the tenant host must still
   // hide the self-signup link when the request has no cookies
   @Test
   void orgWithoutWelcomePage_noCookies_showsGlobalBannerAndHidesSignup() throws Exception {
      ModelAndView noCookies = showLoginPage("org3.example.com");
      ModelAndView unrelatedCookie = showLoginPage("org3.example.com", new Cookie("foo", "bar"));

      assertEquals(GLOBAL_BANNER, banner(noCookies));
      assertSignupLink(noCookies, false);
      assertEquals(GLOBAL_BANNER, banner(unrelatedCookie));
      assertSignupLink(unrelatedCookie, false);
   }

   // [H] orgLocation=path: the org named by the first path segment is used without cookies
   @Test
   void pathMode_orgInPath_noCookies_showsOrgBannerAndHidesSignup() throws Exception {
      String oldLocation = SreeEnv.getProperty("security.login.orgLocation");
      SreeEnv.setProperty("security.login.orgLocation", "path");

      try {
         MockHttpServletRequest request = request("example.com");
         request.setPathInfo("/org1/login.html");
         assertNull(request.getCookies());
         ModelAndView model = showLoginPage(request);

         assertEquals(TENANT_BANNER, banner(model));
         assertSignupLink(model, false);
      }
      finally {
         SreeEnv.setProperty("security.login.orgLocation", oldLocation);
      }
   }

   private ModelAndView showLoginPage(String host, Cookie... cookies) throws Exception {
      MockHttpServletRequest request = request(host);

      if(cookies.length > 0) {
         request.setCookies(cookies);
      }
      else {
         // the no-cookie cases must really have no Cookie header (getCookies() == null)
         assertNull(request.getCookies());
      }

      return showLoginPage(request);
   }

   private ModelAndView showLoginPage(MockHttpServletRequest request) throws Exception {
      return controller.showLoginPage(null, request, new MockHttpServletResponse(),
                                      "http://" + request.getServerName() + "/");
   }

   private static MockHttpServletRequest request(String host) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login.html");
      request.setServerName(host);
      request.setServerPort(80);
      return request;
   }

   private static PortalWelcomePage welcomePage(String banner) {
      PortalWelcomePage page = new PortalWelcomePage();
      page.setBanner(banner);
      return page;
   }

   private static String banner(ModelAndView model) {
      LoginBannerModel banner = (LoginBannerModel) model.getModel().get("loginBanner");
      assertNotNull(banner, "login banner");
      return banner.getBannerText();
   }

   // login.html shows the self-signup link when selfSignUpEnabled && isNotTenantServer
   private static void assertSignupLink(ModelAndView model, boolean shown) {
      assertEquals(Boolean.TRUE, model.getModel().get("selfSignUpEnabled"));
      assertEquals(shown, model.getModel().get("isNotTenantServer"), "isNotTenantServer");
   }
}
