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
package inetsoft.web.portal.model;

import inetsoft.sree.RepositoryEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that provides access to the repository entry model factory instances.
 */
@Service
public class RepositoryEntryModelFactoryService {
   @Autowired
   public RepositoryEntryModelFactoryService(
      List<RepositoryEntryModelFactory<?, ?>> factories)
   {
      this.factories = new HashMap<>();
      factories.forEach(this::registerFactory);
   }

   /**
    * Gets the factory for the specified repository entry type.
    *
    * @param entryClass the repository entry type class.
    * @param <A>        the repository entry type.
    * @param <M>        the repository entry model type.
    * @return the matching factory or {@code null} if not found.
    */
   @SuppressWarnings("unchecked")
   public <A extends RepositoryEntry, M extends RepositoryEntryModel<A>> RepositoryEntryModelFactory<A, M>
   getFactory(Class<A> entryClass)
   {
      return (RepositoryEntryModelFactory<A, M>) factories.get(entryClass);
   }

   /**
    * Creates the object model for the specified repository entry.
    *
    * @param entry the repository entry.
    * @param <A>   the repository entry type.
    * @param <M>   the repository entry model type.
    * @return the new repository entry model.
    */
   @SuppressWarnings("unchecked")
   public <A extends RepositoryEntry, M extends RepositoryEntryModel<A>> M createModel(
      A entry)
   {
      Objects.requireNonNull(entry, "The entry must not be null");
      RepositoryEntryModelFactory factory = getFactory(entry.getClass());
      Objects.requireNonNull(
         factory, () -> "No factory found for repository entry type: " + entry.getClass()
            .getName());
      return (M) factory.createModel(entry);
   }

   private void registerFactory(RepositoryEntryModelFactory<?, ?> factory) {
      factories.put(factory.getEntryClass(), factory);
   }

   private final Map<Class<?>, RepositoryEntryModelFactory<?, ?>> factories;
}
