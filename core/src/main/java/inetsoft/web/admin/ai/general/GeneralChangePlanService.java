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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves a requested general-settings changeset into a hashed {@link ResolvedPlan}, reading every
 * named sub-model's live value and validating the request against what the underlying service will
 * actually do with it. Performs no mutation.
 *
 * <p>Reuses the shared {@code PlanChange}/{@code ResolvedPlan} records and JSON-serializes compound
 * sub-model values into their {@code String} fields, matching the cluster, licensing and
 * presentation areas rather than introducing a parallel plan-type family; that is also what lets
 * this area reuse {@code AdminChangesetApplyService.PlanHashMismatchException} and its
 * controller-level 409 handling verbatim.
 *
 * <p><b>Why this class is larger than its presentation counterpart's equivalent.</b> The
 * merge-onto-current pattern both areas use is only sound where a post-write read returns what was
 * written, and where every field in the model is actually written. Three of these five writable
 * sub-models break one of those assumptions, and each break would otherwise surface as a confusing
 * downstream failure rather than a clear refusal:
 *
 * <ul>
 *   <li>{@code cache.directory} is rewritten on read ({@link #simulateCacheReadback}), so a
 *       correct write would look like a failed one.</li>
 *   <li>{@code localization.locales} is a whole-list replace ({@link #requireWholeLocaleList}), so
 *       a partial list silently deletes.</li>
 *   <li>{@code email} writes most of its fields only under a matching {@code smtpAuthentication}
 *       ({@link #requireEmailFieldsMatchAuthType}), so a mismatched field is a silent no-op.</li>
 * </ul>
 */
@Component
public class GeneralChangePlanService {
   @Autowired
   public GeneralChangePlanService(GeneralSettingsAccess access) {
      this.access = access;
   }

   /**
    * Resolves and hashes a plan. Performs no mutation, but does perform a live read of every named
    * sub-model's current value.
    *
    * @throws IllegalArgumentException with a field-named message on a blank task, an empty change
    *         list, an unrecognized verb or sub-model, a {@code scope}/{@code orgId} field, a
    *         duplicate sub-model, a read-only sub-model, an unrecognized or non-settable
    *         {@code spec} field, a secret {@code email} field, an {@code email} field that the
    *         effective auth type would not write, a {@code localization.locales} list that drops a
    *         configured locale without {@code acknowledgeLocaleRemoval}, or
    *         an {@code mv.defaultCycle} that is not an available cycle.
    */
   public ResolvedPlan resolve(GeneralChangePlanRequest req, Principal principal) throws Exception {
      List<ResolvedChange> resolved = resolveEntries(req, principal);
      List<PlanChange> changes = new ArrayList<>();

      for(ResolvedChange entry : resolved) {
         changes.add(entry.planChange());
      }

      String task = req.getTask().trim();
      List<PlanChange> immutableChanges = Collections.unmodifiableList(changes);
      String planHash = hash(immutableChanges);
      return new ResolvedPlan(task, immutableChanges, true, true,
                              planHash, TaskAuditToken.issue(planHash, task));
   }

   /**
    * Same resolution {@link #resolve} performs, but also returns the actual proposed model object
    * for each change, so {@link GeneralChangesetApplyService} can pass it straight to
    * {@link GeneralSettingsAccess#write} without re-deriving it a second way.
    */
   List<ResolvedChange> resolveEntries(GeneralChangePlanRequest req, Principal principal)
      throws Exception
   {
      if(req == null || req.getTask() == null || req.getTask().trim().isEmpty()) {
         throw new IllegalArgumentException("task: a non-empty description is required");
      }

      if(req.getChanges() == null || req.getChanges().isEmpty()) {
         throw new IllegalArgumentException("changes: at least one change is required");
      }

      List<ResolvedChange> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      int index = 0;

      for(GeneralChangeRequest raw : req.getChanges()) {
         String label = "changes[" + index++ + "]";

         if(raw == null) {
            throw new IllegalArgumentException(label + ": must not be null");
         }

         requireVerb(label, raw.getVerb());
         requireNoScope(label, raw);
         GeneralSubModel subModel = requireSubModel(label, raw.getSubModel());
         requireAckOnlyForLocalization(label, raw, subModel);
         requireWritable(label, subModel);
         requireUnseen(label, subModel, seen);

         JsonNode spec = requireSpecObject(label, raw.getSpec());
         requireKnownFields(label, subModel, spec);
         requireNoSecretFields(label, subModel, spec);

         Object currentModel = access.read(subModel, principal);
         JsonNode currentNode = GeneralJson.toNode(currentModel);
         JsonNode mergedNode = GeneralJson.merge(currentNode, spec);

         switch(subModel) {
         case LOCALIZATION -> requireWholeLocaleList(label, raw, spec, currentNode);
         case MV -> {
            requireNotDirectlySettable(label, spec, "cycles",
                                       "it is derived from the data cycles that exist on this " +
                                       "deployment; set defaultCycle to choose among them");
            requireKnownDefaultCycle(label, mergedNode, currentNode);
         }
         case EMAIL -> {
            requireKnownAuthType(label, spec);
            requireSecretIdNotSet(label, spec);
            requireNotDirectlySettable(label, spec, "secretIdVisible",
                                       "it is a display flag EmailSettingsService never writes");
            requireNotDirectlySettable(label, spec, "fromAddressEnabled",
                                       "it is a display flag EmailSettingsService never writes");
            requireEmailFieldsMatchAuthType(label, spec, mergedNode);
            requireNoCloudSecretClobber(label, mergedNode);
         }
         case PERFORMANCE -> requireInRangeCacheSettings(label, spec);
         default -> {
         }
         }

         Object proposedModel;

         try {
            proposedModel = GeneralJson.toModel(mergedNode, subModel.modelClass());
         }
         catch(Exception e) {
            throw new IllegalArgumentException(
               label + ".spec: does not produce a valid \"" + subModel.key() + "\" value (" +
               e.getMessage() + ")", e);
         }

         // Projected from the round-tripped MODEL, never from mergedNode.
         //
         // mergedNode carries the caller's own JSON verbatim, and the apply service compares the
         // projection to a post-write read as an exact STRING. Anything Jackson accepts but does
         // not spell the same way therefore fails verification and rolls a correct write back:
         // "120" or 120.0 for a long field, "true" for a boolean, or -- for localization, the one
         // sub-model with nested objects -- a locale written {label, language, country} when the
         // model serializes {language, country, label}. Serializing the model puts both sides of
         // the comparison in the same canonical form, so only real differences survive.
         JsonNode proposedNode = GeneralJson.toNode(proposedModel);
         JsonNode readbackNode = switch(subModel) {
            case CACHE -> simulateCacheReadback(proposedNode);
            case LOCALIZATION -> simulateLocalizationReadback(proposedNode);
            default -> proposedNode;
         };

         PlanChange planChange = new PlanChange(
            subModel.key(), null, projectedValue(subModel, currentNode),
            projectedValue(subModel, readbackNode), subModel.risk(), subModel.scope(), true,
            "update " + subModel.key() + " general settings");

         result.add(new ResolvedChange(subModel, currentModel, proposedModel, planChange));
      }

      return result;
   }

   // ---------------------------------------------------------------- validation helpers

   static void requireVerb(String label, String verb) {
      if(verb == null || !GeneralChangeRequest.VERB_UPDATE.equalsIgnoreCase(verb.trim())) {
         throw new IllegalArgumentException(
            label + ".verb: must be \"" + GeneralChangeRequest.VERB_UPDATE + "\" (general " +
            "settings can only be updated -- there is nothing here to create or delete)");
      }
   }

   /**
    * Refuses {@code scope} and {@code orgId} rather than ignoring them.
    *
    * <p>Every service behind this area writes global {@code SreeEnv} state and accepts no org
    * argument, so neither field can select anything. Silently dropping them would let a caller
    * believe a change was confined to one tenant while it in fact applied to the whole deployment
    * -- a wrong answer rather than a missing feature. See {@link GeneralChangeRequest}.
    */
   private static void requireNoScope(String label, GeneralChangeRequest raw) {
      if(raw.getScope() != null) {
         throw new IllegalArgumentException(
            label + ".scope: not supported -- general settings are deployment-global. Every " +
            "service behind this area writes global configuration and takes no organization " +
            "argument, so there is no per-organization variant of these settings to target. " +
            "Remove the field; the change will apply to the whole deployment.");
      }

      if(raw.getOrgId() != null) {
         throw new IllegalArgumentException(
            label + ".orgId: not supported -- general settings are deployment-global and cannot " +
            "be targeted at one organization. Remove the field; the change will apply to the " +
            "whole deployment.");
      }
   }

   /**
    * Refuses {@code acknowledgeLocaleRemoval} on anything but a {@code localization} change.
    *
    * <p>Elsewhere the flag authorizes nothing, so leaving it to be ignored would let a caller
    * believe it had pre-authorized something it had not. Refused rather than dropped, for the same
    * reason {@code scope} is -- and so the two layers agree, since the tool refuses it client-side
    * too.
    */
   private static void requireAckOnlyForLocalization(String label, GeneralChangeRequest raw,
                                                     GeneralSubModel subModel)
   {
      if(Boolean.TRUE.equals(raw.getAcknowledgeLocaleRemoval()) &&
         subModel != GeneralSubModel.LOCALIZATION)
      {
         throw new IllegalArgumentException(
            label + ".acknowledgeLocaleRemoval: only applies to a \"localization\" change -- it " +
            "authorizes dropping a configured locale from spec.locales, and there is nothing for " +
            "it to authorize on \"" + subModel.key() + "\". Remove the field.");
      }
   }

   static GeneralSubModel requireSubModel(String label, String subModel) {
      if(subModel == null || subModel.trim().isEmpty()) {
         throw new IllegalArgumentException(label + ".subModel: required");
      }

      try {
         return GeneralSubModel.require(subModel.trim());
      }
      catch(IllegalArgumentException e) {
         throw new IllegalArgumentException(label + "." + e.getMessage(), e);
      }
   }

   private static void requireWritable(String label, GeneralSubModel subModel) {
      if(!subModel.writable()) {
         throw new IllegalArgumentException(
            label + ".subModel: \"" + subModel.key() + "\" is read-only -- the storage types are " +
            "set in inetsoft.yaml at deployment time and DataSpaceSettingsService has no " +
            "setModel, so there is no runtime write path. It is readable through the general " +
            "settings read tool. To take a storage backup, use the storage backup tool.");
      }
   }

   private static void requireUnseen(String label, GeneralSubModel subModel, Set<String> seen) {
      if(!seen.add(subModel.key())) {
         throw new IllegalArgumentException(
            label + ".subModel: \"" + subModel.key() + "\" appears more than once in this " +
            "changeset -- combine the fields into a single change, since each sub-model is " +
            "written as one whole value and a later entry would overwrite an earlier one");
      }
   }

   private static JsonNode requireSpecObject(String label, JsonNode spec) {
      if(spec == null || spec.isNull() || !spec.isObject() || spec.isEmpty()) {
         throw new IllegalArgumentException(
            label + ".spec: required, must be a non-empty object naming only the fields being " +
            "changed");
      }

      return spec;
   }

   private static void requireKnownFields(String label, GeneralSubModel subModel, JsonNode spec) {
      Set<String> valid = subModel.fieldNames();
      Iterator<String> names = spec.fieldNames();

      while(names.hasNext()) {
         String field = names.next();

         if(!valid.contains(field)) {
            throw new IllegalArgumentException(
               label + ".spec." + field + ": not a field of \"" + subModel.key() + "\" -- valid " +
               "fields are " + valid);
         }
      }
   }

   /**
    * Refuses a field that exists on the model but that the underlying writer never persists, or
    * that is derived from other state.
    *
    * <p>Worth a named refusal rather than silence because these fields round-trip perfectly: the
    * merge keeps them, the model accepts them, the read-back returns them unchanged, and nothing
    * anywhere reports that the value was ignored.
    */
   private static void requireNotDirectlySettable(String label, JsonNode spec, String field,
                                                  String why)
   {
      if(spec.has(field)) {
         throw new IllegalArgumentException(
            label + ".spec." + field + ": cannot be set directly -- " + why + ".");
      }
   }

   /**
    * {@code localization.locales} must be every current locale in full, unless the caller
    * explicitly acknowledges the removal.
    *
    * <p>{@code LocalizationSettingsService.setModel} rebuilds both {@code locale.available} and the
    * entire locale-label properties file from the list it is handed, so a shorter array does not
    * leave the omitted locales alone -- it deletes them. Same hazard, and same treatment, as
    * {@code portalIntegration.tabs} in the presentation area.
    *
    * <p><b>The acknowledgement exists because the refusal alone made removal impossible.</b> An
    * earlier version refused every short list unconditionally while telling the caller to "send
    * the full list without it and say so in the task description" -- which is the very thing it
    * had just refused, and there was no other path: no override, and the only way out was the
    * generic properties tool writing {@code locale.available} directly, which skips the label-file
    * rewrite and leaves the two out of step. Found in live testing, where following the message's
    * own instruction failed. {@link GeneralChangeRequest#getAcknowledgeLocaleRemoval()} makes the
    * documented action actually reachable while keeping the accident it guards against loud.
    */
   private static void requireWholeLocaleList(String label, GeneralChangeRequest raw, JsonNode spec,
                                              JsonNode current)
   {
      JsonNode locales = spec.get("locales");

      if(locales == null) {
         return;
      }

      if(!locales.isArray()) {
         throw new IllegalArgumentException(label + ".spec.locales: must be an array");
      }

      JsonNode currentLocales = current.get("locales");
      Set<String> submitted = new LinkedHashSet<>();

      for(JsonNode locale : locales) {
         submitted.add(localeKey(locale));
      }

      List<String> missing = new ArrayList<>();

      if(currentLocales != null && currentLocales.isArray()) {
         for(JsonNode locale : currentLocales) {
            String key = localeKey(locale);

            if(!submitted.contains(key)) {
               missing.add(key);
            }
         }
      }

      if(!missing.isEmpty() && !Boolean.TRUE.equals(raw.getAcknowledgeLocaleRemoval())) {
         throw new IllegalArgumentException(
            label + ".spec.locales: must include every current locale, and this list omits " +
            missing.size() + " of them (" + String.join(", ", missing) + "). locales is a " +
            "whole-list replace underneath -- LocalizationSettingsService.setModel rebuilds " +
            "locale.available AND the locale label file from exactly this list -- so the omitted " +
            "locale(s) would be deleted, not left unchanged. If that was not intended, call the " +
            "general settings read for \"localization\" first and include every current locale in " +
            "full, even the ones you are not changing. To delete " + String.join(", ", missing) +
            " on purpose, send this same list again with acknowledgeLocaleRemoval set to true on " +
            "this change, after confirming the deletion with the operator.");
      }
   }

   /** {@code language_country}, the key {@code LocalizationSettingsService} itself builds and the
    * identity a locale is tracked by -- the label is mutable and must not participate. */
   private static String localeKey(JsonNode locale) {
      String language = locale.path("language").asText("");
      String country = locale.path("country").asText("");
      return language + "_" + country;
   }

   /**
    * Refuses an {@code mv.defaultCycle} that is not one of the available cycles.
    *
    * <p><b>The global/per-org asymmetry this guards.</b> {@code DataCycleManager.setDefaultCycle}
    * writes the deployment-global {@code default.data.cycle} property, but the {@code cycles} list
    * it is chosen from comes from {@code getDataCycles(orgId)} and is per-organization. A site
    * administrator can therefore set a global default naming a cycle that exists in only one
    * tenant, where it resolves to nothing for every other tenant. Checking membership is the only
    * cheap guard available; the asymmetry itself is the product's, not this area's.
    *
    * <p>An empty {@code cycles} list is NOT treated as "no cycles exist":
    * {@code MVSettingsService.getModel} suppresses the list entirely on a multi-tenant enterprise
    * deployment. Nothing can be validated against a suppressed list, so any value is allowed and
    * the caller is told why by the read tool rather than being blocked here on a false premise.
    */
   private static void requireKnownDefaultCycle(String label, JsonNode merged, JsonNode current) {
      JsonNode defaultCycle = merged.get("defaultCycle");

      if(defaultCycle == null || !defaultCycle.isTextual() || defaultCycle.asText().isEmpty()) {
         return;
      }

      JsonNode cycles = current.get("cycles");

      if(cycles == null || !cycles.isArray() || cycles.isEmpty()) {
         return;
      }

      List<String> available = new ArrayList<>();

      for(JsonNode cycle : cycles) {
         available.add(cycle.asText());
      }

      if(!available.contains(defaultCycle.asText())) {
         throw new IllegalArgumentException(
            label + ".spec.defaultCycle: \"" + defaultCycle.asText() + "\" is not one of the " +
            "available data cycles (" + String.join(", ", available) + "). Note that the default " +
            "cycle is stored as a single deployment-global property while the available cycles " +
            "are per-organization, so a cycle that exists in one organization will not resolve " +
            "for the others.");
      }
   }

   /**
    * Refuses a secret-classified field in a write spec.
    *
    * <p>Called for every sub-model, not just {@code email} -- the set is empty for the other five,
    * so the loop is a no-op. Guarding the call with a sub-model test is what let {@code share}'s
    * webhook URLs stay writable in the presentation area after the properties path stopped writing
    * them (Bug #76170).
    *
    * <p>Refusing here is what keeps {@code AdminPropertyCatalog}'s guidance for these four names
    * ("admin-chat will not set it ... Configure it through Enterprise Manager instead") true of
    * the whole admin-chat surface rather than of one endpoint. That string is instruction text
    * handed to an agent, so a sibling verb that accepted these values would not merely leave a
    * second way in -- it would make the guidance a false statement.
    */
   private static void requireNoSecretFields(String label, GeneralSubModel subModel, JsonNode spec) {
      for(String secret : subModel.secretFields()) {
         if(spec.has(secret)) {
            throw new IllegalArgumentException(
               label + ".spec." + secret + ": secret-classified credentials cannot be set through " +
               "admin-chat -- this caller relays responses off-host, and the same value is " +
               "withheld on the generic properties path for the same reason. Change it through " +
               "Enterprise Manager instead.");
         }
      }
   }

   /**
    * Under cloud secrets, refuses the {@code email} fields whose stored value is a reference to a
    * secret rather than the secret itself.
    *
    * <p>{@code EmailSettingsService} stores {@code smtpSecretId} into {@code mail.smtp.pass} when
    * {@code Tool.isCloudSecrets()} is on. admin-chat can only write the literal value it was
    * given, which nothing downstream could resolve as a reference -- the same refusal
    * {@code AdminPropertyCatalog.cloudSecretsRefusal} states for the property path, restated here
    * so the two surfaces agree.
    *
    * <p>The credential fields themselves are already refused unconditionally by
    * {@link #requireNoSecretFields}; that is what guarantees a merged model can never carry a
    * caller-supplied {@code smtpPassword} into the secret-reference slot, since the merged value
    * for those fields is always the one just read back from the service.
    */
   private static void requireSecretIdNotSet(String label, JsonNode spec) {
      if(!spec.has("smtpSecretId")) {
         return;
      }

      if(Tool.isCloudSecrets()) {
         throw new IllegalArgumentException(
            label + ".spec.smtpSecretId: this deployment uses cloud secrets, so a value stored " +
            "here is resolved as a reference to a secret rather than used directly. admin-chat " +
            "will not set it -- it can only write the literal value it was given, which nothing " +
            "downstream could resolve. Configure it through Enterprise Manager instead.");
      }

      // The non-cloud-secrets branch is refused for a different reason, and refusing it matters
      // just as much: EmailSettingsService reads smtpSecretId ONLY inside its own
      // Tool.isCloudSecrets() branch, and getModel leaves the field null off cloud secrets. A
      // value sent here would therefore be a perfectly silent no-op -- the plan would show it
      // changing, the write would succeed, and the read-back would return null, which the apply
      // service can only report as a failed verification that rolls the whole changeset back.
      // Same class as secretIdVisible/fromAddressEnabled; it just has two distinct causes.
      throw new IllegalArgumentException(
         label + ".spec.smtpSecretId: cannot be set on this deployment -- it names a secret in a " +
         "cloud secrets manager, and this deployment does not use cloud secrets, so " +
         "EmailSettingsService never reads or writes the field. Set the credential itself through " +
         "Enterprise Manager instead.");
   }

   /**
    * Refuses a {@code performance} data-cache value the server would silently clamp.
    *
    * <p>{@code QueryCacheSettings.parseLimit}/{@code parseTimeout} clamp to
    * {@code [0, Integer.MAX_VALUE]} and {@code [0, Long.MAX_VALUE]} respectively, so a negative
    * value written here reads back as {@code 0}. Unlike {@code cache.directory}'s
    * {@code $(sree.home)} rewrite -- a normalization of a perfectly legitimate input, which is why
    * that one is simulated rather than refused -- a negative cache size is not a legitimate input
    * at all. Refusing it names the real problem, where simulating would silently convert a caller
    * who meant "unlimited" into one who asked for "disabled".
    */
   private static void requireInRangeCacheSettings(String label, JsonNode spec) {
      requireNonNegative(label, spec, "dataCacheSize", Integer.MAX_VALUE,
                         "QueryCacheSettings stores the cache limit as an int");
      // No upper bound for the timeout: parseTimeout clamps only at 0, and a long cannot exceed
      // Long.MAX_VALUE, so an upper check would be unreachable.
      requireNonNegative(label, spec, "dataCacheTimeout", Long.MAX_VALUE, null);
   }

   private static void requireNonNegative(String label, JsonNode spec, String field, long max,
                                          String maxReason)
   {
      JsonNode value = spec.get(field);

      if(value == null || value.isNull()) {
         return;
      }

      // Textual as well as numeric: Jackson coerces "-1" to a long just as happily as -1, so a
      // string-encoded number would otherwise skip this check, be written, and come back clamped.
      long number;

      if(value.canConvertToLong()) {
         number = value.asLong();
      }
      else if(value.isTextual()) {
         try {
            number = Long.parseLong(value.asText().trim());
         }
         catch(NumberFormatException e) {
            // Not a number at all -- toModel refuses it with a better message than this could.
            return;
         }
      }
      else {
         return;
      }

      if(number < 0) {
         throw new IllegalArgumentException(
            label + ".spec." + field + ": must not be negative -- the server clamps this value to " +
            "0 or above on read, so " + number + " would be stored, read back as 0, and reported " +
            "as a failed write rather than applied. If you meant \"no limit\", say so explicitly " +
            "rather than using a negative sentinel.");
      }

      if(number > max) {
         throw new IllegalArgumentException(
            label + ".spec." + field + ": must not exceed " + max +
            (maxReason == null ? "" : " (" + maxReason + ")") + " -- the server clamps above that, " +
            "so " + number + " would be read back as " + max + " and reported as a failed write.");
      }
   }

   /**
    * Refuses an {@code email} field that the effective {@code smtpAuthentication} would not write.
    *
    * <p>{@code EmailSettingsService.setModel} persists most of the mail model conditionally: the
    * user/password pair only under {@code smtpAuth}, the OAuth client and token fields only under
    * {@code saslXOauth2} or {@code googleAuth}, and the authorization/token URIs, scopes and flags
    * only under {@code saslXOauth2}. Everything else is dropped on the floor.
    *
    * <p>Without this check such a field is a perfectly silent no-op: the merge keeps it, the model
    * accepts it, the plan shows it changing, the write succeeds, and the read-back then returns
    * the old value -- which the apply service would report as a failed verification and roll the
    * whole changeset back over. A caller would see an unexplained rollback rather than the real
    * problem, which is that the field does not apply under the auth type in force.
    *
    * <p>"Effective" means the merged value: a spec that sets {@code smtpAuthentication} and its
    * matching fields in one change is valid, and must be, since that is the only way to switch
    * auth types.
    */
   private static void requireEmailFieldsMatchAuthType(String label, JsonNode spec, JsonNode merged) {
      SMTPAuthType effective = effectiveAuthType(merged);

      for(Map.Entry<String, Set<SMTPAuthType>> rule : EMAIL_AUTH_TYPE_FIELDS.entrySet()) {
         if(spec.has(rule.getKey()) && !rule.getValue().contains(effective)) {
            throw new IllegalArgumentException(
               label + ".spec." + rule.getKey() + ": is not written when smtpAuthentication is \"" +
               effective.name() + "\" -- EmailSettingsService persists it only for " +
               authTypeList(rule.getValue()) + ". Set smtpAuthentication in the same change if " +
               "you are switching auth types, or drop this field; leaving it in would look like " +
               "it applied when it did not.");
         }
      }
   }

   /**
    * Refuses an {@code email} write that would destroy a cloud-secrets reference.
    *
    * <p><b>The hazard is in the product, not in this area, but this area can trigger it.</b> Under
    * cloud secrets {@code SreeEnv.getPassword} does not return what is stored -- it calls
    * {@code Tool.loadCredentials} and returns the <i>resolved</i> secret -- while
    * {@code SreeEnv.setPassword} skips encryption and stores its argument <i>literally</i>. For
    * {@code mail.smtp.pass} that asymmetry is handled: {@code EmailSettingsService.getModel} reads
    * the raw property into {@code smtpSecretId} and {@code setModel} writes {@code smtpSecretId}
    * back, so the reference round-trips. {@code mail.smtp.clientSecret},
    * {@code mail.smtp.accessToken} and {@code mail.smtp.refreshToken} have no such second field:
    * they are read resolved and written literally.
    *
    * <p>So on a cloud-secrets deployment using {@code SASL_XOAUTH2} or {@code GOOGLE_AUTH}, ANY
    * write to this sub-model -- {@code setModel} rewrites that whole credential group
    * unconditionally for those auth types, even when only {@code smtpHost} changed -- replaces
    * three secrets-manager references with their plaintext values. Rollback cannot repair it: the
    * captured pre-apply model holds the same resolved plaintext, so writing it back stores the
    * plaintext a second time. The reference is gone for good.
    *
    * <p>Refused rather than worked around, because there is no value this area could send that
    * would preserve the references. {@code NONE} and {@code SMTP_AUTH} are unaffected and stay
    * writable: neither auth type causes {@code setModel} to touch those three properties.
    */
   private static void requireNoCloudSecretClobber(String label, JsonNode merged) {
      if(!Tool.isCloudSecrets()) {
         return;
      }

      SMTPAuthType effective = effectiveAuthType(merged);

      if(effective != SMTPAuthType.SASL_XOAUTH2 && effective != SMTPAuthType.GOOGLE_AUTH) {
         return;
      }

      throw new IllegalArgumentException(
         label + ".subModel: email settings cannot be changed through admin-chat on this " +
         "deployment, because it uses cloud secrets and the mail configuration is set to \"" +
         effective.name() + "\". EmailSettingsService rewrites mail.smtp.clientSecret, " +
         "mail.smtp.accessToken and mail.smtp.refreshToken on every write under that auth type, " +
         "and under cloud secrets it reads those three already resolved to their secret values " +
         "but stores them literally -- so any change here, even one that only touches the SMTP " +
         "host, would overwrite three secrets-manager references with plaintext, irreversibly. " +
         "Use Enterprise Manager for email settings on this deployment.");
   }

   /**
    * Refuses an {@code smtpAuthentication} value that is not one of the four auth types.
    *
    * <p>Needed before {@link #requireEmailFieldsMatchAuthType} runs, because
    * {@code SMTPAuthType.forValue} maps anything it does not recognize to {@code NONE}. Without
    * this, {@code {"smtpAuthentication": "smtp_auth", "smtpUser": "bob"}} is reported as
    * "smtpUser is not written when smtpAuthentication is NONE" -- true of the value that was
    * parsed, but not the caller's actual mistake, which is a misspelled auth type.
    */
   private static void requireKnownAuthType(String label, JsonNode spec) {
      JsonNode node = spec.get("smtpAuthentication");

      if(node == null || node.isNull()) {
         return;
      }

      String text = node.isTextual() ? node.asText() : null;

      for(SMTPAuthType type : SMTPAuthType.values()) {
         if(type.name().equals(text)) {
            return;
         }
      }

      throw new IllegalArgumentException(
         label + ".spec.smtpAuthentication: " +
         (text == null ? node.toString() : "\"" + text + "\"") +
         " is not a recognized authentication type. Use one of the enum names: " +
         Arrays.stream(SMTPAuthType.values()).map(SMTPAuthType::name)
            .collect(Collectors.joining(", ")) +
         ". Note these are the constant names, not the lowercase values stored in mail.smtp.auth.");
   }

   /**
    * Resolves the auth type from the merged JSON model.
    *
    * <p><b>The enum NAME is the JSON form here, not {@code value()}.</b> {@code SMTPAuthType}
    * carries no {@code @JsonValue} or {@code @JsonCreator}, so Jackson serializes and deserializes
    * it by constant name ({@code "SASL_XOAUTH2"}), while {@code EmailSettingsService} separately
    * stores {@code value()} ({@code "saslXOauth2"}) in the {@code mail.smtp.auth} property. Reading
    * this node with {@code SMTPAuthType.forValue} alone would therefore match nothing and silently
    * report every auth type as {@code NONE} -- which would turn
    * {@link #requireEmailFieldsMatchAuthType} into a check that refuses every conditional field
    * regardless of the real setting.
    *
    * <p>Only the name form is accepted, and {@link #requireKnownAuthType} has already refused
    * anything else by the time this runs. An earlier version fell back to
    * {@code SMTPAuthType.forValue} to be tolerant of the {@code value()} spelling, which was
    * pointless -- {@link GeneralJson#toModel} rejects that spelling in {@link #resolveEntries}
    * anyway, since the enum has no {@code @JsonCreator} -- and actively harmful, because
    * {@code forValue} maps every
    * unrecognized string to {@code NONE}, turning a misspelled auth type into a confusing
    * complaint about some other field.
    */
   private static SMTPAuthType effectiveAuthType(JsonNode merged) {
      JsonNode node = merged.get("smtpAuthentication");

      if(node == null || node.isNull() || !node.isTextual()) {
         return SMTPAuthType.NONE;
      }

      String text = node.asText();

      for(SMTPAuthType type : SMTPAuthType.values()) {
         if(type.name().equals(text)) {
            return type;
         }
      }

      return SMTPAuthType.NONE;
   }

   /** Names the auth types in the form a caller would send, i.e. the enum name -- see
    * {@link #effectiveAuthType} for why that is not {@code value()}. */
   private static String authTypeList(Set<SMTPAuthType> types) {
      List<String> values = new ArrayList<>();

      for(SMTPAuthType type : SMTPAuthType.values()) {
         if(types.contains(type)) {
            values.add("\"" + type.name() + "\"");
         }
      }

      return String.join(" or ", values);
   }

   /**
    * Reproduces the {@code $(sree.home)} rewrite {@code CacheSettingsService.getModel} performs, so
    * the plan's projected value is what a read would actually return rather than what was sent.
    *
    * <p>Without this, a caller who sets {@code directory} to an absolute path under the StyleBI
    * home gets a plan whose projected value never matches the subsequent read-back, and the apply
    * service rolls a perfectly correct write back over as a failed verification -- the exact shape
    * of Bug #76729 in the presentation area, which is why that area grew
    * {@code simulateLookAndFeelReadback}.
    *
    * <p><b>This deliberately reproduces a flaw in the product rather than correcting it.</b>
    * {@code getModel} tests the path with {@code contains} but then substrings from index zero by
    * the home directory's length, so a path that merely mentions the home directory somewhere in
    * the middle has its own first characters chopped off instead. That is wrong, but this method's
    * job is to predict what the server will return, and a simulation that quietly behaved better
    * than the thing it simulates would resurrect the false-mismatch bug for exactly the paths the
    * product mishandles. If the underlying substitution is ever fixed, fix it there and update
    * this method to match.
    *
    * <p>Applied to the projection only, never to the object that is written.
    */
   static JsonNode simulateCacheReadback(JsonNode merged) {
      JsonNode directory = merged.get("directory");

      if(directory == null || !directory.isTextual()) {
         return merged;
      }

      String home = SreeEnv.getProperty("sree.home");
      String value = directory.asText();

      if(home == null || home.isEmpty() || !value.contains(home)) {
         return merged;
      }

      ObjectNode copy = merged.deepCopy();
      copy.put("directory", "$(sree.home)" + value.substring(home.length()));
      return copy;
   }

   /**
    * Reproduces the ordering {@code LocalizationSettingsService.getModel} imposes, so the plan's
    * projected value is what a read would actually return rather than the order the caller sent.
    *
    * <p>{@code setModel} writes {@code locale.available} in the caller's own order, but
    * {@code getModel} splits that property and calls {@code Arrays.sort} before rebuilding the
    * list. So the natural, correct edit -- read the locales, append one, send the result -- writes
    * {@code [en_US, de_DE]} and reads back {@code [de_DE, en_US]}. Without this simulation the
    * apply service sees a value that "did not read back as written" and rolls a perfectly good
    * write back over, which is the same false-mismatch shape as bug #76729 and as
    * {@link #simulateCacheReadback}'s own case.
    *
    * <p>Sorted on {@code language_country}, the exact string {@code getModel} sorts, not on the
    * label -- the label is a value of the entry, not part of its identity, and sorting on it would
    * reproduce a different order than the server's.
    *
    * <p>Applied to the projection only, never to the object that is written.
    */
   static JsonNode simulateLocalizationReadback(JsonNode merged) {
      JsonNode locales = merged.get("locales");

      if(locales == null || !locales.isArray() || locales.size() < 2) {
         return merged;
      }

      List<JsonNode> sorted = new ArrayList<>();
      locales.forEach(sorted::add);
      sorted.sort(Comparator.comparing(GeneralChangePlanService::localeKey));

      ObjectNode copy = merged.deepCopy();
      ArrayNode array = copy.putArray("locales");
      sorted.forEach(array::add);
      return copy;
   }

   /** Package-visible so {@link GeneralChangesetApplyService} can compute the identical
    * before/after projection when verifying a write's read-back, instead of re-deriving its own.
    *
    * <p><b>The masking has a deliberate consequence for {@link #hash}.</b> Because the plan's
    * before/after values are the masked projections, every {@code email} credential hashes as the
    * same {@code ********} whatever it holds -- so a concurrent change to one of the four secrets
    * between preview and apply does NOT move the plan hash. That is sound rather than a hole: apply
    * re-reads the sub-model and merges the caller's spec onto the fresh value, and the secrets can
    * never appear in a spec ({@link #requireNoSecretFields}), so the newer credential is carried
    * through rather than overwritten. It does mean the hash cannot be relied on to detect a
    * credential change, which the tool description states explicitly. */
   static String projectedValue(GeneralSubModel subModel, JsonNode node) {
      return GeneralJson.writeString(GeneralJson.maskSecrets(subModel, node));
   }

   // ---------------------------------------------------------------- hash

   /** SHA-256 over the canonical plan, same contract as every other area's own {@code hash}.
    *
    * <p>Deliberately does NOT take {@code task}: the free-text description is never canonicalized,
    * so two equally valid paraphrases of the same intent over identical changes would otherwise
    * hash differently and trip a false mismatch between preview and apply. {@code task} is
    * write-only downstream -- it becomes an audit label that is never read back or compared -- so
    * excluding it does not weaken the gate against a changed plan. */
   static String hash(List<PlanChange> changes) {
      StringBuilder canonical = new StringBuilder();

      for(PlanChange change : changes) {
         canonical.append(change.property()).append(SEP)
            .append(canonicalValue(change.orgId())).append(SEP)
            .append(canonicalValue(change.currentValue())).append(SEP)
            .append(canonicalValue(change.proposedValue())).append(SEP)
            .append(change.risk()).append(SEP)
            .append(change.snapshotScope()).append(SEP);
      }

      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash a general change plan", e);
      }
   }

   private static String canonicalValue(String value) {
      return value == null ? NULL_MARKER : value;
   }

   /**
    * The {@code email} fields {@code EmailSettingsService.setModel} writes only under particular
    * auth types, mapped to the types that write them. Fields written unconditionally
    * ({@code smtpHost}, {@code ssl}, {@code tls}, {@code jndiUrl}, {@code fromAddress}, the two
    * subject formats, {@code historyEnabled}) are absent, as are the four credential fields, which
    * are refused outright by {@link #requireNoSecretFields} before this runs.
    */
   private static final Map<String, Set<SMTPAuthType>> EMAIL_AUTH_TYPE_FIELDS = Map.of(
      "smtpUser", Set.of(SMTPAuthType.SMTP_AUTH, SMTPAuthType.SASL_XOAUTH2,
                         SMTPAuthType.GOOGLE_AUTH),
      "smtpClientId", Set.of(SMTPAuthType.SASL_XOAUTH2, SMTPAuthType.GOOGLE_AUTH),
      "tokenExpiration", Set.of(SMTPAuthType.SASL_XOAUTH2, SMTPAuthType.GOOGLE_AUTH),
      "smtpAuthUri", Set.of(SMTPAuthType.SASL_XOAUTH2),
      "smtpTokenUri", Set.of(SMTPAuthType.SASL_XOAUTH2),
      "smtpOAuthScopes", Set.of(SMTPAuthType.SASL_XOAUTH2),
      "smtpOAuthFlags", Set.of(SMTPAuthType.SASL_XOAUTH2));

   private static final char SEP = (char) 0x1f;
   private static final String NULL_MARKER = String.valueOf((char) 0x01);
   private final GeneralSettingsAccess access;

   /** One resolved change: the sub-model it targets, its current and ready-to-write proposed model
    * objects, and the {@link PlanChange} describing it -- returned by {@link #resolveEntries} so
    * {@link GeneralChangesetApplyService} can write {@link #proposedModel()} (and, on rollback,
    * write {@link #currentModel()} back) directly instead of re-deriving either from JSON. */
   record ResolvedChange(GeneralSubModel subModel, Object currentModel, Object proposedModel,
                         PlanChange planChange)
   {
   }
}
