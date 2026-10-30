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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.admin.datasource.*;
import inetsoft.web.admin.datasource.JdbcDataSourceProperties.IsolationLevel;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.Principal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

   // Bug 76457: for a data source under a non-root folder, the list-resolved identity is
   // folder-qualified ("Examples/Orders"). A field-only update with no spec.name must not be
   // misread as a rename. Bug 78817: the get-by-id DTO (JdbcDataSourceProperties) now carries the
   // same full name as the list, so these stubs use it too (a bare "Orders" stub encoded the old
   // defect).
   @Test void resolveUpdateDoesNotRequireConfirmRenameForFolderQualifiedNameWithFieldOnlySpec()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Examples/Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("ansiJoin", true));

      var plan = service.resolve(request("task", List.of(change)), user);

      assertFalse(plan.changes().get(0).description().contains("renaming to"));
   }

   @Test void resolveUpdateDoesNotRequireConfirmRenameForFolderQualifiedNameWithNoOpSpec()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Examples/Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("tableName", JdbcDataSourceProperties.DEFAULT_OPTION));

      var plan = service.resolve(request("task", List.of(change)), user);

      assertFalse(plan.changes().get(0).description().contains("renaming to"));
   }

   @Test void resolveUpdateStillRequiresConfirmRenameForAnExplicitDifferingNameUnderAFolder()
      throws Exception
   {
      stubList("Examples/Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Examples/Orders", false, null));
      DataSourceChangeRequest change =
         updateChange("Examples/Orders", Map.of("name", "Examples/Orders2"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("confirmRename"));
   }

   @Test void buildProposedJdbcDefaultsNameToTheResolvedFolderQualifiedNameWhenSpecOmitsIt()
      throws Exception
   {
      JdbcDataSourceProperties current = jdbc("id-1", "Examples/Orders", false, null);

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
   // bug 78818: padded / other-length masks are refused, real values stored verbatim
   // -------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = {
      " ********** ", "**********\n", "\t******", "\u00a0**********", "\uFEFF******",
      "\u200B**********", "  ******  ", "***", "*", "********", "************",
      "******", "**********"
   })
   void buildProposedJdbcRefusesMaskLikePassword(String password) {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current,
            Map.of("password", password), user));
      assertTrue(ex.getMessage().contains("masked placeholder"), ex.getMessage());
      assertTrue(ex.getMessage().contains("made only of asterisks"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Enterprise Manager"), ex.getMessage());
      assertTrue(ex.getMessage().startsWith("changes[0].spec.password:"), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "a*b", "*a*", "** **", " real pw ", "pass*****", "*****word", "" })
   void buildProposedJdbcStoresNonMaskPasswordVerbatim(String password) throws Exception {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", true, "******");

      JdbcDataSourceProperties proposed = service.buildProposedJdbc(
         "changes[0]", "id-1", "Orders", current, Map.of("password", password), user);

      assertEquals(password, proposed.getPassword());
      verify(dataSourceService, never()).getCurrentPassword(anyString(), any());
   }

   @Test void previewRefusesPaddedMaskPassword() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(
            updateChange("Orders", Map.of("password", " ********** ")))), user));
      assertTrue(ex.getMessage().contains("made only of asterisks"), ex.getMessage());
   }

   @Test void testConnectionRefusesPaddedMaskPassword() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("password", "\u00a0**********"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("made only of asterisks"), ex.getMessage());
      verifyNoInteractions(databaseSettingsService);
   }

   @Test void applyRefusesPaddedMaskPasswordBeforeAnyWrite() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));
      AdminBackupService backupService = mock(AdminBackupService.class);
      DataSourceChangesetApplyService applyService =
         new DataSourceChangesetApplyService(service, dataSourceService, backupService);

      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask("task");
      req.setChanges(List.of(updateChange("Orders", Map.of("password", "**********\n"))));
      req.setPlanHash("any");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> applyService.apply(req, user));
      assertTrue(ex.getMessage().contains("made only of asterisks"), ex.getMessage());
      verifyNoInteractions(backupService);
      verify(dataSourceService, never()).updateDataSource(anyString(), any(), any());
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
   // spec.isolation accepts the JDBC value form (Bug #78820)
   // -------------------------------------------------------------------------

   static Stream<Arguments> acceptedIsolationInputs() {
      return Stream.of(
         Arguments.of(2, IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of("2", IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of(" 2 ", IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of(2L, IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of(2.0, IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of(-1, IsolationLevel.DEFAULT),
         Arguments.of("-1", IsolationLevel.DEFAULT),
         Arguments.of(1, IsolationLevel.TRANSACTION_READ_UNCOMMITTED),
         Arguments.of("4", IsolationLevel.TRANSACTION_REPEATABLE_READ),
         Arguments.of(8, IsolationLevel.TRANSACTION_SERIALIZABLE),
         Arguments.of(8.0f, IsolationLevel.TRANSACTION_SERIALIZABLE),
         Arguments.of(new BigDecimal("4"), IsolationLevel.TRANSACTION_REPEATABLE_READ),
         Arguments.of(BigInteger.valueOf(8), IsolationLevel.TRANSACTION_SERIALIZABLE),
         // undocumented legacy alias: the enum constant name, case-insensitive
         Arguments.of("TRANSACTION_READ_COMMITTED", IsolationLevel.TRANSACTION_READ_COMMITTED),
         Arguments.of("transaction_serializable", IsolationLevel.TRANSACTION_SERIALIZABLE));
   }

   @ParameterizedTest
   @MethodSource("acceptedIsolationInputs")
   void buildProposedJdbcAcceptsIsolationValue(Object input, IsolationLevel expected)
      throws Exception
   {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("isolation", input);

      JdbcDataSourceProperties proposed =
         service.buildProposedJdbc("changes[0]", "id-1", "Orders", current, spec, user);

      assertEquals(expected, proposed.getIsolation());
      String projection = DataSourceProjection.project(proposed);
      assertTrue(projection.contains(";isolation=" + expected.value + ";"), projection);
   }

   static Stream<Arguments> refusedIsolationInputs() {
      return Stream.of(
         Arguments.of(3),
         Arguments.of("3"),
         Arguments.of(0),
         Arguments.of(2.5),
         Arguments.of(-0.5),
         Arguments.of("2.0"),
         Arguments.of("+2"),
         Arguments.of("abc"),
         Arguments.of(""),
         Arguments.of("read_committed"),
         Arguments.of(Double.NaN),
         Arguments.of(Double.POSITIVE_INFINITY),
         Arguments.of(Float.NEGATIVE_INFINITY),
         // must not wrap through intValue(): 99999999999L.intValue() = 1215752191
         Arguments.of(99999999999L),
         // 2^64 + 2: BigInteger.intValue() would wrap to exactly 2 (TRANSACTION_READ_COMMITTED)
         Arguments.of(new BigInteger("18446744073709551618")),
         Arguments.of(new BigDecimal("1E+999999999")),
         // 2^32 + 2 as a string: an 11-digit string is refused before parsing
         Arguments.of("4294967298"),
         Arguments.of("00000000002"),
         Arguments.of(true),
         Arguments.of(List.of(2)),
         Arguments.of(Map.of("value", 2)));
   }

   @ParameterizedTest
   @MethodSource("refusedIsolationInputs")
   void buildProposedJdbcRefusesIsolationValue(Object input) {
      assertIsolationRefused(input);
   }

   @Test void buildProposedJdbcRefusesNullIsolation() {
      // null must never become a null IsolationLevel: DataSourceService.updateDataSource
      // dereferences getIsolation().value when persisting.
      assertIsolationRefused(null);
   }

   @Test void buildProposedJdbcRefusesAHugeDigitStringQuickly() {
      String huge = "2".repeat(1_000_000);

      assertTimeout(Duration.ofSeconds(2), () -> assertIsolationRefused(huge));
   }

   @Test void isolationRefusalListsTheNumericValuesOnly() {
      IllegalArgumentException ex = assertIsolationRefused("abc");

      assertTrue(ex.getMessage().startsWith(
         "changes[0].spec.isolation: must be one of [-1, 1, 2, 4, 8] "), ex.getMessage());
      assertTrue(ex.getMessage().endsWith(", got abc"), ex.getMessage());

      for(IsolationLevel level : IsolationLevel.values()) {
         assertFalse(ex.getMessage().contains(level.name()), ex.getMessage());
      }
   }

   @Test void previewAcceptsNumericIsolationAndHashesEveryFormIdentically() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));

      var fromNumber = service.resolve(
         request("t", List.of(updateChange("Orders", Map.of("isolation", 2)))), user);
      var fromString = service.resolve(
         request("t", List.of(updateChange("Orders", Map.of("isolation", "2")))), user);
      var fromName = service.resolve(request("t", List.of(updateChange(
         "Orders", Map.of("isolation", "TRANSACTION_READ_COMMITTED")))), user);

      String proposedValue = fromNumber.changes().get(0).proposedValue();
      String currentValue = fromNumber.changes().get(0).currentValue();
      assertTrue(proposedValue.contains(";isolation=2;"), proposedValue);
      assertTrue(currentValue.contains(";isolation=-1;"), currentValue);
      assertEquals(fromNumber.planHash(), fromString.planHash());
      assertEquals(fromNumber.planHash(), fromName.planHash());
   }

   @Test void previewAcceptsTheIsolationStringReturnedByGetDataSource() throws Exception {
      // get_data_source serializes isolation through the enum's @JsonValue toString(): "-1".
      ObjectMapper mapper = new ObjectMapper();
      Object roundTripped = mapper.readValue(
         mapper.writeValueAsString(jdbc("id-1", "Orders", false, null)), Map.class)
         .get("isolation");
      assertEquals("-1", roundTripped);

      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);
      current.setIsolation(IsolationLevel.TRANSACTION_SERIALIZABLE);
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", current);

      var plan = service.resolve(request(
         "t", List.of(updateChange("Orders", Map.of("isolation", roundTripped)))), user);

      String proposedValue = plan.changes().get(0).proposedValue();
      assertTrue(proposedValue.contains(";isolation=-1;"), proposedValue);
   }

   @Test void testConnectionAcceptsNumericIsolation() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      ConnectionStatus expected = new ConnectionStatus("ok", true);
      when(databaseSettingsService.testConnection(any(), eq(user))).thenReturn(expected);

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("isolation", "2"));

      assertSame(expected, service.testConnection(req, user));
   }

   @Test void testConnectionRefusesAPendingCredentialId() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("useCredentialId", true, "credentialID", "no-such-secret"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("credentialID"), ex.getMessage());
      verify(databaseSettingsService, never()).testConnection(any(), any());
   }

   @Test void testConnectionRefusesLeavingCredentialModeOnASavedCredentialSource()
      throws Exception
   {
      JdbcDataSourceProperties saved = jdbc("id-1", "Orders", true, "******");
      saved.setUseCredentialId(true);
      saved.setCredentialID("saved-secret");
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", saved);

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("useCredentialId", false));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("credentialID"), ex.getMessage());
      verify(databaseSettingsService, never()).testConnection(any(), any());
   }

   @Test void testConnectionStillProbesASavedCredentialSourceWithoutASpec() throws Exception {
      JdbcDataSourceProperties saved = jdbc("id-1", "Orders", true, "******");
      saved.setUseCredentialId(true);
      saved.setCredentialID("saved-secret");
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", saved);
      ConnectionStatus expected = new ConnectionStatus("ok", true);
      when(databaseSettingsService.testConnection(any(), eq(user))).thenReturn(expected);

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");

      assertSame(expected, service.testConnection(req, user));
   }

   @Test void testConnectionRefusesAnUnknownIsolationValue() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));

      DataSourceTestConnectionRequest req = new DataSourceTestConnectionRequest();
      req.setName("Orders");
      req.setSpec(Map.of("isolation", 3));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.testConnection(req, user));
      assertTrue(ex.getMessage().contains("spec.isolation: must be one of [-1, 1, 2, 4, 8]"),
                 ex.getMessage());
      verifyNoInteractions(databaseSettingsService);
   }

   private IllegalArgumentException assertIsolationRefused(Object input) {
      JdbcDataSourceProperties current = jdbc("id-1", "Orders", false, null);
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("isolation", input);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.buildProposedJdbc("changes[0]", "id-1", "Orders", current, spec, user),
         () -> "accepted isolation " + input);
      assertTrue(ex.getMessage().contains(
         "changes[0].spec.isolation: must be one of [-1, 1, 2, 4, 8]"), ex.getMessage());
      return ex;
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

   @Test void resolveDeleteForcePlanDescriptionNamesEveryDependent() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      var dep1 = dependency("Examples/Orders Query");
      var dep2 = dependency("Examples/Orders Summary");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep1, dep2));
      DataSourceChangeRequest change = deleteChange("Orders");
      change.setForce(true);

      String description = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).description();
      assertTrue(description.contains("2 dependent asset(s)"), description);
      assertTrue(description.contains("Examples/Orders Query"), description);
      assertTrue(description.contains("Examples/Orders Summary"), description);
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
   // Bug #78819: a supplied password is a (hidden) planHash input
   // -------------------------------------------------------------------------

   @Test void hashDiffersBetweenAPasswordRotationAndANoOpUpdate() throws Exception {
      stubLoginSource();

      var rotation = resolveUpdate(passwordSpec("AlphaPass-1"));
      var noOp = resolveUpdate(Map.of("ansiJoin", false));

      assertNotEquals(rotation.planHash(), noOp.planHash());
      assertNotEquals(rotation.changes().get(0).proposedValue(),
                      noOp.changes().get(0).proposedValue());
   }

   @Test void hashDiffersForTwoDifferentPasswords() throws Exception {
      stubLoginSource();

      var alpha = resolveUpdate(passwordSpec("AlphaPass-1"));
      var zulu = resolveUpdate(passwordSpec("ZuluPass-2"));

      assertNotEquals(alpha.planHash(), zulu.planHash());
   }

   @Test void hashIsStableForTheSamePassword() throws Exception {
      stubLoginSource();

      var first = resolveUpdate(passwordSpec("AlphaPass-1"));
      var second = resolveUpdate(passwordSpec("AlphaPass-1"));

      assertEquals(first.planHash(), second.planHash());
      assertEquals(first.taskToken(), second.taskToken());
   }

   @Test void hashDiffersBetweenNullPasswordAndTheStringNull() throws Exception {
      stubLoginSource();

      var cleared = resolveUpdate(passwordSpec(null));
      var literal = resolveUpdate(passwordSpec("null"));

      assertNotEquals(cleared.planHash(), literal.planHash());
   }

   // Pinned values computed on origin/stylebi-wiz-main-rebase 53e69703d8 BEFORE the fix: plans that
   // do not supply a password (or whose password is never written) must keep their exact hash, so
   // planHashes previewed before a deployment still apply after it.
   private static final String PINNED_URL_UPDATE_HASH =
      "fb09f601e6de45f8bf8be53a13231adaff649557f0f2e005c2252bbc4c4a6031";
   private static final String PINNED_LOGIN_OFF_HASH =
      "09ae2241dbbc6ee5900b1ed84c996f7b73d977f0b9c4e0157194e17c06ea85cc";
   private static final String PINNED_DELETE_HASH =
      "03562d80611956fa23cdd70320245a51f837acdd9ff7c475cea4cb3dca58b227";

   @Test void hashOfAPlanWithoutAPasswordIsUnchangedByTheFix() throws Exception {
      stubLoginSource();

      assertEquals(PINNED_URL_UPDATE_HASH,
                   resolveUpdate(Map.of("url", "jdbc:h2:mem:other")).planHash());
      assertEquals(PINNED_LOGIN_OFF_HASH,
                   resolveUpdate(passwordSpecWithLoginOff("ignored-pass")).planHash());
      assertEquals(PINNED_DELETE_HASH,
                   service.resolve(request("t", List.of(deleteChange("Orders"))), user).planHash());
   }

   @Test void omittedPasswordHashIgnoresTheStoredPassword() throws Exception {
      stubLoginSource();
      var before = resolveUpdate(Map.of("url", "jdbc:h2:mem:other"));

      // An out-of-band rotation of the stored password is not plan drift.
      when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn("rotated-elsewhere");
      var after = resolveUpdate(Map.of("url", "jdbc:h2:mem:other"));

      assertEquals(before.planHash(), after.planHash());
   }

   @Test void suppliedPasswordIsMarkedInThePlanWithoutRevealingIt() throws Exception {
      stubLoginSource();

      var plan = resolveUpdate(passwordSpec("AlphaPass-1"));
      var change = plan.changes().get(0);

      assertTrue(change.proposedValue().contains(
         ";password=" + DataSourceChangePlanService.PASSWORD_SUPPLIED_TOKEN + ";"));
      assertTrue(change.description().endsWith("(new password supplied)"),
                 change.description());
      assertFalse(change.description().contains("changed"));
      assertTrue(change.currentValue().contains(";password=" + Util.PLACEHOLDER_PASSWORD + ";"));

      for(String text : List.of(change.proposedValue(), change.currentValue(),
                                change.description(), plan.planHash()))
      {
         assertFalse(text.contains("AlphaPass-1"), text);
         assertFalse(text.contains("OldPass-0"), text);
      }
   }

   @Test void clearedPasswordIsMarkedAsCleared() throws Exception {
      stubLoginSource();

      var change = resolveUpdate(passwordSpec(null)).changes().get(0);

      assertTrue(change.proposedValue().contains(
         ";password=" + DataSourceChangePlanService.PASSWORD_CLEARED_TOKEN + ";"));
      assertTrue(change.description().endsWith("(password cleared)"), change.description());
      assertFalse(change.description().contains("supplied"));
   }

   @Test void planWithoutAPasswordIsNotMarked() throws Exception {
      stubLoginSource();

      var change = resolveUpdate(Map.of("url", "jdbc:h2:mem:other")).changes().get(0);

      assertTrue(change.proposedValue().contains(";password=" + Util.PLACEHOLDER_PASSWORD + ";"));
      assertEquals("update data source \"Orders\"", change.description());
   }

   @Test void renameWithASuppliedPasswordCarriesBothSuffixes() throws Exception {
      stubLoginSource();
      Map<String, Object> spec = new LinkedHashMap<>(passwordSpec("AlphaPass-1"));
      spec.put("name", "Orders2");
      DataSourceChangeRequest change = updateChange("Orders", spec);
      change.setConfirmRename(true);

      var description = service.resolve(request("t", List.of(change)), user)
         .changes().get(0).description();

      assertEquals("update data source \"Orders\" (renaming to \"Orders2\") " +
                   "(new password supplied)", description);
   }

   private void stubLoginSource() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", true, "******"));
      lenient().when(dataSourceService.getCurrentPassword("id-1", user)).thenReturn("OldPass-0");
   }

   private DataSourceChangePlanRequest updateRequest(Map<String, Object> spec) {
      return request("t", List.of(updateChange("Orders", spec)));
   }

   private inetsoft.web.admin.ai.ResolvedPlan resolveUpdate(Map<String, Object> spec)
      throws Exception
   {
      return service.resolve(updateRequest(spec), user);
   }

   private static Map<String, Object> passwordSpec(String password) {
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("password", password);
      return spec;
   }

   private static Map<String, Object> passwordSpecWithLoginOff(String password) {
      Map<String, Object> spec = passwordSpec(password);
      spec.put("requireLogin", false);
      return spec;
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
   // name validation (Bug #78347)
   // -------------------------------------------------------------------------

   @Test void resolveFolderCreateRefusesAnInvalidFolderName() throws Exception {
      for(String path : new String[] { "Examples/Bad>Name", "Cubes", "A//B" }) {
         DataSourceChangeRequest change = folderCreateChange(path);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", List.of(change)), user), path);
         assertTrue(ex.getMessage().contains("folderPath"), ex.getMessage());
      }

      verify(dataSourceService, never()).dataSourceFolderExists(anyString());
   }

   @Test void resolveFolderCreateAcceptsADotInsideAFolderName() throws Exception {
      DataSourceChangeRequest change = folderCreateChange("Examples/v1.2");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveUpdateRefusesAnInvalidNewName() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));

      for(String newName : new String[] { "Orders.v2", "Bad|Name", "Fo>lder/Orders" }) {
         DataSourceChangeRequest change = updateChange("Orders", Map.of("name", newName));
         change.setConfirmRename(true);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", List.of(change)), user), newName);
         assertTrue(ex.getMessage().contains("spec.name"), ex.getMessage());
      }
   }

   @Test void resolveUpdateAcceptsAFolderQualifiedNewName() throws Exception {
      stubList("Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "Orders", false, null));
      DataSourceChangeRequest change = updateChange("Orders", Map.of("name", "NewFolder/Orders"));
      change.setConfirmRename(true);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // A stored name that already breaks the rule stays editable when the name is left alone.
   @Test void resolveUpdateKeepsALegacyNameThatIsNotBeingChanged() throws Exception {
      stubList("My.Orders", "id-1", "jdbc");
      stubGet("id-1", jdbc("id-1", "My.Orders", false, null));
      DataSourceChangeRequest change = updateChange("My.Orders", Map.of("name", "My.Orders"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
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

   // -------------------------------------------------------------------------
   // Bug #79016: separator-forged specs must not share a projection or a plan hash
   // -------------------------------------------------------------------------

   private static Map<String, Object> urlAndDriver(String url, String driver) {
      Map<String, Object> spec = new LinkedHashMap<>();
      spec.put("url", url);
      spec.put("driver", driver);
      return spec;
   }

   @Test void urlAndDriverSplitAtADifferentSeparatorHashDifferently() throws Exception {
      stubLoginSource();

      var a = resolveUpdate(urlAndDriver("jdbc:vfy:A;driver=B", "C"));
      var b = resolveUpdate(urlAndDriver("jdbc:vfy:A", "B;driver=C"));

      assertNotEquals(a.changes().get(0).proposedValue(), b.changes().get(0).proposedValue());
      assertNotEquals(a.planHash(), b.planHash());
   }

   @Test void theSameForgedSpecPreviewedTwiceHashesTheSame() throws Exception {
      stubLoginSource();

      assertEquals(resolveUpdate(urlAndDriver("jdbc:vfy:A;driver=B", "C")).planHash(),
                   resolveUpdate(urlAndDriver("jdbc:vfy:A;driver=B", "C")).planHash());
   }

   @Test void separatorsInAValueAreEscapedInTheDisplayedProjection() throws Exception {
      stubLoginSource();

      String proposed = resolveUpdate(urlAndDriver("jdbc:vfy:A;driver=B", "C"))
         .changes().get(0).proposedValue();

      assertTrue(proposed.contains(";url=jdbc:vfy:A\\;driver\\=B;driver=C;"), proposed);
   }

   @Test void projectionOfAnOrdinaryValueIsUnchangedByEscaping() {
      JdbcDataSourceProperties p = jdbc("id-1", "Orders", false, null);

      String projection = DataSourceProjection.projectJdbc(p);

      assertTrue(projection.startsWith("type=jdbc;id=id-1;name=Orders;"), projection);
   }

   @Test void dependencyListEscapesACommaInAnAssetPath() {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "a,b", null, "host-org");

      String projection = DataSourceProjection.projectDependencies(List.of(entry));

      assertTrue(projection.contains("a\\,b"), projection);
   }
}
