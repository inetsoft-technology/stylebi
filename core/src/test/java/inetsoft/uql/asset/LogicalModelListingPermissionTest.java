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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77400: the logical models of a data source are listed by the QUERY permission stored
 * for each model, which the permission editors save as <tt>model::source</tt>, followed by
 * <tt>^__^folder</tt> when the model is in a folder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LogicalModelListingPermissionTest {
   private static final String DS = "DS";
   private static final String FOLDERED = "LM1";
   private static final String UNFOLDERED = "LM2";
   private static final String FOLDER = "F";
   private static final AssetEntry.Selector SELECTOR = new AssetEntry.Selector(
      AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);

   private AbstractAssetEngine engine;
   private MockedStatic<XRepository> repositoryStatic;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      engine = mock(AbstractAssetEngine.class, withSettings()
         .useConstructor(null, null).defaultAnswer(CALLS_REAL_METHODS));
      doReturn(false).when(engine).checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), any());
      doReturn(true).when(engine).checkDataSourcePermission(anyString(), any());
      doReturn(true).when(engine).checkDataModelFolderPermission(any(), anyString(), any());
      doReturn(mock(IndexedStorage.class)).when(engine).getStorage(any());

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[] { FOLDERED, UNFOLDERED });
      XLogicalModel foldered = mock(XLogicalModel.class);
      when(foldered.getFolder()).thenReturn(FOLDER);
      XLogicalModel unfoldered = mock(XLogicalModel.class);
      when(dataModel.getLogicalModel(FOLDERED)).thenReturn(foldered);
      when(dataModel.getLogicalModel(UNFOLDERED)).thenReturn(unfoldered);

      XRepository repository = mock(XRepository.class);
      when(repository.getDataModel(DS)).thenReturn(dataModel);
      repositoryStatic = mockStatic(XRepository.class);
      repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
   }

   @AfterEach
   void tearDown() {
      repositoryStatic.close();
   }

   private List<String> listModels() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, DS, null);
      entry.setProperty("prefix", DS);

      return Arrays.stream(engine.getEntries(entry, principal, ResourceAction.READ, SELECTOR))
         .filter(AssetEntry::isLogicModel)
         .map(e -> e.getProperty("source"))
         .toList();
   }

   @Test
   void resourceNameMatchesThePermissionEditors() {
      assertEquals(
         ResourcePermissionService.getLogicalModelResourceName(
            DS + XUtil.DATAMODEL_FOLDER_SPLITER + FOLDER + "^" + FOLDERED).getPath(),
         XUtil.getLogicalModelResourceName(DS, FOLDER, FOLDERED));
      assertEquals(
         ResourcePermissionService.getLogicalModelResourceName(DS + "^" + UNFOLDERED).getPath(),
         XUtil.getLogicalModelResourceName(DS, null, UNFOLDERED));
      assertEquals(UNFOLDERED + "::" + DS, XUtil.getLogicalModelResourceName(DS, "", UNFOLDERED));
   }

   @Test
   void explicitDenyOnModelInFolderIsHonored() throws Exception {
      doAnswer(inv -> !(FOLDERED + "::" + DS + "^__^" + FOLDER).equals(inv.getArgument(0)))
         .when(engine).checkQueryPermission(anyString(), any());

      assertEquals(List.of(UNFOLDERED), listModels());
   }

   @Test
   void explicitDenyOnModelWithoutFolderIsHonored() throws Exception {
      doAnswer(inv -> !(UNFOLDERED + "::" + DS).equals(inv.getArgument(0)))
         .when(engine).checkQueryPermission(anyString(), any());

      assertEquals(List.of(FOLDERED), listModels());
   }

   @Test
   void readableModelsAreListed() throws Exception {
      doReturn(true).when(engine).checkQueryPermission(anyString(), any());

      assertEquals(List.of(FOLDERED, UNFOLDERED), listModels());
      verify(engine).checkQueryPermission(eq(FOLDERED + "::" + DS + "^__^" + FOLDER), any());
      verify(engine).checkQueryPermission(eq(UNFOLDERED + "::" + DS), any());
   }
}
