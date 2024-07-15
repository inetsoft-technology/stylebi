/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that provides access to the object model factory instances.
 */
@Service
public class VSObjectModelFactoryService {
   @Autowired
   public VSObjectModelFactoryService(List<VSObjectModelFactory<?, ?>> factories) {
      this.factories = new HashMap<>();
      factories.forEach(this::registerFactory);
      addDummyFactory(AnnotationLineVSAssembly.class);
      addDummyFactory(AnnotationRectangleVSAssembly.class);
   }

   /**
    * Gets the factory for the specified assembly type.
    *
    * @param assemblyClass the assembly type class.
    *
    * @param <A> the assembly type.
    * @param <M> the object model type.
    *
    * @return the matching factory or {@code null} if not found.
    */
   @SuppressWarnings("unchecked")
   public <A extends VSAssembly, M extends VSObjectModel<A>> VSObjectModelFactory<A, M>
   getFactory(Class<A> assemblyClass)
   {
      VSObjectModelFactory<A, M> factory = null;
      Class<?> clazz = assemblyClass;

      while((VSAssembly.class.isAssignableFrom(clazz)) && factory == null) {
         factory = (VSObjectModelFactory<A, M>) factories.get(clazz);
         clazz = clazz.getSuperclass();
      }

      return factory;
   }

   /**
    * Creates the object model for the specified assembly.
    *
    * @param assembly  the source assembly.
    *
    * @param <A> the assembly type.
    * @param <M> the object model type.
    *
    * @return the new object model.
    */
   @SuppressWarnings("unchecked")
   public <A extends VSAssembly, M extends VSObjectModel<A>> M createModel(
      A assembly, RuntimeViewsheet rvs)
   {
      Objects.requireNonNull(assembly, "The assembly must not be null");
      VSObjectModelFactory factory = getFactory(assembly.getClass());
      Objects.requireNonNull(
         factory, () -> "No factory found for assembly type: " + assembly.getClass().getName());

      try {
         return (M) ProfileUtils.addExecutionBreakDownRecord(rvs.getViewsheetSandbox().getID(),
            ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, (args) -> {
            return factory.createModel(assembly, rvs);
         }, assembly, rvs);
      }
      catch(Exception ex) {
         if(!rvs.isDisposed()) {
            LOG.error("Failed to create object model for {}", assembly, ex);
         }
      }

      return null;
   }

   private void registerFactory(VSObjectModelFactory<?, ?> factory) {
      factories.put(factory.getAssemblyClass(), factory);
   }

   private void addDummyFactory(Class<? extends VSAssembly> assemblyClass) {
      registerFactory(new DummyModelFactory<>(assemblyClass));
   }

   private final Map<Class<?>, VSObjectModelFactory<?, ?>> factories;

   private static final class DummyModelFactory<A extends VSAssembly>
      extends VSObjectModelFactory<A, VSObjectModel<A>>
   {
      DummyModelFactory(Class<A> assemblyClass) {
         super(assemblyClass);
      }

      @Override
      public VSObjectModel<A> createModel(A assembly, RuntimeViewsheet viewsheet) {
         return null;
      }
   }

   private final Logger LOG = LoggerFactory.getLogger(VSObjectModelFactoryService.class);
}
