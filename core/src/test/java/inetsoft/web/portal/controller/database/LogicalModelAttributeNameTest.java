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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.MessageException;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Bug #82096: the documentation does not allow a period in a logical model attribute name.
 * Saving a model rejects a new attribute name that contains a period. A name that is already an
 * attribute of the stored model, or of the base model of an extended model, is still accepted so
 * that existing models keep saving. Whether a name is new is judged from the stored models, not
 * from the oldName or baseElement values sent by the client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LogicalModelAttributeNameTest.TestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LogicalModelAttributeNameTest {
   /*
    * The update looks up the model dependencies through the static
    * DependencyStorageService.getInstance(), and an extended model is stored in and read from the
    * static DataSourceRegistry.getRegistry().
    */
   @Configuration
   static class TestConfiguration {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }
   }

   private static final String DS = "DS";
   private static final String LM = "LM";
   private static final String BASE = "Base";
   private static final String VIEW = "View";

   @Autowired
   private DataSourceRegistry registry;

   private final Principal principal = () -> "admin";
   private final Map<String, XLogicalModel> models = new LinkedHashMap<>();
   private final Map<String, Object> registryObjects = new HashMap<>();

   private SecurityEngine securityEngine;
   private XRepository repository;
   private XDataModel dataModel;
   private DataSourceService dataSourceService;

   @BeforeEach
   void setUp() throws Exception {
      reset(registry);
      when(registry.getEntries(anyString(), any(AssetEntry.Type.class)))
         .thenReturn(new AssetEntry[0]);
      when(registry.getObject(any(AssetEntry.class), anyBoolean()))
         .thenAnswer(i -> registryObjects.get(((AssetEntry) i.getArgument(0)).getPath()));
      doAnswer(i -> registryObjects.put(((AssetEntry) i.getArgument(0)).getPath(),
                                        i.getArgument(1)))
         .when(registry).setObject(any(AssetEntry.class), any());

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), any(ResourceType.class),
                                          any(String.class), any(ResourceAction.class)))
         .thenReturn(true);

      dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(DS);
      when(dataModel.getPartition(VIEW)).thenReturn(new XPartition(VIEW));
      when(dataModel.getPartitionNames()).thenReturn(new String[0]);
      when(dataModel.getLogicalModel(anyString())).thenAnswer(i -> models.get(i.getArgument(0)));
      when(dataModel.getLogicalModelNames())
         .thenAnswer(i -> models.keySet().toArray(new String[0]));
      doAnswer(i -> {
         XLogicalModel model = i.getArgument(0);
         models.put(model.getName(), model);
         return null;
      }).when(dataModel).addLogicalModel(any(XLogicalModel.class));

      repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);

      dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.getDataModel(DS)).thenReturn(dataModel);
      when(dataSourceService.getModelAssetEntry(any())).thenAnswer(i -> i.getArgument(0));
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any()))
         .thenReturn(true);
   }

   // ---- new model ----

   @Test
   void createModel_newDottedName_isRejectedBeforeAnyWrite() throws Exception {
      LogicalModelDefinition definition = definition(LM, entity("E", null, attribute("a.b", null)));

      MessageException ex = assertThrows(MessageException.class, () -> service().createModel(
         DS, null, VIEW, definition, null, principal));

      assertTrue(ex.getMessage().contains("a.b"), ex.getMessage());
      verify(dataModel, never()).addLogicalModel(any(XLogicalModel.class));
      verify(repository, never()).updateDataModel(any());
      assertFalse(models.containsKey(LM));
   }

   // a client's oldName does not make a name stored (the expression dialog sends oldName == name)
   @Test
   void createModel_dottedNameWithClientOldName_isRejected() {
      LogicalModelDefinition definition =
         definition(LM, entity("E", null, attribute("a.b", "a.b")));

      assertThrows(MessageException.class, () -> service().createModel(
         DS, null, VIEW, definition, null, principal));
   }

   @Test
   void createModel_nameWithoutPeriod_isSaved() throws Exception {
      LogicalModelDefinition definition = definition(LM, entity("E", null, attribute("a_b", null)));

      service().createModel(DS, null, VIEW, definition, null, principal);

      assertNotNull(models.get(LM).getEntity("E").getAttribute("a_b"));
      verify(repository).updateDataModel(dataModel);
   }

   // ---- stored model ----

   @Test
   void updateModel_storedDottedName_isSaved() throws Exception {
      storedModel(LM, "E", "a.b");
      LogicalModelDefinition definition =
         definition(LM, entity("E", "E", attribute("a.b", "a.b"), attribute("c", null)));

      service().updateModel(DS, null, LM, definition, null, principal);

      assertNotNull(models.get(LM).getEntity("E").getAttribute("a.b"));
      assertNotNull(models.get(LM).getEntity("E").getAttribute("c"));
   }

   // the stored name is found in any entity, so a renamed entity keeps its dotted attribute
   @Test
   void updateModel_storedDottedNameInRenamedEntity_isSaved() throws Exception {
      storedModel(LM, "E", "a.b");
      LogicalModelDefinition definition = definition(LM, entity("F", null, attribute("a.b", null)));

      service().updateModel(DS, null, LM, definition, null, principal);

      assertNotNull(models.get(LM).getEntity("F").getAttribute("a.b"));
   }

   @Test
   void updateModel_newDottedName_isRejectedBeforeAnyWrite() throws Exception {
      XLogicalModel stored = storedModel(LM, "E", "a.b");
      LogicalModelDefinition definition =
         definition(LM, entity("E", "E", attribute("a.b", "a.b"), attribute("c.d", "c.d")));

      MessageException ex = assertThrows(MessageException.class, () -> service().updateModel(
         DS, null, LM, definition, null, principal));

      assertTrue(ex.getMessage().contains("c.d"), ex.getMessage());
      verify(repository, never()).updateDataModel(any());
      assertSame(stored, models.get(LM));
   }

   // the comparison is exact, so a change of case is a new name
   @Test
   void updateModel_dottedNameWithOtherCase_isRejected() {
      storedModel(LM, "E", "a.b");
      LogicalModelDefinition definition = definition(LM, entity("E", "E", attribute("A.b", "a.b")));

      assertThrows(MessageException.class, () -> service().updateModel(
         DS, null, LM, definition, null, principal));
   }

   // ---- extended model ----

   // the base attributes are sent in the definition of an extended model and cannot be renamed
   // there, so a dotted name of the base model must not block creating an extended model
   @Test
   void createExtendedModel_baseDottedName_isSaved() throws Exception {
      storedModel(BASE, "E", "p.q");
      LogicalModelDefinition definition = definition(LM, entity("E", "E", attribute("p.q", "p.q")));

      service().createModel(DS, null, VIEW, definition, BASE, principal);

      verify(repository).updateDataModel(dataModel);
   }

   @Test
   void createExtendedModel_newDottedName_isRejected() throws Exception {
      storedModel(BASE, "E", "p.q");
      LogicalModelDefinition definition = definition(
         LM, entity("E", "E", attribute("p.q", "p.q"), attribute("x.y", null)));

      MessageException ex = assertThrows(MessageException.class, () -> service().createModel(
         DS, null, VIEW, definition, BASE, principal));

      assertTrue(ex.getMessage().contains("x.y"), ex.getMessage());
      verify(repository, never()).updateDataModel(any());
   }

   @Test
   void updateExtendedModel_baseDottedName_isSaved() throws Exception {
      XLogicalModel base = storedModel(BASE, "E", "p.q");
      XLogicalModel child = new XLogicalModel(LM);
      child.setPartition(VIEW);
      child.setDataModel(dataModel);
      base.addLogicalModel(child, false);
      LogicalModelDefinition definition = definition(LM, entity("E", "E", attribute("p.q", "p.q")));
      definition.setParent(BASE);

      service().updateModel(DS, null, LM, definition, BASE, principal);

      verify(repository).updateDataModel(dataModel);
   }

   private XLogicalModel storedModel(String name, String entityName, String attributeName) {
      XLogicalModel model = new XLogicalModel(name);
      model.setPartition(VIEW);
      model.setDataModel(dataModel);
      XEntity entity = new XEntity(entityName);
      entity.addAttribute(new ExpressionAttribute(attributeName, "1"));
      model.addEntity(entity);
      models.put(name, model);
      return model;
   }

   private static LogicalModelDefinition definition(String name, EntityModel... entities) {
      LogicalModelDefinition model = new LogicalModelDefinition();
      model.setName(name);
      model.setPartition(VIEW);
      model.setEntities(new ArrayList<>(Arrays.asList(entities)));
      return model;
   }

   private static EntityModel entity(String name, String oldName, XAttributeModel... attributes) {
      EntityModel entity = new EntityModel();
      entity.setName(name);
      entity.setOldName(oldName);
      entity.setAttributes(new ArrayList<>(Arrays.asList(attributes)));
      return entity;
   }

   private static XAttributeModel attribute(String name, String oldName) {
      XAttributeModel attribute = new XAttributeModel();
      attribute.setName(name);
      attribute.setOldName(oldName);
      attribute.setType("expression");
      attribute.setExpression("1");
      attribute.setDataType("integer");
      RefTypeModel refType = new RefTypeModel();
      refType.setFormula("None");
      attribute.setRefType(refType);
      return attribute;
   }

   private LogicalModelService service() {
      return new LogicalModelService(
         securityEngine, repository, dataSourceService, mock(DataRefModelFactoryService.class),
         mock(LogicalModelTreeService.class), mock(AssetRepository.class),
         mock(DependencyHandler.class), mock(RenameTransformHandler.class));
   }
}
