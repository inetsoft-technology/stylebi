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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.XUtil;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A viewsheet script is handed the viewer's session principal as
 * {@code parameter.__principal__}. It must be a read-only view: a script must not be
 * able to change the identity (org, groups, roles, properties) that the rest of the
 * session -- permission checks, the query sandbox and VPM -- trusts.
 * (Bug #77255, Bug #77256)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ViewsheetScopePrincipalTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetScope viewsheetScope;
   private XPrincipal user;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
      user = (XPrincipal) sandbox.getUser();
      assertNotNull(user);
   }

   @Test
   void scriptCannotChangeOrgOfSessionPrincipal() throws Exception {
      String before = user.getOrgId();

      run("parameter.__principal__.setOrgId('PROBE_SENTINEL_ORG')");

      assertEquals(before, user.getOrgId());
   }

   @Test
   void scriptCannotChangeGroupsOfSessionPrincipal() throws Exception {
      run("parameter.__principal__.setGroups(['vpmbypass'])");
      assertFalse(Arrays.asList(user.getGroups()).contains("vpmbypass"));
   }

   @Test
   void scriptCannotChangeGroupsThroughReturnedArray() throws Exception {
      user.setGroups(new String[] { "g1" });

      run("parameter.__principal__.getGroups()[0] = 'vpmbypass'");

      assertArrayEquals(new String[] { "g1" }, user.getGroups());
   }

   @Test
   void scriptCannotChangeRolesThroughReturnedArray() throws Exception {
      user.setRoles(new IdentityID[] { new IdentityID("Everyone", user.getOrgId()) });

      run("var r = parameter.__principal__.getRoles()[0];" +
          "r.name = 'Administrator'; r.setName('Administrator');");

      assertEquals("Everyone", user.getRoles()[0].getName());
   }

   @Test
   void scriptCannotChangePropertiesOfSessionPrincipal() throws Exception {
      run("parameter.__principal__.setProperty('curr_org_id', 'PROBE_SENTINEL_ORG')");
      assertNotEquals("PROBE_SENTINEL_ORG", user.getProperty("curr_org_id"));
   }

   @Test
   void scriptCannotChangeMutableParameterOfSessionPrincipal() throws Exception {
      user.setParameter("arr", new String[] { "a", "b" });

      run("parameter.__principal__.getParameter('arr')[0] = 'MUTATED'");

      assertArrayEquals(new String[] { "a", "b" }, (String[]) user.getParameter("arr"));
   }

   @Test
   void scriptCannotReachSessionOrClientInfo() throws Exception {
      assertEquals("blocked", run(
         "var p = parameter.__principal__;" +
         "(typeof p.getUser == 'function' || typeof p.getSession == 'function' ||" +
         " typeof p.getSecureID == 'function') ? 'reachable' : 'blocked'"));
   }

   /**
    * Bug #77256: VPM evaluates {@code groups} from the session principal. A viewsheet
    * script must not be able to forge a group that VPM then trusts.
    */
   @Test
   void scriptCannotForgeGroupSeenByVpm() throws Exception {
      // a regular (non-system) viewer session that is a member of a group
      String orgId = user.getOrgId();
      SRPrincipal viewer = new SRPrincipal(
         new IdentityID("user0", orgId), new IdentityID[0], new String[] { "g1" }, orgId, 1L);
      ((VariableTable) viewsheetScope.getVariableScriptable().unwrap()).put("__principal__", viewer);

      run("parameter.__principal__.setGroups(['vpmbypass'])");

      // the query sandbox hands this same principal to VPM (VpmProcessor -> VpmScope)
      VpmScope vpm = new VpmScope();
      vpm.setUser(viewer);
      String[] groups = (String[]) vpm.getMember("groups");

      assertFalse(groups != null && Arrays.asList(groups).contains("vpmbypass"),
                  "script-forged group reached VPM: " + Arrays.toString(groups));
      assertFalse(Arrays.asList(XUtil.getUserGroups(viewer)).contains("vpmbypass"));
   }

   /**
    * Bug #77256: the session groups are also exposed to scripts as
    * {@code parameter._GROUPS_}. That array must be a copy, so a script cannot
    * mutate the live principal's groups in place through it either.
    */
   @Test
   void scriptCannotChangeGroupsThroughGroupsVariable() throws Exception {
      user.setGroups(new String[] { "g1" });

      run("if(parameter._GROUPS_ != null) { parameter._GROUPS_[0] = 'vpmbypass'; }");

      assertArrayEquals(new String[] { "g1" }, user.getGroups());
   }

   @Test
   void scriptCanStillReadSessionPrincipal() throws Exception {
      user.setGroups(new String[] { "g1", "g2" });
      user.setProperty("probeProp", "probeValue");

      assertEquals(user.getName(), run("parameter.__principal__.getName()"));
      assertEquals(user.getOrgId(), run("parameter.__principal__.getOrgId()"));
      assertEquals(2.0, run("parameter.__principal__.getGroups().length"));
      assertEquals("g1", run("parameter.__principal__.getGroups()[0]"));
      assertEquals("g2", run("parameter.__principal__.getGroups()[1]"));
      assertEquals("probeValue", run("parameter.__principal__.getProperty('probeProp')"));
      assertEquals(user.getIdentityID().getName(),
                   run("parameter.__principal__.getIdentityID().getName()"));
      assertEquals(user.toString(), run("'' + parameter.__principal__"));
      assertEquals(true, run("parameter.__principal__ != null"));
   }

   /**
    * Bug #77361: {@code getHost()} and {@code getParameterTS(name)} were callable from
    * script in released builds (the session principal class was on the Rhino
    * allow-list) and are harmless read-only values, so they stay readable.
    */
   @Test
   void scriptCanReadHostAndParameterTimestamp() throws Exception {
      assertInstanceOf(SRPrincipal.class, user);
      user.setParameter("tsProbe", "value");
      long ts = user.getParameterTS("tsProbe");
      assertNotEquals(0L, ts);

      assertEquals("function", run("typeof parameter.__principal__.getHost"));
      assertEquals(((SRPrincipal) user).getHost(), run("parameter.__principal__.getHost()"));
      assertEquals("function", run("typeof parameter.__principal__.getParameterTS"));
      assertEquals(String.valueOf(ts),
                   run("'' + parameter.__principal__.getParameterTS('tsProbe')"));
   }

   @Test
   void parameterTimestampOfMissingOrNullNameIsZero() throws Exception {
      assertEquals("0", run("'' + parameter.__principal__.getParameterTS('noSuchParam')"));
      assertEquals("0", run("'' + parameter.__principal__.getParameterTS(null)"));
      assertEquals("0", run("'' + parameter.__principal__.getParameterTS()"));
   }

   /**
    * Bug #77361: the proxy must not be unwrapped when a script passes it to a raw host
    * method. {@code VpmScope.setUser(Principal)}/{@code getUser()} would hand the live
    * principal back to the script, whose setters would then change the session
    * identity (reopening Bug #77255/#77256).
    */
   @Test
   void scriptCannotGetRawPrincipalBackThroughVpmScope() throws Exception {
      assertPrincipalUnchangedBy(
         "var s = new (Java.type('inetsoft.uql.script.VpmScope'))();" +
         "s.setUser(parameter.__principal__);" +
         "var raw = s.getUser();");
   }

   /**
    * Bug #77361: same boundary for an {@code XPrincipal}-typed round trip.
    * {@code AssetQuerySandbox.setVPMUser(XPrincipal)} also mutates the principal it
    * is given (sets the {@code composer_vpm_user} property).
    */
   @Test
   void scriptCannotGetRawPrincipalBackThroughAssetQuerySandbox() throws Exception {
      assertPrincipalUnchangedBy(
         "var ws = new (Java.type('inetsoft.uql.asset.Worksheet'))();" +
         "var s = new (Java.type('inetsoft.report.composition.execution.AssetQuerySandbox'))(ws);" +
         "s.setVPMUser(parameter.__principal__);" +
         "var raw = s.getVPMUser();");
   }

   // Runs a script that tries to obtain the raw principal as "raw" and then mutate it,
   // and asserts on the principal's state rather than on any error text.
   private void assertPrincipalUnchangedBy(String obtainRaw) throws Exception {
      user.setGroups(new String[] { "g1" });
      String orgBefore = user.getOrgId();
      String vpmBefore = user.getProperty(SUtil.VPM_USER);

      Object result = run(obtainRaw +
          "try { raw.setOrgId('PROBE_SENTINEL_ORG'); } catch(e) {}" +
          "try { raw.setGroups(['vpmbypass']); } catch(e) {}" +
          "try { raw.setProperty('" + SUtil.VPM_USER + "', 'PROBE'); } catch(e) {}" +
          "'done'");

      assertEquals(orgBefore, user.getOrgId(), "script result: " + result);
      assertArrayEquals(new String[] { "g1" }, user.getGroups(), "script result: " + result);
      assertEquals(vpmBefore, user.getProperty(SUtil.VPM_USER), "script result: " + result);
   }

   private Object run(String script) throws Exception {
      // swallow the script-side error so each test asserts the resulting state,
      // whichever way the mutation is refused (missing member, TypeError, ...)
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
