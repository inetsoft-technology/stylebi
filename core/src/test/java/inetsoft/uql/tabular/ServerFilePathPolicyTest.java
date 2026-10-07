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
package inetsoft.uql.tabular;

import inetsoft.util.MessageException;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bug #64331, the rules that decide which server paths a user may browse and save as the path
 * of a tabular data source.
 */
@Tag("core")
class ServerFilePathPolicyTest {
   @TempDir
   Path temp;

   @Test
   void siteAdminOrDisabledSecurityIsUnrestricted() {
      assertTrue(policy(false, false, null).isUnrestricted(null));
      assertTrue(policy(true, true, null).isUnrestricted(USER));
      assertFalse(policy(true, false, null).isUnrestricted(USER));
      assertFalse(policy(true, false, null).isUnrestricted(null));
   }

   @Test
   void pathsUnderARootAreContained() throws IOException {
      Path root = Files.createDirectories(temp.resolve("data"));
      Files.createDirectories(root.resolve("sub"));

      assertTrue(ServerFilePathPolicy.isContained(root, root));
      assertTrue(ServerFilePathPolicy.isContained(root, root.resolve("sub")));
      // a path that doesn't exist yet is resolved through its existing ancestor
      assertTrue(ServerFilePathPolicy.isContained(root, root.resolve("new/deeper")));
   }

   @Test
   void pathsOutsideARootAreNotContained() throws IOException {
      Path root = Files.createDirectories(temp.resolve("data"));
      Path sibling = Files.createDirectories(temp.resolve("data2"));

      assertFalse(ServerFilePathPolicy.isContained(root, sibling));
      assertFalse(ServerFilePathPolicy.isContained(root, temp));
      assertFalse(ServerFilePathPolicy.isContained(root, root.resolve("..")));
      assertFalse(ServerFilePathPolicy.isContained(root, root.resolve("../data2")));
      assertFalse(ServerFilePathPolicy.isContained(root, root.resolve("missing/../../data2")));
      assertFalse(ServerFilePathPolicy.isContained(root, null));
   }

   @Test
   void symbolicLinkOutOfARootIsNotContained() throws IOException {
      Path root = Files.createDirectories(temp.resolve("data"));
      Path outside = Files.createDirectories(temp.resolve("outside"));
      Path link = root.resolve("link");

      try {
         Files.createSymbolicLink(link, outside);
      }
      catch(IOException | UnsupportedOperationException | SecurityException e) {
         assumeTrue(false, "symbolic links can't be created here");
      }

      assertFalse(ServerFilePathPolicy.isContained(root, link));
      assertFalse(ServerFilePathPolicy.isContained(root, link.resolve("file.csv")));
      // the lexical check used for query paths keeps a link placed on the server working
      assertTrue(ServerFilePathPolicy.isUnderFolder(root, link));
   }

   @Test
   void queryPathsMayNotLeaveTheFolder() {
      Path root = temp.resolve("data");

      assertTrue(ServerFilePathPolicy.isUnderFolder(root, root.resolve("a/b.csv")));
      assertTrue(ServerFilePathPolicy.isUnderFolder(root, root));
      assertFalse(ServerFilePathPolicy.isUnderFolder(root, root.resolve("../x.csv")));
      assertFalse(ServerFilePathPolicy.isUnderFolder(root, temp.resolve("data2/x.csv")));
   }

   @Test
   void fileUnderFolderIsReturnedOnlyWhenContained() {
      String root = temp.resolve("data").toString();
      File inside = new File(root + "/a/b/");
      File outside = new File(root + "/../../");

      assertSame(inside, ServerFilePathPolicy.getFileUnderFolder(root, inside));
      assertNull(ServerFilePathPolicy.getFileUnderFolder(root, outside));
      assertNull(ServerFilePathPolicy.getFileUnderFolder(root, null));
      assertNull(ServerFilePathPolicy.getFileUnderFolder(null, inside));
      assertSame(outside, ServerFilePathPolicy.getFileUnderFolder("/", outside));
   }

   @Test
   void rootsAreParsedFromTheProperty() {
      String a = temp.resolve("a").toString();
      String b = temp.resolve("b").toString();
      List<Path> roots = ServerFilePathPolicy.parseRoots(" " + a + " , ;" + b + "; relative/dir");

      assertEquals(List.of(Paths.get(a), Paths.get(b)), roots);
      assertTrue(ServerFilePathPolicy.parseRoots(null).isEmpty());
      assertTrue(ServerFilePathPolicy.parseRoots("  ").isEmpty());
   }

