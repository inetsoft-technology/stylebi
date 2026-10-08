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
package inetsoft.util.swap;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bug #77683: the swapper writes swap files on principal-less threads and a request thread in an
 * organization reads them back, so the cache directory, and the sree.home and server.type
 * settings it defaults from, must not be read from an <code>inetsoft.org.&lt;org&gt;.</code>
 * override.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SwapCacheDirectoryOrgScopeTest {
   @BeforeEach
   void setUp() {
      clearThread();
      savedCacheDir = SreeEnv.getProperty(CACHE_DIR);
      SreeEnv.setProperty(CONTROL, "global");
      SreeEnv.setProperty(ORG_PREFIX + CONTROL, "org");
   }

   @AfterEach
   void tearDown() {
      clearThread();

      if(savedCacheDir == null) {
         SreeEnv.remove(CACHE_DIR);
      }
      else {
         SreeEnv.setProperty(CACHE_DIR, savedCacheDir);
      }

      for(String name : new String[] { CACHE_DIR, SREE_HOME, SERVER_TYPE, CONTROL }) {
         SreeEnv.remove(ORG_PREFIX + name);
      }

      SreeEnv.remove(CONTROL);
   }

   @Test
   void orgThreadResolvesGlobalCacheDirectory(@TempDir Path tempDir) throws Exception {
      String global = tempDir.resolve("global").toString();
      SreeEnv.setProperty(CACHE_DIR, global);
      SreeEnv.setProperty(ORG_PREFIX + CACHE_DIR, tempDir.resolve("orga").toString());
      XIntFragment fragment = new XIntFragment(createValues());
      // a fragment keeps the directory of its first file (#78043), so the org thread
      // resolves the file of a fragment that has not resolved one yet
      XIntFragment orgFragment = new XIntFragment(createValues());

      try {
         File noPrincipalFile = onPrincipalLessThread(() -> fragment.getFile("a.tdat"));
         String noPrincipalDir =
            onPrincipalLessThread(() -> FileSystemService.getInstance().getCacheDirectory());

         inOrg("orga");
         // the override is in effect for a setting that is not JVM-wide
         assertEquals("org", SreeEnv.getProperty(CONTROL));
         assertEquals(global, noPrincipalDir);
         assertEquals(global, FileSystemService.getInstance().getCacheDirectory());
         assertEquals(noPrincipalFile, orgFragment.getFile("a.tdat"));
         assertEquals(noPrincipalFile, fragment.getFile("a.tdat"));
      }
      finally {
         fragment.dispose();
         orgFragment.dispose();
      }
   }

   @Test
   void fragmentSwappedOnPrincipalLessThreadIsReadOnOrgThread(@TempDir Path tempDir)
      throws Exception
   {
      SreeEnv.setProperty(CACHE_DIR, tempDir.resolve("global").toString());
      SreeEnv.setProperty(ORG_PREFIX + CACHE_DIR, tempDir.resolve("orga").toString());
      XIntFragment fragment = new XIntFragment(createValues());

      try {
         // the swapper threads have no principal
         assertTrue(onPrincipalLessThread(fragment::swap), "fragment was not swapped");
         assertFalse(fragment.isValid(), "fragment is still in memory");

         inOrg("orga");
         assertEquals(1005, fragment.getSafely(5));
         assertEquals(100, fragment.size());
      }
      finally {
         fragment.dispose();
      }
   }

   @Test
   void fragmentSwappedOnPrincipalLessThreadIsDeletedOnOrgThread(@TempDir Path tempDir)
      throws Exception
   {
      SreeEnv.setProperty(CACHE_DIR, tempDir.resolve("global").toString());
      SreeEnv.setProperty(ORG_PREFIX + CACHE_DIR, tempDir.resolve("orga").toString());
      XIntFragment fragment = new XIntFragment(createValues());
      File file;

      try {
         assertTrue(onPrincipalLessThread(fragment::swap), "fragment was not swapped");
         file = onPrincipalLessThread(() -> fragment.getFile(fragment.prefix + ".tdat"));
         assertTrue(file.exists(), "swap file was not written");

         inOrg("orga");
      }
      finally {
         fragment.dispose();
      }

      // disposing on the org thread must remove the file the swapper wrote, not orphan it
      assertFalse(file.exists(), "swap file was orphaned: " + file);
   }

   @Test
   void sreeHomeOverrideDoesNotChangeDefaultCacheDirectory(@TempDir Path tempDir)
      throws Exception
   {
      SreeEnv.setProperty(ORG_PREFIX + SREE_HOME, tempDir.resolve("orghome").toString());
      assertDefaultCacheDirectoryIsGlobal();
   }

   @Test
   void serverTypeOverrideDoesNotChangeDefaultCacheDirectory() throws Exception {
      SreeEnv.setProperty(ORG_PREFIX + SERVER_TYPE, "server_cluster");
      assertDefaultCacheDirectoryIsGlobal();
   }

   /**
    * The default cache directory, used when replet.cache.directory is not set, is kept by the
    * first thread that resolves it, so compare fresh services first called on each thread.
    */
   private void assertDefaultCacheDirectoryIsGlobal() throws Exception {
      assumeTrue(System.getenv("SREE_CACHE_DIR") == null, "SREE_CACHE_DIR is set");
      SreeEnv.remove(CACHE_DIR);
      String expected =
         onPrincipalLessThread(() -> new FileSystemService(null, null).getCacheDirectory());

      inOrg("orga");
      assertEquals(expected, new FileSystemService(null, null).getCacheDirectory());
   }

   private static <T> T onPrincipalLessThread(Callable<T> task) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         return executor.submit(() -> {
            clearThread();
            return task.call();
         }).get(30, TimeUnit.SECONDS);
      }
      finally {
         executor.shutdownNow();
      }
   }

   private static void inOrg(String orgID) {
      clearThread();
      ThreadContext.setContextPrincipal(new XPrincipal(new IdentityID("user", orgID)));
   }

   private static void clearThread() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      OrganizationContextHolder.clear();
   }

   private static int[] createValues() {
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      return values;
   }

   private String savedCacheDir;
   private static final String ORG_PREFIX = "inetsoft.org.orga.";
   private static final String CACHE_DIR = "replet.cache.directory";
   private static final String SREE_HOME = "sree.home";
   private static final String SERVER_TYPE = "server.type";
   private static final String CONTROL = "test77683.key";
}
