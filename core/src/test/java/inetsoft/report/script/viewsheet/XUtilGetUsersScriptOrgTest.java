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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A sheet script of one organization must not list the users of other organizations
 * through {@code XUtil.getUsers()}, on any route a script reaches it by. Java callers
 * still get every organization's users. (Bug #77793)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class XUtilGetUsersScriptOrgTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ORG_A = "orgA77793";
   private static final String ORG_B = "orgB77793";

   // lists the users the script gets as name@org
   private static final String LIST =
      "function list(u) { var s = [];" +
      "  for(var i = 0; i < u.length; i++) { s.push(u[i].getName() + '@' + u[i].getOrgID()); }" +
      "  return s.join(','); }";

   private SecurityTestDataBuilder builder;
   private ViewsheetScope viewsheetScope;
   private String oldProvider;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_A, ORG_A)
         .addOrg(ORG_B, ORG_B)
         .addUser("aliceA", ORG_A, "password")
         .addUser("bobB", ORG_B, "password")
         .addUser("carolB", ORG_B, "password")
         .setup();

      SRPrincipal alice = builder.principalOf("aliceA", ORG_A);
      ThreadContext.setContextPrincipal(alice);
      ThreadContext.setPrincipal(alice);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void javaTypeRouteListsOnlyOwnOrgUsers() throws Exception {
      assertOwnOrgOnly(run(LIST + "list(Java.type('inetsoft.uql.util.XUtil').getUsers())"));
   }

   @Test
   void legacyPackageRouteListsOnlyOwnOrgUsers() throws Exception {
      assertOwnOrgOnly(run(LIST + "list(inetsoft.uql.util.XUtil.getUsers())"));
   }

   /**
    * A script function that Java calls after the script returned runs off the script
    * thread; its call is still the script's own.
    */
   @Test
   void deferredCallbackListsOnlyOwnOrgUsers() throws Exception {
      Holder holder = new Holder();
      ((VariableTable) viewsheetScope.getVariableScriptable().unwrap()).put("probe", holder);

      run(LIST + "parameter.probe.keep(function() {" +
          "  return list(Java.type('inetsoft.uql.util.XUtil').getUsers()); })");

      assertNotNull(holder.callback, "the script did not hand over its callback");
      assertFalse(JavaScriptEngine.isScriptThread());
      assertOwnOrgOnly(holder.callback.get());
   }

   /** Java code (bookmark rename for an internal user) still gets every org's users. */
   @Test
   void javaCallerStillGetsAllOrgUsers() {
      List<IdentityID> users = Arrays.asList(XUtil.getUsers());

      assertTrue(users.contains(new IdentityID("aliceA", ORG_A)), users.toString());
      assertTrue(users.contains(new IdentityID("bobB", ORG_B)), users.toString());
      assertTrue(users.contains(new IdentityID("carolB", ORG_B)), users.toString());
   }

   private static void assertOwnOrgOnly(Object result) {
      assertInstanceOf(String.class, result, String.valueOf(result));
      List<String> users = Arrays.asList(((String) result).split(","));

      assertTrue(users.contains("aliceA@" + ORG_A), users.toString());
      assertTrue(users.stream().allMatch(u -> u.endsWith("@" + ORG_A)), users.toString());
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

   /** Keeps a script function for Java to call after the script returned. */
   public static final class Holder {
      public void keep(Supplier<Object> callback) {
         this.callback = callback;
      }

      private Supplier<Object> callback;
   }
}
