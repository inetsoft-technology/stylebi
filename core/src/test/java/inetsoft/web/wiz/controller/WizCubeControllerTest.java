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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.xmla.*;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.model.WizCubeList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cube discovery of an XMLA data source: the route contract, the permission gates (data source
 * READ, then CUBE READ per cube), the shape of the description for hierarchy and plain, time and
 * non-time dimensions and for calculated measures, and the field-named errors for an unknown name.
 */
@Tag("core")
class WizCubeControllerTest {
   // ---- contract ---------------------------------------------------------------------------

   @Test
   void mappingsMatchThePublishedContract() {
      Set<String> paths = new HashSet<>();
      String prefix = WizCubeController.class.getAnnotation(RequestMapping.class).value()[0];

      for(Method method : WizCubeController.class.getDeclaredMethods()) {
         GetMapping get = method.getAnnotation(GetMapping.class);

         if(get != null) {
            paths.add(prefix + get.value()[0]);
         }
      }

      assertEquals(Set.of("/api/wiz/xmla/cubes", "/api/wiz/xmla/cube"), paths);
   }

   // ---- list -------------------------------------------------------------------------------

   @Test
   void listCubes_returnsEveryReadableCubeWithCounts() throws Exception {
      Fixture f = new Fixture();

      WizCubeList list = f.controller.listCubes("OLAP/FoodMart", f.principal);

      assertEquals("OLAP/FoodMart", list.datasource());
      assertNull(list.note());
      assertEquals(List.of("Sales", "HR"),
                   list.cubes().stream().map(WizCubeList.Entry::name).toList());
      WizCubeList.Entry sales = list.cubes().get(0);
      assertEquals("Sales Cube", sales.caption());
      assertEquals("Mondrian", sales.type());
      assertEquals(2, sales.dimensionCount(), "Time and Store; the empty Gender is left out");
      assertEquals(2, sales.measureCount());
   }

   @Test
   void listCubes_leavesOutACubeWithoutCubeReadAndNeverNamesIt() throws Exception {
      Fixture f = new Fixture();
      f.deny(ResourceType.CUBE, "OLAP/FoodMart::HR");

      WizCubeList list = f.controller.listCubes("OLAP/FoodMart", f.principal);

      assertEquals(List.of("Sales"), list.cubes().stream().map(WizCubeList.Entry::name).toList());
   }

   @Test
   void listCubes_withoutDataSourceReadIsDeniedAndReadsNothing() throws Exception {
      Fixture f = new Fixture();
      f.deny(ResourceType.DATA_SOURCE, "OLAP/FoodMart");

      assertThrows(SecurityException.class,
                   () -> f.controller.listCubes("OLAP/FoodMart", f.principal));
      verify(f.repository, never()).getDomain(anyString());
   }

   @Test
   void listCubes_anEmptyDomainSaysToRefresh() throws Exception {
      Fixture f = new Fixture();
      when(f.repository.getDomain("OLAP/FoodMart")).thenReturn(null);

      WizCubeList list = f.controller.listCubes("OLAP/FoodMart", f.principal);

      assertTrue(list.cubes().isEmpty());
      assertTrue(list.note().contains("refresh_data_source_metadata"), list.note());
   }

