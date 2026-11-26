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
package inetsoft.report.script.viewsheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.VSScriptableController;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81635: every name the script editor offers must be defined by the script engine. The fix of #81635 removed the
 * undefined names from the lists and ScriptEditorUndefinedNamesTest checks that those particular names stay out; this
 * test checks the rule itself, so a new undefined name in any list fails here. The lists come from the product:
 * {@code js-functions.json} and its generated twin, {@code VSScriptableController.CALC_GLOBALCLASSES} and
 * {@code CHART_GLOBAL_CLASSES}, and {@code assembly.properties} (Type.property). Chart constants and classes are
 * #81534 and #75524.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class EditorNamesMatchRuntimeTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   /** script library functions: defined only when the script library holds them, so either result is accepted */
   private static final Set<String> LIBRARY_FUNCTIONS = new TreeSet<>(Arrays.asList(
      "createBulletGraph", "createCirclePackingGraph", "createMekkoGraph", "createSunburstGraph", "createTreemap"));

   /** js-functions.json entries that are documentation of a method, not a global (the only one left after #81635) */
   private static final Set<String> NOT_GLOBALS = new TreeSet<>(Arrays.asList("StringObjectSplit"));

   /** types of assembly.properties that have no scriptable of the same name, so they cannot be compared */
   private static final Set<String> TYPES_WITHOUT_SCRIPTABLE = new TreeSet<>(Arrays.asList(
      "Field", "RangeSlider", "Selection", "SelectionContainer", "thisViewsheet"));

   private ViewsheetScope scope;

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      scope = new ViewsheetScope(sandbox, false);
   }

   /** the typeof of an expression in a script of the viewsheet */
   private String typeOf(String expr) throws Exception {
      return String.valueOf(scope.execute("typeof " + expr, (VSAScriptable) null));
   }

   private boolean defined(String expr) throws Exception {
      return !"undefined".equals(typeOf(expr));
   }

   private static JsonNode readJson(String resource) throws Exception {
      try(InputStream in = EditorNamesMatchRuntimeTest.class.getResourceAsStream(resource)) {
         assertNotNull(in, resource);
         // the file holds one object and, after it, text the editor does not read
         return new ObjectMapper().readTree(in);
      }
   }

   private static List<String> fieldNames(JsonNode node) {
      List<String> names = new ArrayList<>();

      for(Iterator<String> it = node.fieldNames(); it.hasNext();) {
         String name = it.next();

         if(!name.startsWith("!")) {
            names.add(name);
         }
      }

      return names;
   }

   private static String quote(String name) {
      return "['" + name.replace("'", "\\'") + "']";
   }

   @Test
   void everyCalcFunctionOfTheEditorIsDefined() throws Exception {
      List<String> undefined = new ArrayList<>();

      for(String entry : VSScriptableController.CALC_GLOBALCLASSES) {
         String name = entry.contains("(") ? entry.substring(0, entry.indexOf('(')) : entry;

         if(!"function".equals(typeOf("CALC" + quote(name)))) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyNameOfTheGeneratedFunctionsIsDefined() throws Exception {
      List<String> undefined = new ArrayList<>();

      for(String name : fieldNames(readJson("/inetsoft/web/binding/js-functions.generated.json"))) {
         if(!defined(name)) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyChartMemberOfTheEditorJsonIsDefined() throws Exception {
      JsonNode chart = readJson("/inetsoft/web/binding/js-functions.json").get("Chart");
      assertNotNull(chart);
      List<String> undefined = new ArrayList<>();

      for(String name : fieldNames(chart)) {
         if(!defined("Chart" + quote(name))) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyGlobalNameOfTheEditorJsonIsDefined() throws Exception {
      List<String> undefined = new ArrayList<>();

      for(String name : fieldNames(readJson("/inetsoft/web/binding/js-functions.json"))) {
         if(!defined(name) && !LIBRARY_FUNCTIONS.contains(name) && !NOT_GLOBALS.contains(name)) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyChartGlobalNameOfTheEditorIsDefined() throws Exception {
      List<String> undefined = new ArrayList<>();

      for(String entry : VSScriptableController.CHART_GLOBAL_CLASSES) {
         String name = entry.contains("(") ? entry.substring(0, entry.indexOf('(')) : entry;

         // typeof undefined is "undefined" by definition; the library functions depend on the script library
         if(!"undefined".equals(name) && !LIBRARY_FUNCTIONS.contains(name) && !defined(name)) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyStyleConstantMemberOfTheEditorJsonIsDefined() throws Exception {
      JsonNode node = readJson("/inetsoft/web/binding/js-functions.json").get("StyleConstant");
      assertNotNull(node);
      List<String> undefined = new ArrayList<>();

      for(String name : fieldNames(node)) {
         if(!defined("StyleConstant" + quote(name))) {
            undefined.add(name);
         }
      }

      assertEquals(List.of(), undefined);
   }

   @Test
   void everyAssemblyPropertyOfTheEditorIsMemberOfItsScriptable() throws Exception {
      Properties props = new Properties();

      try(InputStream in = getClass().getResourceAsStream("/inetsoft/web/binding/assembly.properties")) {
         assertNotNull(in);
         props.load(in);
      }

      Map<String, SortedSet<String>> listed = new TreeMap<>();

      for(String key : props.stringPropertyNames()) {
         int dot = key.indexOf('.');

         if(dot > 0) {
            String prop = key.substring(dot + 1);
            int dot2 = prop.indexOf('.');
            listed.computeIfAbsent(key.substring(0, dot), k -> new TreeSet<>())
               .add(dot2 < 0 ? prop : prop.substring(0, dot2));
         }
      }

      Set<String> missing = new TreeSet<>();
      Set<String> withoutScriptable = new TreeSet<>();

      for(Map.Entry<String, SortedSet<String>> e : listed.entrySet()) {
         Set<String> members;

         try {
            members = memberKeys(e.getKey());
         }
         catch(ReflectiveOperationException ex) {
            withoutScriptable.add(e.getKey());
            continue;
         }

         for(String prop : e.getValue()) {
            if(!members.contains(prop)) {
               missing.add(e.getKey() + "." + prop);
            }
         }
      }

      assertEquals(TYPES_WITHOUT_SCRIPTABLE, withoutScriptable, "types that cannot be compared");
      assertEquals(Set.of(), missing);
   }

   /** the member keys of the real scriptable class of a type, built on an assembly of that type */
   private Set<String> memberKeys(String type) throws ReflectiveOperationException {
      Class<?> scriptable = Class.forName("inetsoft.report.script.viewsheet." + type + "VSAScriptable");
      Class<?> assembly = Class.forName("inetsoft.uql.viewsheet." + type + "VSAssembly");
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      Constructor<?> c = assembly.getConstructor(Viewsheet.class, String.class);
      vs.addAssembly((VSAssembly) c.newInstance(vs, "A1"));
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(vs);
      VSAScriptable s = (VSAScriptable) scriptable.getConstructor(ViewsheetSandbox.class).newInstance(box);
      s.setAssembly("A1");
      Set<String> keys = new TreeSet<>();

      for(Object key : s.getMemberKeys()) {
         if(key instanceof String) {
            keys.add((String) key);
         }
      }

      return keys;
   }
}
