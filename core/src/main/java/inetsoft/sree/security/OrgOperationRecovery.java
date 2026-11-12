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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrgOperationJournal.Entry;
import inetsoft.sree.security.OrgOperationJournal.State;
import inetsoft.sree.security.OrgOperationJournal.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Finishes or undoes the organization clones and id changes that were interrupted (Bug #79875),
 * see {@link OrgOperationJournal}. An id change is rolled forward: the records of the same pair
 * of ids are written again, every step skips what is already done. A clone is rolled back: the
 * partial organization is deleted, the source organization was never changed. Either way a retry
 * of the operation works afterwards.
 * <p>
 * Nothing runs the recovery on its own. It is called before the code that consults an
 * organization id (create, edit), because the death of a node does not reach a membership
 * listener (see claude/cluster.md), the test whether an operation is abandoned reads the
 * membership of the cluster when it is called.
 */
public final class OrgOperationRecovery {
   private OrgOperationRecovery() {
   }

   /**
    * Recovers the interrupted operations of the cluster with the default journal.
    *
    * @param actions the steps of the recovery.
    *
    * @return the number of operations that were recovered.
    */
   public static int recover(Actions actions) {
      return recoverEntries(actions).size();
   }

   /**
    * Recovers the interrupted operations of the cluster with the default journal.
    *
    * @return the records of the operations that were recovered.
    */
   public static List<Entry> recoverEntries(Actions actions) {
      return recoverEntries(OrgOperationJournal.getInstance(), actions,
                            OrgOperationRecovery::isAbandoned);
   }

   static int recover(OrgOperationJournal journal, Actions actions, Abandoned abandoned) {
      return recoverEntries(journal, actions, abandoned).size();
   }

   static List<Entry> recoverEntries(OrgOperationJournal journal, Actions actions,
                                     Abandoned abandoned)
   {
      List<Entry> recovered = new ArrayList<>();

      // the common case, nothing was interrupted, costs a read of an empty store
      if(journal.list().isEmpty()) {
         return recovered;
      }

      Lock lock = Cluster.getInstance().getLock(LOCK_NAME);
      lock.lock();

      try {
         // read again under the lock, another node may have recovered the operation meanwhile
         for(Entry entry : journal.list()) {
            if(!abandoned.test(entry)) {
               continue;
            }

            try {
               LOG.warn("Recovering the interrupted organization operation {}", entry);

               if(entry.getType() == Type.RENAME) {
                  actions.rollForwardRename(entry);
               }
               else {
                  actions.rollBackClone(entry);
               }

               journal.complete(entry.getOpId());
               recovered.add(entry);
            }
            catch(Exception e) {
               // kept, so that the next call tries again. The ids stay refused until then, as
               // the leftovers may not be removed without the record
               LOG.error("Failed to recover the interrupted organization operation {}", entry, e);
               journal.markFailed(entry.getOpId());
            }
         }
      }
      finally {
         lock.unlock();
      }

      return recovered;
   }

   /**
    * Finds the editable authentication provider that holds the organizations, the way the edit
    * paths of IdentityService do: the providers of the authentication chain, not the type of the
    * security provider ({@link inetsoft.sree.internal.SUtil#getEditableAuthenticationProvider}
    * only unwraps an AbstractSecurityProvider, which the enterprise provider is not).
    *
    * @param security     the security provider.
    * @param providerName the name of the provider that the operation was started with, or null.
    *
    * @return the provider with the name, or the first editable one, or null if there is none.
    */
   public static EditableAuthenticationProvider findEditableProvider(SecurityProvider security,
                                                                     String providerName)
   {
      AuthenticationProvider auth = security == null ? null : security.getAuthenticationProvider();
      List<AuthenticationProvider> providers = new ArrayList<>();

      if(auth instanceof AuthenticationChain) {
         providers.addAll(((AuthenticationChain) auth).getProviders());
      }
      else if(auth != null) {
         providers.add(auth);
      }

      EditableAuthenticationProvider first = null;

      for(AuthenticationProvider p : providers) {
         if(p instanceof EditableAuthenticationProvider) {
            if(providerName != null && providerName.equals(p.getProviderName())) {
               return (EditableAuthenticationProvider) p;
            }

            if(first == null) {
               first = (EditableAuthenticationProvider) p;
            }
         }
      }

      return first;
   }

   /**
    * Determines if nothing runs the operation: it stopped with an error, or the node that ran
    * it left the cluster, and no node of the cluster changes either of its organizations.
    */
   public static boolean isAbandoned(Entry entry) {
      if(OrgIdChangeGuard.isActiveOnLiveNode(entry.getFromId()) ||
         OrgIdChangeGuard.isActiveOnLiveNode(entry.getToId()))
      {
         return false;
      }

      return isOwnerGone(entry);
   }

   /**
    * Determines if nothing runs the operation, whatever the guard says: it stopped with an
    * error or the node that ran it left the cluster.
    */
   static boolean isOwnerGone(Entry entry) {
      return entry.getState() == State.FAILED ||
         !Cluster.getInstance().getClusterNodeIds().contains(entry.getNode());
   }

   /**
    * The steps of the recovery of an operation.
    */
   public interface Actions {
      /**
       * Completes the id change of the organization. Must tolerate any step that is already
       * done, and a source or target that is already gone.
       */
      void rollForwardRename(Entry entry) throws Exception;

      /**
       * Deletes the partial organization of the clone. Must tolerate any step that was not
       * done.
       */
      void rollBackClone(Entry entry) throws Exception;
   }

   @FunctionalInterface
   interface Abandoned {
      boolean test(Entry entry);
   }

   private static final String LOCK_NAME = OrgOperationRecovery.class.getName() + ".lock";
   private static final Logger LOG = LoggerFactory.getLogger(OrgOperationRecovery.class);
}
