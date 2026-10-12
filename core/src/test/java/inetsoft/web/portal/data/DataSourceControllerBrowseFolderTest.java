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
package inetsoft.web.portal.data;

import inetsoft.uql.tabular.ServerFilePathPolicy;
import inetsoft.util.FileSystemService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.ws.TabularFileModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #64331: the Browse Folder dialog of a Text/Excel Directory data source lists the whole
 * server only for a site admin, or for anyone when security is disabled. Anyone else only sees
 * the allowed roots and the folders under them, and nothing when no root is configured.
 */
@Tag("core")
class DataSourceControllerBrowseFolderTest {
   @BeforeEach
   void setUp() throws Exception {
      controller = new DataSourceController(null, null, null, null, null,
                                            new FileSystemService(null, null));
      allowed = Files.createDirectories(temp.resolve("allowed"));
      Files.createDirectories(allowed.resolve("sales"));
      Files.createDirectories(temp.resolve("outside"));
   }

   @Test
   void restrictedUserSeesOnlyTheAllowedRootsAtTheTop() {
      TreeNodeModel tree = controller.getRootFolder("/", USER, restricted(allowed.toString()));

      assertEquals(List.of(allowed.toFile().getAbsolutePath()), absolutePaths(tree));
   }

   @Test
   void restrictedUserSeesNothingWithoutAllowedRoots() {
      assertTrue(controller.getRootFolder("/", USER, restricted(null)).children().isEmpty());
      assertTrue(controller.getRootFolder(slashes(temp), USER, restricted(null))
                    .children().isEmpty());
   }

   @Test
   void restrictedUserMayListFoldersUnderAnAllowedRoot() {
      TreeNodeModel tree = controller.getRootFolder(
         slashes(allowed), USER, restricted(allowed.toString()));

      assertEquals(List.of(allowed.resolve("sales").toFile().getAbsolutePath()),
                   absolutePaths(tree));
      assertEquals(slashes(allowed) + "/sales",
                   ((TabularFileModel) tree.children().get(0).data()).getPath());
   }

   @Test
   void restrictedUserMayNotListFoldersOutsideTheAllowedRoots() {
      ServerFilePathPolicy policy = restricted(allowed.toString());

      assertTrue(controller.getRootFolder(slashes(temp), USER, policy).children().isEmpty());
      assertTrue(controller.getRootFolder(slashes(allowed) + "/..", USER, policy)
                    .children().isEmpty());
   }

   @Test
   void siteAdminMayListAnyFolder() {
      ServerFilePathPolicy policy =
         new ServerFilePathPolicy(() -> true, p -> true, p -> null);
      TreeNodeModel tree = controller.getRootFolder(slashes(temp), USER, policy);

      assertEquals(2, tree.children().size());
      assertFalse(controller.getRootFolder("/", USER, policy).children().isEmpty());
   }

   private static ServerFilePathPolicy restricted(String roots) {
      return new ServerFilePathPolicy(() -> true, p -> false, p -> roots);
   }

   private static List<String> absolutePaths(TreeNodeModel tree) {
      return tree.children().stream()
         .map(node -> ((TabularFileModel) node.data()).getAbsolutePath())
         .toList();
   }

   private static String slashes(Path path) {
      return path.toFile().getAbsolutePath().replace(File.separator, "/");
   }

   @TempDir
   Path temp;
   private Path allowed;
   private DataSourceController controller;

   private static final Principal USER = () -> "user";
}
