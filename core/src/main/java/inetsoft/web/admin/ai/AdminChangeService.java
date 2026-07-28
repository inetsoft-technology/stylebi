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
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Objects;

@Service
public class AdminChangeService {
   public AdminChangeResult applyChange(AdminChangeRequest req, Principal principal) {
      AdminChangeResult result = new AdminChangeResult();
      result.setProperty(req.getProperty());

      String before = SreeEnv.getProperty(req.getProperty());
      result.setBeforeValue(before);

      String desired = req.getValue() == null ? null : req.getValue().trim();
      String error = null;
      String status;

      try {
         if(desired == null) {
            SreeEnv.remove(req.getProperty());
         }
         else {
            SreeEnv.setProperty(req.getProperty(), desired);
         }

         SreeEnv.save();
         String after = SreeEnv.getProperty(req.getProperty());
         result.setAfterValue(after);
         status = Objects.equals(after, desired)
            ? AdminChangeRecord.STATUS_VERIFIED : AdminChangeRecord.STATUS_FAILED;
      }
      catch(Exception ex) {
         error = ex.getMessage();
         result.setAfterValue(SreeEnv.getProperty(req.getProperty()));
         status = AdminChangeRecord.STATUS_FAILED;
      }

      result.setStatus(status);
      result.setError(error);
      writeAudit(req, principal, before, result.getAfterValue(), status, error);
      return result;
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
      record.setUserName(principal == null ? null : principal.getName());
      record.setActionTimestamp(new Timestamp(System.currentTimeMillis()));
      record.setServerHostName(Tool.getHost());
      Audit.getInstance().auditAdminChange(record, principal);
   }
}
