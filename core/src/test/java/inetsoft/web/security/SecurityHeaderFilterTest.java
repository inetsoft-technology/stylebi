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
 * Intent vs implementation suspects
 *
 * [Suspect 1] SecurityHeaderFilter.doFilter() line 59-67 -> intent: explicitly documented by the
 *             author's own TODO and commented-out code -- Enterprise Manager requests ("/em/**")
 *             were meant to get a stricter "X-Frame-Options: DENY" instead of the general
 *             "SAMEORIGIN" everything else gets:
 *             // TODO need to differentiate between top-level and nested leave as same origin for now.
 *             //   if(isEnterpriseManager((HttpServletRequest) request)) {
 *             //      httpResponse.setHeader("X-Frame-Options", "DENY");
 *             //   }
 *             //   else {
 *                    httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
 *             //   }
 *             actual: the EM-specific branch is commented out; every path, including "/em/**",
 *             gets the weaker SAMEORIGIN policy. Admin-only EM pages can still be framed
 *             same-origin, which the author's own TODO suggests was not the intended end state.
 *             See doFilter_emPath_stillGetsSameOrigin_notDeny_confirmsCommentedOutIntent below.
 *             Judgment: low practical value -- ONLY same-origin framing is allowed today (cross-
 *             origin iframes still blocked); hardening / unfinished TODO, not a standalone
 *             cross-site clickjacking bug. Nested vs top-level EM framing was never fully
 *             designed (author's own TODO), so not a security P0.
 *
 * [Suspect 2] SecurityHeaderFilter's three feature flags -> intent (inferred from the class
 *             Javadoc): "X-Frame-Options" defaults to the protective SAMEORIGIN unless an admin
 *             opts OUT via security.allow.iframe=true
 *             actual: "X-Content-Type-Options: nosniff" -- a header OWASP lists as essentially
 *             always-safe and broadly recommended, with no legitimate reason to disable it -- is
 *             the opposite: it defaults OFF (security.enableContentTypeOptions default "false")
 *             and an admin must opt IN. Out of the box, this application never sends
 *             X-Content-Type-Options, unlike the frame-options header which is protective by
 *             default. Worth a second look at whether this default should be flipped. See
 *             doFilter_contentTypeOptionsDefault_headerOmitted below.
 *             Judgment: low practical value -- baseline hardening / inconsistent secure-by-default
 *             vs X-Frame-Options; MIME-sniffing exploit needs additional conditions. Consider
 *             flipping the default to "true" later; not worth a high-severity security ticket.
 *
 * Neither suspect is patched in the production filter here; both are pinned down as passing
 * characterization tests below and flagged for the team to triage.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class SecurityHeaderFilterTest {

   @Mock private SessionLicenseServiceProvider licenseProvider;
   @Mock private AuthenticationService authService;
   @Mock private FilterChain chain;

   private MockedStatic<SreeEnv> sreeEnvMock;
   private SecurityHeaderFilter filter;

   @BeforeEach
   void setUp() throws Exception {
      sreeEnvMock = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      filter = new SecurityHeaderFilter(licenseProvider, authService);
      // AbstractSecurityFilter.securityAllowIframe is a *static* SreeEnv.Value shared across every
      // filter test in the JVM, cached for 10s. Without resetting its internal timestamp here,
      // whichever value a previous test (in this class or another AbstractSecurityFilter subclass
      // test) last resolved can leak into this test if it runs within the cache window -- do not
      // rely on execution order to avoid that, force a fresh read every time instead.
      resetStaticSreeEnvValueCache(AbstractSecurityFilter.securityAllowIframe);
   }

   private static void resetStaticSreeEnvValueCache(SreeEnv.Value value) throws Exception {
      java.lang.reflect.Field tsField = SreeEnv.Value.class.getDeclaredField("ts");
      tsField.setAccessible(true);
      tsField.setLong(value, 0L);
   }

   @AfterEach
   void tearDown() throws Exception {
      sreeEnvMock.close();
      // doFilter_allowIframeTrue_omitsXFrameOptions leaves this static cache holding "true" (see
      // setUp()'s comment). Once sreeEnvMock is closed above, SreeEnv.getProperty() reverts to its
      // real implementation, but the cache won't re-query it for up to 10s -- long enough to leak
      // a bogus "iframe allowed" reading into an unrelated test class (e.g. CsrfFilterUnitTest,
      // which also calls isSecurityAllowIframe() via CSRFFilter's SameSite decision) if it happens
      // to run next within that window. Reset again here so nothing after this class inherits it.
      resetStaticSreeEnvValueCache(AbstractSecurityFilter.securityAllowIframe);
   }

   // ── X-Frame-Options ────────────────────────────────────────────────────────

   @Test
   void doFilter_default_setsXFrameOptionsSameOrigin() throws Exception {
      stubProperty("security.allow.iframe", "false");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
      verify(chain).doFilter(request, response);
   }

   @Test
   void doFilter_allowIframeTrue_omitsXFrameOptions() throws Exception {
      stubProperty("security.allow.iframe", "true");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertNull(response.getHeader("X-Frame-Options"));
   }

   // ── suspect 1 ──────────────────────────────────────────────────────────────

   @Test
   void doFilter_emPath_stillGetsSameOrigin_notDeny_confirmsCommentedOutIntent() throws Exception {
      stubProperty("security.allow.iframe", "false");
      MockHttpServletRequest request = request("/em/settings");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // The author's own TODO/commented-out code suggests "/em/**" was meant to get DENY; it gets
      // the same SAMEORIGIN as every other path instead.
      assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
   }

   // ── X-XSS-Protection ───────────────────────────────────────────────────────

   @Test
   void doFilter_xssProtectionDefault_headerOmitted() throws Exception {
      stubProperty("security.enableXSSProtection", "false");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertNull(response.getHeader("X-XSS-Protection"));
   }

   @Test
   void doFilter_xssProtectionEnabled_setsHeaderToOne() throws Exception {
      stubProperty("security.enableXSSProtection", "true");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("1", response.getHeader("X-XSS-Protection"));
   }

   // ── suspect 2 / X-Content-Type-Options ─────────────────────────────────────

   @Test
   void doFilter_contentTypeOptionsDefault_headerOmitted() throws Exception {
      stubProperty("security.enableContentTypeOptions", "false");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      // Ties to file-header Suspect 2: an OWASP-recommended, essentially-always-safe header is
      // off by default and requires an explicit opt-in.
      assertNull(response.getHeader("X-Content-Type-Options"));
   }

   @Test
   void doFilter_contentTypeOptionsEnabled_setsNosniff() throws Exception {
      stubProperty("security.enableContentTypeOptions", "true");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
   }

   // ── X-Robots-Tag ───────────────────────────────────────────────────────────

   @Test
   void doFilter_robotsTagDefault_setsNoindexNofollow() throws Exception {
      stubProperty("security.robotsTag", "noindex, nofollow");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertEquals("noindex, nofollow", response.getHeader("X-Robots-Tag"));
   }

   @Test
   void doFilter_robotsTagExplicitlyEmpty_headerOmitted() throws Exception {
      // Documented escape hatch for publicly-indexable dashboards.
      stubProperty("security.robotsTag", "");
      MockHttpServletRequest request = request("/portal/dashboard");
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter.doFilter(request, response, chain);

      assertNull(response.getHeader("X-Robots-Tag"));
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   private static MockHttpServletRequest request(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      request.setServletPath(path);
      return request;
   }

   /**
    * Stubs both the 1-arg and 2-arg {@code SreeEnv.getProperty} overloads for {@code key} to
    * return {@code value}. The three instance-level {@code SreeEnv.Value(name, timeout, def)}
    * fields on {@code SecurityHeaderFilter} (XSS/content-type/robots-tag) resolve through the
    * 2-arg overload, while the inherited static {@code AbstractSecurityFilter.securityAllowIframe}
    * field uses the 2-arg {@code Value(name, timeout)} constructor (no default), which resolves
    * through the 1-arg overload instead -- stubbing both avoids having to track which shape each
    * property happens to use.
    */
   private void stubProperty(String key, String value) {
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq(key))).thenReturn(value);
      sreeEnvMock.when(() -> SreeEnv.getProperty(eq(key), anyString())).thenReturn(value);
   }
}
