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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80946: the delimiter is derived from the file, so it must not be frozen to the "," fallback
 * by a read made before the file is set (the layout creator reads every property of the query
 * before the request's fileFolder is applied), and an omitted delimiter on an edit must not be
 * "reset" to that fallback.
 */
class ServerFileDelimiterDerivationTest {
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
   void setUp() throws Exception {
      ds = new ServerFileDataSource();
      ds.setName("delimiter-test-ds");
      ds.setFile(root);
      Files.writeString(new File(root, "suppliers.csv").toPath(),
         "S001;Acme;CN;Beijing;A\nS002;Bolt;US;Austin;B\nS003;Core;DE;Berlin;A\n" +
         "S004;Dyna;JP;Tokyo;C\nS005;Echo;FR;Paris;B\nS006;Flux;UK;London;A\n");
   }

   @Test
   void delimiterReadBeforeTheFileIsSetStillDetectsFromTheFile() {
      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);

      assertEquals(",", query.getDelimiter());
      query.setFileFolder(new File(root, "suppliers.csv"));

      assertEquals(";", query.getDelimiter());
      assertEquals(5, query.getColumns().length);
   }

   @Test
   void explicitDelimiterWinsOverDetection() {
      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      query.setDelimiter(",");
      query.setFileFolder(new File(root, "suppliers.csv"));

      assertEquals(",", query.getDelimiter());
   }

   @Test
   void addBuildsTheColumnsGetTableDetailsDescribes() throws Exception {
      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, "suppliers.csv");

      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      // the layout creator reads every property of the still file-less query, as add_table does
      TabularQuerySchema querySchema =
         new TabularSchemaExtractor().extract(query, ServerFileDataSource.TYPE);

      TabularQueryContractSupport.applyQueryContract(
         query, pmap, querySchema, new HashMap<>(schema.params()), ds.getName());

      assertEquals(schema.columns().size(), query.getColumns().length);
      assertEquals(5, query.getColumns().length);
   }

   @Test
   void omittedDelimiterOnEditKeepsTheDetectedOne() throws Exception {
      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);
      query.setFileFolder(new File(root, "suppliers.csv"));
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      TabularQuerySchema querySchema =
         new TabularSchemaExtractor().extract(query, ServerFileDataSource.TYPE);
      Map<String, Object> queryParams = new HashMap<>();
      queryParams.put(ServerFileCatalog.PARAM_FILE_FOLDER, "suppliers.csv");

      TabularQueryContractSupport.resetOmittedToDefaults(query, pmap, querySchema, queryParams);
      TabularQueryContractSupport.applyQueryContract(
         query, pmap, querySchema, queryParams, ds.getName());

      assertEquals(";", query.getDelimiter());
      assertEquals(5, query.getColumns().length);
   }
}
