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

import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;
import inetsoft.web.admin.properties.PropertyChangeSideEffects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Objects;

@Service
public class AdminChangeService {
   @Autowired
   public AdminChangeService(PropertyChangeSideEffects sideEffects) {
      this.sideEffects = sideEffects;
   }

   public AdminChangeResult applyChange(AdminChangeRequest req, Principal principal) {
      requireNonBlank("transactionId", req.getTransactionId());
      requireNonBlank("property", req.getProperty());
      requireValidAction(req.getAction());

      AdminChangeResult result = new AdminChangeResult();
      result.setProperty(req.getProperty());

      // Unlike PropertiesController.editProperty (which treats a "" value as "keep the
      // current value"), admin-chat treats a trimmed "" as an explicit set-to-empty;
      // reset-to-default is expressed via a null value (the broker's stage_property_reset).
      String desired = req.getValue() == null ? null : req.getValue().trim();
      String before = null;
      String status = AdminChangeRecord.STATUS_FAILED;
      String error = null;
      AdminPropertyName name = null;

      try {
         // orgScope=false: SreeEnv.getProperty(name) is ORG-SCOPED and would resolve to
         // inetsoft.org.{currentOrg}.{name} when that key exists, while setProperty/remove write
         // the literal key. Mixing them reads one key and writes another, so the read-back verify
         // fails and the caller's rollback writes the override's value into the global key. The
         // three-argument form applies only fixPropertyNameCase, matching setProperty and remove.
         name = AdminPropertyName.parse(req.getProperty());
         before = SreeEnv.getProperty(name.key(), false, false);
         result.setBeforeValue(before);

         if(desired == null) {
            // Side-effect hooks match exact literals (e.g. "security.exposedefaultorgtoall"), so
            // they must receive the base name; an org-qualified name would silently never fire.
            sideEffects.applyPreRemoveSideEffects(name.baseName());
            SreeEnv.remove(name.key());
         }
         else {
            SreeEnv.setProperty(name.key(), desired);
         }

         SreeEnv.save();

         if(desired == null) {
            sideEffects.applyPostRemoveSideEffects(name.baseName());
         }
         else {
            sideEffects.applyEditSideEffects(name.baseName());
         }

         String after = SreeEnv.getProperty(name.key(), false, false);
         result.setAfterValue(after);
         status = Objects.equals(after, desired)
            ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      }
      catch(Exception ex) {
         error = ex.getMessage();
         status = AdminChangeRecord.STATUS_FAILED;

         try {
            // name is null only when AdminPropertyName.parse itself threw (e.g. a blank
            // property), in which case there is no resolved key and the raw, unparsed string
            // is the only fallback available.
            String key = name != null ? name.key() : req.getProperty();
            result.setAfterValue(SreeEnv.getProperty(key, false, false));
         }
         catch(Exception ignore) {
            // leave afterValue as-is; still audit below
         }
      }
      finally {
         result.setStatus(status);
         result.setError(error);
         writeAudit(req, principal, before, result.getAfterValue(), status, error);
      }

      return result;
   }

   /**
    * Rejects null/blank required fields up front, before any {@code SreeEnv}
    * mutation or audit write. A record with a blank transactionId or property
    * is unqueryable (getChangeset filters by transactionId), so persisting it
    * would silently orphan the audit entry -- fail loud instead.
    */
   private void requireNonBlank(String fieldName, String value) {
      if(value == null || value.trim().isEmpty()) {
         throw new IllegalArgumentException(fieldName + ": must not be blank");
      }
   }

   /**
    * {@code AdminChangeRecord.validate()} also requires a non-blank {@code action}
    * drawn from a known set of values. Fail loud on the same grounds as
    * {@link #requireNonBlank} -- before any {@code SreeEnv} mutation or audit write --
    * rather than letting an unqueryable/unrecognized action slip through.
    */
   private void requireValidAction(String action) {
      requireNonBlank("action", action);

      if(!AdminChangeRecord.ACTION_APPLY.equals(action) &&
         !AdminChangeRecord.ACTION_ROLLBACK.equals(action) &&
         !AdminChangeRecord.ACTION_RESTORE.equals(action))
      {
         throw new IllegalArgumentException("action: must be one of " +
            AdminChangeRecord.ACTION_APPLY + ", " + AdminChangeRecord.ACTION_ROLLBACK +
            ", " + AdminChangeRecord.ACTION_RESTORE);
      }
   }

   private void writeAudit(AdminChangeRequest req, Principal principal,
                           String before, String after, String status, String error)
   {
      AdminChangeRecord record = new AdminChangeRecord();
      record.setTransactionId(req.getTransactionId());
      record.setTaskDescription(req.getTaskDescription());
      record.setProperty(req.getProperty());
      record.setObjectType(ActionRecord.OBJECT_TYPE_EMPROPERTY);
      record.setBeforeValue(before);
      record.setAfterValue(after);
      record.setAction(req.getAction());
      record.setStatus(status);
      record.setRiskLevel(req.getRiskLevel());
      record.setSnapshotScope(req.getSnapshotScope());
      record.setBackupRef(req.getBackupRef());
      record.setReviewOutcome(req.getReviewOutcome());
      // userSessionID is intentionally left unpopulated; organizationId IS populated downstream by
      // DefaultAudit.
      record.setUserName(principal == null ? null : principal.getName());
      record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
      record.setServerHostName(Tool.getHost());
      Audit.getInstance().auditAdminChange(record, principal);
   }

   private final PropertyChangeSideEffects sideEffects;
}
