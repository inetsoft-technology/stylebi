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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformObject;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
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

   /*
    * Bug #76765 (SSL-002): updateScriptDependencies scans a script's text for "identifier("
    * shapes with no awareness of whether that is a call site or the scanned function's own
    * declaration header. Updating a library function's own body -- which necessarily still
    * contains "function <name>(" -- made the function look like a caller of itself, so it was
    * recorded as its own dependent and later listed as a reason it could not be deleted. The fix
    * skips recording when the matched identifier resolves to the same AssetEntry as the entry
    * being scanned; genuine references to other functions must keep working.
    */
   @Test
   void updateScriptDependencies_doesNotRecordAFunctionAsItsOwnDependent() throws Exception {
      ThreadContext.setContextPrincipal(mockPrincipal());
      LibManager manager = mock(LibManager.class);
      when(manager.findScriptName(anyString())).thenAnswer(
         invocation -> invocation.getArgument(0));
      AssetEntry selfEntry = scriptEntry(SCRIPT_NAME);

      try(MockedStatic<LibManagerProvider> provider = mockStatic(LibManagerProvider.class)) {
         LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
         provider.when(LibManagerProvider::getInstance).thenReturn(libManagerProvider);
         when(libManagerProvider.getManager()).thenReturn(manager);

         handler.updateScriptDependencies(
            "function " + SCRIPT_NAME + "(value) { return value; }", selfEntry, true, false);
      }

      verify(dependencyStorageService, never()).put(eq(selfEntry.toIdentifier()), any());
   }

   @Test
   void updateScriptDependencies_stillRecordsAGenuineReferenceToAnotherFunction() throws Exception {
      ThreadContext.setContextPrincipal(mockPrincipal());
      LibManager manager = mock(LibManager.class);
      when(manager.findScriptName(anyString())).thenAnswer(
         invocation -> invocation.getArgument(0));
      AssetEntry selfEntry = scriptEntry(SCRIPT_NAME);
      AssetEntry helperEntry = scriptEntry(HELPER_SCRIPT_NAME);

      try(MockedStatic<LibManagerProvider> provider = mockStatic(LibManagerProvider.class)) {
         LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
         provider.when(LibManagerProvider::getInstance).thenReturn(libManagerProvider);
         when(libManagerProvider.getManager()).thenReturn(manager);

         handler.updateScriptDependencies(
            "function " + SCRIPT_NAME + "(value) { return " + HELPER_SCRIPT_NAME + "(value); }",
            selfEntry, true, false);
      }

      assertEquals(List.of(selfEntry.toIdentifier()),
                   capturedDependencies(helperEntry.toIdentifier()).stream()
                      .toList());
   }

   private AssetEntry scriptEntry(String name) {
      return new AssetEntry(AssetRepository.COMPONENT_SCOPE, AssetEntry.Type.SCRIPT, name, null);
   }

   /** AssetEntry/OrganizationManager require an {@link XPrincipal}, not a plain Principal mock. */
   private XPrincipal mockPrincipal() {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn("testUser");
      return principal;
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
   private static final String SCRIPT_NAME = "formatShortDollar";
   private static final String HELPER_SCRIPT_NAME = "helperFunc";
}
