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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;
import java.util.stream.Stream;

/**
 * {@code SecurityChain} is a base class for classes that wrap a list of security providers.
 *
 * @param <T> the provider type.
 */
public abstract class SecurityChain<T extends JsonConfigurableProvider & CachableProvider>
   implements Iterable<T>, DataChangeListener
{
   /**
    * Creates a new instance of <tt>SecurityChain</tt>.
    */
   public SecurityChain() {
      this.readOnly = false;
   }

   /**
    * Creates a new instance of <tt>SecurityChain</tt>.
    *
    * @param readOnly if {@code true}, the chain will not write an empty configuration file during
    *                 initialization when the config file does not yet exist. This is used on remote
    *                 cluster nodes to avoid overwriting a valid configuration that has not yet
    *                 propagated through distributed storage.
    */
   protected SecurityChain(boolean readOnly) {
      this.readOnly = readOnly;
   }

   /**
    * add write lock for the providers.
    */
   public void writeLock() {
      readWriteLock.writeLock().lock();
   }

   /**
    * remove write lock for the providers.
    */
   public void writeUnlock() {
      readWriteLock.writeLock().unlock();
   }

   // Initialize() must be be called by the subclass' constructor to ensure all instance
   // variables are initialized when this is called
   void initialize() {
      DataSpace dataSpace = DataSpace.getDataSpace();
      String configFile = getConfigFile();

      try {
         if(dataSpace.exists(null, configFile)) {
            loadConfiguration();
         }
         else if(!readOnly) {
            // create empty configuration file to watch
            saveConfiguration();
         }

         dataSpace.addChangeListener(null, configFile, this);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to initialize security chain", e);
      }
   }

   /**
    * Gets the list of delegate security providers. This is a copy of the internal list of
    * providers, i.e. modifications to the returned list do not affect this chain. The providers
    * are the same instance referenced by this chain, and changes to them will be
    *
    * @return the security providers.
    */
   public List<T> getProviders() {
      readWriteLock.readLock().lock();

      try {
         return new ArrayList<>(getProviderList());
      }
      finally {
         readWriteLock.readLock().unlock();
      }
   }

   /**
    * Sets the list of delegate security providers. If any provider instances were removed from the
    * list, it is the caller's responsibility to dispose of them correctly.
    *
    * @param list the security providers.
    */
   public void setProviders(List<T> list) {
      // Hold the reload lock across setting the list and saving it: otherwise a reload of a change
      // saved by another node can replace the list between the two (or between the save's read
      // and write), leaving the file and the runtime chain with different providers (Bug #79127).
      // The lock is reentrant and is the same one the reload and saveConfiguration() take, after
      // any chain write lock and SecurityEngine's init lock.
      lock.lock();

      try {
         setProviderList(list);

         try {
            saveConfiguration();
         }
         catch(IOException e) {
            LOG.error("Failed to save security chain configuration", e);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * {@inheritDoc}
    *
    * <i>Note:</i> calling {@link Iterator#remove()} on the returned value will not remove the
    * provider from this chain. This method is basically shorthand for
    * {@code getProviders().iterator}.
    */
   @Override
   public Iterator<T> iterator() {
      return getProviders().iterator();
   }

   /**
    * Gets a stream of the security providers. This method is basically shorthand for
    * {@code getProviders().stream()}.
    *
    * @return a stream of security providers.
    */
   public Stream<T> stream() {
      return getProviders().stream();
   }

   /**
    * Runs an edit of the providers that reads the list, changes it and saves it, so that it cannot
    * overwrite a change saved by another node (Bug #79127). The edit runs under a cluster lock
    * (held by one node at a time), then this chain's write lock, and the configuration file is
    * reloaded after the locks are taken and before the edit runs, so the edit starts from what the
    * file holds and not from what this node last happened to process. The cluster lock is the
    * outermost lock: it is taken before {@link #writeLock()} and the reload lock, and nothing that
    * holds those waits for it.
    * <p>
    * The edit must read the providers ({@link #getProviders()}) inside this call, not before it,
    * and validate against that list.
    *
    * @param edit the edit.
    * @param <E>  the type of exception the edit throws.
    *
    * @throws E if the edit fails.
    */
   public <E extends Exception> void editProviders(ChainEdit<E> edit) throws E {
      Lock clusterLock = Cluster.getInstance().getLock(getClusterLockName());
      clusterLock.lock();

      try {
         writeLock();

         try {
            try {
               loadConfiguration();
            }
            catch(IOException e) {
               throw new UncheckedIOException("Failed to load security chain configuration", e);
            }

            edit.run();
         }
         finally {
            writeUnlock();
         }
      }
      finally {
         clusterLock.unlock();
      }
   }

   /**
    * Gets the name of the cluster lock of the edits of this chain's configuration file.
    */
   private String getClusterLockName() {
      return "inetsoft.security.chain.editLock." + getConfigFile();
   }

   /**
    * Loads the configuration from file when it differs from the content this node last loaded or
    * saved. Whether the file changed is decided by the digest of its content, never by its
    * modification time: the time is stamped by the writing node before its write lands, so it is
    * neither unique nor ordered across nodes, and a later write with an equal or earlier time was
    * skipped for good (Bug #79127).
    *
    * @throws IOException if an I/O error occurs.
    */
   void loadConfiguration() throws IOException {
      DataSpace dataSpace = DataSpace.getDataSpace();
      String configFile = getConfigFile();
      boolean exists = dataSpace.exists(null, configFile);
      Map<String, String> reloadedFrom = null;
      List<T> reloaded = null;

      // the digest is read BEFORE the content, so a write that lands in between is seen as a
      // change by the next event and never recorded as loaded
      if(exists && !isLoaded(readDigest(dataSpace, configFile))) {
         lock.lock();

         try {
            // a reload by another thread may have loaded it while this one waited for the lock
            if(dataSpace.exists(null, configFile) &&
               !isLoaded(readDigest(dataSpace, configFile)))
            {
               ObjectMapper mapper = new ObjectMapper();
               ObjectNode root = null;
               String contentDigest = null;

               // exists() and getInputStream() are not atomic: the config file can be
               // removed or replaced (e.g. by a concurrent rewrite) between the check above
               // and opening the stream here. When that happens, getInputStream() returns
               // null rather than throwing (see DataSpace.getInputStream()'s
               // FileNotFoundException/NoSuchFileException handling) -- Jackson's readTree()
               // rejects a null stream outright, so guard against it explicitly instead of
               // assuming the two calls observe a consistent file state. See bug #76431.
               try(InputStream input = dataSpace.getInputStream(null, configFile)) {
                  if(input != null) {
                     byte[] bytes = input.readAllBytes();
                     // the digest of exactly the bytes loaded, whatever lands later
                     contentDigest = toDigestString(bytes);
                     root = (ObjectNode) mapper.readTree(bytes);
                  }
               }

               if(root == null) {
                  // Same treatment as the "all providers failed to load" case below: retain
                  // the existing runtime providers and don't record the digest, so the next
                  // dataChanged() retries once the transient condition (the file coming back,
                  // or the concurrent rewrite finishing) clears.
                  LOG.warn(
                     "Security provider configuration file '{}' could not be read " +
                     "(reported present but its content was unavailable, likely a " +
                     "concurrent rewrite); retaining existing providers", configFile);
               }
               else {
                  JsonNode providersNode = root.get("providers");

                  if(!(providersNode instanceof ArrayNode)) {
                     // Same treatment as the null-stream case above: a malformed config
                     // (missing or non-array "providers" field) must not crash
                     // initialize() -- retain the existing providers and don't record
                     // the digest, so the next dataChanged() retries once the config is
                     // fixed. See bug #76431.
                     LOG.warn(
                        "Security provider configuration file '{}' is missing a valid " +
                        "\"providers\" array; retaining existing providers", configFile);
                  }
                  else {
                     ArrayNode providersArray = (ArrayNode) providersNode;
                     List<T> list = new ArrayList<>();
                     int failureCount = 0;

                     for(int i = 0; i < providersArray.size(); i++) {
                        ObjectNode wrapperNode = (ObjectNode) providersArray.get(i);
                        String name = wrapperNode.get("name").asText();
                        String providerClass = wrapperNode.get("providerClass").asText();
                        JsonNode config = wrapperNode.get("configuration");

                        try {
                           @SuppressWarnings("unchecked")
                           T provider =
                              (T) Class.forName(providerClass).getConstructor().newInstance();
                           provider.readConfiguration(config);
                           provider.setProviderName(name);
                           list.add(provider);
                        }
                        catch(Exception e) {
                           failureCount++;
                           LOG.error(
                              "Failed to create instance of security provider: {}", providerClass, e);
                        }
                     }

                     // If the config file specified providers but ALL of them failed to instantiate,
                     // retain the existing runtime providers rather than wiping them. This prevents a
                     // corrupted or temporarily unreadable config (e.g. after a GKE node restart) from
                     // clearing all security providers and locking out every user.
                     // Do NOT record the digest on total failure – allow the next dataChanged() to retry
                     // when the transient condition clears, without requiring a manual config edit.
                     if(list.isEmpty() && failureCount > 0) {
                        LOG.error(
                           "All {} security provider(s) failed to load from '{}'; retaining " +
                           "existing providers to prevent loss of security configuration",
                           failureCount, configFile);
                     }
                     else {
                        if(failureCount > 0) {
                           LOG.warn(
                              "{} of {} security provider(s) failed to load from '{}'; " +
                              "the remaining providers have been applied but security " +
                              "configuration may be incomplete",
                              failureCount, list.size() + failureCount, configFile);
                        }

                        // the first load on this node replaces nothing, so it is not a reload
                        boolean initial = !loaded;
                        // what is recorded is the digest of the bytes loaded: a second edit that
                        // lands while the providers are being created has another digest, so
                        // the next dataChanged() reloads it (Bug #79267)
                        markLoaded(contentDigest);
                        List<T> old = getProviderList();
                        Map<String, String> before =
                           initial ? null : describeProviders(old);
                        // publish the new list in one step, so a reader never sees an empty
                        // chain while the old providers are torn down (Bug #79355)
                        setProviderList(list);
                        disposeAll(old);

                        if(!initial) {
                           reloadedFrom = before;
                           reloaded = list;
                        }
                     }
                  }
               }
            }
         }
         finally {
            lock.unlock();
         }

         if(reloaded != null) {
            try {
               providersReloaded(reloadedFrom, reloaded);
            }
            catch(Exception e) {
               LOG.warn("Failed to handle the reloaded security providers", e);
            }
         }
      }
   }

   /**
    * Gets the providers, waiting for an edit holding {@link #writeLock()} and for a reload of the
    * configuration file that is replacing them to finish. During a reload the list is the old
    * one until the new list is set, and the reload holds its lock until the old providers are
    * disposed. The read lock is taken before the reload lock, the same order as an edit that
    * saves the chain under the write lock. The caller must not hold a provider's locks.
    * <p>
    * A reload that takes long is logged, but this keeps waiting for it: the list may be the
    * replaced one until it ends, so not waiting would lose the caller's work. Waiting can't
    * deadlock, because no thread that holds the reload lock waits for the read lock.
    *
    * @param waiter what is waiting for the reload, for the log.
    *
    * @return the security providers.
    */
   List<T> getProvidersAfterReload(String waiter) {
      readWriteLock.readLock().lock();

      try {
         boolean locked = false;

         try {
            locked = lock.tryLock(RELOAD_WAIT_SECONDS, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }

         if(!locked) {
            LOG.warn("A reload of {} has not finished within {} s, {} keeps waiting for it",
                     getConfigFile(), RELOAD_WAIT_SECONDS, waiter);
            lock.lock();
         }

         try {
            return new ArrayList<>(getProviderList());
         }
         finally {
            lock.unlock();
         }
      }
      finally {
         readWriteLock.readLock().unlock();
      }
   }

   /**
    * Gets a copy of the providers without waiting for an edit that holds {@link #writeLock()}.
    * The edit may be replacing the returned providers. This is for code that may hold a
    * provider's own locks, such as the Database cache service: an edit takes those locks under
    * the write lock when it tears the replaced provider down.
    *
    * @return the security providers.
    */
   public List<T> peekProviders() {
      return new ArrayList<>(getProviderList());
   }

   /**
    * Describes the providers that a reload of the configuration file is about to replace, for
    * {@link #providersReloaded(Map, List)}.
    *
    * @param list the current providers.
    *
    * @return the names of the providers mapped to a description of their configuration.
    */
   Map<String, String> describeProviders(List<T> list) {
      return Collections.emptyMap();
   }

   /**
    * Called after this node replaced its providers with the ones in a configuration file that
    * another node changed, outside the configuration lock. It is not called on the initial load
    * or for a change saved by this chain.
    *
    * @param before the description of the replaced providers.
    * @param list   the new providers.
    */
   void providersReloaded(Map<String, String> before, List<T> list) {
   }

   /**
    * Saves the configuration to file.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void saveConfiguration() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      String configFile = getConfigFile();

      // The provider list is read under the lock that a reload holds while it replaces the list,
      // so a save never serializes the list of a reload that is still replacing it, and
      // never writes a list that a completed reload has since replaced (Bug #79127).
      lock.lock();

      try {
         ObjectNode root = mapper.createObjectNode();
         ArrayNode providerArray = mapper.createArrayNode();
         root.set("providers", providerArray);

         for(T provider : getProviderList()) {
            ObjectNode wrapperNode = mapper.createObjectNode();
            wrapperNode.put("name", provider.getProviderName());
            wrapperNode.put("providerClass", provider.getClass().getName());
            wrapperNode.set("configuration", provider.writeConfiguration(mapper));
            providerArray.add(wrapperNode);
         }

         // serialize once, so the digest recorded is that of exactly the bytes written
         byte[] bytes = mapper.writeValueAsBytes(root);
         String own = toDigestString(bytes);
         DataSpace dataSpace = DataSpace.getDataSpace();
         dataSpace.withOutputStream(null, configFile, out -> out.write(bytes));

         // What is recorded is the content the runtime chain holds, which is what was just
         // serialized, whatever the store holds now. If the store holds the same content (the
         // normal case) this node's own event finds nothing to reload. If another node's write
         // replaced it, or replaces it later, its digest differs and the event reloads it, even
         // when that content is what this node held before (no ABA skip).
         markLoaded(own);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Reads the digest of the configuration file, the lower-case hex MD5 of its content. Called
    * by {@link #loadConfiguration()} before the content is read.
    *
    * @return the digest, or {@code null} if it is unavailable, in which case the file is always
    *         reloaded.
    */
   String readDigest(DataSpace dataSpace, String configFile) {
      return dataSpace.getDigest(null, configFile);
   }

   /**
    * Checks if the content with a digest is what this node last loaded or saved. An unavailable
    * digest never matches, so it is reloaded rather than skipped.
    */
   private boolean isLoaded(String digest) {
      return loaded && digest != null && digest.equals(loadedDigest);
   }

   /**
    * Records the content this node holds. Called under the reload lock.
    */
   private void markLoaded(String digest) {
      loadedDigest = digest;
      loaded = true;
   }

   /** Lower-case hex MD5, the encoding of the digest the blob storage keeps. */
   private static String toDigestString(byte[] bytes) {
      try {
         byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
         StringBuilder buf = new StringBuilder();

         for(byte b : digest) {
            buf.append(String.format("%02x", ((int) b) & 0xff));
         }

         return buf.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   @Override
   public void dataChanged(DataChangeEvent event) {
      try {
         loadConfiguration();
      }
      catch(IOException e) {
         LOG.error("Failed to load security chain configuration", e);
      }
   }

   /**
    * Gets the list of the security providers. This does not copy the underlying list first.
    *
    * @return the security providers.
    */
   List<T> getProviderList() {
      return providers;
   }

   /**
    * Sets the list of security providers.
    *
    * @param list the security providers.
    */
   void setProviderList(List<T> list) {
      this.providers = list;
   }

   /**
    * Clears the list of security providers.
    */
   void clear() {
      List<T> list = providers;
      setProviderList(new ArrayList<>());
      disposeAll(list);
   }

   /**
    * Disposes the security providers that a new list has replaced. A provider that fails to
    * dispose is logged and does not keep the ones after it from being disposed.
    *
    * @param list the replaced security providers.
    */
   void disposeAll(List<T> list) {
      for(T provider : list) {
         try {
            dispose(provider);
         }
         catch(Exception e) {
            LOG.warn("Failed to dispose security provider: {}", provider.getProviderName(), e);
         }
      }
   }

   /**
    * Gets the name of the configuration file for this chain.
    *
    * @return the configuration file.
    */
   abstract String getConfigFile();

   /**
    * Handles disposing of a security provider.
    *
    * @param provider the security provider.
    */
   abstract void dispose(T provider);

   /**
    * Disposes all security providers and removes the listener on the configuration file.
    */
   void dispose() {
      DataSpace dataSpace = DataSpace.getDataSpace();
      dataSpace.removeChangeListener(null, getConfigFile(), this);
      clear();
   }

   private final boolean readOnly;
   private volatile List<T> providers = new ArrayList<>();
   // the digest of the content this node last loaded or saved, and whether it has loaded any
   private volatile String loadedDigest = null;
   private volatile boolean loaded = false;
   private final Lock lock = new ReentrantLock();
   private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
   private static final long RELOAD_WAIT_SECONDS = 10L;

   /**
    * An edit of the providers, run by {@link #editProviders(ChainEdit)}.
    *
    * @param <E> the type of exception the edit throws.
    */
   @FunctionalInterface
   public interface ChainEdit<E extends Exception> {
      void run() throws E;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SecurityChain.class);
}
