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
package inetsoft.sree.security;

import java.security.Principal;

/**
 * Wraps a {@link CheckPermissionStrategy} so that any wiz-authenticated principal (see
 * {@link SRPrincipal#isWizPrincipal(Principal)}) is granted every permission unconditionally,
 * regardless of {@link ResourceType}, without consulting the wrapped strategy.
 * <p>
 * This is wired into {@link CompositeSecurityProvider#create} so it is the single choke point for
 * wiz delegation: {@code SecurityEngine.checkPermission(...)} and every direct
 * {@code SecurityProvider.checkPermission(...)} caller elsewhere in the codebase (the EM access
 * gate in {@code DefaultAuthorizationFilter}, {@code ClusterController}, and roughly two dozen more
 * {@code web/admin/**} call sites) all end up going through a {@code CompositeSecurityProvider}
 * instance, so they all see this behavior consistently.
 * <p>
 * <b>This is a deliberate, accepted trust boundary, not a defect (Redmine bug #76630, decided
 * "intended architecture, close as intended").</b> It rests on the assumption that any StyleBI
 * instance reachable via wiz-services is a backend-only deployment for that product: no direct,
 * non-wiz end-user session is ever served by the same instance concurrently. Under that
 * assumption, a wiz-tagged request bypassing per-user/per-org permission checks is harmless,
 * because there is no other tenant's data or session on the instance to protect the request
 * from. If that deployment assumption is ever violated -- i.e. a single StyleBI instance serves
 * both wiz-service traffic and direct end-user sessions -- then any wiz-authenticated request
 * (any subject, any role, any org, so long as the JWT is validly signed and correctly audienced;
 * see {@code WizServiceAuthenticationFilter}) would have effective EM-admin-level access
 * regardless of its JWT's actual role/org claims. As of bug #76630 this mechanism had been
 * independently rediscovered as an apparent bug at least 8 separate times since 2026-08-26 (most
 * recently via bug #76545) before being formally decided and closed as intended; this javadoc
 * exists so it is not rediscovered a 9th time. Do not narrow, gate, or "fix" this bypass without
 * a new, explicit product/security decision superseding #76630.
 */
public class WizDelegatingCheckPermissionStrategy implements CheckPermissionStrategy {
   public WizDelegatingCheckPermissionStrategy(CheckPermissionStrategy delegate) {
      this.delegate = delegate;
   }

   @Override
   public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                  ResourceAction action)
   {
      if(SRPrincipal.isWizPrincipal(principal)) {
         return true;
      }

      return delegate.checkPermission(principal, type, resource, action);
   }

   private final CheckPermissionStrategy delegate;
}
