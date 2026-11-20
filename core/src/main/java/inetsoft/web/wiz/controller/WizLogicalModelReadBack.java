/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.uql.XConstants;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The read-back guard of a logical model write (charter C2): after the service reports success,
 * the stored model is read again and compared with what was written. A field the service dropped,
 * a name it rewrote or an element it silently discarded is a failure, never a success.
 *
 * <p>Both sides are first brought to a canonical form: blank text and absent text are the same,
 * absent flags take the service's defaults, and the bookkeeping members ({@code oldName},
 * {@code errorMessage}, {@code baseElement}, {@code extended}) are left out. Lists are compared in
 * order, so a reordered or dropped element shows.</p>
 */
final class WizLogicalModelReadBack {
   private WizLogicalModelReadBack() {
   }

   /** Returns the first difference between what was written and what is stored, or null. */
   static String diff(Definition written, Definition stored) {
      Definition w = canon(written);
      Definition s = canon(stored);

      if(!Objects.equals(w.name(), s.name())) {
         return "name: wrote \"" + w.name() + "\", stored \"" + s.name() + "\"";
      }

      for(String[] pair : new String[][] {
         { "view", w.view(), s.view() }, { "folder", w.folder(), s.folder() },
         { "connection", w.connection(), s.connection() },
         { "description", w.description(), s.description() } })
      {
         if(!Objects.equals(pair[1], pair[2])) {
            return pair[0] + ": wrote " + quote(pair[1]) + ", stored " + quote(pair[2]);
         }
      }

      List<Entity> we = w.entities();
      List<Entity> se = s.entities();

      for(int i = 0; i < Math.max(we.size(), se.size()); i++) {
         if(i >= we.size()) {
            return "entities[" + se.get(i).name() + "]: stored but not written";
         }

         if(i >= se.size()) {
            return "entities[" + we.get(i).name() + "]: written but not stored (a duplicate name " +
               "is dropped by the server)";
         }

         String problem = diff(we.get(i), se.get(i));

         if(problem != null) {
            return problem;
         }
      }

      return null;
   }

   private static String diff(Entity w, Entity s) {
      String path = "entities[" + w.name() + "]";

      if(!Objects.equals(w.name(), s.name())) {
         return path + ".name: wrote \"" + w.name() + "\", stored \"" + s.name() + "\"";
      }

      if(!Objects.equals(w.description(), s.description())) {
         return path + ".description: wrote " + quote(w.description()) + ", stored " +
            quote(s.description());
      }

      if(!Objects.equals(w.visible(), s.visible())) {
         return path + ".visible: wrote " + w.visible() + ", stored " + s.visible();
      }

      for(int i = 0; i < Math.max(w.attributes().size(), s.attributes().size()); i++) {
         if(i >= w.attributes().size()) {
            return path + ".attributes[" + s.attributes().get(i).name() +
               "]: stored but not written";
         }

         if(i >= s.attributes().size()) {
            return path + ".attributes[" + w.attributes().get(i).name() +
               "]: written but not stored (a duplicate name is dropped by the server)";
         }

         Attribute wa = w.attributes().get(i);
         Attribute sa = s.attributes().get(i);

         if(!wa.equals(sa)) {
            return path + ".attributes[" + wa.name() + "]." + firstDifference(wa, sa);
         }
      }

      return null;
   }

   /** Names the first component of two records that differ, with both values. */
   private static String firstDifference(Record written, Record stored) {
      try {
         for(java.lang.reflect.RecordComponent component :
            written.getClass().getRecordComponents())
         {
            Object w = component.getAccessor().invoke(written);
            Object s = component.getAccessor().invoke(stored);

            if(!Objects.equals(w, s)) {
               return component.getName() + ": wrote " + w + ", stored " + s;
            }
         }
      }
      catch(ReflectiveOperationException e) {
         return "differs (" + e + ")";
      }

      return "differs";
   }

   /** The canonical form of a definition for comparison. */
   static Definition canon(Definition definition) {
      List<Entity> entities = new ArrayList<>();

      for(Entity entity : definition.entities() == null ? List.<Entity>of() :
         definition.entities())
      {
         List<Attribute> attributes = new ArrayList<>();

         for(Attribute attribute : entity.attributes() == null ? List.<Attribute>of() :
            entity.attributes())
         {
            attributes.add(canon(attribute));
         }

         entities.add(new Entity(entity.name(), null, text(entity.description()),
                                 entity.visible() == null || entity.visible(), null, null,
                                 attributes));
      }

      return new Definition(definition.name(), text(definition.description()),
                            text(definition.view()), text(definition.folder()),
                            text(definition.connection()), null, null, entities);
   }

   private static Attribute canon(Attribute a) {
      boolean expression = WizLogicalModelMapper.isExpression(a);
      String format = a.format() == null ? null : text(a.format().format());
      String spec = a.format() == null ? null : text(a.format().formatSpec());
      boolean duration = XConstants.DURATION_FORMAT.equals(format);
      Format canonFormat = format == null && spec == null ? null :
         new Format(format, spec,
                    duration ? a.format().durationPadZeros() == null ||
                       a.format().durationPadZeros() : null);

      return new Attribute(
         a.name(), null, text(a.description()), a.visible() == null || a.visible(),
         expression ? WizLogicalModelWire.TYPE_EXPRESSION : WizLogicalModelWire.TYPE_COLUMN,
         expression ? null : a.table(), expression ? null : a.column(),
         expression ? a.expression() : null,
         expression ? Boolean.TRUE.equals(a.aggregate()) : null,
         expression ? a.parseable() == null || a.parseable() : null, text(a.dataType()),
         a.refType() == null ? "none" : a.refType(),
         WizLogicalModelMapper.formulaFromStored(a.formula()),
         Boolean.TRUE.equals(a.browseData()), text(a.browseQuery()), canonFormat,
         canon(a.drill()), null, null);
   }

   private static Drill canon(Drill drill) {
      if(drill == null || drill.paths() == null || drill.paths().isEmpty()) {
         return null;
      }

      List<WizLogicalModelWire.DrillPath> paths = new ArrayList<>();

      for(WizLogicalModelWire.DrillPath path : drill.paths()) {
         List<DrillParam> params = new ArrayList<>();

         for(DrillParam param : path.params() == null ? List.<DrillParam>of() : path.params()) {
            params.add(new DrillParam(param.name(), text(param.field()), text(param.type())));
         }

         DrillQuery query = path.query() == null ? null :
            new DrillQuery(path.query().entry(), path.query().params() == null ? List.of() :
               path.query().params());

         paths.add(new WizLogicalModelWire.DrillPath(
            path.name(),
            path.linkType() == null ? WizLogicalModelWire.LINK_WEB : path.linkType(),
            path.link() == null ? "" : path.link(),
            text(path.targetFrame()) == null ? "SELF" : path.targetFrame(),
            path.tip() == null ? "" : path.tip(), path.passParams() == null || path.passParams(),
            Boolean.TRUE.equals(path.disablePrompting()), params, query, null));
      }

      return new Drill(paths);
   }

   private static String text(String value) {
      return value == null || value.isEmpty() ? null : value;
   }

   private static String quote(String value) {
      return value == null ? "none" : "\"" + value + "\"";
   }
}
