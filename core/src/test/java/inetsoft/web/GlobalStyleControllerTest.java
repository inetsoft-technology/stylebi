/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web;

/*
 * Test strategy
 *
 * Class type: behavioral — resolves which theme a style sheet request is served from.
 *
 * The login page and the style sheets it pulls in are requested without a principal, so
 * OrganizationManager falls back to the default organization. A tenant reaching its own login page
 * (organization0.localhost/...) would then be styled with the host organization's theme; only the
 * organization named by the request itself identifies the right theme before login.
 *
 * --- getStyle() organization resolution ---
 *
 *  ├─ [A] unauthenticated + request names an organization → that org's theme is served
 *  ├─ [B] unauthenticated + request names no organization → current (default) org's theme
 *  ├─ [C] authenticated                                   → current org's theme, request ignored
 *  └─ [D] any of the above                                → org context is left clean afterwards
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class GlobalStyleControllerTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private CustomThemesManager customThemesManager;
   @Mock private OrganizationManager organizationManager;
   @Mock private ApplicationContext context;
   @Mock private Resource resource;

   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<ThreadContext> threadContextStatic;
   private GlobalStyleController controller;

   private static final String CSS_PATH = "/app/theme-variables.css";
   private static final String HOST_ORG = "host-org";
   private static final String TENANT_ORG = "organization0";
   private static final String HOST_ORG_THEME = "def2";
   private static final String TENANT_ORG_THEME = "test2";

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class);
      threadContextStatic = mockStatic(ThreadContext.class);
      organizationManagerStatic = mockStatic(OrganizationManager.class);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      // mirrors the real implementation: the principal's org, else the org pinned on the thread,
      // else the default organization
      lenient().when(organizationManager.getCurrentOrgID()).thenAnswer(invocation -> {
         String pinned = OrganizationContextHolder.getCurrentOrgId();
         return pinned != null ? pinned : HOST_ORG;
      });

      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      Organization hostOrg = organization(HOST_ORG_THEME);
      Organization tenantOrg = organization(TENANT_ORG_THEME);
      lenient().when(securityProvider.getOrganization(HOST_ORG)).thenReturn(hostOrg);
      lenient().when(securityProvider.getOrganization(TENANT_ORG)).thenReturn(tenantOrg);
      lenient().when(customThemesManager.getCustomThemes())
         .thenReturn(Set.of(theme(HOST_ORG_THEME), theme(TENANT_ORG_THEME)));

      lenient().when(resource.lastModified()).thenReturn(1L);
      lenient().when(resource.getFilename()).thenReturn("theme-variables.css");
      lenient().when(resource.getInputStream())
         .thenAnswer(invocation -> new ByteArrayInputStream(new byte[] { 'x' }));
      lenient().when(context.getResource(anyString())).thenReturn(resource);

      controller = new GlobalStyleController(securityEngine, customThemesManager);
      controller.setApplicationContext(context);
   }

   @AfterEach
   void tearDown() {
      if(sUtilStatic != null) { sUtilStatic.close(); }
      if(organizationManagerStatic != null) { organizationManagerStatic.close(); }
      if(threadContextStatic != null) { threadContextStatic.close(); }
      OrganizationContextHolder.clear();
   }

   // [Path A] pre-login request for a tenant: the org in the request URL decides the theme, not the
   // default org that OrganizationManager falls back to when there is no principal
   @Test
   void getStyle_unauthenticatedTenantRequest_servesRequestOrgTheme() throws Exception {
      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(null);
      sUtilStatic.when(() -> SUtil.getLoginOrganization(any())).thenReturn(TENANT_ORG);

      getStyle(null);

      assertEquals("theme:" + TENANT_ORG_THEME + "/inetsoft/web/resources" + CSS_PATH,
                   capturedLocation());
   }

   // [Path B] the host/path names no organization -> current (default) organization is kept
   @Test
   void getStyle_unauthenticatedRequestWithoutOrg_servesDefaultOrgTheme() throws Exception {
      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(null);
      sUtilStatic.when(() -> SUtil.getLoginOrganization(any())).thenReturn(null);

      getStyle(null);

      assertEquals("theme:" + HOST_ORG_THEME + "/inetsoft/web/resources" + CSS_PATH,
                   capturedLocation());
   }

   // [Path C] an authenticated user's organization always wins over the requested host
   @Test
   void getStyle_authenticatedRequest_ignoresRequestOrg() throws Exception {
      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(mock(XPrincipal.class));
      lenient().when(customThemesManager.getSelectedTheme(any())).thenReturn(HOST_ORG_THEME);

      getStyle(mock(Principal.class));

      assertEquals("theme:" + HOST_ORG_THEME + "/inetsoft/web/resources" + CSS_PATH,
                   capturedLocation());
      sUtilStatic.verify(() -> SUtil.getLoginOrganization(any()), never());
   }

   // [Path D] the pinned organization must not leak to the next request on this thread
   @Test
   void getStyle_unauthenticatedTenantRequest_clearsPinnedOrgContext() throws Exception {
      threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(null);
      sUtilStatic.when(() -> SUtil.getLoginOrganization(any())).thenReturn(TENANT_ORG);

      getStyle(null);

      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   private void getStyle(Principal user) throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", CSS_PATH);
      request.setServletPath(CSS_PATH);
      controller.getStyle(request, new MockHttpServletResponse(), user);
   }

   private String capturedLocation() {
      ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
      verify(context).getResource(captor.capture());
      return captor.getValue();
   }

   private static Organization organization(String themeId) {
      Organization org = mock(Organization.class);
      lenient().when(org.getTheme()).thenReturn(themeId);
      return org;
   }

   private static CustomTheme theme(String id) {
      CustomTheme theme = new CustomTheme();
      theme.setId(id);
      theme.setName(id);
      return theme;
   }
}
