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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The asset dependency and rename pipeline under inetsoft.uql.asset.sync, and the
 * logical model renames that start it, are refused to a sheet script on every route.
 * Java callers are unaffected. (Bug #77852)
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
   private static final String UNKNOWN = "Unknown identifier";
   private static final String RENAME_REFUSED = "A script may not rename a logical model";

   private ViewsheetScope viewsheetScope;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   @Test
   void dependencyTransformerStaticsRefused() throws Exception {
      String call = ".getTabularAssetId('q77852')";
      assertRefused(run("'' + Java.type('" + SYNC + "DependencyTransformer')" + call));
      assertRefused(run("'' + " + SYNC + "DependencyTransformer" + call));
   }

   @Test
   void dependencyTransformerSubclassRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + SYNC + "DataDependencyTransformer'))(null);" +
                              " 'constructed'"));
      assertNotAllowed(run("new (Java.type('" + SYNC + "AssetDependencyTransformer'))(null);" +
                              " 'constructed'"));
   }

   @Test
   void renameTransformTaskRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + SYNC + "RenameTransformTask'))(null);" +
                              " 'constructed'"));
   }

   @Test
   void renameTransformTaskNestedRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + SYNC + "RenameTransformTask$Rename'))(null);" +
                              " 'constructed'"));
      assertNotAllowed(run("new (Java.type('" + SYNC + "RenameTransformTask$Remove'))(null);" +
                              " 'constructed'"));
   }

   @Test
   void loadDependencyStorageTaskRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + SYNC + "LoadDependencyStorageTask'))('x');" +
                              " 'constructed'"));
   }

   @Test
   void renameTransformQueueRefused() throws Exception {
      assertNotAllowed(run("new (Java.type('" + SYNC + "RenameTransformQueue'))();" +
                              " 'constructed'"));
   }

   @Test
   void updateDependencyHandlerStaticsRefused() throws Exception {
      String call = ".getChildNodes(null, '/x').getLength()";
      assertRefused(run("'' + Java.type('" + SYNC + "UpdateDependencyHandler')" + call));
      assertRefused(run("'' + " + SYNC + "UpdateDependencyHandler" + call));
   }

   @Test
   void dataModelRenameRefused() throws Exception {
      Object result = run("new (Java.type('inetsoft.uql.erm.XDataModel'))('ds77852')" +
                             ".renameLogicalModel('none77852', 'none77852b', null); 'renamed'");

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

   /** Java callers still use the pipeline. */
   @Test
   void dependencyTransformerUsableFromJava() {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, "q77852", null);
      assertEquals(entry.toIdentifier(), DependencyTransformer.getTabularAssetId("q77852"));
      assertDoesNotThrow(() -> DependencyTransformer.renameDep(new RenameDependencyInfo()));
   }

   // a member of a denied type
   private static void assertRefused(Object result) {
      assertErrorContains(result, UNKNOWN);
   }

   // a constructor of a denied type
   private static void assertNotAllowed(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
   }

   private static void assertErrorContains(Object result, String text) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      assertTrue(((String) result).startsWith("error: "), String.valueOf(result));
      assertTrue(((String) result).contains(text), String.valueOf(result));
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
