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
const LOG_OPTIONS = [
   {label: "DEBUG", value: "debug"},
   {label: "INFO", value: "info"},
   {label: "WARN", value: "warn"},
   {label: "ERROR", value: "error"},
   {label: "OFF", value: "off"}
];

/**
 * Common utility methods for properties.
 */
export namespace PropertiesTool {
   export const booleanProperties = [
      "adhoc.section.table", "anonymous.userdata.save", "bigDecimal.as.double",
      "dashboard.tabs.top", "db.caseSensitive",
      "empty.crosstab.convert", "enable.changePassword", "excel.vs.export.grid.show",
      "font.preload", "format.auto.downgrade", "fs.desktop", "graph.axis.inplot",
      "graph.script.action.support", "html.close.button", "html.image.scale",
      "htmlpresenter.fitline", "hyperlink.indicator", "image.antialias",
      "image.filtered", "image.png.alpha", "mail.ssl", "log.output.stderr",
      "mail.smtp.auth", "map.selection.enabled", "mv.detail.data",
      "mv.ignore.nestedSelection", "mv.double.precision", "mv.outer.moveUp", "mv.union.moveUp",
      "olap.table.originalContent", "output.null.to.zero", "pdf.compress.image",
      "pdf.compress.text", "pdf.embed.cmap", "pdf.embed.font", "pdf.generate.links",
      "pdf.map.symbols", "pdf.open.bookmark", "pdf.open.thumbnail", "pdf.output.ascii",
      "pdf.text.avoidoverlap", "portal.history.bar", "query.aggregate.merge",
      "query.cache.data", "query.variable.unique", "remove.outerJoin.only", "replet.cache.clean",
      "dashboard.mydashboard.disabled", "replet.optimize.network", "replet.streaming",
      "report.stringwidth.fontmetrics", "repository.audit.enabled",
      "repository.tree.sort.pathOnly", "rmi.localhost.ip", "rtf.hyperlink.indicator", "rtf.merge.text",
      "schedule.auto.down", "schedule.auto.start", "schedule.reload.auto", "schedule.test.hook",
      "scheduler.restart.auto", "security.cache", "sort.crosstab.aggregate",
      "sort.crosstab.dimension", "sree.find.backward", "string.compare.caseSensitive",
      "text.break.pages", "text.encoding.utf8", "vs.import.button", "vs.form.enabled",
      "schedule.time.12hours", "portal.customLogo.enabled", "calendar.dateCompare.enabled"
   ];

   export const boolean_options = [
      {label: "true", value: "true"},
      {label: "false", value: "false"}
   ];

   export const enumProperties = {
      "log.detail.level": {
         options: LOG_OPTIONS
      },
      "log.level.inetsoft.performance": {
         options: LOG_OPTIONS
      },
      "monitor.level": {
         options: [
            {label: "HIGH", value: "HIGH"},
            {label: "MEDIUM", value: "MEDIUM"},
            {label: "LOW", value: "LOW"},
            {label: "OFF", value: "OFF"}
         ]
      },
      "pdf.output.attachment": {
         options: [
            {label: "embed", value: "embed"},
            {label: "", value: ""}
         ]
      },
      "repository.tree.sort": {
         options: [
            {label: "Ascending", value: "Ascending"},
            {label: "None", value: "none"}
         ]
      },
      "schedule.options.emailDelivery": {
         options: [
            {label: "CHECKED", value: "CHECKED"},
            {label: "", value: ""}
         ]
      },
      "schedule.options.notificationEmail": {
         options: [
            {label: "CHECKED", value: "CHECKED"},
            {label: "", value: ""}
         ]
      },
      "schedule.options.printToPrinters": {
         options: [
            {label: "CHECKED", value: "CHECKED"},
            {label: "", value: ""}
         ]
      },
      "schedule.options.saveInArchive": {
         options: [
            {label: "CHECKED", value: "CHECKED"},
            {label: "", value: ""}
         ]
      },
      "schedule.options.saveToDisk": {
         options: [
            {label: "CHECKED", value: "CHECKED"},
            {label: "", value: ""}
         ]
      },
      "vpm.enforcement.policy": {
         options: [
            {label: "default", value: "default"},
            {label: "all", value: "all"},
            {label: "user", value: "user"}
         ]
      },
      "crosstab.dateTime.expandAll.level": {
         options: [
            {label: "Year", value: "Year"},
            {label: "QuarterOfYear", value: "QuarterOfYear"},
            {label: "MonthOfYear", value: "MonthOfYear"},
            {label: "DayOfMonth", value: "DayOfMonth"},
            {label: "HourOfDay", value: "HourOfDay"},
            {label: "MinuteOfHour", value: "MinuteOfHour"},
            {label: "SecondOfMinute", value: "SecondOfMinute"}
         ]
      },
      "week.start": {
         options: [
            {label: "Sunday", value: "Sunday"},
            {label: "Monday", value: "Monday"},
            {label: "Tuesday", value: "Tuesday"},
            {label: "Wednesday", value: "Wednesday"},
            {label: "Thursday", value: "Thursday"},
            {label: "Friday", value: "Friday"},
            {label: "Saturday", value: "Saturday"}
         ]
      }
   };

   export function getPropertyOptions(propertyName: string): any {
      if(booleanProperties.indexOf(propertyName) != -1) {
         return boolean_options;
      }

      let info = enumProperties[propertyName];
      return !!info ? info.options : [];
   }
}
