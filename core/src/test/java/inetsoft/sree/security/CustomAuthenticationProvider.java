/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CustomAuthenticationProvider extends AbstractAuthenticationProvider {
   @Override
   public IdentityID[] getUsers() {
      return users.keySet().toArray(new IdentityID[0]);
   }

   @Override
   public User getUser(IdentityID userIdentity) {
      return users.get(userIdentity);
   }

   @Override
   public String[] getOrganizations() {
      return organizations.keySet().toArray(new String[0]);
   }

   @Override
   public Organization getOrganization(String name) {
      return organizations.get(name);
   }

   @Override
   public String getOrgId(String name) {
      return organizations.get(name).getId();
   }

   @Override
   public String getOrgNameFromID(String id) {
      for(String org : getOrganizations()) {
         if(getOrganization(org).getId().equalsIgnoreCase(id)) {
            return org;
         }
      }
      return null;
   }

   @Override
   public Group getGroup(IdentityID groupIdentity) {
      return groups.get(groupIdentity);
   }

   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return users.values().stream()
         .filter(u -> new HashSet<>(Arrays.asList(u.getGroups())).contains(groupIdentity.name))
         .map(FSUser::getIdentityID)
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getIndividualUsers() {
      return users.values().stream()
         .filter(u -> u.getGroups().length == 0)
         .map(FSUser::getIdentityID)
         .toArray(IdentityID[]::new);
   }

   @Override
   public IdentityID[] getRoles() {
      return roles.keySet().toArray(new IdentityID[0]);
   }

   @Override
   public IdentityID[] getRoles(IdentityID roleIdentity) {
      User userObject = users.get(roleIdentity);
      return userObject == null ? new IdentityID[0] : userObject.getRoles();
   }

   @Override
   public Role getRole(IdentityID roleIdentity) {
      return roles.get(roleIdentity);
   }

   @Override
   public IdentityID[] getGroups() {
      return groups.keySet().toArray(new IdentityID[0]);
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleIdentity) {
      return "Organization Administrator".equals(roleIdentity.name);
   }

   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
      if(credential == null) {
         return false;
      }

      if(!(credential instanceof DefaultTicket)) {
         credential = DefaultTicket.parse(credential.toString());
      }

      IdentityID userID = ((DefaultTicket) credential).getName();
      String password = ((DefaultTicket) credential).getPassword();

      if(userID == null || userID.name.length() == 0 || password == null ||
         password.length() == 0) {
         return false;
      }

      User userObject = getUser(userIdentity);

      if(null == userObject || !userObject.isActive()) {
         return false;
      }

      String savedPassword = userObject.getPassword();

      if(savedPassword == null) {
         return false;
      }
      else {
         return password.equals(savedPassword) && userIdentity.equals(userID);
      }
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      List<String> globalRoles = Optional.ofNullable(configuration.get("roles"))
         .map(roles -> StreamSupport.stream(roles.spliterator(), false)
            .map(ObjectNode.class::cast)
            .filter(node -> node.get("organization") == null)
            .map(node -> node.get("name").asText())
            .collect(Collectors.toList()))
         .orElse(Collections.emptyList());
      Map<IdentityID, FSUser> userMap =
         StreamSupport.stream(configuration.get("users").spliterator(), false)
            .map(ObjectNode.class::cast)
            .map(node -> readUser(node, globalRoles))
            .collect(Collectors.toMap(FSUser::getIdentityID, Function.identity()));
      Map<IdentityID, FSGroup> groupMap =
         StreamSupport.stream(configuration.get("groups").spliterator(), false)
            .map(ObjectNode.class::cast)
            .map(node -> readGroup(node, globalRoles))
            .collect(Collectors.toMap(FSGroup::getIdentityID, Function.identity()));
      Map<IdentityID, FSRole> roleMap =
         StreamSupport.stream(configuration.get("roles").spliterator(), false)
            .map(ObjectNode.class::cast)
            .map(node -> readRole(node, globalRoles))
            .collect(Collectors.toMap(FSRole::getIdentityID, Function.identity()));
      Map<String, FSOrganization> organizationMap =
         StreamSupport.stream(configuration.get("organizations").spliterator(), false)
            .map(ObjectNode.class::cast)
            .map(this::readOrganization)
            .collect(Collectors.toMap(FSOrganization::getName, Function.identity()));
      users.clear();
      users.putAll(userMap);

      roles.clear();
      roles.putAll(roleMap);

      groups.clear();
      groups.putAll(groupMap);

      organizations.clear();
      organizations.putAll(organizationMap);
   }

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = mapper.createObjectNode();

      ArrayNode array = mapper.createArrayNode();
      config.set("users", array);
      users.values().stream()
         .sorted(Comparator.comparing(FSUser::getName))
         .map(u -> writeUser(u, mapper))
         .forEach(array::add);

      array = mapper.createArrayNode();
      config.set("groups", array);
      groups.values().stream()
         .sorted(Comparator.comparing(FSGroup::getName))
         .map(g -> writeGroup(g, mapper))
         .forEach(array::add);

      array = mapper.createArrayNode();
      config.set("roles", array);
      roles.values().stream()
         .sorted(Comparator.comparing(FSRole::getName))
         .map(r -> writeRole(r, mapper))
         .forEach(array::add);

      array = mapper.createArrayNode();
      config.set("organizations", array);
      organizations.values().stream()
         .sorted(Comparator.comparing(FSOrganization::getName))
         .map(g -> writeOrganization(g, mapper))
         .forEach(array::add);

      return config;
   }

   @Override
   public void tearDown() {
   }

   private FSUser readUser(ObjectNode node, List<String> globalRoles) {
      String organization = node.get("organization").asText();
      return new FSUser(
         createIdentityID(node.get("name").asText(), organization),
         readStringArray(node, "emails"),
         readStringArray(node, "groups"),
         Arrays.stream(readStringArray(node, "roles"))
            .map(role -> createIdentityID(role, globalRoles.contains(role) ? null : organization))
            .toArray(IdentityID[]::new),
         null,
         node.get("password").asText());
   }

   private ObjectNode writeUser(FSUser user, ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", user.getName());

      if(user.getOrganization() != null) {
         node.put("organization", user.getOrganization());
      }

      node.set("emails", writeStringArray(user.getEmails(), mapper));
      node.set("groups", writeStringArray(user.getGroups(), mapper));
      node.set("roles", writeStringArray(Arrays.stream(user.getRoles()).map(IdentityID::getName).toArray(String[]::new), mapper));
      node.put("password", user.getPassword());
      return node;
   }

   private FSGroup readGroup(ObjectNode node, List<String> globalRoles) {
      String organization = node.get("organization").asText();
      return new FSGroup(
         createIdentityID(node.get("name").asText(), organization),
         null,
         readStringArray(node, "groups"),
         Arrays.stream(readStringArray(node, "roles"))
            .map(role -> createIdentityID(role, globalRoles.contains(role) ? null : organization))
            .toArray(IdentityID[]::new)
      );
   }

   private ObjectNode writeGroup(FSGroup group, ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", group.getName());

      if(group.getOrganization() != null) {
         node.put("organization", group.getOrganization());
      }

      node.set("groups", writeStringArray(group.getGroups(), mapper));
      node.set("roles", writeStringArray(Arrays.stream(group.getRoles()).map(IdentityID::getName).toArray(String[]::new), mapper));
      return node;
   }

   private FSRole readRole(ObjectNode node, List<String> globalRoles) {
      String organization =
         node.get("organization") != null ? node.get("organization").asText() : null;
      FSRole role = new FSRole(
         createIdentityID(node.get("name").asText(), organization),
         Arrays.stream(readStringArray(node, "roles"))
            .map(r -> createIdentityID(r, globalRoles.contains(r) ? null : organization))
            .toArray(IdentityID[]::new));
      role.setDefaultRole(node.get("defaultRole").asBoolean());
      return role;
   }

   private ObjectNode writeRole(FSRole role, ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", role.getName());

      if(role.getOrganization() != null) {
         node.put("organization", role.getOrganization());
      }

      node.set("roles", writeStringArray(Arrays.stream(role.getRoles()).map(IdentityID::getName).toArray(String[]::new), mapper));
      node.put("defaultRole", role.isDefaultRole());
      return node;
   }

   private FSOrganization readOrganization(ObjectNode node) {
      return new FSOrganization(
         node.get("name").asText(),
         node.get("organizationID").asText(),
         readStringArray(node, "members"),
         null);
   }

   private ObjectNode writeOrganization(FSOrganization organization, ObjectMapper mapper) {
      ObjectNode node = mapper.createObjectNode();
      node.put("name", organization.getName());
      node.put("organizationID", organization.getId());
      node.set("members", writeStringArray(organization.getMembers(), mapper));
      node.set("roles", writeStringArray(Arrays.stream(organization.getRoles()).map(IdentityID::convertToKey).toArray(String[]::new), mapper));
      return node;
   }

   private String[] readStringArray(ObjectNode node, String property) {
      return StreamSupport.stream(node.get(property).spliterator(), false)
         .map(JsonNode::asText)
         .toArray(String[]::new);
   }

   private ArrayNode writeStringArray(String[] array, ObjectMapper mapper) {
      ArrayNode node = mapper.createArrayNode();

      for(String element : array) {
         node.add(element);
      }

      return node;
   }

   private IdentityID createIdentityID(String name, String organization) {
      return new IdentityID(name, organization);
   }

   private final Map<IdentityID, FSUser> users = new ConcurrentHashMap<>();
   private final Map<IdentityID, FSGroup> groups = new ConcurrentHashMap<>();
   private final Map<IdentityID, FSRole> roles = new ConcurrentHashMap<>();
   private final Map<String, FSOrganization> organizations = new ConcurrentHashMap<>();
}
