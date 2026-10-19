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
import static org.junit.jupiter.api.Assertions.assertNull;

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

      VSDimensionRef nextRef = VSUtil.getCubeNextLevelRef(regionRef, cube);

      assertNotNull(nextRef);
      assertEquals("Customer.City", nextRef.getGroupColumnValue());
   }

   // Bug #76861: a non-date member's date option never has to equal the ref's level (a runtime
   // non-date ref reports 5 by default), whatever option was written. A date member still has to.
   @Test
   void nonDateMemberMatchesWhateverItsOptionIs() {
      for(int option : new int[] { DateRangeRef.NONE_INTERVAL, DateRangeRef.YEAR_INTERVAL }) {
         VSCube cube = regionCityCube(XSchema.STRING, option);

         for(int level : new int[] { DateRangeRef.NONE_INTERVAL, DateRangeRef.YEAR_INTERVAL }) {
            VSDimensionRef ref = new VSDimensionRef(new AttributeRef("Region"));
            ref.setDateLevel(level);

            assertEquals("+", VSUtil.getCrosstabDrillOp(cube, ref, new DataRef[] { ref }));
            assertNotNull(VSUtil.getCubeNextLevelRef(ref, cube));
         }
      }
   }

   @Test
   void dateMemberStillMatchesOnlyItsOwnLevel() {
      VSCube cube = regionCityCube(XSchema.DATE, DateRangeRef.YEAR_INTERVAL);
      VSDimensionRef year = new VSDimensionRef(new AttributeRef("Region"));
      year.setDateLevel(DateRangeRef.YEAR_INTERVAL);
      VSDimensionRef month = new VSDimensionRef(new AttributeRef("Region"));
      month.setDateLevel(DateRangeRef.MONTH_INTERVAL);

      assertNotNull(VSUtil.getCubeNextLevelRef(year, cube));
      assertNull(VSUtil.getCubeNextLevelRef(month, cube));
   }

   // Bug #78684: a TIME column repeated at hour and minute is a date member too, so the
   // runtime ref level has to pick the member (the bare name alone matches both).
   @Test
   void timeMemberRepeatedAtHourAndMinuteKeepsExactLevel() {
      VSDimension dimension = new VSDimension();
      dimension.setName("TimeHierarchy");

      for(int option : new int[] { DateRangeRef.HOUR_INTERVAL, DateRangeRef.MINUTE_INTERVAL }) {
         AttributeRef attr = new AttributeRef("StartTime");
         attr.setDataType(XSchema.TIME);
         VSDimensionMember member = new VSDimensionMember();
         member.setDataRef(attr);
         member.setDateOption(option);
         dimension.addLevel(member);
      }

      assertEquals(0, VSUtil.getScope("StartTime", dimension, DateRangeRef.HOUR_INTERVAL));
      assertEquals(1, VSUtil.getScope("StartTime", dimension, DateRangeRef.MINUTE_INTERVAL));
   }

   private VSCube regionCityCube(String dataType, int option) {
      VSDimension dimension = new VSDimension();
      dimension.setName("RegionCityHierarchy");

      for(String name : new String[] { "Region", "City" }) {
         AttributeRef attr = new AttributeRef(name);
         attr.setDataType(dataType);
         VSDimensionMember member = new VSDimensionMember();
         member.setDataRef(attr);
         member.setDateOption(option);
         dimension.addLevel(member);
      }

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
