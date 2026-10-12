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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77538, a row-limited run of a crosstab must not drop the column header columns
 * that are missing from the sampled data.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetQueryCrosstabColumnsTest {
   @BeforeEach
   void setUp() {
      info = new AggregateInfo();
      info.setCrosstab(true);
      info.addGroup(new GroupRef(column("T", "fixed_field_code")));
      info.addGroup(new GroupRef(column("T", "base_code")));
      info.addAggregate(new AggregateRef(column("T", "fixed_field_value"), null));
      icolumns = selection(column("T", "fixed_field_code"), column("T", "base_code"),
                           column("T", "entry"), column("T", "fixed_field_value"));
   }

   @Test
   void keepsHeaderColumnMissingFromRun() {
      ColumnSelection ncolumns = selection(column("T", "base_code"), column(null, "00011"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), column(null, "00011"),
                                           column(null, "00022"));

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(3, ncolumns.getAttributeCount());
      assertNotNull(ncolumns.getAttribute("00022"));
   }

   @Test
   void doesNotDuplicateHeaderColumnsInRun() {
      ColumnSelection ncolumns = selection(column("T", "base_code"), column(null, "00011"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), column(null, "00011"));

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(2, ncolumns.getAttributeCount());
   }

   @Test
   void dropsRemovedRowHeaderColumn() {
      // entry is no longer a row header group, it has an entity so it isn't a header value
      ColumnSelection ncolumns = selection(column("T", "base_code"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), column("T", "entry"));

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(1, ncolumns.getAttributeCount());
      assertNull(ncolumns.getAttribute("T.entry"));
   }

   @Test
   void dropsExpressionColumn() {
      ColumnRef expression = new ColumnRef(new ExpressionRef(null, "calc"));
      ColumnSelection ncolumns = selection(column("T", "base_code"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), expression);

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(1, ncolumns.getAttributeCount());
   }

   @Test
   void dropsGroupColumnWithoutEntity() {
      info.addGroup(new GroupRef(column(null, "region")));
      ColumnSelection ncolumns = selection(column("T", "base_code"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), column(null, "region"));

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(1, ncolumns.getAttributeCount());
   }

   @Test
   void dropsRemovedRowHeaderColumnWithoutEntity() {
      // e.g. an embedded table, region was a row header and was removed from the groups
      icolumns.addAttribute(column(null, "region"));
      ColumnSelection ncolumns = selection(column("T", "base_code"));
      ColumnSelection ocolumns = selection(column("T", "base_code"), column(null, "region"));

      AssetQuery.keepCrosstabHeaderColumns(ncolumns, ocolumns, icolumns, info);

      assertEquals(1, ncolumns.getAttributeCount());
      assertNull(ncolumns.getAttribute("region"));
   }

   private static ColumnRef column(String entity, String attribute) {
      return new ColumnRef(new AttributeRef(entity, attribute));
   }

   private static ColumnSelection selection(ColumnRef... columns) {
      ColumnSelection selection = new ColumnSelection();

      for(ColumnRef column : columns) {
         selection.addAttribute(column);
      }

      return selection;
   }

   private AggregateInfo info;
   private ColumnSelection icolumns;
}
