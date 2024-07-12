/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model;

import inetsoft.sree.RepositoryEntry;

/**
 * Base class for factories that create a type of {@link RepositoryEntryModel}.
 *
 * @param <A> the type of repository entry.
 * @param <M> the type of model.
 */
public abstract class RepositoryEntryModelFactory<A extends RepositoryEntry, M extends RepositoryEntryModel<A>> {
   /**
    * Creates a new instance of {@code RepositoryEntryModelFactory}.
    *
    * @param entryClass the type of repository entry supported by the factory.
    */
   protected RepositoryEntryModelFactory(Class<A> entryClass) {
      this.entryClass = entryClass;
   }

   /**
    * Gets the type of repository entry supported by this factory.
    *
    * @return the repository entry class.
    */
   public final Class<A> getEntryClass() {
      return entryClass;
   }

   /**
    * Creates a new repository entry model.
    *
    * @param entry the repository entry.
    * @return the new repository entry model.
    */
   public abstract M createModel(A entry);

   private final Class<A> entryClass;
}
