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

import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The single parser for the identity references the admin-ai areas accept (permissions
 * {@code identityId}, identities {@code id}, viewsheet folder and dashboard {@code owner}).
 *
 * <p>Accepted forms, checked in this order:
 * <ol>
 *    <li>{@code name~;~org}: the storage key ({@code IdentityID.convertToKey()}), split at the first
 *        {@code ~;~} by {@link IdentityID#getIdentityIDFromKey}; {@code __GLOBAL__} is a null org.</li>
 *    <li>{@code name:org}: the documented wire form, split at the first {@code :}.</li>
 *    <li>{@code name^org}: the caret label ({@code getLabelWithCaretDelimiter()}, which
 *        {@code list_identity_users} hands out), split at the last {@code ^} only when the suffix is
 *        {@code __GLOBAL__}, the current org or an existing org. Names are not validated on the
 *        server (a DB/LDAP user can be {@code j^smith}), so a literal identity of that name wins
 *        when only the literal exists, and a reference that both readings resolve is refused as
 *        ambiguous.</li>
 *    <li>a bare name: an identity in the current org.</li>
 * </ol>
 *
 * <p>The {@link Directory} is consulted lazily. A bare name, or a {@code :}/{@code ~;~} form that
 * names the current org, makes no lookup, so those inputs resolve exactly as before.
 */
public final class AdminIdentityRefs {
   private AdminIdentityRefs() {
   }

   /** The identity kind a reference names. */
   public enum Kind { USER, GROUP, ROLE, ORGANIZATION }

   /** The provider lookups the caret rule and the org checks need. */
   public interface Directory {
      boolean orgExists(String orgId);

      /** Whether the identity exists. A ROLE also matches a global role of the same name. */
      boolean exists(Kind kind, IdentityID id);
   }

   /** A directory over a provider that is fetched only when a lookup is actually made. */
   public static Directory of(Supplier<? extends AuthenticationProvider> provider) {
      return new Directory() {
         @Override
         public boolean orgExists(String orgId) {
            return orgId != null && provider.get().getOrganization(orgId) != null;
         }

         @Override
         public boolean exists(Kind kind, IdentityID id) {
            AuthenticationProvider p = provider.get();

            switch(kind) {
            case USER:
               return p.getUser(id) != null;
            case GROUP:
               return p.getGroup(id) != null;
            case ROLE:
               return p.getRole(id) != null ||
                  id.getOrgID() != null && p.getRole(new IdentityID(id.getName(), null)) != null;
            default:
               return p.getOrganization(id.getName()) != null;
            }
         }
      };
   }

   /** A directory over the live security engine, for the static owner parsers and controllers. */
   public static Directory fromSecurityEngine() {
      return of(() -> SecurityEngine.getSecurity().getSecurityProvider());
   }

   /**
    * Parses an identity reference.
    *
    * @param field         the field name used in refusal messages, e.g. {@code changes[0].owner}.
    * @param raw           the caller's value.
    * @param currentOrgId  the caller's current org, used for a bare name.
    * @param kind          the kind of identity the reference names.
    * @param allowCrossOrg whether an org other than the current one may be named.
    * @param allowGlobal   whether a global ({@code __GLOBAL__}, null-org) identity may be named.
    * @param dir           the lookups, consulted only for forms that need them.
    *
    * @return the identity, or {@code null} when {@code raw} is blank. Callers that require a value
    *         check for blank first.
    *
    * @throws IllegalArgumentException when the reference is malformed, ambiguous, names an
    *                                  unknown org, or breaks the caller's org policy.
    */
   public static IdentityID parse(String field, String raw, String currentOrgId, Kind kind,
                                  boolean allowCrossOrg, boolean allowGlobal, Directory dir)
   {
      String trimmed = raw == null ? "" : raw.trim();

      if(trimmed.isEmpty()) {
         return null;
      }

      IdentityID id;

      if(trimmed.contains(IdentityID.KEY_DELIMITER)) {
         id = IdentityID.getIdentityIDFromKey(trimmed);
         requireExplicitParts(field, trimmed, id);
      }
      else if(trimmed.indexOf(':') >= 0) {
         int delim = trimmed.indexOf(':');
         id = new IdentityID(trimmed.substring(0, delim), orgOrNull(trimmed.substring(delim + 1)));
         requireExplicitParts(field, trimmed, id);
      }
      else if(trimmed.indexOf('^') >= 0) {
         id = parseCaret(field, trimmed, currentOrgId, kind, dir);
      }
      else {
         // a bare name is an identity in the current org for USER/GROUP/ROLE, but for
         // ORGANIZATION the name itself names the organization -- it is its own org, not an
         // identity nested under the caller's org (bug #79362)
         id = kind == Kind.ORGANIZATION ? new IdentityID(trimmed, trimmed) :
            new IdentityID(trimmed, currentOrgId);
      }

      return requireOrgPolicy(field, trimmed, id, currentOrgId, kind, allowCrossOrg, allowGlobal,
                              dir);
   }

   /**
    * Parses a viewsheet folder / dashboard {@code owner}: a USER, any existing org, never global.
    * A blank owner is {@code null} (the global scope). The owner is not required to exist in the
    * security provider: an SSO-only user has a real per-user registry without a provider record,
    * so plans warn instead ({@link #missingOwnerWarning}).
    */
   public static IdentityID parseOwner(String ownerRaw) {
      return parse("owner", ownerRaw, OrganizationManager.getInstance().getCurrentOrgID(),
                   Kind.USER, true, false, fromSecurityEngine());
   }

   /** The advisory for an owner the security provider does not know, or {@code null}. */
   public static String missingOwnerWarning(IdentityID owner) {
      if(owner == null || fromSecurityEngine().exists(Kind.USER, owner)) {
         return null;
      }

      return "owner \"" + owner.getLabel() + "\" not found in the security provider (an " +
         "SSO-only user can still own a registry, so this is not refused)";
   }

   /** Returns {@code change} with a {@code WARNING:} prefixed to its description. The description
    * is not part of any plan hash, so an advisory adds no hash churn. */
   public static PlanChange withWarning(PlanChange change, String warning) {
      return new PlanChange(change.property(), change.orgId(), change.currentValue(),
                            change.proposedValue(), change.risk(), change.snapshotScope(),
                            change.recognized(),
                            "WARNING: " + warning + ". " + change.description());
   }

   private static IdentityID parseCaret(String field, String trimmed, String currentOrgId,
                                        Kind kind, Directory dir)
   {
      int caret = trimmed.lastIndexOf('^');
      String name = trimmed.substring(0, caret);
      String suffix = trimmed.substring(caret + 1);
      IdentityID literal = new IdentityID(trimmed, currentOrgId);
      boolean knownOrg = GLOBAL_ORG_KEY.equals(suffix) || Objects.equals(suffix, currentOrgId) ||
         !suffix.isEmpty() && dir.orgExists(suffix);

      if(knownOrg) {
         IdentityID split = new IdentityID(name, orgOrNull(suffix));

         if(dir.exists(kind, literal)) {
            if(!name.trim().isEmpty() && dir.exists(kind, split)) {
               throw new IllegalArgumentException(
                  field + ": \"" + trimmed + "\" is ambiguous -- both the identity named \"" +
                  trimmed + "\" in org \"" + currentOrgId + "\" and the identity \"" + name +
                  "\" in org \"" + suffix + "\" exist; use \"" + name + ":" + suffix +
                  "\" to name the second, or \"" + trimmed + ":" + currentOrgId +
                  "\" to name the first");
            }

            // only the literal reading exists, e.g. an external user really named "j^host-org"
            return literal;
         }

         requireExplicitParts(field, trimmed, split);
         return split;
      }

      if(suffix.isEmpty() && !dir.exists(kind, literal)) {
         throw new IllegalArgumentException(
            field + ": name and org must both be non-blank in \"" + trimmed + "\"");
      }

      if(ORG_ID_SHAPE.matcher(suffix).matches() && !dir.exists(kind, literal)) {
         throw new IllegalArgumentException(
            field + ": no identity named \"" + trimmed + "\" in org \"" + currentOrgId +
            "\" and no organization \"" + suffix + "\"; use \"name:orgId\" to name an identity " +
            "in another organization");
      }

      // a suffix that cannot be an org id, or a real identity with this literal name
      return literal;
   }

   private static void requireExplicitParts(String field, String trimmed, IdentityID id) {
      if(id.getName() == null || id.getName().trim().isEmpty() ||
         id.getOrgID() != null && id.getOrgID().trim().isEmpty())
      {
         throw new IllegalArgumentException(
            field + ": name and org must both be non-blank in \"" + trimmed + "\"");
      }
   }

   private static IdentityID requireOrgPolicy(String field, String trimmed, IdentityID id,
                                              String currentOrgId, Kind kind,
                                              boolean allowCrossOrg, boolean allowGlobal,
                                              Directory dir)
   {
      String orgId = id.getOrgID();

      if(kind == Kind.ORGANIZATION) {
         // an ORGANIZATION identity names itself: its "org" has no independent meaning -- the
         // only legitimate value is its own name, never a different real org (bug #79362) and
         // never global (an organization is never a sub-entity of another scope)
         if(orgId == null) {
            throw new IllegalArgumentException(
               field + ": \"" + trimmed + "\" names a global organization reference, which " +
               "does not exist -- an organization identity's org must be its own name");
         }

         if(!orgId.equals(id.getName())) {
            throw new IllegalArgumentException(
               field + ": an ORGANIZATION reference's org and name must agree; got name \"" +
               id.getName() + "\", org \"" + orgId + "\"");
         }

         orgId = id.getName();
      }

      if(orgId == null) {
         if(!allowGlobal) {
            throw new IllegalArgumentException(
               field + ": \"" + trimmed + "\" names a global (" + GLOBAL_ORG_KEY +
               ") identity, which this area does not support");
         }

         return id;
      }

      if(Objects.equals(orgId, currentOrgId)) {
         return id;
      }

      if(!allowCrossOrg) {
         throw new IllegalArgumentException(
            field + ": targets org \"" + orgId + "\", but this area only supports " +
            "the caller's current org (\"" + currentOrgId + "\") in this cut -- no org-targeting " +
            "parameter exists yet");
      }

      if(!dir.orgExists(orgId)) {
         throw new IllegalArgumentException(
            field + ": organization \"" + orgId + "\" does not exist");
      }

      return id;
   }

   private static String orgOrNull(String orgId) {
      return GLOBAL_ORG_KEY.equals(orgId) ? null : orgId;
   }

   /** Mirrors {@code IdentityID}'s private {@code GLOBAL_ORG_KEY}, the org text of a global role. */
   static final String GLOBAL_ORG_KEY = "__GLOBAL__";
   private static final Pattern ORG_ID_SHAPE = Pattern.compile("[A-Za-z0-9-]+");
}
