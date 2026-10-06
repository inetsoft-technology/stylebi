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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.util.*;
import inetsoft.util.dep.XAsset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A schedule task must follow a rename of the asset it references, through the real rename
 * pipeline: the task is saved with {@link ScheduleTaskMap#put}, renamed with
 * {@link DependencyTransformer#transformAsset} or end to end through the real
 * {@link LocalDependencyHandler} lookup and {@link DependencyTransformer#renameDep}, and
 * reloaded from storage.
 * <ul>
 *    <li>Bug #77851: an apostrophe in the old viewsheet id (name, folder, owner) or in the owner
 *    of a backed-up asset was put into an XPath string literal, the XPath failed silently and
 *    the rename was not followed.</li>
 *    <li>Bug #77847: a backup asset path with control characters, stored encoded, must be
 *    matched and rewritten (storage and import asset-file branches), and tasks stored by the old
 *    writer must still be matched.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  TaskAssetDependencyTransformerRenameTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // over 10 s: the Spring context with the real dependency storage, many rows
class TaskAssetDependencyTransformerRenameTest {
   private static final String ORG = "tadrorg";

   @Autowired
   ScheduleManager scheduleManager;

   @TempDir
   Path tempDir;

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
   // Bug #77851: apostrophes in the old id or owner, plus ordinary controls
   // ---------------------------------------------------------------------------------------

   static Stream<Arguments> renames() {
      String[][] cases = {
         // controls, followed before the fix and must stay so
         { "vs1", "vs2" },
         { "F1/vs1", "F1/vs2" },
         { "vs1", "F1/vs1" },
         { "F1/vs1", "F2/vs1" },
         { "F1/my vs", "F1/my vs2" },
         { "F1/a&b", "F1/a&b2" },
         { "F1/a\"b", "F1/a\"b2" },
         { "F1/é中", "F1/é中2" },
         { "U:plain|F1/vs1", "U:plain|F1/vs2" },
         { "U:tom&jerry|F1/vs1", "U:tom&jerry|F1/vs2" },
         // apostrophes
         { "a'b", "a'b2" },
         { "F1/a'b", "F1/a'b2" },
         { "F'1/vs1", "F'1/vs2" },
         { "it's", "its" },
         { "F1/vs1", "F'1/vs1" },
         { "U:o'brien|F1/vs1", "U:o'brien|F1/vs2" },
      };
      List<Arguments> args = new ArrayList<>();

      for(Mode mode : Mode.values()) {
         for(String[] c : cases) {
            args.add(Arguments.of(mode, c[0], c[1]));
         }
      }

      return args.stream();
   }

   @ParameterizedTest(name = "{0}: {1} -> {2}")
   @MethodSource("renames")
   void viewsheetActionFollowsRename(Mode mode, String oldCase, String newCase) throws Exception {
      String oldId = vsId(oldCase);
      String newId = vsId(newCase);
      ScheduleTask task = task("vs", vsAction(oldId));
      String key = store(task);

      rename(mode, key, task, oldId, newId);

      assertEquals(newId, rawActionAttr(key, "viewsheet"));
      ScheduleTask loaded = reload(key);
      assertNotNull(loaded);
      assertEquals(newId, ((ViewsheetAction) loaded.getAction(0)).getViewsheet());
   }

   @ParameterizedTest(name = "{0}: {1} -> {2}")
   @MethodSource("renames")
   void backupActionFollowsRename(Mode mode, String oldCase, String newCase) throws Exception {
      String oldId = vsId(oldCase);
      String newId = vsId(newCase);
      ScheduleTask task = task("bk", backup(asset("VIEWSHEET", path(oldCase), user(oldCase))));
      String key = store(task);

      rename(mode, key, task, oldId, newId);

      // the rewritten attributes are exactly what the writer stores for the new asset
      Element xasset = rawXAssets(key).get(0);
      assertEquals(Tool.byteEncode2(path(newCase)), xasset.getAttribute("path"));
      assertEquals(user(newCase) == null ? "" : user(newCase).convertToKey(),
                   xasset.getAttribute("user"));
      assertEquals(path(newCase), reloadedAssetPath(key, 0));
   }

   @Test
   void ordinaryRenameWritesTheSameBytesAsBefore() throws Exception {
      ScheduleTask task = task("gold", backup(asset("VIEWSHEET", "F1/a'b", null)));
      String key = store(task);
      assertEquals("F1~_2f_~a~_27_~b", rawXAssets(key).get(0).getAttribute("path"));

      rename(Mode.TRANSFORM, key, task, vsId("F1/a'b"), vsId("F2/c&d e"));

      assertEquals("F2~_2f_~c~_26_~d e", rawXAssets(key).get(0).getAttribute("path"));
   }

   // ---------------------------------------------------------------------------------------
   // Guard rows: the Java-side match must not rewrite references to other assets
   // ---------------------------------------------------------------------------------------

   @Test
   void userRenameDoesNotTouchGlobalAssetAndViceVersa() throws Exception {
      IdentityID bob = new IdentityID("bob", ORG);
      ScheduleTask global = task("g", backup(asset("VIEWSHEET", "F1/u1", null)));
      String gkey = store(global);
      ScheduleTask user = task("u", backup(asset("VIEWSHEET", "F1/u1", bob)));
      String ukey = store(user);

      rename(Mode.TRANSFORM, gkey, global, vsId("U:bob|F1/u1"), vsId("U:bob|F1/u2"));
      rename(Mode.TRANSFORM, ukey, user, vsId("F1/u1"), vsId("F1/u2"));

      assertEquals("F1~_2f_~u1", rawXAssets(gkey).get(0).getAttribute("path"));
      assertEquals("", rawXAssets(gkey).get(0).getAttribute("user"));
      assertEquals("F1~_2f_~u1", rawXAssets(ukey).get(0).getAttribute("path"));
      assertEquals(bob.convertToKey(), rawXAssets(ukey).get(0).getAttribute("user"));
   }

   @Test
   void onlyTheMatchingScopeIsRewrittenWhenOneTaskHasBoth() throws Exception {
      IdentityID bob = new IdentityID("bob", ORG);
      ScheduleTask task = task("two", backup(asset("VIEWSHEET", "F1/s1", null),
                                             asset("VIEWSHEET", "F1/s1", bob)));
      String key = store(task);

      rename(Mode.TRANSFORM, key, task, vsId("F1/s1"), vsId("F1/s2"));

      List<Element> assets = rawXAssets(key);
      assertEquals("F1~_2f_~s2", assets.get(0).getAttribute("path"));
      assertEquals("", assets.get(0).getAttribute("user"));
      assertEquals("F1~_2f_~s1", assets.get(1).getAttribute("path"));
      assertEquals(bob.convertToKey(), assets.get(1).getAttribute("user"));
   }

   @Test
   void otherAssetTypeWithTheSamePathIsNotRewritten() throws Exception {
      ScheduleTask task = task("ws", backup(asset("WORKSHEET", "F1/t1", null)));
      String key = store(task);

      rename(Mode.TRANSFORM, key, task, vsId("F1/t1"), vsId("F1/t2"));

      assertEquals("F1~_2f_~t1", rawXAssets(key).get(0).getAttribute("path"));
   }

   @Test
   void viewsheetIdIsComparedRawWithoutDecoding() throws Exception {
      String storedId = vsId("x~_27_~y");
      ScheduleTask task = task("raw", vsAction(storedId));
      String key = store(task);

      rename(Mode.TRANSFORM, key, task, vsId("x'y"), vsId("z"));

      assertEquals(storedId, rawActionAttr(key, "viewsheet"));
   }

   @Test
   void missingUserAttributeIsFollowedByAGlobalRename() throws Exception {
      ScheduleTask task = task("nouser", backup(asset("VIEWSHEET", "F1/m1", null)));
      String key = store(task);
      patchXAsset(key, e -> e.removeAttribute("user"));

      rename(Mode.TRANSFORM, key, task, vsId("F1/m1"), vsId("F1/m2"));

      assertEquals("F1/m2", reloadedAssetPath(key, 0));
   }

   @Test
   void missingUserAttributeIsNotFollowedByAUserRename() throws Exception {
      ScheduleTask task = task("nouser2", backup(asset("VIEWSHEET", "F1/m1", null)));
      String key = store(task);
      patchXAsset(key, e -> e.removeAttribute("user"));

      rename(Mode.TRANSFORM, key, task, vsId("U:bob|F1/m1"), vsId("U:bob|F1/m2"));

      assertEquals("F1~_2f_~m1", rawXAssets(key).get(0).getAttribute("path"));
   }

   @ParameterizedTest
   @org.junit.jupiter.params.provider.ValueSource(strings = { "null", "~;~" })
   void globalUserMarkersAreFollowedByAGlobalRename(String marker) throws Exception {
      ScheduleTask task = task("marker", backup(asset("VIEWSHEET", "F1/m1", null)));
      String key = store(task);
      patchXAsset(key, e -> e.setAttribute("user", marker));
      // the reader treats the marker as a global asset
      ScheduleTask loaded = reload(key);
      assertNull(((IndividualAssetBackupAction) loaded.getAction(0)).getAssets().get(0).getUser());

      rename(Mode.TRANSFORM, key, task, vsId("F1/m1"), vsId("F1/m2"));

      assertEquals("F1/m2", reloadedAssetPath(key, 0));
   }

   // ---------------------------------------------------------------------------------------
   // Bug #77847: control characters in backup asset paths, legacy stored forms
   // ---------------------------------------------------------------------------------------

   static Stream<Arguments> controlCharRenames() {
      return Stream.of(
         Arguments.of("my\u001Fvs", "renamed"),
         Arguments.of("F1/ws\u0001x", "F1/ws2"),
         Arguments.of("myvs", "new\u001Fname"),
         Arguments.of("tab\tvs", "tab\tvs2"));
   }

   @ParameterizedTest(name = "storage: {0} -> {1}")
   @MethodSource("controlCharRenames")
   void controlCharRenameIsFollowedInStorage(String oldPath, String newPath) throws Exception {
      ScheduleTask task = task("c0", backup(asset("VIEWSHEET", oldPath, null)));
      String key = store(task);
      assertEquals(oldPath, reloadedAssetPath(key, 0), "the task must load before the rename");

      rename(Mode.TRANSFORM, key, task, vsId(oldPath), vsId(newPath));

      assertEquals(newPath, reloadedAssetPath(key, 0));
   }

   @ParameterizedTest(name = "asset file: {0} -> {1}")
   @MethodSource("controlCharRenames")
   void controlCharRenameIsFollowedInAssetFile(String oldPath, String newPath) throws Exception {
      ScheduleTask task = task("c0f", backup(asset("VIEWSHEET", oldPath, null)));
      File file = writeAssetFile(task);

      transformFile(task, file, vsId(oldPath), vsId(newPath));

      assertTrue(file.length() > 0, "the import file must not be truncated");
      assertEquals(newPath, fileAssetPath(file));
   }

   @Test
   void legacyRawDeletePathIsStillFollowed() throws Exception {
      ScheduleTask task = task("del", backup(asset("VIEWSHEET", "delvs", null)));
      String key = store(task);
      // what the old writer stored for "del\u007Fvs": DEL is legal XML and was written raw
      patchXAsset(key, e -> e.setAttribute("path", "del\u007Fvs"));
      assertEquals("del\u007Fvs", reloadedAssetPath(key, 0));

      rename(Mode.TRANSFORM, key, task, vsId("del\u007Fvs"), vsId("renamed"));

      assertEquals("renamed", rawXAssets(key).get(0).getAttribute("path"));
   }

   @Test
   void legacyRawDeletePathIsStillFollowedInAssetFile() throws Exception {
      ScheduleTask task = task("delf", backup(asset("VIEWSHEET", "delvs", null)));
      File file = writeAssetFile(task);
      String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8)
         .replace("path=\"delvs\"", "path=\"del\u007Fvs\"");
      Files.writeString(file.toPath(), xml, StandardCharsets.UTF_8);

      transformFile(task, file, vsId("del\u007Fvs"), vsId("renamed"));

      assertEquals("renamed", fileAssetPath(file));
   }

   @Test
   void legacyEncodedFolderPathIsFollowedInAssetFile() throws Exception {
      ScheduleTask task = task("legf", backup(asset("VIEWSHEET", "f1/myvs", null)));
      File file = writeAssetFile(task);
      assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8)
                    .contains("path=\"f1~_2f_~myvs\""));

      transformFile(task, file, vsId("f1/myvs"), vsId("f2/renamed"));

      assertTrue(Files.readString(file.toPath(), StandardCharsets.UTF_8)
                    .contains("path=\"f2~_2f_~renamed\""));
      assertEquals("f2/renamed", fileAssetPath(file));
   }

   @Test
   void otherEncodingOfTheRenamedPathIsFollowed() throws Exception {
      ScheduleTask task = task("arm3", backup(asset("VIEWSHEET", "xy", null)));
      String key = store(task);
      // no writer produces this form, but the reader resolves it to the asset "xAy"
      patchXAsset(key, e -> e.setAttribute("path", "x~_41_~y"));
      assertEquals("xAy", reloadedAssetPath(key, 0));

      rename(Mode.TRANSFORM, key, task, vsId("xAy"), vsId("z"));

      assertEquals("z", reloadedAssetPath(key, 0));
   }

   // ---------------------------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------------------------

   enum Mode { TRANSFORM, HANDLER }

   private static IdentityID user(String c) {
      return c.startsWith("U:") ? new IdentityID(c.substring(2, c.indexOf('|')), ORG) : null;
   }

   private static String path(String c) {
      return c.startsWith("U:") ? c.substring(c.indexOf('|') + 1) : c;
   }

   private static String vsId(String c) {
      IdentityID user = user(c);
      int scope = user == null ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      return new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path(c), user, ORG).toIdentifier();
   }

   private void rename(Mode mode, String taskKey, ScheduleTask task, String oldId, String newId) {
      if(mode == Mode.HANDLER) {
         DependencyHandler handler = new LocalDependencyHandler((XRepository) null);
         handler.updateTaskDependencies(task, true);

         try {
            RenameDependencyInfo dinfo = handler.getRenameDependencyInfo(
               AssetEntry.createAssetEntry(oldId), AssetEntry.createAssetEntry(newId));
            assertNotNull(dinfo, "the dependency lookup must find the task");
            DependencyTransformer.renameDep(dinfo);
         }
         finally {
            handler.updateTaskDependencies(task, false);
         }

         return;
      }

      AssetEntry taskEntry = AssetEntry.createAssetEntry(taskKey);
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      dinfo.addRenameInfo(taskEntry, new RenameInfo(oldId, newId, RenameInfo.VIEWSHEET));
      DependencyTransformer.transformAsset(taskEntry, dinfo);
   }

   private void transformFile(ScheduleTask task, File file, String oldId, String newId) {
      AssetEntry taskEntry = AssetEntry.createAssetEntry(taskKey(task));
      TaskAssetDependencyTransformer transformer = new TaskAssetDependencyTransformer(taskEntry);
      transformer.setAssetFile(file);
      transformer.process(List.of(new RenameInfo(oldId, newId, RenameInfo.VIEWSHEET)));
   }

   private File writeAssetFile(ScheduleTask task) throws IOException {
      File file = Files.createTempFile(tempDir, "task", ".xml").toFile();

      try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8)))
      {
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
         task.writeXML(writer);
      }

      return file;
   }

   private static String fileAssetPath(File file) throws Exception {
      try(InputStream in = new FileInputStream(file)) {
         Document doc = Tool.parseXML(in, "UTF-8", false, false);
         ScheduleTask task = new ScheduleTask();
         task.parseXML(doc.getDocumentElement());
         return ((IndividualAssetBackupAction) task.getAction(0)).getAssets().get(0).getPath();
      }
   }

   private static ViewsheetAction vsAction(String id) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(id);
      action.setEmails("a@b.com");
      return action;
   }

   private static XAsset asset(String type, String path, IdentityID user) {
      return SUtil.getXAsset(type, path, user);
   }

   private static IndividualAssetBackupAction backup(XAsset... assets) {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setAssets(Arrays.asList(assets));
      action.setPaths("backup/x.zip");
      return action;
   }

   private static int taskCount = 0;

   private static ScheduleTask task(String name, ScheduleAction action) {
      ScheduleTask task = new ScheduleTask(name + "_" + (++taskCount));
      task.setOwner(new IdentityID("admin", ORG));
      task.addCondition(TimeCondition.at(1, 0, 0));
      task.addAction(action);
      return task;
   }

   private static String taskKey(ScheduleTask task) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + task.getTaskId(), null, ORG).toIdentifier();
   }

   private String store(ScheduleTask task) {
      String key = taskKey(task);
      scheduleManager.getOrgTaskMap(ORG).put(key, task);
      return key;
   }

   private ScheduleTask reload(String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(ORG);
      map.clearCache();
      return map.get(key);
   }

   private String reloadedAssetPath(String key, int index) {
      ScheduleTask loaded = reload(key);
      assertNotNull(loaded, "the task must load");
      return ((IndividualAssetBackupAction) loaded.getAction(0)).getAssets().get(index).getPath();
   }

   private static Document rawDocument(String key) throws Exception {
      return IndexedStorage.getIndexedStorage().getDocument(key, ORG);
   }

   private static String rawActionAttr(String key, String attr) throws Exception {
      Element action = (Element)
         rawDocument(key).getDocumentElement().getElementsByTagName("Action").item(0);
      return action.getAttribute(attr);
   }

   private static List<Element> rawXAssets(String key) throws Exception {
      return xassets(rawDocument(key));
   }

   private static List<Element> xassets(Document doc) {
      org.w3c.dom.NodeList nodes = doc.getDocumentElement().getElementsByTagName("XAsset");
      List<Element> result = new ArrayList<>();

      for(int i = 0; i < nodes.getLength(); i++) {
         result.add((Element) nodes.item(i));
      }

      return result;
   }

   private void patchXAsset(String key, java.util.function.Consumer<Element> patch)
      throws Exception
   {
      IndexedStorage storage = IndexedStorage.getIndexedStorage();
      Document doc = storage.getDocument(key, ORG);
      patch.accept(xassets(doc).get(0));
      storage.putDocument(key, doc, ScheduleTask.class.getName(), ORG);
      scheduleManager.getOrgTaskMap(ORG).clearCache();
   }

   @Configuration
   static class Beans {
      // the constructor is package private; the real service backs LocalDependencyHandler
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
