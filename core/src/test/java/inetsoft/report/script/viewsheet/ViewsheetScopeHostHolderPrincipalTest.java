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

import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.FormulaContext;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.messaging.simp.SimpAttributes;
import org.springframework.messaging.simp.SimpAttributesContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The #77255 read-only principal proxy is applied where the engine converts a value for
 * a script ({@code parameter.__principal__}). A script must not be able to get around it
 * by reaching the live principal, the identity it hands out, or the session sandboxes
 * that decide which principal a session trusts, through raw host calls. The holder
 * classes stay loadable; what is asserted is that the live identity is unchanged.
 * (Bug #77348)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ViewsheetScopeHostHolderPrincipalTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final String ATTR = "sree.security.principal";

   private ViewsheetScope viewsheetScope;
   private ViewsheetSandbox sandbox;
   private XPrincipal user;
   private SRPrincipal viewer;
   private Principal savedContextPrincipal;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      user = (XPrincipal) sandbox.getUser();
      assertNotNull(user);

      // a session principal of the kind the security filters bind for a logged-in user
      viewer = new DestinationUserNameProviderPrincipal(
         new ClientInfo(new IdentityID("alice", "orgA"), "127.0.0.1"),
         new IdentityID[] { new IdentityID("Everyone", "orgA") }, new String[] { "g1" },
         "orgA", 1L);
      savedContextPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      SimpAttributesContextHolder.resetAttributes();
      RequestContextHolder.resetRequestAttributes();
   }

   // mutations tried on every raw principal a route reaches: the org, and the
   // user identity (name + identity org) handed out by getClientUserID()
   private static final String MUTATE =
      "function mutate(p) {" +
      "  try { p.setOrgId('X_ORG'); } catch(e) {}" +
      "  try { var id = p.getClientUserID(); id.setName('mallory'); id.setOrgID('evilOrg'); }" +
      "  catch(e) {}" +
      "}";

   /** Route 1: the Spring STOMP attribute holder bound for a message handler. */
   @Test
   void simpAttributesHolderCannotMutateSessionPrincipal() throws Exception {
      Map<String, Object> attrs = new HashMap<>();
      attrs.put(ATTR, viewer);
      SimpAttributesContextHolder.setAttributes(new SimpAttributes("s1", attrs));

      run(MUTATE +
          "mutate(Java.type('org.springframework.messaging.simp.SimpAttributesContextHolder')" +
          ".currentAttributes().getAttribute('" + ATTR + "'))");

      assertViewerUnchanged();
   }

   /** Route 2: the Spring servlet request holder and its session attributes. */
   @Test
   void requestContextHolderCannotMutateSessionPrincipal() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.getSession(true).setAttribute(ATTR, viewer);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      run(MUTATE +
          "mutate(Java.type('org.springframework.web.context.request.RequestContextHolder')" +
          ".currentRequestAttributes().getAttribute('" + ATTR + "', 1))");

      assertViewerUnchanged();
   }

   /** Route 3: VariableTable.get('__principal__') returns the thread's context principal. */
   @Test
   void variableTableCannotMutateContextPrincipal() throws Exception {
      ThreadContext.setContextPrincipal(viewer);

      run(MUTATE +
          "mutate(new (Java.type('inetsoft.uql.VariableTable'))().get('__principal__'))");

      assertViewerUnchanged();
   }

   /** Route 4: FormulaContext hands out the raw executing scope and its variables. */
   @Test
   void formulaContextCannotMutateSessionPrincipal() throws Exception {
      String org = user.getOrgId();
      String name = user.getName();

      run(MUTATE +
          "mutate(Java.type('inetsoft.util.script.FormulaContext').getScope().getScope()" +
          ".getVariableScriptable().getMember('__principal__'))");

      assertEquals(org, user.getOrgId());
      assertEquals(name, user.getName());
   }

   /**
    * Route 4: FormulaContext also exposes the restricted (web) flag that
    * ViewsheetScope.execute sets for the duration of a script.
    */
   @Test
   void formulaContextCannotLiftRestriction() throws Exception {
      // reads the flag from Java while the script runs
      Supplier<Boolean> probe = FormulaContext::isRestricted;
      ((VariableTable) viewsheetScope.getVariableScriptable().unwrap()).put("probe", probe);

      assertEquals(true, run(
         "try { Java.type('inetsoft.util.script.FormulaContext').setRestricted(false); }" +
         "catch(e) {}" +
         "parameter.probe.get()"));
   }

   /** Route 5: the engine hands out every live sheet on the node, and their principals. */
   @Test
   void worksheetEngineCannotMutateOtherSessionsPrincipals() throws Exception {
      RuntimeSheet[] sheets = WorksheetEngine.getWorksheetService().getRuntimeSheets(null);
      assertTrue(sheets.length > 0);
      List<String> before = describeUsers(sheets);

      run(MUTATE +
          "var ss = Java.type('inetsoft.report.composition.WorksheetEngine')" +
          "  .getWorksheetService().getRuntimeSheets(null);" +
          "for(var i = 0; i < ss.length; i++) { mutate(ss[i].getUser()); }");

      assertEquals(before, describeUsers(sheets));
   }

   /**
    * Route 5: with the sheets in hand, a script could make a session's sandbox trust a
    * different principal without calling any principal mutator.
    */
   @Test
   void worksheetEngineCannotSwapSandboxUser() throws Exception {
      AssetQuerySandbox wbox = sandbox.getAssetQuerySandbox();
      Principal baseUser = wbox == null ? null : wbox.getBaseUser();
      XPrincipal vpmUser = wbox == null ? null : wbox.getVPMUser();
      // the foreign principal the script swaps in
      ThreadContext.setContextPrincipal(viewer);

      run("var other = new (Java.type('inetsoft.uql.VariableTable'))().get('__principal__');" +
          "var ss = Java.type('inetsoft.report.composition.WorksheetEngine')" +
          "  .getWorksheetService().getRuntimeSheets(null);" +
          "for(var i = 0; i < ss.length; i++) {" +
          "  try {" +
          "    var box = ss[i].getViewsheetSandbox().get();" +
          "    try { box.setUser(other); } catch(e) {}" +
          "    var w = box.getAssetQuerySandbox();" +
          "    try { w.setBaseUser(other); } catch(e) {}" +
          "    try { w.setVPMUser(other); } catch(e) {}" +
          "  } catch(e) {}" +
          "}");

      assertSame(user, sandbox.getUser());

      if(wbox != null) {
         assertSame(baseUser, wbox.getBaseUser());
         assertSame(vpmUser, wbox.getVPMUser());
      }
   }

   /**
    * Guard: a raw principal a script reaches through any host path must expose no
    * members at all, so an interface added to a principal class later cannot reopen a
    * mutator (the way LogPrincipal.getClientUserID did).
    */
   @Test
   void rawPrincipalExposesNoMembers() throws Exception {
      Principal[] principals = {
         new XPrincipal(new IdentityID("alice", "orgA")),
         new SRPrincipal(new IdentityID("alice", "orgA"), new IdentityID[0],
                         new String[0], "orgA", 1L),
         viewer
      };

      for(Principal p : principals) {
         ThreadContext.setContextPrincipal(p);

         assertEquals("[]", run(
            "JSON.stringify(Object.keys(" +
            "new (Java.type('inetsoft.uql.VariableTable'))().get('__principal__')))"),
            p.getClass().getName());
      }
   }

   /** The sanctioned read-only view keeps working. */
   @Test
   void proxiedPrincipalIsStillReadable() throws Exception {
      assertEquals(user.getName(), run("parameter.__principal__.getName()"));
      assertEquals(user.getOrgId(), run("parameter.__principal__.getOrgId()"));
      assertEquals(user.getIdentityID().getName(),
                   run("parameter.__principal__.getIdentityID().getName()"));
   }

   private void assertViewerUnchanged() {
      assertEquals("orgA", viewer.getOrgId());
      assertEquals(new IdentityID("alice", "orgA"), viewer.getIdentityID());
      assertEquals(new IdentityID("alice", "orgA").convertToKey(), viewer.getName());
      assertEquals(new IdentityID("alice", "orgA"), viewer.getClientUserID());
   }

   private static List<String> describeUsers(RuntimeSheet[] sheets) {
      List<String> result = new ArrayList<>();

      for(RuntimeSheet sheet : sheets) {
         Principal p = sheet.getUser();
         result.add(p == null ? "null" : p.getName() + "|" +
            (p instanceof XPrincipal x ? x.getOrgId() : ""));
      }

      return result;
   }

   private Object run(String script) throws Exception {
      // swallow the script-side error so each test asserts the resulting state,
      // whichever way the access is refused (missing member, TypeError, ...)
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
}
