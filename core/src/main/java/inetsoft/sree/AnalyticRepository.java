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
package inetsoft.sree;

import inetsoft.uql.erm.XLogicalModel;
import inetsoft.util.ConfigurationContext;

import java.rmi.RemoteException;
import java.security.Principal;

/**
 * This defines the server interface for the analytic component of the report
 * server. This API is likely to change in the future.
 *
 * @author InetSoft Technology Corp.
 * @version 7.0
 */
public interface AnalyticRepository extends RepletRepository {
   /**
    * Gets the shared instance of the analytic repository.
    *
    * @return the analytic repository.
    */
   static AnalyticRepository getInstance() {
      return ConfigurationContext.getContext().getSpringBean(AnalyticRepository.class);
   }


   /**
    * Get a data model with the specified name. The actual data model returned
    * may be a virtual private model that provides a view to the data model for
    * the assigned roles.
    * @param name the name of the data model.
    * @param principal the roles assigned to the user that is requesting the
    * data model.
    * @return the requested data model.
    * @throws RemoteException if an error occurs.
    */
   XLogicalModel getLogicalModel(String name, Principal principal)
      throws RemoteException;

   /**
    * Returns {@code true} if the repository is a wrapper for the given interface.
    *
    * @param iface the interface to check.
    * @return {@code true} if the repository is a wrapper for {@code iface}.
    */
   default boolean isWrapperFor(Class<?> iface) {
      return false;
   }

   /**
    * Unwraps the repository to the given interface.
    *
    * @param iface the interface to unwrap to.
    * @param <T>   the type of the interface.
    * @return the unwrapped instance.
    * @throws IllegalArgumentException if this repository is not a wrapper for {@code iface}.
    */
   default <T> T unwrap(Class<T> iface) {
      throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
   }

}
