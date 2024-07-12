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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.composer.model.TreeNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IdentityTreeService {
   @Autowired
   public IdentityTreeService() {
   }

   public List<TreeNodeModel> getSearchTree(SecurityEngine engine, SecurityProvider provider, IdentityID identityID,
                                            boolean searchMode, String searchString,
                                            Principal principal, boolean hideOrgName)
   {
      Catalog catalog = Catalog.getCatalog();
      List<TreeNodeModel> treeNodeModels = new ArrayList<>();
      List<TreeNodeModel> userTeeNode = getUserTeeNode(engine, provider, searchMode, identityID,
         searchString, principal);

      if(userTeeNode != null && userTeeNode.size() > 0) {
         TreeNodeModel users = TreeNodeModel.builder()
            .label(catalog.getString("Users"))
             .organization(OrganizationManager.getCurrentOrgName())
             .children(userTeeNode)
            .expanded(true)
            .type(IdentityNode.USERS + "")
            .build();
         treeNodeModels.add(users);
      }

      List<TreeNodeModel> groupTreeNode =
         getGroupTreeNode(provider, searchMode, searchString, principal, hideOrgName);

      if(groupTreeNode != null && groupTreeNode.size() > 0) {
         TreeNodeModel groups = TreeNodeModel.builder()
            .label(catalog.getString("Groups"))
            .organization(OrganizationManager.getCurrentOrgName())
            .children(groupTreeNode)
            .expanded(true)
            .type(IdentityNode.GROUPS + "")
            .build();
         treeNodeModels.add(groups);
      }

      return treeNodeModels;
   }

   public List<TreeNodeModel> getGroupTreeNode(SecurityProvider provider, boolean searchMode,
                                               String searchStr, Principal principal,
                                               boolean hideOrgName)
   {
      Set<String> childGroups = Arrays.stream(provider.getGroups())
         .map(name -> provider.getGroup(name))
         .filter(Objects::nonNull)
         .filter(g -> g.getGroups().length > 0)
         .map(Group::getName)
         .collect(Collectors.toSet());

      return Arrays.stream(provider.getGroups())
         .filter(group -> !childGroups.contains(group))
         .filter(group -> !StringUtils.isEmpty(group))
         .filter(group -> provider.getGroup(group) != null &&
                 provider.getGroup(group).getOrganization().equals(OrganizationManager.getCurrentOrgName()))
         .sorted()
         .map(group -> getGroupNode(provider, new IdentityID("/", group.organization), group, searchMode,
            Tool.equals(group, searchStr.toLowerCase()) ? "" : searchStr, principal, hideOrgName))
         .filter(Objects::nonNull)
         .filter(node -> node.children().size() != 0 || Tool.isEmptyString(searchStr) ||
            node.children().size() == 0 && node.label().toLowerCase().contains(searchStr.toLowerCase()))
         .collect(Collectors.toList());
   }

   private TreeNodeModel getGroupNode(SecurityProvider provider, IdentityID pGroup, IdentityID groupName,
                                      boolean searchMode, String searchStr, Principal principal,
                                      boolean hideOrgName)
   {
      List<TreeNodeModel> groupChildren = Arrays.stream(provider.getGroupMembers(groupName))
         .filter(member -> member.getType() == Identity.GROUP)
         .map(group -> group.getIdentityID())
         .sorted()
         .map(g -> getGroupNode(provider, pGroup.name == "/" ? groupName :
                                   new IdentityID(pGroup.name + "/" + groupName.name, pGroup.organization),
            g, searchMode, Tool.equals(g.name.toLowerCase(), searchStr.toLowerCase()) ?
                                   "" : searchStr, principal, hideOrgName))
         .filter(Objects::nonNull)
         .filter(node -> Tool.isEmptyString(searchStr) || node.children() != null && node.children().size() > 0 ||
            node.label().toLowerCase().contains(searchStr.toLowerCase()))
         .collect(Collectors.toList());

      String[] userChildren = Arrays.stream(provider.getGroupMembers(groupName))
         .filter(identity -> identity.getType() == Identity.USER)
         .filter(identity -> ((User) identity).getEmails() != null && ((User) identity).getEmails().length > 0)
         .map(Identity::getName)
         .toArray(String[]::new);

      if(groupChildren.isEmpty() && userChildren.length == 0) {
         return null;
      }

      IdentityModel identityModel = IdentityModel.builder()
         .identityID(groupName)
         .type(Identity.GROUP)
         .parentNode(pGroup.name)
         .build();

      boolean leaf = groupChildren == null || groupChildren.isEmpty();
      Group group = provider.getGroup(groupName);

      if(group == null) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("Failed to find group {0}.", groupName));
      }

      return TreeNodeModel.builder()
         .label(groupName.name)
         .data(identityModel)
         .organization(groupName.organization)
         .children(groupChildren)
         .type(Identity.GROUP + "")
         .expanded(!leaf && searchMode)
         .leaf(leaf)
         .build();
   }

   private boolean filteredUsers(SecurityProvider provider, String user, Principal principal) {
      return provider.checkPermission(
         principal, ResourceType.SECURITY_USER, user, ResourceAction.ADMIN);
   }

   public List<TreeNodeModel> getUserTeeNode(SecurityEngine engine, SecurityProvider provider, boolean searchMode,
                                             IdentityID identityID, String searchString, Principal principal)
   {
      List<IdentityNode> identityNodes;
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());

      if(searchMode) {
         identityNodes = Arrays.stream(engine.getUsers())
            .map(u -> new User(u))
            .map(IdentityNode::new)
            .collect(Collectors.toList());
      }
      else {
         IdentityNode node = new IdentityNode(identityID, IdentityNode.USERS, false);
         identityNodes = Arrays.stream(engine.getSubIdentities(node))
            .collect(Collectors.toList());
      }

      Map<String, Boolean> groups = new HashMap<>();
      Map<String, Boolean> users = new HashMap<>();
      boolean individual = true;

      for(String group : provider.getUserGroups(IdentityID.getIdentityIDFromKey(principal.getName()))) {
         groups.put(group, true);
         individual = false;
      }

      List<IdentityID> usersWithEmail = EmailDialogController.getUsers(principal);

      identityNodes = identityNodes.stream()
         .filter(n -> n.getType() == Identity.USER)
         .filter(n -> StringUtils.isEmpty(searchString) ||
            (n.getIdentityID() != null && n.getIdentityID().name.toLowerCase().contains(searchString.toLowerCase())))
         .filter(n -> usersWithEmail.contains(n.getIdentityID()))
         .collect(Collectors.toList());

      if(individual && identityNodes.isEmpty()) {
         Arrays.stream(provider.getIndividualUsers())
            .filter(usersWithEmail::contains)
            .filter(u -> !u.equals(pId))
            .filter(n -> StringUtils.isEmpty(searchString) ||
               (n != null && n.name.toLowerCase().contains(searchString.toLowerCase())))
            .map(name1 -> provider.getUser(name1))
            .map(IdentityNode::new)
            .forEach(identityNodes::add);
      }

      boolean limitApplied = false;

      if(identityNodes.size() > MAX_NODES) {
         limitApplied = true;
      }

      List<TreeNodeModel> treeNodeModels = identityNodes.stream()
         .limit(MAX_NODES)
         .map(n -> TreeNodeModel.builder()
            .label(n.getIdentityID().name)
            .alias(getUserAlias(provider, n.getIdentityID()))
            .organization(n.getIdentityID().organization)
            .data(getUserEmails(n.getIdentityID()))
            .type(n.getType() + "")
            .leaf(n.getType() == Identity.USER)
            .build())
         .collect(Collectors.toList());

      if(limitApplied) {
         treeNodeModels = new ArrayList<>(treeNodeModels);
         treeNodeModels.add(TreeNodeModel.builder()
            .label(Catalog.getCatalog()
               .getString("schedule.task.options.userTreeLimited",
                  MAX_NODES))
            .leaf(true)
            .icon("alert-circle-icon")
            .cssClass("alert alert-danger disable-actions")
            .build());
      }

      return treeNodeModels;
   }

   private String[] getUserEmails(IdentityID user) {
      try {
         return SUtil.getEmails(user);
      }
      catch(Exception e) {
         LOG.warn("Failed to get emails for user: {}", user, e);
         return new String[0];
      }
   }

   private String getUserAlias(SecurityProvider provider, IdentityID name) {
      if(provider == null || name == null) {
         return null;
      }

      User user = provider.getUser(name);
      return user != null ? user.getAlias() : null;
   }

   private static final int MAX_NODES = 1000;
   private static final Logger LOG =
      LoggerFactory.getLogger(IdentityTreeService.class);
}
