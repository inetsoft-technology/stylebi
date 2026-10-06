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
package inetsoft.sree.schedule;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.util.Tool;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77893: renaming a worksheet, or a column of its primary table, must be followed by every
 * batch action of a schedule task, not only by the task's first action. The inner loop over an
 * action's query entries read the entry at the outer (action) index, so for any action after the
 * first a column rename was skipped, and a worksheet rename threw a NullPointerException that
 * skipped saving the task. The task is saved with {@link ScheduleTaskMap#put}, renamed with
 * {@link DependencyTransformer#transformAsset} and reloaded from storage.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  TaskAssetDependencyTransformerBatchActionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // about 10 s alone: the Spring context with the real dependency storage
class TaskAssetDependencyTransformerBatchActionTest {
   private static final String ORG = "tadbaorg";
   private static final String WS = "ws1";
   private static final String WS_RENAMED = "ws1renamed";

   @Autowired
   ScheduleManager scheduleManager;

   @BeforeEach
   void setUp() {
      SRPrincipal principal = new SRPrincipal(new IdentityID("admin", ORG), new IdentityID[0],
                                              new String[0], ORG,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      ThreadContext.setContextPrincipal(principal);
   }

   @AfterEach
   void tearDown() {
      scheduleManager.getOrgTaskMap(ORG).clear();
      ThreadContext.setContextPrincipal(null);
   }

   // ---------------------------------------------------------------------------------------
   // column rename of the primary table: every batch action's query parameter follows
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest(name = "{0} batch action(s)")
   @ValueSource(ints = { 1, 2, 3 })
   void columnRenameFollowedByEveryBatchAction(int count) {
      String key = store(task("col", batchActions(count)));

      renameColumn(key);

      assertEquals(Collections.nCopies(count, "col2"), parameters(key));
   }

   @Test
   void columnRenameFollowedByBatchActionAfterViewsheetAction() {
      String key = store(task("colvs", viewsheetThenBatch()));

      renameColumn(key);

      assertEquals(List.of("col2"), parameters(key));
   }

   // ---------------------------------------------------------------------------------------
   // worksheet rename: every batch action's query entry follows and the task is saved
   // ---------------------------------------------------------------------------------------

   @ParameterizedTest(name = "{0} batch action(s)")
   @ValueSource(ints = { 1, 2, 3 })
   void worksheetRenameFollowedByEveryBatchAction(int count) {
      String key = store(task("ws", batchActions(count)));

      renameWorksheet(key, false);

      assertEquals(Collections.nCopies(count, WS_RENAMED), queryPaths(key));
   }

   // the primary table is a bound table, so the rename info carries its old and new path
   @Test
   void worksheetRenameWithPrimaryTablePathFollowedByEveryBatchAction() {
      String key = store(task("wsp", batchActions(2)));

      renameWorksheet(key, true);

      assertEquals(List.of(WS_RENAMED, WS_RENAMED), queryPaths(key));
   }

   @Test
   void worksheetRenameFollowedByBatchActionAfterViewsheetAction() {
      String key = store(task("wsvs", viewsheetThenBatch()));

      renameWorksheet(key, false);

      assertEquals(List.of(WS_RENAMED), queryPaths(key));
   }

   // ---------------------------------------------------------------------------------------
   // guard: a batch action on another worksheet is left alone
   // ---------------------------------------------------------------------------------------

   @Test
   void batchActionOnOtherWorksheetIsNotRenamed() {
      BatchAction other = batch();
      other.setQueryEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.WORKSHEET, "other", null, ORG));
      String key = store(task("other", List.of(batch(), other)));

      renameColumn(key);
      renameWorksheet(key, false);

      assertEquals(List.of("col2", "col"), parameters(key));
      assertEquals(List.of(WS_RENAMED, "other"), queryPaths(key));
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   private static String wsId(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            ORG).toIdentifier();
   }

   // as DependencyTransformer.addAssemblyRenameInfo builds it for a renamed column
   private static void renameColumn(String key) {
      RenameInfo info = new RenameInfo("col", "col2", RenameInfo.ASSET | RenameInfo.COLUMN,
                                       wsId(WS), "T", null);
      info.setPrimaryTable(true);
      transform(key, info);
   }

   // as AbstractAssetEngine.renameSheet builds it for a renamed worksheet
   private static void renameWorksheet(String key, boolean tablePath) {
      RenameInfo info = new RenameInfo(wsId(WS), wsId(WS_RENAMED),
                                       RenameInfo.ASSET | RenameInfo.SOURCE);

      if(tablePath) {
         info.setOldPath(WS + "/T");
         info.setNewPath(WS_RENAMED + "/T");
      }

      transform(key, info);
   }

   private static void transform(String key, RenameInfo info) {
      AssetEntry taskEntry = AssetEntry.createAssetEntry(key);
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.addRenameInfo(taskEntry, info);
      DependencyTransformer.transformAsset(taskEntry, dinfo);
   }

   private static List<ScheduleAction> batchActions(int count) {
      List<ScheduleAction> actions = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         actions.add(batch());
      }

      return actions;
   }

   private static List<ScheduleAction> viewsheetThenBatch() {
      ViewsheetAction vs = new ViewsheetAction();
      vs.setViewsheet(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                     "vs1", null, ORG).toIdentifier());
      vs.setEmails("a@b.com");
      return List.of(vs, batch());
   }

   private static BatchAction batch() {
      BatchAction action = new BatchAction();
      action.setTaskId(new IdentityID("admin", ORG).convertToKey() + ":target");
      action.setQueryEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                          AssetEntry.Type.WORKSHEET, WS, null, ORG));
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("pK", "col");
      action.setQueryParameters(params);
      return action;
   }

   private static int taskCount = 0;

   private static ScheduleTask task(String name, List<ScheduleAction> actions) {
      ScheduleTask task = new ScheduleTask(name + "_" + (++taskCount));
      task.setOwner(new IdentityID("admin", ORG));
      task.addCondition(TimeCondition.at(1, 0, 0));
      actions.forEach(task::addAction);
      return task;
   }

   private String store(ScheduleTask task) {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                                  "/" + task.getTaskId(), null, ORG).toIdentifier();
      scheduleManager.getOrgTaskMap(ORG).put(key, task);
      return key;
   }

   private List<BatchAction> reloadBatchActions(String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(ORG);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, "the task must load");
      List<BatchAction> result = new ArrayList<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         if(task.getAction(i) instanceof BatchAction batch) {
            result.add(batch);
         }
      }

      return result;
   }

   private List<Object> parameters(String key) {
      List<Object> result = new ArrayList<>();
      reloadBatchActions(key).forEach(a -> result.add(a.getQueryParameters().get("pK")));
      return result;
   }

   private List<String> queryPaths(String key) {
      List<String> result = new ArrayList<>();
      reloadBatchActions(key).forEach(a -> result.add(a.getQueryEntry().getPath()));
      return result;
   }

   @Configuration
   static class Beans {
      // the constructor is package private; the transformer reads the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }
   }
}
