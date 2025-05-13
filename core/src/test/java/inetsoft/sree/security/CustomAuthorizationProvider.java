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
package inetsoft.sree.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CustomAuthorizationProvider extends AbstractAuthorizationProvider {
   @Override
   public Permission getPermission(ResourceType type, String resource, String orgID) {
      return permissions.get(new ResourceKey(type, resource, orgID));
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID identityID, String orgID) {
      return permissions.get(new ResourceKey(type, identityID.convertToKey(), orgID));
   }

   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      permissions.remove(new ResourceKey(type, resource, orgID));
   }

   @Override
   public void removePermission(ResourceType type, IdentityID resource, String orgID) {
      permissions.remove(new ResourceKey(type, resource.convertToKey(), orgID));
   }

   @Override
   public void setPermission(ResourceType type, String resource, Permission perm, String orgID) {
      if(perm == null) {
         removePermission(type, resource, orgID);
      }
      else {
         permissions.put(new ResourceKey(type, resource, orgID), perm);
      }
   }

   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm, String orgID) {
      if(perm == null) {
         removePermission(type, identityID, orgID);
      }
      else {
         permissions.put(new ResourceKey(type, identityID.convertToKey(), orgID), perm);
      }
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      try {
         Configuration config = new ObjectMapper().treeToValue(configuration, Configuration.class);
         Map<ResourceKey, Permission> map = config.permissions.stream()
            .collect(Collectors.toMap(ResourceConfig::toResourceKey, ResourceConfig::toPermission));
         permissions.clear();
         permissions.putAll(map);
      }
      catch(JsonProcessingException e) {
         throw new RuntimeException("Failed to parse configuration", e);
      }
   }

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      Configuration config = new Configuration();
      config.permissions = permissions.entrySet().stream()
         .map(e -> new ResourceConfig(e.getKey(), e.getValue()))
         .collect(Collectors.toList());
      return mapper.valueToTree(config);
   }

   @Override
   public void tearDown() {
   }

   @Override
   public void authenticationChanged(AuthenticationChangeEvent event) {
   }

   private final Map<ResourceKey, Permission> permissions = new ConcurrentHashMap<>();

   public static final class Configuration {
      public List<ResourceConfig> permissions = new ArrayList<>();
   }

   public static final class ResourceConfig {
      public String type;
      public String resource;
      public String orgID;
      public Map<String, GrantConfig> grants;

      public ResourceConfig() {
         this.grants = new HashMap<>();
      }

      public ResourceConfig(ResourceKey resource, Permission permission) {
         this.type = resource.getType().name();
         this.resource = resource.getPath();
         this.orgID = resource.getOrgID();
         this.grants = new HashMap<>();

         for(ResourceAction action : ResourceAction.values()) {
            GrantConfig grant = new GrantConfig(action , permission);

            if(!grant.isEmpty()) {
               grants.put(action.name(), grant);
            }
         }
      }

      public ResourceKey toResourceKey() {
         return new ResourceKey(ResourceType.valueOf(type), resource, orgID);
      }

      public Permission toPermission() {
         Permission permission = new Permission();

         for(Map.Entry<String, GrantConfig> e : grants.entrySet()) {
            ResourceAction action = ResourceAction.valueOf(e.getKey());
            permission.setUserGrants(action, e.getValue().users.stream()
               .map(id -> {
                  permission.updateGrantAllByOrg(id.getOrgID(), true);
                  return new Permission.PermissionIdentity(id.getName(), id.getOrgID());
               }).collect(Collectors.toSet()));
            permission.setGroupGrants(action, e.getValue().groups.stream()
               .map(id -> {
                  permission.updateGrantAllByOrg(id.getOrgID(), true);
                  return new Permission.PermissionIdentity(id.getName(), id.getOrgID());
               }).collect(Collectors.toSet()));
            permission.setRoleGrants(action, e.getValue().roles.stream()
               .map(id -> {
                  permission.updateGrantAllByOrg(id.getOrgID(), true);
                  return new Permission.PermissionIdentity(id.getName(), id.getOrgID());
               }).collect(Collectors.toSet()));
            permission.setOrganizationGrants(action, e.getValue().organizations.stream()
               .map(id -> {
                  permission.updateGrantAllByOrg(id.getOrgID(), true);
                  return new Permission.PermissionIdentity(id.getName(), id.getOrgID());
               }).collect(Collectors.toSet()));
         }

         return permission;
      }
   }

   public static final class GrantConfig {
      public Set<IdentityID> users;
      public Set<IdentityID> groups;
      public Set<IdentityID> roles;
      public Set<IdentityID> organizations;

      public GrantConfig() {
         this.users = new HashSet<>();
         this.groups = new HashSet<>();
         this.roles = new HashSet<>();
         this.organizations = new HashSet<>();
      }

      public GrantConfig(ResourceAction action, Permission permission) {
         users = permission.getUserGrants(action, null).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         groups = permission.getGroupGrants(action, null).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         roles = permission.getRoleGrants(action, null).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         organizations = permission.getOrganizationGrants(action, null).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
      }

      @JsonIgnore
      public boolean isEmpty() {
         return users.isEmpty() && groups.isEmpty() && roles.isEmpty() && organizations.isEmpty();
      }
   }
}