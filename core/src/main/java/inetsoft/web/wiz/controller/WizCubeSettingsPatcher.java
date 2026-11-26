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

import inetsoft.uql.XDimension;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.Cube;
import inetsoft.uql.xmla.DimMember;
import inetsoft.uql.xmla.Dimension;
import inetsoft.util.Tool;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.request.WizCubeSettingsRequest;
import inetsoft.web.wiz.model.WizCubeSettingsResult;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Applies a {@link WizCubeSettingsRequest} to a cube of a stored XMLA domain: the Portal's "OLAP
 * Data" settings, Keep Original Order per dimension (hierarchy) and As Date, Database Locale and
 * Parse Pattern per level.
 *
 * <p>Every name is resolved against the cube first, with the names {@code get_cube_details}
 * reports, and every value is validated, before anything is changed, so a bad request leaves the
 * cube untouched. A problem is an {@link IllegalArgumentException} whose message names the field
 * and lists the valid values.</p>
 *
 * <p>This works on the stored objects directly (the way {@code refreshStoredCubes} merges them)
 * and never rebuilds members from a Portal model, which could drop what the model does not
 * carry.</p>
 */
final class WizCubeSettingsPatcher {
   private WizCubeSettingsPatcher() {
   }

   /**
    * Applies the request to the cube.
    *
    * @param cube    the cube of the (cloned) domain to change.
    * @param source  the data source path.
    * @param request the edit.
    *
    * @return the settings of what was named, as the patched cube now holds them.
    *
    * @throws IllegalArgumentException if a name or value is not valid; nothing is changed.
    */
   static WizCubeSettingsResult apply(Cube cube, String source, WizCubeSettingsRequest request) {
      List<WizCubeSettingsRequest.Dimension> dimensions =
         request.dimensions() == null ? List.of() : request.dimensions();
      List<WizCubeSettingsRequest.Level> levels =
         request.levels() == null ? List.of() : request.levels();

      if(dimensions.isEmpty() && levels.isEmpty()) {
         throw new IllegalArgumentException(
            "dimensions and levels: nothing to change; give dimensions " +
               "[{dimension, hierarchy?, keepOriginalOrder}] and/or levels [{dimension, " +
               "hierarchy?, level, asDate?, locale?, datePattern?}]");
      }

      WizCubeDetails details = WizCubeController.describe(source, cube);
      List<DimensionPlan> dimensionPlans = new ArrayList<>();
      List<LevelPlan> levelPlans = new ArrayList<>();

      for(int i = 0; i < dimensions.size(); i++) {
         dimensionPlans.addAll(planDimension("dimensions[" + i + "]", cube, details,
                                             dimensions.get(i)));
      }

      for(int i = 0; i < levels.size(); i++) {
         levelPlans.add(planLevel("levels[" + i + "]", details, levels.get(i)));
      }

      for(DimensionPlan plan : dimensionPlans) {
         XDimension dim = findDimension(cube, plan.hierarchy().entity());

         if(dim instanceof Dimension d) {
            d.setOriginalOrder(plan.keepOriginalOrder());
         }
      }

      for(LevelPlan plan : levelPlans) {
         XDimension dim = findDimension(cube, plan.hierarchy().entity());

         if(dim == null || plan.level().levelNumber() >= dim.getLevelCount() ||
            !(dim.getLevelAt(plan.level().levelNumber()) instanceof DimMember member))
         {
            throw new IllegalArgumentException(
               plan.field() + ".level: \"" + plan.level().name() + "\" is not an editable level " +
                  "of hierarchy \"" + plan.hierarchy().entity() + "\"");
         }

         XMetaInfo meta = member.getXMetaInfo() == null ? new XMetaInfo() :
            member.getXMetaInfo().clone();

         if(plan.asDate() != null) {
            meta.setAsDate(plan.asDate());
         }

         if(plan.locale() != null) {
            meta.setLocale(plan.locale().isEmpty() ? null : parseLocale(plan.field(), plan.locale()));
         }

         if(plan.datePattern() != null) {
            meta.setDatePattern(plan.datePattern().isEmpty() ? null : plan.datePattern());
         }

         member.setXMetaInfo(meta);
      }

      return snapshot(source, cube, dimensionPlans, levelPlans);
   }

