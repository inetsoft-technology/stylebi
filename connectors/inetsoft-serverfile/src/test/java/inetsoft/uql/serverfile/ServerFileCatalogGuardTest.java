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
package inetsoft.uql.serverfile;

import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A7 -- the traversal guards still fire through the SPI path, at BOTH layers:
 * {@code ServerFileCatalog.describeDataset}'s own guard, and the shared
 * {@code TabularQueryContractSupport.resolveTargetFile} guard the round trip reaches afterward.
 * See {@link ServerFileSpringTestSupport}: {@code applyQueryContract}'s schema extraction
 * unconditionally needs a {@code Config} Spring bean, unrelated to Excel or file I/O.
 */
class ServerFileCatalogGuardTest {
   @BeforeAll
   static void installSpringBeans() {
      ServerFileSpringTestSupport.install();
   }

   @AfterAll
   static void uninstallSpringBeans() {
      ServerFileSpringTestSupport.uninstall();
   }

   @TempDir
   File root;

   private ServerFileDataSource ds;

   @BeforeEach
   void setUp() {
      ds = new ServerFileDataSource();
      ds.setName("guard-test-ds");
      ds.setFile(root);
   }

   @Test
   void fileFolderPropertyIsJavaIoFileTyped() throws Exception {
      // 03-reconcile.md D-6: the shared guards below fire only for a File-typed property --
      // "the guard throws" is true by construction and proves nothing about ServerFile unless
      // this property is genuinely File-typed. A refactor to String would silently leave the
      // whole guarded path unentered.
      Method getter = ServerFileQuery.class.getMethod("getFileFolder");
      assertEquals(File.class, getter.getReturnType());
      assertNotNull(ServerFileQuery.class.getMethod("setFileFolder", File.class));
   }

   @Test
   void describeDatasetRejectsAnAbsolutePathItself_beforeTouchingTheFilesystem() {
      assertThrows(IllegalArgumentException.class,
         () -> ServerFileCatalog.describeDataset(ds, "/etc/passwd"));
   }

   @Test
   void describeDatasetRejectsADotDotSegmentItself_beforeTouchingTheFilesystem() {
      assertThrows(IllegalArgumentException.class,
         () -> ServerFileCatalog.describeDataset(ds, "../outside.csv"));
   }

   @Test
   void applyQueryContractAlsoRejectsAnAbsolutePath_sharedLayerExactMessage() throws Exception {
      Files.writeString(new File(root, "ok.csv").toPath(), "a\n1\n");
      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> fill(Map.of(ServerFileCatalog.PARAM_FILE_FOLDER, "/etc/passwd")));
      assertTrue(ex.getMessage().contains("must be relative to the data source's root folder"));
   }

   @Test
   void applyQueryContractAlsoRejectsADotDotSegment_sharedLayerExactMessage() throws Exception {
      Files.writeString(new File(root, "ok.csv").toPath(), "a\n1\n");
      Exception ex = assertThrows(IllegalArgumentException.class,
         () -> fill(Map.of(ServerFileCatalog.PARAM_FILE_FOLDER, "../ok.csv")));
      assertTrue(ex.getMessage().contains("must not contain '..'"));
   }

   private void fill(Map<String, String> params) throws Exception {
      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      TabularQuerySchema schema =
         new TabularSchemaExtractor().extract(query, ServerFileDataSource.TYPE);
      Map<String, Object> queryParams = new HashMap<>(params);
      TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, ds.getName());
   }
}
