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

package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.XCube;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.xmla.Cube;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetOpenAssetService;
import inetsoft.web.wiz.controller.WizCubeController;
import inetsoft.web.wiz.model.WizCubeDetails;
import inetsoft.web.wiz.pairing.PairingException;

import java.awt.Point;
import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds a worksheet table from an XMLA cube: dimension levels, measures, or both.
 *
 * <p>The columns are the tree entries the Composer's data tree offers for the cube
 * ({@link AssetEventUtil#getCubeNode}), turned into a table by the same code a drop uses
 * ({@link WorksheetOpenAssetService#createTable}), so a column carries the ref type, level number
 * and data type of a UI drop. Names are checked against the cube with the names
 * {@code get_cube_details} reports, before anything is built.</p>
 *
 * <p>A drop keeps only the entries of the first entry's kind. So a request that is all levels of
 * one kind of dimension, or all measures, is built exactly as the drop builds it (the source keeps
 * that kind's data ref type); a request that mixes levels and measures, or time and model
 * dimensions, is stored with {@code SourceInfo.CUBE}, which the cube query handles per column and
 * which the save and reopen permission checks recognise. Nothing is dropped silently.</p>
 */
final class WorksheetCubeTableBuilder {
   private WorksheetCubeTableBuilder() {
   }

   /**
    * @param ws         the worksheet.
    * @param user       the caller.
    * @param cube       the cube, already read with the caller's permission.
    * @param datasource the data source path.
    * @param levels     the levels wanted.
    * @param measures   the measures wanted.
    * @param tableName  the table name, or null for the cube's name.
    * @param check      the permission check on the source.
    *
    * @return the new, not yet added, table.
    *
    * @throws PairingException naming the field and the valid values for a bad name.
    */
   static AbstractTableAssembly build(Worksheet ws, Principal user, Cube cube, String datasource,
                                      List<WorksheetMutationSupport.CubeLevelSpec> levels,
                                      List<String> measures, String tableName,
                                      WorksheetOpenAssetService.SourceCheck check)
      throws Exception
   {
      boolean noLevels = levels == null || levels.isEmpty();
      boolean noMeasures = measures == null || measures.isEmpty();

      if(noLevels && noMeasures) {
         throw new PairingException("add_cube_table needs at least one of levels " +
            "[{dimension, hierarchy?, level}] and measures [name] (see get_cube_details).");
      }

      if(tableName != null && tableName.startsWith("___inetsoft_cube_")) {
         throw new PairingException("table: \"" + tableName + "\" starts with the reserved " +
            "prefix ___inetsoft_cube_, which a saved worksheet may not carry; use a plain name.");
      }

      WizCubeDetails details = WizCubeController.describe(datasource, cube);
      List<Pick> picks = new ArrayList<>();

      if(!noLevels) {
         for(int i = 0; i < levels.size(); i++) {
            picks.add(pickLevel("levels[" + i + "]", details, levels.get(i)));
         }
      }

      if(!noMeasures) {
         for(int i = 0; i < measures.size(); i++) {
            picks.add(pickMeasure("measures[" + i + "]", details, measures.get(i)));
         }
      }

      Set<String> seen = new HashSet<>();

      for(Pick pick : picks) {
         if(!seen.add(pick.key())) {
            throw new PairingException(pick.field() + ": \"" + pick.label() +
               "\" is listed twice; each column may appear once.");
         }
      }

      AssetTreeModel.Node root = AssetEventUtil.getCubeNode(
         cube, datasource, true, true, null, null);
      List<AssetEntry> leaves = new ArrayList<>();
      collect(root, leaves);
      List<AssetEntry> entries = new ArrayList<>();
      Set<String> types = new LinkedHashSet<>();

      for(Pick pick : picks) {
         AssetEntry entry = null;

         for(AssetEntry leaf : leaves) {
            if(pick.matches(leaf)) {
               entry = leaf;
               break;
            }
         }

         if(entry == null) {
            throw new PairingException(pick.field() + ": \"" + pick.label() + "\" is in cube \"" +
               cube.getName() + "\" but the data tree does not offer it as a column.");
         }

         entries.add(entry);
         types.add(entry.getProperty("type"));
      }

      Integer forcedType = types.size() > 1 ? SourceInfo.CUBE : null;
      String name = tableName == null || tableName.isBlank() ? cube.getName() : tableName.trim();
      AbstractTableAssembly table = WorksheetOpenAssetService.createTable(
         ws, user, null, entries.toArray(new AssetEntry[0]), new Point(25, 0), check,
         forcedType, name);

      if(table == null || table.getColumnSelection().getAttributeCount() != entries.size()) {
         throw new PairingException("The cube table was not built with all " + entries.size() +
            " requested columns (got " +
            (table == null ? 0 : table.getColumnSelection().getAttributeCount()) +
            "); the organization's column limit may apply.");
      }

      return table;
   }

   private static void collect(AssetTreeModel.Node node, List<AssetEntry> leaves) {
      AssetEntry entry = node.getEntry();

      if(entry != null && entry.isColumn()) {
         leaves.add(entry);
      }

      for(AssetTreeModel.Node child : node.getNodes()) {
         collect(child, leaves);
      }
   }

   private static Pick pickLevel(String field, WizCubeDetails details,
                                 WorksheetMutationSupport.CubeLevelSpec spec)
      throws PairingException
   {
      if(spec == null || Tool.isEmptyString(spec.dimension()) || Tool.isEmptyString(spec.level())) {
         throw new PairingException(field + ": must be an object {dimension, hierarchy?, level}; " +
            "dimension and level are required.");
      }

      WizCubeDetails.Dimension dimension = match(details.dimensions(), spec.dimension().trim(),
                                                 WizCubeDetails.Dimension::name,
                                                 WizCubeDetails.Dimension::caption);

      if(dimension == null) {
         List<String> valid = new ArrayList<>();
         details.dimensions().forEach(d -> valid.add(d.name()));
         throw new PairingException(field + ".dimension: no dimension \"" + spec.dimension().trim() +
            "\" in cube \"" + details.name() + "\"; valid dimensions: " + valid);
      }

      List<WizCubeDetails.Hierarchy> hierarchies = dimension.hierarchies();

      if(!Tool.isEmptyString(spec.hierarchy())) {
         WizCubeDetails.Hierarchy hierarchy = match(hierarchies, spec.hierarchy().trim(),
                                                    WizCubeDetails.Hierarchy::entity,
                                                    WizCubeDetails.Hierarchy::caption);

         if(hierarchy == null) {
            List<String> valid = new ArrayList<>();
            hierarchies.forEach(h -> valid.add(h.entity()));
            throw new PairingException(field + ".hierarchy: no hierarchy \"" +
               spec.hierarchy().trim() + "\" in dimension \"" + dimension.name() +
               "\"; valid hierarchies: " + valid);
         }

         hierarchies = List.of(hierarchy);
      }

      String wanted = spec.level().trim();
      List<WizCubeDetails.Hierarchy> owners = new ArrayList<>();
      List<WizCubeDetails.Level> found = new ArrayList<>();

      for(WizCubeDetails.Hierarchy h : hierarchies) {
         WizCubeDetails.Level level = match(h.levels(), wanted, WizCubeDetails.Level::name,
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

         throw new PairingException(field + ".level: no level \"" + wanted + "\" in dimension \"" +
            dimension.name() + "\"" + (hierarchies.size() == 1 ?
            " hierarchy \"" + hierarchies.get(0).entity() + "\"" : "") +
            "; valid levels: " + valid);
      }

      if(owners.size() > 1) {
         List<String> names = new ArrayList<>();
         owners.forEach(h -> names.add(h.entity() + " (" + path(h) + ")"));
         throw new PairingException(field + ".hierarchy: level \"" + wanted +
            "\" exists in several hierarchies of dimension \"" + dimension.name() +
            "\"; name one of: " + names);
      }

      String entity = owners.get(0).entity();
      String attribute = found.get(0).name();

      return new Pick(field, entity + "/" + attribute, dimension.name() + "." + attribute,
                      leaf -> (AssetEntry.DIMENSIONS + "").equals(
                                 leaf.getProperty(AssetEntry.CUBE_COL_TYPE)) &&
                         entity.equals(leaf.getProperty("entity")) &&
                         attribute.equals(leaf.getProperty("attribute")));
   }

   private static String path(WizCubeDetails.Hierarchy hierarchy) {
      List<String> names = new ArrayList<>();
      hierarchy.levels().forEach(l -> names.add(l.name()));

      return String.join(">", names);
   }

   private static Pick pickMeasure(String field, WizCubeDetails details, String name)
      throws PairingException
   {
      if(Tool.isEmptyString(name)) {
         throw new PairingException(field + ": a measure name is required.");
      }

      WizCubeDetails.Measure measure = match(details.measures(), name.trim(),
                                             WizCubeDetails.Measure::name,
                                             WizCubeDetails.Measure::caption);

      if(measure == null) {
         List<String> valid = new ArrayList<>();
         details.measures().forEach(m -> valid.add(m.name()));
         throw new PairingException(field + ": no measure \"" + name.trim() + "\" in cube \"" +
            details.name() + "\"; valid measures: " + valid);
      }

      String attribute = measure.name();

      return new Pick(field, "measure/" + attribute, attribute,
                      leaf -> (AssetEntry.MEASURES + "").equals(
                                 leaf.getProperty(AssetEntry.CUBE_COL_TYPE)) &&
                         attribute.equals(leaf.getProperty("attribute")));
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

   private record Pick(String field, String key, String label, Predicate<AssetEntry> test) {
      boolean matches(AssetEntry leaf) {
         return test.test(leaf);
      }
   }
}
