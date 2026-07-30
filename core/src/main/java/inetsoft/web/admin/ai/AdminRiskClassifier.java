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

package inetsoft.web.admin.ai;

import inetsoft.util.audit.AdminChangeRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Classifies a proposed property change on two independent axes.
 *
 * <ul>
 *   <li><b>risk</b> — blast radius, which drives whether an agent reviewer must sign off.</li>
 *   <li><b>snapshotScope</b> — how the change is made reversible. {@code value} means restoring the
 *       recorded before-value suffices; {@code storage} means a Tier-2 backup is needed because the
 *       change reaches beyond the property value (e.g. {@code security.exposedefaultorgtoall} fires
 *       repository side effects through {@code PropertyChangeSideEffects}).</li>
 * </ul>
 *
 * <p>Conflating them would make the catalog's per-property {@code snapshotScope} meaningless for
 * high-risk entries: {@code mail.smtp.host} is high risk yet perfectly reversible from its
 * before-value. Scope escalates to {@code storage} only when the namespace rules <em>override</em>
 * the catalog — a disagreement implying the entry is under-specified — or when the property is
 * uncatalogued, where nothing is known about side effects.
 *
 * <p>Namespace rules match the <em>base</em> name, so aliases and org-qualified names cannot escape
 * them.
 */
@Component
public class AdminRiskClassifier {
   @Autowired
   public AdminRiskClassifier(AdminPropertyCatalog catalog) {
      this.catalog = catalog;
   }

   /**
    * @param recognized {@code false} when the property is uncatalogued, i.e. the classification is
    *                   a fail-safe guess rather than a considered judgement.
    */
   public record RiskClassification(String risk, String snapshotScope, boolean recognized) {
   }

   public RiskClassification classify(AdminPropertyName name) {
      CatalogEntry entry = catalog.getEntry(name);
      boolean namespaceHigh = isHighRiskNamespace(name.baseName());

      if(entry == null) {
         return new RiskClassification(
            AdminChangeRecord.RISK_HIGH, AdminChangeRecord.SCOPE_STORAGE, false);
      }

      String risk = namespaceHigh ? AdminChangeRecord.RISK_HIGH : entry.risk();
      boolean escalated = namespaceHigh && !AdminChangeRecord.RISK_HIGH.equals(entry.risk());
      String scope = escalated ? AdminChangeRecord.SCOPE_STORAGE : entry.snapshotScope();

      return new RiskClassification(risk, scope, true);
   }

   private static boolean isHighRiskNamespace(String baseName) {
      for(String prefix : HIGH_RISK_PREFIXES) {
         if(baseName.startsWith(prefix)) {
            return true;
         }
      }

      return baseName.endsWith(".port");
   }

   private static final String[] HIGH_RISK_PREFIXES = { "security.", "cluster.", "mail." };
   private final AdminPropertyCatalog catalog;
}