   @Test
   void restrictedUserMaySaveOnlyPathsUnderAnAllowedRoot() throws IOException {
      Path root = Files.createDirectories(temp.resolve("data"));
      ServerFilePathPolicy policy = policy(true, false, root.toString());

      assertNull(policy.getRefusedPath(source(root.resolve("sub").toFile()), null, USER));
      assertEquals(temp.toFile(), policy.getRefusedPath(source(temp.toFile()), null, USER));
      assertThrows(MessageException.class,
                   () -> policy.checkDataSource(source(temp.toFile()), null, USER));
   }

   @Test
   void restrictedUserMaySaveNoPathWithoutAllowedRoots() {
      ServerFilePathPolicy policy = policy(true, false, null);

      assertNotNull(policy.getRefusedPath(source(temp.toFile()), null, USER));
      assertFalse(policy.isAllowed(temp.toFile(), USER));
   }

   @Test
   void unchangedPathOfTheStoredDataSourceIsAllowed() {
      ServerFilePathPolicy policy = policy(true, false, null);
      File outside = temp.resolve("secret").toFile();

      // a rename or an edit of another property keeps the stored path
      assertNull(policy.getRefusedPath(source(outside), source(outside), USER));
      assertNull(policy.getRefusedPath(
         source(new File(temp.resolve("x/../secret").toString())), source(outside), USER));
      // narrowing a stored path outside of the allowed roots is still a change
      assertNotNull(policy.getRefusedPath(
         source(new File(outside, "sub")), source(outside), USER));
   }

   @Test
   void unrestrictedUserMaySaveAnyPath() {
      assertNull(policy(true, true, null).getRefusedPath(source(temp.toFile()), null, USER));
      assertNull(policy(false, false, null).getRefusedPath(source(temp.toFile()), null, null));
   }

   @Test
   void dataSourceWithoutServerPathsIsNotChecked() {
      assertTrue(ServerFilePathPolicy.getServerPaths(new NoPathDataSource()).isEmpty());
      assertNull(policy(true, false, null).getRefusedPath(new NoPathDataSource(), null, USER));
      assertEquals(1, ServerFilePathPolicy.getServerPaths(source(temp.toFile())).size());
   }

   @Test
   void missingRequiredPathIsRefusedUnlessTheStoredOneIsMissing() throws IOException {
      Path root = Files.createDirectories(temp.resolve("data"));
      ServerFilePathPolicy policy = policy(true, false, root.toString());

      File refused = policy.getRefusedPath(source(null), null, USER);
      assertNotNull(refused);
      assertTrue(ServerFilePathPolicy.isEmptyPath(refused));
      assertNotNull(policy.getRefusedPath(source(new File("")), null, USER));
      // clearing a stored path is a change
      assertNotNull(policy.getRefusedPath(source(null), source(root.toFile()), USER));
      // a stored data source without a path may still be edited
      assertNull(policy.getRefusedPath(source(null), source(null), USER));
      assertThrows(MessageException.class, () -> policy.checkDataSource(source(null), null, USER));
      // unrestricted users may leave it out
      assertNull(policy(true, true, null).getRefusedPath(source(null), null, USER));
   }

   @Test
   void missingOptionalPathIsAllowed() {
      OptionalPathDataSource ds = new OptionalPathDataSource();

      assertNull(policy(true, false, null).getRefusedPath(ds, null, USER));
      assertTrue(ServerFilePathPolicy.hasServerPaths(OptionalPathDataSource.class));
      assertFalse(ServerFilePathPolicy.hasServerPaths(NoPathDataSource.class));
   }

   private static ServerFilePathPolicy policy(boolean securityEnabled, boolean siteAdmin,
                                              String roots)
   {
      return new ServerFilePathPolicy(() -> securityEnabled, p -> siteAdmin, p -> roots);
   }

   private static PathDataSource source(File file) {
      PathDataSource ds = new PathDataSource();
      ds.setFile(file);
      return ds;
   }

   private static final Principal USER = () -> "user";

   public static final class PathDataSource extends TabularDataSource<PathDataSource> {
      public PathDataSource() {
         super("TEST_PATH", PathDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Property(label = "Root Folder", required = true)
      public File getFile() {
         return file;
      }

      public void setFile(File file) {
         this.file = file;
      }

      private File file;
   }

   public static final class OptionalPathDataSource
      extends TabularDataSource<OptionalPathDataSource>
   {
      public OptionalPathDataSource() {
         super("TEST_OPTIONAL_PATH", OptionalPathDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Property(label = "Folder")
      public File getFile() {
         return file;
      }

      public void setFile(File file) {
         this.file = file;
      }

      private File file;
   }

   public static final class NoPathDataSource extends TabularDataSource<NoPathDataSource> {
      public NoPathDataSource() {
         super("TEST_NO_PATH", NoPathDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Property(label = "Name")
      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      private String label;
   }
}
