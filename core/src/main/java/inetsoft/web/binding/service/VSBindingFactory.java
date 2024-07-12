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
package inetsoft.web.binding.service;

import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.binding.model.BindingModel;

/**
 * Interface for classes that handle the creation of DTO models for viewsheet
 * assemblies.
 *
 * @param <V> the source assembly type.
 * @param <M> the target model type.
 */
public abstract class VSBindingFactory<V extends VSAssembly, M extends BindingModel> {
   /**
    * Gets the assembly class supported by this factory.
    *
     * @return the assembly class.
    */
   public abstract Class<V> getAssemblyClass();

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param assembly the assembly.
    *
    * @return a new model.
    */
   public abstract M createModel(V assembly);

   /**
    * Update assembly.
    *
    * @param model the specified binidng model.
    * @param assembly the specified assembly.
    *
    * @return the assembly.
    */
   public abstract V updateAssembly(M model, V assembly);
}