   /**
    * The settings of the named dimensions and levels as a cube holds them now, in request order.
    * Used on the patched cube and again on the cube read back after saving.
    */
   static WizCubeSettingsResult snapshot(String source, Cube cube, WizCubeSettingsResult expected) {
      WizCubeDetails details = WizCubeController.describe(source, cube);
      List<WizCubeSettingsResult.Dimension> dimensions = new ArrayList<>();
      List<WizCubeSettingsResult.Level> levels = new ArrayList<>();

      for(WizCubeSettingsResult.Dimension d : expected.dimensions()) {
         WizCubeDetails.Hierarchy h = hierarchyByEntity(details, d.hierarchy());
         dimensions.add(new WizCubeSettingsResult.Dimension(
            d.dimension(), d.hierarchy(), h != null && h.originalOrder()));
      }

      for(WizCubeSettingsResult.Level l : expected.levels()) {
         WizCubeDetails.Hierarchy h = hierarchyByEntity(details, l.hierarchy());
         WizCubeDetails.Level level = h == null ? null : h.levels().stream()
            .filter(x -> x.name().equals(l.level())).findFirst().orElse(null);
         levels.add(level == null ? new WizCubeSettingsResult.Level(
            l.dimension(), l.hierarchy(), l.level(), false, null, null) :
            new WizCubeSettingsResult.Level(
               l.dimension(), l.hierarchy(), l.level(), level.asDate(), level.locale(),
               level.datePattern()));
      }

      return new WizCubeSettingsResult(expected.datasource(), expected.cube(), dimensions, levels);
   }

   private static WizCubeSettingsResult snapshot(String source, Cube cube,
                                                 List<DimensionPlan> dimensionPlans,
                                                 List<LevelPlan> levelPlans)
   {
      List<WizCubeSettingsResult.Dimension> dimensions = new ArrayList<>();
      List<WizCubeSettingsResult.Level> levels = new ArrayList<>();

      for(DimensionPlan p : dimensionPlans) {
         dimensions.add(new WizCubeSettingsResult.Dimension(
            p.dimension().name(), p.hierarchy().entity(), false));
      }

      for(LevelPlan p : levelPlans) {
         levels.add(new WizCubeSettingsResult.Level(
            p.dimension().name(), p.hierarchy().entity(), p.level().name(), false, null, null));
      }

      return snapshot(source, cube,
                      new WizCubeSettingsResult(source, cube.getName(), dimensions, levels));
   }

   private static WizCubeDetails.Hierarchy hierarchyByEntity(WizCubeDetails details,
                                                             String entity)
   {
      for(WizCubeDetails.Dimension d : details.dimensions()) {
         for(WizCubeDetails.Hierarchy h : d.hierarchies()) {
            if(h.entity().equals(entity)) {
               return h;
            }
         }
      }

      return null;
   }

   private static XDimension findDimension(Cube cube, String entity) {
      for(Enumeration<XDimension> dims = cube.getDimensions(); dims.hasMoreElements();) {
         XDimension dim = dims.nextElement();

         if(entity.equals(dim.getName())) {
            return dim;
         }
      }

      return null;
   }

   private static List<DimensionPlan> planDimension(String field, Cube cube,
                                                    WizCubeDetails details,
                                                    WizCubeSettingsRequest.Dimension request)
   {
      if(request == null) {
         throw new IllegalArgumentException(field + ": must be an object {dimension, " +
                                               "hierarchy?, keepOriginalOrder}");
      }

      if(request.keepOriginalOrder() == null) {
         throw new IllegalArgumentException(field + ".keepOriginalOrder: required (true or false)");
      }

      WizCubeDetails.Dimension dimension = resolveDimension(field, details, request.dimension());
      List<DimensionPlan> plans = new ArrayList<>();

      if(Tool.isEmptyString(request.hierarchy())) {
         for(WizCubeDetails.Hierarchy h : dimension.hierarchies()) {
            plans.add(new DimensionPlan(dimension, h, request.keepOriginalOrder()));
         }
      }
      else {
         plans.add(new DimensionPlan(dimension, resolveHierarchy(field, dimension,
                                                                 request.hierarchy()),
                                     request.keepOriginalOrder()));
      }

      for(DimensionPlan plan : plans) {
         XDimension dim = findDimension(cube, plan.hierarchy().entity());

         // Dimension.isOriginalOrder() is always true for a time dimension, and the Portal
         // disables the option there, so a stored false would read back as true
         if(dim != null && (dim.getType() & DataRef.CUBE_TIME_DIMENSION) ==
            DataRef.CUBE_TIME_DIMENSION)
         {
            throw new IllegalArgumentException(
               field + ".keepOriginalOrder: dimension \"" + plan.dimension().name() +
                  "\" hierarchy \"" + plan.hierarchy().entity() + "\" is a time dimension, " +
                  "which always keeps the original member order (the Portal disables the " +
                  "option for it); it cannot be set" + (Tool.isEmptyString(request.hierarchy()) ?
                  "; name a non-time hierarchy of the dimension" : ""));
         }
      }

      return plans;
   }

