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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;

/**
 * Base class for factories that create a type of {@link VSObjectModel}.
 *
 * @param <A> the type of assembly.
 * @param <M> the type of model.
 */
public abstract class VSObjectModelFactory<A extends VSAssembly, M extends VSObjectModel<A>> {
   /**
    * Creates a new instance of {@code VSObjectModelFactory}.
    *
    * @param assemblyClass the type of assembly supported by the factory.
    */
   protected VSObjectModelFactory(Class<A> assemblyClass) {
      this.assemblyClass = assemblyClass;
   }

   /**
    * Gets the type of assembly supported by this factory.
    *
    * @return the assembly class.
    */
   public final Class<A> getAssemblyClass() {
      return assemblyClass;
   }

   /**
    * Creates a new object model.
    *
    * @param assembly  the source assembly.
    * @param viewsheet the parent runtime viewsheet.
    *
    * @return the new object model.
    */
   public abstract M createModel(A assembly, RuntimeViewsheet viewsheet);

   private final Class<A> assemblyClass;
}
