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
package inetsoft.web.admin.ai.general;

import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.general.model.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The closed, compile-time-known catalog of the Enterprise Manager "Settings &gt; General"
 * sub-models this area exposes -- a hardcoded Java enum, not a JSON resource, matching the sibling
 * {@code PresentationSubModel}'s precedent for a small closed unit set.
 *
 * <p>{@code GeneralSettingsPageController} aggregates seven sub-models. Six appear here.
 * <b>{@code licenseKey} is deliberately excluded</b>: the Licensing area
 * ({@code inetsoft.web.admin.ai.licensing}) already owns {@code LicenseKeySettingsService} and
 * models a license key as an add/remove/update unit rather than a settings blob. Two surfaces
 * writing the same service through different change models is how two callers end up with
 * different ideas of what a plan covers, so {@link #require} refuses that one name with its own
 * message pointing at the licensing tools rather than the generic unknown-name error.
 *
 * <p>An unrecognized name is a hard refusal everywhere this enum is resolved by key -- unlike
 * properties' open-ended namespace, these six names ARE the entire namespace.
 */
public enum GeneralSubModel {
   /** Custom locale labels. Storage-scope because {@code LocalizationSettingsService.setModel}
    * rewrites the locale properties file through {@code SUtil.saveLocaleProperties} in addition to
    * the {@code locale.available} property, but compensable -- see {@link #compensable()}. */
   LOCALIZATION("localization", LocalizationSettingsModel.class,
                AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, true, true),
   /** Server-wide materialized-view defaults. NOT per-viewsheet MV creation -- that is the
    * separate {@code inetsoft.web.admin.ai.mv} area ({@code analyze}/{@code preview}/{@code apply}
    * over {@code MVSupportService}). These two share only the letters "MV": nothing here creates,
    * deletes or rebuilds a materialized view. Storage-scope because
    * {@code MVSettingsService.setModel} calls {@code dataCycleManager.save()}. */
   MV("mv", MVSettingsModel.class,
      AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, true, true),
   /** Cache directory and the clean-on-startup flag. Note that changing {@code directory} does not
    * move or delete anything -- the irreversible act is the separate cache-cleanup action. */
   CACHE("cache", CacheSettingsModel.class,
         AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_HIGH, true, true),
   /** SMTP/mail settings. The only sub-model with {@link #secretFields()}. */
   EMAIL("email", EmailSettingsModel.class,
         AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_HIGH, true, true),
   /** Query timeouts, row caps and the data cache. */
   PERFORMANCE("performance", PerformanceSettingsModel.class,
               AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, true, true),
   /** Storage types and the asset-backup task name. <b>Read-only</b>: see {@link #writable()}. */
   DATA_SPACE("dataSpace", DataSpaceSettingsModel.class,
              AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false, false);

   GeneralSubModel(String key, Class<?> modelClass, String scope, String risk, boolean writable,
                   boolean compensable)
   {
      this.key = key;
      this.modelClass = modelClass;
      this.scope = scope;
      this.risk = risk;
      this.writable = writable;
      this.compensable = compensable;
      this.fieldNames = GeneralJson.fieldNames(modelClass);
   }

   public String key() {
      return key;
   }

   public Class<?> modelClass() {
      return modelClass;
   }

   /** {@link AdminChangeRecord#SCOPE_VALUE} for a thin {@code SreeEnv} wrapper, or
    * {@link AdminChangeRecord#SCOPE_STORAGE} for a sub-model whose writer also rewrites a
    * DataSpace-backed file ({@code localization}, {@code mv}). */
   public String scope() {
      return scope;
   }

   public String risk() {
      return risk;
   }

   public boolean isStorageScope() {
      return AdminChangeRecord.SCOPE_STORAGE.equals(scope);
   }

   /**
    * Whether a failed changeset can be undone by writing the previous value back.
    *
    * <p><b>Deliberately separate from {@link #isStorageScope()}, which is where this area departs
    * from {@code PresentationSubModel}.</b> That enum treats storage-scope as implying
    * non-compensable, and its apply service reports every storage-scope entry as an unconditional
    * rollback failure. That coupling does not hold here. {@code localization} and {@code mv} are
    * storage-scope because their writers rewrite a registry file, which is what the snapshot and
    * audit classification need to know -- but both have a real live inverse: the complete previous
    * model object, captured before the write, replayed through the very same {@code setModel}.
    * There is no partially-written intermediate state to strand, because each {@code setModel}
    * takes a whole model.
    *
    * <p>Every writable sub-model in this area is therefore compensable, which is why the change
    * path carries no {@code acknowledgeIrreversibleUpdate} flag at all. Do not add one back for
    * symmetry with presentation: an acknowledgement that every plan can satisfy trains a caller to
    * pass it without reading it, which is exactly what would make the one genuinely irreversible
    * operation here -- {@code cleanUpCache} -- dangerous.
    *
    * <p>{@link #DATA_SPACE} is {@code false} only because it is not writable at all.
    */
   public boolean compensable() {
      return compensable;
   }

   /**
    * Whether this sub-model can appear in a change spec at all.
    *
    * <p>{@code false} for {@link #DATA_SPACE} alone: {@code DataSpaceSettingsService} has no
    * {@code setModel}. The storage types come from {@code InetsoftConfig}, read from
    * {@code inetsoft.yaml} at boot, and {@code assetBackupTaskName} is the name of a scheduled
    * task rather than a setting. There is no runtime write path to expose, so this is a read gap
    * being closed, not a write one. It stays in the catalog -- and in every GET response --
    * because "which storage backend is this deployment on?" is genuinely useful context, and
    * because a sub-model silently missing from a read reads as a bug to a caller.
    */
   public boolean writable() {
      return writable;
   }

   /**
    * Whether re-reading this sub-model can prove that a failed write changed nothing.
    *
    * <p>True for every sub-model whose writer only touches state the reader reports back. False
    * for {@link #MV}: {@code MVSettingsService.setModel} calls
    * {@code dataCycleManager.setDefaultCycle()} and {@code save()} before
    * {@code mvManager.setDefaultCycle()}, but {@code getModel} reports {@code mvManager}'s value
    * -- so a throw from {@code save()} leaves the data-cycle manager holding the new default while
    * the read still shows the old one. {@link GeneralChangesetApplyService} uses this to report
    * unknown state rather than claiming a clean rollback over a deployment that did change.
    */
   public boolean readBackObservesEveryWrite() {
      return this != MV;
   }

   /** The exact set of {@code spec} field names this sub-model accepts -- mechanically derived
    * from {@link #modelClass()}, see {@link GeneralJson#fieldNames}. */
   public Set<String> fieldNames() {
      return fieldNames;
   }

   /** The subset of {@link #fieldNames()} whose values are secret-classified: masked on every read
    * and plan projection ({@link GeneralJson#maskSecrets}) and refused outright in a write
    * {@code spec} ({@code GeneralChangePlanService.requireNoSecretFields}). Empty for the other
    * five sub-models.
    *
    * <p>A property OF the sub-model rather than a condition at each call site, for the reason
    * {@code PresentationSubModel.secretFields()} documents: the three places that project or
    * validate a value used to spell out the condition independently, and one of them was missed
    * (Bug #76170). Asking the sub-model makes the next addition one edit instead of three. */
   public Set<String> secretFields() {
      return switch(this) {
         case EMAIL -> GeneralJson.EMAIL_SECRET_FIELDS;
         default -> Set.of();
      };
   }

   /** Exact, case-sensitive match against the six names -- no fuzzy or prefix matching, since a
    * wrong guess would silently target the wrong sub-model.
    *
    * @throws IllegalArgumentException naming all six valid values when {@code key} does not match,
    * or, for {@code "licenseKey"}, naming the licensing tools that own it instead. */
   public static GeneralSubModel require(String key) {
      for(GeneralSubModel subModel : values()) {
         if(subModel.key.equals(key)) {
            return subModel;
         }
      }

      if(LICENSE_KEY_SUB_MODEL.equals(key)) {
         throw new IllegalArgumentException(
            "subModel: \"" + LICENSE_KEY_SUB_MODEL + "\" is part of Enterprise Manager's " +
            "Settings > General page but is not handled by this area -- license keys are owned by " +
            "the licensing tools, which model a key as an add/remove/update unit rather than a " +
            "settings value. Use list_license_keys and preview_license_changes/" +
            "apply_license_changes instead. The sub-models handled here are: " + allKeys());
      }

      throw new IllegalArgumentException(
         "subModel: \"" + key + "\" is not one of the six recognized general sub-models (" +
         allKeys() + ")");
   }

   public static String allKeys() {
      return Arrays.stream(values()).map(GeneralSubModel::key).collect(Collectors.joining(", "));
   }

   /** The seventh sub-model of the Enterprise Manager page, handled by the licensing area. */
   static final String LICENSE_KEY_SUB_MODEL = "licenseKey";

   private final String key;
   private final Class<?> modelClass;
   private final String scope;
   private final String risk;
   private final boolean writable;
   private final boolean compensable;
   private final Set<String> fieldNames;
}
