package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.admin.ai.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
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

/** THROWAWAY tester probe for #78583 item 4. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, Probe78583RenameTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Probe78583RenameTest {
   @Configuration
   static class Config {
      @Bean
      RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   @Autowired private BlobStorageManager blobStorageManager;

   private LibManager lib;
   private ScriptLibraryChangePlanService planService;
   private ScriptLibraryChangesetApplyService service;
   private final Principal user = mock(Principal.class);

   static final String FACT = "function factP(n) {\n  // factP recursion\n  return n <= 1 ? 1 : n * factP(n - 1);\n}";
   static final String CALC = "function calcP(v) { return calcPHelper(v) + 1; }";

   @BeforeEach
   void setUp() throws Exception {
      lib = spy(new LibManager(new NoopLibrarySecurity(), "p78583-orgB", mock(Cluster.class),
                               blobStorageManager) { });
      LibManagerProvider provider = mock(LibManagerProvider.class);
      when(provider.getManager(any(Principal.class))).thenReturn(lib);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCRIPT), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      AdminBackupService backupService = mock(AdminBackupService.class);
      when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
      ScriptLibraryService sls = new ScriptLibraryService(provider, securityEngine);
      planService = new ScriptLibraryChangePlanService(sls);
      service = new ScriptLibraryChangesetApplyService(planService, sls, provider, backupService);
      lib.setScript("factP", FACT);
      lib.setScript("calcP", CALC);
      lib.setScript("calcPHelper", "function calcPHelper(v) { return v; }");
      lib.setScript("keepP", "function keepP() { return 1; }");
      lib.save();
   }

   @AfterEach
   void tearDown() throws Exception {
      for(String n : List.of("factP", "factQ", "calcP", "calcPHelper", "zzz", "zzzHelper", "keepP")) {
         lib.removeScript(n);
      }

      lib.save();
   }

   private static ScriptLibraryChangeRequest rename(String o, String n) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(ScriptLibraryChangeRequest.VERB_RENAME);
      c.setName(o);
      c.setNewName(n);
      return c;
   }

   private ScriptLibraryApplyResult apply(ScriptLibraryChangeRequest... cs) throws Exception {
      ScriptLibraryChangePlanRequest pr = new ScriptLibraryChangePlanRequest();
      pr.setTask("probe");
      pr.setChanges(List.of(cs));
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("probe");
      req.setChanges(List.of(cs));
      req.setPlanHash(planService.resolve(pr, user).planHash());
      req.setReviewOutcome("ok");
      req.setAcknowledgeIrreversibleDelete(true);
      return service.apply(req, user);
   }

   @Test
   void recursiveFunctionRenamedAndPreviewMatchesApply() throws Exception {
      ScriptLibraryChangePlanRequest pr = new ScriptLibraryChangePlanRequest();
      pr.setTask("probe");
      pr.setChanges(List.of(rename("factP", "factQ")));
      PlanChange pc = planService.resolve(pr, user).changes().get(0);
      ScriptLibraryApplyResult r = apply(rename("factP", "factQ"));
      System.out.println("PROBE results=" + r.results() + " rf=" + r.rollbackFailures());
      System.out.println("PROBE recursive text=" + lib.getScript("factQ"));
      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, r.status());
      assertEquals(FACT.replace("factP", "factQ"), lib.getScript("factQ").strip());
      System.out.println("PROBE preview=" + pc.proposedValue() + " applyAfter=" + r.results().get(0).after());
      assertEquals(pc.proposedValue(), r.results().get(0).after());
   }

   @Test
   void undoRestoresExactText() throws Exception {
      ScriptLibraryChangeRequest del = new ScriptLibraryChangeRequest();
      del.setVerb(ScriptLibraryChangeRequest.VERB_DELETE);
      del.setName("keepP");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());
         doAnswer(inv -> null).when(lib).removeScript("keepP");
         ScriptLibraryApplyResult r = apply(rename("factP", "factQ"), del);
         System.out.println("PROBE undo status=" + r.status() + " failures=" + r.rollbackFailures());
         assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, r.status());
      }

      assertNull(lib.getScript("factQ"));
      assertEquals(FACT, lib.getScript("factP").strip());
   }

   @Test
   void substringOfAnotherFunctionName() throws Exception {
      ScriptLibraryApplyResult r = apply(rename("calcP", "zzz"));
      System.out.println("PROBE substring status=" + r.status() + " text=" + lib.getScript("zzz"));
   }
}
