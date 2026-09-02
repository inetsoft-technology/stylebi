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
package inetsoft.web.admin.ai.presentation;

import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.general.model.WebMapSettingsModel;
import inetsoft.web.admin.presentation.model.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The closed, compile-time-known catalog of the 16 sub-models {@code PresentationSettingsController}
 * actually wires (01-spec.md section 0/2) -- a hardcoded Java enum, not a JSON resource, matching
 * {@code AdminProviderController}'s own sibling area's precedent for a small closed unit set
 * (01-spec.md "Flagged decisions" item 1). Deliberately excludes the two dead
 * {@code PresentationSettingsModel} fields ({@code reportToolbarOptionsModel},
 * {@code reportViewerSettingsModel} -- declared on the model, never read/written by the real
 * controller, 01-spec.md section 2).
 *
 * <p>An unrecognized name is a hard refusal everywhere this enum is resolved by key -- unlike
 * properties' open-ended namespace, these 16 names ARE the entire namespace (01-spec.md section 2).
 */
public enum PresentationSubModel {
   FORMATS("formats", PresentationFormatsSettingsModel.class,
           AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   DASHBOARD("dashboard", PresentationDashboardSettingsModel.class,
             AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   VIEWSHEET_TOOLBAR("viewsheetToolbar", PresentationViewsheetToolbarOptionsModel.class,
                      AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   LOOK_AND_FEEL("lookAndFeel", LookAndFeelSettingsModel.class,
                 AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, false),
   WELCOME_PAGE("welcomePage", WelcomePageSettingsModel.class,
                AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, false),
   LOGIN_BANNER("loginBanner", PresentationLoginBannerSettingsModel.class,
                AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, false),
   PORTAL_INTEGRATION("portalIntegration", PortalIntegrationSettingsModel.class,
                       AdminChangeRecord.SCOPE_STORAGE, AdminChangeRecord.RISK_HIGH, false),
   PDF_GENERATION("pdfGeneration", PresentationPdfGenerationSettingsModel.class,
                  AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   EXPORT_MENU("exportMenu", PresentationExportMenuSettingsModel.class,
               AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   FONT_MAPPING("fontMapping", PresentationFontMappingSettingsModel.class,
                AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, true),
   SHARE("share", PresentationShareSettingsModel.class,
         AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   COMPOSER_MESSAGE("composerMessage", PresentationComposerMessageSettingsModel.class,
                     AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   TIME("time", PresentationTimeSettingsModel.class,
        AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   DATA_SOURCE_VISIBILITY("dataSourceVisibility", PresentationDataSourceVisibilitySettingsModel.class,
                           AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   WEB_MAP("webMap", WebMapSettingsModel.class,
           AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, false),
   AI("ai", PresentationAISettingsModel.class,
      AdminChangeRecord.SCOPE_VALUE, AdminChangeRecord.RISK_LOW, true);

   PresentationSubModel(String key, Class<?> modelClass, String scope, String risk,
                         boolean globalOnly)
   {
      this.key = key;
      this.modelClass = modelClass;
      this.scope = scope;
      this.risk = risk;
      this.globalOnly = globalOnly;
      this.fieldNames = PresentationJson.fieldNames(modelClass);
   }

   public String key() {
      return key;
   }

   public Class<?> modelClass() {
      return modelClass;
   }

   /** {@link AdminChangeRecord#SCOPE_VALUE} for a thin {@code SreeEnv} wrapper, or
    * {@link AdminChangeRecord#SCOPE_STORAGE} for one of the 4 {@code DataSpace}-backed sub-models
    * (01-spec.md section 4). */
   public String scope() {
      return scope;
   }

   /** {@link AdminChangeRecord#RISK_HIGH} for the 4 storage-scope sub-models, {@link
    * AdminChangeRecord#RISK_LOW} for the other 12 (01-spec.md section 4). */
   public String risk() {
      return risk;
   }

   public boolean isStorageScope() {
      return AdminChangeRecord.SCOPE_STORAGE.equals(scope);
   }

   /** {@code fontMapping}/{@code ai} -- the underlying sub-service takes no org-scope parameter at
    * all, so {@code scope: "organization"} is refused rather than silently applied to the global
    * layer (01-spec.md section 11). */
   public boolean globalOnly() {
      return globalOnly;
   }

   /** The exact set of {@code spec} field names this sub-model accepts (01-spec.md section 5) --
    * mechanically derived from {@link #modelClass()}, see {@link PresentationJson#fieldNames}. */
   public Set<String> fieldNames() {
      return fieldNames;
   }

   /** The subset of {@link #fieldNames()} whose values are secret-classified for this area: masked
    * on every read and plan projection ({@link PresentationJson#maskSecrets}) and refused outright
    * in a write {@code spec} ({@code PresentationChangePlanService.requireNoSecretFields}). Empty
    * for the other fourteen sub-models.
    *
    * <p>Deliberately a property OF the sub-model rather than a condition at each call site. The
    * three places that project or validate a value used to spell out {@code == WEB_MAP}
    * independently, so {@code share}'s two webhook URLs -- withheld on the properties path by
    * {@code AdminPropertyCatalog.CONFIRMED_SECRET} -- were still returned in full, and still
    * settable, through this area (Bug #76170). Asking the sub-model makes the next addition one
    * edit instead of three that can be made one at a time.
    *
    * <p>A switch rather than a constructor parameter so that the fourteen sub-models with no
    * secrets do not each carry an empty set through the constructor; it is evaluated lazily, so it
    * does not reintroduce the enum-constant/static-field initialization ordering hazard
    * {@link PresentationJson}'s own javadoc describes. */
   public Set<String> secretFields() {
      return switch(this) {
         case WEB_MAP -> PresentationJson.WEB_MAP_SECRET_FIELDS;
         case SHARE -> PresentationJson.SHARE_SECRET_FIELDS;
         default -> Set.of();
      };
   }

   /** Exact, case-sensitive match against the 16 names (01-spec.md section 11) -- no fuzzy/prefix
    * matching, since a wrong guess here would silently target the wrong sub-model.
    *
    * @throws IllegalArgumentException naming all 16 valid values when {@code key} does not match. */
   public static PresentationSubModel require(String key) {
      for(PresentationSubModel subModel : values()) {
         if(subModel.key.equals(key)) {
            return subModel;
         }
      }

      throw new IllegalArgumentException(
         "subModel: \"" + key + "\" is not one of the 16 recognized presentation sub-models (" +
         allKeys() + ")");
   }

   public static String allKeys() {
      return Arrays.stream(values()).map(PresentationSubModel::key)
         .collect(Collectors.joining(", "));
   }

   private final String key;
   private final Class<?> modelClass;
   private final String scope;
   private final String risk;
   private final boolean globalOnly;
   private final Set<String> fieldNames;
}
