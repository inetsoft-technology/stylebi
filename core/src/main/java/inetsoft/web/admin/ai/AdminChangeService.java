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

      try {
         before = SreeEnv.getProperty(req.getProperty());
         result.setBeforeValue(before);

         if(desired == null) {
            sideEffects.applyRemoveSideEffects(req.getProperty());
            SreeEnv.remove(req.getProperty());
         }
         else {
            SreeEnv.setProperty(req.getProperty(), desired);
         }

         SreeEnv.save();

         if(desired != null) {
            sideEffects.applyEditSideEffects(req.getProperty());
         }

         String after = SreeEnv.getProperty(req.getProperty());
         result.setAfterValue(after);
         status = Objects.equals(after, desired)
            ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      }
      catch(Exception ex) {
         error = ex.getMessage();
         status = AdminChangeRecord.STATUS_FAILED;

         try {
            result.setAfterValue(SreeEnv.getProperty(req.getProperty()));
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
      // reviewOutcome and userSessionID are intentionally left unpopulated in Plan 1;
      // they are reserved for the Plan 2 broker. (organizationId IS populated downstream,
      // by DefaultAudit, not left blank like these two.)
      record.setUserName(principal == null ? null : principal.getName());
      record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
      record.setServerHostName(Tool.getHost());
      Audit.getInstance().auditAdminChange(record, principal);
   }

   private final PropertyChangeSideEffects sideEffects;
}
