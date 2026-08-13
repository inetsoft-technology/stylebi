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
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins the request-mapping prefix and the authorization gates of {@code WizTabularController}, the
 * same way {@code WizDatabaseControllerSecurityTest} pins its sibling's.
 *
 * <p><b>The prefix.</b> {@code /api/wiz/**} is load-bearing rather than cosmetic: {@code CSRFFilter}
 * exempts only that prefix, and {@code WizServiceAuthenticationFilter} validates a wiz caller's
 * bearer token only there. Moved off it, this controller breaks asymmetrically — every POST fails
 * with a CSRF 403 while every GET keeps working.</p>
 *
 * <p><b>The gates.</b> A direct method call bypasses {@code SecuredAspect} entirely, so the
 * {@code @Secured} annotations are untested by any behavioural test in this package — hence the
 * reflective half. The other three gates are ordinary code in the method bodies, which is what makes
 * them the easy ones to lose in a refactor: the rename's DELETE check, the connector capability
 * refresh needs, and the scope on the duplicate check.</p>
 *
 * <p><b>The connector session.</b> Not a permission gate but the same class of problem, and
 * invisible from any single request: {@code TabularUtil.sessionId} is a static {@code ThreadLocal}
 * nothing ever clears, so a handler that leaves it alone inherits the previous request's value on a
 * pooled thread — possibly another user's.</p>
 */
@Tag("core")
class WizTabularControllerSecurityTest {
   @Test
   void everyEndpointLivesUnderTheWizPrefix() {
      List<String> paths = mappedPaths();
      assertFalse(paths.isEmpty(), "expected request mappings on WizTabularController");

      for(String path : paths) {
         assertTrue(path.startsWith(WIZ_PREFIX), "endpoint '" + path + "' must live under '" +
            WIZ_PREFIX + "' or every POST from the wiz portal fails the CSRF filter while every " +
            "GET keeps working");
      }
   }

   @Test
   void mappingsMatchTheFrozenContract() {
      assertEquals(
         Set.of("/api/wiz/tabular/listings",
                "/api/wiz/tabular/listing",
                "/api/wiz/tabular/definition",
                "/api/wiz/tabular/refresh",
                "/api/wiz/tabular/create",
                "/api/wiz/tabular/update",
                "/api/wiz/tabular/check-duplicate"),
         new HashSet<>(mappedPaths()));
   }

   @Test
   void tabularDefinitionIsGatedOnWriteWithAWiredPermissionPath() throws Exception {
      assertWriteGateOnPathParameter(
         WizTabularController.class.getDeclaredMethod(
            "getTabularDefinition", String.class, Principal.class));
   }

   @Test
   void tabularUpdateIsGatedOnWriteWithAWiredPermissionPath() throws Exception {
      assertWriteGateOnPathParameter(
         WizTabularController.class.getDeclaredMethod(
            "updateTabularDataSource", String.class, DataSourceDefinition.class, Principal.class));
   }

   /**
    * The annotation on the update covers WRITE on the path, but a rename also removes the source
    * from its old name, which the service demands DELETE for. It signals that denial with a
    * {@code MessageException} the controller would map to a 200 carrying {@code UNKNOWN} — "could
    * not save" for what is really "not allowed".
    */
   @Test
   void update_deniesARenameWithoutDeleteOnTheOldPath() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.datasourcesService.checkDuplicate("folder/old")).thenReturn(true);
      when(fixture.datasourcesService.checkDuplicate("folder/new")).thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("folder/old"),
         eq(ResourceAction.DELETE)))
         .thenReturn(false);

      // java.lang.SecurityException, the same type SecuredAspect throws for the annotated
      // endpoints, so both denials reach WizControllerErrorHandler as one 403.
      assertThrows(SecurityException.class, () -> fixture.controller.updateTabularDataSource(
         "folder/old", definition("new"), fixture.principal));

      verify(fixture.datasourcesService, never()).updateDataSource(anyString(), any(), any());
   }

   @Test
   void update_asksForNoDeleteWhenTheNameIsUnchanged() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.datasourcesService.checkDuplicate("folder/mongo")).thenReturn(true);

      assertTrue(fixture.controller.updateTabularDataSource(
         "folder/mongo", definition("mongo"), fixture.principal).ok());

      verify(fixture.securityEngine, never()).checkPermission(
         any(), any(ResourceType.class), anyString(), eq(ResourceAction.DELETE));
   }

   /**
    * Refresh has no path to gate on — it recomputes a view from a definition the caller posted —
    * but it does invoke the connector's button and editor methods with a caller-supplied type,
    * property values and {@code clicked} flag, and those are what dial a remote endpoint or touch a
    * file. So the gate is the capability that grants configuring a data source at all.
    */
   @Test
   void refresh_deniesWithoutTheCapabilityToConfigureADataSource() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class, () ->
         fixture.controller.refreshTabularView(definition("mongo"), fixture.principal));

      verify(fixture.datasourcesService, never()).refreshTabularView(any());
   }

   /**
    * The gate is the whole root create rule, both of its branches, and not just the standalone
    * grant. Refresh is a step inside the flow create and update already authorize: a user who may
    * create at the root by holding WRITE on the root folder must not be 403'd halfway through the
    * save that follows.
    */
   @Test
   void refresh_acceptsRootFolderWriteWithoutTheStandaloneGrant() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("/"),
         eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(fixture.datasourcesService.refreshTabularView(any())).thenReturn(definition("mongo"));

      assertNotNull(fixture.controller.refreshTabularView(definition("mongo"), fixture.principal));
      verify(fixture.datasourcesService).refreshTabularView(any());
   }

   /**
    * The answer is an existence oracle: unscoped, any logged-in user could enumerate what exists
    * anywhere in the repository, including data sources they cannot read. Scoped to the folder the
    * caller may create in, which is the only folder the editor ever asks about.
    */
   @Test
   void checkDuplicate_deniesAFolderTheCallerCannotWrite() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> fixture.controller.checkDuplicate("secret/orders", fixture.principal));

      verify(fixture.datasourcesService, never()).checkDuplicate(anyString());
   }

   @Test
   void checkDuplicate_scopesTheCheckToTheParentFolder() throws Exception {
      Fixture fixture = new Fixture();

      fixture.controller.checkDuplicate("folder/orders", fixture.principal);

      verify(fixture.securityEngine).checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("folder"),
         eq(ResourceAction.WRITE));
   }

   /**
    * Set on the way in and cleared on the way out, on every endpoint that can reach
    * {@code TabularUtil.refreshView}. Leaving it unset is not the neutral option: the thread-local
    * is static and nothing else in the codebase clears it, so an unset value on a pooled request
    * thread is whatever the previous request left there, and a connector that resolves an OAuth
    * token by session id would resolve that user's.
    */
   @Test
   void connectorSessionIsBoundToThePrincipalAndClearedAfterwards() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.principal.getName()).thenReturn("alice");
      when(fixture.datasourcesService.refreshTabularView(any())).thenReturn(definition("mongo"));

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class)) {
         fixture.controller.refreshTabularView(definition("mongo"), fixture.principal);

         // Per principal rather than per HTTP session: a wiz caller authenticates with a bearer
         // token and has no cookie jar, so getSession() would mint a throwaway session per call.
         tabularUtil.verify(() -> TabularUtil.setSessionId("wiz:alice"));
         tabularUtil.verify(() -> TabularUtil.setSessionId(null));
      }
   }

   @Test
   void connectorSessionIsClearedEvenWhenTheCallFails() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.principal.getName()).thenReturn("alice");
      when(fixture.datasourcesService.refreshTabularView(any()))
         .thenThrow(new RuntimeException("connector blew up"));

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class)) {
         assertThrows(RuntimeException.class, () ->
            fixture.controller.refreshTabularView(definition("mongo"), fixture.principal));

         tabularUtil.verify(() -> TabularUtil.setSessionId(null));
      }
   }

   @Test
   void connectorSessionIsBoundOnTheSavePathsToo() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.principal.getName()).thenReturn("alice");
      when(fixture.datasourcesService.checkDuplicate("mongo")).thenReturn(false, true);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class)) {
         fixture.controller.createTabularDataSource(
            new WizTabularCreateRequest("", definition("mongo")), fixture.principal);

         tabularUtil.verify(() -> TabularUtil.setSessionId("wiz:alice"));
         tabularUtil.verify(() -> TabularUtil.setSessionId(null));
      }
   }

   /**
    * Asserts that a handler carries the DATA_SOURCE/WRITE gate <em>and</em> that the gate is wired
    * to the path parameter. An annotation whose {@code @PermissionPath} has gone missing still
    * looks correct in a diff, but {@code SecuredAspect} then has no resource to check.
    */
   private static void assertWriteGateOnPathParameter(Method method) {
      Secured secured = method.getAnnotation(Secured.class);
      assertNotNull(secured, method.getName() + " must carry @Secured");
      assertEquals(1, secured.value().length,
                   method.getName() + " should declare exactly one required permission");

      RequiredPermission permission = secured.value()[0];
      assertEquals(ResourceType.DATA_SOURCE, permission.resourceType(),
                   method.getName() + " must be gated on the data source, not another resource");
      assertArrayEquals(new ResourceAction[]{ ResourceAction.WRITE }, permission.actions(),
                        method.getName() + " must require WRITE: a tabular definition carries the " +
                           "connector's own credentials, which is the editor's business and not a " +
                           "data reader's");
      assertEquals("", permission.resource(),
                   method.getName() + " must resolve its resource from the request path, not a " +
                      "fixed resource name");

      List<Parameter> annotated = new ArrayList<>();

      for(Parameter parameter : method.getParameters()) {
         if(parameter.getAnnotation(PermissionPath.class) != null) {
            annotated.add(parameter);
         }
      }

      assertEquals(1, annotated.size(), method.getName() +
         " must have exactly one @PermissionPath parameter, or SecuredAspect has no path to check");

      RequestParam requestParam = annotated.get(0).getAnnotation(RequestParam.class);
      assertNotNull(requestParam,
                    "the @PermissionPath parameter of " + method.getName() +
                       " must be the request's own path parameter");
      assertEquals("path", requestParam.value(),
                   "the gate must check the same path the handler acts on");
   }

   /** Every mapped path of the controller, class-level prefix included. */
   private static List<String> mappedPaths() {
      RequestMapping classMapping = WizTabularController.class.getAnnotation(RequestMapping.class);
      String prefix = classMapping == null || classMapping.value().length == 0
         ? "" : classMapping.value()[0];
      List<String> paths = new ArrayList<>();

      for(Method method : WizTabularController.class.getDeclaredMethods()) {
         List<String> methodPaths = new ArrayList<>();
         GetMapping get = method.getAnnotation(GetMapping.class);
         PostMapping post = method.getAnnotation(PostMapping.class);
         RequestMapping generic = method.getAnnotation(RequestMapping.class);

         if(get != null) {
            methodPaths.addAll(Arrays.asList(get.value()));
         }

         if(post != null) {
            methodPaths.addAll(Arrays.asList(post.value()));
         }

         if(generic != null) {
            methodPaths.addAll(Arrays.asList(generic.value()));
         }

         for(String path : methodPaths) {
            paths.add(prefix + path);
         }
      }

      return paths;
   }

   private static DataSourceDefinition definition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName(name);
      definition.setType("MongoDB");

      return definition;
   }

   /** The controller with every collaborator mocked, and every permission granted by default. */
   private static final class Fixture {
      Fixture() throws Exception {
         when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                             any(ResourceAction.class))).thenReturn(true);

         // The update path refuses anything that is not tabular, so the stored source has to be one
         // for every test whose subject is something else.
         when(xrepository.getDataSource(anyString())).thenReturn(mock(TabularDataSource.class));
         controller = new WizTabularController(datasourcesService, securityEngine, xrepository);
      }

      final DatasourcesService datasourcesService = mock(DatasourcesService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final XRepository xrepository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final WizTabularController controller;
   }

   private static final String WIZ_PREFIX = "/api/wiz/";
}
