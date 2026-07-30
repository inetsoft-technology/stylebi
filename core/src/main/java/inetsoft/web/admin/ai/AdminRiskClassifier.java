/*
 * This software is licensed under the AGPL license, see LICENSE.txt
 * and http://www.fsf.org/licensing/licenses/agpl-3.0.html for details.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
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
