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
package inetsoft.web.portal.controller.database;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.ResourceAction;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.SaveVpmEvent;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * J1.0 probes of the data modeling bridge (design section 6, unknowns b, d, e, f): each test pins
 * the PREMISE the design read from the Java, prints the observed behavior, and fails loudly if
 * the premise is wrong. They run against the real data source registry through the unchanged
 * Portal services, so a verdict here is a fact about StyleBI, not about the bridge. The verdicts
 * are recorded in {@code 04-build-j1.md}. Unknown (c) lives in
 * {@code inetsoft.uql.asset.DataModelBrowseReadProbeTest} because it needs a protected member of
 * the asset engine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataModelUnknownsTest.DependencyStorageConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@Tag("integration")
class DataModelUnknownsTest {
   /** {@code DependencyTransformer.getDependencies} reaches this bean through a static lookup. */
   @Configuration
   static class DependencyStorageConfig {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   private static int count = 0;
   private final Principal bob = () -> "bob";
   @Autowired private DependencyStorageService dependencyStorage;
   private String ds;
   private DependencyHandler dependencyHandler;
   private RenameTransformHandler transformHandler;
   private DataSourceService dataSourceService;
   private LogicalModelService logicalService;

   @BeforeEach
   void setUp() throws Exception {
      reset(dependencyStorage);
      DataSourceRegistry.getRegistry().init();
      ds = "dmprobe_" + (++count);
      XDataModel dataModel = new XDataModel(ds);
      XPartition view = new XPartition("pv");
      view.addTable("T1", null, null);
      dataModel.addPartition(view);
      dataModel.addFolder("F");
      DataSourceRegistry.getRegistry().clearCache();

      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(anyString())).thenAnswer(i -> new XDataModel(ds));
      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any(Principal.class))).thenReturn(true);
      when(dataSourceService.checkPermission(any(), any(), any(), any())).thenReturn(true);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      XDataModel folderModel = new XDataModel(ds);
      folderModel.addFolder("F");
      when(dataSourceService.getDataModel(anyString())).thenReturn(folderModel);
      dependencyHandler = mock(DependencyHandler.class);
      transformHandler = mock(RenameTransformHandler.class);
      repositoryMock = repository;
      logicalService = new LogicalModelService(
         mock(SecurityEngine.class), repository, dataSourceService, null, null,
         mock(AssetRepository.class), dependencyHandler, transformHandler);
   }

   private XRepository repositoryMock;

   private XDataModel fresh() {
      DataSourceRegistry.getRegistry().clearCache();
      return new XDataModel(ds);
   }

   // -- (b) move persists and migrates ------------------------------------------------------

   @Test
   void moveDataModelsPersistsAcrossReload() throws Exception {
      XDataModel dataModel = new XDataModel(ds);
      XLogicalModel lm = new XLogicalModel("lm");
      lm.setPartition("pv");
      lm.addEntity(new XEntity("E"));
      dataModel.addLogicalModel(lm);
      DataSourceRegistry.getRegistry().clearCache();

      // a recorded dependent of the logical model and of the view
      seedDependency(AssetEntry.Type.LOGIC_MODEL, ds + "/lm");
      seedDependency(AssetEntry.Type.PARTITION, ds + "/pv");

      DatabaseModelBrowserService service = new DatabaseModelBrowserService(
         dataSourceService, repositoryMock, null, logicalService, mockSecurity(),
         mock(DataModelFolderManagerService.class), transformHandler);

      LogicalModel lmItem = new LogicalModel();
      lmItem.setName("lm");
      PhysicalModel pvItem = new PhysicalModel();
      pvItem.setName("pv");
      service.moveDataModels(ds, List.of(lmItem, pvItem), "F", bob);

      XDataModel reloaded = fresh();
      String lmFolder = reloaded.getLogicalModel("lm").getFolder();
      String pvFolder = reloaded.getPartition("pv").getFolder();
      System.out.println("PROBE(b) after reload: logical model folder=" + lmFolder +
                         ", physical view folder=" + pvFolder);
      assertEquals("F", lmFolder, "premise: the logical model move persists in the registry");
      assertEquals("F", pvFolder, "premise: the view move persists in the registry");

      ArgumentCaptor<RenameDependencyInfo> captor =
         ArgumentCaptor.forClass(RenameDependencyInfo.class);
      verify(transformHandler, atLeastOnce()).addTransformTask(captor.capture());
      int withObjects = 0;

      for(RenameDependencyInfo info : captor.getAllValues()) {
         System.out.println("PROBE(b) transform task objects=" + info.getDependencyMap().keySet());
         withObjects += info.getDependencyMap().size();
      }

      assertTrue(withObjects > 0,
                 "premise: the seeded dependents reach addTransformTask (async migration)");
   }

   // -- (d) duplicate entity / attribute names ----------------------------------------------

   @Test
   void duplicateEntityNamesAreRejectedOrAccepted() throws Exception {
      // two entities with one name, the second carrying other attributes; a case variant of it
      String entities = createAndDescribe("dupEntity",
         entity("Sales", attr("Amount", "T1", "A")),
         entity("Sales", attr("Other", "T1", "D")),
         entity("sales", attr("Third", "T1", "E")));
      System.out.println("PROBE(d) duplicate entity names -> " + entities);
      // premise (F5): the second 'Sales' is dropped with its attributes, first wins; the case
      // variant is a different key
      assertEquals("[Sales[Amount->A], sales[Third->E]]", entities,
                   "premise: duplicate entity dropped silently (first wins), case variant kept");

      // a duplicate attribute name that is the LAST attribute is dropped silently
      String tail = createAndDescribe("dupAttrTail",
         entity("Sales", attr("Amount", "T1", "A"), attr("Amount", "T1", "B")));
      System.out.println("PROBE(d) duplicate attribute last -> " + tail);
      assertEquals("[Sales[Amount->A]]", tail, "premise: duplicate attribute dropped (first wins)");

      // a case variant of an attribute name is a different key
      String variant = createAndDescribe("dupAttrCase",
         entity("Sales", attr("Amount", "T1", "A"), attr("amount", "T1", "B")));
      System.out.println("PROBE(d) attribute case variant -> " + variant);
      assertEquals("[Sales[Amount->A, amount->B]]", variant,
                   "premise: attribute names are case-sensitive keys");

      // a duplicate attribute name FOLLOWED by another attribute: the service adds each
      // attribute at its list position, so the position runs past the shortened list
      String middle;

      try {
         middle = createAndDescribe("dupAttrMiddle",
            entity("Sales", attr("Amount", "T1", "A"), attr("Amount", "T1", "B"),
                   attr("Other", "T1", "C")));
      }
      catch(Exception e) {
         middle = "THROWS " + e.getClass().getSimpleName() + ": " + e.getMessage();
      }

      System.out.println("PROBE(d) duplicate attribute then another -> " + middle);
      assertTrue(middle.startsWith("THROWS IndexOutOfBoundsException"),
                 "premise: a duplicate attribute followed by another throws (a 500, not a refusal)");
      assertNull(fresh().getLogicalModel("dupAttrMiddle"),
                 "the failed create stored nothing");
   }

   private String createAndDescribe(String name, EntityModel... entities) throws Exception {
      LogicalModelDefinition def = new LogicalModelDefinition();
      def.setName(name);
      def.setPartition("pv");
      def.setEntities(new ArrayList<>(List.of(entities)));
      logicalService.createModel(ds, null, "pv", def, null, bob);

      XLogicalModel stored = fresh().getLogicalModel(name);
      List<String> described = new ArrayList<>();
      Enumeration<XEntity> en = stored.getEntities();

      while(en.hasMoreElements()) {
         XEntity e = en.nextElement();
         List<String> attrs = new ArrayList<>();
         Enumeration<XAttribute> an = e.getAttributes();

         while(an.hasMoreElements()) {
            XAttribute a = an.nextElement();
            attrs.add(a.getName() + "->" + a.getColumn());
         }

         described.add(e.getName() + attrs);
      }

      return described.toString();
   }

   // -- (e) deleting a base logical model vs its extended children --------------------------

   @Test
   void deleteBaseLogicalModelRemovesOrOrphansExtended() throws Exception {
      XDataModel dataModel = new XDataModel(ds);
      XLogicalModel base = new XLogicalModel("base");
      base.setPartition("pv");
      base.addEntity(new XEntity("E"));
      dataModel.addLogicalModel(base);
      dataModel.getLogicalModel("base").addLogicalModel(new XLogicalModel("child"), true);
      DataSourceRegistry.getRegistry().clearCache();
      assertNotNull(fresh().getLogicalModel("base").getLogicalModel("child"),
                    "control: the child exists");
      AssetEntry[] before = extendedEntries();
      assertEquals(1, before.length, "control: one extended registry entry");

      logicalService.removeModel(ds, null, "base", null, bob);

      XDataModel reloaded = fresh();
      AssetEntry[] after = extendedEntries();
      System.out.println("PROBE(e) base present=" + (reloaded.getLogicalModel("base") != null) +
                         ", extended registry entries left=" + after.length);
      assertNull(reloaded.getLogicalModel("base"), "premise: the base is gone");
      assertEquals(0, after.length, "premise: the extended child vanishes with the base");

      ArgumentCaptor<AssetEntry> deleted = ArgumentCaptor.forClass(AssetEntry.class);
      verify(dependencyHandler, atLeastOnce()).deleteDependencies(deleted.capture());
      List<String> paths = deleted.getAllValues().stream().map(AssetEntry::getPath).toList();
      System.out.println("PROBE(e) dependency records deleted for: " + paths);
      assertEquals(List.of(ds + "/base"), paths,
                   "premise: only the BASE's dependency record is deleted; the child's is orphaned");
   }

   // -- (f) VPM update with a different body name ------------------------------------------

   @Test
   void vpmUpdateWithDifferentBodyNameOverwritesOtherVpm() throws Exception {
      XDataModel dataModel = new XDataModel(ds);
      VirtualPrivateModel a = new VirtualPrivateModel("A");
      a.setDescription("original A");
      VirtualPrivateModel b = new VirtualPrivateModel("B");
      b.setDescription("original B");
      dataModel.addVirtualPrivateModel(a, true);
      dataModel.addVirtualPrivateModel(b, true);
      DataSourceRegistry.getRegistry().clearCache();

      VPMController controller = new VPMController(
         null, null, dataSourceService, repositoryMock, mockSecurity(), dependencyHandler,
         transformHandler, null, null);
      VPMDefinition body = new VPMDefinition();
      body.setName("B");
      body.setDescription("written through update of A");
      body.setConditions(new ArrayList<>());
      SaveVpmEvent event = new SaveVpmEvent();
      event.setDatabase(ds);
      event.setVpmName("A");
      event.setModel(body);

      controller.updateModel(event, bob);

      XDataModel reloaded = fresh();
      List<String> names = Arrays.asList(reloaded.getVirtualPrivateModelNames());
      VirtualPrivateModel storedB = reloaded.getVirtualPrivateModel("B");
      System.out.println("PROBE(f) vpm names=" + names + ", B description=" +
                         (storedB == null ? null : storedB.getDescription()));
      assertFalse(names.contains("A"), "premise: A was removed by the update of A");
      assertNotNull(storedB);
      assertEquals("written through update of A", storedB.getDescription(),
                   "premise: B was silently overwritten by the update of A");
   }

   // -- helpers ----------------------------------------------------------------------------

   private AssetEntry[] extendedEntries() {
      return DataSourceRegistry.getRegistry().getExtendedModelEntries(
         ds, ds + "/base", AssetEntry.Type.EXTENDED_LOGIC_MODEL, true);
   }

   private SecurityEngine mockSecurity() throws Exception {
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      return security;
   }

   private void seedDependency(AssetEntry.Type type, String path) throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(List.of((AssetObject) new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null)));
      when(dependencyStorage.getWithOrg(eq(entry.toIdentifier()), any())).thenReturn(info);
   }

   private static EntityModel entity(String name, XAttributeModel... attributes) {
      EntityModel entity = new EntityModel();
      entity.setName(name);
      entity.setVisible(true);
      entity.setAttributes(new ArrayList<>(List.of(attributes)));
      return entity;
   }

   private static XAttributeModel attr(String name, String table, String column) {
      XAttributeModel attribute = new XAttributeModel();
      attribute.setName(name);
      attribute.setType("column");
      attribute.setTable(table);
      attribute.setColumn(column);
      attribute.setDataType("string");
      attribute.setVisible(true);
      attribute.setRefType(new RefTypeModel());
      return attribute;
   }
}