   private static LevelPlan planLevel(String field, WizCubeDetails details,
                                      WizCubeSettingsRequest.Level request)
   {
      if(request == null) {
         throw new IllegalArgumentException(field + ": must be an object {dimension, " +
                                               "hierarchy?, level, asDate?, locale?, datePattern?}");
      }

      if(request.asDate() == null && request.locale() == null && request.datePattern() == null) {
         throw new IllegalArgumentException(
            field + ": nothing to set; give asDate, locale and/or datePattern");
      }

      if(Tool.isEmptyString(request.level())) {
         throw new IllegalArgumentException(field + ".level: required");
      }

      WizCubeDetails.Dimension dimension = resolveDimension(field, details, request.dimension());
      List<WizCubeDetails.Hierarchy> hierarchies = dimension.hierarchies();

      if(!Tool.isEmptyString(request.hierarchy())) {
         hierarchies = List.of(resolveHierarchy(field, dimension, request.hierarchy()));
      }

      String wanted = request.level().trim();
      List<WizCubeDetails.Hierarchy> owners = new ArrayList<>();
      List<WizCubeDetails.Level> found = new ArrayList<>();

      for(WizCubeDetails.Hierarchy h : hierarchies) {
         WizCubeDetails.Level level = match(h.levels(), wanted,
                                            WizCubeDetails.Level::name,
                                            WizCubeDetails.Level::caption);

         if(level != null) {
            owners.add(h);
            found.add(level);
         }
      }

      if(owners.isEmpty()) {
         List<String> valid = new ArrayList<>();

         for(WizCubeDetails.Hierarchy h : hierarchies) {
            for(WizCubeDetails.Level l : h.levels()) {
               if(!valid.contains(l.name())) {
                  valid.add(l.name());
               }
            }
         }

         throw new IllegalArgumentException(
            field + ".level: no level \"" + wanted + "\" in dimension \"" + dimension.name() +
               "\"" + (hierarchies.size() == 1 ? " hierarchy \"" + hierarchies.get(0).entity() +
               "\"" : "") + "; valid levels: " + valid);
      }

      if(owners.size() > 1) {
         List<String> names = new ArrayList<>();

         for(WizCubeDetails.Hierarchy h : owners) {
            List<String> path = new ArrayList<>();
            h.levels().forEach(l -> path.add(l.name()));
            names.add(h.entity() + " (" + String.join(">", path) + ")");
         }

         throw new IllegalArgumentException(
            field + ".hierarchy: level \"" + wanted + "\" exists in several hierarchies of " +
               "dimension \"" + dimension.name() + "\"; name one of: " + names);
      }

      String locale = request.locale() == null ? null : request.locale().trim();
      String pattern = request.datePattern();

      if(locale != null && !locale.isEmpty()) {
         parseLocale(field, locale);
      }

      if(pattern != null && !pattern.isEmpty()) {
         try {
            new SimpleDateFormat(pattern);
         }
         catch(IllegalArgumentException ex) {
            throw new IllegalArgumentException(
               field + ".datePattern: \"" + pattern + "\" is not a valid date pattern (" +
                  ex.getMessage() + "); use a java.text.SimpleDateFormat pattern such as " +
                  "yyyy-MM-dd or MMM yyyy");
         }
      }

      WizCubeDetails.Level level = found.get(0);
      boolean finalAsDate = request.asDate() != null ? request.asDate() : level.asDate();

      if(!finalAsDate && ((locale != null && !locale.isEmpty()) ||
         (pattern != null && !pattern.isEmpty())))
      {
         throw new IllegalArgumentException(
            field + ": locale and datePattern only apply when asDate is true (the Portal " +
               "disables them otherwise); set asDate:true in the same call");
      }

      return new LevelPlan(field, dimension, owners.get(0), level, request.asDate(), locale,
                           pattern);
   }

