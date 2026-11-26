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

package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.*;
import inetsoft.web.composer.ws.WorksheetOpenAssetService;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code add_cube_table}: a worksheet table from an XMLA cube. Levels only or measures only are
 * built exactly as a Composer drop of the same tree entries builds them; levels and measures
 * together (which a drop cannot hold) are stored as {@code SourceInfo.CUBE} with every column kept;
 * every name is checked against the cube and a bad one builds nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WorksheetCubeTableBuilderTest {
   private static final String DS = "OLAP/FoodMart";

   private static Worksheet worksheet() {
      return new Worksheet();
   }

   // ---- native-equal modes -----------------------------------------------------------------

   @Test
   void levelsOnly_areBuiltAsADropOfTheSameEntriesBuildsThem() throws Exception {
      Worksheet ws = worksheet();
      Checks checks = new Checks();

      AbstractTableAssembly table = build(ws, checks, "Sales",
         levels(level("Time", "Year"), level("Time", "Quarter"), level("Time", "Month")),
         List.of(), "ByTime");

      AbstractTableAssembly drop = drop(ws, "Time", "Year", "Quarter", "Month");

      assertInstanceOf(CubeTableAssembly.class, table);
      assertEquals(List.of("Year", "Quarter", "Month"), attributes(table));
      assertEquals(attributes(drop), attributes(table));
      assertEquals(refTypes(drop), refTypes(table), "ref types differ from the drop's");
      assertEquals(dataTypes(drop), dataTypes(table));
      assertEquals(drop.getSourceInfo().getType(), table.getSourceInfo().getType());
      assertEquals(DS, table.getSourceInfo().getPrefix());
      assertEquals("Sales", table.getSourceInfo().getSource());
      assertEquals(drop.getSourceInfo().getProperty("level_number"),
                   table.getSourceInfo().getProperty("level_number"));
      assertNotEquals(SourceInfo.CUBE, table.getSourceInfo().getType(),
                      "levels of one kind should keep the data ref type a drop stores");
      assertEquals("ByTime", table.getName());
      assertEquals(1, checks.calls.size());
   }

   @Test
   void measuresOnly_areBuiltAsADropOfTheSameEntriesBuildsThem() throws Exception {
      Worksheet ws = worksheet();

      AbstractTableAssembly table = build(ws, new Checks(), "Sales", List.of(),
                                          List.of("Unit Sales", "Profit"), null);
      AbstractTableAssembly drop = dropMeasures(ws, "Unit Sales", "Profit");

      assertEquals(List.of("Unit Sales", "Profit"), attributes(table));
      assertEquals(refTypes(drop), refTypes(table));
      assertEquals(DataRef.CUBE_MEASURE, table.getSourceInfo().getType());
      assertEquals(drop.getSourceInfo().getType(), table.getSourceInfo().getType());
      assertEquals("Sales", table.getName(), "the default name is the cube's, as a drop names it");
   }

   // ---- mixed mode -------------------------------------------------------------------------

   @Test
   void levelsAndMeasures_areOneTableStoredAsCubeWithEveryColumn() throws Exception {
      Worksheet ws = worksheet();

      AbstractTableAssembly table = build(ws, new Checks(), "Sales",
         levels(level("Time", "Year"), level("Time", "Quarter"), level("Time", "Month")),
         List.of("Unit Sales"), "UnitSalesByTime");

      assertEquals(List.of("Year", "Quarter", "Month", "Unit Sales"), attributes(table));
      assertEquals(SourceInfo.CUBE, table.getSourceInfo().getType());
      assertEquals(DS, table.getSourceInfo().getPrefix());
      assertEquals("Sales", table.getSourceInfo().getSource());
      List<Integer> types = refTypes(table);
      assertEquals(DataRef.CUBE_TIME_DIMENSION, types.get(0) & DataRef.CUBE_TIME_DIMENSION);
      assertEquals(DataRef.CUBE_MEASURE, types.get(3) & DataRef.CUBE_MEASURE,
                   "the measure column is not typed as a measure");
      assertFalse(table.getName().startsWith(Assembly.CUBE_VS),
                  "a saved worksheet may not carry the reserved cube table prefix");
      assertTrue(new HashSet<>(dependedNames(table)).stream()
                    .noneMatch(n -> n.startsWith(Assembly.CUBE_VS)));
   }

   @Test
   void positiveControl_aPlainDropOfTheSameMixedEntriesSilentlyKeepsOnlyOneKind() throws Exception {
      // the reason the builder has a mixed mode: the drop code filters on the first entry's type
      Worksheet ws = worksheet();
      List<AssetEntry> entries = new ArrayList<>();
      entries.addAll(treeEntries(ws, "Time", "Year", "Quarter", "Month"));
      entries.addAll(measureEntries("Unit Sales"));

      AbstractTableAssembly plain = WorksheetOpenAssetService.createTable(
         ws, null, null, entries.toArray(new AssetEntry[0]), new Point(0, 0), s -> { },
         null, null);

      assertEquals(List.of("Year", "Quarter", "Month"), attributes(plain),
                   "the drop path was expected to discard the measure");

      AbstractTableAssembly mixed = WorksheetOpenAssetService.createTable(
         ws, null, null, entries.toArray(new AssetEntry[0]), new Point(0, 0), s -> { },
         SourceInfo.CUBE, null);

      assertEquals(List.of("Year", "Quarter", "Month", "Unit Sales"), attributes(mixed));
   }

   @Test
   void timeAndModelDimensionsTogether_alsoUseTheCubeType() throws Exception {
      AbstractTableAssembly table = build(worksheet(), new Checks(), "Sales",
         levels(level("Time", "Year"), level("Product", "Family")), List.of(), null);

      assertEquals(List.of("Year", "Family"), attributes(table));
      assertEquals(SourceInfo.CUBE, table.getSourceInfo().getType());
   }

   // ---- names ------------------------------------------------------------------------------

   @Test
   void anUnknownDimension_namesTheFieldAndTheValidDimensions() {
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales",
                     levels(level("Tiem", "Year")), List.of(), null));

      assertTrue(ex.getMessage().startsWith("levels[0].dimension: no dimension \"Tiem\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid dimensions: [Product, Time]"), ex.getMessage());
   }

   @Test
   void anUnknownLevel_namesTheFieldAndTheValidLevels() {
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales",
                     levels(level("Time", "Year"), bare("Time", "Decade")), List.of(), null));

      assertTrue(ex.getMessage().startsWith("levels[1].level: no level \"Decade\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid levels: [Year, Quarter, Month, Week, Day]"),
                 ex.getMessage());
   }

   @Test
   void anUnknownMeasure_namesTheFieldAndTheValidMeasures() {
      Worksheet ws = worksheet();

      PairingException ex = assertThrows(PairingException.class,
         () -> build(ws, new Checks(), "Sales", levels(level("Time", "Year")),
                     List.of("Unit Sales", "Revenue"), null));

      assertTrue(ex.getMessage().startsWith("measures[1]: no measure \"Revenue\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid measures: [Profit, Unit Sales]"),
                 ex.getMessage());
      assertEquals(0, ws.getAssemblies().length, "a refused request built something");
   }

   @Test
   void aLevelInSeveralHierarchies_needsTheHierarchyNamed() throws Exception {
      Cube cube = cube();
      String monthly = hierarchyEntity(cube, 0);
      String weekly = hierarchyEntity(cube, 1);

      // FoodMart's Time has Year in both of its hierarchies
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales",
                     levels(bare("Time", "Year")), List.of(), null));

      assertTrue(ex.getMessage().startsWith("levels[0].hierarchy: level \"Year\" exists in " +
                                               "several hierarchies"), ex.getMessage());
      assertTrue(ex.getMessage().contains(monthly + " (Year>Quarter>Month)") &&
                    ex.getMessage().contains(weekly + " (Year>Week>Day)"), ex.getMessage());

      AbstractTableAssembly table = build(worksheet(), new Checks(), "Sales",
         List.of(new WorksheetMutationSupport.CubeLevelSpec("Time", weekly, "Year")),
         List.of(), null);
      assertEquals(List.of("Year"), attributes(table));
      assertEquals(weekly, ((ColumnRef) table.getColumnSelection().getAttribute(0)).getEntity());
   }

   @Test
   void aCaptionOrDifferentCaseIsAccepted() throws Exception {
      AbstractTableAssembly table = build(worksheet(), new Checks(), "sales",
         List.of(new WorksheetMutationSupport.CubeLevelSpec(
            "time", hierarchyEntity(cube(), 0), "year")), List.of("unit sales"), null);

      assertEquals(List.of("Year", "Unit Sales"), attributes(table));
   }

   @Test
   void aRepeatedColumnIsRefused() {
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales", List.of(),
                     List.of("Unit Sales", "Unit Sales"), null));

      assertTrue(ex.getMessage().contains("measures[1]") &&
                    ex.getMessage().contains("listed twice"), ex.getMessage());
   }

   @Test
   void nothingRequested_isRefused() {
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales", List.of(), List.of(), null));

      assertTrue(ex.getMessage().contains("at least one of levels"), ex.getMessage());
   }

   @Test
   void theReservedCubePrefixIsNotAcceptedAsATableName() {
      PairingException ex = assertThrows(PairingException.class,
         () -> build(worksheet(), new Checks(), "Sales", List.of(), List.of("Unit Sales"),
                     "___inetsoft_cube_OLAP/FoodMart/Sales"));

      assertTrue(ex.getMessage().startsWith("table: "), ex.getMessage());
      assertTrue(ex.getMessage().contains("___inetsoft_cube_"), ex.getMessage());
   }

   @Test
   void aTakenNameGetsTheNextFreeOne() throws Exception {
      Worksheet ws = worksheet();
      AbstractTableAssembly first = build(ws, new Checks(), "Sales", List.of(),
                                          List.of("Unit Sales"), "Sales");
      ws.addAssembly(first);

      AbstractTableAssembly second = build(ws, new Checks(), "Sales", List.of(),
                                           List.of("Profit"), "Sales");

      assertEquals("Sales", first.getName());
      assertNotEquals("Sales", second.getName());
      assertTrue(second.getName().startsWith("Sales"));
   }

   @Test
   void theSourcePermissionCheckRunsBeforeAnythingIsReturned() {
      Checks checks = new Checks();
      checks.deny = true;

      assertThrows(SecurityException.class,
         () -> build(worksheet(), checks, "Sales", List.of(), List.of("Unit Sales"), null));
      assertEquals(1, checks.calls.size());
   }

   // ---- helpers ----------------------------------------------------------------------------

   private static final class Checks implements WorksheetOpenAssetService.SourceCheck {
      @Override
      public void check(SourceInfo sinfo) {
         calls.add(sinfo);

         if(deny) {
            throw new SecurityException("denied");
         }
      }

      final List<SourceInfo> calls = new ArrayList<>();
      boolean deny;
   }

   private static AbstractTableAssembly build(Worksheet ws, Checks checks, String cubeName,
                                              List<WorksheetMutationSupport.CubeLevelSpec> levels,
                                              List<String> measures, String name)
      throws Exception
   {
      Cube cube = cube();
      Cube target = WizCubeResolve.resolve(cube, cubeName);

      return WorksheetCubeTableBuilder.build(ws, null, target, DS, levels, measures, name, checks);
   }

   /** The builder is given an already resolved cube; resolve by name or caption as the op does. */
   private static final class WizCubeResolve {
      static Cube resolve(Cube cube, String name) {
         return inetsoft.web.wiz.controller.WizCubeController.resolveCube(List.of(cube), name);
      }
   }

   /** A level, with its hierarchy named for Time (the usual Year, Quarter, Month one). */
   private static WorksheetMutationSupport.CubeLevelSpec level(String dimension, String level) {
      return new WorksheetMutationSupport.CubeLevelSpec(
         dimension, "Time".equals(dimension) ? hierarchyEntity(cube(), 0) : null, level);
   }

   private static WorksheetMutationSupport.CubeLevelSpec bare(String dimension, String level) {
      return new WorksheetMutationSupport.CubeLevelSpec(dimension, null, level);
   }

   private static List<WorksheetMutationSupport.CubeLevelSpec> levels(
      WorksheetMutationSupport.CubeLevelSpec... levels)
   {
      return List.of(levels);
   }

   private static List<AssetEntry> treeEntries(Worksheet ws, String dimension, String... names) {
      List<AssetEntry> leaves = new ArrayList<>();
      collect(AssetEventUtil.getCubeNode(cube(), DS, true, true, null, null), leaves);
      List<AssetEntry> result = new ArrayList<>();
      String entity = hierarchyEntity(cube(), 0);

      for(String name : names) {
         for(AssetEntry leaf : leaves) {
            if(entity.equals(leaf.getProperty("entity")) &&
               name.equals(leaf.getProperty("attribute")))
            {
               result.add(leaf);
            }
         }
      }

      assertEquals(names.length, result.size());

      return result;
   }

   private static List<AssetEntry> measureEntries(String... names) {
      List<AssetEntry> leaves = new ArrayList<>();
      collect(AssetEventUtil.getCubeNode(cube(), DS, true, true, null, null), leaves);
      List<AssetEntry> result = new ArrayList<>();

      for(String name : names) {
         for(AssetEntry leaf : leaves) {
            if((AssetEntry.MEASURES + "").equals(leaf.getProperty(AssetEntry.CUBE_COL_TYPE)) &&
               name.equals(leaf.getProperty("attribute")))
            {
               result.add(leaf);
            }
         }
      }

      assertEquals(names.length, result.size());

      return result;
   }

   private static void collect(AssetTreeModel.Node node, List<AssetEntry> leaves) {
      if(node.getEntry() != null && node.getEntry().isColumn()) {
         leaves.add(node.getEntry());
      }

      for(AssetTreeModel.Node child : node.getNodes()) {
         collect(child, leaves);
      }
   }

   /** What the Composer's drop builds from the same tree entries. */
   private static AbstractTableAssembly drop(Worksheet ws, String dimension, String... names)
      throws Exception
   {
      return WorksheetOpenAssetService.createTable(
         ws, null, null, treeEntries(ws, dimension, names).toArray(new AssetEntry[0]),
         new Point(0, 0), s -> { }, null, null);
   }

   private static AbstractTableAssembly dropMeasures(Worksheet ws, String... names)
      throws Exception
   {
      return WorksheetOpenAssetService.createTable(
         ws, null, null, measureEntries(names).toArray(new AssetEntry[0]), new Point(0, 0),
         s -> { }, null, null);
   }

   private static List<String> attributes(AbstractTableAssembly table) {
      List<String> names = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      return names;
   }

   private static List<Integer> refTypes(AbstractTableAssembly table) {
      List<Integer> types = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         types.add(columns.getAttribute(i).getRefType());
      }

      return types;
   }

   private static List<String> dataTypes(AbstractTableAssembly table) {
      List<String> types = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         types.add(columns.getAttribute(i).getDataType());
      }

      return types;
   }

   private static List<String> dependedNames(AbstractTableAssembly table) {
      Set<AssemblyRef> depends = new HashSet<>();
      table.getDependeds(depends);
      List<String> names = new ArrayList<>();

      for(AssemblyRef ref : depends) {
         names.add(ref.getEntry().getName());
      }

      return names;
   }

   private static String hierarchyEntity(Cube cube, int index) {
      return inetsoft.web.wiz.controller.WizCubeController.describe(DS, cube).dimensions()
         .stream().filter(d -> d.name().equals("Time")).findFirst().orElseThrow()
         .hierarchies().get(index).entity();
   }

   private static Cube cube() {
      Cube cube = new Cube();
      cube.setName("Sales");
      cube.setCaption("Sales");
      cube.setType("Mondrian");
      HierDimension time = hierarchy("Time", "[Time]", DataRef.CUBE_TIME_DIMENSION,
                                     "Year", "Quarter", "Month");
      HierDimension weekly = hierarchy("Time", "[Time.Weekly]", DataRef.CUBE_TIME_DIMENSION,
                                       "Year", "Week", "Day");
      HierDimension product = hierarchy("Product", "[Product]", DataRef.CUBE_DIMENSION,
                                        "Family", "Name");
      cube.setDimensions(List.of(time, weekly, product));
      cube.setMeasures(List.of(measure("Unit Sales", false), measure("Profit", true)));

      return cube;
   }

   private static Measure measure(String name, boolean calculated) {
      Measure measure = new Measure();
      measure.setName(name);
      measure.setUniqueName(name);
      measure.setCaption(name);
      measure.setType("double");
      measure.setCalcMeasure(calculated);

      return measure;
   }

   private static HierDimension hierarchy(String parent, String uniqueName, int type,
                                          String... levels)
   {
      HierDimension dim = new HierDimension();
      dim.setDimensionName(parent);
      dim.setHierarchyUniqueName(uniqueName);
      dim.setHierarchyName(parent);
      dim.setHierCaption(parent);
      dim.setParentCaption(parent);
      dim.setType(type);
      List<DimMember> members = new ArrayList<>();

      for(String level : levels) {
         DimMember member = new DimMember();
         member.setName(level);
         member.setUniqueName(level);
         member.setCaption(level);
         members.add(member);
      }

      dim.setLevels(members);

      return dim;
   }
}
