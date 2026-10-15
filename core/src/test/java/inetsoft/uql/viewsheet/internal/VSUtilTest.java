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

package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.composition.graph.calc.RunningTotalColumn;
import inetsoft.test.*;
import inetsoft.uql.XDimension;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSCube;
import inetsoft.uql.viewsheet.VSDimension;
import inetsoft.uql.viewsheet.VSDimensionMember;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Bug #76797
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSUtilTest {
   // breakBy defaulting from unset to its first-time ROW_INNER sentinel must not wipe an
   // explicitly-set resetLevel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // Same defaulting behavior for the column-inner sentinel.
   @Test
   void resetLevelIsPreservedWhenBreakByDefaultsToColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(false, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // breakBy already explicitly set to the sentinel matching the current shelf shape must
   // continue to short-circuit before the reset-clearing check (pre-existing behavior).
   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesRowInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.ROW_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, false, false, calc, null);

      assertEquals(AbstractCalc.ROW_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   @Test
   void resetLevelIsPreservedWhenBreakByAlreadyMatchesColumnInner() {
      RunningTotalCalc calc = new RunningTotalCalc();
      calc.setBreakBy(AbstractCalc.COLUMN_INNER);
      calc.setResetLevel(RunningTotalColumn.YEAR);

      VSUtil.updateCalculateInfo(true, true, false, calc, null);

      assertEquals(AbstractCalc.COLUMN_INNER, calc.getBreakBy());
      assertEquals(RunningTotalColumn.YEAR, calc.getResetLevel());
   }

   // Bug #76884: a custom VS hierarchy built from a joined/multi-table logical model stores
   // each member's name entity-qualified (e.g. "Customer.Region"), while the drill lookup
   // key is always the bare attribute (e.g. "Region"). getCubeDrillOp (icon visibility) must
   // still find the member so a drill icon renders.
   @Test
   void crosstabDrillOpFindsEntityQualifiedHierarchyMember() {
      VSCube cube = entityQualifiedRegionCityCube();
      VSDimensionRef regionRef = new VSDimensionRef(new AttributeRef("Region"));
      regionRef.setDateLevel(DateRangeRef.NONE_INTERVAL);

      String op = VSUtil.getCrosstabDrillOp(cube, regionRef, new DataRef[] {regionRef});

      assertEquals("+", op);
   }

   // Bug #76884: same entity-qualification mismatch, on the drill-click resolution path
   // (getCubeNextLevelRef) shared by both Chart's VSChartDrillHandler and Crosstab's
   // BaseTableDrillService. Without the fix, this returns null and a drill click silently
   // does nothing.
   @Test
   void cubeNextLevelRefResolvesEntityQualifiedHierarchyMember() {
      VSCube cube = entityQualifiedRegionCityCube();
      VSDimensionRef regionRef = new VSDimensionRef(new AttributeRef("Region"));
      regionRef.setDateLevel(DateRangeRef.NONE_INTERVAL);

      VSDimensionRef nextRef = VSUtil.getCubeNextLevelRef(regionRef, cube);

      assertNotNull(nextRef);
      assertEquals("Customer.City", nextRef.getGroupColumnValue());
   }

   // Bug #76884: production shapes. Member and shelf ref columns are VS-qualified
   // ("Customer:Region", no entity); the shelf ref carries the default level "-1" (resolves to
   // year) while a plugin-built member stores option NONE. A string member's option must not
   // gate the lookup.
   @Test
   void stringMemberOptionNoneIsFoundByDefaultLevelRef() {
      VSCube cube = vsQualifiedCube(DateRangeRef.NONE_INTERVAL, DateRangeRef.NONE_INTERVAL);
      VSDimensionRef regionRef = vsRegionRef("-1");

      assertEquals(DateRangeRef.YEAR_INTERVAL, regionRef.getDateLevel());
      assertEquals("+", VSUtil.getCrosstabDrillOp(cube, regionRef, new DataRef[] {regionRef}));

      VSDimensionRef nextRef = VSUtil.getCubeNextLevelRef(regionRef, cube);
      assertNotNull(nextRef);
      assertEquals("Customer:City", nextRef.getGroupColumnValue());
   }

   // A Composer-built hierarchy (option 5) keeps working with the default-level ref.
   @Test
   void stringMemberOptionYearIsFoundByDefaultLevelRef() {
      VSCube cube = vsQualifiedCube(DateRangeRef.YEAR_INTERVAL, DateRangeRef.YEAR_INTERVAL);
      VSDimensionRef regionRef = vsRegionRef("-1");

      assertEquals("+", VSUtil.getCrosstabDrillOp(cube, regionRef, new DataRef[] {regionRef}));
      assertNotNull(VSUtil.getCubeNextLevelRef(regionRef, cube));
   }

   // An explicit "none" ref against a Composer-built (option 5) string hierarchy.
   @Test
   void stringMemberOptionYearIsFoundByExplicitNoneRef() {
      VSCube cube = vsQualifiedCube(DateRangeRef.YEAR_INTERVAL, DateRangeRef.YEAR_INTERVAL);
      VSDimensionRef regionRef = vsRegionRef("0");

      assertEquals("+", VSUtil.getCrosstabDrillOp(cube, regionRef, new DataRef[] {regionRef}));
   }

   // An explicit "none" ref against a plugin-built (option 0) string hierarchy.
   @Test
   void stringMemberOptionNoneIsFoundByExplicitNoneRef() {
      VSCube cube = vsQualifiedCube(DateRangeRef.NONE_INTERVAL, DateRangeRef.NONE_INTERVAL);
      VSDimensionRef regionRef = vsRegionRef("0");

      assertEquals("+", VSUtil.getCrosstabDrillOp(cube, regionRef, new DataRef[] {regionRef}));
   }

   // Date members still need the exact level: on [Year, QuarterOfYear, MonthOfYear] over one
   // date column, the month ref resolves to index 2, not the first same-named member.
   @Test
   void partLevelDateHierarchyKeepsExactLevelMatching() {
      XDimension dim = dateDimension(XSchema.DATE,
                                     DateRangeRef.YEAR_INTERVAL,
                                     DateRangeRef.QUARTER_OF_YEAR_PART,
                                     DateRangeRef.MONTH_OF_YEAR_PART);

      assertEquals(0, VSUtil.getScope("Order Date", dim, DateRangeRef.YEAR_INTERVAL));
      assertEquals(1, VSUtil.getScope("Order Date", dim, DateRangeRef.QUARTER_OF_YEAR_PART));
      assertEquals(2, VSUtil.getScope("Order Date", dim, DateRangeRef.MONTH_OF_YEAR_PART));
   }

   // TIME columns are date-like for level matching: [Hour, Minute] resolves minute to index 1.
   @Test
   void timeHierarchyKeepsExactLevelMatching() {
      XDimension dim = dateDimension(XSchema.TIME,
                                     DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL);

      assertEquals(0, VSUtil.getScope("Order Date", dim, DateRangeRef.HOUR_INTERVAL));
      assertEquals(1, VSUtil.getScope("Order Date", dim, DateRangeRef.MINUTE_INTERVAL));
   }

   private XDimension dateDimension(String type, int... options) {
      VSDimension dimension = new VSDimension();
      dimension.setName("DateHierarchy");

      for(int option : options) {
         AttributeRef attr = new AttributeRef(null, "Order Date");
         attr.setDataType(type);
         ColumnRef column = new ColumnRef(attr);
         column.setDataType(type);
         VSDimensionMember member = new VSDimensionMember();
         member.setDataRef(column);
         member.setDateOption(option);
         dimension.addLevel(member);
      }

      return dimension;
   }

   private VSDimensionRef vsRegionRef(String dateLevelValue) {
      VSDimensionRef ref =
         new VSDimensionRef(new ColumnRef(new AttributeRef(null, "Customer:Region")));
      ref.setDateLevelValue(dateLevelValue);
      return ref;
   }

   private VSCube vsQualifiedCube(int regionOption, int cityOption) {
      VSDimensionMember regionMember = new VSDimensionMember();
      regionMember.setDataRef(new ColumnRef(new AttributeRef(null, "Customer:Region")));
      regionMember.setDateOption(regionOption);

      VSDimensionMember cityMember = new VSDimensionMember();
      cityMember.setDataRef(new ColumnRef(new AttributeRef(null, "Customer:City")));
      cityMember.setDateOption(cityOption);

      VSDimension dimension = new VSDimension();
      dimension.setName("RegionCityHierarchy");
      dimension.addLevel(regionMember);
      dimension.addLevel(cityMember);

      VSCube cube = new VSCube();
      cube.setDimensions(List.<XDimension>of(dimension));
      return cube;
   }

   private VSCube entityQualifiedRegionCityCube() {
      VSDimensionMember regionMember = new VSDimensionMember();
      regionMember.setDataRef(new AttributeRef("Customer", "Region"));
      regionMember.setDateOption(DateRangeRef.NONE_INTERVAL);

      VSDimensionMember cityMember = new VSDimensionMember();
      cityMember.setDataRef(new AttributeRef("Customer", "City"));
      cityMember.setDateOption(DateRangeRef.NONE_INTERVAL);

      VSDimension dimension = new VSDimension();
      dimension.setName("RegionCityHierarchy");
      dimension.addLevel(regionMember);
      dimension.addLevel(cityMember);

      VSCube cube = new VSCube();
      cube.setDimensions(List.<XDimension>of(dimension));
      return cube;
   }
}
