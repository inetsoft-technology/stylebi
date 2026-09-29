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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.security.Principal;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Decides which owner and execute-as identity a caller may store in a schedule task. A task
 * whose owner doesn't exist runs with the roles of a site admin of the same name in another
 * organization (SUtil.getScheduleTaskOwnerPrincipal, Bug #73978), so every path that stores
 * an owner or execute-as identity sent by the client (the task editor, the schedule task
 * import, the deploy import and the public schedule API) uses this check. A site admin, or any
 * caller when security is disabled, may store any identity. Any other caller may only store:
 * <ul>
 *    <li>an owner that is the caller, or an existing user of the caller's current
 *    organization the caller has SECURITY_USER ADMIN on. The system and anonymous users are
 *    refused. A user that doesn't exist is refused even though the ADMIN check allows it
 *    (Bug #66393, an SSO user that isn't in the provider);</li>
 *    <li>an execute-as user or group the task editor offers the caller
 *    (ScheduleTaskService.getExecuteAsUsers/getExecuteAsGroups): the caller, the owner, or an
 *    existing user or group of the current organization the caller administers. A role is
 *    never allowed.</li>
 * </ul>
 * A path that updates a stored task keeps allowing an identity that is unchanged, that is done
 * by the caller of this class.
 */
public class ScheduleTaskIdentityChecker {
   public ScheduleTaskIdentityChecker(SecurityEngine securityEngine) {
      this(securityEngine::isSecurityEnabled, securityEngine::getSecurityProvider);
   }

   public ScheduleTaskIdentityChecker(SecurityProvider securityProvider) {
      this(() -> !securityProvider.isVirtual(), () -> securityProvider);
   }

   private ScheduleTaskIdentityChecker(BooleanSupplier securityEnabled,
                                       Supplier<SecurityProvider> securityProvider)
   {
      this.securityEnabled = securityEnabled;
      this.securityProvider = securityProvider;
   }

   /**
    * Determines if the caller may store any owner and execute-as identity, that is when the
    * caller is a site admin or security is disabled.
    */
   public boolean isUnrestricted(Principal principal) {
      return !securityEnabled.getAsBoolean() ||
         OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   /**
    * Determines if the caller may make a user the owner of a task.
    *
    * @param owner     the new owner.
    * @param principal the caller.
    */
   public boolean isOwnerAllowed(IdentityID owner, Principal principal) {
      if(isUnrestricted(principal)) {
         return true;
      }

      IdentityID caller = IdentityID.getIdentityIDFromKey(principal.getName());
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      return owner != null && owner.getOrgID() != null && owner.getOrgID().equals(orgID) &&
         !XPrincipal.SYSTEM.equals(owner.getName()) &&
         !XPrincipal.ANONYMOUS.equals(owner.getName()) &&
         (owner.equals(caller) ||
            getProvider().getUser(owner) != null && isAdmin(principal, owner));
   }

   /**
    * Determines if the caller may make a task owned by a user run as an identity.
    *
    * @param identityID the execute-as identity.
    * @param type       the identity type, {@link Identity#USER} or {@link Identity#GROUP}.
    * @param owner      the owner of the task, already allowed or unchanged.
    * @param principal  the caller.
    */
   public boolean isExecuteAsAllowed(IdentityID identityID, int type, IdentityID owner,
                                     Principal principal)
   {
      if(isUnrestricted(principal)) {
         return true;
      }

      IdentityID caller = IdentityID.getIdentityIDFromKey(principal.getName());
      String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(identityID == null || identityID.getOrgID() == null ||
         !identityID.getOrgID().equals(orgID))
      {
         return false;
      }

      if(type == Identity.USER && identityID.equals(caller)) {
         return true;
      }

      // the task editor offers nothing else when the caller neither owns the task nor
      // administers its owner
      if(owner == null || !owner.equals(caller) && !isAdmin(principal, owner)) {
         return false;
      }

      SecurityProvider provider = getProvider();

      // the users and groups of the lists the task editor offers
      if(type == Identity.USER) {
         return identityID.equals(owner) ||
            Arrays.asList(provider.getUsers()).contains(identityID) &&
            isAdmin(principal, identityID);
      }

      if(type == Identity.GROUP) {
         return Arrays.asList(provider.getGroups()).contains(identityID) &&
            provider.checkPermission(principal, ResourceType.SECURITY_GROUP,
                                     identityID.convertToKey(), ResourceAction.ADMIN);
      }

      return false;
   }

   /**
    * Determines if the caller may store the owner and execute-as identity of an imported task.
    *
    * @param task      the imported task, parsed with {@link #parseImportedTask}.
    * @param taskId    the task id, for the log.
    * @param principal the caller.
    */
   public boolean isAllowed(ScheduleTask task, String taskId, Principal principal) {
      if(isUnrestricted(principal)) {
         return true;
      }

      IdentityID owner = task.getOwner();

      if(!isOwnerAllowed(owner, principal)) {
         LOG.warn("Task {} is not imported, the owner {} is not allowed", taskId, owner);
         return false;
      }

      Identity identity = task.getIdentity();

      if(identity == null) {
         return true;
      }

      IdentityID identityID = identity.getIdentityID();

      if(!isExecuteAsAllowed(identityID, identity.getType(), owner, principal)) {
         LOG.warn("Task {} is not imported, the execute-as identity {} is not allowed",
                  taskId, identityID);
         return false;
      }

      return true;
   }

   /**
    * Parses an imported task. The owner, execute-as identity, completion conditions and
    * actions are moved to the caller's current organization, the same as a site admin deploy
    * import.
    */
   public ScheduleTask parseImportedTask(Element elem, Principal principal) throws Exception {
      ScheduleTask task = new ScheduleTask();
      Principal oldPrincipal = ThreadContext.getContextPrincipal();

      // parseXML(elem, true) takes the organization from the context principal, through the
      // no-arg OrganizationManager.getCurrentOrgID() which lower-cases it
      if(principal != null) {
         ThreadContext.setContextPrincipal(principal);
      }

      try {
         task.parseXML(elem, true);
      }
      finally {
         if(principal != null) {
            ThreadContext.setContextPrincipal(oldPrincipal);
         }
      }

      if(principal != null) {
         useCurrentOrgID(task, OrganizationManager.getInstance().getCurrentOrgID(principal));
      }

      return task;
   }

   /**
    * Bug #77259, an organization id may be mixed case (OrganizationIdRules) but parseXML(elem,
    * true) lower-cases the remapped owner and execute-as organization. Use the caller's actual
    * organization id so the owner matches the caller, the users and groups of the organization
    * and the task map the task is stored in, the same as a task created in the task editor.
    */
   private void useCurrentOrgID(ScheduleTask task, String orgID) {
      if(orgID == null) {
         return;
      }

      IdentityID owner = task.getOwner();

      if(owner != null && !orgID.equals(owner.getOrgID()) && orgID.equalsIgnoreCase(owner.getOrgID())) {
         task.setOwner(new IdentityID(owner.getName(), orgID));
      }

      Identity identity = task.getIdentity();
      IdentityID identityID = identity == null ? null : identity.getIdentityID();

      if(identityID != null && !orgID.equals(identityID.getOrgID()) &&
         orgID.equalsIgnoreCase(identityID.getOrgID()))
      {
         IdentityID id = new IdentityID(identityID.getName(), orgID);
         int type = identity.getType();
         SecurityProvider provider = getProvider();
         Identity resolved = provider == null ? null :
            type == Identity.USER ? provider.getUser(id) :
            type == Identity.GROUP ? provider.getGroup(id) :
            type == Identity.ROLE ? provider.getRole(id) : null;

         // keep an unresolved reference the same as parseXML does (Bug #77120)
         task.setIdentity(resolved != null ? resolved :
                             type == Identity.GROUP ? new Group(id) :
                             type == Identity.ROLE ? new Role(id) : new User(id));
      }
   }

   /**
    * Gets the names of the internal tasks whose actions or conditions are in the task, or that
    * a completion condition of the task depends on. Only a caller that may write those
    * internal tasks may import the task.
    */
   public static Set<String> getInternalTaskContents(ScheduleTask task) {
      Set<String> names = new HashSet<>();

      for(int i = 0; i < task.getActionCount(); i++) {
         ScheduleAction action = task.getAction(i);

         if(action instanceof AssetFileBackupAction) {
            names.add(InternalScheduledTaskService.ASSET_FILE_BACKUP);
         }
         else if(action instanceof TaskBalancerAction) {
            names.add(InternalScheduledTaskService.BALANCE_TASKS);
         }
         else if(action instanceof UpdateAssetsDependenciesAction) {
            names.add(InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES);
         }
      }

      for(int i = 0; i < task.getConditionCount(); i++) {
         ScheduleCondition condition = task.getCondition(i);

         if(condition instanceof TaskBalancerCondition) {
            names.add(InternalScheduledTaskService.BALANCE_TASKS);
         }
         else if(condition instanceof CompletionCondition completion &&
            ScheduleManager.isInternalTask(completion.getTaskName()))
         {
            names.add(completion.getTaskName());
         }
      }

      return names;
   }

   private boolean isAdmin(Principal principal, IdentityID user) {
      return getProvider().checkPermission(
         principal, ResourceType.SECURITY_USER, user.convertToKey(), ResourceAction.ADMIN);
   }

   private SecurityProvider getProvider() {
      return securityProvider.get();
   }

   private final BooleanSupplier securityEnabled;
   private final Supplier<SecurityProvider> securityProvider;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskIdentityChecker.class);
}
