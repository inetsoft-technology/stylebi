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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.FixtureTabularDataSource;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.datasource.*;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78817: a data source stored under a folder ("Examples/Orders") is identified by its full,
 * folder-qualified name everywhere -- the list, the get-by-id DTO, the change plan's current
 * value and id resolution, and the apply after-check. Before the fix the get-by-id DTO carried
 * only the leaf ("Orders"), so preview showed name=Orders, an id-only preview failed, and an
 * applied update was reported as "rolled-back" although the change stayed.
 *
 * <p>Runs the real {@link DataSourceService}, {@link DataSourceChangePlanService} and
 * {@link DataSourceChangesetApplyService} over real {@link JDBCDataSource} and tabular data
 * sources; only the repository is an in-memory fake keyed by full name, as the real one is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceFolderFullNameTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderFullNameTest {
   @BeforeEach
   void setUp() throws Exception {
      store.clear();
      failingUpdates.clear();
      put(jdbc("Examples/Orders", "jdbc:h2:mem:a"));

      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames(any(IdentityID.class)))
         .thenAnswer(inv -> store.keySet().toArray(new String[0]));
      when(repository.getDataSource(anyString()))
         .thenAnswer(inv -> copy(store.get(inv.<String>getArgument(0))));
      when(repository.getDataSource(anyString(), any()))
         .thenAnswer(inv -> copy(store.get(inv.<String>getArgument(0))));
      doAnswer(inv -> {
         XDataSource ds = inv.getArgument(0);
         String oldName = inv.getArgument(1);

         if(failingUpdates.contains(oldName)) {
            throw new IOException("write failed");
         }

         store.remove(oldName);
         store.put(ds.getFullName(), ds);
         return null;
      }).when(repository).updateDataSource(any(), anyString(), anyBoolean());

      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);

      ids = new MD5IdentifierGenerator();
      dataSourceService = new DataSourceService(
         repository, security, mock(DatabaseTypeService.class), ids,
         mock(DataSourceRegistry.class));
      planService = new DataSourceChangePlanService(dataSourceService,
                                                    mock(DatabaseSettingsService.class));
      AdminBackupService backup = mock(AdminBackupService.class);
      when(backup.backup(anyString())).thenReturn("backup-ref");
      applyService = new DataSourceChangesetApplyService(planService, dataSourceService, backup);

      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      dependencyTool = mockStatic(DependencyTool.class);
      dependencyTool.when(() -> DependencyTool.getDependencies(anyString())).thenReturn(List.of());
      // a rename queues a dependency transform; there are no dependents here
      dependencyTransformer = mockStatic(DependencyTransformer.class);

      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      dependencyTransformer.close();
      dependencyTool.close();
      audit.close();
   }

   @Test
   void getByIdReturnsTheFullNameLikeTheList() throws Exception {
      put(tabular("Examples/Rest"));
      DataSourceList list = dataSourceService.getDataSources(principal);
      Map<String, String> idsByName = new HashMap<>();
      list.getDataSources().forEach(d -> idsByName.put(d.getName(), d.getId()));

      assertEquals(Set.of("Examples/Orders", "Examples/Rest"), idsByName.keySet());
      assertEquals("Examples/Orders",
                   dataSourceService.getDataSource(idsByName.get("Examples/Orders"), principal)
                      .getName());
      assertEquals("Examples/Rest",
                   dataSourceService.getDataSource(idsByName.get("Examples/Rest"), principal)
                      .getName());
   }

   // a GET -> PUT round trip of an unchanged data source must not move it out of its folder
   @Test
   void unchangedGetPutRoundTripKeepsTheFolder() throws Exception {
      String id = idOf("Examples/Orders");
      DataSourceProperties properties = dataSourceService.getDataSource(id, principal);

      dataSourceService.updateDataSource(id, properties, principal);

      assertEquals(Set.of("Examples/Orders"), store.keySet());
   }

   @Test
   void previewCurrentValueCarriesTheFullName() throws Exception {
      PlanChange change = preview(update(null, "Examples/Orders", Map.of("ansiJoin", true)));

      assertTrue(change.currentValue().contains("name=Examples/Orders;"), change.currentValue());
      assertTrue(change.proposedValue().contains("name=Examples/Orders;"), change.proposedValue());
      assertFalse(change.description().contains("renaming to"), change.description());
   }

   @Test
   void tabularDeletePreviewCurrentValueCarriesTheFullName() throws Exception {
      put(tabular("Examples/Rest"));
      DataSourceChangeRequest delete = new DataSourceChangeRequest();
      delete.setVerb(DataSourceChangeRequest.VERB_DELETE);
      delete.setName("Examples/Rest");

      PlanChange change = preview(delete);

      assertTrue(change.currentValue().contains("name=Examples/Rest;"), change.currentValue());
   }

   @Test
   void idOnlyPreviewResolvesTheFolderedDataSource() throws Exception {
      PlanChange change = preview(update(idOf("Examples/Orders"), null, Map.of("ansiJoin", true)));

      assertEquals("Examples/Orders", change.property());
      assertTrue(change.currentValue().contains("name=Examples/Orders;"), change.currentValue());
   }

   @Test
   void idAndFullNamePreviewIsNotRefusedAsAMismatch() throws Exception {
      PlanChange change = preview(
         update(idOf("Examples/Orders"), "Examples/Orders", Map.of("ansiJoin", true)));

      assertEquals("Examples/Orders", change.property());
   }

   @Test
   void idAndLeafNamePreviewIsStillRefusedAsAMismatch() {
      DataSourceChangeRequest change =
         update(idOf("Examples/Orders"), "Orders", Map.of("ansiJoin", true));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> preview(change));
      assertTrue(ex.getMessage().contains("does not match"), ex.getMessage());
   }

   // the after-check compared the leaf name read back with the full name proposed, so the update
   // stayed but was reported as rolled back
   @Test
   void applyOfAnUpdateToAFolderedDataSourceIsVerified() throws Exception {
      var result = apply(update(null, "Examples/Orders", Map.of("url", "jdbc:h2:mem:b")));

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(), result.toString());
      assertEquals("verified", result.results().get(0).status(), result.toString());
      assertTrue(result.results().get(0).after().contains("name=Examples/Orders;"),
                 result.results().get(0).after());
      assertEquals(Set.of("Examples/Orders"), store.keySet());
      assertEquals("jdbc:h2:mem:b", ((JDBCDataSource) store.get("Examples/Orders")).getURL());
   }

   @Test
   void applyOfATabularRenameWithinAFolderIsVerified() throws Exception {
      put(tabular("Examples/Rest"));
      DataSourceChangeRequest rename = update(null, "Examples/Rest",
                                              Map.of("name", "Examples/Rest2"));
      rename.setConfirmRename(true);

      var result = apply(rename);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status(), result.toString());
      assertEquals("verified", result.results().get(0).status(), result.toString());
      assertTrue(store.containsKey("Examples/Rest2"), store.keySet().toString());
   }

   // rolling back a verified update of a data source in a folder writes back its full name, so
   // the data source stays in its folder
   @Test
   void rollbackOfAFolderedUpdateKeepsItInItsFolder() throws Exception {
      put(jdbc("Other", "jdbc:h2:mem:o"));
      DataSourceChangeRequest first =
         update(null, "Examples/Orders", Map.of("url", "jdbc:h2:mem:b"));
      DataSourceChangeRequest second = update(null, "Other", Map.of("url", "jdbc:h2:mem:p"));
      DataSourceApplyRequest req = applyRequest(first, second);
      failingUpdates.add("Other");

      var result = applyService.apply(req, principal);

      // the failed write leaves "Other" in an unknown state, which alone makes it rollback-failed;
      // the foldered update itself must be rolled back cleanly
      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status(),
                   result.toString());
      assertEquals(List.of("Other"),
                   result.rollbackFailures().stream().map(f -> f.property()).toList());
      assertEquals(Set.of("Examples/Orders", "Other"), store.keySet());
      assertEquals("jdbc:h2:mem:a", ((JDBCDataSource) store.get("Examples/Orders")).getURL());
   }

   private PlanChange preview(DataSourceChangeRequest change) throws Exception {
      DataSourceChangePlanRequest req = new DataSourceChangePlanRequest();
      req.setTask("bug 78817");
      req.setChanges(List.of(change));
      return planService.resolve(req, principal).changes().get(0);
   }

   private DataSourceApplyResult apply(DataSourceChangeRequest... changes) throws Exception {
      return applyService.apply(applyRequest(changes), principal);
   }

   private DataSourceApplyRequest applyRequest(DataSourceChangeRequest... changes)
      throws Exception
   {
      DataSourceChangePlanRequest probe = new DataSourceChangePlanRequest();
      probe.setTask("bug 78817");
      probe.setChanges(List.of(changes));
      ResolvedPlan plan = planService.resolve(probe, principal);
      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask("bug 78817");
      req.setChanges(List.of(changes));
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private static DataSourceChangeRequest update(String id, String name, Map<String, Object> spec) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_UPDATE);
      change.setId(id);
      change.setName(name);
      change.setSpec(spec);
      return change;
   }

   private String idOf(String fullName) {
      return ids.getId(fullName);
   }

   private void put(XDataSource ds) {
      store.put(ds.getFullName(), ds);
   }

   private static XDataSource copy(XDataSource ds) {
      return ds == null ? null : (XDataSource) ds.clone();
   }

   private static JDBCDataSource jdbc(String name, String url) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setDriver("org.h2.Driver");
      ds.setURL(url);
      ds.setRequireLogin(false);
      return ds;
   }

   private static XDataSource tabular(String name) {
      FixtureTabularDataSource ds = new FixtureTabularDataSource();
      ds.setName(name);
      return ds;
   }

   private final Map<String, XDataSource> store = new LinkedHashMap<>();
   private final Set<String> failingUpdates = new HashSet<>();
   private MD5IdentifierGenerator ids;
   private DataSourceService dataSourceService;
   private DataSourceChangePlanService planService;
   private DataSourceChangesetApplyService applyService;
   private Principal principal;
   private MockedStatic<Audit> audit;
   private MockedStatic<DependencyTool> dependencyTool;
   private MockedStatic<DependencyTransformer> dependencyTransformer;

   @Configuration
   static class Beans {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
