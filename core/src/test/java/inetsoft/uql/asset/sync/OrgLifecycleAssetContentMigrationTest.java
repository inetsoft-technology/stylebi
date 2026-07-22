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
package inetsoft.uql.asset.sync;

/*
 * Mechanism 2: asset content rewrite (BlobIndexedStorage.migrateStorageData() /
 * copyStorageData() -> MigrateXxxTask). Strictly separate from mechanism 1
 * (DependencyStorageService, see OrgLifecycleDependencyMigrationTest) -- no shared base
 * class or fixture helpers, per the plan's Global Constraints.
 *
 * Scenario list and mechanism write-up live in
 * community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "二、机制二：资产内容本体重写" -- not duplicated here.
 *
 * Landed: 2b (copy: viewsheet -> base worksheet via Viewsheet.wentry). Confirms the migrated
 * viewsheet deserializes cleanly under the target org, its wentry is rewritten to the target
 * org, and the worksheet that wentry identifies also deserializes cleanly under the target org
 * -- proving the binding survives migration instead of dangling. This is the one binding type
 * flagged in claude/org-migration-content-rewrite.md as having no parse-time self-heal, so a
 * missed XPath in MigrateViewsheetTask would surface here as a real broken binding, not
 * something silently corrected at load time the way MirrorAssemblyImpl/Viewsheet.ventry would.
 *
 * Landed: 2a (copy: worksheet internal mirror, i.e. a cross-worksheet/"outer" MirrorTableAssembly).
 * Unlike 2b, this scenario MUST assert against the raw XML document (BlobIndexedStorage.getDocument()),
 * not the deserialized object -- MirrorAssemblyImpl.parseXML() unconditionally overwrites the
 * mirror's stored org segment with whatever OrganizationManager.getCurrentOrgID() reports at parse
 * time (handleWSOrgMismatch(), a parse-time self-heal). Reading back through getXMLSerializable()
 * would silently mask a real MigrateWorksheetTask bug -- see claude/org-migration-content-rewrite.md
 * "Parse-time self-heal inconsistency".
 *
 * Landed: 2c (copy: viewsheet embedding a nested/library viewsheet). Production embeds a viewsheet
 * via ComposerObjectService.addEmbeddedViewsheet(): the nested Viewsheet's OWN Viewsheet.ventry
 * field (set via Viewsheet.setEntry()) is what records "which library viewsheet this embedded copy
 * came from" -- ViewsheetVSAssemblyInfo.entry (a separate field, same "mirrored viewsheet entry"
 * Javadoc wording, easy to confuse) is NOT populated by that flow. MigrateViewsheetTask.updateViewsheet()
 * confirms this structurally: it does Tool.getChildNodeByTagName(assembly, "viewsheetEntry") --
 * a DIRECT-CHILD-only search -- which matches ventry's XML (written directly under the nested
 * <assembly> element, Viewsheet.java:4034-4038), not ViewsheetVSAssemblyInfo.entry's XML (nested
 * one level deeper, inside <assemblyInfo>, Viewsheet.java:4046-4048). Also, just like 2a's mirror,
 * ventry parses with a self-heal (Viewsheet.java:4341-4342, unconditionally overwrites the org
 * segment to the current runtime org) -- so this scenario, like 2a, MUST assert against the raw
 * XML document, not the deserialized object.
 *
 * Landed: 10a (copy: Data Source, generic fallback branch). Unlike the explicitly-dispatched
 * types above, `Type.DATA_SOURCE` entries don't match isViewsheet()/isWorksheet()/isLogicModel()/
 * isDomain()/isScheduleTask(), so they fall into BlobIndexedStorage's generic branch
 * (`:649-676`, called synchronously in migrateStorageData()'s main loop, NOT dispatched to the
 * executor like the other branches): read the object, `cloneAssetEntry(norg)` the container key,
 * write it back -- no DOM/field parsing at all. `XDataSourceWrapper` (the class
 * DataSourceRegistry actually stores under `Type.DATA_SOURCE`) has no embedded orgID of its own
 * to rewrite, so this is a pure re-key -- but getting a real `JDBCDataSource` to round-trip in a
 * test environment took real work, see `DataSourceConfig.drivers()`'s Javadoc: with zero plugins
 * installed, `Drivers.getDriverClass()` silently returns null (no exception anywhere in the
 * chain), leaving `XDataSourceWrapper.source` null after ANY read-then-rewrite cycle -- which
 * this pipeline's own migration always does exactly once. A placeholder `DriverService` seeded
 * via reflection is enough to fix it (classloader identity doesn't matter, just needs to exist).
 *
 * Landed: 2f (copy: inline SQL/Query node's own `orgId` element, distinct from `SourceInfo` which
 * has no org concept at all). `SQLBoundTableAssemblyInfo`'s `JDBCQuery` carries its own `orgId`
 * field (`XQuery.java`, written as a plain `<orgId>` CDATA element, read back via
 * `Tool.getChildNodesByTagName(root, "orgId")` -- not an `AssetEntry`/self-heal situation at all,
 * so deserialized-object assertions are safe here, unlike wentry/ventry/mirror).
 *
 * Landed: 2h (identity fields). Two DIFFERENT rewrite mechanisms live under the same matrix-doc
 * row and behave completely differently:
 * - `modifiedBy`/`createdBy` on a plain viewsheet/worksheet root (`MigrateWorksheetTask`/
 *   `MigrateViewsheetTask.processAssemblies()`) are gated on `Tool.equals(ouser, getOldName())`.
 *   `getOldName()`/`getNewName()` are ONLY set by the `(entry, oldUserName, newUserName)`
 *   constructor family (a *user rename*, unrelated to this pipeline) -- the Organization-based
 *   constructor `BlobIndexedStorage.migrateStorageData()` actually calls
 *   (`new MigrateWorksheetTask(entry, oorg, norg)`) never touches those fields, so `getOldName()`
 *   is always `null`. A real `modifiedBy`/`createdBy` string never equals `null`, so **this
 *   rewrite never fires during a pure org copy/rename** -- confirmed by test, pinned as current
 *   (not necessarily intended) behavior.
 * - `defaultBookmarkUser` on a `VIEWSHEET_BOOKMARK`-type entry is handled by a completely
 *   separate class, `MigrateBookmarkTask.processAssemblies()`, which checks
 *   `user.getOrgID().equals(getOldOrganization().getOrganizationID())` instead -- an
 *   organization check, not a user-name check -- so it DOES fire correctly during org
 *   copy/rename. Confirmed by test.
 *
 * Landed: 10b (copy: VPM / Partition, generic fallback branch). `VirtualPrivateModel`/
 * `XPartition` are plain POJOs (no `Config`/`Drivers`/`Plugins` chain needed, unlike
 * `JDBCDataSource`) that also ride the same generic fallback branch as 10a's Data Source. Doubles
 * as scenario 10c's pinned-current-behavior evidence: their `createdBy`/`modifiedBy` fields are
 * confirmed NOT rewritten by the generic fallback branch (it only re-keys the container
 * `AssetEntry`, never parses object internals) -- same *symptom class* as 2h's org-migration
 * modifiedBy/createdBy gap above, but a structurally different cause (no dispatch at all here,
 * vs. a name-based guard that never matches there).
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.ConnectionPoolFactory;
import inetsoft.uql.jdbc.DriverService;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.SQLExecutor;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  OrgLifecycleAssetContentMigrationTest.IndexedStorageConfig.class,
                                  OrgLifecycleAssetContentMigrationTest.RenameTransformHandlerConfig.class,
                                  OrgLifecycleAssetContentMigrationTest.CredentialServiceConfig.class,
                                  OrgLifecycleAssetContentMigrationTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleAssetContentMigrationTest {

   /**
    * {@code MigrateDocumentTask} (the per-asset worker {@code BlobIndexedStorage.migrateStorageData()}
    * dispatches to) does not use the instance a caller holds -- it looks up the running process's
    * {@code IndexedStorage} Spring bean via the static {@code IndexedStorage.getIndexedStorage()}
    * accessor. A {@code BlobIndexedStorage} built with {@code new} and never registered as a bean
    * is invisible to it: every dispatched migration task throws {@code NoSuchBeanDefinitionException},
    * which {@code BlobIndexedStorage.migrateStorageData()} logs and swallows per-task rather than
    * propagating -- so the top-level {@code copyStorageData()} call itself does not throw, it just
    * silently migrates nothing. This bean must be the one under test, not a private local instance.
    */
   @Configuration
   static class IndexedStorageConfig {
      @Bean
      public IndexedStorage indexedStorage(BlobStorageManager blobStorageManager) {
         return new BlobIndexedStorage(blobStorageManager);
      }
   }

   /**
    * {@code RenameTransformHandler} is the entry point of the completely separate asset-rename
    * pipeline ({@code claude/rename-transform.md}) -- {@code AssetDependencyTransformer} and the
    * rest of that family are only reachable downstream of {@code addTransformTask()}, so proving
    * this mock sees zero interactions during org copy/rename is sufficient to prove the whole
    * asset-rename pipeline never runs (scenario 2j: mechanism two must not accidentally call into
    * mechanism three, see claude/org-migration-content-rewrite.md's "Relationship to the
    * Asset-Rename Pipeline" section).
    */
   @Configuration
   static class RenameTransformHandlerConfig {
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }
   }

   /**
    * {@code JDBCDataSource}'s constructor calls {@code initCredential()} ->
    * {@code CredentialService.getInstance()} (a {@code @Lazy @Service} bean, not provided by
    * {@code BaseTestConfiguration}). {@code CredentialService}'s own constructor is
    * package-private (Spring would normally reach it via reflection during component scanning,
    * which {@code BaseTestConfiguration} doesn't do) -- so this bean method does the same via
    * reflection, giving a real, ServiceLoader-backed instance rather than a bare Mockito mock
    * (a mock's unstubbed {@code createCredential()} would return null, and JDBCDataSource
    * doesn't guard against that).
    */
   @Configuration
   static class CredentialServiceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   /**
    * {@code XDataSourceWrapper.parseXML()} looks up the concrete {@code XDataSource} subclass by
    * type name via {@code Config.getConfig()} -> {@code Config.getClass()} -> {@code
    * Drivers.getInstance()} (a small dependency chain: `Config` needs `Plugins`, `Drivers` needs
    * `Plugins` + `ConnectionPoolFactory` -- none of these are {@code @Service}/annotated, so all
    * have to be declared as beans since {@code BaseTestConfiguration} does no component
    * scanning). Mirrors the equivalent minimal bean set {@code IntegrationTestConfiguration}
    * declares (`:242-259`, `:486-487`), without pulling in that class's much heavier bean set
    * (`DataSourceRegistry`/`XRepository`/`viewsheetEngine`/etc., some of which would collide with
    * this test's own {@code IndexedStorageConfig}). {@code ConnectionPoolFactory} is a pure mock
    * there too -- this test never exercises real connection pooling.
    */
   @Configuration
   static class DataSourceConfig {
      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         return mock(ConnectionPoolFactory.class);
      }

      /**
       * {@code Drivers.getDriverClass()} only tries classloaders belonging to actually-installed
       * plugins ({@code Plugins.getPlugins()}) -- with zero plugins installed (this test's fresh
       * {@code Plugins} has none), {@code getDriverServices()} is empty, so the resolution loop
       * never runs and silently returns {@code null} (no exception). {@code Config.getClass()}
       * then leaves {@code dxClass} null, and {@code XDataSourceWrapper.parseXML()}'s {@code if
       * (dxClass != null)} guard skips {@code setSource(dx)} -- leaving the wrapper's
       * {@code source} field null, again with no exception. A subsequent {@code writeXML()} on
       * that hollow wrapper (this pipeline's own migration necessarily does exactly one
       * read-then-rewrite cycle) then writes nothing at all, silently truncating the asset. None
       * of this needs an actual JDBC driver plugin to fix -- any classloader that can see this
       * test's own classpath resolves {@code inetsoft.uql.jdbc.JDBCDataSource} correctly via
       * normal parent delegation, so seeding one placeholder {@code DriverService} via
       * reflection (there is no public API to register one without a real plugin) is enough.
       */
      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory)
         throws Exception
      {
         Drivers drivers = new Drivers(plugins, connectionPoolFactory);
         Field field = Drivers.class.getDeclaredField("driverServices");
         field.setAccessible(true);
         Map<String, List<DriverService>> services = new HashMap<>();
         services.put("test-placeholder", List.of(new DriverService() {
            @Override
            public boolean matches(String driver, String url) {
               return false;
            }

            @Override
            public SQLExecutor getSQLExecutor() {
               return null;
            }

            @Override
            public Set<String> getDrivers() {
               return Set.of();
            }
         }));
         field.set(drivers, services);
         return drivers;
      }
   }

   @Autowired
   private IndexedStorage indexedStorage;

   @Autowired
   private RenameTransformHandler renameTransformHandler;

   private BlobIndexedStorage storage;
   private final List<String> seededOrgIds = new ArrayList<>();

   @BeforeEach
   void setUp() {
      storage = (BlobIndexedStorage) indexedStorage;
   }

   @AfterEach
   void tearDown() {
      for(String orgId : seededOrgIds) {
         try {
            storage.removeStorage(orgId);
         }
         catch(Exception ignore) {
            // best-effort cleanup -- the scenario itself may already have migrated/removed it
         }
      }

      seededOrgIds.clear();
   }

   // ── scenario 2b: copy -- viewsheet -> base worksheet (wentry) survives migration ──

   @Test
   void copy_viewsheetToBaseWorksheet_bindingSurvivesMigration() throws Exception {
      String fromOrgId = "vs_wentry_from";
      String toOrgId = "vs_wentry_to";
      track(fromOrgId);
      track(toOrgId);

      String wsKey = seedWorksheet(fromOrgId, "/ws1");
      String vsKey = seedViewsheetBoundTo(fromOrgId, "/vs1", wsKey);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      String newVsKey = AssetEntry.createAssetEntry(vsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      Viewsheet migratedVs = (Viewsheet) storage.getXMLSerializable(newVsKey, null, toOrgId);
      assertNotNull(migratedVs,
                   "copy must produce a migrated viewsheet under the target org bucket");

      AssetEntry migratedWentry = migratedVs.getBaseEntry();
      assertEquals(toOrgId, migratedWentry.getOrgID(),
                  "the viewsheet's wentry must be rewritten to point at the target org -- wentry " +
                  "has no parse-time self-heal (unlike ventry/MirrorAssemblyImpl), so a missed " +
                  "XPath here would surface as a real dangling binding, not something silently " +
                  "corrected at load time");

      String expectedWsKey = AssetEntry.createAssetEntry(wsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      assertEquals(expectedWsKey, migratedWentry.toIdentifier(true),
                  "the migrated wentry must identify the SAME worksheet that was itself migrated " +
                  "alongside it, not just some other worksheet that happens to live in the " +
                  "target org");

      Worksheet migratedWs =
         (Worksheet) storage.getXMLSerializable(migratedWentry.toIdentifier(true), null, toOrgId);
      assertNotNull(migratedWs,
                   "the worksheet the migrated viewsheet's wentry points at must itself load " +
                   "successfully -- proves the binding isn't dangling, not just that the orgID " +
                   "string happens to match");

      Worksheet sourceWs = (Worksheet) storage.getXMLSerializable(wsKey, null, fromOrgId);
      assertNotNull(sourceWs, "copy must not remove the source org's own worksheet");
      Viewsheet sourceVs = (Viewsheet) storage.getXMLSerializable(vsKey, null, fromOrgId);
      assertNotNull(sourceVs, "copy must not remove the source org's own viewsheet");
      assertEquals(fromOrgId, sourceVs.getBaseEntry().getOrgID(),
                  "the source org's own viewsheet must still point at the source org, untouched " +
                  "by the copy");
   }

   // ── scenario 2d (rename version of 2b): viewsheet -> base worksheet (wentry) ──

   @Test
   void rename_viewsheetToBaseWorksheet_bindingSurvivesMigration_sourceRemoved() throws Exception {
      String fromOrgId = "vs_wentry_rename_from";
      String toOrgId = "vs_wentry_rename_to";
      track(fromOrgId);
      track(toOrgId);

      String wsKey = seedWorksheet(fromOrgId, "/ws1");
      String vsKey = seedViewsheetBoundTo(fromOrgId, "/vs1", wsKey);

      // rename = copyStorageData(rename=true) + a separate removeStorage() of the source bucket
      // -- BlobIndexedStorage has no single public 3-arg method that does both, unlike
      // DependencyStorageService (see B1a note)
      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), true);
      storage.removeStorage(fromOrgId);

      String newVsKey = AssetEntry.createAssetEntry(vsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      Viewsheet migratedVs = (Viewsheet) storage.getXMLSerializable(newVsKey, null, toOrgId);
      assertNotNull(migratedVs,
                   "rename must produce a migrated viewsheet under the target org bucket");

      AssetEntry migratedWentry = migratedVs.getBaseEntry();
      assertEquals(toOrgId, migratedWentry.getOrgID(),
                  "the viewsheet's wentry must be rewritten to point at the target org");

      String expectedWsKey = AssetEntry.createAssetEntry(wsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      assertEquals(expectedWsKey, migratedWentry.toIdentifier(true),
                  "the migrated wentry must identify the SAME worksheet that was itself migrated " +
                  "alongside it");

      Worksheet migratedWs =
         (Worksheet) storage.getXMLSerializable(migratedWentry.toIdentifier(true), null, toOrgId);
      assertNotNull(migratedWs,
                   "the worksheet the migrated viewsheet's wentry points at must itself load " +
                   "successfully after rename");

      assertNull(storage.getXMLSerializable(wsKey, null, fromOrgId),
                "rename must remove the source org's own worksheet -- no orphan left behind");
      assertNull(storage.getXMLSerializable(vsKey, null, fromOrgId),
                "rename must remove the source org's own viewsheet -- no orphan left behind");
   }

   // ── scenario 2a: copy -- worksheet internal (cross-worksheet, "outer") mirror ──

   @Test
   void copy_worksheetMirror_pointsAtNewOrg_sourceUnaffected() throws Exception {
      String fromOrgId = "ws_mirror_from";
      String toOrgId = "ws_mirror_to";
      track(fromOrgId);
      track(toOrgId);

      String wsAKey = seedWorksheet(fromOrgId, "/wsA");
      String wsBKey = seedWorksheetWithMirror(fromOrgId, "/wsB", wsAKey);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      String newWsBKey = AssetEntry.createAssetEntry(wsBKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      String expectedWsAKeyInNewOrg = AssetEntry.createAssetEntry(wsAKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);

      // raw DOM, not getXMLSerializable() -- see class-level Javadoc note on 2a's self-heal trap
      Document migratedDoc = storage.getDocument(newWsBKey, toOrgId);
      assertNotNull(migratedDoc,
                   "copy must produce a migrated worksheet document under the target org bucket");

      // MirrorTableAssemblyInfo.writeXML() wraps MirrorAssemblyImpl's own <mirrorAssembly
      // source="..."> element inside an outer, attribute-less <mirrorAssembly> of its own (see
      // MigrateWorksheetTask's own XPath: ".../mirrorAssembly/mirrorAssembly") -- item(0) is the
      // outer wrapper (no "source"), item(1) is the one actually carrying the rewritten source.
      Element mirrorElem = (Element) migratedDoc.getElementsByTagName("mirrorAssembly").item(1);
      assertNotNull(mirrorElem,
                   "sanity check: the migrated worksheet must still contain its mirrorAssembly node");
      assertEquals(expectedWsAKeyInNewOrg, mirrorElem.getAttribute("source"),
                  "mirrorAssembly[@source] must be rewritten to identify the SAME worksheet A " +
                  "that was itself migrated alongside it, under the target org");

      String assetDependencyText =
         migratedDoc.getElementsByTagName("assetDependency").item(0).getTextContent();
      assertEquals(expectedWsAKeyInNewOrg, assetDependencyText,
                  "assetDependency CDATA must stay in sync with mirrorAssembly[@source]");

      Document sourceDoc = storage.getDocument(wsBKey, fromOrgId);
      assertNotNull(sourceDoc, "copy must not remove the source org's own worksheet");
      Element sourceMirrorElem =
         (Element) sourceDoc.getElementsByTagName("mirrorAssembly").item(1);
      assertEquals(wsAKey, sourceMirrorElem.getAttribute("source"),
                  "the source org's own mirror must still point at its own worksheet A, " +
                  "untouched by the copy");
   }

   // ── scenario 2d (rename version of 2a): worksheet internal mirror ──

   @Test
   void rename_worksheetMirror_pointsAtNewOrg_sourceRemoved() throws Exception {
      String fromOrgId = "ws_mirror_rename_from";
      String toOrgId = "ws_mirror_rename_to";
      track(fromOrgId);
      track(toOrgId);

      String wsAKey = seedWorksheet(fromOrgId, "/wsA");
      String wsBKey = seedWorksheetWithMirror(fromOrgId, "/wsB", wsAKey);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), true);
      storage.removeStorage(fromOrgId);

      String newWsBKey = AssetEntry.createAssetEntry(wsBKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      String expectedWsAKeyInNewOrg = AssetEntry.createAssetEntry(wsAKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);

      // raw DOM, not getXMLSerializable() -- see class-level Javadoc note on 2a's self-heal trap
      Document migratedDoc = storage.getDocument(newWsBKey, toOrgId);
      assertNotNull(migratedDoc,
                   "rename must produce a migrated worksheet document under the target org bucket");

      Element mirrorElem = (Element) migratedDoc.getElementsByTagName("mirrorAssembly").item(1);
      assertNotNull(mirrorElem,
                   "sanity check: the migrated worksheet must still contain its mirrorAssembly node");
      assertEquals(expectedWsAKeyInNewOrg, mirrorElem.getAttribute("source"),
                  "mirrorAssembly[@source] must be rewritten to identify the SAME worksheet A " +
                  "that was itself migrated alongside it, under the target org");

      String assetDependencyText =
         migratedDoc.getElementsByTagName("assetDependency").item(0).getTextContent();
      assertEquals(expectedWsAKeyInNewOrg, assetDependencyText,
                  "assetDependency CDATA must stay in sync with mirrorAssembly[@source]");

      assertNull(storage.getDocument(wsBKey, fromOrgId),
                "rename must remove the source org's own worksheet -- no orphan left behind");
      assertNull(storage.getDocument(wsAKey, fromOrgId),
                "rename must remove the source org's own mirrored worksheet A too -- no orphan " +
                "left behind");
   }

   // ── scenario 2c: copy -- viewsheet embedding a nested/library viewsheet ──

   @Test
   void copy_viewsheetEmbeddingNestedViewsheet_referenceRewritten() throws Exception {
      String fromOrgId = "vs_nested_from";
      String toOrgId = "vs_nested_to";
      track(fromOrgId);
      track(toOrgId);

      String libWsKey = seedWorksheet(fromOrgId, "/libWs");
      String libVsKey = seedViewsheetBoundTo(fromOrgId, "/libVs", libWsKey);
      String otherLibVsKey = seedViewsheetBoundTo(fromOrgId, "/otherLibVs", libWsKey);
      String parentVsKey = seedViewsheetEmbedding(fromOrgId, "/parentVs", libVsKey, otherLibVsKey);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      String newParentVsKey = AssetEntry.createAssetEntry(parentVsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      String expectedLibVsKeyInNewOrg = AssetEntry.createAssetEntry(libVsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);

      // raw DOM, not getXMLSerializable() -- Viewsheet.ventry self-heals at parse time just like
      // 2a's mirror source, see class-level Javadoc note
      Document migratedDoc = storage.getDocument(newParentVsKey, toOrgId);
      assertNotNull(migratedDoc,
                   "copy must produce a migrated parent viewsheet document under the target org bucket");

      Element nestedAssembly = findAssemblyByClass(migratedDoc, Viewsheet.class.getName());
      assertNotNull(nestedAssembly,
                   "sanity check: migrated parent viewsheet must still contain its nested " +
                   "viewsheet assembly");

      // ventry: the DIRECT child <viewsheetEntry> of the nested <assembly> element -- this is
      // what MigrateViewsheetTask.updateViewsheet() actually rewrites (confirmed by its use of
      // a direct-child-only Tool.getChildNodeByTagName() search)
      Element ventryWrapper = Tool.getChildNodeByTagName(nestedAssembly, "viewsheetEntry");
      assertNotNull(ventryWrapper,
                   "sanity check: nested assembly must still carry its own viewsheetEntry (ventry)");
      AssetEntry migratedVentry =
         AssetEntry.createAssetEntry(Tool.getChildNodeByTagName(ventryWrapper, "assetEntry"));
      assertEquals(toOrgId, migratedVentry.getOrgID(),
                  "the nested viewsheet's own ventry (\"which library viewsheet was this embedded " +
                  "copy sourced from\") must be rewritten to the target org");
      assertEquals(expectedLibVsKeyInNewOrg, migratedVentry.toIdentifier(true),
                  "the migrated ventry must identify the SAME library viewsheet that was itself " +
                  "migrated alongside it, not just some other viewsheet under the target org");

      // ViewsheetVSAssemblyInfo.entry: a SEPARATE field, nested two levels deeper (outer
      // <assemblyInfo> wrapper -> inner <assemblyInfo class="...ViewsheetVSAssemblyInfo"> -> its
      // own <viewsheetEntry>, same double-wrapping shape as 2a's mirrorAssembly/mirrorAssembly)
      // that production's embed-viewsheet flow does not populate, and that MigrateViewsheetTask's
      // direct-child search structurally cannot reach. Recorded here as current
      // (unconfirmed-by-product) behavior, not asserted as correct -- see matrix doc scenario 2c note.
      Element assemblyInfoWrapper = Tool.getChildNodeByTagName(nestedAssembly, "assemblyInfo");
      Element assemblyInfoElem = Tool.getChildNodeByTagName(assemblyInfoWrapper, "assemblyInfo");
      Element infoEntryWrapper = Tool.getChildNodeByTagName(assemblyInfoElem, "viewsheetEntry");
      assertNotNull(infoEntryWrapper,
                   "sanity check: ViewsheetVSAssemblyInfo.entry must still be present after migration");
      AssetEntry migratedInfoEntry =
         AssetEntry.createAssetEntry(Tool.getChildNodeByTagName(infoEntryWrapper, "assetEntry"));
      assertEquals(fromOrgId, migratedInfoEntry.getOrgID(),
                  "CURRENT (likely unintended) behavior: ViewsheetVSAssemblyInfo.entry is NOT " +
                  "reachable by MigrateViewsheetTask's direct-child search and is left pointing " +
                  "at the source org even after migration -- this assertion pins down present " +
                  "behavior, it is not a claim that this is correct");

      Document sourceDoc = storage.getDocument(parentVsKey, fromOrgId);
      assertNotNull(sourceDoc, "copy must not remove the source org's own parent viewsheet");
      Element sourceNestedAssembly = findAssemblyByClass(sourceDoc, Viewsheet.class.getName());
      AssetEntry sourceVentry = AssetEntry.createAssetEntry(Tool.getChildNodeByTagName(
         Tool.getChildNodeByTagName(sourceNestedAssembly, "viewsheetEntry"), "assetEntry"));
      assertEquals(fromOrgId, sourceVentry.getOrgID(),
                  "the source org's own nested viewsheet reference must still point at the " +
                  "source org, untouched by the copy");
   }

   // ── scenario 2d (rename version of 2c): viewsheet embedding a nested/library viewsheet ──

   @Test
   void rename_viewsheetEmbeddingNestedViewsheet_referenceRewritten_sourceRemoved() throws Exception {
      String fromOrgId = "vs_nested_rename_from";
      String toOrgId = "vs_nested_rename_to";
      track(fromOrgId);
      track(toOrgId);

      String libWsKey = seedWorksheet(fromOrgId, "/libWs");
      String libVsKey = seedViewsheetBoundTo(fromOrgId, "/libVs", libWsKey);
      String otherLibVsKey = seedViewsheetBoundTo(fromOrgId, "/otherLibVs", libWsKey);
      String parentVsKey = seedViewsheetEmbedding(fromOrgId, "/parentVs", libVsKey, otherLibVsKey);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), true);
      storage.removeStorage(fromOrgId);

      String newParentVsKey = AssetEntry.createAssetEntry(parentVsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      String expectedLibVsKeyInNewOrg = AssetEntry.createAssetEntry(libVsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);

      // raw DOM, not getXMLSerializable() -- ventry self-heals at parse time, see class Javadoc
      Document migratedDoc = storage.getDocument(newParentVsKey, toOrgId);
      assertNotNull(migratedDoc,
                   "rename must produce a migrated parent viewsheet document under the target org bucket");

      Element nestedAssembly = findAssemblyByClass(migratedDoc, Viewsheet.class.getName());
      assertNotNull(nestedAssembly,
                   "sanity check: migrated parent viewsheet must still contain its nested " +
                   "viewsheet assembly");

      Element ventryWrapper = Tool.getChildNodeByTagName(nestedAssembly, "viewsheetEntry");
      AssetEntry migratedVentry =
         AssetEntry.createAssetEntry(Tool.getChildNodeByTagName(ventryWrapper, "assetEntry"));
      assertEquals(toOrgId, migratedVentry.getOrgID(),
                  "the nested viewsheet's own ventry must be rewritten to the target org");
      assertEquals(expectedLibVsKeyInNewOrg, migratedVentry.toIdentifier(true),
                  "the migrated ventry must identify the SAME library viewsheet that was itself " +
                  "migrated alongside it");

      assertNull(storage.getDocument(parentVsKey, fromOrgId),
                "rename must remove the source org's own parent viewsheet -- no orphan left behind");
      assertNull(storage.getDocument(libVsKey, fromOrgId),
                "rename must remove the source org's own library viewsheet too -- no orphan " +
                "left behind");
   }

   // ── scenario 2j: boundary assertion -- org migration never triggers the asset-rename pipeline ──

   @Test
   void copyAndRename_neverTriggerAssetRenamePipeline() throws Exception {
      String copyFromOrgId = "boundary_copy_from";
      String copyToOrgId = "boundary_copy_to";
      String renameFromOrgId = "boundary_rename_from";
      String renameToOrgId = "boundary_rename_to";
      track(copyFromOrgId);
      track(copyToOrgId);
      track(renameFromOrgId);
      track(renameToOrgId);

      // exercise every fixture shape landed so far (mirror, wentry, nested viewsheet) so this
      // regression guard covers every dispatch branch MigrateWorksheetTask/MigrateViewsheetTask
      // actually has, not just a trivial empty migration
      String wsAKey = seedWorksheet(copyFromOrgId, "/wsA");
      seedWorksheetWithMirror(copyFromOrgId, "/wsB", wsAKey);
      String wsKey = seedWorksheet(copyFromOrgId, "/ws1");
      seedViewsheetBoundTo(copyFromOrgId, "/vs1", wsKey);
      String libVsKey = seedViewsheetBoundTo(copyFromOrgId, "/libVs", wsKey);
      seedViewsheetEmbedding(copyFromOrgId, "/parentVs", libVsKey, libVsKey);

      storage.copyStorageData(new Organization(copyFromOrgId), new Organization(copyToOrgId), false);

      seedWorksheet(renameFromOrgId, "/ws1");
      storage.copyStorageData(new Organization(renameFromOrgId), new Organization(renameToOrgId), true);
      storage.removeStorage(renameFromOrgId);

      verifyNoInteractions(renameTransformHandler);
   }

   // ── scenario 10a: copy -- Data Source, generic fallback branch ──

   @Test
   void copy_dataSource_keyMigrated_sourceInfoResolvesToSameNameDataSource() throws Exception {
      String fromOrgId = "ds_fallback_from_v2";
      String toOrgId = "ds_fallback_to_v2";
      track(fromOrgId);
      track(toOrgId);

      String dsName = "TestDS";
      String dsKey = seedDataSource(fromOrgId, dsName);
      String wsKey = seedWorksheetBoundToDataSource(fromOrgId, "/ws1", dsName);

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      // generic fallback branch just re-keys the container AssetEntry -- confirm the Data
      // Source object itself lands under the target org, same name, fully readable
      String newDsKey = AssetEntry.createAssetEntry(dsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier();
      XDataSourceWrapper migratedDs =
         (XDataSourceWrapper) storage.getXMLSerializable(newDsKey, null, toOrgId);
      assertNotNull(migratedDs,
                   "copy must produce a migrated data source under the target org bucket");
      assertEquals(dsName, migratedDs.getSource().getFullName(),
                  "the migrated data source must keep the same name -- SourceInfo is a pure " +
                  "name reference with no orgID of its own, so name preservation is the only " +
                  "thing that keeps a worksheet binding resolvable after migration");

      // the worksheet's SourceInfo (no orgID field at all, see claude/org-migration-content-
      // rewrite.md's Binding Storage Formats table) is untouched by migration and still names
      // the same data source, which now also exists under the target org -- together this is
      // what "resolves to the same-name data source in the new org" means in practice
      String newWsKey = AssetEntry.createAssetEntry(wsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      Worksheet migratedWs = (Worksheet) storage.getXMLSerializable(newWsKey, null, toOrgId);
      assertNotNull(migratedWs, "copy must produce a migrated worksheet under the target org bucket");
      BoundTableAssembly migratedTable =
         (BoundTableAssembly) migratedWs.getAssembly("t1");
      assertEquals(dsName, migratedTable.getSourceInfo().getPrefix(),
                  "the migrated worksheet's SourceInfo must still name the same data source " +
                  "(unchanged, since SourceInfo has no orgID to rewrite), and that name now " +
                  "resolves under the target org because the data source itself was migrated " +
                  "alongside it");

      assertNotNull(storage.getXMLSerializable(dsKey, null, fromOrgId),
                   "copy must not remove the source org's own data source");
      assertNotNull(storage.getXMLSerializable(wsKey, null, fromOrgId),
                   "copy must not remove the source org's own worksheet");
   }

   // ── scenario 2f: copy -- inline SQL/Query node's own orgId element ──

   @Test
   void copy_sqlBoundQuery_orgIdElementRewritten() throws Exception {
      String fromOrgId = "sql_query_from";
      String toOrgId = "sql_query_to";
      track(fromOrgId);
      track(toOrgId);

      String wsKey = seedWorksheetWithSqlBoundQuery(fromOrgId, "/ws1");

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      // XQuery.orgId is a plain CDATA element, not an AssetEntry -- no self-heal anywhere in its
      // parse path (unlike wentry/ventry/mirror), so a deserialized-object assertion is safe here
      String newWsKey = AssetEntry.createAssetEntry(wsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      Worksheet migratedWs = (Worksheet) storage.getXMLSerializable(newWsKey, null, toOrgId);
      assertNotNull(migratedWs, "copy must produce a migrated worksheet under the target org bucket");
      SQLBoundTableAssembly migratedTable =
         (SQLBoundTableAssembly) migratedWs.getAssembly("t1");
      JDBCQuery migratedQuery =
         ((SQLBoundTableAssemblyInfo) migratedTable.getTableInfo()).getQuery();
      assertEquals(toOrgId, migratedQuery.getOrganizationId(),
                  "the inline JDBCQuery's own orgId element must be rewritten to the target org " +
                  "-- this is a completely separate mechanism from SourceInfo (which has no " +
                  "orgID field at all) and from AssetEntry-based bindings");

      SQLBoundTableAssembly sourceTable = (SQLBoundTableAssembly)
         ((Worksheet) storage.getXMLSerializable(wsKey, null, fromOrgId)).getAssembly("t1");
      assertEquals(fromOrgId,
                  ((SQLBoundTableAssemblyInfo) sourceTable.getTableInfo()).getQuery().getOrganizationId(),
                  "the source org's own query must still carry its own orgId, untouched by the copy");
   }

   // ── scenario 2h: identity fields -- modifiedBy/createdBy vs defaultBookmarkUser ──

   @Test
   void copy_modifiedByCreatedBy_notRewritten_duringOrgOnlyMigration() throws Exception {
      String fromOrgId = "identity_fields_from";
      String toOrgId = "identity_fields_to";
      track(fromOrgId);
      track(toOrgId);

      String wsKey = seedWorksheet(fromOrgId, "/ws1");

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      String newWsKey = AssetEntry.createAssetEntry(wsKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      Worksheet migratedWs = (Worksheet) storage.getXMLSerializable(newWsKey, null, toOrgId);
      assertNotNull(migratedWs, "copy must produce a migrated worksheet under the target org bucket");

      // CURRENT (likely unintended) behavior, not a correctness claim: MigrateWorksheetTask's
      // modifiedBy/createdBy rewrite is gated on Tool.equals(ouser, getOldName()) -- getOldName()
      // is only ever populated by the (entry, oldUserName, newUserName) constructor family (a
      // *user* rename), never by the (entry, oOrg, nOrg) constructor BlobIndexedStorage actually
      // uses for org migration. So getOldName() is always null here, a real "anonymous" string
      // never equals null, and this rewrite silently never fires during a pure org copy/rename.
      assertEquals("anonymous", migratedWs.getCreatedBy(),
                  "createdBy is left completely unchanged by org-only migration -- pinning this " +
                  "as observed behavior, not asserting it's correct");
   }

   @Test
   void copy_bookmarkDefaultUser_orgSegmentRewritten() throws Exception {
      String fromOrgId = "bookmark_from";
      String toOrgId = "bookmark_to";
      track(fromOrgId);
      track(toOrgId);

      String bookmarkKey = seedViewsheetBookmark(fromOrgId, "/vs1", "alice");

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      // unlike the modifiedBy/createdBy check above, MigrateBookmarkTask.processAssemblies()
      // checks user.getOrgID().equals(getOldOrganization().getOrganizationID()) -- an
      // organization check, not a user-name check -- so this one DOES fire during org migration
      String newBookmarkKey = AssetEntry.createAssetEntry(bookmarkKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier(true);
      VSBookmark migratedBookmark =
         (VSBookmark) storage.getXMLSerializable(newBookmarkKey, null, toOrgId);
      assertNotNull(migratedBookmark,
                   "copy must produce a migrated bookmark under the target org bucket");
      assertEquals(toOrgId, migratedBookmark.getDefaultBookmark().getOwner().getOrgID(),
                  "defaultBookmarkUser's org segment must be rewritten to the target org -- " +
                  "unlike modifiedBy/createdBy, this rewrite is gated on an organization check, " +
                  "not a user-name check, so it fires correctly during a pure org migration");
      assertEquals("alice", migratedBookmark.getDefaultBookmark().getOwner().getName(),
                  "the user name portion must be untouched, only the org segment changes");

      VSBookmark sourceBookmark =
         (VSBookmark) storage.getXMLSerializable(bookmarkKey, null, fromOrgId);
      assertEquals(fromOrgId, sourceBookmark.getDefaultBookmark().getOwner().getOrgID(),
                  "the source org's own bookmark must still point at the source org, untouched " +
                  "by the copy");
   }

   // ── scenario 10b: copy -- VPM / Partition / Data Model, generic fallback branch ──

   @Test
   void copy_vpmPartitionDataModel_keyMigrated_identityFieldsNotRewritten() throws Exception {
      String fromOrgId = "vpm_partition_from";
      String toOrgId = "vpm_partition_to";
      track(fromOrgId);
      track(toOrgId);

      String vpmKey = seedVpm(fromOrgId, "myDs/myVpm");
      String partitionKey = seedPartition(fromOrgId, "myDs/myPartition");
      String dataModelKey = seedDataModel(fromOrgId, "myDs");

      storage.copyStorageData(new Organization(fromOrgId), new Organization(toOrgId), false);

      String newVpmKey = AssetEntry.createAssetEntry(vpmKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier();
      VirtualPrivateModel migratedVpm =
         (VirtualPrivateModel) storage.getXMLSerializable(newVpmKey, null, toOrgId);
      assertNotNull(migratedVpm, "copy must produce a migrated VPM under the target org bucket");

      String newPartitionKey = AssetEntry.createAssetEntry(partitionKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier();
      XPartition migratedPartition =
         (XPartition) storage.getXMLSerializable(newPartitionKey, null, toOrgId);
      assertNotNull(migratedPartition,
                   "copy must produce a migrated partition under the target org bucket");

      // XDataModel (the container) has no createdBy/modifiedBy fields at all -- just a name +
      // folder list -- so it only needs the key-rekey assertion, no identity-field evidence
      String newDataModelKey = AssetEntry.createAssetEntry(dataModelKey)
         .cloneAssetEntry(new Organization(toOrgId)).toIdentifier();
      assertNotNull(storage.getXMLSerializable(newDataModelKey, null, toOrgId),
                   "copy must produce a migrated Data Model container under the target org bucket");

      // scenario 10c evidence: the generic fallback branch only re-keys the container AssetEntry,
      // it never parses/rewrites fields inside the deserialized object -- createdBy/modifiedBy
      // are confirmed left pointing at the source org's user identity. Pinning current behavior,
      // not asserting it's correct (same symptom class as 2h's org-migration modifiedBy gap,
      // different mechanism: no dispatch at all here, vs. a name-based guard that never matches).
      assertEquals(fromOrgId + ":creator", migratedVpm.getCreatedBy(),
                  "CURRENT (likely unintended) behavior: VPM's createdBy is NOT rewritten by the " +
                  "generic fallback branch and is left pointing at the source org's identity");
      assertEquals(fromOrgId + ":creator", migratedPartition.getCreatedBy(),
                  "CURRENT (likely unintended) behavior: Partition's createdBy is NOT rewritten " +
                  "by the generic fallback branch either");

      assertNotNull(storage.getXMLSerializable(vpmKey, null, fromOrgId),
                   "copy must not remove the source org's own VPM");
      assertNotNull(storage.getXMLSerializable(partitionKey, null, fromOrgId),
                   "copy must not remove the source org's own partition");
   }

   // ── fixture helpers ──

   private void track(String orgId) {
      if(!seededOrgIds.contains(orgId)) {
         seededOrgIds.add(orgId);
      }
   }

   /**
    * Seeds a minimal {@link Worksheet} (one {@link EmbeddedTableAssembly}) directly into
    * {@link BlobIndexedStorage}, bypassing {@code AssetRepository.setSheet()} -- this test
    * targets {@code BlobIndexedStorage} directly, not the full repository stack.
    */
   private String seedWorksheet(String orgId, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null, orgId);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, "t1");
      ws.addAssembly(assembly);
      ws.setPrimaryAssembly(assembly);
      ws.setCreated(System.currentTimeMillis());
      ws.setCreatedBy("anonymous");
      ws.setLastModified(System.currentTimeMillis());
      ws.setLastModifiedBy("anonymous");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, ws);
      return key;
   }

   /**
    * Seeds a minimal {@link Worksheet} containing a single cross-worksheet ("outer")
    * {@link MirrorTableAssembly} pointing at the given, already-seeded worksheet key's
    * {@code "t1"} assembly -- the same shape {@code AssetEventUtil.createMirrorAssembly()}
    * produces for a real worksheet-to-worksheet mirror.
    */
   private String seedWorksheetWithMirror(String orgId, String path, String mirroredWsKey)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null, orgId);
      AssetEntry mirroredEntry = AssetEntry.createAssetEntry(mirroredWsKey);
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly mirroredAssembly = new EmbeddedTableAssembly(new Worksheet(), "t1");
      MirrorTableAssembly mirror =
         new MirrorTableAssembly(ws, "m1", mirroredEntry, true, mirroredAssembly);
      ws.addAssembly(mirror);
      ws.setPrimaryAssembly(mirror);
      ws.setCreated(System.currentTimeMillis());
      ws.setCreatedBy("anonymous");
      ws.setLastModified(System.currentTimeMillis());
      ws.setLastModifiedBy("anonymous");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, ws);
      return key;
   }

   /**
    * Seeds a minimal {@link Viewsheet} whose {@code wentry} (base worksheet binding) points at
    * the given, already-seeded worksheet key.
    */
   private String seedViewsheetBoundTo(String orgId, String path, String wsKey) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, orgId);
      AssetEntry wsEntry = AssetEntry.createAssetEntry(wsKey);
      Viewsheet vs = new Viewsheet(wsEntry);
      vs.setCreated(System.currentTimeMillis());
      vs.setCreatedBy("anonymous");
      vs.setLastModified(System.currentTimeMillis());
      vs.setLastModifiedBy("anonymous");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, vs);
      return key;
   }

   /**
    * Seeds a minimal parent {@link Viewsheet} embedding a nested {@link Viewsheet} assembly, the
    * same shape {@code ComposerObjectService.addEmbeddedViewsheet()} produces: the nested
    * viewsheet's own {@code ventry} (via {@code setEntry()}) records which library viewsheet it
    * was embedded from. Also sets {@code ViewsheetVSAssemblyInfo.entry} to a second, different
    * key purely to give the test something to probe -- production's embed flow does not
    * populate this field itself (see class-level Javadoc note on scenario 2c).
    */
   private String seedViewsheetEmbedding(String orgId, String path, String embeddedVsKey,
                                         String infoEntryVsKey) throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, orgId);
      Viewsheet parentVs = new Viewsheet();
      parentVs.setCreated(System.currentTimeMillis());
      parentVs.setCreatedBy("anonymous");
      parentVs.setLastModified(System.currentTimeMillis());
      parentVs.setLastModifiedBy("anonymous");

      Viewsheet nestedVs = new Viewsheet().createVSAssembly("Nested1");
      nestedVs.setEntry(AssetEntry.createAssetEntry(embeddedVsKey));
      ((ViewsheetVSAssemblyInfo) nestedVs.getVSAssemblyInfo())
         .setEntry(AssetEntry.createAssetEntry(infoEntryVsKey));
      parentVs.addAssembly(nestedVs);

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, parentVs);
      return key;
   }

   /**
    * Finds the first NESTED {@code <assembly class="...">} element in the document whose
    * {@code class} attribute matches (skipping the document's own root element -- a stored
    * {@link Viewsheet}'s {@code writeXML()} emits {@code <assembly class="...Viewsheet">} as
    * its own root tag too, so a naive search would match the parent viewsheet itself first).
    */
   private Element findAssemblyByClass(Document doc, String className) {
      NodeList assemblies = doc.getElementsByTagName("assembly");

      for(int i = 0; i < assemblies.getLength(); i++) {
         Element assembly = (Element) assemblies.item(i);

         if(assembly == doc.getDocumentElement()) {
            continue;
         }

         if(className.equals(assembly.getAttribute("class"))) {
            return assembly;
         }
      }

      return null;
   }

   /**
    * Seeds a minimal {@link XDataSourceWrapper} (the class {@code DataSourceRegistry} actually
    * stores under {@code Type.DATA_SOURCE}) directly into {@link BlobIndexedStorage} --
    * production data source entries use {@code AssetRepository.QUERY_SCOPE}, not
    * {@code GLOBAL_SCOPE} (see {@code DataSourceRegistry.java:543-544}).
    */
   private String seedDataSource(String orgId, String name) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE,
                                        name, null, orgId);
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName(name);
      ds.setCustom(true);
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:" + name);
      ds.setRequireLogin(false);

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, new XDataSourceWrapper(ds));
      return key;
   }

   /**
    * Seeds a minimal {@link Worksheet} containing a single {@link BoundTableAssembly} whose
    * {@link SourceInfo} (a pure name reference, no {@code orgID} field of its own -- see
    * claude/org-migration-content-rewrite.md's Binding Storage Formats table) names the given
    * data source by prefix.
    */
   private String seedWorksheetBoundToDataSource(String orgId, String path, String dsName)
      throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null, orgId);
      Worksheet ws = new Worksheet();
      BoundTableAssembly assembly = new BoundTableAssembly(ws, "t1");
      assembly.setSourceInfo(new SourceInfo(XSourceInfo.PHYSICAL_TABLE, dsName, "SOME_TABLE"));
      ws.addAssembly(assembly);
      ws.setPrimaryAssembly(assembly);
      ws.setCreated(System.currentTimeMillis());
      ws.setCreatedBy("anonymous");
      ws.setLastModified(System.currentTimeMillis());
      ws.setLastModifiedBy("anonymous");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, ws);
      return key;
   }

   /**
    * Seeds a minimal {@link Worksheet} containing a single {@link SQLBoundTableAssembly} whose
    * {@link JDBCQuery} carries its own {@code orgId} -- a completely separate mechanism from
    * {@code SourceInfo} (used by {@link BoundTableAssembly}), which has no org concept at all.
    * Deliberately leaves the query's data source unset: {@code XQuery.writeXML()}/{@code
    * parseXML()} only touch {@code DataSourceRegistry} when a {@code <datasource>} node is
    * present, so this keeps the fixture independent of the {@code Config}/{@code Drivers}/
    * {@code Plugins} chain scenario 10a needed.
    */
   private String seedWorksheetWithSqlBoundQuery(String orgId, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        path, null, orgId);
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, "t1");
      JDBCQuery query = new JDBCQuery();
      query.setOrganizationId(orgId);
      query.setSQLDefinition(new UniformSQL());
      ((SQLBoundTableAssemblyInfo) assembly.getTableInfo()).setQuery(query);
      ws.addAssembly(assembly);
      ws.setPrimaryAssembly(assembly);
      ws.setCreated(System.currentTimeMillis());
      ws.setCreatedBy("anonymous");
      ws.setLastModified(System.currentTimeMillis());
      ws.setLastModifiedBy("anonymous");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, ws);
      return key;
   }

   /**
    * Seeds a minimal {@link VSBookmark} whose default bookmark is owned by {@code userName} in
    * the given org, directly into {@link BlobIndexedStorage} under a {@code
    * Type.VIEWSHEET_BOOKMARK} entry -- mirrors the real key format {@code
    * VSUtil.createBookmarkIdentifier()} produces (mangling a viewsheet {@link AssetEntry}'s
    * {@code toIdentifier()} string), without needing a real {@link Viewsheet} or the
    * {@code AssetRepository}-level bookmark-save flow.
    */
   private String seedViewsheetBookmark(String orgId, String vsPath, String userName)
      throws Exception
   {
      AssetEntry vsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                          vsPath, null, orgId);
      String bookmarkId = VSUtil.createBookmarkIdentifier(vsEntry);
      IdentityID owner = new IdentityID(userName, orgId);
      AssetEntry bookmarkEntry = new AssetEntry(AssetRepository.USER_SCOPE,
         AssetEntry.Type.VIEWSHEET_BOOKMARK, bookmarkId, owner, orgId);

      VSBookmark bookmark = new VSBookmark(vsEntry.toIdentifier(), owner);
      bookmark.setDefaultBookmark(new VSBookmark.DefaultBookmark(VSBookmark.HOME_BOOKMARK, owner));

      String key = bookmarkEntry.toIdentifier(true);
      storage.putXMLSerializable(key, bookmark);
      return key;
   }

   /**
    * Seeds a minimal {@link VirtualPrivateModel} directly into {@link BlobIndexedStorage} under a
    * {@code Type.VPM} entry (generic fallback branch, same shape as {@code seedDataSource()} but
    * a plain POJO -- no {@code Config}/{@code Drivers}/{@code Plugins} chain needed).
    */
   private String seedVpm(String orgId, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.VPM,
                                        path, null, orgId);
      VirtualPrivateModel vpm = new VirtualPrivateModel("myVpm");
      vpm.setCreatedBy(orgId + ":creator");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, vpm);
      return key;
   }

   /**
    * Seeds a minimal {@link XPartition} directly into {@link BlobIndexedStorage} under a
    * {@code Type.PARTITION} entry (generic fallback branch, same shape as {@link #seedVpm}).
    */
   private String seedPartition(String orgId, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PARTITION,
                                        path, null, orgId);
      XPartition partition = new XPartition("myPartition");
      partition.setCreatedBy(orgId + ":creator");

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, partition);
      return key;
   }

   /**
    * Seeds a minimal {@link XDataModel} container directly into {@link BlobIndexedStorage} under
    * a {@code Type.DATA_MODEL} entry (generic fallback branch). Unlike VPM/Partition, this class
    * has no {@code createdBy}/{@code modifiedBy} fields -- just a name and a folder list -- so
    * there's nothing to pin for scenario 10c here beyond the key re-key itself.
    */
   private String seedDataModel(String orgId, String datasourceName) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_MODEL,
                                        datasourceName, null, orgId);
      XDataModel model = new XDataModel(datasourceName);

      String key = entry.toIdentifier(true);
      storage.putXMLSerializable(key, model);
      return key;
   }
}
