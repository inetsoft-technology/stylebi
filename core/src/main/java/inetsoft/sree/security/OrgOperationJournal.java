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

import com.fasterxml.jackson.annotation.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueStorage;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Durable record of an organization clone or id change that is in progress (Bug #79875). Both
 * operations are a long series of writes into several stores (identities, properties, the data
 * space, blob storages) that no transaction spans, so the death of the node that runs one leaves
 * the organizations half done, and the half done state blocks a retry. The record is written
 * before the first write and removed after the last one, in a store that survives the loss of
 * a node or of the whole cluster, so that {@link OrgOperationRecovery} can finish (id change) or
 * undo (clone) an operation whose node is gone.
 * <p>
 * The record also is the proof that files under the new organization id belong to the operation,
 * and not to someone else. A new id is only accepted when the data space has no path of that id
 * (see {@link OrganizationIdRules#hasDataSpacePaths(String)}), which is checked before the record
 * is written.
 */
public final class OrgOperationJournal {
   OrgOperationJournal(Store store) {
      this.store = store;
   }

   /**
    * Gets the journal of the current cluster.
    */
   public static OrgOperationJournal getInstance() {
      Cluster cluster = Cluster.getInstance();
      Holder h = holder;

      if(h == null || h.cluster != cluster) {
         synchronized(OrgOperationJournal.class) {
            h = holder;

            if(h == null || h.cluster != cluster) {
               h = new Holder(cluster, new OrgOperationJournal(new KeyValueJournalStore(
                  KeyValueStorage.newInstance(STORAGE_ID, cluster))));
               holder = h;
            }
         }
      }

      return h.journal;
   }

   /**
    * Records the start of an operation. Must be called before the operation writes anything.
    *
    * @param type    the type of the operation.
    * @param fromId  the id of the organization that is cloned or whose id is changed.
    * @param toId    the new organization id.
    * @param payload what the operation needs to be resumed, or null.
    * @param targets the data space paths the operation creates under the new id.
    *
    * @return the record.
    *
    * @throws IllegalStateException if an operation on one of the ids is already recorded.
    */
   public Entry begin(Type type, String fromId, String toId, String payload,
                      List<String> targets)
   {
      synchronized(this) {
         for(Entry e : store.values()) {
            if(e.involves(fromId) || e.involves(toId)) {
               throw new IllegalStateException(
                  "Organization " + e.fromId + " is already being " +
                  (e.type == Type.CLONE ? "cloned to " : "changed to ") + e.toId);
            }
         }

         Entry entry = new Entry(UUID.randomUUID().toString(), type, fromId, toId,
                                 Cluster.getInstance().getLocalNodeId(),
                                 System.currentTimeMillis(), State.RUNNING, payload,
                                 targets == null ? List.of() : targets);
         store.put(entry.opId, entry);
         return entry;
      }
   }

   /**
    * Records that the operation stopped with an error in a running node, so that nothing waits
    * for that node to leave the cluster before the operation is recovered.
    */
   public void markFailed(String opId) {
      synchronized(this) {
         Entry e = store.get(opId);

         if(e != null && e.state != State.FAILED) {
            store.put(opId, e.withState(State.FAILED));
         }
      }
   }

   /**
    * Removes the record of an operation that completed, or was recovered.
    */
   public void complete(String opId) {
      if(opId != null) {
         synchronized(this) {
            store.remove(opId);
         }
      }
   }

   /**
    * Gets the records of all unfinished operations.
    */
   public List<Entry> list() {
      return new ArrayList<>(store.values());
   }

   /**
    * Gets the record of an operation.
    */
   public Entry get(String opId) {
      return store.get(opId);
   }

   private static String key(String orgId) {
      return orgId == null ? "" : orgId.toLowerCase(Locale.ROOT);
   }

   public enum Type {
      CLONE, RENAME
   }

   public enum State {
      /** The node that runs the operation is expected to finish it. */
      RUNNING,
      /** The operation stopped with an exception, nothing runs it. */
      FAILED
   }

   /**
    * The record of an operation.
    */
   @JsonIgnoreProperties(ignoreUnknown = true)
   public static final class Entry implements Serializable {
      @JsonCreator
      Entry(@JsonProperty("opId") String opId, @JsonProperty("type") Type type,
            @JsonProperty("fromId") String fromId, @JsonProperty("toId") String toId,
            @JsonProperty("node") String node, @JsonProperty("started") long started,
            @JsonProperty("state") State state, @JsonProperty("payload") String payload,
            @JsonProperty("targets") List<String> targets)
      {
         this.opId = opId;
         this.type = type;
         this.fromId = fromId;
         this.toId = toId;
         this.node = node;
         this.started = started;
         this.state = state;
         this.payload = payload;
         this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
      }

      Entry withState(State state) {
         return new Entry(opId, type, fromId, toId, node, started, state, payload, targets);
      }

      public String getOpId() {
         return opId;
      }

      public Type getType() {
         return type;
      }

      public String getFromId() {
         return fromId;
      }

      public String getToId() {
         return toId;
      }

      /**
       * Gets the id of the cluster node that runs the operation.
       */
      public String getNode() {
         return node;
      }

      public long getStarted() {
         return started;
      }

      public State getState() {
         return state;
      }

      /**
       * Gets what the operation needs to be resumed.
       */
      public String getPayload() {
         return payload;
      }

      /**
       * Gets the data space paths that the operation creates under the new organization id.
       */
      public List<String> getTargets() {
         return Collections.unmodifiableList(targets);
      }

      /**
       * Determines if the record is of an operation that touches the organization id, the
       * comparison ignores case, like the organization ids do.
       */
      public boolean involves(String orgId) {
         return orgId != null && (key(orgId).equals(key(fromId)) || key(orgId).equals(key(toId)));
      }

      @Override
      public String toString() {
         return type + " " + fromId + " -> " + toId + " (" + opId + ", node " + node + ", " +
            state + ", started " + TimeUnit.MILLISECONDS.toSeconds(started) + ")";
      }

      private final String opId;
      private final Type type;
      private final String fromId;
      private final String toId;
      private final String node;
      private final long started;
      private final State state;
      private final String payload;
      private final ArrayList<String> targets;
      @Serial
      private static final long serialVersionUID = 1L;
   }

   /**
    * The storage of the records.
    */
   interface Store {
      Entry get(String key);

      void put(String key, Entry entry);

      void remove(String key);

      Collection<Entry> values();
   }

   private static final class KeyValueJournalStore implements Store {
      KeyValueJournalStore(KeyValueStorage<Entry> storage) {
         this.storage = storage;
      }

      @Override
      public Entry get(String key) {
         return storage.get(key);
      }

      @Override
      public void put(String key, Entry entry) {
         // the record must be durable before the operation writes anything, so a failure to
         // write it stops the operation
         try {
            storage.put(key, entry).get(30L, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while recording an organization " +
                                            "operation", e);
         }
         catch(Exception e) {
            throw new IllegalStateException("Failed to record an organization operation", e);
         }
      }

      @Override
      public void remove(String key) {
         try {
            storage.remove(key).get(30L, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while removing an organization " +
                                            "operation record", e);
         }
         catch(Exception e) {
            throw new IllegalStateException("Failed to remove an organization operation record",
                                            e);
         }
      }

      @Override
      public Collection<Entry> values() {
         List<Entry> list = new ArrayList<>();
         storage.stream().forEach(p -> list.add(p.getValue()));
         return list;
      }

      private final KeyValueStorage<Entry> storage;
   }

   private static final class Holder {
      Holder(Cluster cluster, OrgOperationJournal journal) {
         this.cluster = cluster;
         this.journal = journal;
      }

      final Cluster cluster;
      final OrgOperationJournal journal;
   }

   private final Store store;
   private static volatile Holder holder;
   private static final String STORAGE_ID = "orgOperationJournal";
}
