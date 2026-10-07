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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The asset dependency and rename pipeline under inetsoft.uql.asset.sync, the delete
 * dependency checkers under inetsoft.uql.asset.delete, and the logical model renames that
 * start the pipeline, are refused to a sheet script on every route. Java callers are
 * unaffected. (Bug #77852) The two-argument logical model rename delegates to the guarded
 * rename instead of calling itself. (Bug #77920)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ScriptRenamePipelineAccessTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String SYNC = "inetsoft.uql.asset.sync.";
   private static final String DELETE = "inetsoft.uql.asset.delete.";
   private static final String UNKNOWN = "Unknown identifier";
   private static final String CTOR_REFUSED = "Message not supported";
   private static final String RENAME_REFUSED = "A script may not rename a logical model";

   private ViewsheetScope viewsheetScope;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   @Test
   void dependencyTransformerStaticRefused() throws Exception {
      assertRefused(run("'' + Java.type('" + SYNC + "DependencyTransformer')" +
                           ".getTabularAssetId('q77852')"));
   }

   @Test
   void dependencyTransformerStaticLegacyRouteRefused() throws Exception {
      assertRefused(run("'' + " + SYNC + "DependencyTransformer.getTabularAssetId('q77852')"));
   }

   @Test
   void dataDependencyTransformerRefused() throws Exception {
      assertNotAllowed(construct("DataDependencyTransformer", "null"));
   }

   @Test
   void assetDependencyTransformerRefused() throws Exception {
      assertNotAllowed(construct("AssetDependencyTransformer", "null"));
   }

   /** The deny on DependencyTransformer also covers each of its other public subclasses. */
   @Test
   void otherDependencyTransformerSubclassesRefused() {
      String[] types = {
         "AssetCubeDependencyTransformer", "AssetEmbedDependencyTransformer",
         "AssetHyperlinkDependencyTransformer", "AssetLMDependencyTransformer",
         "AssetPhyTableDependencyTransformer", "AssetSQLTableDependencyTransformer",
         "AssetScriptDependencyTransformer", "AssetTabularDependencyTransformer",
         "AssetWSDependencyTransformer", "DashboardAssetDependencyTransformer",
         "LogicalModelPartitionDependencyTransformer", "QueryDatasourceDependencyTransformer",
         "TaskAssetDependencyTransformer"
      };

      assertAll(Arrays.stream(types)
                   .map(type -> (Executable) () -> assertNotAllowed(construct(type, "null"))));
   }

   @Test
   void renameTransformTaskRefused() throws Exception {
      assertNotAllowed(construct("RenameTransformTask", "null"));
   }

   @Test
   void renameTransformTaskRenameRefused() throws Exception {
      assertNotAllowed(construct("RenameTransformTask$Rename", "null"));
   }

   @Test
   void renameTransformTaskRemoveRefused() throws Exception {
      assertNotAllowed(construct("RenameTransformTask$Remove", "null"));
   }

   @Test
   void loadDependencyStorageTaskRefused() throws Exception {
      assertNotAllowed(construct("LoadDependencyStorageTask", "'x'"));
   }

   @Test
   void renameTransformQueueRefused() throws Exception {
      assertNotAllowed(construct("RenameTransformQueue", ""));
   }

   @Test
   void updateDependencyHandlerStaticRefused() throws Exception {
      assertRefused(run("'' + Java.type('" + SYNC + "UpdateDependencyHandler')" +
                           ".getChildNodes(null, '/x').getLength()"));
   }

   @Test
   void updateDependencyHandlerStaticLegacyRouteRefused() throws Exception {
      assertRefused(run("'' + " + SYNC + "UpdateDependencyHandler" +
                           ".getChildNodes(null, '/x').getLength()"));
   }

   @Test
   void assetDependencyCheckerRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + DELETE + "AssetDependencyChecker'))(null);" +
                              " 'constructed'"));
   }

   @Test
   void viewsheetDependencyCheckerRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + DELETE + "ViewsheetDependencyChecker'))(null);" +
                              " 'constructed'"));
   }

   @Test
   void deleteDependencyHandlerStaticRefused() throws Exception {
      assertRefused(run("'' + Java.type('" + DELETE + "DeleteDependencyHandler')" +
                           ".checkDependency(null)"));
   }

   @Test
   void dataModelRenameRefused() throws Exception {
      Object result = run("new (Java.type('inetsoft.uql.erm.XDataModel'))('ds77852')" +
                             ".renameLogicalModel('none77852', 'none77852b', null); 'renamed'");

      assertErrorContains(result, RENAME_REFUSED);
   }

   /** Refused by the guard, not a StackOverflowError (Bug #77920). */
   @Test
   void dataModelTwoArgRenameRefused() throws Exception {
      Object result = run("new (Java.type('inetsoft.uql.erm.XDataModel'))('ds77852')" +
                             ".renameLogicalModel('none77852', 'none77852b'); 'renamed'");

      assertErrorContains(result, RENAME_REFUSED);
   }

   @Test
   void logicalModelRenameRefused() throws Exception {
      Object result = run("new (Java.type('inetsoft.uql.erm.XLogicalModel'))('lm77852')" +
                             ".renameLogicalModel('none77852', null); 'renamed'");

      assertErrorContains(result, RENAME_REFUSED);
   }

   /** The rest of the data model and the script-facing uql types are still usable. */
   @Test
   void scriptFacingTypesStillAllowed() throws Exception {
      assertEquals("1", run("var v = new (Java.type('inetsoft.uql.VariableTable'))();" +
                               " v.put('p77852', 1); '' + v.get('p77852')"));
      assertEquals("ds77852", run("'' + new (Java.type('inetsoft.uql.erm.XDataModel'))" +
                                     "('ds77852').getDataSource()"));
   }

   /** The sync value classes are not denied. */
   @Test
   void syncValueClassStillAllowed() throws Exception {
      assertEquals("b77852", run("'' + new (Java.type('" + SYNC + "RenameInfo'))" +
                                    "('a77852', 'b77852', 1).getNewName()"));
   }

   /** Java callers still reach the logical model renames. */
   @Test
   void logicalModelRenamesUsableFromJava() {
      assertDoesNotThrow(() -> new XDataModel("ds77852")
         .renameLogicalModel("none77852", "none77852b", null));
      assertDoesNotThrow(() -> new XDataModel("ds77852")
         .renameLogicalModel("none77852", "none77852b"));
      // the guard lets a Java caller through; whatever the body then does with the null
      // model, it is not the guard's refusal
      Throwable thrown = null;

      try {
         new XLogicalModel("lm77852").renameLogicalModel("none77852", null);
      }
      catch(Throwable ex) {
         thrown = ex;
      }

      assertFalse(thrown instanceof SecurityException, String.valueOf(thrown));
   }

   // a member of a denied type
   private static void assertRefused(Object result) {
      assertErrorContains(result, UNKNOWN);
   }

   // a constructor of a denied type
   private static void assertNotAllowed(Object result) {
      assertErrorContains(result, CTOR_REFUSED);
   }

   private static void assertErrorContains(Object result, String text) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(text), String.valueOf(result));
   }

   private Object construct(String type, String args) throws Exception {
      return run("new (Java.type('" + SYNC + type + "'))(" + args + "); 'constructed'");
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
