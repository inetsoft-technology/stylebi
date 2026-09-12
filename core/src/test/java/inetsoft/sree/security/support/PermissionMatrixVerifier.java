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
package inetsoft.sree.security.support;

import inetsoft.sree.security.*;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent assertion helper that verifies a permission matrix against a live {@link SecurityEngine}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * PermissionMatrixVerifier.of(SecurityEngine.getSecurity())
 *     .resource(ResourceType.VIEWSHEET, "reports/vs1")
 *         .expectAllow(aliceA, ResourceAction.READ)
 *         .expectDeny(aliceB, ResourceAction.READ)
 *     .verify();
 * }</pre>
 *
 * <p>Failure messages include the principal name, action, and resource path so test output
 * pinpoints which matrix entry failed without requiring a debugger.
 *
 * <p>Principals passed to {@code expectAllow}/{@code expectDeny} must already be registered
 * in the SecurityEngine session cache (via
 * {@link SecurityTestDataBuilder#principalOf(String, String)}), otherwise
 * {@link SecurityEngine#checkPermission} will throw {@link SecurityException} and the
 * verifier will fail with a descriptive message.
 */
public class PermissionMatrixVerifier {

   private final SecurityEngine engine;
   private ResourceType currentType;
   private String currentResource;
   private final List<Expectation> expectations = new ArrayList<>();

   private PermissionMatrixVerifier(SecurityEngine engine) {
      this.engine = engine;
   }

   public static PermissionMatrixVerifier of(SecurityEngine engine) {
      return new PermissionMatrixVerifier(engine);
   }

   /**
    * Sets the current resource context for subsequent {@code expectAllow}/{@code expectDeny} calls.
    * Can be called multiple times to switch resources within a single verifier chain.
    */
   public PermissionMatrixVerifier resource(ResourceType type, String resource) {
      this.currentType = type;
      this.currentResource = resource;
      return this;
   }

   /**
    * Declares that {@code principal} must be allowed to perform each of the given {@code actions}
    * on the current resource.
    */
   public PermissionMatrixVerifier expectAllow(SRPrincipal principal, ResourceAction... actions) {
      for(ResourceAction action : actions) {
         expectations.add(new Expectation(principal, currentType, currentResource, action, true));
      }

      return this;
   }

   /**
    * Declares that {@code principal} must be denied each of the given {@code actions}
    * on the current resource.
    */
   public PermissionMatrixVerifier expectDeny(SRPrincipal principal, ResourceAction... actions) {
      for(ResourceAction action : actions) {
         expectations.add(new Expectation(principal, currentType, currentResource, action, false));
      }

      return this;
   }

   /**
    * Runs all accumulated assertions.
    * Fails with a descriptive message (principal key + action + resource) for any mismatch.
    */
   public void verify() {
      for(Expectation e : expectations) {
         String label = String.format("[%s] %s on %s/%s",
                                     e.principal.getName(), e.action, e.type, e.resource);
         boolean actual;

         try {
            actual = engine.checkPermission(e.principal, e.type, e.resource, e.action);
         }
         catch(inetsoft.sree.security.SecurityException | java.lang.SecurityException ex) {
            Assertions.fail("checkPermission threw SecurityException for " + label
                            + ": " + ex.getMessage());
            return;
         }

         String message = (e.expectedAllow
            ? "Expected ALLOW but got DENY: "
            : "Expected DENY but got ALLOW: ") + label;
         Assertions.assertEquals(e.expectedAllow, actual, message);
      }
   }

   private record Expectation(SRPrincipal principal, ResourceType type, String resource,
                               ResourceAction action, boolean expectedAllow) {}
}