   private static WizCubeDetails.Dimension resolveDimension(String field, WizCubeDetails details,
                                                            String name)
   {
      if(Tool.isEmptyString(name)) {
         throw new IllegalArgumentException(field + ".dimension: required");
      }

      WizCubeDetails.Dimension dimension = match(details.dimensions(), name.trim(),
                                                 WizCubeDetails.Dimension::name,
                                                 WizCubeDetails.Dimension::caption);

      if(dimension == null) {
         List<String> valid = new ArrayList<>();

         for(WizCubeDetails.Dimension d : details.dimensions()) {
            valid.add(d.name());
         }

         throw new IllegalArgumentException(
            field + ".dimension: no dimension \"" + name.trim() + "\" in cube \"" +
               details.name() + "\"; valid dimensions: " + valid);
      }

      return dimension;
   }

   private static WizCubeDetails.Hierarchy resolveHierarchy(String field,
                                                            WizCubeDetails.Dimension dimension,
                                                            String name)
   {
      WizCubeDetails.Hierarchy hierarchy = match(dimension.hierarchies(), name.trim(),
                                                 WizCubeDetails.Hierarchy::entity,
                                                 WizCubeDetails.Hierarchy::caption);

      if(hierarchy == null) {
         List<String> valid = new ArrayList<>();

         for(WizCubeDetails.Hierarchy h : dimension.hierarchies()) {
            valid.add(h.entity());
         }

         throw new IllegalArgumentException(
            field + ".hierarchy: no hierarchy \"" + name.trim() + "\" in dimension \"" +
               dimension.name() + "\"; valid hierarchies: " + valid);
      }

      return hierarchy;
   }

   /** An exact match on any key, then a unique case-insensitive one; null when none or several. */
   @SafeVarargs
   private static <T> T match(List<T> items, String wanted, Function<T, String>... keys) {
      for(Function<T, String> key : keys) {
         for(T item : items) {
            if(wanted.equals(key.apply(item))) {
               return item;
            }
         }
      }

      T match = null;

      for(T item : items) {
         for(Function<T, String> key : keys) {
            if(wanted.equalsIgnoreCase(key.apply(item))) {
               if(match != null && match != item) {
                  return null;
               }

               match = item;
            }
         }
      }

      return match;
   }

   /**
    * A database locale as the Portal stores it: a language code, or {@code language_COUNTRY}
    * ({@code -} is accepted for {@code _}). The language must be an ISO 639 code (or {@code jp},
    * which the Portal's own list offers) and the country an ISO 3166 code. The result is built as
    * {@code new Locale(language, country)}, which is what the stored domain reads back as;
    * {@code new Locale("en_US")} alone would make an odd language of the whole string.
    */
   static Locale parseLocale(String field, String text) {
      String normalized = text.trim().replace('-', '_');

      if(!LOCALE.matcher(normalized).matches()) {
         throw badLocale(field, text);
      }

      String[] parts = normalized.split("_");
      String language = parts[0].toLowerCase(Locale.ROOT);
      String country = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT) : "";

      if(!"jp".equals(language) && !Arrays.asList(Locale.getISOLanguages()).contains(language) ||
         !country.isEmpty() && !Arrays.asList(Locale.getISOCountries()).contains(country))
      {
         throw badLocale(field, text);
      }

      return new Locale(language, country);
   }

   private static IllegalArgumentException badLocale(String field, String text) {
      return new IllegalArgumentException(
         field + ".locale: \"" + text + "\" is not a supported locale; use a language code or " +
            "language_COUNTRY such as en, fr, de, it, jp, ko, zh, en_US, en_CA (the Portal's " +
            "list; any ISO 639 language with an ISO 3166 country is accepted), or \"\" to clear");
   }

   private record DimensionPlan(WizCubeDetails.Dimension dimension,
                                WizCubeDetails.Hierarchy hierarchy, boolean keepOriginalOrder)
   {
   }

   private record LevelPlan(String field, WizCubeDetails.Dimension dimension,
                            WizCubeDetails.Hierarchy hierarchy, WizCubeDetails.Level level,
                            Boolean asDate, String locale, String datePattern)
   {
   }

   private static final Pattern LOCALE = Pattern.compile("[A-Za-z]{2,3}(_[A-Za-z]{2})?");
}
