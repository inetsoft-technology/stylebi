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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers charter A6 -- "the only assertion that proves the pipeline swap is usable". A
 * {@code describeDataset} result's {@code params} map must round-trip, unchanged, through
 * {@code TabularQueryContractSupport.applyQueryContract} into a query that builds the RIGHT
 * target (not merely A target). See {@link ServerFileSpringTestSupport}: schema extraction
 * unconditionally needs a {@code Config} Spring bean.
 */
class ServerFileCatalogParamsRoundTripTest {
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
      ds.setName("roundtrip-test-ds");
      ds.setFile(root);
   }

   @Test
   void paramsKeysAreRealSettablePropertiesOfServerFileQuery_derivedNotHardcoded() {
      // SKILL: a params key that is a hardcoded literal stays green after a getter rename that
      // would break the real caller. Derive the expected keys the same way
      // TabularQueryContractSupport itself does.
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      assertTrue(pmap.containsKey(ServerFileCatalog.PARAM_FILE_FOLDER));
      assertTrue(pmap.containsKey(ServerFileCatalog.PARAM_EXCEL_SHEET));
   }

   @Test
   void describedParamsRoundTrip_andBuildTheTargetTheIdActuallyNamed() throws Exception {
      File aDir = new File(root, "a");
      assertTrue(aDir.mkdirs());
      Files.writeString(new File(aDir, "1.csv").toPath(), "id,name\n1,foo\n");
      File bDir = new File(root, "b");
      assertTrue(bDir.mkdirs());
      Files.writeString(new File(bDir, "2.csv").toPath(), "region,total,notes\nEast,1,x\n");

      // Mutation check (A6-3): two DIFFERENT dataset ids from the same tree, each with a
      // DIFFERENT column set -- a params map that always points at the first-enumerated file (or
      // otherwise ignores which id was actually asked for) fails this, where a round trip that
      // only checks "the build succeeds" would not.
      assertRoundTrips("a/1.csv", List.of("id", "name"));
      assertRoundTrips("b/2.csv", List.of("region", "total", "notes"));
   }

   private void assertRoundTrips(String datasetId, List<String> expectedColumns) throws Exception {
      TabularDatasetSchema schema = ServerFileCatalog.describeDataset(ds, datasetId);
      assertEquals(datasetId, schema.datasetId());

      ServerFileQuery query = new ServerFileQuery();
      query.setDataSource(ds);

      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(ServerFileQuery.class);
      TabularQuerySchema querySchema =
         new TabularSchemaExtractor().extract(query, ServerFileDataSource.TYPE);
      Map<String, Object> queryParams = new HashMap<>(schema.params());

      TabularQueryContractSupport.applyQueryContract(
         query, pmap, querySchema, queryParams, ds.getName());

      ColumnDefinition[] columns = query.getColumns();
      assertNotNull(columns, "query.getColumns() must self-heal from fileFolder/excelSheet alone");
      List<String> actual = Arrays.stream(columns)
         .map(ColumnDefinition::getName).collect(Collectors.toList());
      assertEquals(expectedColumns, actual);
   }
}
