/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
var stressTestFlag = "___xx_this_is_the_reloading_page_xx__ DON't DELETE";
var rpt_cancelled = false;
var retryInterval = $(retryInterval);
var retryUrl = "$(uri)op=Page" + encodeURIComponent("$(upageID)") + "&action=" + encodeURIComponent("$(action)") +
 "&ID=" + encodeURIComponent("$(id)") + "&pn=" + encodeURIComponent("$(pn)") + "&mode=" + encodeURIComponent("$(mode)") +
      "&__applyclicked__=" + encodeURIComponent("$(__applyclicked__)") + "&wizard=" + encodeURIComponent("$(wizard)") + "&custom=" + encodeURIComponent("$(custom)");
var ajaxUrl = "$(ajaxuri)op=Page" + encodeURIComponent("$(upageID)") + "&action=" + encodeURIComponent("$(action)") + "&ID=" + encodeURIComponent("$(id)") +
 "&pn=" + encodeURIComponent("$(pn)") + "&mode=" + encodeURIComponent("$(mode)") + "&ajaxretry=true";

function stop() {
   rpt_cancelled = true;
}

function $(key)checkReport2() {
   if(!rpt_cancelled) {
      window.location.replace("$(uri)op=Page" + encodeURIComponent("$(upageID)") + "&action=" + encodeURIComponent("$(action)") +
         "&ID=" + encodeURIComponent("$(id)") + "&pn=" + encodeURIComponent("$(pn)") + "&mode=" + encodeURIComponent("$(mode)"));
   }
}

function $(key)checkReport() {
   if(rpt_cancelled) {
      return;
   }

   if(window.inDashboard) {
      window.setTimeout("$(key)checkReport2()", retryInterval);
      return;
   }


   var config = {"hideLoadingDiv":true, "isText":true};
   var ajax = new isii_AJAX(null, null, null, ajaxUrl, true, config);

   ajax.addFinishListener(function(msg) {
      if(rpt_cancelled) {
         return;
      }

      var ajax = msg.ajax;

      if(ajax.getError() != null) {
         alert(ajax.getError());
         return;
      }

      if(!msg.text || msg.text.indexOf("__xx_this_is_the_reloading_page_xx__") > 0) {
         window.setTimeout("$(key)checkReport()", retryInterval);
      }
      else {
         window.location.replace(retryUrl);
      }
   });

   try {
      ajax.send();
   }
   catch(ex) {
      alert(ex);
   }
}
window.f$(key)checkReport = $(key)checkReport;

window.setTimeout("window.f$(key)checkReport()", retryInterval);
