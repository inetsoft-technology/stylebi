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

package inetsoft.web.wiz.controller;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.*;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.model.WizCubeSettingsResult;
import inetsoft.web.wiz.request.WizCubeSettingsRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Portal's "OLAP Data" settings of a cube, applied to the stored objects: Keep Original Order
 * per dimension (hierarchy), As Date, Database Locale and Parse Pattern per level. Every name is
 * checked against the cube first and a bad request leaves the cube as it was.
 */
@Tag("core")
class WizCubeSettingsPatcherTest {
   private static final String SOURCE = "OLAP/FoodMart";

   // ---- Keep Original Order ----------------------------------------------------------------

   @Test
   void keepOriginalOrder_withoutAHierarchy_setsEveryHierarchyOfTheDimension() {
      Cube cube = cube();

      WizCubeSettingsResult result = WizCubeSettingsPatcher.apply(cube, SOURCE, dims(
         new WizCubeSettingsRequest.Dimension("Product", null, true)));

      assertEquals(2, result.dimensions().size());
      assertTrue(result.dimensions().stream().allMatch(
         WizCubeSettingsResult.Dimension::originalOrder));
      assertTrue(hierarchy(cube, "Product", 0).originalOrder());
      assertTrue(hierarchy(cube, "Product", 1).originalOrder());
      assertFalse(hierarchy(cube, "Store", 0).originalOrder(), "an unnamed dimension changed");
   }

