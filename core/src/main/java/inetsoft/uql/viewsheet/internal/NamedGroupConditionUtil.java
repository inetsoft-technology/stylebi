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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.Catalog;

/**
 * Shared support for splicing an {@link XNamedGroupInfo} group's own {@link ConditionList} into
 * a filter/selection condition against a real bound column.
 *
 * <p>Extracted from the near-identical {@code TableConditionUtil.syncConditionList} and
 * {@code BaseTableShowDetailsService.syncConditionList}, which resolve the same
 * {@code "this"}-placeholder convention for calc-table cells; chart/crosstab/table dimensions
 * need the identical resolution wherever an Asset-typed group's conditions are spliced into a
 * query-time or interactive filter.
 */
public final class NamedGroupConditionUtil {
   private NamedGroupConditionUtil() {
   }

   /**
    * Resolves an Asset-typed named group's condition list against a real bound column, replacing
    * the {@code "this"} placeholder attribute a repository-registered (data-type-attached) named
    * group asset's conditions carry when authored unattached to any specific column.
    */
   public static void resolveConditionColumn(ConditionList list, String columnName) {
      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);

         if(item == null) {
            continue;
         }

         DataRef ref = item.getAttribute();
         String attr = ref == null ? null : ref.getAttribute().trim();

         if(Catalog.getCatalog().getString("this").equals(attr) || "this".equals(attr)) {
            item.setAttribute(new ColumnRef(new AttributeRef(columnName)));
         }
      }
   }

   /** True only for the two asset-backed shapes whose conditions may carry the "this" placeholder. */
   public static boolean needsColumnResolution(XNamedGroupInfo info) {
      return info != null && (info.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF ||
         info.getType() == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO);
   }
}
