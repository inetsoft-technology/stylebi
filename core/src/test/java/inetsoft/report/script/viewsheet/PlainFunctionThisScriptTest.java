/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81930 through a real runtime viewsheet: the {@code this} of a plain-called function of
 * a component script, an onInit script or a script library function is the scope the script
 * runs on ({@link ViewsheetScope#execute(String, String)}), so {@code this.visible = false}
 * in a helper reaches the component.
 *
 * <p>Not covered by these: {@code this === TableView1} is false for the top-level this and for
 * the plain function alike, the scope being the chain proxy and not the component itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class PlainFunctionThisScriptTest {
   private static final String ASSEMBLY = "TableView1";

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetSandbox sandbox;
   private ViewsheetScope scope;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      scope = sandbox.getScope();
      assertNotNull(sandbox.getViewsheet().getAssembly(ASSEMBLY));
   }

   private Object exec(String script) throws Exception {
      return scope.execute(script, ASSEMBLY);
   }

   @Test
   void plainFunctionWriteReachesComponent() throws Exception {
      assertEquals("true", String.valueOf(exec("String(TableView1.visible)")));
      exec("function h(){ this.visible = false; } h();");
      assertEquals("false", String.valueOf(exec("String(TableView1.visible)")));
   }

   @Test
   void plainFunctionReadSeesComponentMember() throws Exception {
      assertEquals("boolean", exec("function g(){ return typeof this.visible; } g()"));
      assertEquals("boolean", exec("(function(){ return typeof this.visible; })()"));
      assertEquals("boolean", exec("function g(){ return (() => typeof this.visible)(); } g()"));
   }

   @Test
   void withThisInPlainFunctionAfterAHelperWriteReadsTheComponent() throws Exception {
      // the report's with(this) row: before the fix the helper write went to the global and a
      // later with(this) read that leaked value (false) while the component still said true
      exec("function h(){ this.visible = false; } h();");
      assertEquals("boolean:false",
         exec("function w(){ with(this){ return typeof visible + ':' + visible; } } w()"));
      assertEquals("false", String.valueOf(exec("String(TableView1.visible)")));
   }

   @Test
   void helperWriteDoesNotLeakToTheGlobal() throws Exception {
      exec("function h(){ this.leaked81930 = 5; } h();");
      assertEquals("undefined", exec("typeof globalThis.leaked81930"));
   }

   @Test
   void methodsAndConstructorsKeepTheirReceiver() throws Exception {
      assertEquals("number", exec("var o={v:1, m:function(){ return typeof this.v; }}; o.m()"));
      assertEquals("undefined",
         exec("var o={m:function(){ return typeof this.visible; }}; o.m()"));
      assertEquals(1.0, ((Number) exec("function F(){ this.a = 1; } new F().a")).doubleValue());
   }

   @Test
   void globalObjectIdiomStillFindsBuiltins() throws Exception {
      assertEquals("object|function", exec(
         "(function(){ var g = this; return typeof g.Math + '|' + typeof g.parseInt; })()"));
   }

   @Test
   void indirectFunctionReturnThisIsTheRealGlobal() throws Exception {
      assertEquals(true, exec("Function('return this')() === globalThis"));
   }

   /**
    * A function that an onInit script defines and a component script calls gets the scope of
    * the call (the component), where Rhino bound it to the scope that defined it (the sheet).
    */
   @Test
   void onInitFunctionCalledFromComponentGetsTheCallersScope() throws Exception {
      Viewsheet vs = sandbox.getViewsheet();
      vs.getViewsheetInfo().setScriptEnabled(true);
      vs.getViewsheetInfo().setOnInit("function setHidden81930(){ this.visible = false; }");
      sandbox.processOnInit();

      exec("setHidden81930();");
      assertEquals("false", String.valueOf(exec("String(TableView1.visible)")));
   }

   /** A script library function is installed outside the compile path, and is rewritten too. */
   @Test
   void libraryFunctionSeesTheCallingScope() throws Exception {
      LibManager mgr = LibManagerProvider.getInstance().getManager();
      mgr.setScript("lib81930", "function libHide81930(){ this.visible = false; }\n" +
         "function libRead81930(){ return typeof this.visible; }");

      try {
         // the engine installs library sources when it starts; restart it so it picks up the
         // library added above, as a new session would
         scope.getScriptEnv().reset();
         assertEquals("boolean", exec("libRead81930()"));
         exec("libHide81930();");
         assertEquals("false", String.valueOf(exec("String(TableView1.visible)")));
      }
      finally {
         mgr.removeScript("lib81930");
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
