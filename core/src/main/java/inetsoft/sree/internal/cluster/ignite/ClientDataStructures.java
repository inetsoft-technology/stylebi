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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.internal.cluster.DistributedReference;
import org.apache.ignite.*;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterTopologyException;
import org.apache.ignite.configuration.ExecutorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * The cluster locks and atomics of a client node (a cloud-runner task container), kept on a
 * server on the client's behalf (bug #78451).
 * <p>
 * An Ignite lock, semaphore, latch or atomic that a node creates or obtains makes that node
 * register Ignite's internal data-structure continuous query on every server. Its notifications
 * are synchronous: every lock-state change, and every removal of a data structure, is sent to the
 * node on a server's {@code sys-stripe} thread inside the commit. A server cannot dial a client
 * started with {@code forceClientToServerConnections}, so after such a client dies without
 * leaving (container killed) each of those commits waits for it until discovery drops it, about
 * 30 s, and so does every login and logout, which take the license lock. Ignite has no setting
 * that turns the query off, so a client node never touches an Ignite data structure. Instead it
 * sends each operation to a server:
 * <ul>
 *    <li>A lock is taken on the oldest server, by a server thread dedicated to the client thread,
 *    so Ignite's per-thread ownership and reentrancy apply to each client thread as they do to a
 *    server thread. The client remembers which server granted each hold and sends the reentrant
 *    takes and the release of that hold there. A lock wait is sent in steps of at most
 *    {@link #LOCK_STEP_MILLIS}, so no server thread waits long for a client. Waiting steps run on
 *    their own server executor ({@link #WAIT_EXECUTOR}), so they never delay a release, an
 *    immediate {@code tryLock()} or an atomic operation ({@link #RELEASE_EXECUTOR}). The server
 *    releases what a client thread still holds once the client leaves the topology.</li>
 *    <li>An atomic operation runs on the oldest server's own Ignite atomic.</li>
 * </ul>
 * A lock that a client thread waits for in several steps rejoins the queue of a fair lock at
 * each step. {@link Lock#newCondition()} is not supported, and no caller of a cluster lock uses
 * it.
 * <p>
 * If the server that granted a hold leaves the cluster (for example a rolling update of the
 * oldest server) while the client thread holds the lock, Ignite marks the lock broken, as it does
 * for a lock held by any node that leaves: mutual exclusion is lost for the rest of that critical
 * section, and later takes of the lock fail with "Lock broken". The client's release of such a
 * hold only logs a warning, so an operational event doesn't replace the result of the task.
 */
final class ClientDataStructures {
   private ClientDataStructures() {
   }

   /**
    * Adds the server executors of the jobs of client nodes. Every node gets them, as with the
    * other executors, and a server runs them.
    */
   static void addExecutors(IgniteConfiguration config) {
      ExecutorConfiguration wait = new ExecutorConfiguration(WAIT_EXECUTOR);
      wait.setSize(WAIT_EXECUTOR_SIZE);
      ExecutorConfiguration release = new ExecutorConfiguration(RELEASE_EXECUTOR);
      release.setSize(RELEASE_EXECUTOR_SIZE);
      ExecutorConfiguration[] old = config.getExecutorConfiguration();
      List<ExecutorConfiguration> executors =
         new ArrayList<>(old == null ? List.of() : Arrays.asList(old));
      executors.add(wait);
      executors.add(release);
      config.setExecutorConfiguration(executors.toArray(new ExecutorConfiguration[0]));
   }

   /**
    * Gets a cluster lock for a client node.
    */
   static Lock getLock(Ignite ignite, String name) {
      return new RemoteLock(ignite, name);
   }

   /**
    * Removes a cluster lock from a client node.
    */
   static void destroyLock(Ignite ignite, String name) {
      call(ignite, new DestroyJob(name, DestroyJob.LOCK));
   }

   /**
    * Gets a cluster long for a client node.
    */
   static DistributedLong getLong(Ignite ignite, String name) {
      return new RemoteLong(ignite, name);
   }

   /**
    * Removes a cluster long from a client node.
    */
   static void destroyLong(Ignite ignite, String name) {
      call(ignite, new DestroyJob(name, DestroyJob.LONG));
   }

   /**
    * Gets a cluster reference for a client node.
    */
   static <V> DistributedReference<V> getReference(Ignite ignite, String name) {
      return new RemoteReference<>(ignite, name);
   }

   /**
    * Removes a cluster reference from a client node.
    */
   static void destroyReference(Ignite ignite, String name) {
      call(ignite, new DestroyJob(name, DestroyJob.REFERENCE));
   }

   /**
    * Runs a job that doesn't wait on the oldest server and waits for its result.
    */
   private static <T> T call(Ignite ignite, IgniteCallable<T> job) {
      return call(ignite, ignite.cluster().forServers().forOldest(), RELEASE_EXECUTOR, job);
   }

   /**
    * Runs a job on a server and waits for its result. An interrupt does not abandon the job,
    * whose effect (a lock it took) the caller must know about; the interrupt status is kept.
    */
   private static <T> T call(Ignite ignite, ClusterGroup server, String executor,
                             IgniteCallable<T> job)
   {
      IgniteFuture<T> future = ignite.compute(server).withExecutor(executor).callAsync(job);
      boolean interrupted = false;

      try {
         while(true) {
            try {
               return future.get();
            }
            catch(IgniteInterruptedException e) {
               Thread.interrupted();
               interrupted = true;
            }
         }
      }
      finally {
         if(interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * A cluster lock of a client thread. The server-side holder is identified by the client node
    * and thread.
    */
   private static final class RemoteLock implements Lock {
      RemoteLock(Ignite ignite, String name) {
         this.ignite = ignite;
         this.name = name;
      }

      @Override
      public void lock() {
         while(!tryLockStep(LOCK_STEP_MILLIS)) {
            // keep waiting, as Lock.lock() does
         }
      }

      @Override
      public void lockInterruptibly() throws InterruptedException {
         while(!tryLock(LOCK_STEP_MILLIS, TimeUnit.MILLISECONDS)) {
            // keep waiting until interrupted
         }
      }

      @Override
      public boolean tryLock() {
         return tryLockStep(0L);
      }

      @Override
      public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
         if(Thread.interrupted()) {
            throw new InterruptedException();
         }

         long remaining = Math.max(0L, unit.toMillis(time));
         long deadline = System.currentTimeMillis() + remaining;

         while(true) {
            boolean acquired = tryLockStep(Math.min(remaining, LOCK_STEP_MILLIS));

            if(Thread.interrupted()) {
               if(acquired) {
                  unlock();
               }

               throw new InterruptedException();
            }

            if(acquired) {
               return true;
            }

            remaining = deadline - System.currentTimeMillis();

            if(remaining <= 0) {
               return false;
            }
         }
      }

      private boolean tryLockStep(long timeout) {
         Map<HoldKey, Hold> holds = holds(ignite);
         HoldKey key = new HoldKey(Thread.currentThread().threadId(), name);
         Hold hold = holds.get(key);
         ClusterGroup server;

         if(hold != null && ignite.cluster().node(hold.server) != null) {
            // a reentrant take goes to the holder of the earlier takes
            server = ignite.cluster().forNodeId(hold.server);
         }
         else {
            if(hold != null) {
               LOG.warn("The server that granted the cluster lock {} left the cluster while " +
                           "this thread held it, the lock is broken", name);
               holds.remove(key);
            }

            server = ignite.cluster().forServers().forOldest();
         }

         UUID granted = call(ignite, server, timeout > 0 ? WAIT_EXECUTOR : RELEASE_EXECUTOR,
                             new LockJob(ownerNode(), key.thread, name, timeout));

         if(granted != null) {
            holds.merge(key, new Hold(granted, 1), (a, b) -> new Hold(a.server, a.count + 1));
         }

         return granted != null;
      }

      @Override
      public void unlock() {
         Map<HoldKey, Hold> holds = holds(ignite);
         HoldKey key = new HoldKey(Thread.currentThread().threadId(), name);
         Hold hold = holds.get(key);

         if(hold == null) {
            throw new IllegalMonitorStateException(
               "The cluster lock " + name + " is not held by the current thread");
         }

         if(hold.count > 1) {
            holds.put(key, new Hold(hold.server, hold.count - 1));
         }
         else {
            holds.remove(key);
         }

         if(ignite.cluster().node(hold.server) == null) {
            LOG.warn("The server that granted the cluster lock {} left the cluster while this " +
                        "thread held it, the lock was broken and is not released", name);
            return;
         }

         try {
            UUID released = call(ignite, ignite.cluster().forNodeId(hold.server),
                                 RELEASE_EXECUTOR,
                                 new LockJob(ownerNode(), key.thread, name, -1L));

            if(released == null) {
               LOG.warn("The server that granted the cluster lock {} has no hold of this " +
                           "thread to release", name);
            }
         }
         catch(ClusterTopologyException e) {
            LOG.warn("The server that granted the cluster lock {} left the cluster while this " +
                        "thread held it, the lock was broken and is not released", name);
         }
      }

      private UUID ownerNode() {
         return ignite.cluster().localNode().id();
      }

      @NotNull
      @Override
      public Condition newCondition() {
         throw new UnsupportedOperationException("Conditions of a cluster lock on a client node");
      }

      private final Ignite ignite;
      private final String name;
   }

   /**
    * Gets the locks that the threads of a client node hold, with the server that granted each.
    * A thread reads and writes only its own entries.
    */
   private static Map<HoldKey, Hold> holds(Ignite ignite) {
      ConcurrentMap<Object, Object> map = ignite.cluster().nodeLocalMap();
      @SuppressWarnings("unchecked")
      Map<HoldKey, Hold> holds = (Map<HoldKey, Hold>)
         map.computeIfAbsent(HoldKey.class.getName(), k -> new ConcurrentHashMap<>());
      return holds;
   }

   private record HoldKey(long thread, String name) {
   }

   private record Hold(UUID server, int count) {
   }

   /**
    * Takes ({@code timeout >= 0}) or releases ({@code timeout < 0}) a lock on the server thread
    * that holds the locks of a client thread. Returns the id of this server if the lock was
    * taken, or if the client thread held the lock it releases, otherwise {@code null}.
    */
   private static final class LockJob implements IgniteCallable<UUID> {
      LockJob(UUID node, long thread, String name, long timeout) {
         this.node = node;
         this.thread = thread;
         this.name = name;
         this.timeout = timeout;
      }

      @Override
      public UUID call() {
         boolean done = LockHolders.get(ignite).run(new Owner(node, thread), name, timeout);
         return done ? ignite.cluster().localNode().id() : null;
      }

      @IgniteInstanceResource
      private transient Ignite ignite;
      private final UUID node;
      private final long thread;
      private final String name;
      private final long timeout;
   }

   /**
    * The server threads that hold locks for client threads, one per client thread.
    */
   private static final class LockHolders {
      private LockHolders(Ignite ignite) {
         this.ignite = ignite;
      }

      static LockHolders get(Ignite ignite) {
         ConcurrentMap<Object, Object> map = ignite.cluster().nodeLocalMap();
         return (LockHolders) map.computeIfAbsent(LockHolders.class.getName(),
                                                  k -> new LockHolders(ignite));
      }

      boolean run(Owner owner, String name, long timeout) {
         FutureTask<Boolean> task;

         while(true) {
            LockHolder holder = holders.computeIfAbsent(owner, this::startHolder);
            task = new FutureTask<>(() -> holder.apply(name, timeout));

            if(holder.submit(task)) {
               break;
            }

            // the holder ended after the lookup, start a new one
            holders.remove(owner, holder);
         }

         // The step is bounded by the lock timeout. Don't abandon it on an interrupt: the client
         // would not know about a lock the holder took.
         boolean interrupted = false;

         try {
            while(true) {
               try {
                  return task.get();
               }
               catch(InterruptedException e) {
                  interrupted = true;
               }
               catch(ExecutionException e) {
                  if(e.getCause() instanceof RuntimeException ex) {
                     throw ex;
                  }

                  throw new IgniteException(e.getCause());
               }
            }
         }
         finally {
            if(interrupted) {
               Thread.currentThread().interrupt();
            }
         }
      }

      private LockHolder startHolder(Owner owner) {
         LockHolder holder = new LockHolder(owner);
         holder.start();
         return holder;
      }

      /**
       * The thread that takes and releases the locks of one client thread, so that Ignite sees
       * one owner. It ends when it holds nothing and has been idle for a while, or when the client
       * has left, after releasing what the client still held.
       */
      private final class LockHolder extends Thread {
         LockHolder(Owner owner) {
            super("IgniteClientLock-" + owner.node + "-" + owner.thread);
            setDaemon(true);
            this.owner = owner;
         }

         synchronized boolean submit(Runnable task) {
            if(closed) {
               return false;
            }

            tasks.add(task);
            return true;
         }

         boolean apply(String name, long timeout) throws InterruptedException {
            if(timeout < 0) {
               Integer count = holds.get(name);

               if(count == null) {
                  return false;
               }

               IgniteLock lock = ignite.reentrantLock(name, true, false, false);

               if(lock != null) {
                  DistributedLockProxy.unlock(lock);
               }

               if(count > 1) {
                  holds.put(name, count - 1);
               }
               else {
                  holds.remove(name);
               }

               return true;
            }

            IgniteLock lock = ignite.reentrantLock(name, true, false, true);
            boolean acquired = timeout == 0 ? lock.tryLock() :
               lock.tryLock(timeout, TimeUnit.MILLISECONDS);

            if(acquired) {
               holds.merge(name, 1, Integer::sum);
            }

            return acquired;
         }

         @Override
         public void run() {
            long idleSince = System.currentTimeMillis();

            try {
               while(true) {
                  Runnable task = tasks.poll(1, TimeUnit.SECONDS);

                  if(task != null) {
                     task.run();
                     idleSince = System.currentTimeMillis();
                     continue;
                  }

                  boolean left = ignite.cluster().node(owner.node) == null;

                  if(left) {
                     releaseAll();
                  }

                  synchronized(this) {
                     if(tasks.isEmpty() && holds.isEmpty() &&
                        (left || System.currentTimeMillis() - idleSince > IDLE_MILLIS))
                     {
                        closed = true;
                        break;
                     }
                  }
               }
            }
            catch(InterruptedException | IllegalStateException ignore) {
               // the node is stopping
            }
            catch(Throwable e) {
               LOG.warn("Cluster lock holder of client thread {} on node {} failed",
                        owner.thread, owner.node, e);
            }
            finally {
               synchronized(this) {
                  closed = true;
               }

               holders.remove(owner, this);

               // a task queued just before the holder closed must not wait forever
               for(Runnable task; (task = tasks.poll()) != null; ) {
                  if(task instanceof Future<?> future) {
                     future.cancel(false);
                  }
               }
            }
         }

         /**
          * Releases the locks of a client that left the topology without releasing them.
          */
         private void releaseAll() {
            for(Map.Entry<String, Integer> entry : holds.entrySet()) {
               try {
                  IgniteLock lock = ignite.reentrantLock(entry.getKey(), true, false, false);

                  for(int i = 0; lock != null && i < entry.getValue(); i++) {
                     DistributedLockProxy.unlock(lock);
                  }
               }
               catch(Exception e) {
                  LOG.warn("Failed to release the cluster lock {} of client node {}",
                           entry.getKey(), owner.node, e);
               }
            }

            holds.clear();
         }

         private final Owner owner;
         private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
         // lock name -> hold count, used only by this thread
         private final Map<String, Integer> holds = new HashMap<>();
         private boolean closed;
      }

      private final Ignite ignite;
      private final ConcurrentMap<Owner, LockHolder> holders = new ConcurrentHashMap<>();
   }

   private record Owner(UUID node, long thread) {
   }

   /**
    * A cluster long of a client node.
    */
   private static final class RemoteLong extends DistributedLong {
      RemoteLong(Ignite ignite, String name) {
         this.ignite = ignite;
         this.name = name;
      }

      @Override
      public long get() {
         return apply(LongJob.GET, 0L, 0L);
      }

      @Override
      public void set(long newValue) {
         getAndSet(newValue);
      }

      @Override
      public void lazySet(long newValue) {
         getAndSet(newValue);
      }

      @Override
      public long getAndSet(long newValue) {
         return apply(LongJob.GET_AND_SET, newValue, 0L);
      }

      @Override
      public boolean compareAndSet(long expect, long update) {
         return apply(LongJob.COMPARE_AND_SET, expect, update) != 0L;
      }

      @Override
      public long getAndIncrement() {
         return getAndAdd(1L);
      }

      @Override
      public long getAndDecrement() {
         return getAndAdd(-1L);
      }

      @Override
      public long getAndAdd(long delta) {
         return apply(LongJob.GET_AND_ADD, delta, 0L);
      }

      @Override
      public long incrementAndGet() {
         return addAndGet(1L);
      }

      @Override
      public long decrementAndGet() {
         return addAndGet(-1L);
      }

      @Override
      public long addAndGet(long delta) {
         return apply(LongJob.ADD_AND_GET, delta, 0L);
      }

      @Override
      public int intValue() {
         return (int) get();
      }

      @Override
      public long longValue() {
         return get();
      }

      @Override
      public float floatValue() {
         return (float) get();
      }

      @Override
      public double doubleValue() {
         return (double) get();
      }

      @Override
      public String toString() {
         return Long.toString(get());
      }

      private long apply(int op, long a, long b) {
         return call(ignite, new LongJob(name, op, a, b));
      }

      private final Ignite ignite;
      private final String name;
   }

   /**
    * Runs an operation on a server's Ignite atomic long.
    */
   private static final class LongJob implements IgniteCallable<Long> {
      LongJob(String name, int op, long a, long b) {
         this.name = name;
         this.op = op;
         this.a = a;
         this.b = b;
      }

      @Override
      public Long call() {
         IgniteAtomicLong value = ignite.atomicLong(name, 0, true);

         return switch(op) {
            case GET -> value.get();
            case GET_AND_SET -> value.getAndSet(a);
            case COMPARE_AND_SET -> value.compareAndSet(a, b) ? 1L : 0L;
            case GET_AND_ADD -> value.getAndAdd(a);
            case ADD_AND_GET -> value.addAndGet(a);
            default -> throw new IllegalArgumentException("Unknown operation " + op);
         };
      }

      static final int GET = 0;
      static final int GET_AND_SET = 1;
      static final int COMPARE_AND_SET = 2;
      static final int GET_AND_ADD = 3;
      static final int ADD_AND_GET = 4;

      @IgniteInstanceResource
      private transient Ignite ignite;
      private final String name;
      private final int op;
      private final long a;
      private final long b;
   }

   /**
    * A cluster reference of a client node.
    */
   private static final class RemoteReference<V> implements DistributedReference<V> {
      RemoteReference(Ignite ignite, String name) {
         this.ignite = ignite;
         this.name = name;
      }

      @Override
      public V get() {
         return apply(ReferenceJob.GET, null, null);
      }

      @Override
      public void set(V newValue) {
         apply(ReferenceJob.SET, newValue, null);
      }

      @Override
      public boolean compareAndSet(V expect, V update) {
         return Boolean.TRUE.equals(apply(ReferenceJob.COMPARE_AND_SET, expect, update));
      }

      @Override
      public V getAndSet(V newValue) {
         return apply(ReferenceJob.GET_AND_SET, newValue, null);
      }

      @SuppressWarnings("unchecked")
      private <T> T apply(int op, Object a, Object b) {
         return (T) call(ignite, new ReferenceJob(name, op, a, b));
      }

      private final Ignite ignite;
      private final String name;
   }

   /**
    * Runs an operation on a server's Ignite atomic reference, as
    * {@code IgniteDistributedReference} does on a server.
    */
   private static final class ReferenceJob implements IgniteCallable<Object> {
      ReferenceJob(String name, int op, Object a, Object b) {
         this.name = name;
         this.op = op;
         this.a = a;
         this.b = b;
      }

      @Override
      public Object call() {
         IgniteAtomicReference<Object> value = ignite.atomicReference(name, null, true);

         switch(op) {
         case GET:
            return value.get();
         case SET:
            value.set(a);
            return null;
         case COMPARE_AND_SET:
            return value.compareAndSet(a, b);
         case GET_AND_SET:
            Object oldValue = value.get();
            value.set(a);
            return oldValue;
         default:
            throw new IllegalArgumentException("Unknown operation " + op);
         }
      }

      static final int GET = 0;
      static final int SET = 1;
      static final int COMPARE_AND_SET = 2;
      static final int GET_AND_SET = 3;

      @IgniteInstanceResource
      private transient Ignite ignite;
      private final String name;
      private final int op;
      private final Object a;
      private final Object b;
   }

   /**
    * Removes a data structure on a server.
    */
   private static final class DestroyJob implements IgniteCallable<Boolean> {
      DestroyJob(String name, int type) {
         this.name = name;
         this.type = type;
      }

      @Override
      public Boolean call() {
         switch(type) {
         case LOCK:
            IgniteLock lock = ignite.reentrantLock(name, true, false, false);

            if(lock != null) {
               lock.close();
            }

            return lock != null;
         case LONG:
            IgniteAtomicLong value = ignite.atomicLong(name, 0, false);

            if(value != null) {
               value.close();
            }

            return value != null;
         default:
            IgniteAtomicReference<?> reference = ignite.atomicReference(name, null, false);

            if(reference != null) {
               reference.close();
            }

            return reference != null;
         }
      }

      static final int LOCK = 0;
      static final int LONG = 1;
      static final int REFERENCE = 2;

      @IgniteInstanceResource
      private transient Ignite ignite;
      private final String name;
      private final int type;
   }

   // the longest a server thread waits for a lock on behalf of a client in one step
   private static final long LOCK_STEP_MILLIS = 3000L;
   // the server executor of the lock steps that wait, a thread per waiting client thread
   static final String WAIT_EXECUTOR = "inetsoft.cluster.clientLockWait";
   static final int WAIT_EXECUTOR_SIZE = 64;
   // the server executor of the releases, immediate takes and atomic operations, which don't wait
   static final String RELEASE_EXECUTOR = "inetsoft.cluster.clientLockRelease";
   private static final int RELEASE_EXECUTOR_SIZE = 8;
   // how long an idle holder thread that holds nothing is kept for its client thread
   private static final long IDLE_MILLIS = 60_000L;
   private static final Logger LOG = LoggerFactory.getLogger(ClientDataStructures.class);
}
