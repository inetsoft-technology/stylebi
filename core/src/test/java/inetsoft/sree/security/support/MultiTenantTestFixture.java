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
import inetsoft.uql.util.Identity;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-organization test fixture built on top of {@link SecurityTestDataBuilder}.
 *
 * <p>Org IDs follow the convention {@code <orgName>_id} (e.g. org name "alpha" → id "alpha_id").
 *
 * <p>Typical usage:
 * <pre>{@code
 * MultiTenantTestFixture fixture = MultiTenantTestFixture.twoOrgs()
 *     .orgA("alpha")
 *         .withAdmin("adminA")
 *         .withUser("aliceA")
 *     .and()
 *     .orgB("beta")
 *         .withAdmin("adminB")
 *         .withUser("aliceB")
 *     .and()
 *     .withSharedResource(ResourceType.VIEWSHEET, "reports/vs1")
 *     .setup();
 *
 * SRPrincipal aliceA = fixture.principalOf("aliceA", fixture.orgAId());
 * // ... assertions ...
 * fixture.teardown();
 * }</pre>
 *
 * <p>For each shared resource, the fixture automatically grants:
 * <ul>
 *   <li>Admin users of each org: READ + ADMIN on the resource (via an org-scoped admin role)</li>
 *   <li>Regular users of each org: READ on the resource (direct user grant)</li>
 * </ul>
 * Grants are scoped per org so cross-org access is denied by default.
 */
public class MultiTenantTestFixture {

   private final SecurityTestDataBuilder builder;
   private final String orgAId;
   private final String orgBId;

   private MultiTenantTestFixture(SecurityTestDataBuilder builder,
                                   String orgAId, String orgBId)
   {
      this.builder = builder;
      this.orgAId = orgAId;
      this.orgBId = orgBId;
   }

   /** Entry point — returns a {@link Builder} for a two-org scenario. */
   public static Builder twoOrgs() {
      return new Builder();
   }

   // ── fixture accessors ─────────────────────────────────────────────────────

   public String orgAId() { return orgAId; }

   public String orgBId() { return orgBId; }

   /**
    * Returns a logged-in {@link SRPrincipal} for {@code userName} in {@code orgId}.
    * Delegates to the underlying {@link SecurityTestDataBuilder#principalOf}.
    */
   public SRPrincipal principalOf(String userName, String orgId) {
      return builder.principalOf(userName, orgId);
   }

   /** Tears down the underlying {@link SecurityTestDataBuilder} and resets SecurityEngine. */
   public void teardown() {
      builder.teardown();
   }

   // ── Builder ───────────────────────────────────────────────────────────────

   public static class Builder {

      private OrgSpec orgASpec;
      private OrgSpec orgBSpec;
      private final List<ResourceSpec> sharedResources = new ArrayList<>();

      private Builder() {}

      /** Configures org A with the given display name. Returns an {@link OrgBuilder} for this org. */
      public OrgBuilder orgA(String name) {
         orgASpec = new OrgSpec(name);
         return new OrgBuilder(this, orgASpec);
      }

      /** Configures org B with the given display name. Returns an {@link OrgBuilder} for this org. */
      public OrgBuilder orgB(String name) {
         orgBSpec = new OrgSpec(name);
         return new OrgBuilder(this, orgBSpec);
      }

      /**
       * Declares a resource that both orgs' users will have access to (each in their own
       * org context). Admins receive READ + ADMIN; regular users receive READ.
       */
      public Builder withSharedResource(ResourceType type, String path) {
         sharedResources.add(new ResourceSpec(type, path));
         return this;
      }

      /** Builds and initialises the fixture. */
      public MultiTenantTestFixture setup() throws Exception {
         String orgAId = orgASpec.name + "_id";
         String orgBId = orgBSpec.name + "_id";

         SecurityTestDataBuilder db = SecurityTestDataBuilder.create()
            .addOrg(orgASpec.name, orgAId)
            .addOrg(orgBSpec.name, orgBId);

         configureOrg(db, orgASpec, orgAId);
         configureOrg(db, orgBSpec, orgBId);

         db.setup();
         return new MultiTenantTestFixture(db, orgAId, orgBId);
      }

      private void configureOrg(SecurityTestDataBuilder db, OrgSpec spec, String orgId) {
         // Create a dedicated admin role for this org
         String adminRole = orgId + "_admin";
         db.addRole(adminRole, orgId);

         for(String adminName : spec.admins) {
            db.addUser(adminName, orgId, "password")
              .addUserToRole(adminName, adminRole, orgId);
         }

         for(String userName : spec.users) {
            db.addUser(userName, orgId, "password");
         }

         // Grant permissions on each shared resource, scoped to this org
         for(ResourceSpec res : sharedResources) {
            // Admin role gets READ + ADMIN
            db.grantPermission(res.type, res.path, ResourceAction.READ,
                               adminRole, Identity.ROLE, orgId);
            db.grantPermission(res.type, res.path, ResourceAction.ADMIN,
                               adminRole, Identity.ROLE, orgId);

            // Regular users get READ
            for(String userName : spec.users) {
               db.grantPermission(res.type, res.path, ResourceAction.READ,
                                  userName, Identity.USER, orgId);
            }
         }
      }
   }

   // ── OrgBuilder ────────────────────────────────────────────────────────────

   public static class OrgBuilder {

      private final Builder parent;
      private final OrgSpec spec;

      private OrgBuilder(Builder parent, OrgSpec spec) {
         this.parent = parent;
         this.spec = spec;
      }

      /** Adds an admin user to this org. */
      public OrgBuilder withAdmin(String name) {
         spec.admins.add(name);
         return this;
      }

      /** Adds a regular user to this org. */
      public OrgBuilder withUser(String name) {
         spec.users.add(name);
         return this;
      }

      /** Returns to the parent {@link Builder} to configure the other org or add shared resources. */
      public Builder and() {
         return parent;
      }

      /** Convenience: calls {@link Builder#setup()} directly from the org-builder chain. */
      public MultiTenantTestFixture setup() throws Exception {
         return parent.setup();
      }
   }

   // ── internal specs ────────────────────────────────────────────────────────

   private static class OrgSpec {
      final String name;
      final List<String> admins = new ArrayList<>();
      final List<String> users = new ArrayList<>();

      OrgSpec(String name) {
         this.name = name;
      }
   }

   private record ResourceSpec(ResourceType type, String path) {}
}
