/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.viewsheet.service;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.command.UpdateZIndexesCommand;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * A service responsible for managing the layering and arrangement of viewsheet components,
 * both inside and outside containers, by modifying Z-index and other composition-related properties.
 */
@Service
public class VSCompositionService {
   /**
    * Update all the children zindex when remove container.
    */
   public void updateZIndex(Viewsheet vs, Assembly assembly) {
      if(!(assembly instanceof ContainerVSAssembly) || vs == null) {
         return;
      }

      ContainerVSAssembly cass = (ContainerVSAssembly) assembly;
      String[] assemblies = cass.getAssemblies();
      updateZIndex(vs, cass, assemblies);
   }

   /**
    * Update specified child zindex when remove container assembly or move out
    * of container assembly.
    */
   private void updateZIndex(Viewsheet vs, ContainerVSAssembly assembly, String[] children) {
      String name = assembly.getName();
      String prefix = name.contains(".") ? name.substring(0, name.indexOf('.') + 1) : "";

      Arrays.stream(children)
         .forEach(child -> {
            child = child.contains(".") ? child : prefix + child;
            VSAssembly vsobj = (VSAssembly) vs.getAssembly(child);

            if(vsobj != null) {
               vsobj.setZIndex(vsobj.getZIndex() + assembly.getZIndex());
            }
         });
   }

   /**
    * Add layer command to set all objects.
    */
   public void addLayerCommand(Viewsheet vs, CommandDispatcher dispatcher) {
      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(false, true);
      addLayerCommand(vs, assemblies, dispatcher);
   }

   /**
    * Add layer command.
    */
   private void addLayerCommand(Viewsheet vs, Assembly[] assemblies,
                                CommandDispatcher dispatcher)
   {
      List<String> names = new ArrayList<>();
      List<Integer> indexes = new ArrayList<>();

      Arrays.stream(assemblies)
         .forEach(assembly -> {
            VSAssembly vsass = (VSAssembly) assembly;
            names.add(vsass.getAbsoluteName());
            indexes.add(vsass.getZIndex());
         });

      UpdateZIndexesCommand command = new UpdateZIndexesCommand();
      command.setAssemblies(names);
      command.setzIndexes(indexes);
      dispatcher.sendCommand(vs.getAbsoluteName(), command);

      // also send command to any embedded viewsheets
      Arrays.stream(assemblies)
         .filter(Viewsheet.class::isInstance)
         .map(Viewsheet.class::cast)
         .forEach(viewsheet -> addLayerCommand(viewsheet,
                                               viewsheet.getAssemblies(false, true),
                                               dispatcher));
   }


   /**
    * Adjust the z-index for all the components in the viewsheet.
    */
   public void shrinkZIndex(Viewsheet vs, CommandDispatcher dispatcher) {
      shrinkZIndex0(vs);
      addLayerCommand(vs, dispatcher);
   }

   /**
    * Shrink all object in viewsheet, include child viewsheets.
    */
   private void shrinkZIndex0(Viewsheet vs) {
      if(vs == null) {
         return;
      }

      vs.calcChildZIndex();
      Assembly[] ass = vs.getAssemblies(false, false);

      Arrays.stream(ass)
         .forEach(assembly -> {
            if(assembly instanceof Viewsheet) {
               shrinkZIndex0((Viewsheet) assembly);
            }
         });
   }
}
