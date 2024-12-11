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
package inetsoft.web.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.viewsheet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class AuthorizationProviderService {
   public AuthorizationProviderService(SecurityEngine securityEngine, ObjectMapper objectMapper) {
      this.securityEngine = securityEngine;
      this.objectMapper = objectMapper;
   }

   public SecurityProviderStatusList getProviderListModel() {
      Optional<AuthorizationChain> chain = getAuthorizationChain();
      SecurityProviderStatusList.Builder builder = SecurityProviderStatusList.builder();

      if(!chain.isPresent()) {
         LOG.warn("The authorization chain has not been initialized.");
      }
      else {
         chain.get().stream()
            .map(p -> SecurityProviderStatus.builder().from(p).build())
            .forEach(builder::addProviders);
      }

      return builder.build();
   }

   public AuthorizationProviderModel getAuthorizationProvider(String name) {
      AuthorizationProvider selectedProvider = getProviderByName(name);

      if(selectedProvider == null) {
         throw new MessageException(Catalog.getCatalog().getString("em.security.providerNotFound"));
      }

      boolean isFile = selectedProvider instanceof FileAuthorizationProvider;
      SecurityProviderType type = isFile ? SecurityProviderType.FILE : SecurityProviderType.CUSTOM;

      AuthorizationProviderModel.Builder builder =
         AuthorizationProviderModel.builder()
            .providerName(name)
            .providerType(type);

      if(!isFile) {
         builder.customProviderModel(selectedProvider, objectMapper);
      }

      return builder.build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_CREATE,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void addAuthorizationProvider(AuthorizationProviderModel model,
                                        @AuditObjectName String name,
                                        @AuditUser Principal principal) throws Exception
   {
      AuthorizationProvider newProvider = getProviderFromModel(model)
         .orElseThrow(() -> new MessageException("Failed to create new authorization provider"));

      if(getProviderByName(model.providerName()) != null) {
         throw new MessageException(Catalog.getCatalog().getString("security.authorization.exists"));
      }

      AuthorizationChain chain = getAuthorizationChain()
         .orElseGet(() -> {
            securityEngine.newChain();
            return getAuthorizationChain().orElseThrow(() -> new MessageException("Could not initialize security."));
         });

      List<AuthorizationProvider> providerList = chain.getProviders();
      providerList.add(newProvider);
      chain.setProviders(providerList);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void editAuthorizationProvider(@AuditObjectName String providerName,
                                         AuthorizationProviderModel model,
                                         @AuditUser Principal principal)
      throws Exception
   {
      if(!model.providerName().equals(providerName) &&
         getProviderByName(model.providerName()) != null)
      {
         throw new MessageException(Catalog.getCatalog().getString("security.authorization.exists"));
      }

      AuthorizationChain chain = getAuthorizationChain()
         .orElseThrow(() -> new IllegalStateException("The authorization chain has not been initialized."));

      List<AuthorizationProvider> providerList = chain.getProviders();
      boolean found = false;

      for(int i = 0; i < providerList.size(); i++) {
         AuthorizationProvider provider = providerList.get(i);

         if(Objects.equals(providerName, provider.getProviderName())) {
            found = true;
            provider = getProviderFromModel(model).orElseThrow(
               () -> new MessageException("Failed to edit authorization provider"));
            providerList.set(i, provider);
            break;
         }
      }

      if(!found) {
         throw new MessageException(
            "Could not find authorization provider named \"" + providerName + "\"");
      }

      chain.setProviders(providerList);
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_DELETE,
      objectType = ActionRecord.OBJECT_TYPE_SECURITY_PROVIDER
   )
   public void removeAuthorizationProvider(int index, @AuditObjectName String providerName,
                                           @AuditUser Principal principal) throws Exception
   {
      AuthorizationChain chain = getAuthorizationChain()
         .orElseThrow(() -> new Exception("The authorization chain has not been initialized."));

      List<AuthorizationProvider> providerList = chain.getProviders();

      //If index == -1, throw an exception here.
      if(index >= 0 && index < providerList.size()) {
         AuthorizationProvider removedProvider = providerList.remove(index);
         removedProvider.tearDown();
         chain.setProviders(providerList);
      }
      else {
         throw new IndexOutOfBoundsException("Provider index out of bounds");
      }
   }

   public void reorderAuthorizationProviders(ProviderListReorderModel reorderModel) throws Exception {
      AuthorizationChain providerChain = getAuthorizationChain()
         .orElseThrow(() -> new Exception("The authorization chain has not been initialized."));

      int source = reorderModel.source();
      int destination = reorderModel.destination();
      List<AuthorizationProvider> providers = providerChain.getProviders();

      //Check if the destination index is in bounds
      if(source >= 0 && source < providers.size() &&
         destination >= 0 && destination < providers.size()) {

         //Rotate is used to preserve the order of everything but the source
         if(source > destination) { //Moving "up" the list
            Collections.rotate(providers.subList(destination, source + 1), +1);
            providerChain.setProviders(providers);
         }
         else { //Moving "down" the list
            Collections.rotate(providers.subList(source, destination + 1), -1);
            providerChain.setProviders(providers);
         }
      }
      else {
         throw new IndexOutOfBoundsException("Source and/or Destination index out of bounds");
      }
   }

   public SecurityProviderStatus clearAuthorizationProviderCache(int index) throws Exception {
      AuthorizationProvider provider = getAuthorizationChain()
         .orElseThrow(() -> new Exception("The authorization chain has not been initialized."))
         .getProviders().get(index);
      provider.clearCache();
      return SecurityProviderStatus.builder()
         .from(provider)
         .build();
   }

   public AuthorizationProvider getProviderByName(String name) {
      if(!getAuthorizationChain().isPresent()) {
         LOG.error("em.common.security.noProvider");
         return null;
      }

      return getAuthorizationChain().get().stream()
         .filter((p) -> p.getProviderName().equals(name))
         .findAny()
         .orElse(null);
   }

   private Optional<AuthorizationProvider> getProviderFromModel(AuthorizationProviderModel model)
      throws Exception
   {
      AuthorizationProvider provider;

      if(model.providerType() == SecurityProviderType.CUSTOM) {
         provider = createCustomProvider(model.customProviderModel());
      }
      else if(model.providerType() == SecurityProviderType.FILE) {
         provider = new FileAuthorizationProvider();
      }
      else {
         return Optional.empty();
      }

      provider.setProviderName(model.providerName());
      return Optional.of(provider);
   }

   private AuthorizationProvider createCustomProvider(CustomProviderModel model)
      throws Exception
   {
      Class<?> cls;

      try {
         cls = Class.forName(model.className());
      }
      catch(ClassNotFoundException exc) {
         String msg = Catalog.getCatalog().getString(
            "em.securityProvider.invalidClassName", exc.getMessage());
         throw new MessageException(msg);
      }

      if(FileAuthorizationProvider.class.isAssignableFrom(cls)) {
         String msg = Catalog.getCatalog().getString(
            "em.securityProvider.invalidClassName",
            "Built-in providers cannot be used as custom providers");
         throw new MessageException(msg);
      }

      AuthorizationProvider provider = (AuthorizationProvider) cls.newInstance();

      if(!model.jsonConfiguration().isEmpty()) {
         try {
            JsonNode config = objectMapper.readTree(model.jsonConfiguration());
            provider.readConfiguration(config);
         }
         catch(Exception e) {
            LOG.error("Invalid authentication provider configuration", e);
            throw new MessageException(Catalog.getCatalog().getString(
               "em.securityProvider.invalidJsonConfiguration"));
         }
      }

      return provider;
   }

   public Optional<AuthorizationChain> getAuthorizationChain() {
      return securityEngine.getAuthorizationChain();
   }

   private final SecurityEngine securityEngine;
   private final ObjectMapper objectMapper;
   private final Logger LOG = LoggerFactory.getLogger(AuthorizationProviderService.class);
}
