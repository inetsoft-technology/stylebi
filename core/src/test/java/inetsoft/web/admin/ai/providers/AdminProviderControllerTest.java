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
package inetsoft.web.admin.ai.providers;

/*
 * Test strategy (bug 76602)
 *
 * Covers the four new bare-action endpoints this bug adds to AdminProviderController:
 *   1. testAuthenticationProviderConnection -- must resolve the model via
 *      authenticationProviderService.getAuthenticationProvider(name) (which already sets
 *      oldName := name), never accept/forward a caller-supplied model, so a masked placeholder
 *      password is never tested literally (the bug's load-bearing fix).
 *   2. getAuthenticationProviderDirectory -- kind dispatch (users/groups/roles) + invalid kind.
 *   3/4. clear*ProviderCacheByName -- name-to-index resolution + FILE-provider refuse-loud.
 * requireSiteAdmin/requireExists are exercised indirectly (not-found path); the full site-admin
 * gate is already covered by every other endpoint's existing behavior and AdminAiCallerGuard's own
 * tests, not re-verified here.
 *
 * Also covers bug #76588: AdminProviderController has no @ControllerAdvice equivalent for
 * AdminChangesetApplyService.TaskTokenMismatchException (AdminExceptionHandler's shared advice
 * only maps it to a generic 500), so the local @ExceptionHandler added alongside the existing
 * handlePlanHashMismatch is load-bearing, not optional -- see handleTaskTokenMismatch* below.
 */

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminProviderControllerTest {
   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private AuthorizationProviderService authorizationProviderService;
   @Mock private ProviderChangePlanService planService;
   @Mock private ProviderChangesetApplyService applyService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;

   private AdminProviderController controller;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setUp() {
      controller = new AdminProviderController(authenticationProviderService,
                                                authorizationProviderService, planService,
                                                applyService);
      lenient().when(user.getName()).thenReturn("admin");

      // requireSiteAdmin() gates every endpoint on AdminAiCallerGuard's bearer-token check first,
      // then OrganizationManager.isSiteAdmin -- both mocked here rather than in each test, mirroring
      // AdminPropertiesControllerTest's own setUp precedent for the identical guard.
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(user)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   // -------------------------------------------------------------------------
   // testAuthenticationProviderConnection() -- oldName/placeholder-forcing (bug 76602)
   // -------------------------------------------------------------------------

   @Test void testConnection_resolvesModelServerSideRatherThanAcceptingOne() throws Exception {
      stubProviderList("p1");
      // getAuthenticationProvider(name) is the real service method that already forces
      // oldName := name (AuthenticationProviderService.java:119) -- the controller must call
      // THIS, not accept any model from the request, so a caller can never even smuggle a
      // literal placeholder password through this endpoint.
      AuthenticationProviderModel resolvedModel = AuthenticationProviderModel.builder()
         .providerName("p1").oldName("p1").providerType(SecurityProviderType.LDAP).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(resolvedModel);
      when(authenticationProviderService.testConnection(resolvedModel)).thenReturn("Connection is OK.");

      ConnectionStatus result = controller.testAuthenticationProviderConnection("p1", user);

      assertEquals("Connection is OK.", result.getStatus());
      verify(authenticationProviderService).testConnection(resolvedModel);
   }

   @Test void testConnection_unknownName_throwsStructuredNotFound() throws Exception {
      stubProviderList();

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.testAuthenticationProviderConnection("missing", user));
      assertEquals(404, ex.getStatusCode().value());
      verify(authenticationProviderService, never()).testConnection(any());
   }

   // -------------------------------------------------------------------------
   // getAuthenticationProviderDirectory() -- kind dispatch (bug 76602)
   // -------------------------------------------------------------------------

   @Test void directory_usersKind_delegatesToGetUsers() throws Exception {
      stubProviderList("p1");
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p1").oldName("p1").providerType(SecurityProviderType.FILE).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(model);
      IdentityListModel expected = IdentityListModel.builder().ids(new IdentityID[0]).type(0).build();
      when(authenticationProviderService.getUsers(model)).thenReturn(expected);

      IdentityListModel result = controller.getAuthenticationProviderDirectory("p1", "users", user);

      assertSame(expected, result);
   }

   @Test void directory_groupsKind_delegatesToGetGroups() throws Exception {
      stubProviderList("p1");
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p1").oldName("p1").providerType(SecurityProviderType.FILE).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(model);
      IdentityListModel expected = IdentityListModel.builder().ids(new IdentityID[0]).type(1).build();
      when(authenticationProviderService.getGroups(model)).thenReturn(expected);

      IdentityListModel result = controller.getAuthenticationProviderDirectory("p1", "groups", user);

      assertSame(expected, result);
   }

   @Test void directory_rolesKind_delegatesToGetRoles() throws Exception {
      stubProviderList("p1");
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p1").oldName("p1").providerType(SecurityProviderType.FILE).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(model);
      IdentityListModel expected = IdentityListModel.builder().ids(new IdentityID[0]).type(2).build();
      when(authenticationProviderService.getRoles(model)).thenReturn(expected);

      IdentityListModel result = controller.getAuthenticationProviderDirectory("p1", "roles", user);

      assertSame(expected, result);
   }

   @Test void directory_unrecognizedKind_throwsIllegalArgument() throws Exception {
      stubProviderList("p1");
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("p1").oldName("p1").providerType(SecurityProviderType.FILE).build();
      when(authenticationProviderService.getAuthenticationProvider("p1")).thenReturn(model);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.getAuthenticationProviderDirectory("p1", "permissions", user));
      assertTrue(ex.getMessage().contains("kind"));
   }

   // -------------------------------------------------------------------------
   // clearAuthenticationProviderCacheByName() -- name-to-index + FILE refuse-loud (bug 76602)
   // -------------------------------------------------------------------------

   @Test void clearAuthenticationCache_cacheableProvider_clearsByResolvedIndex() throws Exception {
      stubProviderList("keep", "p1");
      AuthenticationChain chain = mock(AuthenticationChain.class);
      AuthenticationProvider keepProvider = mock(AuthenticationProvider.class);
      // AuthenticationProvider extends CachableProvider directly (confirmed by refute) -- a plain
      // mock with isCacheEnabled() stubbed true stands in for LdapAuthenticationProvider's own
      // hardcoded-true override, no need for a hand-written fake implementing the whole interface.
      AuthenticationProvider ldapProvider = mock(AuthenticationProvider.class);
      lenient().when(ldapProvider.isCacheEnabled()).thenReturn(true);
      when(chain.getProviders()).thenReturn(List.of(keepProvider, ldapProvider));
      when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.of(chain));
      SecurityProviderStatus expected = SecurityProviderStatus.builder()
         .name("p1").label("p1").cacheEnabled(true).cacheAge(0).loading(false).build();
      when(authenticationProviderService.clearAuthenticationProviderCache(1)).thenReturn(expected);

      SecurityProviderStatus result = controller.clearAuthenticationProviderCacheByName("p1", user);

      assertSame(expected, result);
      verify(authenticationProviderService).clearAuthenticationProviderCache(1);
   }

   @Test void clearAuthenticationCache_fileProvider_refusesLoudRatherThanNoOp() throws Exception {
      stubProviderList("p1");
      AuthenticationChain chain = mock(AuthenticationChain.class);
      AuthenticationProvider fileProvider = mock(AuthenticationProvider.class); // not CachableProvider
      when(chain.getProviders()).thenReturn(List.of(fileProvider));
      when(authenticationProviderService.getAuthenticationChain()).thenReturn(Optional.of(chain));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.clearAuthenticationProviderCacheByName("p1", user));
      assertTrue(ex.getMessage().contains("no cache to clear"));
      verify(authenticationProviderService, never()).clearAuthenticationProviderCache(anyInt());
   }

   @Test void clearAuthenticationCache_unknownName_throwsStructuredNotFound() {
      stubProviderList();

      assertThrows(ResponseStatusException.class,
         () -> controller.clearAuthenticationProviderCacheByName("missing", user));
   }

   // -------------------------------------------------------------------------
   // clearAuthorizationProviderCacheByName() -- mirrors the authentication-chain behavior
   // -------------------------------------------------------------------------

   @Test void clearAuthorizationCache_fileProvider_refusesLoud() throws Exception {
      stubAuthzProviderList("z1");
      AuthorizationChain chain = mock(AuthorizationChain.class);
      AuthorizationProvider fileProvider = mock(AuthorizationProvider.class); // not CachableProvider
      when(chain.getProviders()).thenReturn(List.of(fileProvider));
      when(authorizationProviderService.getAuthorizationChain()).thenReturn(Optional.of(chain));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> controller.clearAuthorizationProviderCacheByName("z1", user));
      assertTrue(ex.getMessage().contains("no cache to clear"));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubProviderList(String... names) {
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      for(String name : names) {
         builder.addProviders(SecurityProviderStatus.builder()
            .name(name).label(name).cacheEnabled(false).cacheAge(0).loading(false).build());
      }

      lenient().when(authenticationProviderService.getProviderListModel()).thenReturn(builder.build());
   }

   private void stubAuthzProviderList(String... names) {
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      for(String name : names) {
         builder.addProviders(SecurityProviderStatus.builder()
            .name(name).label(name).cacheEnabled(false).cacheAge(0).loading(false).build());
      }

      lenient().when(authorizationProviderService.getProviderListModel()).thenReturn(builder.build());
   }

   // -------------------------------------------------------------------------
   // handleTaskTokenMismatch() -- 409 mapping for TaskTokenMismatchException (bug #76588)
   // -------------------------------------------------------------------------

   @Test void handleTaskTokenMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("create p1", List.of(), true, true,
                                              "hash456", "token456");
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current,
            "taskToken: does not match the current plan; re-review before applying");

      Map<String, Object> actual = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      ResolvedPlan returnedPlan = (ResolvedPlan) actual.get("plan");
      assertEquals(current.task(), returnedPlan.task());
      assertEquals(current.planHash(), returnedPlan.planHash());
      // The 409 body must never hand back a fresh, still-unverified taskToken a caller could
      // replay without re-review (AdminChangesetApplyService.TaskTokenMismatchException scrubs it).
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminProviderController.class
         .getMethod("handleTaskTokenMismatch",
                    AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleTaskTokenMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}
