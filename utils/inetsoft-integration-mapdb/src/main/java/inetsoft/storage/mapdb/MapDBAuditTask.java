/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.storage.mapdb;

import inetsoft.enterprise.audit.*;
import inetsoft.util.audit.*;

import java.util.Comparator;
import java.util.Objects;

public abstract class MapDBAuditTask<T extends AuditRecord> {

   protected boolean applyFilter(T record, AuditQueryPredicate predicate) {
      if(predicate == null) {
         return true;
      }

      return switch(predicate) {
         case And p -> applyAnd(record, p);
         case Or p -> applyOr(record, p);
         case Between p -> applyBetween(record, p);
         case Equals p -> applyEquals(record, p);
         case Like p -> applyLike(record, p);
         case GreaterThan p -> applyGreaterThan(record, p);
         case GreaterThanOrEqual p -> applyGreaterThanOrEqual(record, p);
         case In p -> applyIn(record, p);
         case LessThan p -> applyLessThan(record, p);
         case LessThanOrEqual p -> applyLessThanOrEqual(record, p);
         default -> throw new IllegalArgumentException("Unknown predicate " + predicate);
      };
   }

   private boolean applyAnd(T record, And and) {
      return applyFilter(record, and.left()) && applyFilter(record, and.right());
   }

   private boolean applyOr(T record, Or or) {
      return applyFilter(record, or.left()) || applyFilter(record, or.right());
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private boolean applyBetween(T record, Between predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof Comparable comp) {
         int lower = comp.compareTo(predicate.lower());
         int upper = comp.compareTo(predicate.upper());
         return lower >= 0 && upper <= 0;
      }
      else {
         throw new IllegalArgumentException(
            "Value is not comparable: " + record.getClass().getSimpleName() +
               "." + predicate.field());
      }
   }

   private boolean applyEquals(T record, Equals predicate) {
      Object actual = getValue(record, predicate.field());
      return Objects.equals(actual, predicate.value());
   }

