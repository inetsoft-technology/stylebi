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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The members of XDataModel, XLogicalModel and XPartition that add, remove, rename or update
 * a logical model, a partition or a virtual private model write the data source registry, so
 * a sheet script may not call them and the registry is left as it was. Reading the data model
 * from a script, and the writes from Java, still work. (Bug #77918)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  ScriptDataModelWriteAccessTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ScriptDataModelWriteAccessTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String REFUSED = "A script may not ";
   private static final String PRELUDE =
      "var LM = Java.type('inetsoft.uql.erm.XLogicalModel');" +
      " var XP = Java.type('inetsoft.uql.erm.XPartition');" +
      " var VPM = Java.type('inetsoft.uql.erm.vpm.VirtualPrivateModel');";

   /**
    * A script call of each registry-writing member, keyed by the member's signature. The
    * script runs with dm, a new data model of the seeded data source, which has the logical
    * model base with the extended model child, the partition p with the extended partition
    * pc, and the virtual private models v1 and v2.
    */
   private static final Map<String, String> CALLS = new LinkedHashMap<>();

   static {
      String lm = "dm.getLogicalModel('base')";
      String xp = "dm.getPartition('p')";
      CALLS.put("XDataModel.addLogicalModel(XLogicalModel)", "dm.addLogicalModel(new LM('a1'))");
      CALLS.put("XDataModel.addLogicalModel(XLogicalModel,boolean)",
                "dm.addLogicalModel(new LM('a1'), true)");
      CALLS.put("XDataModel.updateLogicalModel(XLogicalModel)",
                "dm.updateLogicalModel(new LM('a1'))");
      CALLS.put("XDataModel.removeLogicalModel(String)", "dm.removeLogicalModel('base')");
      CALLS.put("XDataModel.removeLogicalModel(String,boolean)",
                "dm.removeLogicalModel('base', true)");
      CALLS.put("XDataModel.renameLogicalModel(String,String)",
                "dm.renameLogicalModel('base', 'b2')");
      CALLS.put("XDataModel.renameLogicalModel(String,String,String)",
                "dm.renameLogicalModel('base', 'b2', null)");
      CALLS.put("XDataModel.addVirtualPrivateModel(VirtualPrivateModel,boolean)",
                "dm.addVirtualPrivateModel(new VPM('v3'), true)");
      CALLS.put("XDataModel.removeVirtualPrivateModel(String)",
                "dm.removeVirtualPrivateModel('v1')");
      CALLS.put("XDataModel.removeVirtualPrivateModel(VirtualPrivateModel)",
                "dm.removeVirtualPrivateModel(dm.getVirtualPrivateModel('v1'))");
      CALLS.put("XDataModel.removeVirtualPrivateModels()", "dm.removeVirtualPrivateModels()");
      CALLS.put("XDataModel.renameVirtualPrivateModel(String,VirtualPrivateModel)",
                "dm.renameVirtualPrivateModel('v1', new VPM('v3'))");
      CALLS.put("XDataModel.addPartition(XPartition)", "dm.addPartition(new XP('p2'))");
      CALLS.put("XDataModel.addPartition(XPartition,boolean)",
                "dm.addPartition(new XP('p2'), false)");
      CALLS.put("XDataModel.removePartition(String)", "dm.removePartition('p')");
      CALLS.put("XDataModel.updatePartition(String)", "dm.updatePartition('p')");
      CALLS.put("XDataModel.renamePartition(String,String)", "dm.renamePartition('p', 'p2')");
      CALLS.put("XDataModel.renamePartition(String,String,String)",
                "dm.renamePartition('p', 'p2', null)");
      CALLS.put("XLogicalModel.addLogicalModel(XLogicalModel,boolean)",
                lm + ".addLogicalModel(new LM('c2'), true)");
      CALLS.put("XLogicalModel.removeLogicalModel(String)", lm + ".removeLogicalModel('child')");
      CALLS.put("XLogicalModel.renameLogicalModel(String,XLogicalModel)",
                lm + ".renameLogicalModel('child', new LM('c2'))");
      CALLS.put("XPartition.addPartition(XPartition,boolean)",
                xp + ".addPartition(new XP('pc2'), false)");
      CALLS.put("XPartition.removePartition(String)", xp + ".removePartition('pc')");
      CALLS.put("XPartition.renamePartition(String,XPartition)",
                xp + ".renamePartition('pc', new XP('pc2'))");
      CALLS.put("XPartition.renamePartition(String,String)",
                xp + ".renamePartition('pc', 'pc2')");
   }

   private static int count = 0;
   private ViewsheetScope viewsheetScope;
   private String ds;

   @BeforeEach
   void setUp() throws Exception {
      // without it the registry root is null, and an add fails only after it has written
      DataSourceRegistry.getRegistry().init();
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      ds = "ds77918_" + (++count);
      seed();
   }

   /** A future write member must be guarded and listed here. */
   @Test
   void everyWriteMemberIsCovered() {
      Set<String> members = new TreeSet<>();

      for(Class<?> type : List.of(XDataModel.class, XLogicalModel.class, XPartition.class)) {
         for(Method method : type.getMethods()) {
            if(Modifier.isPublic(method.getModifiers()) && method.getName().matches(
               "(add|remove|rename|update)(LogicalModel|Partition|VirtualPrivateModel)s?"))
            {
               members.add(type.getSimpleName() + "." + method.getName() + "(" +
                  Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                     .collect(Collectors.joining(",")) + ")");
            }
         }
      }

      assertEquals(members, new TreeSet<>(CALLS.keySet()));
   }

   @Test
   void seededStateIsReadable() {
      assertEquals(List.of("LM base seed", "ELM base/child seed", "P p seed", "EP p/pc seed",
                           "VPM v1 seed", "VPM v2 seed"), state());
   }

   /** Each member is refused to a script, and the registry is left as it was. */
   @Test
   void scriptWritesRefused() {
      List<Executable> checks = new ArrayList<>();

      for(Map.Entry<String, String> call : CALLS.entrySet()) {
         checks.add(() -> {
            setUp();
            List<String> before = state();
            Object result = run(PRELUDE + " var dm = new (Java.type('" +
                                   XDataModel.class.getName() + "'))('" + ds + "'); " +
                                   call.getValue() + "; 'ok'");

            assertEquals(before, state(), call.getKey() + ": " + result);
            assertErrorContains(call.getKey(), result, REFUSED);
         });
      }

      assertAll(checks);
   }

   @Test
   void removeVirtualPrivateModelsRefused() throws Exception {
      List<String> before = state();
      Object result = run("new (Java.type('inetsoft.uql.erm.XDataModel'))('" + ds + "')" +
                             ".removeVirtualPrivateModels(); 'ok'");

      assertEquals(before, state(), String.valueOf(result));
      assertErrorContains("removeVirtualPrivateModels", result,
                          "A script may not remove a virtual private model");
   }

   /** The package-root route is refused as Java.type is. */
   @Test
   void legacyRouteRefused() throws Exception {
      List<String> before = state();
      Object result = run("new inetsoft.uql.erm.XDataModel('" + ds + "').removePartition('p');" +
                             " 'ok'");
      Object result2 = run("new inetsoft.uql.erm.XDataModel('" + ds + "')" +
                              ".removeVirtualPrivateModels(); 'ok'");

      assertEquals(before, state(), result + ", " + result2);
      assertErrorContains("removePartition", result, "A script may not remove a partition");
      assertErrorContains("removeVirtualPrivateModels", result2,
                          "A script may not remove a virtual private model");
   }

   /**
    * A write that Java calls back for the script, a script function or the member itself
    * passed as a Java functional interface, is still the script's call and is refused.
    */
   @Test
   void callbackRouteRefused() throws Exception {
      String list = "var L = new (Java.type('java.util.ArrayList'))(); L.add('p'); L.add('v1');";
      Map<String, String> calls = new LinkedHashMap<>();
      calls.put("forEach(function)", list + " L.forEach(function(n) { dm.removePartition(n); })");
      calls.put("forEach(member)", list + " L.subList(1, 2).forEach(dm.removeVirtualPrivateModel)");
      calls.put("sort(comparator)", list + " Java.type('java.util.Collections').sort(L," +
                   " function(a, b) { dm.removeVirtualPrivateModels(); return 0; })");
      calls.put("stream().map(function)", list + " L.stream().map(function(n) {" +
                   " dm.removePartition(n); return n; }).toArray()");

      for(Map.Entry<String, String> call : calls.entrySet()) {
         List<String> before = state();
         Object result = run(PRELUDE + " var dm = new (Java.type('" +
                                XDataModel.class.getName() + "'))('" + ds + "'); " +
                                call.getValue() + "; 'ok'");

         assertEquals(before, state(), call.getKey() + ": " + result);
         assertErrorContains(call.getKey(), result, REFUSED);
      }
   }

   /** A script still reads the data model, its extended models and its partitions. */
   @Test
   void scriptReadsStillAllowed() throws Exception {
      String dm = "new (Java.type('inetsoft.uql.erm.XDataModel'))('" + ds + "')";
      assertEquals("1,base,child,1,pc,2", run(
         "var dm = " + dm + "; var lm = dm.getLogicalModel('base');" +
         " dm.getLogicalModelNames().length + ',' + lm.getName() + ',' +" +
         " lm.getLogicalModelNames()[0] + ',' + dm.getPartitionNames().length + ',' +" +
         " dm.getPartition('p').getPartitionNames()[0] + ',' +" +
         " dm.getVirtualPrivateModelNames().length"));
      assertEquals("null", run(
         "'' + Java.type('inetsoft.uql.util.XUtil').getLogicModel(" +
         "new (Java.type('inetsoft.uql.asset.SourceInfo'))(1, '" + ds + "', 'base'), null)"));
      assertEquals(List.of("LM base seed", "ELM base/child seed", "P p seed", "EP p/pc seed",
                           "VPM v1 seed", "VPM v2 seed"), state());
   }

   /** Java callers still reach the writes of each class. */
   @Test
   void javaWritesStillWork() {
      XDataModel dm = new XDataModel(ds);
      dm.getLogicalModel("base").removeLogicalModel("child");
      dm.getPartition("p").removePartition("pc");
      dm.removeVirtualPrivateModels();
      dm.addLogicalModel(model("a1"));

      assertEquals(List.of("LM a1 seed", "LM base seed", "P p seed"), state());
   }

   private void seed() {
      XDataModel dm = new XDataModel(ds);
      XLogicalModel base = model("base");
      dm.addLogicalModel(base);
      dm.getLogicalModel("base").addLogicalModel(model("child"), true);
      XPartition p = new XPartition("p");
      p.setDescription("seed");
      dm.addPartition(p);
      XPartition pc = new XPartition("pc");
      pc.setDescription("seed");
      dm.getPartition("p").addPartition(pc, false);

      for(String name : List.of("v1", "v2")) {
         VirtualPrivateModel vpm = new VirtualPrivateModel(name);
         vpm.setDescription("seed");
         dm.addVirtualPrivateModel(vpm, true);
      }
   }

   private static XLogicalModel model(String name) {
      XLogicalModel model = new XLogicalModel(name);
      model.setDescription("seed");
      return model;
   }

   // the data model as stored, read back from storage
   private List<String> state() {
      DataSourceRegistry.getRegistry().clearCache();
      XDataModel dm = new XDataModel(ds);
      List<String> state = new ArrayList<>();

      for(String name : sorted(dm.getLogicalModelNames())) {
         XLogicalModel lm = dm.getLogicalModel(name);
         state.add("LM " + name + " " + lm.getDescription());

         for(String child : sorted(lm.getLogicalModelNames())) {
            state.add("ELM " + name + "/" + child + " " +
                         lm.getLogicalModel(child).getDescription());
         }
      }

      for(String name : sorted(dm.getPartitionNames())) {
         XPartition p = dm.getPartition(name);
         state.add("P " + name + " " + p.getDescription());

         for(String child : sorted(p.getPartitionNames())) {
            state.add("EP " + name + "/" + child + " " + p.getPartition(child).getDescription());
         }
      }

      for(String name : sorted(dm.getVirtualPrivateModelNames())) {
         state.add("VPM " + name + " " + dm.getVirtualPrivateModel(name).getDescription());
      }

      return state;
   }

   private static List<String> sorted(String[] names) {
      List<String> list = names == null ? new ArrayList<>() : new ArrayList<>(List.of(names));
      Collections.sort(list);
      return list;
   }

   private static void assertErrorContains(String member, Object result, String text) {
      assertInstanceOf(String.class, result, member + ": " + result);
      assertTrue(((String) result).startsWith("error: "), member + ": " + result);
      assertTrue(((String) result).contains(text), member + ": " + result);
   }

   private Object run(String script) throws Exception {
      return viewsheetScope.execute(
         "try { " + script + " } catch(e) { 'error: ' + e }",
         viewsheetScope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }

   @Configuration
   static class Beans {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }
}
