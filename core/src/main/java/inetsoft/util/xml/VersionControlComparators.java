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
package inetsoft.util.xml;

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.binding.Field;
import inetsoft.uql.asset.AssetEntry;

import java.util.*;
import java.util.stream.Collectors;

public class VersionControlComparators {

   public static final Comparator<AssetEntry> assetEntry = AssetEntry::compareTo;

   public static final Comparator<String> string = String::compareTo;

   public static final Comparator<Map.Entry<String, ?>> keyStringMapEntry
      = Comparator.comparing(Map.Entry::getKey);

   public static final Comparator<Map.Entry<TableDataPath, ?>> keyTableDataPathMapEntry
      = Comparator.comparing(entry -> String.join("", entry.getKey().getPath()));

   public static final Comparator<Map.Entry<Field, ?>> keyFieldMapEntry
      = Comparator.comparing(entry -> entry.getKey().getName());

   public static <T> List<Map.Entry<String, T>> sortStringKeyMap(Map<String, T> map) {
      return sortStringKeyEntrySet(map.entrySet());
   }

   public static <T> List<Map.Entry<String, T>> sortStringKeyEntrySet(
      Set<Map.Entry<String, T>> entrySet)
   {
      return entrySet
         .stream()
         .sorted(keyStringMapEntry)
         .collect(Collectors.toList());
   }

   public static <T> List<Map.Entry<TableDataPath, T>> sortTableDataPathKeyMap(
      Map<TableDataPath, T> map)
   {
      return map.entrySet()
         .stream()
         .sorted(keyTableDataPathMapEntry)
         .collect(Collectors.toList());
   }

   public static <T> List<Map.Entry<Field, T>> sortFieldKeyMap(Map<Field, T> map) {
      return map.entrySet()
         .stream()
         .sorted(keyFieldMapEntry)
         .collect(Collectors.toList());
   }

   public static <K extends Comparable<K>, T> List<Map.Entry<K, T>> sortComparableKeyMap(
      Map<K, T> map)
   {
      return map.entrySet()
         .stream()
         .sorted(Map.Entry.comparingByKey())
         .collect(Collectors.toList());
   }

   public static List<String> sortStringSets(Set<String> set) {
      return set.stream()
         .sorted(string)
         .collect(Collectors.toList());
   }

}
