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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.*;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * B16: the {@code excelSheetName} scalar alias (see {@code OneDriveQuery}'s own javadoc on it) means
 * one backing field has two writable properties. This pins what happens when a caller supplies BOTH
 * {@code excelSheet} and {@code excelSheetName} in one {@code queryParams} map -- deterministically,
 * not left to this class's own {@code @View} declaration order (03-reconcile.md §4/G3).
 */
@Tag("core")
class OneDriveQueryExcelSheetAliasTest {
   @BeforeAll
   static void installContext() {
      previous = OneDriveTestSupport.installMockContext();
   }

   @AfterAll
   static void clearContext() {
      OneDriveTestSupport.clearContext(previous);
   }

   private static ConfigurationContext previous;

   private static String apply(OneDriveQuery query, Map<String, Object> queryParams) throws Exception {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, query.getType());
      return inetsoft.web.wiz.service.TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, "myds");
   }

   /**
    * {@code excelSheet} can only pass its own {@code tagsMethod} validation with the value {@code
    * ""} on a fresh query (P-2: {@code getExcelSheetNames()} answers {@code [""]} until a file has
    * been downloaded) -- so this is the only combination where BOTH properties can be written in
    * one call without one of them throwing first. {@code getFile()} is stubbed to fail instantly
    * ({@code setExcelSheet}'s own {@code loadColumns()} call swallows the exception, per its
    * source) so this never attempts a real Graph call.
    */
   @Test
   void whenBothAreSupplied_theAliasWins_deterministically() throws Exception {
      OneDriveQuery query = spy(new OneDriveQuery());
      doAnswer(invocation -> {
         throw new IOException("no live Graph in this test");
      }).when(query).getFile();

      apply(query, Map.of(
         "path", "Test/TestExcelSingleSheet.xlsx",
         "excelSheet", "",
         "excelSheetName", "Data"));

      assertEquals("Data", query.getExcelSheet());
      assertEquals("Data", query.getExcelSheetName());
   }

   /**
    * The STRUCTURAL reason the alias always wins: {@code TabularSchemaExtractor.extract} places
    * every {@code @View}-referenced param (including {@code excelSheet}) before every param it does
    * not reference (the alias), and {@code TabularQueryContractSupport}'s fill order follows that
    * same list -- so this is not an accident of THIS class's {@code @View} layout, and is true
    * regardless of how {@code @View} is ever reordered in the future.
    */
   @Test
   void theAliasIsAnUnreferencedParam_soItAlwaysSortsAfterEveryViewParam() {
      TabularQuerySchema schema =
         new TabularSchemaExtractor().extract(new OneDriveQuery(), OneDriveDataSource.TYPE);

      assertTrue(schema.getUnreferencedParams().contains("excelSheetName"));
      assertFalse(schema.getUnreferencedParams().contains("excelSheet"));

      int excelSheetIndex = indexOf(schema, "excelSheet");
      int excelSheetNameIndex = indexOf(schema, "excelSheetName");
      assertTrue(excelSheetIndex >= 0 && excelSheetNameIndex >= 0);
      assertTrue(excelSheetNameIndex > excelSheetIndex,
         "the alias must always be ordered AFTER excelSheet in the schema's own params list");
   }

   private static int indexOf(TabularQuerySchema schema, String name) {
      List<TabularQuerySchema.Param> params = schema.getParams();

      for(int i = 0; i < params.size(); i++) {
         if(name.equals(params.get(i).getName())) {
            return i;
         }
      }

      return -1;
   }

   // ----- the alias setter must not normalise (SKILL's scalar-alias-pattern constraint) -----

   @Test
   void setterDoesNotNormalise_getReturnsExactlyWhatWasSet() {
      OneDriveQuery query = new OneDriveQuery();
      query.setExcelSheetName("  Data With Spaces  ");
      assertEquals("  Data With Spaces  ", query.getExcelSheetName());
   }

   @Test
   void setterDoesNotTriggerADownload_unlikeSetExcelSheet() {
      OneDriveQuery query = spy(new OneDriveQuery());
      query.setExcelSheetName("Data");
      // setExcelSheetName must NOT call loadColumns()/getFile() -- that is what costs a download
      // (P-14). Confirmed by never stubbing getFile() at all: a real call would throw
      // (no live Graph client, no data source configured) and, unlike setExcelSheet, there is no
      // surrounding try/catch to swallow it -- so an unswallowed exception here would mean this
      // setter is doing more than assigning the field.
      assertEquals("Data", query.getExcelSheetName());
   }
}
