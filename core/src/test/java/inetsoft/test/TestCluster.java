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

package inetsoft.test;

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.Tool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class TestCluster implements Cluster {
   @Override
   public String getId() {
      return clusterId;
   }

   @Override
   public String getLocalMember() {
      return Tool.getIP() + ":5701";
   }

   @Override
   public Set<String> getClusterNodes(boolean includeClients) {
      return Set.of(getLocalMember());
   }

   @Override
   public Set<String> getServerClusterNodes() {
      return Set.of(getLocalMember());
   }

   @Override
   public void debug() {
      // no-op
   }

   @Override
   public String getClusterNodeHost(String server) {
      int index = server.lastIndexOf(':');
      return index < 0 ? server : server.substring(0, index);
   }

   @Override
   public String getByName(String server) {
      if(Tool.getIP().equals(server)) {
         return server;
      }

      return null;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T getClusterNodeProperty(String server, String name) {
      return (T) clusterNodeProperties.computeIfAbsent(
         server, k -> new ConcurrentHashMap<>()).get(name);
   }

   @Override
   public <T> T getLocalNodeProperty(String name) {
      return getClusterNodeProperty(getLocalMember(), name);
   }

   @Override
   public void setLocalNodeProperty(String name, Object value) {
      clusterNodeProperties.computeIfAbsent(
         getLocalMember(), k -> new ConcurrentHashMap<>()).put(name, value);
   }

   @Override
   public boolean isMaster() {
      return true;
   }

   @Override
   public String getKeyOwner(String key) {
      return null;
   }

   @Override
   public Lock getLock(String name) {
      return locks.computeIfAbsent(name, k -> new ReentrantLock());
   }

   @Override
   public void destroyLock(String name) {
      Lock lock = locks.get(name);

      if(lock != null) {
         lock.unlock();
      }
   }

   @SuppressWarnings("ResultOfMethodCallIgnored")
   @Override
   public void lockKey(String name) {
      try {
         Lock lock = getLock(name);
         lock.tryLock(10, TimeUnit.MINUTES);
      }
      catch(InterruptedException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void unlockKey(String name) {
      Lock lock = locks.get(name);

      if(lock != null) {
         lock.unlock();
      }
   }

   @Override
   public void lockRead(String name) {
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      Integer count = rwLocks.computeIfAbsent(readKeyName, k -> 0) + 1;
      rwLocks.put(readKeyName, count);
      lock.unlock();
   }

   @Override
   public void unlockRead(String name) {
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      Integer count = rwLocks.get(readKeyName);

      if(count != null) {
         if(count <= 1) {
            rwLocks.remove(readKeyName);
         }
         else {
            rwLocks.put(readKeyName, --count);
         }
      }

      lock.unlock();
   }

   @SuppressWarnings("BusyWait")
   @Override
   public void lockWrite(String name) {
      String writeKeyName = "write." + name;
      String readKeyName = "read." + name;

      // lock on write
      Lock lock = getLock(writeKeyName);
      lock.lock();

      while(rwLocks.containsKey(readKeyName)) {
         lock.unlock();

         try {
            Thread.sleep(100);
         }
         catch(Exception ignore) {
         }

         lock.lock();
      }
   }

   @Override
   public void unlockWrite(String name) {
      Lock lock = locks.get("write." + name);

      if(lock != null) {
         lock.unlock();
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public <K, V> DistributedMap<K, V> getMap(String name) {
      return (DistributedMap<K, V>) maps.computeIfAbsent(name, k -> new LocalDistributedMap<>(name));
   }

   @SuppressWarnings("unchecked")
   @Override
   public <K, V> MultiMap<K, V> getMultiMap(String name) {
      return (MultiMap<K, V>) multiMaps.computeIfAbsent(name, k -> {
         LocalDistributedMap<K, List<V>> map =
            (LocalDistributedMap<K, List<V>>) maps.computeIfAbsent(k, LocalDistributedMap::new);
         return new LocalMultiMap<>(map);
      });
   }

   @Override
   public void destroyMap(String name) {
      maps.remove(name);
      multiMaps.remove(name);
   }

   @Override
   public void addMapListener(String name, MapChangeListener<?, ?> l) {
      mapListeners.computeIfAbsent(name, k -> new ArrayList<>()).add(l);
   }

   @Override
   public void removeMapListener(String name, MapChangeListener<?, ?> l) {
      List<MapChangeListener<?, ?>> listeners = mapListeners.get(name);

      if(listeners != null) {
         listeners.remove(l);
      }
   }

   @Override
   public <K, V> void addMultiMapListener(String name, MapChangeListener<K, Collection<V>> l) {
      addMapListener(name, l);
   }

   @Override
   public void removeMultiMapListener(String name, MapChangeListener<?, ?> l) {
      removeMapListener(name, l);
   }

   @Override
   public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
      return getMap(name);
   }

   @Override
   public void destroyReplicatedMap(String name) {
      maps.remove(name);
   }

   @Override
   public <K, V> void addReplicatedMapListener(String name, MapChangeListener<K, V> l) {
      addMapListener(name, l);
   }

   @Override
   public void removeReplicatedMapListener(String name, MapChangeListener<?, ?> l) {
      removeMapListener(name, l);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <E> BlockingQueue<E> getQueue(String name) {
      return (BlockingQueue<E>) queues.computeIfAbsent(name, k -> new LinkedBlockingQueue<E>());
   }

   @Override
   public void destroyQueue(String name) {
      queues.remove(name);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <E> Set<E> getSet(String name) {
      return (Set<E>) sets.computeIfAbsent(name, k -> new HashSet<>());
   }

   @Override
   public void destroySet(String name) {
      sets.remove(name);
   }

   @Override
   public <E> Set<E> getReplicatedSet(String name, boolean transactional) {
      return getSet(name);
   }

   @Override
   public void destroyReplicatedSet(String name) {
      destroySet(name);
   }

   @Override
   public DistributedLong getLong(String name) {
      return longs.computeIfAbsent(name, k -> new LocalDistributedLong());
   }

   @Override
   public void destroyLong(String name) {
      longs.remove(name);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <V> DistributedReference<V> getReference(String name) {
      return (DistributedReference<V>)
         references.computeIfAbsent(name, k -> new LocalDistributedReference<>());
   }

   @Override
   public void destroyReference(String name) {
      references.remove(name);
   }

   @Override
   public <T> Future<T> submit(Callable<T> task, boolean scheduler) {
      return executor.submit(task);
   }

   @Override
   public <T> Future<Collection<T>> submitAll(Callable<T> task) {
      Callable<Collection<T>> wrapper = () -> List.of(task.call());
      return executor.submit(wrapper);
   }

   @Override
   public DistributedScheduledExecutorService getScheduledExecutor() {
      return scheduledExecutor;
   }

   @Override
   public void destroyScheduledExecutor() {
      // no-op
   }

   @Override
   public String getServiceOwner(String serviceId) {
      return getLocalMember();
   }

   @Override
   public <T extends Serializable> Future<T> submit(String serviceId, SingletonCallableTask<T> task) {
      return getSingletonExecutor(serviceId).submit(task);
   }

   @Override
   public Future<?> submit(String serviceId, SingletonRunnableTask task) {
      return getSingletonExecutor(serviceId).submit(task);
   }

   @Override
   public boolean isSchedulerRunning() {
      return false;
   }

   @Override
   public void addMembershipListener(MembershipListener l) {
      // no-op
   }

   @Override
   public void removeMembershipListener(MembershipListener l) {
      // no-op
   }

   @Override
   public void addMessageListener(MessageListener l) {
      messageListeners.add(l);
   }

   @Override
   public void removeMessageListener(MessageListener l) {
      messageListeners.remove(l);
   }

   @Override
   public void sendMessage(Serializable message) {
      MessageEvent event = null;

      for(MessageListener listener : messageListeners) {
         if(event == null) {
            event = new MessageEvent(this, getLocalMember(), true, message);
         }

         listener.messageReceived(event);
      }
   }

   @Override
   public void sendMessage(String server, Serializable message) {
      if(Objects.equals(server, getLocalMember())) {
         sendMessage(message);
      }
   }

   @Override
   public <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                                      Function<MessageEvent, T> matcher)
      throws Exception
   {
      if(Objects.equals(address, getLocalMember())) {
         CountDownLatch latch = new CountDownLatch(1);
         AtomicReference<T> result = new AtomicReference<>(null);

         MessageListener listener = e -> {
            if(address.equals(e.getSender())) {
               T value = matcher.apply(e);

               if(value != null) {
                  result.set(value);
                  latch.countDown();
               }
            }
         };

         addMessageListener(listener);

         try {
            sendMessage(address, outgoingMessage);

            if(!latch.await(30, TimeUnit.SECONDS)) {
               throw new InterruptedException("Timed out waiting for response from " + address);
            }
         }
         finally {
            removeMessageListener(listener);
         }

         return result.get();
      }
      else {
         // simulate time out to non-existent node
         Thread.sleep(Duration.ofSeconds(30L));
         throw new InterruptedException("Timed out waiting for response from " + address);
      }
   }

   @Override
   public <T extends Serializable> T exchangeMessages(String address, Serializable outgoingMessage,
                                                      Class<T> responseType) throws Exception
   {
      return exchangeMessages(address, outgoingMessage, e ->
         e.getMessage() != null && responseType.isAssignableFrom(e.getMessage().getClass()) ?
            responseType.cast(e.getMessage()) : null);
   }

   @Override
   public void refreshConfig(boolean start) {
      // no-op
   }

   @Override
   public boolean isMasterScheduler() {
      return false;
   }

   @Override
   public void addClusterLifecycleListener(ClusterLifecycleListener l) {
      // no-op
   }

   @Override
   public void removeClusterLifecycleListener(ClusterLifecycleListener l) {
      // no-op
   }

   @Override
   public String addTransferFile(File file) {
      String link = getLocalMember() + "/" + UUID.randomUUID();
      transferFiles.put(link, file);
      return link;
   }

   @Override
   public File getTransferFile(String link) {
      return transferFiles.remove(link);
   }

   @Override
   public List<String> getClusterAddresses() {
      return List.of(getLocalMember());
   }

   @Override
   public void close() throws Exception {
      executor.shutdown();

      for(ExecutorService service : singletonExecutors.values()) {
         service.shutdown();
      }

      singletonExecutors.clear();
      scheduledExecutor.shutdown();
   }

   private ExecutorService getSingletonExecutor(String serviceId) {
      synchronized(singletonExecutors) {
         return singletonExecutors.computeIfAbsent(serviceId, k -> Executors.newSingleThreadScheduledExecutor());
      }
   }

   @Override
   public DistributedTransaction startTx() {
      return new TestTransaction();
   }

   private final ConcurrentMap<String, Map<String, Object>> clusterNodeProperties =
      new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Lock> locks = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Integer> rwLocks = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, LocalDistributedMap<?, ?>> maps = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, LocalMultiMap<?, ?>> multiMaps = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, List<MapChangeListener<?, ?>>> mapListeners =
      new ConcurrentHashMap<>();
   private final ConcurrentMap<String, BlockingQueue<?>> queues = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, Set<?>> sets = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, DistributedLong> longs = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, DistributedReference<?>> references =
      new ConcurrentHashMap<>();
   private final Map<String, ExecutorService> singletonExecutors = new HashMap<>();
   private final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
   private final DistributedScheduledExecutorService scheduledExecutor =
      new LocalDistributedScheduledExecutorService();
   private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
   private final ConcurrentMap<String, File> transferFiles = new ConcurrentHashMap<>();
   private final String clusterId = UUID.randomUUID().toString();

   private final class LocalDistributedMap<K, V> implements DistributedMap<K, V> {
      private LocalDistributedMap(String name) {
         this.name = name;
      }

      @Override
      public V getOrDefault(Object key, V defaultValue) {
         return delegate.getOrDefault(key, defaultValue);
      }

      @Override
      public void forEach(BiConsumer<? super K, ? super V> action) {
         delegate.forEach(action);
      }

      @Nullable
      @Override
      public V putIfAbsent(@NotNull K key, V value) {
         V previous = delegate.putIfAbsent(key, value);

         if(previous == null) {
            fireEntryAdded(() -> new EntryEvent<>(name, key, null, value));
         }

         return previous;
      }

      @SuppressWarnings("unchecked")
      @Override
      public boolean remove(@NotNull Object key, Object value) {
         boolean removed = delegate.remove(key, value);

         if(removed) {
            fireEntryRemoved(() -> new EntryEvent<>(name, (K) key, (V) value, null));
         }

         return removed;
      }

      @Override
      public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
         boolean replaced = delegate.replace(key, oldValue, newValue);

         if(replaced) {
            fireEntryUpdated(() -> new EntryEvent<>(name, key, oldValue, newValue));
         }

         return replaced;
      }

      @Nullable
      @Override
      public V replace(@NotNull K key, @NotNull V value) {
         V previous = delegate.replace(key, value);

         if(previous != null) {
            fireEntryUpdated(() -> new EntryEvent<>(name, key, previous, value));
         }

         return previous;
      }

      @Override
      public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
         List<EntryEvent<K, V>> events = new ArrayList<>();

         for(Map.Entry<K, V> entry : delegate.entrySet()) {
            K key = entry.getKey();
            V previous = entry.getValue();
            V value = function.apply(key, previous);
            entry.setValue(value);
            events.add(new EntryEvent<>(name, key, previous, value));
         }

         events.forEach(e -> fireEntryUpdated(() -> e));
      }

      @Override
      public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
         V value;
         V previous = delegate.get(key);

         if(previous == null) {
            value = mappingFunction.apply(key);
            delegate.put(key, value);
            fireEntryAdded(() -> new EntryEvent<>(name, key, null, value));
         }
         else {
            value = previous;
         }

         return value;
      }

      @Override
      public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
         V value;
         V previous = delegate.get(key);

         if(previous != null) {
            value = remappingFunction.apply(key, previous);
            delegate.put(key, value);
            fireEntryUpdated(() -> new EntryEvent<>(name, key, previous, value));
         }
         else {
            value = null;
         }

         return value;
      }

      @Override
      public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
         V oldValue = delegate.get(key);
         V newValue = remappingFunction.apply(key, oldValue);

         if(newValue != null) {
            delegate.put(key, newValue);

            if(oldValue == null) {
               fireEntryAdded(() -> new EntryEvent<>(name, key, null, newValue));
            }
            else {
               fireEntryUpdated(() -> new EntryEvent<>(name, key, oldValue, newValue));
            }
         }
         else if(oldValue != null || delegate.containsKey(key)) {
            delegate.remove(key);
            fireEntryRemoved(() -> new EntryEvent<>(name, key, oldValue, null));
         }

         return newValue;
      }

      @Override
      public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
         V oldValue = delegate.get(key);
         V newValue = oldValue == null ? value : remappingFunction.apply(oldValue, value);

         if(newValue == null) {
            delegate.remove(key);
            fireEntryRemoved(() -> new EntryEvent<>(name, key, oldValue, null));
         }
         else {
            delegate.put(key, newValue);

            if(oldValue == null) {
               fireEntryAdded(() -> new EntryEvent<>(name, key, null, newValue));
            }
            else {
               fireEntryUpdated(() -> new EntryEvent<>(name, key, oldValue, newValue));
            }
         }

         return newValue;
      }

      @Override
      public int size() {
         return delegate.size();
      }

      @Override
      public boolean isEmpty() {
         return delegate.isEmpty();
      }

      @Override
      public boolean containsKey(Object key) {
         return delegate.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
         return delegate.containsValue(value);
      }

      @Override
      public V get(Object key) {
         return delegate.get(key);
      }

      @Nullable
      @Override
      public V put(K key, V value) {
         V oldValue = delegate.put(key, value);

         if(oldValue == null) {
            // don't fire event when initing visual security providers, else will cause dead lock
            // like: sree init -> log -> init security engine(locked) -> init&save visual security -> PutBlobTask
            // -> LocalKeyValueStorage.entryAdded(new thread) -> log -> init security engine(blocked)
            if("virtual_security.xml".equals(key)) {
               return value;
            }

            fireEntryAdded(() -> new EntryEvent<>(name, key, null, value));
         }
         else {
            fireEntryUpdated(() -> new EntryEvent<>(name, key, oldValue, value));
         }

         return oldValue;
      }

      @SuppressWarnings("unchecked")
      @Override
      public V remove(Object key) {
         V oldValue = delegate.remove(key);

         if(oldValue != null) {
            fireEntryRemoved(() -> new EntryEvent<>(name, (K) key, oldValue, null));
         }

         return oldValue;
      }

      @Override
      public void removeAll(Set<? extends K> keys) {
         delegate.keySet().removeAll(keys);
      }

      @Override
      public void removeAll() {
         delegate.clear();
      }

      @Override
      public void putAll(@NotNull Map<? extends K, ? extends V> m) {
         for(Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
         }
      }

      @Override
      public void clear() {
         List<EntryEvent<K, V>> events = delegate.entrySet().stream()
               .map(e -> new EntryEvent<>(name, e.getKey(), e.getValue(), null))
               .toList();
         delegate.clear();
         events.forEach(e -> fireEntryRemoved(() -> e));
      }

      @NotNull
      @Override
      public Set<K> keySet() {
         return delegate.keySet();
      }

      @NotNull
      @Override
      public Collection<V> values() {
         return delegate.values();
      }

      @NotNull
      @Override
      public Set<Entry<K, V>> entrySet() {
         return delegate.entrySet();
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(!(o instanceof LocalDistributedMap<?, ?> that)) {
            return false;
         }

         return Objects.equals(name, that.name) && Objects.equals(delegate, that.delegate);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, delegate);
      }

      @Override
      public void lock(K key) {
         getLock(key).lock();
      }

      @SuppressWarnings("ResultOfMethodCallIgnored")
      @Override
      public void lock(K key, long leaseTime, TimeUnit timeUnit) {
         try {
            getLock(key).tryLock(leaseTime, timeUnit);
         }
         catch(InterruptedException ignore) {
         }
      }

      private boolean tryLock(K key, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
         return getLock(key).tryLock(leaseTime, timeUnit);
      }

      @Override
      public void unlock(K key) {
         getLock(key).unlock();
      }

      @Override
      public void set(K key, V value) {
         put(key, value);
      }

      private Lock getLock(K key) {
         return lockMap.computeIfAbsent(key, k -> new ReentrantLock());
      }

      @SuppressWarnings({ "rawtypes", "unchecked" })
      private void fireEntryAdded(Supplier<EntryEvent> supplier) {
         fireEntryEvent(supplier, MapChangeListener::entryAdded);
      }

      @SuppressWarnings({ "rawtypes", "unchecked" })
      private void fireEntryUpdated(Supplier<EntryEvent> supplier) {
         fireEntryEvent(supplier, MapChangeListener::entryUpdated);
      }

      @SuppressWarnings({ "rawtypes", "unchecked" })
      private void fireEntryRemoved(Supplier<EntryEvent> supplier) {
         fireEntryEvent(supplier, MapChangeListener::entryRemoved);
      }

      @SuppressWarnings("rawtypes")
      private void fireEntryEvent(Supplier<EntryEvent> supplier,
                                  BiConsumer<MapChangeListener, EntryEvent> fire)
      {
         EntryEvent<?, ?> event = null;

         for(MapChangeListener<?, ?> listener :
            mapListeners.computeIfAbsent(name, k -> new ArrayList<>()))
         {
            if(event == null) {
               event = supplier.get();
            }

            fire.accept(listener, event);
         }
      }

      private final String name;
      private final ConcurrentMap<K, V> delegate = new ConcurrentHashMap<>();
      private final ConcurrentMap<K, Lock> lockMap = new ConcurrentHashMap<>();
   }

   @SuppressWarnings("ClassCanBeRecord")
   private static final class LocalMultiMap<K, V> implements MultiMap<K, V> {
      LocalMultiMap(LocalDistributedMap<K, List<V>> delegate) {
         this.delegate = delegate;
      }

      @Override
      public void put(K key, V value) {
         List<V> list = delegate.get(key);

         if(list == null) {
            list = new ArrayList<>();
         }

         list.add(value);
         delegate.put(key, list);
      }

      @Override
      public Collection<V> get(K key) {
         return delegate.get(key);
      }

      @Override
      public void remove(K key, V value) {
         List<V> list = delegate.get(key);

         if(list != null) {
            if(list.removeIf(v -> Objects.equals(v, value))) {
               if(list.isEmpty()) {
                  delegate.remove(key);
               }
               else {
                  delegate.put(key, list);
               }
            }
         }
      }

      @Override
      public Collection<V> remove(K key) {
         return delegate.remove(key);
      }

      @Override
      public void delete(K key) {
         delegate.remove(key);
      }

      @Override
      public Set<K> keySet() {
         return delegate.keySet();
      }

      @Override
      public Collection<V> values() {
         return delegate.values().stream().flatMap(List::stream).collect(Collectors.toList());
      }

      @Override
      public Set<Map.Entry<K, V>> entrySet() {
         return delegate.entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> new Map.Entry<K, V>() {
               @Override
               public K getKey() {
                  return e.getKey();
               }

               @Override
               public V getValue() {
                  return v;
               }

               @Override
               public V setValue(Object value) {
                  throw new UnsupportedOperationException();
               }
            }))
            .collect(Collectors.toSet());
      }

      @Override
      public boolean containsKey(K key) {
         return delegate.containsKey(key);
      }

      @Override
      public boolean containsValue(V value) {
         return values().stream()
            .anyMatch(v -> Objects.equals(v, value));
      }

      @Override
      public boolean containsEntry(K key, V value) {
         List<V> list = delegate.get(key);
         return list != null && list.contains(value);
      }

      @Override
      public int size() {
         return entrySet().size();
      }

      @Override
      public void clear() {
         delegate.clear();
      }

      @Override
      public int valueCount(K key) {
         List<V> list = delegate.get(key);
         return list == null ? 0 : list.size();
      }

      @Override
      public void lock(K key) {
         delegate.lock(key);
      }

      @Override
      public void lock(K key, long leaseTime, TimeUnit timeUnit) {
         delegate.lock(key, leaseTime, timeUnit);
      }

      @Override
      public boolean tryLock(K key) {
         return false;
      }

      @Override
      public boolean tryLock(K key, long time, TimeUnit timeUnit) throws InterruptedException {
         return delegate.tryLock(key, time, timeUnit);
      }

      @Override
      public void unlock(K key) {
         delegate.unlock(key);
      }

      private final LocalDistributedMap<K, List<V>> delegate;
   }

   private static final class LocalDistributedLong extends DistributedLong {
      @Override
      public long get() {
         return delegate.get();
      }

      @Override
      public void set(long newValue) {
         delegate.set(newValue);
      }

      @Override
      public void lazySet(long newValue) {
         delegate.lazySet(newValue);
      }

      @Override
      public long getAndSet(long newValue) {
         return delegate.getAndSet(newValue);
      }

      @Override
      public boolean compareAndSet(long expect, long update) {
         return delegate.compareAndSet(expect, update);
      }

      @Override
      public long getAndIncrement() {
         return delegate.getAndIncrement();
      }

      @Override
      public long getAndDecrement() {
         return delegate.getAndDecrement();
      }

      @Override
      public long getAndAdd(long delta) {
         return delegate.getAndAdd(delta);
      }

      @Override
      public long incrementAndGet() {
         return delegate.incrementAndGet();
      }

      @Override
      public long decrementAndGet() {
         return delegate.decrementAndGet();
      }

      @Override
      public long addAndGet(long delta) {
         return delegate.addAndGet(delta);
      }

      @Override
      public int intValue() {
         return delegate.intValue();
      }

      @Override
      public long longValue() {
         return delegate.longValue();
      }

      @Override
      public float floatValue() {
         return delegate.floatValue();
      }

      @Override
      public double doubleValue() {
         return delegate.doubleValue();
      }

      private final AtomicLong delegate = new AtomicLong();
   }

   private static final class LocalDistributedReference<T> implements DistributedReference<T> {
      @Override
      public T get() {
         return delegate.get();
      }

      @Override
      public void set(T newValue) {
         delegate.set(newValue);
      }

      @Override
      public boolean compareAndSet(T expect, T update) {
         return delegate.compareAndSet(expect, update);
      }

      @Override
      public T getAndSet(T newValue) {
         return delegate.getAndSet(newValue);
      }

      private final AtomicReference<T> delegate = new AtomicReference<>();
   }

   private static final class LocalDistributedScheduledExecutorService
      implements DistributedScheduledExecutorService
   {
      @Override
      public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
         return delegate.schedule(command, delay, unit);
      }

      @Override
      public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
         return delegate.schedule(command, delay, unit);
      }

      @Override
      public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
         delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
      }

      @Override
      public void shutdown() {
         delegate.shutdown();
      }

      private final ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
   }

   private static final class TestTransaction implements DistributedTransaction {
      @Override
      public long startTime() {
         return startTime;
      }

      @Override
      public long timeout() {
         return timeout;
      }

      @Override
      public long timeout(long timeout) {
         return this.timeout = timeout;
      }

      @Override
      public boolean setRollbackOnly() {
         return rollbackOnly = true;
      }

      @Override
      public boolean isRollbackOnly() {
         return rollbackOnly;
      }

      @Override
      public void commit() {
      }

      @Override
      public void close() {
      }

      @Override
      public void rollback() {
      }

      @Override
      public void resume() {
      }

      @Override
      public void suspend() {
      }

      @Override
      public String label() {
         return null;
      }

      private final long startTime = System.currentTimeMillis();
      private long timeout = 0L;
      private boolean rollbackOnly = false;
   }
}
