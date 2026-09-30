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
package inetsoft.util.script;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.DestinationUserNameProviderPrincipal;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.VariableTable;
import inetsoft.uql.script.VariableScriptable;
import org.junit.jupiter.api.*;
import org.mozilla.javascript.*;

import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77384: the session principal exposed to Rhino scripts as
 * {@code parameter.__principal__} must be read-only.
 */
class PrincipalScriptAccessTest {
   @BeforeEach
   void setUp() {
      // production-shaped principal, as SecurityEngine.authenticate creates it for
      // password, anonymous and security-off logins
      principal = new DestinationUserNameProviderPrincipal(
         new ClientInfo(new IdentityID("alice", "orgA"), "10.0.0.1"),
         new IdentityID[] { new IdentityID("Everyone", "orgA") },
         new String[] { "readers" }, "orgA", 12345L, "Alice A");
      principal.setProperty("__internal__", "true");
      principal.setProperty("login.user", "true");
      principal.setParameter("p1", new String[] { "a", "b" });

      cx = TimeoutContext.enter();
      scope = cx.initStandardObjects();
      VariableTable vars = new VariableTable();
      vars.put("__principal__", principal);
      scope.put("parameter", scope, new VariableScriptable(vars));
      // viewsheet-style binding: VariableScriptable with a parent scope
      VariableScriptable vsparameter = new VariableScriptable(vars);
      vsparameter.setParentScope(scope);
      scope.put("vsparameter", scope, vsparameter);
      host = new Host(principal);
      scope.put("host", scope, Context.javaToJS(host, scope));
   }

   @AfterEach
   void tearDown() {
      Context.exit();
   }

   @Test
   void settersCannotMutatePrincipal() {
      tryExec("p.setOrgId('orgVICTIM')");
      tryExec("p.setGroups(['admins'])");
      tryExec("p.setProperty('__internal__', 'false')");
      tryExec("p.setProperty('virtual', 'true')");
      tryExec("p.setParameter('p1', 'x')");
      tryExec("p.setAlias('mallory')");
      tryExec("p.setIgnoreLogin(true)");
      tryExec("p.setProfiling(true)");
      tryExec("p.orgId = 'orgVICTIM'");
      tryExec("p.groups = ['admins']");
      tryExec("p['__internal__'] = 'false'");
      tryExec("delete p.getName");

      assertPrincipalUnchanged();
   }

   @Test
   void setterAndMutatorFunctionsAreUnreachable() {
      for(String name : new String[] {
         "setOrgId", "setGroups", "setRoles", "setProperty", "setParameter", "setAlias",
         "setUser", "setLocale", "setSession", "setIgnoreLogin", "setProfiling",
         "setLastAccess", "updateRoles", "getUser", "getSession", "getSessionID",
         "getSecureID", "getDestinationUserName", "getClass", "getAllRoles", "clone"
      })
      {
         assertEquals("undefined", exec("typeof p." + name), name);
      }

      assertPrincipalUnchanged();
   }

   @Test
   void allowListedReadersStillWork() {
      assertEquals("alice~;~orgA", exec("'' + p.getName()"));
      assertEquals("alice~;~orgA", exec("'' + p.name"));
      assertEquals("orgA", exec("'' + p.getOrgId()"));
      assertEquals("orgA", exec("'' + p.orgId"));
      assertEquals("readers", exec("'' + p.getGroups()[0]"));
      assertEquals("1", exec("'' + p.getGroups().length"));
      assertEquals("1", exec("'' + p.getRoles().length"));
      assertEquals("true", exec("'' + p.getProperty('__internal__')"));
      assertEquals("Alice A", exec("'' + p.getAlias()"));
      assertEquals(principal.getHost(), exec("'' + p.getHost()"));
      assertEquals("b", exec("'' + p.getParameter('p1')[1]"));
      assertEquals(true, exec("p.getParameterTS('p1') > 0"));
      assertEquals(true, exec("p.getPropertyNames().hasMoreElements()"));
      assertEquals(true, exec("p.getParameterNames().hasMoreElements()"));
      assertEquals(principal.toString(), exec("'' + p"));
      assertEquals(principal.toString(), exec("'' + p.toString()"));
      assertEquals(principal.toView(), exec("'' + p.toView()"));
   }

   @Test
   void mutatingReturnedValuesDoesNotAffectPrincipal() {
      tryExec("var g = p.getGroups(); g[0] = 'admins';");
      tryExec("var v = p.getParameter('p1'); v[0] = 'z';");
      assertPrincipalUnchanged();
      assertEquals("a", ((String[]) principal.getParameter("p1"))[0]);
   }

   @Test
   void principalCanBePassedToHostMethods() {
      assertEquals("equal:orgA", exec("'' + host.consume(p)"));
      assertEquals("equal:orgA", exec("'' + host.consume(host.getUser())"));
   }

