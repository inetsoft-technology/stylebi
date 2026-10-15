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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.admin.ai.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78583: an admin-chat Script Library rename must also rewrite the function's own
 * declaration, or the renamed entry still installs the old global name while its callers are
 * rewritten to the new one. Runs on a real {@link LibManager} over real blob storage.
 *
 * <p>The LibManager gets a no-op Cluster. The test MockCluster delivers LibManager's own change
 * message synchronously, and the resulting {@code reloadLibrary()} undoes a rename before it is
 * saved; in production the local delivery is asynchronous.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ScriptLibraryRenameDeclarationTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptLibraryRenameDeclarationTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;
   @Autowired private RenameTransformHandler renameTransformHandler;

   private LibManager lib;
   private ScriptLibraryChangePlanService planService;
   private ScriptLibraryChangesetApplyService service;
   private final Principal user = mock(Principal.class);

   @BeforeEach
   void setUp() throws Exception {
      lib = newLibManager();
      LibManagerProvider provider = mock(LibManagerProvider.class);
      when(provider.getManager(any(Principal.class))).thenReturn(lib);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCRIPT), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      AdminBackupService backupService = mock(AdminBackupService.class);
      when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");

      ScriptLibraryService scriptLibraryService =
         new ScriptLibraryService(provider, securityEngine);
      planService = new ScriptLibraryChangePlanService(scriptLibraryService);
      service = new ScriptLibraryChangesetApplyService(planService, scriptLibraryService,
                                                       provider, backupService);

      lib.setScript("oldFn78583", "function oldFn78583(v) { return v * 2; }");
      lib.save();
   }

   @AfterEach
   void tearDown() throws Exception {
      lib.removeScript("oldFn78583");
      lib.removeScript("newFn78583");
      lib.save();
   }

   @Test
   void previewShowsTheRewrittenDeclarationAndThatCallersAreRewritten() throws Exception {
      PlanChange change = planService.resolve(renameRequest(), user).changes().get(0);

      assertNotEquals(digest(change.currentValue()), digest(change.proposedValue()));
      assertEquals(digest(ScriptLibraryChangePlanService.project(
         "newFn78583", null, "function newFn78583(v) { return v * 2; }")),
                   digest(change.proposedValue()));
      assertFalse(change.description().contains("does not rewrite callers"),
                  change.description());
   }

   @Test
   void applyRewritesTheDeclarationAndPersistsIt() throws Exception {
      ScriptLibraryChangeRequest c = renameChange();
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("rename");
      req.setChanges(List.of(c));
      req.setPlanHash(planService.resolve(renameRequest(), user).planHash());

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(lib.getScript("oldFn78583"));
      assertEquals("function newFn78583(v) { return v * 2; }", lib.getScript("newFn78583"));
      assertEquals("function newFn78583(v) { return v * 2; }",
                   newLibManager().getScript("newFn78583").strip(), "reloaded from storage");
      // callers are rewritten by the rename transform task renameScript enqueues
      verify(renameTransformHandler).addTransformTask(any(RenameInfo.class));
   }

   private LibManager newLibManager() {
      // no-op cluster: see the class javadoc
      return new LibManager(new NoopLibrarySecurity(), "r78583-org", mock(Cluster.class),
                            blobStorageManager) { };
   }

   private static ScriptLibraryChangeRequest renameChange() {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_RENAME);
      c.setName("oldFn78583");
      c.setNewName("newFn78583");
      return c;
   }

   private static ScriptLibraryChangePlanRequest renameRequest() {
      ScriptLibraryChangePlanRequest req = new ScriptLibraryChangePlanRequest();
      req.setTask("rename");
      req.setChanges(List.of(renameChange()));
      return req;
   }

   private static String digest(String projection) {
      return projection.substring(projection.indexOf(";textDigest="));
   }
}
