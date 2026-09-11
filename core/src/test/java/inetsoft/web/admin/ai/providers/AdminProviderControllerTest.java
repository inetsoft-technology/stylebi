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

import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.AuthorizationProviderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bug #76588 -- {@code AdminProviderController} has no {@code @ControllerAdvice} equivalent for
 * {@link AdminChangesetApplyService.TaskTokenMismatchException} (confirmed by
 * bug-provider/02-refute.md Claim 2: {@code AdminExceptionHandler}'s shared advice only maps it to
 * a generic 500), so the local {@code @ExceptionHandler} added alongside the existing
 * {@code handlePlanHashMismatch} is load-bearing, not optional -- this covers that new handler
 * directly, mirroring {@code AdminAiControllerTest}'s equivalent coverage for the Properties area.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminProviderControllerTest {
   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private AuthorizationProviderService authorizationProviderService;
   @Mock private ProviderChangePlanService planService;
   @Mock private ProviderChangesetApplyService applyService;
   private AdminProviderController controller;

   @BeforeEach void setup() {
      controller = new AdminProviderController(authenticationProviderService,
         authorizationProviderService, planService, applyService);
   }

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
