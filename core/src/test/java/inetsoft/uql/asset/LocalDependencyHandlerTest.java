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
package inetsoft.uql.asset;

/*
 * General-purpose test file for LocalDependencyHandler. Add further scenarios for this class here
 * rather than creating new per-scenario test classes -- keep each scenario's own rationale in a
 * comment block right above its test method(s), the way the logical model scenario below does, so
 * the file-level comment doesn't have to be rewritten every time a new scenario is added.
 */

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformObject;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  LocalDependencyHandlerTest.TestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalDependencyHandlerTest {
   /*
    * DependencyStorageService is a @Service picked up by component scan in production, so it is not
    * in the test context. The handler reaches it through the static
    * DependencyStorageService.getInstance() -> ConfigurationContext.getSpringBean(), which throws
    * when the bean is missing -- and the handler swallows that, silently writing nothing.
    */
   @Configuration
   static class TestConfiguration {
      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }

   @BeforeEach
   void setUp() {
      // the context is cached across test methods, so drop the previous method's stubbing
      reset(dependencyStorageService);
      handler = new LocalDependencyHandler(mock(XRepository.class));
   }

   /*
    * Bug #75816: an extended (child) logical model was registered under the flat
    * "datasource/extended name" path, the same form a base model uses. Extended names come from
    * additional connections and can collide with a base model's name, so the edge could be
    * attributed to -- and later deleted from -- the wrong model. Extended models are identified by
    * the three-segment "datasource/base name/extended name" path instead, matching what the delete
    * paths clean up (RepositoryObjectService.deleteNodes(), LogicalModelService.removeModel()).
    *
    * The key side is unchanged: XLogicalModel.getPartition() returns the base model's partition for
    * an extended model, so both register under the base physical view.
    */
   @Test
   void baseLogicalModelIsRegisteredUnderItsOwnName() throws Exception {
      handler.updateModelDependencies(logicalModel(null), true);

      assertEquals(List.of(identifier(AssetEntry.Type.LOGIC_MODEL, LOGICAL_MODEL_NAME)),
                   capturedDependencies(identifier(AssetEntry.Type.PARTITION,
                                                   PHYSICAL_VIEW_NAME)));
   }

   @Test
   void extendedLogicalModelIsQualifiedByItsBaseModel() throws Exception {
      handler.updateModelDependencies(logicalModel(LOGICAL_MODEL_NAME), true);

      assertEquals(List.of(identifier(AssetEntry.Type.LOGIC_MODEL,
                                      LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME)),
                   capturedDependencies(identifier(AssetEntry.Type.PARTITION,
                                                   PHYSICAL_VIEW_NAME)));
   }

   /*
    * Removal has to name the extended model exactly the way registration did, otherwise the rename
    * path leaves a stale edge behind.
    */
   @Test
   void removingAnExtendedLogicalModelUsesTheSameQualifiedName() throws Exception {
      DependenciesInfo stored = new DependenciesInfo();
      AssetEntry extended = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL,
         DATA_SOURCE + "/" + LOGICAL_MODEL_NAME + "/" + EXTENDED_NAME, null);
      stored.setDependencies(new ArrayList<>(List.of(extended)));
      when(dependencyStorageService.get(identifier(AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME)))
         .thenReturn(stored);

      handler.updateModelDependencies(logicalModel(LOGICAL_MODEL_NAME), false);

      assertTrue(capturedDependencies(identifier(AssetEntry.Type.PARTITION, PHYSICAL_VIEW_NAME))
                    .isEmpty(),
                 "the extended model should no longer depend on the physical view");
   }

   /**
    * Creates a logical model bound to {@link #PHYSICAL_VIEW_NAME}.
    *
    * @param baseName the name of the base model, or {@code null} for a base model.
    */
   private XLogicalModel logicalModel(String baseName) {
      XLogicalModel model = mock(XLogicalModel.class);
      when(model.getDataSource()).thenReturn(DATA_SOURCE);
      when(model.getPartition()).thenReturn(PHYSICAL_VIEW_NAME);
      when(model.getEntities()).thenReturn(Collections.enumeration(List.<XEntity>of()));

      if(baseName == null) {
         when(model.getName()).thenReturn(LOGICAL_MODEL_NAME);
      }
      else {
         XLogicalModel base = mock(XLogicalModel.class);
         when(base.getName()).thenReturn(baseName);
         when(model.getName()).thenReturn(EXTENDED_NAME);
         when(model.getBaseModel()).thenReturn(base);
      }

      return model;
   }

   private String identifier(AssetEntry.Type type, String name) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, type, DATA_SOURCE + "/" + name, null)
         .toIdentifier();
   }

   /**
    * @return the identifiers of the assets stored as dependents of the given key.
    */
   private List<String> capturedDependencies(String key) throws Exception {
      ArgumentCaptor<RenameTransformObject> captor =
         ArgumentCaptor.forClass(RenameTransformObject.class);
      verify(dependencyStorageService).put(eq(key), captor.capture());

      return ((DependenciesInfo) captor.getValue()).getDependencies().stream()
         .map(asset -> ((AssetEntry) asset).toIdentifier())
         .toList();
   }

   private LocalDependencyHandler handler;
   @Autowired private DependencyStorageService dependencyStorageService;

   private static final String DATA_SOURCE = "Derby Embedded";
   private static final String PHYSICAL_VIEW_NAME = "physicalView";
   private static final String LOGICAL_MODEL_NAME = "logicalModel";
   private static final String EXTENDED_NAME = "additionalConnection";
}
