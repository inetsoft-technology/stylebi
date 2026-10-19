/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.DataSpace;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78866: every {@code owner} form on the REAL {@code AdminViewsheetController.getFolder},
 * {@code ViewsheetChangePlanService} and {@code ViewsheetChangesetApplyService}, over a REAL
 * {@code RepletRegistryManager} on the test {@code DataSpace}. Only the {@code ViewsheetService}
 * wrapper is a mock; its {@code addFolder} makes the same {@code addRepositoryFolder} call the
 * real wrapper makes. The security provider seen by {@code SecurityEngine.getSecurity()} is a
 * static mock (user {@code admin@host-org}; orgs {@code host-org} and {@code other-org}).
 *
 * <p>Uses only the public API, so the class also runs against the code before the fix.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ViewsheetIdentityRefsTest.RegistryConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetIdentityRefsTest {
   private static final String ORG = "host-org";
   private static final IdentityID ADMIN = new IdentityID("admin", ORG);

   @Autowired private DataSpace dataSpace;

   private RepletRegistryManager registryManager;
   private RepletRegistryService registryService;
   private ViewsheetService viewsheetApiService;
   private ViewsheetFolderService folderService;
   private ViewsheetChangePlanService planService;
   private ViewsheetChangesetApplyService applyService;
   private AdminViewsheetController controller;
   private Principal user;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<Audit> auditStatic;
   private String folder;

   @BeforeEach void setUp() throws Exception {
      OrganizationManager orgManager = spy(OrganizationManager.getInstance());
      doReturn(ORG).when(orgManager).getCurrentOrgID();
      doReturn(true).when(orgManager).isSiteAdmin(any(Principal.class));
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      SecurityProvider provider = mock(SecurityProvider.class, withSettings().lenient());
      User someUser = mock(User.class);
      when(provider.getUser(any()))
         .thenAnswer(inv -> ADMIN.equals(inv.getArgument(0)) ? someUser : null);
      Organization someOrg = mock(Organization.class);
      when(provider.getOrganization(any())).thenAnswer(
         inv -> Set.of(ORG, "other-org").contains(inv.<String>getArgument(0)) ? someOrg : null);
      SecurityEngine engine = mock(SecurityEngine.class, withSettings().lenient());
      when(engine.getSecurityProvider()).thenReturn(provider);
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);
      auditStatic = mockStatic(Audit.class, withSettings().lenient());
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      registryManager = new RepletRegistryManager(dataSpace);
      registryService = new RepletRegistryService(
         mock(SecurityEngine.class), mock(ScheduleManager.class), mock(DependencyHandler.class),
         mock(RenameTransformHandler.class), registryManager);
      folderService = new ViewsheetFolderService(registryManager, registryService);
      viewsheetApiService = mock(ViewsheetService.class);
      // the registry write ViewsheetService.addFolder makes, for an owner-scoped folder
      doAnswer(inv -> {
         registryService.addRepositoryFolder("My Dashboards/" + inv.getArgument(1),
            inv.getArgument(2), inv.getArgument(3), inv.getArgument(4));
         return null;
      }).when(viewsheetApiService).addFolder(any(), anyString(), any(), any(), any(), any());
      planService = new ViewsheetChangePlanService(viewsheetApiService, folderService,
                                                   mock(AssetRepository.class),
                                                   mock(WorksheetService.class));
      applyService = new ViewsheetChangesetApplyService(planService, viewsheetApiService,
         folderService, mock(AdminBackupService.class), mock(WorksheetService.class));
      controller = new AdminViewsheetController(viewsheetApiService, folderService, planService,
                                                applyService);

      user = mock(Principal.class);
      when(user.getName()).thenReturn(new IdentityID("caller", ORG).convertToKey());
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      // a unique real folder per test, owned by admin@host-org
      folder = "RealF" + System.nanoTime();
      registryService.addRepositoryFolder("My Dashboards/" + folder, null, null, ADMIN);
   }

   @AfterEach void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      auditStatic.close();
      securityEngineStatic.close();
      orgManagerStatic.close();
   }

   // ------------------------------------------------------------------ get_viewsheet_folder

   @ParameterizedTest
   @ValueSource(strings = { "admin", "admin:host-org", "admin~;~host-org", "admin^host-org" })
   void getFolderFindsTheRealFolderInEveryForm(String owner) throws Exception {
      GetViewsheetFolderResult result = controller.getFolder(folder, owner, user);

      assertTrue(result.found(), owner);
      assertEquals("admin~;~host-org", result.owner());
   }

   @Test void getFolderWithACaretAndAnUnknownOrgIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.getFolder(folder, "admin^SELF", user));

      assertTrue(ex.getMessage().contains("no organization \"SELF\""), ex.getMessage());
   }

   @Test void getFolderWithAnUnknownExplicitOrgIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.getFolder(folder, "admin~;~nope-org", user));

      assertTrue(ex.getMessage().contains("organization \"nope-org\" does not exist"),
                 ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin^__GLOBAL__", "admin~;~__GLOBAL__", "admin:__GLOBAL__" })
   void aGlobalOwnerIsRefused(String owner) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.getFolder(folder, owner, user));

      assertTrue(ex.getMessage().contains("global"), ex.getMessage());
   }

   // ------------------------------------------------------------------ preview / apply

   @ParameterizedTest
   @ValueSource(strings = { "admin~;~host-org", "admin^host-org" })
   void updateAndDeleteOfTheRealFolderAreAcceptedInKeyAndCaretForm(String owner) throws Exception {
      ViewsheetChangeRequest update = folderChange(ViewsheetChangeRequest.VERB_UPDATE, owner);
      update.setPath("My Dashboards/" + folder);
      update.setAlias("New Alias");
      ViewsheetChangeRequest delete = folderChange(ViewsheetChangeRequest.VERB_DELETE, owner);
      delete.setPath("My Dashboards/" + folder);

      ResolvedPlan updatePlan = preview(update);
      ResolvedPlan deletePlan = preview(delete);

      assertEquals("folder:My Dashboards/" + folder + "|admin~;~host-org",
                   updatePlan.changes().get(0).property());
      assertEquals(updatePlan.planHash(), preview(sameWithOwner(update, "admin")).planHash());
      assertEquals("folder:My Dashboards/" + folder + "|admin~;~host-org",
                   deletePlan.changes().get(0).property());
   }

   @ParameterizedTest
   @ValueSource(strings = { "admin~;~host-org", "admin^host-org", "admin:host-org", "admin" })
   void applyCreateWritesTheRegistryOfTheRealOwner(String owner) throws Exception {
      String name = "NewF" + System.nanoTime();
      ViewsheetChangeRequest create = folderChange(ViewsheetChangeRequest.VERB_CREATE, owner);
      create.setFolderName(name);

      ResolvedPlan plan = preview(create);
      ViewsheetApplyResult result = apply(plan, create);

      assertEquals("folder:My Dashboards/" + name + "|admin~;~host-org",
                   plan.changes().get(0).property());
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(viewsheetApiService).addFolder(any(), eq(name), any(), any(), eq(ADMIN), any());
      assertTrue(registryManager.getRegistry(ADMIN).isFolder("My Dashboards/" + name));
   }

   @Test void anOwnerTheProviderDoesNotKnowIsAcceptedWithAWarning() throws Exception {
      ViewsheetChangeRequest create = folderChange(ViewsheetChangeRequest.VERB_CREATE,
                                                   "no-such-user-xyz");
      create.setFolderName("NewF" + System.nanoTime());

      String description = preview(create).changes().get(0).description();

      assertTrue(description.startsWith("WARNING: owner \"no-such-user-xyz"), description);
   }

   @Test void aKnownOwnerGetsNoWarning() throws Exception {
      ViewsheetChangeRequest create = folderChange(ViewsheetChangeRequest.VERB_CREATE, "admin");
      create.setFolderName("NewF" + System.nanoTime());

      String description = preview(create).changes().get(0).description();

      assertFalse(description.contains("WARNING"), description);
   }

   @Test void previewWithACaretAndAnUnknownOrgIsRefused() {
      ViewsheetChangeRequest create = folderChange(ViewsheetChangeRequest.VERB_CREATE, "admin^SELF");
      create.setFolderName("NewF");

      assertThrows(IllegalArgumentException.class, () -> preview(create));
   }

   // ------------------------------------------------------------------ helpers

   /** The registry save path reaches {@code AnalyticRepository}, which BaseTestConfiguration
    * lacks. */
   @Configuration
   static class RegistryConfig {
      @Bean
      AnalyticRepository analyticRepository() {
         return mock(AnalyticRepository.class);
      }
   }

   private static ViewsheetChangeRequest folderChange(String verb, String owner) {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(verb);
      change.setOwner(owner);
      return change;
   }

   private static ViewsheetChangeRequest sameWithOwner(ViewsheetChangeRequest change, String owner) {
      ViewsheetChangeRequest copy = folderChange(change.getVerb(), owner);
      copy.setPath(change.getPath());
      copy.setAlias(change.getAlias());
      return copy;
   }

   private ResolvedPlan preview(ViewsheetChangeRequest change) throws Exception {
      ViewsheetChangePlanRequest req = new ViewsheetChangePlanRequest();
      req.setTask("folder");
      req.setChanges(List.of(change));
      return planService.resolve(req, user);
   }

   private ViewsheetApplyResult apply(ResolvedPlan plan, ViewsheetChangeRequest change)
      throws Exception
   {
      ViewsheetApplyRequest req = new ViewsheetApplyRequest();
      req.setTask("folder");
      req.setChanges(List.of(change));
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved");
      return applyService.apply(req, user);
   }
}