   @Test
   void keepOriginalOrder_onATimeDimension_isRefusedBecauseItIsAlwaysOn() {
      Cube cube = cube();

      // positive control: a time dimension already reads as keeping the order, and a stored
      // false would not change that, so a "success" here would be a silent no-op
      assertTrue(hierarchy(cube, "Time", 0).originalOrder());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, dims(
            new WizCubeSettingsRequest.Dimension("Time", null, false))));

      assertTrue(ex.getMessage().startsWith("dimensions[0].keepOriginalOrder: dimension \"Time\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("is a time dimension"), ex.getMessage());
      assertTrue(hierarchy(cube, "Time", 0).originalOrder());
   }

   @Test
   void keepOriginalOrder_withAHierarchy_setsOnlyThatOneAndCanSwitchItOff() {
      Cube cube = cube();
      String brand = hierarchy(cube, "Product", 1).entity();

      WizCubeSettingsPatcher.apply(cube, SOURCE, dims(
         new WizCubeSettingsRequest.Dimension("Product", brand, true)));

      assertFalse(hierarchy(cube, "Product", 0).originalOrder());
      assertTrue(hierarchy(cube, "Product", 1).originalOrder());

      WizCubeSettingsPatcher.apply(cube, SOURCE, dims(
         new WizCubeSettingsRequest.Dimension("Product", brand, false)));

      assertFalse(hierarchy(cube, "Product", 1).originalOrder());
   }

   @Test
   void keepOriginalOrder_needsAValue() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube(), SOURCE, dims(
            new WizCubeSettingsRequest.Dimension("Product", null, null))));

      assertTrue(ex.getMessage().contains("dimensions[0].keepOriginalOrder"), ex.getMessage());
   }

   @Test
   void unknownDimension_namesTheFieldAndTheValidDimensions() {
      Cube cube = cube();
      boolean before = hierarchy(cube, "Product", 0).originalOrder();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, dims(
            new WizCubeSettingsRequest.Dimension("Tiem", null, true))));

      assertTrue(ex.getMessage().startsWith("dimensions[0].dimension: no dimension \"Tiem\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid dimensions: [Product, Store, Time]"),
                 ex.getMessage());
      assertEquals(before, hierarchy(cube, "Product", 0).originalOrder());
   }

   @Test
   void unknownHierarchy_namesTheFieldAndTheValidHierarchies() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube(), SOURCE, dims(
            new WizCubeSettingsRequest.Dimension("Product", "Nope", true))));

      assertTrue(ex.getMessage().startsWith("dimensions[0].hierarchy: no hierarchy \"Nope\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid hierarchies: ["), ex.getMessage());
   }

   // ---- date settings ----------------------------------------------------------------------

   @Test
   void dateSettings_areStoredOnTheLevelAndReadBack() {
      Cube cube = cube();
      String weekly = hierarchy(cube, "Time", 1).entity();

      WizCubeSettingsResult result = WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
         new WizCubeSettingsRequest.Level("Time", weekly, "Week", true, "en_US", "yyyy-MM-dd")));

      WizCubeSettingsResult.Level level = result.levels().get(0);
      assertEquals("Week", level.level());
      assertTrue(level.asDate());
      assertEquals("en_US", level.locale());
      assertEquals("yyyy-MM-dd", level.datePattern());

      XMetaInfo meta = ((DimMember) dimensionByEntity(cube, weekly).getLevelAt(1)).getXMetaInfo();
      assertTrue(meta.isAsDate());
      assertEquals(new Locale("en", "US"), meta.getLocale());
      assertEquals("yyyy-MM-dd", meta.getDatePattern());
      // what the stored XML says is what reads back, as `new Locale("en_US")` would not
      assertEquals("en_US", XMetaInfo.formatLocale(meta.getLocale()));
      assertEquals(meta.getLocale(), XMetaInfo.getLocale(XMetaInfo.formatLocale(meta.getLocale())));
   }

   @Test
   void dateSettings_keepWhatTheRequestDoesNotNameIncludingFormatAndDrillInfo() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();
      DimMember year = (DimMember) dimensionByEntity(cube, time).getLevelAt(0);
      XMetaInfo existing = new XMetaInfo();
      existing.setProperty("keep", "me");
      existing.setAsDate(true);
      existing.setDatePattern("yyyy");
      year.setXMetaInfo(existing);

      WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
         new WizCubeSettingsRequest.Level("Time", time, "Year", null, "fr", null)));

      XMetaInfo meta = year.getXMetaInfo();
      assertEquals("me", meta.getProperty("keep"), "a property the request did not name was lost");
      assertTrue(meta.isAsDate());
      assertEquals("yyyy", meta.getDatePattern());
      assertEquals(new Locale("fr"), meta.getLocale());
   }

   @Test
   void emptyLocaleAndPattern_clearThem() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();
      WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
         new WizCubeSettingsRequest.Level("Time", time, "Year", true, "de", "yyyy")));

      WizCubeSettingsResult result = WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
         new WizCubeSettingsRequest.Level("Time", time, "Year", null, "", "")));

      assertNull(result.levels().get(0).locale());
      assertNull(result.levels().get(0).datePattern());
      assertTrue(result.levels().get(0).asDate(), "clearing locale and pattern turned As Date off");
   }

   @Test
   void localeSyntax_isWhatThePortalOffersPlusIsoTags() {
      assertEquals(new Locale("en"), WizCubeSettingsPatcher.parseLocale("f", "en"));
      assertEquals(new Locale("jp"), WizCubeSettingsPatcher.parseLocale("f", "jp"));
      assertEquals(new Locale("en", "CA"), WizCubeSettingsPatcher.parseLocale("f", "en_CA"));
      assertEquals(new Locale("en", "US"), WizCubeSettingsPatcher.parseLocale("f", "en-US"));
      assertEquals(new Locale("zh", "CN"), WizCubeSettingsPatcher.parseLocale("f", "ZH_cn"));
   }

   @Test
   void unknownLocale_isRefusedNamingTheFieldAndTheSyntax() {
      for(String bad : new String[] { "xx", "english", "en_XX", "en_US_POSIX", "e", "12" }) {
         Cube cube = cube();
         String time = hierarchy(cube, "Time", 0).entity();

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
               new WizCubeSettingsRequest.Level("Time", time, "Year", true, bad, null))),
            bad);

         assertTrue(ex.getMessage().contains("levels[0].locale: \"" + bad + "\""),
                    ex.getMessage());
         assertTrue(ex.getMessage().contains("en_US"), ex.getMessage());
         assertNull(((DimMember) dimensionByEntity(cube, time).getLevelAt(0)).getXMetaInfo(),
                    "a refused locale was saved: " + bad);
      }
   }

   @Test
   void badDatePattern_isRefusedNamingTheField() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
            new WizCubeSettingsRequest.Level("Time", time, "Year", true, null, "yyyy-QQ-{"))));

      assertTrue(ex.getMessage().startsWith("levels[0].datePattern: \"yyyy-QQ-{\""),
                 ex.getMessage());
   }

   @Test
   void localeOrPatternWithoutAsDate_isRefused() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
            new WizCubeSettingsRequest.Level("Time", time, "Year", null, null, "yyyy"))));

      assertTrue(ex.getMessage().contains("only apply when asDate is true"), ex.getMessage());
   }

   @Test
   void aLevelInSeveralHierarchies_needsTheHierarchyNamed() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();
      String weekly = hierarchy(cube, "Time", 1).entity();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
            new WizCubeSettingsRequest.Level("Time", null, "Year", true, null, null))));

      assertTrue(ex.getMessage().startsWith("levels[0].hierarchy: level \"Year\" exists in " +
                                               "several hierarchies"), ex.getMessage());
      assertTrue(ex.getMessage().contains(time + " (Year>Quarter>Month)"), ex.getMessage());
      assertTrue(ex.getMessage().contains(time) && ex.getMessage().contains(weekly),
                 ex.getMessage());

      // the same level, hierarchy named, is fine; and a level of one hierarchy needs none
      WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
         new WizCubeSettingsRequest.Level("Time", time, "Year", true, null, null),
         new WizCubeSettingsRequest.Level("Time", null, "Week", true, null, null)));
   }

   @Test
   void unknownLevel_namesTheFieldAndTheValidLevels() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube, SOURCE, levels(
            new WizCubeSettingsRequest.Level("Time", time, "Decade", true, null, null))));

      assertTrue(ex.getMessage().startsWith("levels[0].level: no level \"Decade\""),
                 ex.getMessage());
      assertTrue(ex.getMessage().contains("valid levels: [Year, Quarter, Month]"),
                 ex.getMessage());
   }

   @Test
   void aLevelEntryThatSetsNothing_isRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube(), SOURCE, levels(
            new WizCubeSettingsRequest.Level("Time", null, "Week", null, null, null))));

      assertTrue(ex.getMessage().startsWith("levels[0]: nothing to set"), ex.getMessage());
   }

   @Test
   void anEmptyRequest_isRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizCubeSettingsPatcher.apply(cube(), SOURCE, new WizCubeSettingsRequest(
            SOURCE, "Sales", List.of(), List.of())));

      assertTrue(ex.getMessage().contains("nothing to change"), ex.getMessage());
   }

   @Test
   void aBadSecondEntry_leavesTheFirstUnapplied() {
      Cube cube = cube();
      String time = hierarchy(cube, "Time", 0).entity();

      assertThrows(IllegalArgumentException.class, () -> WizCubeSettingsPatcher.apply(
         cube, SOURCE, new WizCubeSettingsRequest(
            SOURCE, "Sales",
            List.of(new WizCubeSettingsRequest.Dimension("Product", null, true)),
            List.of(new WizCubeSettingsRequest.Level("Time", time, "Year", true, "zz", null)))));

      assertFalse(hierarchy(cube, "Product", 0).originalOrder(),
                  "the dimension change of a refused request was applied");
      assertNull(((DimMember) dimensionByEntity(cube, time).getLevelAt(0)).getXMetaInfo());
   }

   // ---- helpers ----------------------------------------------------------------------------

   private static WizCubeSettingsRequest dims(WizCubeSettingsRequest.Dimension... dimensions) {
      return new WizCubeSettingsRequest(SOURCE, "Sales", List.of(dimensions), null);
   }

   private static WizCubeSettingsRequest levels(WizCubeSettingsRequest.Level... levels) {
      return new WizCubeSettingsRequest(SOURCE, "Sales", null, List.of(levels));
   }

   private static WizCubeDetails.Hierarchy hierarchy(Cube cube, String dimension, int index) {
      WizCubeDetails details = WizCubeController.describe(SOURCE, cube);

      return details.dimensions().stream().filter(d -> d.name().equals(dimension)).findFirst()
         .orElseThrow().hierarchies().get(index);
   }

   private static Dimension dimensionByEntity(Cube cube, String entity) {
      for(Enumeration<?> e = cube.getDimensions(); e.hasMoreElements();) {
         Dimension dim = (Dimension) e.nextElement();

         if(dim.getName().equals(entity)) {
            return dim;
         }
      }

      throw new AssertionError("no dimension " + entity);
   }

   private static Cube cube() {
      Cube cube = new Cube();
      cube.setName("Sales");
      cube.setCaption("Sales");
      HierDimension time = hierarchy("Time", "[Time]", "Time", DataRef.CUBE_TIME_DIMENSION,
                                     "Year", "Quarter", "Month");
      HierDimension weekly = hierarchy("Time", "[Time.Weekly]", "Weekly",
                                       DataRef.CUBE_TIME_DIMENSION, "Year", "Week");
      HierDimension store = hierarchy("Store", "[Store]", "Store", DataRef.CUBE_DIMENSION,
                                      "Country");
      HierDimension product = hierarchy("Product", "[Product]", "Product", DataRef.CUBE_DIMENSION,
                                        "Family", "Name");
      HierDimension brand = hierarchy("Product", "[Product.ByBrand]", "By Brand",
                                      DataRef.CUBE_DIMENSION, "Brand");
      cube.setDimensions(List.of(time, weekly, store, product, brand));
      Measure units = new Measure();
      units.setName("Unit Sales");
      units.setUniqueName("Unit Sales");
      units.setCaption("Unit Sales");
      units.setType("double");
      cube.setMeasures(List.of(units));

      return cube;
   }

   private static HierDimension hierarchy(String parent, String uniqueName, String caption,
                                          int type, String... levels)
   {
      HierDimension dim = new HierDimension();
      dim.setDimensionName(parent);
      dim.setHierarchyUniqueName(uniqueName);
      dim.setHierarchyName(caption);
      dim.setHierCaption(caption);
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
