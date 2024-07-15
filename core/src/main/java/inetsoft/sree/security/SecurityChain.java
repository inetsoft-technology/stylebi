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
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
   }

   // Initialize() must be be called by the subclass' constructor to ensure all instance
   // variables are initialized when this is called
   void initialize() {
      DataSpace dataSpace = DataSpace.getDataSpace();

      try {
         if(dataSpace.exists(null, getConfigFile())) {
            loadConfiguration();
         }
         else {
            // create empty configuration file to watch
            saveConfiguration();
         }

         dataSpace.addChangeListener(null, getConfigFile(), this);
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
      return new ArrayList<>(getProviderList());
   }

   /**
    * Sets the list of delegate security providers. If any provider instances were removed from the
    * list, it is the caller's responsibility to dispose of them correctly.
    *
    * @param list the security providers.
    */
   public void setProviders(List<T> list) {
      setProviderList(list);

      try {
         saveConfiguration();
      }
      catch(IOException e) {
         LOG.error("Failed to save security chain configuration", e);
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
    * Loads the configuration from file.
    *
    * @throws IOException if an I/O error occurs.
    */
   void loadConfiguration() throws IOException {
      DataSpace dataSpace = DataSpace.getDataSpace();
      long now = dataSpace.exists(null, getConfigFile()) ?
         dataSpace.getLastModified(null, getConfigFile()) : 1;

      if(timestamp < now) {
         lock.lock();

         try {
            if(timestamp < now) {
               if(dataSpace.exists(null, getConfigFile())) {
                  ObjectMapper mapper = new ObjectMapper();
                  ObjectNode root;

                  try(InputStream input = dataSpace.getInputStream(null, getConfigFile())) {
                     root = (ObjectNode) mapper.readTree(input);
                  }

                  timestamp = dataSpace.getLastModified(null, getConfigFile());

                  ArrayNode providersArray = (ArrayNode) root.get("providers");
                  List<T> list = new ArrayList<>();

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
                        LOG.error(
                           "Failed to create instance of security provider: {}", providerClass, e);
                     }
                  }

                  clear();
                  setProviderList(list);
               }
            }
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * Saves the configuration to file.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void saveConfiguration() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
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

      lock.lock();

      try {
         DataSpace dataSpace = DataSpace.getDataSpace();
         dataSpace.withOutputStream(null, getConfigFile(), out -> mapper.writeValue(out, root));
         timestamp = dataSpace.getLastModified(null, getConfigFile());
      }
      finally {
         lock.unlock();
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

      for(T provider : list) {
         dispose(provider);
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

   private volatile List<T> providers = new ArrayList<>();
   private volatile long timestamp = 0L;
   private final Lock lock = new ReentrantLock();

   private static final Logger LOG = LoggerFactory.getLogger(SecurityChain.class);
}
