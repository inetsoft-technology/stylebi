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
package inetsoft.web.admin.ai;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;

/**
 * Asserts that the security provider this system resolves can actually persist a write, before any
 * admin-AI area attempts a permission or identity mutation.
 *
 * <p>{@link SecurityEngine#getSecurityProvider()} falls back to the virtual provider when
 * <em>either</em> {@code security.enabled} is not {@code "true"} <em>or</em> the real provider
 * chain was never initialized:
 *
 * <pre>
 *    if(isSecurityEnabled() &amp;&amp; provider != null) {
 *       return provider;
 *    }
 *
 *    return vprovider;
 * </pre>
 *
 * <p>That fallback is silent and, for a caller that writes and then verifies, actively misleading.
 * {@link VirtualAuthorizationProvider} inherits empty no-op {@code setPermission}/
 * {@code removePermission} bodies from {@link AbstractAuthorizationProvider}, so a write neither
 * persists nor throws; its {@code getPermission} then returns a fixed, never-null {@code Permission}
 * seeded only for {@code anonymous}/{@code admin}/{@code SYSTEM}. A write-then-verify apply service
 * therefore sees every create and update "succeed" and then fail verification, and reports a
 * spurious {@code rolled-back} with a message about mismatched actions -- pointing at the changeset
 * rather than at the uninitialized provider that is the real cause. Its authentication counterpart
 * is not an {@link EditableAuthenticationProvider} at all, so identity mutations fail the same way.
 *
 * <p>Checking the {@code security.enabled} property alone is not sufficient: the property can read
 * {@code "true"} while {@code provider} is still {@code null}, which takes the same fallback.
 * These helpers therefore inspect the <em>resolved</em> provider, not the property.
 */
public final class SecurityProviderGuard {
   private SecurityProviderGuard() {
   }

   /**
    * Returns the resolved security provider, having confirmed its authorization module can persist
    * a permission write.
    *
    * @param securityEngine the engine to resolve the provider from.
    *
    * @return the resolved provider, guaranteed not to be virtual-backed.
    *
    * @throws SecurityNotInitializedException if the resolved provider's authorization module
    *         silently discards writes.
    */
   public static SecurityProvider requireWritableAuthorization(SecurityEngine securityEngine) {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      AuthorizationProvider authorization =
         provider == null ? null : provider.getAuthorizationProvider();

      if(provider == null || authorization instanceof VirtualAuthorizationProvider) {
         throw new SecurityNotInitializedException("permission", describe(securityEngine, provider));
      }

      return provider;
   }

   /**
    * Returns the editable authentication provider identity mutations must write through.
    *
    * <p>Resolves it via {@link SUtil#getEditableAuthenticationProvider(SecurityProvider)}, the same
    * public utility {@code SecurityApiService} resolves its own writer with, so this check and the
    * subsequent write agree on the provider.
    *
    * @param securityEngine the engine to resolve the provider from.
    *
    * @return the editable authentication provider, never {@code null}.
    *
    * @throws SecurityNotInitializedException if security is not initialized at all.
    * @throws ReadOnlyAuthenticationException if it is initialized but the configured chain has no
    *         editable provider.
    */
   public static EditableAuthenticationProvider requireEditableAuthentication(
      SecurityEngine securityEngine)
   {
      SecurityProvider provider = securityEngine.getSecurityProvider();
      AuthenticationProvider authentication =
         provider == null ? null : provider.getAuthenticationProvider();

      // Checked BEFORE editability, and deliberately not via the editable lookup: unlike its
      // authorization counterpart, VirtualAuthenticationProvider extends
      // AbstractEditableAuthenticationProvider, so SUtil resolves it happily and an
      // editability-only check would pass -- while the inherited no-op mutators
      // (AbstractEditableAuthenticationProvider) drop every write.
      if(provider == null || authentication instanceof VirtualAuthenticationProvider) {
         throw new SecurityNotInitializedException("identity", describe(securityEngine, provider));
      }

      EditableAuthenticationProvider editable = SUtil.getEditableAuthenticationProvider(provider);

      if(editable == null) {
         throw new ReadOnlyAuthenticationException(describe(securityEngine, provider));
      }

      return editable;
   }

   /**
    * Renders the state that produced the fallback, so the failure names the actual condition to
    * fix rather than leaving the caller to guess which of the two branches was taken.
    */
   private static String describe(SecurityEngine securityEngine, SecurityProvider provider) {
      String authentication = provider == null || provider.getAuthenticationProvider() == null ?
         "none" : provider.getAuthenticationProvider().getClass().getSimpleName();
      String authorization = provider == null || provider.getAuthorizationProvider() == null ?
         "none" : provider.getAuthorizationProvider().getClass().getSimpleName();

      return "security.enabled=" + securityEngine.isSecurityEnabled() +
         ", authentication=" + authentication + ", authorization=" + authorization;
   }

   /**
    * Signals that security is not initialized well enough to persist an admin-AI change. Mapped to
    * HTTP 503 by the admin-AI controllers: the request itself is well-formed, so 400 would be
    * wrong, and the condition is expected to clear once security is initialized.
    */
   public static class SecurityNotInitializedException extends IllegalStateException {
      public SecurityNotInitializedException(String area, String state) {
         super("security is not initialized; " + area + " changes cannot be applied (" + state +
               "). The resolved provider discards writes silently, so applying a change here would " +
               "report a misleading verification failure. Enable security and confirm the provider " +
               "chain is initialized, then retry.");
      }
   }

   /**
    * Signals that security <em>is</em> initialized but the configured authentication chain holds
    * no editable provider -- a database- or LDAP-only chain, say, whose identities live in an
    * external directory. Distinct from {@link SecurityNotInitializedException} because nothing is
    * broken and retrying can never succeed: mapped to HTTP 409, not 503.
    */
   public static class ReadOnlyAuthenticationException extends IllegalStateException {
      public ReadOnlyAuthenticationException(String state) {
         super("the configured authentication provider is read-only; identity changes cannot be " +
               "applied (" + state + "). A chain with no editable provider -- database or LDAP, " +
               "typically -- cannot be modified through this API, and retrying will not help. " +
               "Manage these identities in the external directory, or configure an editable " +
               "provider.");
      }
   }
}
