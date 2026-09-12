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
package inetsoft.web.security;

/*
 * Regression coverage for the community half of Bug #73926 (commit 04dfb3074): SecuredAspect used
 * to throw the CHECKED inetsoft.sree.security.SecurityException when a @Secured check failed.
 * Spring AOP proxies wrap a checked exception thrown from a method that does not declare it in
 * an UndeclaredThrowableException, which is not a SecurityException at all, so
 * ControllerErrorHandler's @ExceptionHandler(SecurityException.class) never matched and a 403
 * became a 500. The fix switched the thrown type to the unchecked java.lang.SecurityException and
 * added it to ControllerErrorHandler's handler alongside the original checked type (kept for any
 * other code path that still throws it directly). This class had zero test coverage of either half
 * of that fix before this file.
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.portal.controller.ControllerErrorHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class SecuredAspectTest {
   @Mock
   private DataSourceRegistry dataSourceRegistry;
   @Mock
   private ComponentAuthorizationService componentAuthorizationService;
   @Mock
   private AnalyticRepository repletRepository;
   @Mock
   private HttpServletRequest request;

   private SecuredAspect aspect;
   private MockedStatic<SUtil> sUtilMock;

   @BeforeEach
   void setUp() {
      aspect = new SecuredAspect(dataSourceRegistry, componentAuthorizationService);
      sUtilMock = mockStatic(SUtil.class);
      sUtilMock.when(SUtil::getRepletRepository).thenReturn(repletRepository);
      lenient().when(request.getRequestURI()).thenReturn("/api/portal/secured-resource");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      sUtilMock.close();
   }

   private ProceedingJoinPoint joinPointFor(String methodName, Principal user) throws Exception {
      Method method = SecuredFixture.class.getMethod(methodName, Principal.class);
      MethodSignature signature = mock(MethodSignature.class);
      when(signature.getMethod()).thenReturn(method);
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(joinPoint.getArgs()).thenReturn(new Object[] { user });
      return joinPoint;
   }

   // ── scenario 1: denied access throws java.lang.SecurityException, not the checked type ──

   @Test
   void deniedAccess_throwsUncheckedSecurityException_notCheckedType() throws Throwable {
      when(repletRepository.checkPermission(any(), eq(ResourceType.REPORT),
         eq("secured-resource"), eq(ResourceAction.READ))).thenReturn(false);
      Principal user = () -> "alice";
      ProceedingJoinPoint joinPoint = joinPointFor("securedMethod", user);

      java.lang.SecurityException thrown = assertThrows(java.lang.SecurityException.class,
         () -> aspect.authorize(joinPoint),
         "denied @Secured access must throw java.lang.SecurityException (a RuntimeException), " +
            "not the checked inetsoft.sree.security.SecurityException -- otherwise Spring AOP " +
            "wraps it in UndeclaredThrowableException and ControllerErrorHandler never matches it");

      assertEquals(java.lang.SecurityException.class, thrown.getClass());
      verify(joinPoint, never()).proceed();
   }

   // ── scenario: allowed access proceeds normally (regression guard for the main path) ──

   @Test
   void allowedAccess_proceeds() throws Throwable {
      when(repletRepository.checkPermission(any(), eq(ResourceType.REPORT),
         eq("secured-resource"), eq(ResourceAction.READ))).thenReturn(true);
      Principal user = () -> "alice";
      ProceedingJoinPoint joinPoint = joinPointFor("securedMethod", user);

      assertDoesNotThrow(() -> aspect.authorize(joinPoint));
      verify(joinPoint).proceed();
   }

   // ── scenario 2: ControllerErrorHandler maps BOTH exception types to 403, not 500 ──

   @Test
   void controllerErrorHandler_mapsUncheckedSecurityExceptionTo403() {
      ControllerErrorHandler handler = new ControllerErrorHandler(mock(LogManager.class));

      ResponseEntity<Map<String, String>> response =
         handler.handleSecurityException(new java.lang.SecurityException("denied"));

      assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
   }

   @Test
   void controllerErrorHandler_mapsCheckedSecurityExceptionTo403() {
      ControllerErrorHandler handler = new ControllerErrorHandler(mock(LogManager.class));

      ResponseEntity<Map<String, String>> response =
         handler.handleSecurityException(new inetsoft.sree.security.SecurityException("denied"));

      assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
         "the checked type stays handled too, in case any other code path still throws it directly");
   }

   // ── fixture ──────────────────────────────────────────────────────────────

   private static final class SecuredFixture {
      @Secured({
         @RequiredPermission(resourceType = ResourceType.REPORT, resource = "secured-resource",
            actions = ResourceAction.READ)
      })
      public void securedMethod(@PermissionUser Principal user) {
      }
   }
}
