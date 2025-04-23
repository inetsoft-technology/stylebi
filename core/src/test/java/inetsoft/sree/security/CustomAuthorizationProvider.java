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
   public Permission getPermission(ResourceType type, String resource) {
      return permissions.get(new Resource(type, resource));
   }

   @Override
   public Permission getPermission(ResourceType type, IdentityID identityID) {
      return permissions.get(new Resource(type, identityID.convertToKey()));
   }

   @Override
   public void removePermission(ResourceType type, String resource) {
      permissions.remove(new Resource(type, resource));
   }


   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      Permission perm = permissions.get(new Resource(type, resource));

      for(ResourceAction action: ResourceAction.values()) {
         perm.cleanOrganizationFromPermission(action, orgID);
      }

      setPermission(type, resource, perm);
   }

   @Override
   public void removePermission(ResourceType type, IdentityID resource) {
      permissions.remove(new Resource(type, resource.convertToKey()));
   }

   @Override
   public void setPermission(ResourceType type, String resource, Permission perm) {
      if(perm == null) {
         removePermission(type, resource);
      }
      else {
         permissions.put(new Resource(type, resource), perm);
      }
   }

   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm) {
      if(perm == null) {
         removePermission(type, identityID);
      }
      else {
         permissions.put(new Resource(type, identityID.convertToKey()), perm);
      }
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      try {
         Configuration config = new ObjectMapper().treeToValue(configuration, Configuration.class);
         Map<Resource, Permission> map = config.permissions.stream()
            .collect(Collectors.toMap(ResourceConfig::toResource, ResourceConfig::toPermission));
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

   private final Map<Resource, Permission> permissions = new ConcurrentHashMap<>();

   public static final class Configuration {
      public List<ResourceConfig> permissions = new ArrayList<>();
   }

   public static final class ResourceConfig {
      public String type;
      public String resource;
      public Map<String, GrantConfig> grants;

      public ResourceConfig() {
         this.grants = new HashMap<>();
      }

      public ResourceConfig(Resource resource, Permission permission) {
         this.type = resource.getType().name();
         this.resource = resource.getPath();
         this.grants = new HashMap<>();

         for(ResourceAction action : ResourceAction.values()) {
            GrantConfig grant = new GrantConfig(action , permission);

            if(!grant.isEmpty()) {
               grants.put(action.name(), grant);
            }
         }
      }

      public Resource toResource() {
         return new Resource(ResourceType.valueOf(type), resource);
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
         users = permission.getUserGrants(action).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         groups = permission.getGroupGrants(action).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         roles = permission.getRoleGrants(action).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
         organizations = permission.getOrganizationGrants(action).stream()
            .map(u -> new IdentityID(u.getName(), u.getOrganizationID()))
            .collect(Collectors.toSet());
      }

      @JsonIgnore
      public boolean isEmpty() {
         return users.isEmpty() && groups.isEmpty() && roles.isEmpty() && organizations.isEmpty();
      }
   }
}
