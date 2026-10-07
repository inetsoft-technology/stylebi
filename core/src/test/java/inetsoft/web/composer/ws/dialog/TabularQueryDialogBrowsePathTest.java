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
package inetsoft.web.composer.ws.dialog;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.Config;
import inetsoft.util.FileSystemService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.TabularFileModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #64331: the File/Folder picker of a tabular query lists the root folder of its data
 * source (the relativeTo editor property) and the folders under it. A client path such as
 * ".." or "sub/../.." used to list the folders above the root folder.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  TabularQueryDialogBrowsePathTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TabularQueryDialogBrowsePathTest {
   @BeforeEach
   void setUp() throws Exception {
      root = Files.createDirectories(temp.resolve("data"));
      Files.createDirectories(root.resolve("sub"));
      Files.writeString(root.resolve("sales.csv"), "a\n1\n");
      Files.createDirectories(temp.resolve("outside"));
      Files.writeString(temp.resolve("secret.csv"), "x\n");

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.DATA_SOURCE),
                                          anyString(), eq(ResourceAction.READ)))
         .thenReturn(true);
      controller = new TabularQueryDialogController(
         mock(TabularQueryDialogServiceProxy.class), new FileSystemService(null, null),
         securityEngine);

      PathQuery query = new PathQuery();
      query.rootFolder = root.toFile().getAbsolutePath();
      tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS);
      tabularUtil.when(() -> TabularUtil.createQuery(DS)).thenReturn(query);
   }

   @AfterEach
   void tearDown() {
      tabularUtil.close();
   }

   @Test
   void listsTheRootFolderOfTheDataSource() {
      assertEquals(List.of("sales.csv", "sub"), names(browse("/")));
      assertEquals(List.of(), names(browse("sub")));
   }

   @Test
   void parentPathsCanNotLeaveTheRootFolder() {
      assertTrue(names(browse("..")).isEmpty());
      assertTrue(names(browse("sub/../..")).isEmpty());
      assertTrue(names(browse("../outside")).isEmpty());
   }

   @Test
   void siblingFolderWithTheSamePrefixIsNotListed() throws Exception {
      Files.createDirectories(temp.resolve("data2/inner"));
      assertTrue(names(browse("../data2")).isEmpty());
   }

   private TreeNodeModel browse(String path) {
      return controller.browse(DS, "fileFolder", path, false, new TabularView(), USER);
   }

   private static List<String> names(TreeNodeModel tree) {
      return tree.children().stream()
         .map(node -> new File(((TabularFileModel) node.data()).getAbsolutePath()).getName())
         .sorted()
         .toList();
   }

   @TempDir
   Path temp;
   private Path root;
   private TabularQueryDialogController controller;
   private MockedStatic<TabularUtil> tabularUtil;
   private static final String DS = "files";
   private static final Principal USER = () -> "user";

   @Configuration
   static class TestConfig {
      // LayoutCreator reads the resource bundle of the query type from Config
      @Bean
      Config config() {
         return mock(Config.class);
      }

      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @View(vertical = true, value = { @View1(value = "fileFolder") })
   public static class PathQuery extends TabularQuery {
      public PathQuery() {
         super("TEST_BROWSE_PATH");
      }

      @Property(label = "File/Folder")
      @PropertyEditor(
         editorProperties = {
            @EditorProperty(name = "relativeTo", method = "getRootFolder"),
            @EditorProperty(name = "acceptTypes", value = ".txt,.csv")
         }
      )
      public File getFileFolder() {
         return fileFolder;
      }

      public void setFileFolder(File fileFolder) {
         this.fileFolder = fileFolder;
      }

      public String getRootFolder() {
         return rootFolder;
      }

      private File fileFolder;
      private String rootFolder;
   }
}
