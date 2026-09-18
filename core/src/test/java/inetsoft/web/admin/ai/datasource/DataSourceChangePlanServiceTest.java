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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 13. Focuses on what this area alone needs, beyond the shared preview/hash
 * mechanics every prior area already covers: section 0.1's password-merge mechanism (never
 * trusting the wrapped API's own masked DTO field as a write input, real password never leaking
 * into a hash/audit projection), section 0.2's dependency preflight for delete, 03-reconcile.md
 * Addition 1's {@code confirmRename} safeguard, and the tabular field-restriction refusal.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSourceChangePlanServiceTest {
   @Mock private DataSourceService dataSourceService;
   @Mock private DatabaseSettingsService databaseSettingsService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private DataSourceChangePlanService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<DependencyTool> dependencyToolStatic;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new DataSourceChangePlanService(dataSourceService, databaseSettingsService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      dependencyToolStatic = mockStatic(DependencyTool.class, withSettings().lenient());
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      dependencyToolStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      DataSourceChangePlanRequest req = request("   ", List.of(deleteChange("Orders")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      DataSourceChangePlanRequest req = request("do something", List.of());

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      DataSourceChangeRequest change = deleteChange("Orders");
      change.setVerb("rename");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveAcceptsModifyAsUpdateAlias() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb("modify");
      change.setName("Orders");
      change.setSpec(Map.of("driver", "org.h2.Driver"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveAcceptsRemoveAsDeleteAlias() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb("remove");
      change.setName("Orders");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveThrowsWhenNeitherIdNorNameGiven() {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("id/name"));
   }

   @Test void resolveThrowsWhenIdAndNameResolveDifferently() throws Exception {
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);
      change.setId("id-1");
      change.setName("SomethingElse");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("does not match"));
   }

   @Test void resolveThrowsOnDuplicateEntry() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change1 = deleteChange("Orders");
      DataSourceChangeRequest change2 = deleteChange("Orders");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change1, change2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // update: field validation
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenSpecMissing() {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_UPDATE);
      change.setName("Orders");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   @Test void resolveUpdateThrowsOnUnrecognizedJdbcField() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = updateChange("Orders", Map.of("bogusField", "x"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("bogusField"));
   }

   @Test void resolveUpdateTabularRefusesFieldsOtherThanName() throws Exception {
      stubList("Rest1", "id-2", "tabular");
      stubGet("id-2", tabular("id-2", "Rest1", "Rest"));
      DataSourceChangeRequest change = updateChange("Rest1", Map.of("tabularType", "Other"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("tabularType"));
   }

   @Test void resolveUpdateTabularAllowsNameOnly() throws Exception {
      stubList("Rest1", "id-2", "tabular");
      stubGet("id-2", tabular("id-2", "Rest1", "Rest"));
      DataSourceChangeRequest change = updateChange("Rest1", Map.of("name", "Rest1"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // -------------------------------------------------------------------------
   // confirmRename (03-reconcile.md Addition 1)
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenNameDiffersWithoutConfirmRename() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = updateChange("Orders", Map.of("name", "Orders2"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("confirmRename"));
   }

   @Test void resolveUpdateSucceedsWhenNameDiffersWithConfirmRenameTrue() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = updateChange("Orders", Map.of("name", "Orders2"));
      change.setConfirmRename(true);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveUpdateDoesNotRequireConfirmRenameWhenNameUnchanged() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = updateChange("Orders", Map.of("url", "jdbc:h2:mem:test"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // Bug 76457: for a data source under a non-root folder, the list-resolved identity
   // ("Examples/Orders") is folder-qualified while the get-by-id DTO's own name
   // (JdbcDataSourceProperties, mirroring the wrapped API's own documented bare-name quirk) is
   // bare ("Orders"). A field-only update with no spec.name must not be misread as a rename just
   // because those two representations differ.
   @Test void resolveUpdateDoesNotRequireConfirmRenameForFolderQualifiedNameWithFieldOnlySpec()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("ansiJoin", true));

      var plan = service.resolve(request("task", List.of(change)), user);

      assertFalse(plan.changes().get(0).description().contains("renaming to"));
   }

   @Test void resolveUpdateDoesNotRequireConfirmRenameForFolderQualifiedNameWithNoOpSpec()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("tableName", JdbcDataSourceProperties.DEFAULT_OPTION));

      var plan = service.resolve(request("task", List.of(change)), user);

      assertFalse(plan.changes().get(0).description().contains("renaming to"));
   }

   @Test void resolveUpdateStillRequiresConfirmRenameForAnExplicitDifferingNameUnderAFolder()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("name", "Examples/Orders2"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("confirmRename"));
   }

   @Test void buildProposedJdbcDefaultsNameToTheResolvedFolderQualifiedNameWhenSpecOmitsIt()
      throws Exception
   {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Examples/Orders", current, Map.of("ansiJoin", true), user);

      assertEquals("Examples/Orders", proposed.getName());
   }

   // -------------------------------------------------------------------------
   // section 0.1: password merge -- proposed value (real API-call input)
   // -------------------------------------------------------------------------

   @Test void buildProposedJdbcOmittedPasswordPreservesRealValue() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");
      when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn("real-secret");

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Orders", current, Map.of("url", "jdbc:h2:mem:test2"), user);

      assertEquals("real-secret", proposed.getPassword());
      verify(dataSourceService).getCurrentPassword("id-1", user);
   }

   @Test void buildProposedJdbcExplicitNewPasswordUsesNewValue() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Orders", current, Map.of("password", "new-secret"), user);

      assertEquals("new-secret", proposed.getPassword());
      verify(dataSourceService, never()).getCurrentPassword(anyString(), any());
   }

   @Test void buildProposedJdbcRefusesWrappedApiMaskLiteral() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("password", "******"), user));
      assertTrue(ex.getMessage().contains("masked placeholder"));
   }

   @Test void buildProposedJdbcRefusesPlaceholderPasswordConstant() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("password", Util.PLACEHOLDER_PASSWORD), user));
      assertTrue(ex.getMessage().contains("masked placeholder"));
   }

   @Test void buildProposedJdbcThrowsWhenEnablingRequireLoginWithoutPassword() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("requireLogin", true), user));
      assertTrue(ex.getMessage().contains("password"));
   }

   @Test void buildProposedJdbcClearsPasswordWhenDisablingRequireLogin() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Orders", current, Map.of("requireLogin", false), user);

      assertNull(proposed.getPassword());
      verify(dataSourceService, never()).getCurrentPassword(anyString(), any());
   }

   // -------------------------------------------------------------------------
   // section 0.1: password merge -- before-value capture (rollback input)
   // -------------------------------------------------------------------------

   @Test void buildBeforeJdbcCapturesRealPasswordWhenRequireLoginTrue() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");
      when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn("real-secret");

      JdbcDataSourceProperties before = service.buildBeforeJdbc("id-1", current, user);

      assertEquals("real-secret", before.getPassword());
   }

   @Test void buildBeforeJdbcDoesNotReadPasswordWhenRequireLoginFalse() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);

      service.buildBeforeJdbc("id-1", current, user);

      verify(dataSourceService, never()).getCurrentPassword(anyString(), any());
   }

   // -------------------------------------------------------------------------
   // the real password must never appear in a hash/audit projection -- string search, not just
   // field omission (spec section 13's own stronger assertion discipline)
   // -------------------------------------------------------------------------

   @Test void realPasswordNeverAppearsInProposedOrBeforeProjection() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");
      String realPassword = "super-secret-value-should-never-leak";
      when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn(realPassword);

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Orders", current, Map.of("url", "jdbc:h2:mem:test2"), user);
      JdbcDataSourceProperties before = service.buildBeforeJdbc("id-1", current, user);

      // Sanity: the DTOs really do carry the real password internally (else this test would prove
      // nothing) -- the projection is what must never leak it.
      assertEquals(realPassword, proposed.getPassword());
      assertEquals(realPassword, before.getPassword());

      String proposedProjection = DataSourceProjection.project(proposed);
      String beforeProjection = DataSourceProjection.project(before);
      assertFalse(proposedProjection.contains(realPassword));
      assertFalse(beforeProjection.contains(realPassword));
      assertTrue(proposedProjection.contains(Util.PLACEHOLDER_PASSWORD));
      assertTrue(beforeProjection.contains(Util.PLACEHOLDER_PASSWORD));
   }

   // -------------------------------------------------------------------------
   // useCredentialId cross-validation
   // -------------------------------------------------------------------------

   @Test void buildProposedJdbcThrowsWhenUseCredentialTrueWithoutCredentialID() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("useCredentialId", true), user));
      assertTrue(ex.getMessage().contains("credentialID"));
   }

   @Test void buildProposedJdbcThrowsWhenUseCredentialTrueWithPasswordField() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("useCredentialId", true);
      spec.put("credentialID", "secret-ref-1");
      spec.put("password", "oops");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current, spec, user));
      assertTrue(ex.getMessage().contains("credentialID"));
   }

   @Test void buildProposedJdbcThrowsWhenUseCredentialFalseWithCredentialIDField() {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("credentialID", "secret-ref-1"), user));
      assertTrue(ex.getMessage().contains("useCredentialId"));
   }

   @Test void buildProposedJdbcAcceptsUseCredentialTrueWithCredentialID() throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      JdbcDataSourceProperties proposed = service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
         Map.of("useCredentialId", true, "credentialID", "secret-ref-1"), user);

      assertTrue(proposed.isUseCredentialId());
      assertEquals("secret-ref-1", proposed.getCredentialID());
   }

   // -------------------------------------------------------------------------
   // section 0.2: dependency preflight for delete
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenDependenciesExistAndForceFalse() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      AssetObject dep = dependency("Examples/Orders Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      DataSourceChangeRequest change = deleteChange("Orders");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("force"));
   }

   @Test void resolveDeleteSucceedsWhenDependenciesExistAndForceTrue() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      AssetObject dep = dependency("Examples/Orders Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      DataSourceChangeRequest change = deleteChange("Orders");
      change.setForce(true);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveDeleteSucceedsWhenNoDependencies() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));

      assertDoesNotThrow(
         () -> service.resolve(request("task", List.of(deleteChange("Orders"))), user));
   }

   @Test void resolveDeleteThrowsWhenSpecPresent() {
      DataSourceChangeRequest change = deleteChange("Orders");
      change.setSpec(Map.of("url", "x"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("spec"));
   }

   // -------------------------------------------------------------------------
   // hash stability
   // -------------------------------------------------------------------------

   @Test void hashIsStableForIdenticalRequests() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = deleteChange("Orders");

      var first = service.resolve(request("t", List.of(change)), user);
      var second = service.resolve(request("t", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void issuesATaskTokenBoundToThePlanHash() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = deleteChange("Orders");

      var plan = service.resolve(request("t", List.of(change)), user);

      assertEquals("TKN:" + plan.planHash() + "\u001ft", plan.taskToken());
   }

   @Test void hashChangesWhenDependencyListChanges() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = deleteChange("Orders");
      change.setForce(true);

      var beforePlan = service.resolve(request("t", List.of(change)), user);

      AssetObject dep = dependency("Examples/Orders Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      var afterPlan = service.resolve(request("t", List.of(change)), user);

      assertNotEquals(beforePlan.planHash(), afterPlan.planHash());
   }

   // task is a free-text, audit-only label (bug 76454) -- a caller that does not replay it
   // byte-for-byte between preview and apply must not see a false planHash conflict.
   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = deleteChange("Orders");

      var first = service.resolve(request("delete the Orders data source", List.of(change)), user);
      var second = service.resolve(request("remove Orders datasource", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void resolveSetsRiskHighAndScopeStorageUnconditionally() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));

      var plan = service.resolve(request("t", List.of(deleteChange("Orders"))), user);

      assertEquals("Orders", plan.changes().get(0).property());
      assertEquals("high", plan.changes().get(0).risk());
      assertEquals("storage", plan.changes().get(0).snapshotScope());
      assertTrue(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
   }

   // -------------------------------------------------------------------------
   // bug 76599, Gap 2a: bare folder create
   // -------------------------------------------------------------------------

   @Test void resolveAcceptsAddAsCreateAlias() throws Exception {
      DataSourceChangeRequest change = folderCreateChange("Examples/NewFolder");
      change.setVerb("add");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("low", plan.changes().get(0).risk());
   }

   @Test void resolveFolderCreateThrowsWhenFolderPathMissing() {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_CREATE);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("folderPath"));
   }

   @Test void resolveFolderCreateThrowsWhenFolderAlreadyExists() throws Exception {
      when(dataSourceService.dataSourceFolderExists("Examples/NewFolder")).thenReturn(true);
      DataSourceChangeRequest change = folderCreateChange("Examples/NewFolder");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveFolderCreateThrowsWhenNameGiven() {
      DataSourceChangeRequest change = folderCreateChange("Examples/NewFolder");
      change.setName("Orders");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test void resolveFolderCreateThrowsOnDuplicateEntry() {
      DataSourceChangeRequest change1 = folderCreateChange("Examples/NewFolder");
      DataSourceChangeRequest change2 = folderCreateChange("Examples/NewFolder");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change1, change2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   @Test void resolveFolderCreateSucceedsAndClassifiesRiskLow() throws Exception {
      DataSourceChangeRequest change = folderCreateChange("Examples/NewFolder");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("low", plan.changes().get(0).risk());
      assertEquals("storage", plan.changes().get(0).snapshotScope());
   }

   // -------------------------------------------------------------------------
   // bug 76599, Gap 1: test connection
   // -------------------------------------------------------------------------

   @Test void testConnectionMergesSpecAndDelegatesToDatabaseSettingsService() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      ConnectionStatus expected = new ConnectionStatus("ok", true);
      when(databaseSettingsService.testConnection(any(), eq(user))).thenReturn(expected);

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("url", "jdbc:h2:mem:test2"));

      ConnectionStatus result = service.testConnection(req, user);

      assertSame(expected, result);
      verify(databaseSettingsService).testConnection(argThat(
         model -> "jdbc:h2:mem:test2".equals(model.databaseURL())
            && "org.h2.Driver".equals(model.driver())), eq(user));
   }

   @Test void testConnectionOmittedPasswordPreservesRealValue() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));
      when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn("real-secret");
      when(databaseSettingsService.testConnection(any(), eq(user)))
         .thenReturn(new ConnectionStatus("ok", true));

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("url", "jdbc:h2:mem:test2"));

      service.testConnection(req, user);

      verify(databaseSettingsService).testConnection(
         argThat(model -> "real-secret".equals(model.password())), eq(user));
   }

   @Test void testConnectionThrowsForTabularDataSource() throws Exception {
      stubList("Rest1", "id-2", "tabular");
      stubGet("id-2", tabular("id-2", "Rest1", "Rest"));

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Rest1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("jdbc-type"));
      verifyNoInteractions(databaseSettingsService);
   }

   @Test void testConnectionThrowsWhenNeitherIdNorNameGiven() {
      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("id/name"));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubList(String name, String id, String type) throws Exception {
      DataSourceDescription description = new DataSourceDescription();
      description.setId(id);
      description.setName(name);
      description.setType(type);
      DataSourceList list = new DataSourceList();
      list.setDataSources(List.of(description));
      lenient().when(dataSourceService.getDataSources(name, user)).thenReturn(list);
   }

   private void stubGet(String id, DataSourceProperties properties) throws Exception {
      lenient().when(dataSourceService.getDataSource(id, user)).thenReturn(properties);
   }

   private static JdbcDataSourceProperties jdbc(String id, String name, boolean requireLogin,
                                                String password)
   {
      JdbcDataSourceProperties p = new JdbcDataSourceProperties();
      p.setId(id);
      p.setName(name);
      p.setUrl("jdbc:h2:mem:test");
      p.setDriver("org.h2.Driver");
      p.setDefaultDatabase("APP");
      p.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      p.setIsolation(JdbcDataSourceProperties.IsolationLevel.DEFAULT);
      p.setAnsiJoin(false);
      p.setRequireLogin(requireLogin);
      p.setUser(requireLogin ? "sa" : null);
      p.setPassword(password);
      p.setUseCredentialId(false);
      return p;
   }

   private static TabularDataSourceProperties tabular(String id, String name, String tabularType) {
      TabularDataSourceProperties p = new TabularDataSourceProperties();
      p.setId(id);
      p.setName(name);
      p.setTabularType(tabularType);
      return p;
   }

   private static AssetObject dependency(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY, path, null,
                            "host-org");
   }

   private static DataSourceChangePlanRequest request(String task,
                                                       List<DataSourceChangeRequest> changes)
   {
      DataSourceChangePlanRequest req = new DataSourceChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static DataSourceChangeRequest updateChange(String name, Map<String, Object> spec) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_UPDATE);
      change.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static DataSourceChangeRequest deleteChange(String name) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);
      change.setName(name);
      return change;
   }

   private static DataSourceChangeRequest folderCreateChange(String folderPath) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_CREATE);
      change.setFolderPath(folderPath);
      return change;
   }
}