   @Test
   void listCubes_unknownDataSourceIs404NamingTheXmlaSources() throws Exception {
      Fixture f = new Fixture();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class, () -> f.controller.listCubes("OLAP/Nope", f.principal));

      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
      assertTrue(ex.getReason().startsWith("datasource:"), ex.getReason());
      assertTrue(ex.getReason().contains("OLAP/FoodMart"), ex.getReason());
      assertFalse(ex.getReason().contains("db/orders"), "a JDBC source is not an XMLA suggestion");
   }

   @Test
   void listCubes_aJdbcSourceIs422PointingAtTheXmlaSources() throws Exception {
      Fixture f = new Fixture();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class, () -> f.controller.listCubes("db/orders", f.principal));

      assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
      assertTrue(ex.getReason().contains("not an XMLA"), ex.getReason());
      assertTrue(ex.getReason().contains("OLAP/FoodMart"), ex.getReason());
   }

   @Test
   void listCubes_blankPathIs400() throws Exception {
      Fixture f = new Fixture();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class, () -> f.controller.listCubes(" ", f.principal));

      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
   }

   // ---- details ----------------------------------------------------------------------------

   @Test
   void getCube_describesHierarchiesLevelsInOrderTimeFlagAndMeasures() throws Exception {
      Fixture f = new Fixture();

      WizCubeDetails details = f.controller.getCube("OLAP/FoodMart", "Sales", f.principal);

      assertEquals("Sales", details.name());
      assertEquals("Sales Cube", details.caption());

      assertEquals(List.of("Store", "Time"),
                   details.dimensions().stream().map(WizCubeDetails.Dimension::name).toList());
      WizCubeDetails.Dimension time = details.dimensions().get(1);
      assertTrue(time.time());
      assertTrue(time.originalOrder());
      assertEquals(List.of("Time", "Weekly"),
                   time.hierarchies().stream().map(WizCubeDetails.Hierarchy::caption).toList());

      WizCubeDetails.Hierarchy main = time.hierarchies().get(0);
      assertEquals("[Time]", main.entity(), "the tree's entity: the hierarchy's unique name");
      assertEquals(List.of("Year", "Quarter", "Month"),
                   main.levels().stream().map(WizCubeDetails.Level::name).toList());
      assertEquals(List.of(0, 1, 2),
                   main.levels().stream().map(WizCubeDetails.Level::levelNumber).toList());
      assertEquals("Fiscal Year", main.levels().get(0).caption());
      assertEquals("Quarter", main.levels().get(1).caption(), "a missing caption falls back");

      WizCubeDetails.Dimension store = details.dimensions().get(0);
      assertFalse(store.time());
      assertFalse(store.originalOrder());
      assertEquals(1, store.hierarchies().size());
      assertEquals("Store", store.hierarchies().get(0).entity());
   }

   @Test
   void getCube_describesMeasuresIncludingCalculatedOnes() throws Exception {
      Fixture f = new Fixture();

      WizCubeDetails details = f.controller.getCube("OLAP/FoodMart", "Sales", f.principal);

      assertEquals(List.of("Profit", "Unit Sales"),
                   details.measures().stream().map(WizCubeDetails.Measure::name).toList());
      WizCubeDetails.Measure profit = details.measures().get(0);
      assertTrue(profit.calculated());
      WizCubeDetails.Measure units = details.measures().get(1);
      assertFalse(units.calculated());
      assertEquals("Unit Sales", units.caption());
      assertEquals("double", units.dtype());
   }

   @Test
   void getCube_aMeasureNamedLikeADimensionHidesTheDimension() throws Exception {
      Fixture f = new Fixture();

      WizCubeDetails hr = f.controller.getCube("OLAP/FoodMart", "HR", f.principal);

      assertEquals(List.of("Employee"),
                   hr.dimensions().stream().map(WizCubeDetails.Dimension::name).toList());
      assertEquals(List.of("Count"),
                   hr.measures().stream().map(WizCubeDetails.Measure::name).toList());
   }

   @Test
   void getCube_matchesByCaptionAndCaseInsensitivelyButNotWhenAmbiguous() throws Exception {
      Fixture f = new Fixture();

      assertEquals("Sales", f.controller.getCube("OLAP/FoodMart", "Sales Cube", f.principal).name());
      assertEquals("Sales", f.controller.getCube("OLAP/FoodMart", " sales ", f.principal).name());

      List<Cube> twins = List.of(cube("Twin", "A"), cube("TWIN", "B"));
      assertNull(WizCubeController.resolveCube(twins, "twin"));
      assertEquals("Twin", WizCubeController.resolveCube(twins, "Twin").getName());
   }

   @Test
   void getCube_unknownCubeIs404NamingTheFieldAndTheValidCubes() throws Exception {
      Fixture f = new Fixture();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> f.controller.getCube("OLAP/FoodMart", "Salez", f.principal));

      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
      assertTrue(ex.getReason().startsWith("cube:"), ex.getReason());
      assertTrue(ex.getReason().contains("Salez"), ex.getReason());
      assertTrue(ex.getReason().contains("[Sales, HR]"), ex.getReason());
   }

   @Test
   void getCube_aCubeWithoutCubeReadIsAnsweredAsUnknown() throws Exception {
      Fixture f = new Fixture();
      f.deny(ResourceType.CUBE, "OLAP/FoodMart::HR");

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> f.controller.getCube("OLAP/FoodMart", "HR", f.principal));

      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
      assertFalse(ex.getReason().contains("HR]"), "the denied cube must not be listed");
   }

   @Test
   void getCube_blankCubeIs400AndReadsNothing() throws Exception {
      Fixture f = new Fixture();

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> f.controller.getCube("OLAP/FoodMart", "  ", f.principal));

      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
      assertTrue(ex.getReason().startsWith("cube"), ex.getReason());
      verify(f.repository, never()).getDomain(anyString());
   }

   @Test
   void dimensionsOf_aPlainNonHierarchyCubeListsEachDimensionOnce() {
      Cube cube = new Cube();
      cube.setName("Flat");
      Dimension a = dimension("Region", DataRef.CUBE_DIMENSION, "Country");
      Dimension b = dimension("Day", DataRef.CUBE_TIME_DIMENSION, "Day");
      cube.setDimensions(List.of(a, b));
      cube.setMeasures(List.of());

      List<WizCubeDetails.Dimension> dims = WizCubeController.dimensionsOf(cube);

      assertEquals(List.of("Day", "Region"), dims.stream().map(WizCubeDetails.Dimension::name).toList());
      assertTrue(dims.get(0).time());
      assertEquals("Day", dims.get(0).hierarchies().get(0).entity());
   }

   // ---- fixtures ---------------------------------------------------------------------------

   private static Cube cube(String name, String caption) {
      Cube cube = new Cube();
      cube.setName(name);
      cube.setCaption(caption);
      cube.setDimensions(List.of());
      cube.setMeasures(List.of());

      return cube;
   }

   private static DimMember member(String name, String caption) {
      DimMember member = new DimMember();
      member.setName(name);
      member.setUniqueName(name);
      member.setCaption(caption);

      return member;
   }

   private static Dimension dimension(String name, int type, String... levels) {
      Dimension dim = new Dimension();
      dim.setDimensionName(name);
      dim.setType(type);
      List<DimMember> members = new ArrayList<>();

      for(String level : levels) {
         members.add(member(level, level));
      }

      dim.setLevels(members);

      return dim;
   }

   private static HierDimension hierarchy(String parent, String uniqueName, String caption,
                                          int type, DimMember... levels)
   {
      HierDimension dim = new HierDimension();
      dim.setDimensionName(parent);
      dim.setHierarchyUniqueName(uniqueName);
      dim.setHierarchyName(caption);
      dim.setHierCaption(caption);
      dim.setParentCaption(parent);
      dim.setType(type);
      dim.setLevels(List.of(levels));

      return dim;
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

   private static final class Fixture {
      Fixture() throws Exception {
         Cube sales = cube("Sales", "Sales Cube");
         sales.setType("Mondrian");
         HierDimension time = hierarchy(
            "Time", "[Time]", "Time", DataRef.CUBE_TIME_DIMENSION,
            member("Year", "Fiscal Year"), member("Quarter", null), member("Month", "Month"));
         time.setOriginalOrder(true);
         HierDimension weekly = hierarchy(
            "Time", "[Time.Weekly]", "Weekly", DataRef.CUBE_TIME_DIMENSION,
            member("Week", "Week"));
         HierDimension store = hierarchy(
            "Store", "Store", "Store", DataRef.CUBE_DIMENSION, member("Country", "Country"));
         // no levels: the tree does not list it
         HierDimension gender = hierarchy(
            "Gender", "[Gender]", "Gender", DataRef.CUBE_DIMENSION);
         sales.setDimensions(List.of(time, weekly, store, gender));
         sales.setMeasures(List.of(measure("Unit Sales", false), measure("Profit", true)));

         // "Count" is both a hierarchy name and a measure: only the measure survives
         Cube hr = cube("HR", "HR");
         HierDimension employee = hierarchy(
            "Employee", "Employee", "Employee", DataRef.CUBE_DIMENSION, member("Title", "Title"));
         HierDimension count = hierarchy(
            "Count", "Count", "Count", DataRef.CUBE_DIMENSION, member("Number", "Number"));
         hr.setDimensions(List.of(employee, count));
         hr.setMeasures(List.of(measure("Count", false)));

         Domain domain = new Domain();
         domain.setDataSource("OLAP/FoodMart");
         domain.setCubes(List.of(sales, hr));

         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenAnswer(
            inv -> !denied.contains(inv.getArgument(1) + "|" + inv.getArgument(2)));
         when(repository.getDataSourceFullNames()).thenReturn(
            new String[] { "OLAP/FoodMart", "db/orders" });
         when(repository.getDataSource("OLAP/FoodMart")).thenReturn(mock(XMLADataSource.class));
         JDBCDataSource jdbc = mock(JDBCDataSource.class);
         when(jdbc.getType()).thenReturn("jdbc");
         when(repository.getDataSource("db/orders")).thenReturn(jdbc);
         when(repository.getDomain("OLAP/FoodMart")).thenReturn(domain);
         controller = new WizCubeController(security, repository);
      }

      void deny(ResourceType type, String path) {
         denied.add(type + "|" + path);
      }

      final SecurityEngine security = mock(SecurityEngine.class);
      final XRepository repository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final Set<String> denied = new HashSet<>();
      final WizCubeController controller;
   }
}
