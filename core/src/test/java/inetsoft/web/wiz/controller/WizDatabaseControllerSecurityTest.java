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
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceConnectionStatusRequest;
import inetsoft.web.portal.data.DataSourceStatus;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.PermissionPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizDatabaseTestRequest;
import inetsoft.web.wiz.request.WizDatasourceStatusRequest;
import inetsoft.web.wiz.request.WizEndpointCatalogRequest;
import inetsoft.web.wiz.request.WizFolderCreateRequest;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the request-mapping prefix and the authorization gates of {@code WizDatabaseController}.
 *
 * <p><b>The prefix.</b> {@code /api/wiz/**} is load-bearing rather than cosmetic, for the same
 * reason spelled out in {@code AdminAiControllerMappingTest}: {@code CSRFFilter} exempts only that
 * prefix, and {@code WizServiceAuthenticationFilter} validates a wiz caller's bearer token only
 * there. Moved off it, this controller breaks asymmetrically — every POST fails with a CSRF 403
 * while every GET keeps working, which is the harder half to diagnose from the client.</p>
 *
 * <p><b>The gates.</b> Neither {@code DatabaseDatasourcesService} nor {@code DataSourceStatusService}
 * checks permissions on its reads, so the checks the native controllers perform before delegating
 * are this controller's own responsibility. Two of them are {@code @Secured} annotations, and an
 * annotation without a {@code @PermissionPath} parameter has no path to check — so both halves are
 * asserted. The third, on the connection test, is conditional and therefore cannot be an annotation
 * at all; being ordinary code inside the method body, it is also the one most easily lost in a
 * refactor, which is why it gets three cases here.</p>
 */
@Tag("core")
class WizDatabaseControllerSecurityTest {
   @Test
   void everyEndpointLivesUnderTheWizPrefix() {
      List<String> paths = mappedPaths();
      assertFalse(paths.isEmpty(), "expected request mappings on WizDatabaseController");

      for(String path : paths) {
         assertTrue(path.startsWith(WIZ_PREFIX), "endpoint '" + path + "' must live under '" +
            WIZ_PREFIX + "' or every POST from the wiz portal fails the CSRF filter while every " +
            "GET keeps working");
      }
   }

   @Test
   void mappingsMatchTheFrozenContract() {
      assertEquals(
         Set.of("/api/wiz/datasources/browser",
                "/api/wiz/datasources/search",
                "/api/wiz/datasources/statuses",
                "/api/wiz/datasources/endpoint-catalog",
                "/api/wiz/databases/meta",
                "/api/wiz/databases/template",
                "/api/wiz/databases/definition",
                "/api/wiz/databases/default-test-query",
                "/api/wiz/databases/test",
                "/api/wiz/databases/create",
                "/api/wiz/databases/update",
                "/api/wiz/datasources/folders/create"),
         new HashSet<>(mappedPaths()));
   }

   @Test
   void databaseDefinitionIsGatedOnWriteWithAWiredPermissionPath() throws Exception {
      assertWriteGateOnPathParameter(
         WizDatabaseController.class.getDeclaredMethod(
            "getDatabaseDefinition", String.class, Principal.class));
   }

   @Test
   void databaseUpdateIsGatedOnWriteWithAWiredPermissionPath() throws Exception {
      assertWriteGateOnPathParameter(
         WizDatabaseController.class.getDeclaredMethod(
            "updateDatabase", String.class, WizDatabaseDefinition.class, Principal.class));
   }

   /**
    * A test that names an existing database is a credential read: the controller fills in
    * {@code oldName} from the path so an unmodified connection can be tested, which is exactly what
    * triggers StyleBI's stored-password recovery. Without this gate a caller could pass a victim's
    * path together with a definition pointing at a host they control, and have the server dial out
    * to that host using the victim's real password.
    */
   @Test
   void testDatabaseConnection_deniesAndNeverConnectsWhenPathGivenWithoutWrite() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq(EXISTING_PATH),
         eq(ResourceAction.WRITE)))
         .thenReturn(false);

      WizDatabaseTestRequest request =
         new WizDatabaseTestRequest(EXISTING_PATH, mysqlDefinition());

      // java.lang.SecurityException, the same type SecuredAspect throws for the annotated
      // endpoints, so both denials reach WizControllerErrorHandler as one 403.
      assertThrows(SecurityException.class,
                   () -> fixture.controller.testDatabaseConnection(request, fixture.principal));

      verify(fixture.databaseDatasourcesService, never())
         .testDataSourceConnection(any(), any(), any(), anyBoolean());
   }

   @Test
   void testDatabaseConnection_proceedsWhenPathGivenAndCallerHoldsWrite() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq(EXISTING_PATH),
         eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(fixture.databaseDatasourcesService.testDataSourceConnection(
         any(), any(), any(), anyBoolean()))
         .thenReturn(new ConnectionStatus(null, true));

      WizDatabaseTestRequest request =
         new WizDatabaseTestRequest(EXISTING_PATH, mysqlDefinition());
      WizConnectionTestResult result =
         fixture.controller.testDatabaseConnection(request, fixture.principal);

      assertTrue(result.connected(), "an authorized test should report the connection outcome");

      ArgumentCaptor<DatabaseDefinition> definition =
         ArgumentCaptor.forClass(DatabaseDefinition.class);
      verify(fixture.databaseDatasourcesService).testDataSourceConnection(
         eq(EXISTING_PATH), definition.capture(), eq(fixture.principal), eq(false));

      // oldName is what makes the stored password recoverable, i.e. what makes the gate above
      // necessary in the first place.
      assertEquals("orders", definition.getValue().getOldName(),
                   "oldName must come from the request path, not from the client");
   }

   @Test
   void testDatabaseConnection_skipsThePermissionCheckWhenNoPathIsGiven() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.databaseDatasourcesService.testDataSourceConnection(
         any(), any(), any(), anyBoolean()))
         .thenReturn(new ConnectionStatus(null, true));

      WizDatabaseTestRequest request = new WizDatabaseTestRequest(null, mysqlDefinition());
      WizConnectionTestResult result =
         fixture.controller.testDatabaseConnection(request, fixture.principal);

      assertTrue(result.connected(),
                 "testing a connection that does not exist yet must not require a permission");

      // Nothing is stored under a path that was never given, so there is no credential to recover
      // and nothing to check a permission against.
      verifyNoInteractions(fixture.securityEngine);

      ArgumentCaptor<DatabaseDefinition> definition =
         ArgumentCaptor.forClass(DatabaseDefinition.class);
      verify(fixture.databaseDatasourcesService).testDataSourceConnection(
         eq(""), definition.capture(), eq(fixture.principal), eq(false));
      assertNull(definition.getValue().getOldName(),
                 "a database that does not exist yet has no stored password to recover");
   }

   /**
    * {@code DataSourceStatusService} checks nothing, so the paths it is handed must already be the
    * readable ones. An unreadable path is dropped rather than rejected: the client pairs answers
    * with requests by path, so a short answer degrades to "no status known" instead of failing the
    * whole folder listing.
    */
   @Test
   void datasourceStatuses_dropsPathsTheCallerCannotRead() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/readable"),
         eq(ResourceAction.READ)))
         .thenReturn(true);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE), eq("/secret"),
         eq(ResourceAction.READ)))
         .thenReturn(false);
      when(fixture.dataSourceStatusService.getDataSourceConnectionStatuses(any(), any()))
         .thenReturn(List.of(
            DataSourceStatus.builder().connected(true).message("connected").build()));

      List<WizDatasourceStatus> result = fixture.controller.getDatasourceStatuses(
         new WizDatasourceStatusRequest(List.of("/readable", "/secret")), fixture.principal);

      ArgumentCaptor<DataSourceConnectionStatusRequest> statusRequest =
         ArgumentCaptor.forClass(DataSourceConnectionStatusRequest.class);
      verify(fixture.dataSourceStatusService)
         .getDataSourceConnectionStatuses(statusRequest.capture(), eq(fixture.principal));

      assertEquals(List.of("/readable"), statusRequest.getValue().paths(),
                   "the status service must never be handed a path the caller cannot read");
      assertEquals(List.of("/readable"), result.stream().map(WizDatasourceStatus::path).toList());
   }

   @Test
   void datasourceStatuses_neverCallsTheServiceWhenNoPathIsReadable() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE), any(String.class), eq(ResourceAction.READ)))
         .thenReturn(false);

      List<WizDatasourceStatus> result = fixture.controller.getDatasourceStatuses(
         new WizDatasourceStatusRequest(List.of("/secret")), fixture.principal);

      assertTrue(result.isEmpty(), "an unreadable path yields no status rather than an error");
      verifyNoInteractions(fixture.dataSourceStatusService);
   }

   @Test
   void createFolder_succeedsUnderAnExistingParent() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("Examples"),
         eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(fixture.dataSourceBrowserService.checkFolderDuplicate("Examples/Sales"))
         .thenReturn(new CheckDuplicateResponse(false));

      WizFolderSaveResult result = fixture.controller.createFolder(
         new WizFolderCreateRequest("Examples", "Sales"), fixture.principal);

      assertTrue(result.ok(), "an unclaimed name under a writable parent must be created");
      assertEquals("Examples/Sales", result.path());
      assertNull(result.reason());
      verify(fixture.dataSourceBrowserService)
         .addDatasourceFolder(eq("Examples/Sales"), any(), eq(fixture.principal), eq(false));
   }

   /**
    * {@code addDatasourceFolder} performs no duplicate check of its own, so the controller must run
    * {@code checkFolderDuplicate} itself before calling it — otherwise a name collision would either
    * silently overwrite the existing folder or fail deep inside the repository with no stable reason
    * code to report.
    */
   @Test
   void createFolder_reportsDuplicateNameWithoutCallingAddFolder() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("Examples"),
         eq(ResourceAction.WRITE)))
         .thenReturn(true);
      when(fixture.dataSourceBrowserService.checkFolderDuplicate("Examples/Sales"))
         .thenReturn(new CheckDuplicateResponse(true));

      WizFolderSaveResult result = fixture.controller.createFolder(
         new WizFolderCreateRequest("Examples", "Sales"), fixture.principal);

      assertFalse(result.ok());
      assertEquals(WizFolderSaveResult.DUPLICATE_NAME, result.reason());
      assertNull(result.path());
      verify(fixture.dataSourceBrowserService, never())
         .addDatasourceFolder(any(), any(), any(), anyBoolean());
   }

   /**
    * The case {@code userScope} exists for: a caller with no WRITE on the root folder, only the
    * standalone {@code CREATE_DATA_SOURCE} grant, must come out of {@code addDatasourceFolder} owning
    * the folder they just created — otherwise they would have no permission entry on it and no root
    * WRITE to inherit from, and would be locked out of their own new folder immediately.
    */
   @Test
   void createFolder_grantsCreatorOwnershipWhenOnlyCreateDataSourcePermissionApplies() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("/"),
         eq(ResourceAction.WRITE)))
         .thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.CREATE_DATA_SOURCE), eq("*"),
         eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(fixture.dataSourceBrowserService.checkFolderDuplicate("NewFolder"))
         .thenReturn(new CheckDuplicateResponse(false));

      WizFolderSaveResult result = fixture.controller.createFolder(
         new WizFolderCreateRequest(null, "NewFolder"), fixture.principal);

      assertTrue(result.ok());
      verify(fixture.dataSourceBrowserService)
         .addDatasourceFolder(eq("NewFolder"), any(), eq(fixture.principal), eq(true));
   }

   @Test
   void createFolder_deniesWithoutWriteOnTheParentOrRootCreatePermission() throws Exception {
      Fixture fixture = new Fixture();
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("/"),
         eq(ResourceAction.WRITE)))
         .thenReturn(false);
      when(fixture.securityEngine.checkPermission(
         eq(fixture.principal), eq(ResourceType.CREATE_DATA_SOURCE), eq("*"),
         eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizFolderCreateRequest request = new WizFolderCreateRequest(null, "NewFolder");

      assertThrows(SecurityException.class,
                   () -> fixture.controller.createFolder(request, fixture.principal));
      verifyNoInteractions(fixture.dataSourceBrowserService);
   }

   @Test
   void createFolder_rejectsANameThatIsNotASinglePathSegment() {
      Fixture fixture = new Fixture();
      WizFolderCreateRequest request = new WizFolderCreateRequest("Examples", "Sales/2026");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> fixture.controller.createFolder(request, fixture.principal));
      assertEquals(400, ex.getStatusCode().value());
      verifyNoInteractions(fixture.dataSourceBrowserService);
   }

   // "." and ".." are each a single path segment by the slash check alone, but neither names a real
   // folder — letting either through would concatenate into a path whose parent-directory semantics
   // nothing downstream (lastSegment, the breadcrumb builder) is prepared to handle.
   @Test
   void createFolder_rejectsDotAsAName() {
      Fixture fixture = new Fixture();
      WizFolderCreateRequest request = new WizFolderCreateRequest("Examples", ".");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> fixture.controller.createFolder(request, fixture.principal));
      assertEquals(400, ex.getStatusCode().value());
      verifyNoInteractions(fixture.dataSourceBrowserService);
   }

   @Test
   void createFolder_rejectsDotDotAsAName() {
      Fixture fixture = new Fixture();
      WizFolderCreateRequest request = new WizFolderCreateRequest("Examples", "..");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> fixture.controller.createFolder(request, fixture.principal));
      assertEquals(400, ex.getStatusCode().value());
      verifyNoInteractions(fixture.dataSourceBrowserService);
   }

   /**
    * A null or blank element in {@code types} is neither "no catalogue" nor "unreadable" - it does
    * not name a type at all, and {@code Config.getQueryClass} does not reject it. Left unfiltered it
    * would surface as a literal, uninterpretable entry in the response instead of being dropped
    * before the lookup ever runs.
    */
   @Test
   void endpointCatalog_dropsNullAndBlankTypesFromTheResponse() {
      Fixture fixture = new Fixture();
      WizEndpointCatalogRequest request = new WizEndpointCatalogRequest();
      request.setTypes(Arrays.asList(null, "  ", MySQLDatabaseType.TYPE));

      WizEndpointCatalogResponse response = fixture.controller.getEndpointCatalog(request);

      assertEquals(List.of(MySQLDatabaseType.TYPE), response.unavailable(),
                   "a null or blank type must never surface as a literal entry in the response");
      assertTrue(response.notCatalogued().isEmpty());
      assertTrue(response.catalogs().isEmpty());
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
                        method.getName() + " must require WRITE: the connection settings are the " +
                           "editor's business, not a data reader's");
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
      RequestMapping classMapping = WizDatabaseController.class.getAnnotation(RequestMapping.class);
      String prefix = classMapping == null || classMapping.value().length == 0
         ? "" : classMapping.value()[0];
      List<String> paths = new ArrayList<>();

      for(Method method : WizDatabaseController.class.getDeclaredMethods()) {
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

   /** A definition of a type the editor actually offers, so the mapping to the stored model works. */
   private static WizDatabaseDefinition mysqlDefinition() {
      return new WizDatabaseDefinition(
         "orders", null, MySQLDatabaseType.TYPE, new WizNetworkLocation("attacker.example", 3306),
         new WizAuthentication(true, "victim", null), null, false, -1, 3, null, false, false);
   }

   /** The controller with every collaborator mocked. */
   private static final class Fixture {
      Fixture() {
         doReturn(new MySQLDatabaseType())
            .when(databaseTypeService).getDatabaseType(MySQLDatabaseType.TYPE);
         controller = new WizDatabaseController(
            dataSourceBrowserService, dataSourceStatusService, databaseDatasourcesService,
            databaseTypeService, securityEngine, uqlConfig, xrepository, endpointCatalogReader);
      }

      final DataSourceBrowserService dataSourceBrowserService = mock(DataSourceBrowserService.class);
      final DataSourceStatusService dataSourceStatusService = mock(DataSourceStatusService.class);
      final DatabaseDatasourcesService databaseDatasourcesService =
         mock(DatabaseDatasourcesService.class);
      final DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final Config uqlConfig = mock(Config.class);
      final XRepository xrepository = mock(XRepository.class);
      final EndpointCatalogReader endpointCatalogReader = mock(EndpointCatalogReader.class);
      final Principal principal = mock(Principal.class);
      final WizDatabaseController controller;
   }

   private static final String WIZ_PREFIX = "/api/wiz/";
   private static final String EXISTING_PATH = "/orders";
}
