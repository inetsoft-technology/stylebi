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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the canonical, human-readable projection of a data source's properties -- used as both
 * the plan hash input (section 5) and the audit before/after value (section 8).
 *
 * <p><b>Never projects the real password.</b> Regardless of what value the {@code password} field
 * of the DTO passed in actually holds -- the wrapped API's own {@code "******"} literal, {@link
 * Util#PLACEHOLDER_PASSWORD}, or (if a caller of this class made a mistake) the real value -- the
 * projection always substitutes the literal {@link Util#PLACEHOLDER_PASSWORD} string for password.
 * This is a second, independent line of defense (section 9) against the real password ever
 * reaching a hash or an {@code AdminChangeRecord}: even if {@link DataSourceChangePlanService}'s
 * own merge logic had a bug and passed a real password into a DTO handed to this class, this class
 * itself would still never emit it -- the reason every test in {@code
 * DataSourceChangePlanServiceTest} that asserts "the real password never appears in a hash/audit
 * projection" can do so by literal string-search over this method's own output.
 */
final class DataSourceProjection {
   private DataSourceProjection() {
   }

   static String projectJdbc(JdbcDataSourceProperties p) {
      StringBuilder sb = new StringBuilder();
      sb.append("type=jdbc")
         .append(";id=").append(canonical(p.getId()))
         .append(";name=").append(canonical(p.getName()))
         .append(";url=").append(canonical(p.getUrl()))
         .append(";driver=").append(canonical(p.getDriver()))
         .append(";defaultDatabase=").append(canonical(p.getDefaultDatabase()))
         .append(";tableName=").append(canonical(p.getTableName()))
         .append(";isolation=").append(p.getIsolation())
         .append(";ansiJoin=").append(p.isAnsiJoin())
         .append(";requireLogin=").append(p.isRequireLogin())
         .append(";user=").append(canonical(p.getUser()))
         // Never the real value or either wrapped-API masking literal -- always this one constant,
         // regardless of what p.getPassword() actually returns (see class javadoc).
         .append(";password=").append(p.isRequireLogin() ? Util.PLACEHOLDER_PASSWORD : "(none)")
         .append(";useCredentialId=").append(p.isUseCredentialId())
         .append(";credentialID=").append(canonical(p.getCredentialID()));
      return sb.toString();
   }

   static String projectTabular(TabularDataSourceProperties p) {
      return "type=tabular" +
         ";id=" + canonical(p.getId()) +
         ";name=" + canonical(p.getName()) +
         ";tabularType=" + canonical(p.getTabularType());
   }

   static String project(DataSourceProperties p) {
      if(p instanceof JdbcDataSourceProperties) {
         return projectJdbc((JdbcDataSourceProperties) p);
      }

      return projectTabular((TabularDataSourceProperties) p);
   }

   /** Section 0.2/5: the dependency-preflight result folds into the plan hash for a delete, so a
    * concurrent change that adds a new dependency between preview and apply also perturbs it. */
   static String projectDependencies(List<AssetObject> dependencies) {
      if(dependencies == null || dependencies.isEmpty()) {
         return "(none)";
      }

      return dependencies.stream()
         .map(o -> o instanceof AssetEntry
            ? ((AssetEntry) o).getType() + ":" + ((AssetEntry) o).getPath() : String.valueOf(o))
         .sorted()
         .collect(Collectors.joining(","));
   }

   private static String canonical(String value) {
      return value == null ? NULL_MARKER : value;
   }

   private static final String NULL_MARKER = String.valueOf((char) 0x01);
}
