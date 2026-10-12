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

import inetsoft.mv.MVManager;
import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77835: the REST org clone (copyFromOrgID) delegates to UserTreeService, which audits the
 * clone itself. A step this service runs after the clone returned (the requested admin grants)
 * could still fail; the client got an error but the only record was the clone's SUCCESS. The
 * plain create records "was created, but a later step failed" for the same request.
 *
 * This drives the real SecurityService through the real UserTreeService into the real
 * FileAuthenticationProvider copy, with Audit captured, so the records are the ones both layers
 * actually write.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceCloneOrgAuditIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceCloneOrgAuditIntegrationTest {
   private static final String HOST = "host-org";
   private static final String SRC = "src77835";
   private static final String OID = "c77835";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private EditableAuthenticationProvider provider;
   private SecurityService service;
   private SRPrincipal siteAdmin;
   private Audit audit;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SUtil> sUtilStatic;
   // status|objectName|resourceOrganization|error of each action record, when it was audited
   private final List<String> actions = new ArrayList<>();

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77835", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77835", HOST)
         .addOrg(SRC, SRC)
         .addUser("u1", SRC, "password")
         .setup();
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      audit = mock(Audit.class);
      doAnswer(inv -> {
         ActionRecord r = inv.getArgument(0);
         actions.add(r.getActionStatus() + "|" + r.getObjectName() + "|" +
                     r.getResourceOrganization() + "|" + r.getActionError());
         return null;
      }).when(audit).auditAction(any(), any());
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      provider = mock(EditableAuthenticationProvider.class,
                      org.mockito.AdditionalAnswers.delegatesTo(fileProvider));
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(() -> SUtil.getEditableAuthenticationProvider(any(SecurityProvider.class)))
         .thenReturn(provider);

      AuthenticationProviderService providers = mock(AuthenticationProviderService.class);
      when(providers.getProviderByName(any())).thenReturn(provider);
      LocalizationSettingsModel localization = mock(LocalizationSettingsModel.class);
      when(localization.locales()).thenReturn(new ArrayList<>());
      LocalizationSettingsService localizationService = mock(LocalizationSettingsService.class);
      when(localizationService.getModel()).thenReturn(localization);
      UserTreeService userTreeService = new UserTreeService(
         providers, mock(SystemAdminService.class), mock(IdentityService.class),
         localizationService, SecurityEngine.getSecurity(), mock(IdentityThemeService.class),
         mock(SimpMessagingTemplate.class), mock(FavoritesService.class),
         mock(DataCycleManager.class), mock(LicenseManager.class), mock(MVManager.class),
         mock(IndexedStorage.class), mock(CustomThemesManager.class),
         mock(DashboardRegistryManager.class), mock(XRepository.class),
         mock(DependencyStorageService.class), mock(RecycleBin.class));
      service = new SecurityService(
         SecurityEngine.getSecurity(), mock(IdentityService.class),
         mock(ActionPermissionService.class), mock(LocalizationSettingsService.class),
         mock(IdentityThemeService.class), mock(SystemAdminService.class), userTreeService,
         mock(CustomThemesManager.class));

      siteAdmin = builder.principalOf("sa", HOST);
      ThreadContext.setContextPrincipal(siteAdmin);
   }

   @AfterEach
   void tearDown() throws Exception {
      ThreadContext.setContextPrincipal(null);

      if(sUtilStatic != null) {
         sUtilStatic.close();
      }

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(fileProvider != null && fileProvider.getOrganization(OID) != null) {
         fileProvider.removeOrganization(OID);
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void cloneSucceeds_onlyTheCloneRecord() throws Exception {
      service.createOrganization(request(), SRC, siteAdmin);

      assertNotNull(fileProvider.getOrganization(OID));
      assertEquals(1, actions.size(), "only UserTreeService records the clone: " + actions);
      assertTrue(actions.get(0).startsWith(ActionRecord.ACTION_STATUS_SUCCESS + "|" + OID + "|"),
                 actions.toString());
      verify(audit, times(1)).auditIdentityInfo(any(), any());
   }

   @Test
   void adminGrantFailsAfterClone_failureRecorded() {
      SecurityOrganization request = request();
      request.setAdminIdentities(nullAdminUser());

      // a null admin user fails the grant write after the clone completed
      assertThrows(NullPointerException.class,
                   () -> service.createOrganization(request, SRC, siteAdmin));

      assertNotNull(fileProvider.getOrganization(OID), "the clone is kept");
      assertEquals(2, actions.size(), actions.toString());
      assertTrue(actions.get(0).startsWith(ActionRecord.ACTION_STATUS_SUCCESS + "|" + OID + "|"),
                 "UserTreeService's clone record: " + actions);
      // the clone switched the current org to the new org, the record keeps the caller's
      assertTrue(actions.get(1).startsWith(
         ActionRecord.ACTION_STATUS_FAILURE + "|" + OID + "|" + HOST + "|Organization " + OID +
            " was created, but a later step failed: "), actions.toString());
      // UserTreeService already recorded the org's identity info
      verify(audit, times(1)).auditIdentityInfo(any(), any());
   }

   @Test
   void cloneCopyFails_onlyTheCloneFailureRecord() {
      doThrow(new RuntimeException("copy failed 77835")).when(provider).copyOrganization(
         any(), anyString(), any(), any(), any(), any(), any(), anyBoolean(), any());

      assertThrows(RuntimeException.class,
                   () -> service.createOrganization(request(), SRC, siteAdmin));

      // the clone did not complete, so UserTreeService's FAILURE is the only record
      assertNull(fileProvider.getOrganization(OID));
      assertEquals(1, actions.size(), actions.toString());
      assertTrue(actions.get(0).startsWith(ActionRecord.ACTION_STATUS_FAILURE + "|"),
                 actions.toString());
      assertTrue(actions.get(0).endsWith("|copy failed 77835"), actions.toString());
      verify(audit, never()).auditIdentityInfo(any(), any());
   }

   @Test
   void plainCreateAdminGrantFails_unchanged() {
      SecurityOrganization request = request();
      request.setAdminIdentities(nullAdminUser());

      assertThrows(NullPointerException.class,
                   () -> service.createOrganization(request, null, siteAdmin));

      assertEquals(1, actions.size(), actions.toString());
      assertTrue(actions.get(0).startsWith(
         ActionRecord.ACTION_STATUS_FAILURE + "|" + OID + "|" + HOST + "|Organization " + OID +
            " was created, but a later step failed: "), actions.toString());
   }

   private static SecurityOrganization request() {
      SecurityOrganization request = new SecurityOrganization();
      request.setId(OID);
      request.setName(OID);
      request.setDefaultPassword("Str0ng!Passw0rd77835");
      return request;
   }

   // a client can send "users": [null]
   private static AdminIdentities nullAdminUser() {
      List<IdentityID> users = new ArrayList<>();
      users.add(null);
      AdminIdentities admins = new AdminIdentities();
      admins.setUsers(users);
      return admins;
   }

   @Configuration
   static class TestBeans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }

      @Bean
      public CustomThemesManager customThemesManager() {
         return mock(CustomThemesManager.class, RETURNS_DEEP_STUBS);
      }
   }
}
