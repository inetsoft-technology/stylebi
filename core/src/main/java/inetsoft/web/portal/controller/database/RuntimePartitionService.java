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
package inetsoft.web.portal.controller.database;

import com.google.common.collect.Sets;
import inetsoft.sree.internal.cluster.*;
import inetsoft.uql.erm.AutoAlias;
import inetsoft.uql.erm.XDataModel;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.graph.PhysicalGraphLayout;
import org.apache.commons.collections4.EnumerationUtils;
import org.apache.ignite.transactions.TransactionHeuristicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.cache.Cache;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class RuntimePartitionService {
   @Autowired
   public RuntimePartitionService(Cluster cluster) {
      this.cluster = cluster;
   }

   @PostConstruct
   public void init() {
      cache = cluster.getCache(
         CACHE_NAME, false, new TouchedExpiryPolicy(new Duration(TimeUnit.MINUTES, 3L)));
   }

   /**
    * create a new model.
    */
   public RuntimeXPartition createModel(XPartition partition, String database) {
      return createModel(partition, database, null);
   }

   /**
    * Creates a new runtime.
    *
    * @param init prepares the new runtime before it is stored, or {@code null}.
    */
   public RuntimeXPartition createModel(XPartition partition, String database,
                                        Consumer<RuntimeXPartition> init)
   {
      RuntimeXPartition runtimeXPartition =
         new RuntimeXPartition(partition, generateRuntimeId(partition.getName()),
            database);

      if(init != null) {
         init.accept(runtimeXPartition);
      }

      putNew(runtimeXPartition);

      return runtimeXPartition;
   }

   /**
    * Open the physical model create a runtime.
    */
   public RuntimeXPartition openModel(XDataModel dataModel, String name, String parent) {
      return openModel(dataModel, name, parent, null);
   }

   /**
    * Opens a physical model in a new runtime.
    *
    * @param init prepares the new runtime before it is stored, or {@code null}.
    */
   public RuntimeXPartition openModel(XDataModel dataModel, String name, String parent,
                                      Consumer<RuntimeXPartition> init)
   {
      boolean isExtended = StringUtils.hasText(parent);
      XPartition partition = !isExtended ? dataModel.getPartition(name) :
         dataModel.getPartition(parent);

      if(partition == null) {
         return null;
      }

      if(isExtended) {
         partition = partition.getPartition(name);

         if(partition == null) {
            return null;
         }
      }

      RuntimeXPartition runtimeXPartition =
         new RuntimeXPartition((XPartition) partition.deepClone(true), generateRuntimeId(name),
            dataModel.getDataSource());

      if(init != null) {
         init.accept(runtimeXPartition);
      }

      putNew(runtimeXPartition);

      return runtimeXPartition;
   }

   public String openNewRuntimePartition(String oldId) throws Exception {
      RuntimeXPartition partition = (RuntimeXPartition) this.getRuntimePartition(oldId).clone();
      String newId = generateRuntimeId(partition.partition.getName());
      partition.setId(newId);

      putNew(partition);

      return newId;
   }

   public void closeRuntimePartition(String originRuntimeId, String newRuntimeId, boolean save) {
      closeRuntimePartition(originRuntimeId, newRuntimeId, save, null);
   }

   /**
    * Closes the runtime of a join edit pane and, when saving, replaces the runtime the pane was
    * opened from with it. Both runtimes are locked in a fixed order, the origin first, and the
    * pane runtime is destroyed in the same transaction.
    *
    * @param beforeSave prepares the pane runtime before it replaces the origin, or
    *                   {@code null}. It must only change the runtime in memory, see
    *                   {@link #update}.
    */
   public void closeRuntimePartition(String originRuntimeId, String newRuntimeId, boolean save,
                                     Consumer<RuntimeXPartition> beforeSave)
   {
      runLocked(originRuntimeId, () -> {
         // Bug #78428, lock the origin before the pane, the only order a transaction that
         // locks two runtimes may use
         RuntimeXPartition oldPartition = cache.get(originRuntimeId);
         RuntimeXPartition newPartition = cache.get(newRuntimeId);

         if(oldPartition == null || newPartition == null) {
            return null;
         }

         if(save) {
            if(beforeSave != null) {
               beforeSave.accept(newPartition);
            }

            newPartition.setId(originRuntimeId);
            newPartition.version = oldPartition.version + 1;
            cache.put(originRuntimeId, newPartition);
         }

         cache.remove(newRuntimeId);
         return null;
      });
   }

   /**
    * Changes a runtime atomically. The mutation is applied to a fresh copy of the runtime that
    * is read in a cluster transaction, which locks the runtime, and the copy is written back in
    * the same transaction. Concurrent changes of a runtime, on any node or thread, are applied
    * one after the other and none of them is lost.
    * <p>
    * Bug #78428, an open transaction delays partition map exchange and blocks the other
    * writers of the runtime, so the mutation must only change the runtime in memory: it must
    * not read data source meta-data, query a database, read or write storage, or call another
    * update. Such work is done before the update, on a copy from {@link #getRuntimePartition},
    * and the mutation applies only its result, after checking that what the result depends on
    * is still in the fresh copy. The mutation runs again on a new copy if the transaction
    * fails, so it must not depend on anything it changes outside the runtime.
    *
    * @param id       the runtime identifier.
    * @param mutation changes the runtime in memory and returns a result.
    *
    * @return the result of the mutation, or {@code null} if the runtime does not exist.
    *
    * @throws E                if the mutation throws it, nothing is written.
    * @throws MessageException if the runtime could not be locked or written after a few
    *                          attempts, nothing is written.
    */
   public <T, E extends Exception> T update(String id, Mutation<T, E> mutation) throws E {
      if(!StringUtils.hasText(id)) {
         return null;
      }

      return runLocked(id, () -> {
         RuntimeXPartition runtime = cache.get(id);

         if(runtime == null) {
            return null;
         }

         T result = mutation.apply(runtime);
         runtime.version++;
         cache.put(id, runtime);
         return result;
      });
   }

   /**
    * Runs an action in a short cluster transaction, and runs it again a few times if the
    * transaction fails, e.g. because another request held the lock of the runtime too long.
    */
   private <T, E extends Exception> T runLocked(String id, TransactionalAction<T, E> action)
      throws E
   {
      if(UPDATING.get() != null) {
         // a nested update would join the outer transaction, and could neither retry nor
         // fail on its own
         throw new IllegalStateException(
            "A physical view runtime update can't run inside another one: " + id);
      }

      UPDATING.set(Boolean.TRUE);

      try {
         for(int attempt = 1; ; attempt++) {
            try {
               return cluster.runInTransaction(TX_TIMEOUT, TimeUnit.MILLISECONDS, action);
            }
            catch(DistributedTransactionException ex) {
               if(attempt >= MAX_ATTEMPTS || isHeuristic(ex)) {
                  LOG.warn("Failed to update the physical view runtime {} after {} attempt(s)",
                           id, attempt, ex);
                  throw updateFailed(ex);
               }

               LOG.debug("Failed to update the physical view runtime {}, retrying", id, ex);

               try {
                  Thread.sleep(RETRY_DELAY * attempt);
               }
               catch(InterruptedException ie) {
                  Thread.currentThread().interrupt();
                  throw updateFailed(ex);
               }
            }
         }
      }
      finally {
         UPDATING.remove();
      }
   }

   private static MessageException updateFailed(Exception cause) {
      MessageException ex = new MessageException(
         Catalog.getCatalog().getString("data.physicalmodel.runtimeBusy"), LogLevel.WARN, false);
      ex.initCause(cause);
      return ex;
   }

   /**
    * Checks if a transaction failed with a heuristic outcome while committing, which may have
    * written some of its changes, so that it must not run again.
    */
   private static boolean isHeuristic(Throwable ex) {
      for(Throwable cause = ex; cause != null; cause = cause.getCause()) {
         if(cause instanceof TransactionHeuristicException) {
            return true;
         }
      }

      return false;
   }

   /**
    * Stores a runtime under a new identifier that no client knows yet. Every change of an
    * existing runtime goes through {@link #update}.
    */
   private void putNew(RuntimeXPartition runtimeXPartition) {
      cache.put(runtimeXPartition.getId(), runtimeXPartition);
   }

   public static String generateRuntimeId(String name) {
      return name + UUID.randomUUID().toString().replace("-", "");
   }

   /**
    * Get the physical model from runtime.
    */
   public XPartition getPartition(String id) {
      RuntimeXPartition runtimeXPartition = cache.get(id);

      if(runtimeXPartition == null) {
         return null;
      }

      XPartition partition = runtimeXPartition.getPartition();

      if(partition == null) {
         throw new RuntimeException("Partition is null for id: " + id);
      }

      return partition;
   }

   /**
    * get run time partition.
    */
   public RuntimeXPartition getRuntimePartition(String id) {
      return cache.get(id);
   }

   /**
    * Checks that a data source named in a request is the one the runtime was opened on. The
    * runtime's source was permission-checked when it was opened, the request parameter was not.
    *
    * @param runtimePartition the runtime, or {@code null} if it does not exist.
    * @param dataSource       the data source named in the request.
    *
    * @throws java.io.FileNotFoundException if the runtime does not exist.
    * @throws inetsoft.sree.security.SecurityException if the names differ.
    */
   public static void checkDataSource(RuntimeXPartition runtimePartition, String dataSource)
      throws java.io.FileNotFoundException, inetsoft.sree.security.SecurityException
   {
      if(runtimePartition == null) {
         throw new java.io.FileNotFoundException("The physical view runtime does not exist");
      }

      if(!Objects.equals(runtimePartition.getDataSource(), dataSource)) {
         throw new inetsoft.sree.security.SecurityException(
            "Data source \"" + dataSource + "\" does not match the open physical view");
      }
   }

   /**
    * Destroy the runtime.
    */
   public void destroy(String id) {
      cache.remove(id);
   }

   /**
    * Process the heartbeat.
    *
    * @return {@code true} if the partition has already expired or {@code false} if the partition
    *         is not expired and the access time has been updated.
    */
   public boolean touch(String id) {
      return isExpired(id);
   }

   public boolean isExpired(String id) {
      if(!StringUtils.hasText(id)) {
         return true;
      }

      return cache.get(id) == null;
   }

   private final Cluster cluster;
   private Cache<String, RuntimeXPartition> cache;
   public static final String CACHE_NAME =
      "inetsoft.web.portal.controller.database.RuntimePartitionService.cache";
   // the timeout of the transaction of an update, which also bounds waiting for the lock
   static final long TX_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
   static final int MAX_ATTEMPTS = 3;
   private static final long RETRY_DELAY = 200L;
   private static final ThreadLocal<Boolean> UPDATING = new ThreadLocal<>();
   private static final Logger LOG = LoggerFactory.getLogger(RuntimePartitionService.class);

   /**
    * A change of a runtime, see {@link #update}.
    */
   @FunctionalInterface
   public interface Mutation<T, E extends Exception> {
      /**
       * Changes the runtime in memory.
       *
       * @param runtime a fresh copy of the runtime, which is locked until it is written back.
       *
       * @return the result of the update.
       */
      T apply(RuntimeXPartition runtime) throws E;
   }

   public static final class RuntimeXPartition implements Cloneable, Serializable {
      public RuntimeXPartition() {
      }

      public RuntimeXPartition(XPartition partition, String id, String dataSource) {
         this.partition = partition;
         this.id = id;
         this.dataSource = dataSource;
      }

      public XPartition getPartition() {
         return partition;
      }

      public void setPartition(XPartition partition) {
         this.partition = partition;
      }

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public String getDataSource() {
         return dataSource;
      }

      /**
       * Gets the number of changes of the runtime, which every update increases.
       */
      public long getVersion() {
         return version;
      }

      @Override
      protected Object clone() throws CloneNotSupportedException {
         RuntimeXPartition clone = (RuntimeXPartition) super.clone();
         clone.partition = (XPartition) this.partition.clone();
         clone.dataSource = this.dataSource;
         clone.id = this.id;
         clone.graphWidth = this.graphWidth;
         clone.graphHeight = this.graphHeight;
         clone.removedIncomingJoins = new ConcurrentHashMap<>();
         this.getRemovedIncomingJoins().forEach(
            (key, removed) -> clone.removedIncomingJoins.put(key, removed.clone()));

         return clone;
      }

      public Set<String> getMovedTables() {
         return Collections.unmodifiableSet(movedTables);
      }

      public void addMovedTable(String table) {
         this.movedTables.add(table);
      }

      public boolean removeMovedTable(String name) {
         return movedTables.remove(name);
      }

      public void clearMovedTables() {
         movedTables.clear();
      }

      public void setBounds(String tableName, Rectangle bounds) {
         this.setBounds(tableName, bounds, false);
      }

      public void setBounds(String tableName, Rectangle bounds, boolean runtime) {
         partition.setBounds(tableName, bounds, runtime);
         this.addMovedTable(tableName);
      }

      public int getGraphWidth() {
         return graphWidth;
      }

      public void setGraphWidth(int graphWidth) {
         this.graphWidth = graphWidth;
      }

      public int getGraphHeight() {
         return graphHeight;
      }

      public void setGraphHeight(int graphHeight) {
         this.graphHeight = graphHeight;
      }

      /**
       * Remembers an auto-alias incoming join that was removed with the last join between its
       * tables, so that re-adding a join between the tables in this editor session can restore
       * the same auto-alias.
       */
      public void rememberRemovedIncomingJoin(RemovedIncomingJoin removed) {
         getRemovedIncomingJoins().put(
            getRemovedIncomingJoinKey(removed.getTable(), removed.getJoin().getSourceTable()),
            removed);
      }

      /**
       * Gets a remembered auto-alias incoming join.
       *
       * @param table       the auto-aliased table.
       * @param sourceTable the source table of the incoming join.
       *
       * @return the removed incoming join or {@code null} if none.
       */
      public RemovedIncomingJoin getRemovedIncomingJoin(String table, String sourceTable) {
         return getRemovedIncomingJoins().get(getRemovedIncomingJoinKey(table, sourceTable));
      }

      /**
       * Forgets the remembered incoming joins between two tables, in both directions.
       */
      public void forgetRemovedIncomingJoins(String table1, String table2) {
         getRemovedIncomingJoins().remove(getRemovedIncomingJoinKey(table1, table2));
         getRemovedIncomingJoins().remove(getRemovedIncomingJoinKey(table2, table1));
      }

      /**
       * Forgets the remembered incoming joins of a table, either as the auto-aliased table or as
       * the source table.
       */
      public void forgetRemovedIncomingJoins(String table) {
         getRemovedIncomingJoins().values().removeIf(removed ->
            Objects.equals(table, removed.getTable()) ||
            Objects.equals(table, removed.getJoin().getSourceTable()));
      }

      /**
       * Forgets the remembered incoming joins whose tables are joined again in the partition.
       */
      public void forgetJoinedRemovedIncomingJoins() {
         getRemovedIncomingJoins().values().removeIf(removed -> partition.findRelationship(
            removed.getJoin().getSourceTable(), removed.getTable()) != null);
      }

      public void clearRemovedIncomingJoins() {
         getRemovedIncomingJoins().clear();
      }

      private Map<String, RemovedIncomingJoin> getRemovedIncomingJoins() {
         // null when deserialized from a version without the field
         if(removedIncomingJoins == null) {
            removedIncomingJoins = new ConcurrentHashMap<>();
         }

         return removedIncomingJoins;
      }

      private static String getRemovedIncomingJoinKey(String table, String sourceTable) {
         return table + "|" + sourceTable;
      }

      @Override
      public String toString() {
         return "RuntimeXPartition{" +
            "partition=" + partition +
            ", relationships=" + EnumerationUtils.toList(partition.getRelationships()) +
            ", id='" + id + '\'' +
            ", dataSource='" + dataSource + '\'' +
            ", graphWidth=" + graphWidth +
            ", graphHeight=" + graphHeight +
            ", movedTables=" + movedTables +
            ", removedIncomingJoins=" + getRemovedIncomingJoins().keySet() +
            '}';
      }

      private XPartition partition;
      private String id;
      private String dataSource;
      private long version;
      private int graphWidth = PhysicalGraphLayout.DEFAULT_VIEWPORT_WIDTH;
      private int graphHeight = PhysicalGraphLayout.DEFAULT_VIEWPORT_HEIGHT;
      private final Set<String> movedTables = Sets.newConcurrentHashSet();
      // auto-alias incoming joins removed with the last join between their tables in this
      // editor session, keyed by "table|sourceTable"
      private Map<String, RemovedIncomingJoin> removedIncomingJoins = new ConcurrentHashMap<>();
   }

   /**
    * An auto-alias incoming join removed from a table, and its index in the auto-alias.
    */
   public static final class RemovedIncomingJoin implements Cloneable, Serializable {
      public RemovedIncomingJoin() {
      }

      public RemovedIncomingJoin(String table, int index, AutoAlias.IncomingJoin join) {
         this.table = table;
         this.index = index;
         this.join = join;
      }

      /**
       * Gets the auto-aliased table.
       */
      public String getTable() {
         return table;
      }

      /**
       * Gets the index of the incoming join in the auto-alias.
       */
      public int getIndex() {
         return index;
      }

      public AutoAlias.IncomingJoin getJoin() {
         return join;
      }

      @Override
      public RemovedIncomingJoin clone() {
         return new RemovedIncomingJoin(table, index, (AutoAlias.IncomingJoin) join.clone());
      }

      private String table;
      private int index;
      private AutoAlias.IncomingJoin join;
   }
}