   private boolean applyLike(T record, Like predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof String str) {
         boolean startsWith = predicate.expression().endsWith("%");
         boolean endsWith = predicate.expression().startsWith("%");

         if(startsWith && endsWith) {
            return str.contains(predicate.expression()
                                   .substring(1, predicate.expression().length() - 1));
         }
         else if(startsWith) {
            return str.startsWith(predicate.expression()
                                     .substring(0, predicate.expression().length() - 1));
         }
         else if(endsWith) {
            return str.endsWith(predicate.expression().substring(1));
         }
         else {
            return str.equals(predicate.expression());
         }
      }
      else {
         throw new IllegalArgumentException(
            "Value is not a string: " + record.getClass().getSimpleName() +
            "." + predicate.field());
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private boolean applyGreaterThan(T record, GreaterThan predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof Comparable comp) {
         return comp.compareTo(predicate.value()) > 0;
      }
      else {
         throw new IllegalArgumentException(
            "Value is not comparable: " + record.getClass().getSimpleName() +
            "." + predicate.field());
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private boolean applyGreaterThanOrEqual(T record, GreaterThanOrEqual predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof Comparable comp) {
         return comp.compareTo(predicate.value()) >= 0;
      }
      else {
         throw new IllegalArgumentException(
            "Value is not comparable: " + record.getClass().getSimpleName() +
               "." + predicate.field());
      }
   }

   private boolean applyIn(T record, In predicate) {
      Object actual = getValue(record, predicate.field());

      if(predicate.values() == null || predicate.values().length == 0) {
         return actual == null;
      }

      for(Object value : predicate.values()) {
         if(Objects.equals(actual, value)) {
            return true;
         }
      }

      return false;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private boolean applyLessThan(T record, LessThan predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof Comparable comp) {
         return comp.compareTo(predicate.value()) < 0;
      }
      else {
         throw new IllegalArgumentException(
            "Value is not comparable: " + record.getClass().getSimpleName() +
               "." + predicate.field());
      }
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private boolean applyLessThanOrEqual(T record, LessThanOrEqual predicate) {
      Object actual = getValue(record, predicate.field());

      if(actual == null) {
         return false;
      }

      if(actual instanceof Comparable comp) {
         return comp.compareTo(predicate.value()) <= 0;
      }
      else {
         throw new IllegalArgumentException(
            "Value is not comparable: " + record.getClass().getSimpleName() +
               "." + predicate.field());
      }
   }

   protected Object getValue(T record, String field) {
      return switch(record) {
         case ActionRecord r -> getActionRecordValue(r, field);
         case BookmarkRecord r -> getBookmarkRecordValue(r, field);
         case ExecutionRecord r -> getExecutionRecordValue(r, field);
         case ExportRecord r -> getExportRecordValue(r, field);
         case IdentityInfoRecord r -> getIdentityInfoRecordValue(r, field);
         case QueryRecord r -> getQueryRecordValue(r, field);
         case SessionRecord r -> getSessionRecordValue(r, field);
         default -> throw new IllegalArgumentException("Unknown record: " + record);
      };
   }

   protected Comparator<T> createSort(Class<T> type, AuditQuerySort sort) {
      return sort.createComparator(type);
   }

   private Object getActionRecordValue(ActionRecord record, String field) {
      return switch(field) {
         case "userSessionID" -> record.getUserSessionID();
         case "actionName" -> record.getActionName();
         case "objectName" -> record.getObjectName();
         case "objectType" -> record.getObjectType();
         case "actionTimestamp" -> record.getActionTimestamp();
         case "actionStatus" -> record.getActionStatus();
         case "actionError" -> record.getActionError();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" -> record.getOrganizationId();
         case "resourceOrganization" -> record.getResourceOrganization();
         default -> throw new IllegalArgumentException("Unknown field: ActionRecord." + field);
      };
   }

   private Object getBookmarkRecordValue(BookmarkRecord record, String field) {
      return switch(field) {
         case "userName" -> record.getUserName();
         case "userRole" -> record.getUserRole();
         case "userActiveStatus" -> record.getUserActiveStatus();
         case "userEmail" -> record.getUserEmail();
         case "userLastLogin" -> record.getUserLastLogin();
         case "actionType" -> record.getActionType();
         case "actionExecTimestamp" -> record.getActionExecTimestamp();
         case "dashboardName" -> record.getDashboardName();
         case "dashboardAlias" -> record.getDashboardAlias();
         case "bookmarkName" -> record.getBookmarkName();
         case "bookmarkType" -> record.getBookmarkType();
         case "bookmarkReadOnly" -> record.getBookmarkReadOnly();
         case "bookmarkCreateDate" -> record.getBookmarkCreateDate();
         case "bookmarkLastUpdateDate" -> record.getBookmarkLastUpdateDate();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" ->  record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: BookmarkRecord." + field);
      };
   }

   private Object getExecutionRecordValue(ExecutionRecord record, String field) {
      return switch(field) {
         case "execSessionID" -> record.getExecSessionID();
         case "userSessionID" -> record.getUserSessionID();
         case "objectName" -> record.getObjectName();
         case "objectType" -> record.getObjectType();
         case "execType" -> record.getExecType();
         case "execTimestamp" -> record.getExecTimestamp();
         case "execStatus" -> record.getExecStatus();
         case "execError" -> record.getExecError();
         case "organizationId" -> record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: ExecutionRecord." + field);
      };
   }

   private Object getExportRecordValue(ExportRecord record, String field) {
      return switch(field) {
         case "userName" -> record.getUserName();
         case "objectName" -> record.getObjectName();
         case "objectType" -> record.getObjectType();
         case "exportType" -> record.getExportType();
         case "exportTimestamp" -> record.getExportTimestamp();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" -> record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: ExportRecord." + field);
      };
   }

   private Object getIdentityInfoRecordValue(IdentityInfoRecord record, String field) {
      return switch(field) {
         case "actionTimestamp" -> record.getActionTimestamp();
         case "actionType" -> record.getActionType();
         case "identityID" -> record.getIdentityID();
         case "identityName" -> record.getIdentityName();
         case "identityOrganization" -> record.getIdentityOrganization();
         case "identityType" -> record.getIdentityType();
         case "actionDesc" -> record.getActionDesc();
         case "identityState" -> record.getIdentityState();
         case "state" -> record.getState();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" -> record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: IdentityInfoRecord." + field);
      };
   }

   private Object getQueryRecordValue(QueryRecord record, String field) {
      return switch(field) {
         case "execSessionID" -> record.getExecSessionID();
         case "userSessionID" -> record.getUserSessionID();
         case "objectName" -> record.getObjectName();
         case "objectType" -> record.getObjectType();
         case "execType" -> record.getExecType();
         case "execTimestamp" -> record.getExecTimestamp();
         case "execStatus" -> record.getExecStatus();
         case "execError" -> record.getExecError();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" -> record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: QueryRecord." + field);
      };
   }

   private Object getSessionRecordValue(SessionRecord record, String field) {
      return switch(field) {
         case "opError" -> record.getOpError();
         case "opStatus" -> record.getOpStatus();
         case "opTimestamp" -> record.getOpTimestamp();
         case "opType" -> record.getOpType();
         case "userGroup" -> record.getUserGroup();
         case "userHost" -> record.getUserHost();
         case "userID" -> record.getUserID();
         case "userRole" -> record.getUserRole();
         case "userSessionID" -> record.getUserSessionID();
         case "logoffReason" -> record.getLogoffReason();
         case "serverHostName" -> record.getServerHostName();
         case "organizationId" -> record.getOrganizationId();
         default -> throw new IllegalArgumentException("Unknown field: SessionRecord." + field);
      };
   }
}