   @Test
   void principalReturnedByHostMethodIsReadOnly() {
      tryExec("host.getUser().setOrgId('orgVICTIM')");
      tryExec("host.getUser().setGroups(['admins'])");
      tryExec("host.getUser().setProperty('__internal__', 'false')");
      assertEquals("undefined", exec("typeof host.getUser().setOrgId"));
      assertEquals("orgA", exec("'' + host.getUser().getOrgId()"));
      assertPrincipalUnchanged();
   }

   @Test
   void hostMethodReceivesDetachedCopyOfPrincipal() {
      assertHostReceivesDetachedCopy("host.capture(p)");
   }

   @Test
   void hostMethodReceivesDetachedCopyOfPrincipalWithParentScope() {
      assertHostReceivesDetachedCopy("host.capture(vsparameter.__principal__)");
   }

   private void assertHostReceivesDetachedCopy(String script) {
      Object session = new Object();
      principal.setSession(session);
      exec(script);
      XPrincipal copy = host.captured;

      // the host reads the same identity, but not the live session instance
      assertNotNull(copy);
      assertNotSame(principal, copy);
      assertInstanceOf(DestinationUserNameProviderPrincipal.class, copy);
      assertEquals(principal, copy);
      assertEquals("orgA", copy.getOrgId());
      assertEquals(Arrays.asList("readers"), Arrays.asList(copy.getGroups()));
      assertEquals("true", copy.getProperty("__internal__"));
      assertEquals(principal.getSecureID(), ((SRPrincipal) copy).getSecureID());
      assertEquals(principal.getUser(), ((SRPrincipal) copy).getUser());
      assertNull(((SRPrincipal) copy).getSession());

      // changing the copy in Java must leave the session principal unchanged
      copy.setOrgId("orgVICTIM");
      copy.setGroups(new String[] { "admins" });
      copy.setRoles(new IdentityID[] { new IdentityID("Administrator", "orgA") });
      copy.setProperty("__internal__", "false");
      copy.setProperty("virtual", "true");
      copy.setParameter("p2", "x");
      copy.setAlias("mallory");
      copy.setIgnoreLogin(true);
      copy.setProfiling(true);
      ((String[]) copy.getParameter("p1"))[0] = "z";
      ((SRPrincipal) copy).getUser().getUserIdentity().setName("mallory");
      ((SRPrincipal) copy).getUser().setLocale(java.util.Locale.GERMAN);
      ((SRPrincipal) copy).setSession(new Object());

      assertPrincipalUnchanged();
      assertEquals("Everyone", principal.getRoles()[0].getName());
      assertEquals(1, principal.getRoles().length);
      assertNull(principal.getParameter("p2"));
      assertEquals("alice", principal.getUser().getUserIdentity().getName());
      assertNull(principal.getUser().getLocale());
      assertSame(session, principal.getSession());
   }

   @Test
   void tabularUtilIsNotAccessibleFromScript() {
      assertFalse(new SecureClassShutter().visibleToScripts("inetsoft.uql.tabular.TabularUtil"));
      // a blocked class name resolves to a (useless) package, not a Java class
      assertFalse(((String) exec("'' + Packages.inetsoft.uql.tabular.TabularUtil"))
                     .startsWith("[JavaClass"));
      // control: a class in the same package is still visible
      assertTrue(((String) exec("'' + Packages.inetsoft.uql.tabular.TabularQuery"))
                    .startsWith("[JavaClass"));
   }

   private void assertPrincipalUnchanged() {
      assertEquals("alice~;~orgA", principal.getName());
      assertEquals("orgA", principal.getOrgId());
      assertEquals(Arrays.asList("readers"), Arrays.asList(principal.getGroups()));
      assertEquals("true", principal.getProperty("__internal__"));
      assertNull(principal.getProperty("virtual"));
      assertEquals("Alice A", principal.getAlias());
      assertArrayEquals(new String[] { "a", "b" }, (String[]) principal.getParameter("p1"));
      assertFalse(principal.isIgnoreLogin());
      assertFalse(principal.isProfiling());
   }

   private Object exec(String script) {
      Object val = cx.evaluateString(scope, "var p = parameter.__principal__;\n" + script,
                                     "<test>", 1, null);
      return JavaScriptEngine.unwrap(val);
   }

   // run a script expected to fail or be ignored; the principal is checked afterwards
   private void tryExec(String script) {
      try {
         exec(script);
      }
      catch(RhinoException | IllegalArgumentException ignore) {
      }
   }

   public static final class Host {
      Host(Principal principal) {
         this.principal = principal;
      }

      public String consume(Principal p) {
         return principal.equals(p)
            ? "equal:" + ((DestinationUserNameProviderPrincipal) p).getOrgId() : "different";
      }

      public void capture(Principal p) {
         captured = (XPrincipal) p;
      }

      public Principal getUser() {
         return principal;
      }

      private final Principal principal;
      private XPrincipal captured;
   }

   private DestinationUserNameProviderPrincipal principal;
   private Host host;
   private Context cx;
   private Scriptable scope;
}
