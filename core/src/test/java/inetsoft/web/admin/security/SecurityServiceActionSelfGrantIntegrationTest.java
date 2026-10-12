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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77385: a user who only holds ACCESS on EM_COMPONENT settings/security/actions could grant
 * itself ACCESS on any other node of the action tree through the public permissions API
 * (PUT /api/public/security/permissions, POST .../grants). Unlike SecurityServicePermissionWriteTest
 * (mocked SecurityEngine and action tree), this drives the real SecurityService against the real
 * SecurityEngine, FileAuthorizationProvider and ActionPermissionService (built from the EM view
 * component snapshot), and asserts the outcome through SecurityEngine.checkPermission() run as the
 * delegate, as the reporter's live check did.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  SecurityServiceActionSelfGrantIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceActionSelfGrantIntegrationTest {
   private static final String ORG_ID = "p77385";
   private static final String ORG_NAME = "Name77385";
   private static final String ACTIONS = "settings/security/actions";
   private static final String USERS = "settings/security/users";

   private SecurityTestDataBuilder builder;
   private SecurityService service;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<LicenseManager> license;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("delegate", ORG_ID, "password")
         .grantPermission(ResourceType.EM_COMPONENT, ACTIONS, ResourceAction.ACCESS,
                          "delegate", Identity.USER, ORG_ID)
         .addOrgAdminRole("OrgAdmin77385", ORG_ID)
         .addUser("oadmin", ORG_ID, "password")
         .addUserToRole("oadmin", "OrgAdmin77385", ORG_ID)
         .addUser("target", ORG_ID, "password")
         .setup();

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      license = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      license.when(LicenseManager::isEnterprise).thenReturn(true);

      ViewComponent components;

      try(InputStream in = getClass().getClassLoader().getResourceAsStream(
         "inetsoft/web/admin/security/action/view-components-snapshot.json"))
      {
         assertNotNull(in, "precondition: the EM view component snapshot is on the classpath");
         components = new ObjectMapper().readValue(in, ViewComponent.class);
      }

      ComponentAuthorizationService componentService = mock(ComponentAuthorizationService.class);
      when(componentService.getComponentTree()).thenReturn(components);
      PortalThemesManager themes =
         new PortalThemesManager(Cluster.getInstance(), DataSpace.getDataSpace());
      themes.loadThemes();
      SecurityEngine engine = SecurityEngine.getSecurity();

      service = new SecurityService(
         engine, mock(IdentityService.class),
         new ActionPermissionService(componentService, engine, themes),
         mock(LocalizationSettingsService.class), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class), mock(CustomThemesManager.class));
   }

   @AfterEach
   void tearDown() {
      SecurityEngine.getSecurity().getSecurityProvider()
         .removePermission(ResourceType.EM_COMPONENT, USERS, ORG_ID);
      ThreadContext.setContextPrincipal(null);
      license.close();
      sutil.close();
      auditStatic.close();

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void delegate_setPermission_selfAccessOnNotHeldPage_refused() throws Exception {
      SRPrincipal delegate = as("delegate");
      assertTrue(canAccess(delegate, ACTIONS), "precondition: the delegate holds " + ACTIONS);
      assertFalse(canAccess(delegate, USERS), "precondition: the delegate cannot access " + USERS);

      Throwable thrown = thrown(() -> service.setPermission(
         USERS, "EM_COMPONENT", model(user("delegate", "ACCESS")), delegate));

      assertRefused(thrown, delegate);
   }

   @Test
   void delegate_createPermissionGrant_selfAccessOnNotHeldPage_refused() throws Exception {
      SRPrincipal delegate = as("delegate");

      Throwable thrown = thrown(() -> service.createPermissionGrant(
         USERS, "EM_COMPONENT", user("delegate", "ACCESS"), delegate));

      assertRefused(thrown, delegate);
   }

   // control: an org admin may still grant the page
   @Test
   void orgAdmin_setPermission_accessOnPage_stored() throws Exception {
      SRPrincipal orgAdmin = as("oadmin");

      service.setPermission(USERS, "EM_COMPONENT", model(user("target", "ACCESS")), orgAdmin);

      SRPrincipal target = as("target");
      assertTrue(canAccess(target, USERS), "the org admin's grant of " + USERS + " must apply");
   }

   private static void assertRefused(Throwable thrown, SRPrincipal delegate) throws Exception {
      boolean escalated = canAccess(delegate, USERS);
      assertAll(
         () -> assertInstanceOf(UnauthorizedAccessException.class, thrown,
                                "the self grant of ACCESS on " + USERS + " must be refused"),
         () -> assertFalse(escalated, "the delegate escalated to ACCESS on " + USERS));
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static Throwable thrown(ThrowingRunnable r) {
      try {
         r.run();
         return null;
      }
      catch(Throwable t) {
         return t;
      }
   }

   private SRPrincipal as(String user) {
      SRPrincipal principal = builder.principalOf(user, ORG_ID);
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private static boolean canAccess(SRPrincipal principal, String page) throws Exception {
      return SecurityEngine.getSecurity().checkPermission(
         principal, ResourceType.EM_COMPONENT, page, ResourceAction.ACCESS);
   }

   private static ResourcePermission model(PermissionGrant... grants) {
      ResourcePermission model = new ResourcePermission();
      model.setPermissionGrants(new ArrayList<>(List.of(grants)));
      return model;
   }

   private static PermissionGrant user(String name, String... actions) {
      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(new IdentityID(name, ORG_ID));
      grant.setType("USER");
      grant.setActions(new ArrayList<>(List.of(actions)));
      return grant;
   }

   // the enterprise license strategy on this classpath looks up ElasticLicenseService when
   // SecurityEngine initializes
   @Configuration
   static class TestBeans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }
   }
}
