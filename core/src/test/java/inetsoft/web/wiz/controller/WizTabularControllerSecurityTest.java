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
import inetsoft.web.portal.data.DataSourceDefinition;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.WizTabularSaveResult;
import inetsoft.web.wiz.request.WizTabularCreateRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the request-mapping prefix and the authorization gates of {@code WizTabularController}.
 *
 * <p><b>The prefix.</b> {@code /api/wiz/**} is load-bearing rather than cosmetic, for the same
 * reason spelled out in {@code AdminAiControllerMappingTest}: {@code CSRFFilter} exempts only that
 * prefix, and {@code WizServiceAuthenticationFilter} validates a wiz caller's bearer token only
 * there. Moved off it, this controller breaks asymmetrically — every POST fails with a CSRF 403
 * while every GET keeps working, which is the harder half to diagnose from the client. Four of these
 * seven endpoints are POSTs, including both that write.</p>
 *
 * <p><b>The gates.</b> Two are {@code @Secured} annotations, and an annotation without a
 * {@code @PermissionPath} parameter has no path to check — so both halves are asserted. The other
 * two live in method bodies, where a refactor loses them without leaving a trace in the annotation
 * list: the create check, whose root fall-back is easy to drop, and the rename check, which exists
 * because a rename is a delete of the old name. The one endpoint that deliberately has no gate,
 * {@code /tabular/refresh}, is asserted to have none, so that adding one is a decision rather than
 * an accident.</p>
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

   /**
    * A local handler takes precedence over {@code WizControllerErrorHandler}, so one declared here
    * would catch the {@code SecurityException}s these gates throw and answer with whatever status it
    * chose — in practice a 400, turning a permission denial into what reads like a bad request.
    */
   @Test
   void declaresNoLocalExceptionHandler() {
      for(Method method : WizTabularController.class.getDeclaredMethods()) {
         assertNull(method.getAnnotation(ExceptionHandler.class), method.getName() +
            " must not declare a local @ExceptionHandler: it would take precedence over " +
            "WizControllerErrorHandler and downgrade a permission 403");
      }
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

   @Test
   void create_deniesAndNeverSavesWithoutWriteOnTheParentFolder() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq(FOLDER),
         eq(ResourceAction.WRITE)))
         .thenReturn(false);

      WizTabularCreateRequest request = new WizTabularCreateRequest(FOLDER, definition("Orders"));

      assertThrows(SecurityException.class,
                   () -> fixture.controller.createTabularDataSource(request, fixture.principal));

      verify(fixture.datasourcesService, never())
         .createNewDataSource(any(), anyBoolean(), any());

      // The root fall-back must not rescue a named folder: CREATE_DATA_SOURCE lets a user own data
      // sources at the root, not write into someone else's folder.
      verify(fixture.securityEngine, never()).checkPermission(
         any(), eq(ResourceType.CREATE_DATA_SOURCE), anyString(), any());
   }

   /**
    * The permission is checked against the request's folder, so the definition's own
    * {@code parentPath} must never decide where the data source lands — otherwise a caller
    * authorized for one folder could name another in the body and be saved there.
    */
   @Test
   void create_savesIntoTheAuthorizedFolderRatherThanTheOneTheBodyClaims() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq(FOLDER),
         eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(fixture.datasourcesService.checkDuplicate(anyString())).thenReturn(false);

      DataSourceDefinition definition = definition("Orders");
      definition.setParentPath("Someone Elses Folder");

      WizTabularSaveResult result = fixture.controller.createTabularDataSource(
         new WizTabularCreateRequest(FOLDER, definition), fixture.principal);

      assertTrue(result.ok());
      assertEquals(EXISTING_PATH, result.path());

      ArgumentCaptor<DataSourceDefinition> saved =
         ArgumentCaptor.forClass(DataSourceDefinition.class);
      verify(fixture.datasourcesService)
         .createNewDataSource(saved.capture(), eq(false), eq(fixture.principal));
      assertEquals(FOLDER, saved.getValue().getParentPath(),
                   "the parent folder must come from the checked request field, not from the body");
   }

   /**
    * At the root only, {@code CREATE_DATA_SOURCE} stands in for folder WRITE — that grant is what
    * lets a user own data sources without holding the root folder itself.
    */
   @Test
   void create_fallsBackToTheCreateDataSourceGrantAtTheRoot() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("/"),
         eq(ResourceAction.WRITE)))
         .thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.CREATE_DATA_SOURCE), eq("*"),
         eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(fixture.datasourcesService.checkDuplicate(anyString())).thenReturn(false);

      WizTabularSaveResult result = fixture.controller.createTabularDataSource(
         new WizTabularCreateRequest(null, definition("Orders")), fixture.principal);

      assertTrue(result.ok(), "the standalone create grant must be honoured at the root");
      assertEquals("Orders", result.path());
      verify(fixture.datasourcesService).createNewDataSource(any(), eq(false), eq(fixture.principal));
   }

   /**
    * Renaming a data source deletes the old name, and StyleBI demands DELETE for it. The gate is
    * repeated here so the denial arrives as a 403 rather than as the translated message the service
    * raises, which the save result would have to report as an unrecognized failure.
    */
   @Test
   void update_deniesARenameWithoutDeleteOnTheExistingPath() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq(EXISTING_PATH),
         eq(ResourceAction.DELETE)))
         .thenReturn(false);
      when(fixture.xrepository.getDataSource(EXISTING_PATH))
         .thenReturn(mock(TabularDataSource.class));
      when(fixture.datasourcesService.getDataSourceDefinition(eq(EXISTING_PATH), any()))
         .thenReturn(definition("Orders"));

      DataSourceDefinition renamed = definition("Renamed");

      assertThrows(SecurityException.class,
                   () -> fixture.controller.updateTabularDataSource(
                      EXISTING_PATH, renamed, fixture.principal));

      verify(fixture.datasourcesService, never()).updateDataSource(any(), any(), any());
   }

   /**
    * The identity of what is written comes from the query parameter the gate checked, never from the
    * body: {@code updateDataSource} resolves the stored data source as
    * {@code definition.parentPath + "/" + name}, so a body naming a different folder would be a
    * write the gate never saw.
    */
   @Test
   void update_takesTheParentFolderFromTheGatedPathRatherThanTheBody() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.xrepository.getDataSource(EXISTING_PATH))
         .thenReturn(mock(TabularDataSource.class));
      when(fixture.datasourcesService.getDataSourceDefinition(eq(EXISTING_PATH), any()))
         .thenReturn(definition("Orders"));

      DataSourceDefinition definition = definition("Orders");
      definition.setParentPath("Someone Elses Folder");

      WizTabularSaveResult result =
         fixture.controller.updateTabularDataSource(EXISTING_PATH, definition, fixture.principal);

      assertTrue(result.ok());
      assertEquals(EXISTING_PATH, result.path());

      ArgumentCaptor<DataSourceDefinition> saved =
         ArgumentCaptor.forClass(DataSourceDefinition.class);
      verify(fixture.datasourcesService)
         .updateDataSource(eq("Orders"), saved.capture(), eq(fixture.principal));
      assertEquals(FOLDER, saved.getValue().getParentPath(),
                   "the parent folder must come from the gated path, not from the body");

      // Not a rename, so DELETE is not demanded — the check is conditional on purpose.
      verify(fixture.securityEngine, never()).checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), anyString(), eq(ResourceAction.DELETE));
   }

   /**
    * Refreshing names no stored resource: the service builds a new bean from the type in the body,
    * applies the caller's own values and hands the tree back. There is nothing to check a permission
    * against, and inventing one would reject the legitimate "fill in a form before creating" case.
    */
   @Test
   void refresh_requiresNoPermissionBecauseItTouchesNothingStored() {
      Fixture fixture = new Fixture();
      DataSourceDefinition definition = definition("Orders");
      definition.setSequenceNumber(7);
      when(fixture.datasourcesService.refreshTabularView(definition)).thenReturn(definition);

      DataSourceDefinition refreshed =
         fixture.controller.refreshTabularView(definition, fixture.principal);

      assertEquals(7, refreshed.getSequenceNumber(),
                   "the sequence number must survive so a stale answer can be discarded");
      verifyNoInteractions(fixture.securityEngine);
      verifyNoInteractions(fixture.xrepository);
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
                        method.getName() + " must require WRITE: a tabular definition carries every " +
                           "credential the connection holds, in clear text");
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

   /** A definition carrying only what the controller itself reads: the name and the type. */
   private static DataSourceDefinition definition(String name) {
      DataSourceDefinition definition = new DataSourceDefinition();
      definition.setName(name);
      definition.setType("mongo");

      return definition;
   }

   /** The controller with every collaborator mocked. */
   private static final class Fixture {
      Fixture() {
         controller = new WizTabularController(datasourcesService, securityEngine, xrepository);
      }

      final DatasourcesService datasourcesService = mock(DatasourcesService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final XRepository xrepository = mock(XRepository.class);
      final Principal principal = mock(Principal.class);
      final WizTabularController controller;
   }

   private static final String WIZ_PREFIX = "/api/wiz/";
   private static final String FOLDER = "Examples";
   private static final String EXISTING_PATH = "Examples/Orders";
}
