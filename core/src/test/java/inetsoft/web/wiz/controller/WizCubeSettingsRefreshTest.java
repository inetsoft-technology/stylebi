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
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.request.WizCubeSettingsRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cube settings written by the wiz edit are kept by {@code refresh_data_source_metadata} while the
 * members' keys are unchanged: the refresh matches them with the keys the Portal editor uses, and
 * a member that is renamed in the endpoint starts again from the defaults.
 */
@Tag("core")
class WizCubeSettingsRefreshTest {
   private static final String SOURCE = "OLAP/FoodMart";

   @Test
   void editedSettingsSurviveARefreshWhileTheKeysAreUnchanged() {
      Cube stored = cube("Year", "Quarter");
      String product = hierarchyEntity(stored, "Product");
      String time = hierarchyEntity(stored, "Time");
      WizCubeSettingsPatcher.apply(stored, SOURCE, new WizCubeSettingsRequest(
         SOURCE, "Sales",
         List.of(new WizCubeSettingsRequest.Dimension("Product", null, true)),
         List.of(new WizCubeSettingsRequest.Level(
            "Time", time, "Year", true, "en_US", "yyyy"))));

      // what the endpoint returns on the next load: the same cube, no edits
      Cube freshCube = cube("Year", "Quarter");
      assertFalse(describe(freshCube).hierarchyOriginalOrder(product), "fixture is not fresh");
      Domain fresh = domain(freshCube);

      XmlaDatasourceService.mergeDomainMetadata(fresh, domain(stored));

      Cube merged = (Cube) fresh.getCube("Sales");
      assertTrue(describe(merged).hierarchyOriginalOrder(product),
                 "Keep Original Order was lost by the refresh");
      XMetaInfo meta = ((DimMember) dimension(merged, time).getLevelAt(0)).getXMetaInfo();
      assertNotNull(meta, "the level's settings were lost by the refresh");
      assertTrue(meta.isAsDate());
      assertEquals(new Locale("en", "US"), meta.getLocale());
      assertEquals("yyyy", meta.getDatePattern());
      assertNull(((DimMember) dimension(merged, time).getLevelAt(1)).getXMetaInfo(),
                 "a level that was never edited gained settings");
   }

   @Test
   void aRenamedMemberStartsAgainFromTheDefaults() {
      Cube stored = cube("Year", "Quarter");
      String time = hierarchyEntity(stored, "Time");
      WizCubeSettingsPatcher.apply(stored, SOURCE, new WizCubeSettingsRequest(
         SOURCE, "Sales", null,
         List.of(new WizCubeSettingsRequest.Level("Time", time, "Year", true, "fr", "yyyy"))));

      Cube renamed = cube("Fiscal Year", "Quarter");
      Domain fresh = domain(renamed);

      XmlaDatasourceService.mergeDomainMetadata(fresh, domain(stored));

      assertNull(((DimMember) dimension((Cube) fresh.getCube("Sales"), time).getLevelAt(0))
                    .getXMetaInfo(), "the edit followed a member that has another key now");
   }

   // ---- helpers ----------------------------------------------------------------------------

   private record Described(WizCubeDetails details) {
      boolean hierarchyOriginalOrder(String entity) {
         return details.dimensions().stream().flatMap(d -> d.hierarchies().stream())
            .filter(h -> h.entity().equals(entity)).findFirst().orElseThrow().originalOrder();
      }
   }

   private static Described describe(Cube cube) {
      return new Described(WizCubeController.describe(SOURCE, cube));
   }

   private static String hierarchyEntity(Cube cube, String dimension) {
      return WizCubeController.describe(SOURCE, cube).dimensions().stream()
         .filter(d -> d.name().equals(dimension)).findFirst().orElseThrow().hierarchies().get(0)
         .entity();
   }

   private static Dimension dimension(Cube cube, String entity) {
      for(Enumeration<?> e = cube.getDimensions(); e.hasMoreElements();) {
         Dimension dim = (Dimension) e.nextElement();

         if(dim.getName().equals(entity)) {
            return dim;
         }
      }

      throw new AssertionError("no dimension " + entity);
   }

   private static Domain domain(Cube cube) {
      Domain domain = new Domain();
      domain.setDataSource(SOURCE);
      domain.setCubes(List.of(cube));

      return domain;
   }

   private static Cube cube(String... timeLevels) {
      Cube cube = new Cube();
      cube.setName("Sales");
      cube.setCaption("Sales");
      cube.setDimensions(List.of(
         hierarchy("Time", "[Time]", DataRef.CUBE_TIME_DIMENSION, timeLevels),
         hierarchy("Product", "[Product]", DataRef.CUBE_DIMENSION, "Family", "Name")));
      cube.setMeasures(List.of());

      return cube;
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
