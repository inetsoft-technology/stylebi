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
/**
 * Use to run the specific script on the current window.
 */
window.runScript = function(script) {
   try {
      eval(script);
   }
   catch(ex) {
      alert(ex);
   }
}

/**
 * insert item to the array..
 * @return new array.
 */
function insertItemToArray(array, item, index) {
   if((index == null) || (index < 0) || (index > array.length)) {
      index = array.length;
   }

   var newA = new Array(array.length + 1);

   for(var i = 0; i < index; i++) {
      newA[i] = array[i];
   }

   newA[index] = item;

   for(var i = index; i < array.length; i++) {
      newA[i + 1] = array[i];
   }

   return newA;
}

/**
 * Return the element represented by this id.
 */
function elementLocation(targetDocument, theId) {
   if(targetDocument == null) {
      targetDocument = document;
   }

   if(targetDocument.all && !targetDocument.getElementById) {
      return targetDocument.all[theId];
   }
   else {
      var candidate = targetDocument.getElementById(theId);

      if(!candidate) {
         candidate = (targetDocument.getElementsByName(theId))[0];
      }

      return candidate;
   }
}

/**
 * Returns the string with leading whitespace omitted.
 */
function lTrim(str) {
   var whitespace = new String(" \t\n\r");
   var s = new String(str);

   if (whitespace.indexOf(s.charAt(0)) != -1) {
      var j=0, i = s.length;

      while (j < i && whitespace.indexOf(s.charAt(j)) != -1) {
         j++;
      }

      s = s.substring(j, i);
   }

   // google chrome returns "object" for typeof new String()
   return s + "";
}

/**
 * Returns the string with trailing whitespace omitted.
 */
function rTrim(str) {
   var whitespace = new String(" \t\n\r");
   var s = new String(str);

   if(whitespace.indexOf(s.charAt(s.length-1)) != -1) {
      var i = s.length - 1;

      while (i >= 0 && whitespace.indexOf(s.charAt(i)) != -1) {
         i--;
      }

      s = s.substring(0, i+1);
   }

   // google chrome returns "object" for typeof new String()
   return s + "";
}

/**
 * Returns the string with leading and trailing whitespace omitted.
 */
function trim(str) {
   return rTrim(lTrim(str));
}

/**
 * delete item from the array.
 * @return new array.
 */
function deleteItemFromArray(array, index) {
   if((index < 0) || (index > array.length)) {
      return array;
   }
   var newA = new Array(array.length - 1);

   for(var i = 0; i < index; i++) {
      newA[i] = array[i];
   }

   for(var i = (index + 1); i < array.length; i++) {
      newA[i - 1] = array[i];
   }

   return newA;
}

function htmlEncode(str) {
   var charray=new Array("%","+","&",",","#");
   var chcodearray=new Array("%25","%2b","%26","%2c","%23");
   var newstr=str;
   for(var i = 0; i < charray.length; i++) {
      newstr=replaceChar(newstr,charray[i],chcodearray[i]);
   }
   return newstr;
}

function replaceChar(str, ch, chcode) {
   var newstr="";
   var sidx = -1;
   while(true) {
      sidx = str.indexOf(ch);
      if(sidx==-1) {
         newstr += str;
         break;
      }
      newstr += str.substring(0, sidx) + chcode;
      str = str.substring(sidx+1);
   }
   return newstr;
}

function showHiddenTree(node) {
   var divnode = eval(node);
   if(divnode.style.display=="none") {
      divnode.style.display="";
   }
   else {
      divnode.style.display="none";
   }
}

/**
 * Add an item at the end of the array.
 * This function act as Array.push() which is supported after IE 5.5.
 */
function pushItemToArray(arr, item) {
   var len = arr.length;
   arr[len] = item;
}

/**
 * Test if the input string is a valid int value.
 *
 * @param str the specified input string
 * @return true if is, false otherwise
 */
function isValidJavaInt(str){
   if(str == "NULL_VALUE") {
      return true;
   }

   if(isNaN(str)) {
      return false;
   }

   var val = parseInt(str);
   var str2 = val + "";

   if(str2 != str) {
      return false;
   }

   if(val < -2147483648 || val > 2147483647) {
      return false;
   }

   return true;
}

/**
 * Check if is a valid name.
 * @param name the specified name. Only Character, number, '$' and '_' is valid
 * @return true if is a valid name, false otherwise
 */
function isValidName(name, includeColon) {
   if(name.indexOf("\\") != -1) {
      return false;
   }

   if(name.indexOf("/") != -1) {
      return false;
   }

   if(includeColon && name.indexOf(":") != -1) {
      return false;
   }

   if(name.indexOf("*") != -1) {
      return false;
   }

   if(name.indexOf(" ") != -1) {
      return false;
   }

   if(name.indexOf("?") != -1) {
      return false;
   }

   if(name.indexOf("\"") != -1) {
      return false;
   }

   if(name.indexOf("<") != -1) {
      return false;
   }

   if(name.indexOf(">") != -1) {
      return false;
   }

   if(name.indexOf("|") != -1) {
      return false;
   }

   if(name.indexOf(".") != -1) {
      return false;
   }

   if(name.indexOf("'") != -1) {
      return false;
   }

   if(name.indexOf("&") != -1) {
      return false;
   }

   if(name.indexOf("%") != -1) {
      return false;
   }

   if(name.indexOf(",") != -1) {
      return false;
   }
   if(name.indexOf("`") != -1) {
      return false;
   }

   if(name.indexOf("~") != -1) {
      return false;
   }

   if(name.indexOf("!") != -1) {
      return false;
   }

   if(name.indexOf("@") != -1) {
      return false;
   }

   if(name.indexOf("#") != -1) {
      return false;
   }

   if(name.indexOf("=") != -1) {
      return false;
   }

   if(name.indexOf("-") != -1) {
      return false;
   }

   if(name.indexOf("+") != -1) {
      return false;
   }

   if(name.indexOf("(") != -1) {
      return false;
   }

   if(name.indexOf(")") != -1) {
      return false;
   }

   if(name.indexOf("{") != -1) {
      return false;
   }

   if(name.indexOf("}") != -1) {
      return false;
   }

   if(name.indexOf("[") != -1) {
      return false;
   }

   if(name.indexOf("]") != -1) {
      return false;
   }

   if(name.indexOf("^") != -1) {
      return false;
   }

   if(name.indexOf(";") != -1) {
      return false;
   }

   return true;
}

/**
 * Check if is a valid character.
 */
function isValidChar(code, start) {
   if(start && ((code >= "0") && (code <= "9"))) {
      return false;
   }

   return isValidName(code, true);
}

/**
 * Check if is a valid vs/ws name char.
 */
function isValidSheetNameChar(code) {
   return code != "%" && code != "^" && code != "\\" && code != "\"" &&
      code != "\'" && code != "/" && code != "<";
}

/**
 * Check if is an additional char.
 */
function isAdditionalChar(code, strict, sheetName, start) {
   if(strict) {
      return false;
   }

   // is digit?
   if(code >= "0" && code <= "9") {
      return true;
   }

   if(sheetName) {
      return !start && isValidSheetNameChar(code);
   }

   return !start && (code == " " || code == "$" || code == "-" || code == "&" ||
           code == "@" || code == '+' || code == '_');
}

/**
 * Check if is a valid name. A valid name should not be null or empty,
 * start with a letter, and any character should be a letter or a digit.
 *
 * @param name the specified name
 * @param strict <tt>true</tt> if check name strictly
 */
function isValidIdentifier(name, strict, inform, sheetName) {
   inform = inform == null ? true : inform;

   if(name == null || name.length == 0) {
      if(inform) {
         alert("$(designer.property.emptyNullError)#(inter)");
      }

      return false;
   }

   if(name.indexOf("]]>") != -1) {
      if(inform) {
         alert("$(designer.property.charSequenceError)#(inter)");
      }

      return false;
   }

   for(var i = 0; i < name.length; i++) {
      var code = name.charAt(i);

      // not starts with a letter?
      if(i == 0 && !isValidChar(code, true)) {
         if(isAdditionalChar(code, strict, sheetName, true)) {
            continue;
         }

         var msg = strict ?
            "$(The name)#(inter)" + " \"" + name + "\" " +
               "$(designer.property.startCharErrorSuffix)#(inter)" :
            "$(The name)#(inter)" + " \"" + name + "\" " +
               "$(designer.property.startCharDigitErrorSuffix)#(inter)";

         if(inform) {
            alert(msg);
         }

         return false;
      }
      // any character is not a letter or digit?
      else if(i > 0 && !isValidChar(code, false)) {
         if(isAdditionalChar(code, strict, sheetName)) {
            continue;
         }

         var msg = sheetName ?
            "$(The character)#(inter)" + " \"" + code + "\" " +
               "$(designer.property.invalidNameCharSuffix)#(inter)" :
            "$(designer.property.anyCharErrorPrefix)#(inter)" +
               " \"" + name + "\"!";

         if(inform) {
            alert(msg);
         }

         return false;
      }
   }

   return true;
}

/**
 * Test if the input string is a valid double value.
 *
 * @param str the specified input string
 * @return true if is, false otherwise
 */
function isValidJavaDouble(str){
   if(str) {
      var strs = str.split(",");

      for(var i = 0; i < strs.length; i++) {
         if(!isValidJavaDouble0(strs[i])) {
            return false;
         }
      }

      return true;
   }
   else {
      return isValidJavaDouble0(str);
   }
}

/**
 * Test if the input string is a valid double value.
 *
 * @param str the specified input string
 * @return true if is, false otherwise
 */
function isValidJavaDouble0(str){
   if(str.trim() == "") {
      return true;
   }

   if(str == "NULL_VALUE") {
      return true;
   }

   if(isNaN(str)) {
      return false;
   }

   var val = parseFloat(str);

   var str2 = val + "";

   if(parseFloat(str2) != str) {
      return false;
   }

   return true;
}

/**
 * Check the valid role name.
 */
function isValidRoleName(str, includenum) {
   if(str.indexOf("*") >= 0 || str.indexOf("\"") >= 0 ||
      str.indexOf("\\") >= 0 || str.indexOf("#") >= 0 ||
      str.indexOf("'") >= 0 || str.indexOf("<") >= 0 || str.indexOf(">") >= 0)
   {
      return false;
   }

   if(!includenum && !isValidChar(str.charAt(0), true)) {
      return false;
   }

   return true;
}

/**
 * Check the valid group name.
 */
function isValidGroupName(str) {
   if(str.indexOf("*") >= 0 || str.indexOf("\"") >= 0 ||
      str.indexOf("\\") >= 0 || str.indexOf("#") >= 0 ||
      str.indexOf("'") >= 0 || str.indexOf("<") >= 0 || str.indexOf(">") >= 0)
   {
      return false;
   }

   return true;
}

/**
 * Check the valid user name.
 */
function isValidUserName(str, silent, includenum) {
   if(isValidFileName(str, includenum)) {
      if(str != "anonymous") {
         return true;
      }

      if(!silent) {
         alert("$(common.sree.internal.invalidUserName)#(inter)");
      }
   }

   return false;
}

/**
 * Check the valid folder name.
 */
function isValidFolderName(str, silent) {
   // if str contains '[uiui]' or '[123]' it's not valid for file name
   if(str.indexOf("*") >= 0 || str.indexOf("[") >= 0 || str.indexOf("]") >= 0 ||
      str.indexOf(":") >= 0 || str.indexOf("\"") >= 0 || str.indexOf("<") >= 0 ||
      str.indexOf(">") >= 0 || str.indexOf("?") >= 0 || str.indexOf("|") >= 0 ||
      str.indexOf("\\") >= 0 || str.indexOf("#") >= 0 || str.indexOf(".") >= 0 ||
      str.indexOf("'") >= 0 || str.indexOf("%") >= 0 || str.indexOf("/") >= 0 ||
      str.indexOf(",") >= 0 || str.indexOf("&") >= 0 || str.indexOf("!") == 0)
   {
      if(!silent) {
         alert("$(common.sree.internal.invalidCharInFolderName)#(inter)");
      }

      return false;
   }

   return true;
}

/**
 * Check the valid file name.
 */
function isValidFileName(str, includenum) {
   // if str contains '[uiui]' or '[123]' it's not valid for file name
   if(str.indexOf("*") >= 0 || str.indexOf("[") >= 0 ||
      str.indexOf(":") >= 0 || str.indexOf("\"") >= 0 ||
      str.indexOf("<") >= 0 || str.indexOf(">") >= 0 ||
      str.indexOf("?") >= 0 || str.indexOf("|") >= 0 ||
      str.indexOf("\\") >= 0 || str.indexOf("#") >= 0 ||
      str.indexOf("'") >= 0 || str.indexOf("%") >= 0 || str.indexOf("/") >= 0 ||
      str.indexOf(",") >= 0 || str.indexOf("&") >= 0 || str.indexOf("]") >= 0 ||
      str.indexOf("^") >= 0)
   {
      return false;
   }

   if(!includenum && !isValidChar(str.charAt(0), true)) {
      return false;
   }

   return true;
}

/**
 * Check the valid report name.
 */
function isValidReportName(str) {
   if(!isValidIdentifier(str, false, true)) {
      return false;
   }

   return true;
}

/**
 * Check the valid vs/ws name.
 */
function isValidSheetName(str) {
   if(!isValidIdentifier(str, false, true, true)) {
      return false;
   }

   return true;
}

/**
 * Check the valid report name.
 */
function isValidTaskName(str) {
   return /^[A-Za-zÀ-ÿ0-9$ &?#!*`;>|~={}()@+_:.\-\[\]\u4e00-\u9fa5]+$/.test(str);
}

/**
 * Check the valid wizard title.
 */
function isValidWizardTitle(str) {
   if(str.indexOf("&") >= 0 || str.indexOf("<") >= 0 || str.indexOf(">") >= 0 ||
      str.indexOf("'") >= 0 || str.indexOf(":") >= 0 || str.indexOf("]") >= 0 ||
      str.indexOf("[") >= 0 || str.indexOf("\"") >= 0 ||
      str.indexOf("\\") >= 0 || str.indexOf("/") >= 0 || str.indexOf(".") >= 0)
   {
      return false;
   }

   return true;
}

/**
 * Check the valid report name.
 */
function isValidParameterName(str) {
   return isValidReportName(str);
}

/**
 * Check the valid report name.
 */
function isValidParameterString(str) {
   // the parameter value was input by customer
   // as the report parameter, we could not limit it's content.
   return true;
}

function URLDecode(val) {
  return val == null ? null : unescape(val.replace('+', ' '));
}

function postURLWithParameters(doc, url, params) {
   var context = "<html>";
   context += "<head></head>";
   context += "<script language='javascript'>";
   context += "function submitit() {";
   context += "  form1.submit();";
   context += "}";
   context += "</script>";
   context += "<body>";
   context += "<form action='" + url + "' name='form1' method='post'>";

   if(params) {
      for(var i = 0; i < params.length; i += 2) {
         if(params[i] == 'req' || params[i] == 'name' || params[i] == 'submit')
         {
            continue;
         }

         var pname = params[i];
         var idx = pname.lastIndexOf("[");

         if(idx >= 0) {
            pname = pname.substring(0, idx);
         }

         if((typeof params[i + 1]) == 'object') {
            for(var j = 0; j < params[i + 1].length; j++) {
               context += "<input type=hidden name='" + URLDecode(pname) + "' "
                  + "value='" + URLDecode(params[i + 1][j]) + "'/>";
            }
         }
         else {
            // if parameter name is para or
            // start with para_, IE encount problem. So we need to encode
            // the parameter name in this situation, and decode when use it.
            var encodeName = URLDecode(pname);

            if(encodeName == "para" || encodeName.indexOf("para_") == 0) {
               encodeName = "^_^" + encodeName;
            }

            context += "<input type=hidden name='" + encodeName + "' " +
               "value='" + URLDecode(params[i + 1]) + "'/>";
         }
      }
   }

   context += "</form>";

   context += "</body><script language='javascript'>";
   context += "var timer = setInterval(function() {";
   context += "   try {";
   context += "      submitit();";
   context += "      clearInterval(timer);";
   context += "   }";
   context += "   catch(ex) {;";
   context += "   };}, 1000)";
   context += "</script></html>";

   doc.writeln(context);
   doc.close();
}

/**
 * Get the root window.
 */
function getRoot(win, dnd) {
   if(win.rootwin) {
      return win.rootwin;
   }

   var res = null;
   var win2 = win;
   var wins = new Array();

   while(true) {
      if(win2.isRoot) {
         res = win2;
         break;
      }

      wins.push(win2);

      try {
         if(win2.parent && win2.parent != win2) {
            win2 = win2.parent;
            win2.isRoot;
         }
         else {
            break;
         }
      }
      catch(ex) {
         //cross domain
         break;
      }
   }

   for(var i = wins.length - 1; res == null && i >= 0; i--) {
      try {
         if(wins[i].runScript) {
            res = wins[i];
            break;
         }
      }
      catch(ex) {
      }
   }

   if(res == null) {
      alert("Integration error. Root window not found!");
   }

   win.rootwin = res;

   return res;
}

/**
 * Check if this window contains frame sets.
 */
function containsFrameset(win) {
   var framesets = win.document.getElementsByTagName("FRAMESET");

   if(framesets != null && framesets.length > 0) {
      return true;
   }

   return false;
}

var _root = getRoot(self);

if(!_root._objectCounter) {
   try {
      _root._objectCounter = 0;
   }
   catch(e) {
      self._objectCounter = 0;
   }
}

if(!_root._isinit) {
   try {
      _root.is_opera = /opera/i.test(navigator.userAgent);
      _root.is_edge= /Edge/i.test(navigator.userAgent);
      _root.is_ie = (/msie/i.test(navigator.userAgent) && !_root.is_opera);
      _root.is_ie5 = (_root.is_ie && /msie 5\.0/i.test(navigator.userAgent));
      _root.is_ie6 = (_root.is_ie && /msie 6\.0/i.test(navigator.userAgent));
      _root.is_ie7 = (_root.is_ie && /msie 7\.0/i.test(navigator.userAgent));
      _root.is_ie8 = (_root.is_ie && /msie 8\.0/i.test(navigator.userAgent));
      _root.is_ie9 = (_root.is_ie && /msie 9\.0/i.test(navigator.userAgent));
      _root.is_ie10 = (_root.is_ie && /msie 10\.0/i.test(navigator.userAgent));
      _root.is_ie11 = /trident\/7\.0/i.test(navigator.userAgent);
      _root.is_ie9minus = (_root.is_ie && /msie ([0-9])\.0/i.test(navigator.userAgent));
      _root.is_mac_ie = (/msie.*mac/i.test(navigator.userAgent) && !_root.is_opera);
      _root.is_mac = (/mac/i.test(navigator.userAgent) && !_root.is_opera);
      _root.is_khtml = /Konqueror|Safari|KHTML/i.test(navigator.userAgent);
      _root.is_konqueror = /Konqueror/i.test(navigator.userAgent);
      _root.is_gecko = /Gecko/i.test(navigator.userAgent);
      _root.is_firefox = /Firefox/i.test(navigator.userAgent);
      _root.is_chrome = (/Chrome/i.test(navigator.userAgent) && !_root.is_edge);
      _root.is_ios = /Safari/i.test(navigator.userAgent) &&
         (/iPad;/i.test(navigator.userAgent) ||
          /iPod;/i.test(navigator.userAgent) ||
          /iPhone;/i.test(navigator.userAgent));
      // this function is taken from http://detectmobilebrowsers.com (public domain)
      _root.is_mobile = (function(a){return (/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(a)||/1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(a.substr(0,4)))})(navigator.userAgent||navigator.vendor||window.opera);

      _root._isinit = true;
   }
   catch(e) {
      alert(e);
      self.is_opera = /opera/i.test(navigator.userAgent);
      self.is_ie = (/msie/i.test(navigator.userAgent) && !self.is_opera);
      self.is_ie5 = (self.is_ie && /msie 5\.0/i.test(navigator.userAgent));
      self.is_mac_ie = (/msie.*mac/i.test(navigator.userAgent) && !self.is_opera);
      self.is_mac = (/mac/i.test(navigator.userAgent) && !self.is_opera);
      self.is_khtml = /Konqueror|Safari|KHTML/i.test(navigator.userAgent);
      self.is_konqueror = /Konqueror/i.test(navigator.userAgent);
      self.is_gecko = /Gecko/i.test(navigator.userAgent);
      self.is_firefox = /Firefox/i.test(navigator.userAgent);
      self.is_chrome = /Chrome/i.test(navigator.userAgent);
      self.is_ios = /Safari/i.test(navigator.userAgent) &&
         (/iPad;/i.test(navigator.userAgent) ||
          /iPod;/i.test(navigator.userAgent) ||
          /iPhone;/i.test(navigator.userAgent));

      self._isinit = true;
   }
}

/**
 * Wrap the javascript event.
 */
IEvent = function(evt, win) {
   win = win ? win :window;
   var ievent = (evt) ? evt : ((win.event) ? win.event : null);
   this.event = ievent;
   this.src = ievent ?
      (ievent.target ? ievent.target : ievent.srcElement) : null;
   this.pageX = ievent ? (ievent.pageX ? ievent.pageX :
      (ievent.clientX + win.document.body.scrollLeft)) : null;
   this.pageY = ievent ? (ievent.pageY ? ievent.pageY :
      (ievent.clientY + win.document.body.scrollTop)) : null;
   this.mouseButton = ievent ?
      (ievent.button ? ievent.button : ievent.which) : null;
   this.keyCode = ievent ? (ievent.which ? ievent.which : ievent.keyCode) : null;
}

/**
 * Stack trace.
 */
Exception = function(message, ex) {
   this._class = "Exception";
   var _error = ex ? ex : new Error(message);
   var _callee = arguments.callee;
   var _caller = _callee.caller;
   var _calstr = _error.stack ? _error.stack.split("\n") :
      [_error.message];
   _calstr[0] = message + ": " + _calstr[0];
   var callers = new Array();

   /**
    * Get stack trace info.
    * @open.
    */
   this.getMsg = getMsgFunction;
   this.getMsg._class = this._class;

   function getMsgFunction() {
      var strace = _calstr;

      if(_error.stack) {
         strace.splice(1, 1);
         strace.splice(strace.length - 2);
      }

      return strace.join("\n");
   }

   function checkCaller(caller) {
      callers.push(caller);
      var str = caller.name ? caller.name : caller.toString();

      if(caller._class && _error.stack) {
         for(var i = 0; i < _calstr.length; i++) {
            _calstr[0] = "----Error:" + _error.message;

            if(_calstr[i].indexOf(str) != -1) {
               _calstr[i] = caller._class + "." +
               _calstr[i].substring(0, _calstr[i].indexOf("@")) + " " +
               _calstr[i].substring(_calstr[i].lastIndexOf("/") + 1);
            }
         }
      }
      else {
         _calstr.push(caller._class + "." +
            str.substring(9, str.indexOf("{")));
      }

      if(caller.caller && callers.indexOf(caller.caller) == -1) {
         checkCaller(caller.caller);
      }
   }

   checkCaller(_caller);
}

/**
 * Return the index of the object in Array.
 */
Array.prototype.indexOf = function(vObject) {
   for(var i = 0; i < this.length; i++) {
      if(vObject) {
         if(this[i] == vObject || (vObject.equals && vObject.equals(this[i]))) {
            return i;
         }
      }
      else {
         if(this[i] == vObject || (this[i].equals && this[i].equals(vObject))) {
            return i;
         }
      }
   }

   return -1;
}

/**
 * Remove the object from Array.
 */
Array.prototype.remove = function(vObject) {
   var index = this.indexOf(vObject);

   if(index != -1) {
      this.splice(index, 1);

      return true;
   }

   return false;
}

/**
 * Equals in Array.
 */
Array.prototype.equals = function(vObject) {
   if(!vObject || this.length != vObject.length) {
      return false;
   }

   for(var i = 0; i < this.length; i++) {
      if(!Tool.equals(this[i], vObject[i])) {
         return false;
      }
   }

   return true;
}

/**
 * Return a copy of the target array.
 */
Array.copy = function(source, isArrays) {
   var results = [];

   for(var i = 0; i < source.length; i++) {
      results.push(isArrays ? Array.copy(source[i]) : source[i]);
   }

   return results;
}

Array.writeXML = function(obj) {
   var str = "";

   for(var i = 0; i < obj.length; i++) {
      str += "<row>";

      var row = obj[i];

      for(var j = 0; j < row.length; j++) {
         str += "<cell>";
         var rowObj = row[j] == null ? "__NULL__" : byteEncode(row[j], true);
         str += "<![CDATA[" + rowObj + "]]>";
         str += "</cell>";
      }

      str += "</row>";
   }

   return str;
}

Array.parseXML = function(node) {
   var list = Tool.getChildNodesByTagName(node, "row");
   var result = [];

   for(var i = 0; i < list.length; i++) {
      var element = list[i];
      var list2 = Tool.getChildNodesByTagName(element, "cell");

      if(list2 && list2.length) {
         result[i] = [];

         for(var j = 0; j < list2.length; j++) {
            var val = Tool.getFirstChildNodeValue(list2[j]);

            result[i][j] = val == "null" ? "" : byteDecode(val);
         }
      }
   }

   return result;
}

String.prototype.replaceAll = function(str2, newChar) {
   var k = this;

   while(true) {
      var tmp = k.replace(str2, newChar);

      if(k == tmp) {
         break;
      }
      else {
         k = tmp;
      }
   }

   return k;
};

String.prototype.lTrim = function() {
   var whitespace = new String(" \t\n\r");
   var s = this;

   if(whitespace.indexOf(s.charAt(0)) != -1) {
     var j=0, i = s.length;

     while (j < i && whitespace.indexOf(s.charAt(j)) != -1) {
      j++;
     }

     s = s.substring(j, i);
   }

   // google chrome returns "object" for typeof new String()
   return s + "";
}

String.prototype.rTrim = function() {
   var whitespace = new String(" \t\n\r");
   var s = this;

   if (whitespace.indexOf(s.charAt(s.length-1)) != -1) {
     var i = s.length - 1;
     while (i >= 0 && whitespace.indexOf(s.charAt(i)) != -1) {
         i--;
     }
     s = s.substring(0, i+1);
   }

   // google chrome returns "object" for typeof new String()
   return s + "";
}

String.prototype.trim = function() {
   return this.rTrim().lTrim();
}

String.prototype.endWith=function(str){
   if(str == null || str=="" || this.length==0 || str.length > this.length) {
      return false;
   }

   return this.substring(this.length - str.length) == str;
}

/**
 * Function bind.
 */
Function.prototype.bind = function() {
  var __method = this;
  var args = Array.copy(arguments);
  var object = args.shift();

  return function() {
    var arg2 = Array.copy(arguments);
    return __method.apply(object, args.concat(arg2));
  }
}

/**
 * Extend.
 */
Object.prototype._extend = function() {
   var args = Array.copy(arguments);
   var superClass = args.shift();
   this._this = this._this ? this._this : this;
   eval(superClass + ".prototype._this = this._this");
   var str = "var source = new " + superClass + "(";

   for(var i = 0; i < args.length; i++) {
      str = str + "args[" + i + "]" + (i + 1 == args.length ? "" : ",");
   }

   str = str + ")";
   eval(str);
   eval(superClass + ".prototype._this = null");

   for(property in source) {
      if(this[property] == null) {
         this[property] = source[property];
      }
   }

   this._super = source;
}

/**
 * Base object.
 */
isii_Object = function() {
   this._class = "isii_Object";
   this._this = this._this ? this._this : this;
   this.eventEnabled = true;
   this._refs = new Object();

   this.setEventEnabled = setEventEnabledFunction;
   this.setEventEnabled._class = this._class;

   function setEventEnabledFunction(enabled) {
      this._this.eventEnabled = enabled;
   }
}

/**
 * Get the hashcode of object.
 */
isii_Object.toHashCode = function(obj) {
   if(obj._hashCode != null) {
      return obj._hashCode;
   };

   return obj._hashCode = _root._objectCounter++;
}

/**
 * Add the specified type event listener to the object.
 */
isii_Object.prototype.addEventListener = function(vType, vFunction, vObject) {
   if(!this._listeners) {
      this._listeners = {};
      this._listeners[vType] = {};
   }
   else if(!this._listeners[vType]) {
      this._listeners[vType] = {};
   };

   var vKey = "evt" + isii_Object.toHashCode(vFunction) +
      (vObject ? "_" + isii_Object.toHashCode(vObject) : "");
   this._listeners[vType][vKey] = {handler : vFunction, object : vObject};
};

/**
 * Remove the specified type event listener off the object.
 */
isii_Object.prototype.removeEventListener = function(vType, vFunction, vObject) {
   var vListeners = this._listeners;

   if(!vListeners || !vListeners[vType]) {
      return;
   };

   var vKey = "evt" +
      isii_Object.toHashCode(vFunction) + (vObject ? "_" + isii_Object.toHashCode(vObject) : "");
   delete this._listeners[vType][vKey];
};

/**
 * Dispatch event.
 */
isii_Object.prototype.dispatchEvent = function(vType, message) {
   var _this = this._this;
   message = message ? message : {};

   if(!_this.eventEnabled) {
      return;
   }
   _this._dispatchEvent(vType, message);
   _this._dispatchEvent("all", message, vType);
};

isii_Object.prototype._dispatchEvent = function(vType, message, aType) {
   var _this = this._this;

   if(_this._listeners && _this._listeners[vType]) {
      var vListeners = _this._listeners;
      var vTypeListeners = vListeners[vType];
      var vFunction, vObject;
      var count =0;
      for(var vHashCode in vTypeListeners) {
         vFunction = vTypeListeners[vHashCode].handler;
         vObject = vTypeListeners[vHashCode].object;

         if(typeof vFunction == "undefined") {
            continue;
         }

         message.type = aType ? aType : vType;
         vFunction.call(vObject != undefined ? vObject : _this, message, _this);
      };
   };
};


function Element() {
}

/**
 * Get the element by this id.
 */
Element.get = function(vid, doc) {
   doc = doc ? doc : document;

   if(typeof vid == "string") {
      v = doc.getElementById(vid);

      if(v == null) {
         var vs = doc.getElementsByName(vid);

         if(vs.length > 0) {
            v = vs[0];
         }
      }
   }

   return v;
}

/**
 * To pixel value.
 */
Element.toPixel = function(val) {
   if(document.getElementById) {
      return val;
   }

   return val;
}

/**
 * Set the element selecteable or not.
 */
Element.setSelectable = function(velement, selectable) {
   if(selectable) {
      if(_root.is_ie) {
         velement.removeAttribute("unselectable");
      }

      if(_root.is_gecko) {
         velement.style.removeProperty("-moz-user-select");
      }

      if(_root.is_chrome) {
         velement.style.removeProperty("-webkit-user-select");
      }

      if(navigator.userAgent.toLowerCase().indexOf("safari") >= 0) {
         velement.style.removeProperty("-khtml-user-select");
      }
   }
   else {
      if(_root.is_ie) {
         velement.setAttribute("unselectable", true);
      }

      if(_root.is_gecko) {
         velement.style.setProperty("-moz-user-select", "none", "");
      }

      if(_root.is_chrome) {
         velement.style.setProperty("-webkit-user-select", "none", "");
      }

      if(navigator.userAgent.toLowerCase().indexOf("safari") >= 0) {
         velement.style.setProperty("-khtml-user-select", "none", "");
      }
   }
}

/**
 * Set the button element enabled or not.
 */
Element.setEnabled = function(vbutton, enabled) {
   vbutton.disabled = !enabled;

   if(enabled) {
      vbutton.style.color = "blue";
   }
   else {
      vbutton.style.color = "silver";
   }
}

/**
 * Add a child node to the parent node.
 */
Element.append = function(vparent, vchild) {
   try {
      vparent.appendChild(vchild);
   }
   catch(ex) {
      alert("" + ex + "|" + ex.stack);
   }
}

/**
 * Remove a child node from parent.
 */
Element.remove = function(vparent, vchild) {
   if(_root.is_ie) {
      if(vchild.className == "dialog_container" && _root.is_ie10) {
         //only for swf dialog in ie10
         setTimeout(function() {removeIENode(vchild);}, 50);
      }
      else {
         removeIENode(vchild);
      }
   }
   else {
      vparent.removeChild(vchild);
   }
}

/**
 * Remove child nodes recursivly in IE.
 */
function removeIENode(node) {
   if(node.childNodes) {
      for(var i = 0; i < node.childNodes.length; i++) {
         removeIENode(node.childNodes[i]);
      }
   }

   node.removeNode(true);
}

/**
 * Clear reference and events.
 */
function purge(elem) {
   if(elem.style && elem.style.backgroundImage) {
      elem.style.backgroundImage = "";
   }

   if(elem.childNodes) {
      var attr = elem.childNodes;

      for(var i = 0; i < attr.length; i++) {
          purge(attr[i]);
      }
   }

   if(elem._refs) {
      if(elem._refs._listeners && elem._refs._listeners.length > 0) {
         for(var i = elem._refs._listeners.length - 1; i >= 0; i--) {
            var obj = elem._refs._listeners[i];
            Element.removeEventListener(elem, obj.vType, obj.vFunction, i);
         }
      }

      elem._refs = null;
   }
}

/**
 * Insert the child element into parent element before the specified node.
 */
Element.insert = function(vparent, vchild, vnode) {
   vparent.insertBefore(vchild, vnode);
}

/**
 * Create a specified type element.
 */
Element.create = function(vtype, vclass, selectable, doc, vparent) {
   doc = doc ? doc : document;

   var element = doc.createElement(vtype);
   element._refs = new Object();

   if(vparent) {
      Element.append(vparent, element);
   }

   Element.setSelectable(element, selectable);

   if(vclass) {
      element.className = vclass;
   }

   return element;
}

/**
 * Create a text node.
 */
Element.createText = function(vtext, doc) {
   doc = doc ? doc : document;
   var txt = doc.createTextNode(vtext);

   return txt;
}


/**
 * Add the specified type event listener to the elemnt.
 */
Element.addEventListener = function(vElement, vType, vFunction) {
   if(_root.is_ie) {
      vElement.attachEvent("on" + vType, vFunction);
   }
   else if(_root.is_firefox || _root.is_opera) {
      vElement.addEventListener(vType, vFunction, false);
   }
   else {
      Tool.trythese(
         function() {vElement.addEventListener(vType, vFunction, false)},
         function() {vElement.attachEvent("on" + vType, vFunction)}
      );
   }

   if(!vElement._refs) {
      vElement._refs = new Object();
   }

   if(!vElement._refs._listeners) {
      vElement._refs._listeners = new Array();
   }

   try {
      vElement._refs._listeners.push({"vType":vType, "vFunction":vFunction});
   }
   catch(ex) {
      // IE9 work-around
      try {
         Array.prototype.push.call(
            vElement._refs._listeners.push,
            {"vType":vType, "vFunction":vFunction});
      }
      catch(e) {
      }
   }
};

/**
 * Remove the specified type event listener off the elemnt.
 */
Element.removeEventListener = function(vElement, vType, vFunction, index) {
   if(_root.is_ie) {
      vElement.detachEvent("on" + vType, vFunction);
   }
   else if(_root.is_firefox || _root.is_opera) {
      vElement.removeEventListener(vType, vFunction, false);
   }
   else {
      Tool.trythese(
         function() {vElement.detachEvent("on" + vType, vFunction)},
         function() {vElement.removeEventListener(vType, vFunction, false)});
   }

   if(!isNaN(index)) {
      vElement._refs._listeners.splice(index, 1);
   }
   else {
      for(var i = vElement._refs._listeners.length - 1; i >= 0; i--) {
         var obj = vElement._refs._listeners[i];

         if(obj.vType == vType) {
            vElement._refs._listeners.splice(i, 1);
         }
      }
   }
};

function Tool() {
}

/**
 * Get the window width.
 */
Tool.getWindowWidth = function(win) {
   win = win ? win : window;

   return (win.innerWidth) ? win.innerWidth :
      win.document.body.clientWidth;
}

/**
 * Get the window height.
 */
Tool.getWindowHeight = function(win) {
   win = win ? win : window;

   return (win.innerHeight) ? win.innerHeight :
      win.document.body.clientHeight;
}


Tool.getWindowOrDocumentHeight = function(win, doc) {
   var clientHeight = doc.body.clientHeight;
   var height = clientHeight;

   if(win) {
      height = win.style.height ? win.style.height : win.height ?
         win.height : height ;

      if(isByPercentage(height)) {
         height = extractAbsoluteValue(height) / 100;
         height = calculateLength(clientHeight, "*", height);
      }
   }

   height = calculateLength(height, "-", 10);

   return height;
}

/**
 * Try funtions.
 */
Tool.trythese = function() {
   var returnValue;

   for(var i = 0; i < arguments.length; i++) {
      var lambda = arguments[i];

      try {
         returnValue = lambda();
         break;
      }
      catch(ex) {
      }
   }

   return returnValue;
}

/**
 * Get a child element by its tag name. If more than one elements have the
 * same tag name, the first one will be returned.
 */
Tool.getChildNodeByTagName = function(element, name) {
   return listNode(element, name, true);
}

/**
 * Get all the children of the element that has name as its tag name.
 */
Tool.getChildNodesByTagName = function(element, name) {
   return listNode(element, name, false);
}

/**
 * Get first child value. In safari, the node is not equal null, but its
   firstchild is null. So here give it a judgement.If the firstchild is
   null, then its nodevalue is "".
 */
Tool.getFirstChildNodeValue = function(element) {
   var value = "";
   if(element.firstChild){
      value = element.firstChild.nodeValue;
   }
   return value;
}

/**
 * Get target frame window by the specified name.
 */
Tool.findFrameWindow = function(vname, vwin) {
   try {
      vwin = vwin ? vwin : window.top;

      if(vwin.frames[vname]) {
         return vwin.frames[vname];
      }

      for(var i = 0; i < vwin.frames.length; i++) {
         var result = Tool.findFrameWindow(vname, vwin.frames[i]);

         if(result != null) {
            return result;
         }
      }
   }
   catch(ex) {
      return null;
   }
}

Tool.equals = function(obj1, obj2) {
   if(obj1 == obj2) {
      return true;
   }

   if(obj1 == null) {
      return false;
   }

   if(obj1.equals) {
      return obj1.equals(obj2);
   }

   return false;
}

/**
 * Gets the bounding rectangle for the specified element.
 *
 * @param element the element to inspect.
 *
 * @returns {{top: number, left: number, bottom: number, right: number}} the bounds.
 *
 * @public
 */
Tool.getElementBounds = function(element) {
   var bounds = {
      top: 0,
      left: 0,
      bottom: 0,
      right: 0
   };

   var e = element;

   do {
      bounds.top += e.offsetTop;
      bounds.left += e.offsetLeft;
   }
   while(e = e.offsetParent);

   bounds.bottom = bounds.top + element.offsetHeight;
   bounds.right = bounds.left + element.offsetWidth;
   return bounds;
};

function listNode(element, name, first) {
   var result = [];
   var cnodes = element.childNodes;

   for(var i = 0; cnodes.length && i < cnodes.length; i++) {
      var node = cnodes[i];

      if(node.nodeType == 1 && node.nodeName == name) {
         if(first) {
            return node;
         }

         result.push(node);
      }
   }

   return first ? null : result;
}

/**
 * Manages http object.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */

/**
 * Constructor.
 * Creates a new instance of isii_AJAX.
 */
function isii_AJAX(handler, op, action, uri, async, config) {
   this._class = "isii_AJAX";
   this._extend("isii_Object");
   var _this = this._this;

   this.handler = handler;
   this.uri = uri;
   this.op = op;
   this.action = action;
   this.async = async;
   this.notXML = false;
   config = config == null ? {} : config;
   var hideLoadingDiv = config.hideLoadingDiv == null ? false : config.hideLoadingDiv;
   var isText = config.isText == null ? false : config.isText;
   var servlet = uri;
   servlet = servlet + getConcat(servlet);
   clear();

   /**
    * Create http object if there is not.
    * @return http object of this.
    * @public.
    */
   this.getXMLHttp = getXMLHttpFunction;
   this.getXMLHttp._class = this._class;

   function getXMLHttpFunction() {
      if(!_this.xmlHttp) {
         if(window.ActiveXObject) {
            _this.xmlHttp = new ActiveXObject("Microsoft.XMLHTTP");
         }
         else if(window.XMLHttpRequest) {
            _this.xmlHttp = new XMLHttpRequest();
         }
         else {
            alert("Not supported xmlhttp! ");
         }
      }

      return _this.xmlHttp;
   }

   /**
    * Get the status.
    * @public.
    */
   this.getStatus = getStatusFunction;
   this.getStatus._class = this._class;

   function getStatusFunction() {
      return _this.status;
   }

   /**
    * Get error message.
    * @return http object of this.
    * @public.
    */
   this.getError = getErrorFunction;
   this.getError._class = this._class;

   function getErrorFunction() {
      if(_this.status == isii_AJAX.COMMUNICATION_FAILED) {
         return _this.error ? _this.error : "Communication failed";
      }
      else if(_this.status == isii_AJAX.TRANSACTION_FAILED) {
         return _this.error ? _this.error : "Transaction failed";
      }

      return null;
   }

   /**
    * Get reason message.
    * @return http object of this.
    * @public.
    */
   this.getReason = getReasonFunction;
   this.getReason._class = this._class;

   function getReasonFunction() {
      return _this.reason;
   }

   /**
    * Add a listener to finish event.
    * @param func the function to be executed when communication over.
    * @param obj the object that func be registered to .
    * @public.
    */
   this.addFinishListener = addFinishListenerFunction;
   this.addFinishListener._class = this._class;

   function addFinishListenerFunction(func, obj) {
      _this.addEventListener('complete', func, obj);
   }

   /**
    * Removes a listener previously added with addFinishListener().
    * @param func the function to be executed when communication over.
    * @param obj the object that func be registered to.
    * @public.
    */
   this.removeFinishListener = removeFinishListenerFunction;
   this.removeFinishListener._class = this._class;

   function removeFinishListenerFunction(func, obj) {
      _this.removeEventListener('complete', func, obj);
   }

   /**
    * Send a request.
    * @param method alternative 'GET' or 'POST'.
    * @param param the xml to be send or part of url according to param method.
    * @public.
    */
   this.send = sendFunction;
   this.send._class = this._class;

   function sendFunction(param, loadingStr, successStr) {
      clear();
      var isXML = false;

      if(param != null && param.length > 1) {
         isXML = param.charAt(0) == '<' && param.charAt(param.length - 1) == '>';
      }

      var url = "";
      url = (_this.action ? "action=" + _this.action + "&" : "") + url;
      url = (_this.op ? "op=" + _this.op + "&" : "") + url;
      url = (_this.handler ? "handler=" + _this.handler + "&" : "") + url;
      _this.successStr = successStr;

      if(url.charAt(url.length - 1) == "&") {
         url = url.substring(0, url.length - 1);
      }

      var isContainer = param && param.indexOf("<xmlContainer") != -1 ? true : false;
      url = url + "&isAJAXRequest=true" + "&isContainer=" + isContainer + "&isXML=" + isXML;

      try {
         var uriString = _this.uri + getConcat(_this.uri) + url;
         _this.getXMLHttp().open("POST", uriString, _this.async);

         if(_this.async) {
            _this.getXMLHttp().onreadystatechange = processResult;
         }

         _this.getXMLHttp().setRequestHeader("Content-Type",
            "application/x-www-form-urlencoded");
         createLoadingDiv(loadingStr);

         param = param ?
            _this.uri.indexOf("sree_jsp_id") >= 0 ? "xmlstream=" + param : param
            : "";

         _this.getXMLHttp().send(param);

         if(!_this.async) {
            processResult();
         }
      }
      catch(ex) {
         var exception = new Exception("ajax send, exception occur.", ex);
         _this.error = exception.getMsg();
         _this.status =  isii_AJAX.COMMUNICATION_FAILED;
      }
   }

   /**
    * Process result.
    */
   function processResult() {
      var xmlhttp = _this.getXMLHttp();

      if(_this.async && xmlhttp.readyState != 4) {
         return;
      }

      if(_this.notXML) {
         hideLoading();
        return;
      }

      var status = xmlhttp.status;
      var responseText = xmlhttp.responseText;
      var responseXML = xmlhttp.responseXML;
      _this.xmlHttp = null;

      try {
         if(status != 200) {
            hideLoading();
            _this.status = isii_AJAX.COMMUNICATION_FAILED;
            return;
         }
      }
      catch(ex) {
         return;
      }

      var xml;

      if(!responseXML || !responseXML.firstChild) {
         var ajaxResponse;

         if(_root.is_ie || _root.is_ie11) {
            ajaxResponse = new ActiveXObject('Microsoft.XMLDOM');
            ajaxResponse.loadXML(responseText);
         }
         else {
            ajaxResponse = new DOMParser().parseFromString(responseText,
                                                           'text/xml');
         }

         xml = ajaxResponse.firstChild;
      }
      else {
         xml = responseXML.firstChild;
      }

      try {
         var heads = xml.getElementsByTagName("head");
         var head = heads == null || heads.length == 0 ? null : heads[0];

         if(head != null) {
            var flag = "" + Tool.getFirstChildNodeValue(
               head.getElementsByTagName("isiisuccessful")[0]);

            if("true" == flag) {
               _this.status = isii_AJAX.TRANSACTION_SUCCESSFUL;
            }
            else {
               _this.status = isii_AJAX.TRANSACTION_FAILED;
               _this.error = byteDecode(Tool.getFirstChildNodeValue(
                  head.getElementsByTagName("isiimessage")[0]));

               if(head.getElementsByTagName("reason") != null) {
                  _this.reason = Tool.getFirstChildNodeValue(
                  head.getElementsByTagName("reason")[0]);
               }
            }
         }
      }
      catch(error) {
      }

      // set status before complete
      if(isText) {
         hideLoading();
         _this.dispatchEvent('complete',
            {"ajax" : _this, "text" : responseText});

         return;
      }

      if(!head) {
         hideLoading();

         if(_this.op == "replet" && _this.action != null &&
            _this.action.indexOf("getRepletParameters") != -1 &&
            xml.firstChild.nodeName == "parsererror")
         {
            alert("$(em.schedule.report.parserError)#(inter)");
         }
         else {
            alert("$(em.servlet.sessionTimeout)#(inter)");
         }

         // alert("AJAx:wrong head" + _this.op + "|" + _this.action + "|" +
         //   xml.firstChild.nodeName + "|" + xmlhttp.responseText);
         return;
      }

      var element = !!xml.getElementsByTagName("body")[0] ?
         xml.getElementsByTagName("body")[0].firstChild : null;
      _this.element = element;

      if(_this.getError() != null) {
         hideLoading();
      }
      else {
         var loadingTxt = Element.get("ajax_loadingtxt");
         var loadingImg = Element.get("ajax_loadingimg");
         var successImg = Element.get("ajax_successimg");

         if(loadingTxt && loadingImg) {
            loadingTxt.innerHTML = _this.successStr ?
               _this.successStr : "$(Success)#(inter)!";
            successImg.src = servlet + "op=Resource&name=%2finetsoft%2fsree%2fportal%2fimages%2fajax_success%2egif";
            loadingImg.style.display = "none";
            successImg.style.display = "";

            var timeId = window.setTimeout(function() {
                hideLoading();
                window.clearTimeout(timeId);
            }, 1000);
         }
      }

      //window.prompt("", xmlhttp.responseText);
      _this.dispatchEvent('complete', {"ajax" : _this, "element" : element});
   }

   // remove loading div
   function hideLoading() {
      var loadingDiv = Element.get("ajax_loadingdiv");

      if(loadingDiv) {
         loadingDiv.style.display = "none";
      }
   }

   // create loading div
   function createLoadingDiv(loadingStr) {
      if(hideLoadingDiv) {
         return;
      }

      var loadingDiv = Element.get("ajax_loadingdiv");
      var loadingTxt = Element.get("ajax_loadingtxt");
      var loadingImg = Element.get("ajax_loadingimg");
      var successImg = Element.get("ajax_successimg");

      if(!loadingDiv) {
         loadingDiv = Element.create("div", _this._class + "_loading", false);
         loadingDiv.id = "ajax_loadingdiv";
         loadingDiv.style.position = "absolute";
         loadingDiv.style.top =
            Math.min(Tool.getWindowHeight(self),
                     Tool.getWindowHeight(getRoot(self)))/2;
         loadingDiv.style.left =
            Math.min(Tool.getWindowWidth(self),
                     Tool.getWindowWidth(getRoot(self))) / 2;
         var loadingTable = Element.create("table",
            _this._class + "_loadingTable", false);
         Element.append(loadingDiv, loadingTable);
         var temprow = loadingTable.insertRow(0);
         var tempcell = temprow.insertCell(0);
         // put two img components in same
         // place, when in loading, display loadingImg, when in success, display
         // successImg
         loadingImg = Element.create("img", _this._class + "_loadingImg");
         loadingImg.setAttribute("alt", "Loading");
         loadingImg.id = "ajax_loadingimg";
         successImg = Element.create("img", _this._class + "_loadingImg");
         successImg.setAttribute("alt", "Success");
         successImg.id = "ajax_successimg";
         loadingImg.src = servlet + "op=resource&style=true&theme=true&type=portal&name=ajax_loading%2egif";
         Element.append(tempcell, loadingImg);
         Element.append(tempcell, successImg);
         tempcell = temprow.insertCell(1);
         loadingTxt = Element.create("div", _this._class + "_loadingTxt");
         loadingTxt.id = "ajax_loadingtxt";
         Element.append(tempcell, loadingTxt);
         Element.append(document.body,  loadingDiv);
      }

      loadingDiv.style.display = "";
      successImg.style.display = "none";
      loadingImg.style.display = "";
      loadingStr = loadingStr ?  loadingStr + "..." : "$(Loading)#(inter)...";
      loadingTxt.innerHTML = loadingStr;
   }

   // clear status, private
   function clear() {
      _this.status = isii_AJAX.UNKNOWN;
      _this.error = null;
      _this.reason = null;
      _this.element = null;
         _this.successStr = null;
   }
}

/**
 * Unknown state.
 */
isii_AJAX.UNKNOWN = -3;

/**
 * Commnunication failed.
 */
isii_AJAX.COMMUNICATION_FAILED = -2;

/**
 * Transaction failed.
 */
isii_AJAX.TRANSACTION_FAILED = -1;

/**
 * Transaction successful.
 */
isii_AJAX.TRANSACTION_SUCCESSFUL = 0;

/**
 * XMLContainer.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */

/**
 * Constructor.
 */
isii_XMLContainer = function() {
   this._class = "inetsoft.sree.internal.XMLContainer";
   this._extend("isii_Object");
   this.keys = [];
   this.values = [];
   var _this = this._this;

   /**
    * Put a key-value pair.
    * @public
    */
   this.put = putFunction;
   this.put._class = this._class;

   function putFunction(key, value) {
      if(value == null) {
         _this.keys.remove(key);

         return;
      }

      if(typeof value != "string" && !value.writeXML) {
         var ex = new Exception("invalid value[" + key +", " + value + "]");
         alert(ex.getMsg());
      }

      var index = _this.keys.indexOf(key);

      if(index == -1) {
         _this.keys.push(key);
         _this.values.push(value);
      }
      else {
         _this.values.splice(index, 1, value);
      }
   }

   /**
    * Get the value of a key-value pair.
    * @public
    */
   this.get = getFunction;
   this.get._class = this._class;

   function getFunction(key) {
      var index = _this.keys.indexOf(key);

      if(index != -1) {
         return _this.values[index];
      }

      return null;
   }

   /**
    * Clear the xml container.
    * @public
    */
   this.clear = clearFunction;
   this.clear._class = this._class;

   function clearFunction() {
      _this.keys = [];
      _this.values = [];
   }

   /**
    * Write the xml segment.
    * @public
    */
   this.writeXML = writeXMLFunction;
   this.writeXML._class = this._class;

   function writeXMLFunction() {
      var str = "";
      str = '<xmlContainer class="' + _this._class + '">';

      for(var i = 0; i < _this.keys.length; i++) {
         var value = _this.values[i];
         var key =  _this.keys[i];
         var isstr = (typeof value == "string");

         str = str + '<pairEntry><key><![CDATA[' + key + ']]></key>' +
            '<value string="' + isstr + '" class="' +
            (isstr ? "java.lang.String" : "inetsoft.sree.internal.XMLContainer")+
            '">' + (isstr ? '<![CDATA[' + byteEncode2(value) + ']]' :
            value.writeXML()) + '></value></pairEntry>';
      }

      str = str + _this.writeContents();
      str = str + '</xmlContainer>';

      return str;
   }

   this.writeContents = writeContentsFunction;
   this.writeContents._class = this._class;

   function writeContentsFunction() {
      return "";
   }

   /**
    * Method to parse an xml segment.
    * @public
    */
   this.parseXML = parseXMLFunction;
   this.parseXML._class = this._class;

   function parseXMLFunction(elem) {
      if(elem == null) {
         return;
      }

      var nodes = elem.getElementsByTagName("pairEntry");

      for(var i = 0; i < nodes.length; i++) {
         var node = nodes[i];
         var knode = node.getElementsByTagName("key")[0];
         var key = Tool.getFirstChildNodeValue(knode);
         var vnode = node.getElementsByTagName("value")[0];
         var str = "true" == vnode.getAttribute("string");
         var cls = vnode.getAttribute("class");
         var value;

         if(str) {
            value = byteDecode(Tool.getFirstChildNodeValue(vnode));
         }
         else {
            value = isii_XMLContainer.createXMLContainer(vnode);
         }

         _this.put(key, value);
      }

      _this.parseContents(elem);
   }

   this.parseContents = parseContentsFunction;
   this.parseContents._class = this._class;

   function parseContentsFunction() {
   }

   /**
    * Check if equals another obj.
    */
    this.equals = equalsFunction;
    this.equals._class = this._class;

    function equalsFunction(obj) {
      if(this._class != obj._class) {
         return false;
      }

      if(this.keys.length != obj.keys.length) {
         return false;
      }

      for(var i = 0; i < this.keys.length; i++) {
         var tval = this.get(this.keys[i]);
         var oval = obj.get(this.keys[i]);

         if(typeof tval != typeof oval) {
            return false;
         }

         if(typeof tval == typeof oval) {
            if(typeof tval == 'string') {
               if(tval != oval) {
                  return false;
               }
            }
            else {
               if(!tval.equals(oval)) {
                  return false;
               }
            }
         }
      }

      for(var i = 0; i < obj.keys.length; i++) {
         var tval = this.get(obj.keys[i]);
         var oval = obj.get(obj.keys[i]);

         if(typeof tval != typeof oval) {
            return false;
         }

         if(typeof tval == typeof oval) {
            if(typeof tval == 'string') {
               if(tval != oval) {
                  return false;
               }
            }
            else {
               if(!tval.equals(oval)) {
                  return false;
               }
            }
         }
      }

      return true;
   }
}

isii_XMLContainer.createXMLContainer = function(element) {
   var cls = element.getAttribute("class");
   var object = null;

   if(cls == "inetsoft.sree.internal.XMLContainer") {
      object = new isii_XMLContainer();
   }

   if(object != null) {
      object.parseXML(element);
   }

   return object;
}

/**
 * Event broker.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */
isii_EventBroker = function() {
   this._class = "isii_EventBroker";
   this._extend("isii_Object");
   var _this = this._this;

   var events = {};

   /**
    * Publish a message.
    * @param eventid the event id.
    * @param message the message object.
    * @public
    */
   this.publish = publishFunction;
   this.publish._class = this._class;

   function publishFunction(eventid, message) {
      var topic = getTopic(eventid);
      return send(topic, message);
   }

   /**
    * Subscribe the specified type of event.
    * @public
    */
   this.subscribe = subscribeFunction;
   this.subscribe._class = this._class;

   function subscribeFunction(eventid, vFunction, vObject) {
      var topic = getTopic(eventid);
      var vKey = "sub" + isii_Object.toHashCode(vFunction) +
         (vObject ? "_" + isii_Object.toHashCode(vObject) : "");
      topic[vKey] = {handler : vFunction, object : vObject};
   }

   /**
    * Unsubscribe the specified type of event.
    * @public
    */
   this.unsubscribe = unsubscribeFunction;
   this.unsubscribe._class = this._class;

   function unsubscribeFunction(eventid, vFunction, vObject) {
      var topic = getTopic(eventid);
      var vKey = "sub" + isii_Object.toHashCode(vFunction) +
         (vObject ? "_" + isii_Object.toHashCode(vObject) : "");

      delete topic[vKey];
   }

   /**
    * send a message.
    * @private
    */
   function send(topic, message) {
      var res = false;

      for(var vHashCode in topic) {
         vFunction = topic[vHashCode].handler;
         vObject = topic[vHashCode].object;

         if(typeof vFunction == "undefined") {
            continue;
         }

         try {
            vFunction.call(vObject != "undefined" ? vObject : this, message);
            res = true;
         }
         catch(ex) {
            delete topic[vHashCode];
         };
      }

      return res;
   }

   /**
    * Get a top by event type.
    * @private
    */
   function getTopic(eventid) {
      if(!events[eventid]) {
         var topic = {};
         events[eventid] = topic;
      }

      return events[eventid];
   }
}

/**
 * Get a broker.
 * @public
 */
isii_EventBroker.getBroker = function(root) {
   if(root) {
      if(!root.runScript) {
         alert("Invalid broker root![" + root.location + "]");
         return new isii_EventBroker();
      }
      else {
         root.runScript("this.eventBroker = this.eventBroker ? this.eventBroker : new isii_EventBroker();");

         return root.eventBroker;
      }
   }
   else {
      return new isii_EventBroker();
   }
}

function isByPixel(val1) {
   var val = "" + val1;
   return val.indexOf("px") != -1;
}

function isByPt(val1) {
   var val = "" + val1;
   return val.indexOf("pt") != -1;
}

function isByPercentage(val1) {
   var val = "" + val1;
   return val.indexOf("%") != -1;
}

function extractAbsoluteValue(val1) {
   var val = "" + val1;
   var suf = 0;

   if(isByPixel(val1) || isByPt(val1)) {
      suf = 2;
   }
   else if(isByPercentage(val1)) {
      suf = 1;
   }

   var val0 = val.substring(0, val.length - suf);

   if(isNaN(val0)) {
      return parseInt(val0, 10);
   }
   else {
      return val0;
   }
}

function calculateLength(val, operator, val2) {
   var val = extractAbsoluteValue(val);
   eval("var result = " + val + " " + operator + " " + val2);
   return result;
}

function showPopWin(url, title, move, width, height, x, y,
                    callback, callbackOwner)
{
   try {
      parent.setClose("true");
      parent.hidetab();
   }
   catch(ex) {
   }

   var winWidth = Tool.getWindowWidth(getRoot(self));
   var winHeight = Tool.getWindowHeight(getRoot(self));
   var realWidth = !width || width > winWidth ? winWidth : width;
   var realHeight = !height || height > winHeight ? winHeight : height;
   var dialog = new isii_JSIFrameDialog(self, title, "", getRoot(self), url,
                                        function(dlg)
   {
      // make dialog can display right on ie and chrome.
      if(_root.is_ie9minus || _root.is_chrome) {
         realHeight += 6;
      }

      dlg.setSize(realWidth, realHeight);

      if(x && y) {
         dlg.setPosition(x, y);
      }

      dlg.show(true);

      if(callback) {
         callback.call(callbackOwner ? callbackOwner : dlg, dlg);
      }
   });
}

/**
 * Init component when document is ready.
 */
Element.initComponent = function(doc, component) {
   if(doc.readyState && doc.readyState != "complete" && doc.onreadystatechange)
   {
      doc.onreadystatechange = function() {
         if(doc.readyState == "complete") {
            component.init();
         }
      }
   }
   else {
      component.init();
   }
}

/**
 * Window Time out  with parameters.
 */
Element.setTimeout = function(win, delay, obj, fun) {
   if(typeof fun == 'function') {
      var args = Array.prototype.slice.call(arguments, 4);
      var obj = args.shift;
      var f = fun.bind(obj, args);
      return win.setTimeout(f, delay);
   }

   return window.setTimeout(fun, delay);
}

isii_SimpleDateFormat = function(vformat) {
   this.vformat = "" + vformat;
   var localedWeek = null;
   var localedMonth = null;

   /**
    * Formats a Date into a date/time string.
    * @param date the time value to be formatted into a time string.
    * @return the formatted time string.
    */
   this.format = function(date) {
      var result = "";
      var i_format = 0;
      var c = "";
      var token = "";
      var y = date.getYear()+"";
      var M = date.getMonth()+1;
      var d = date.getDate();
      var E = date.getDay();
      var H = date.getHours();
      var m = date.getMinutes();
      var s = date.getSeconds();
      var yyyy,yy,MMM,MM,dd,hh,h,mm,ss,ampm,HH,H,KK,K,kk,k;
      // Convert real date parts into formatted versions
      var value = new Object();

      if(y.length < 4) {
         y = "" + (y - 0 + 1900);
      }

      value["y"] = "" + y;
      value["yyyy"] = y;
      value["yy"] = y.substring(2,4);
      value["M"] = M;
      value["MM"] = LZ(M);
      value["MMM"] = localedMonth != null ?
         localedMonth[M - 1] : MONTH_NAMES[M+11];
      value["MMMM"] = localedMonth != null ?
         localedMonth[M - 1] : MONTH_NAMES[M-1];
      value["d"] = d;
      value["dd"] = LZ(d);
      value["EE"] = localedWeek != null ? localedWeek[E] : DAY_NAMES[E+7];
      value["EEEE"] = localedWeek != null ? localedWeek[E] : DAY_NAMES[E];
      value["H"] = H;
      value["HH"] = LZ(H);

      if(H == 0) {
         value["h"] = 12;
      }
      else if(H > 12) {
         value["h"] = H - 12;
      }
      else{
         value["h"] = H;
      }

      value["hh"] = LZ(value["h"]);

      if(H > 11) {
         value["K"] = H-12;
      }
      else{
         value["K"] = H;
      }

      value["k"] = H+1;
      value["KK"] = LZ(value["K"]);
      value["kk"] = LZ(value["k"]);

      if(H > 11) {
         value["a"] = "PM";
      }
      else{
         value["a"] = "AM";
      }

      value["m"] = m;
      value["mm"] = LZ(m);
      value["s"] = s;
      value["ss"] = LZ(s);

      while(i_format < this.vformat.length) {
         c = this.vformat.charAt(i_format);
         token="";

         while((this.vformat.charAt(i_format)==c) &&
               (i_format < this.vformat.length)) {
            token += this.vformat.charAt(i_format++);
         }

         token = this.normalizeToken(token);

         // When local is chinese, the format contains Chinese characters,
         // should filter the escape characters.
         if(token == "'") {
            continue;
         }

         if(value[token] != null) {
            result=result + value[token];
         }
         else {
            result=result + token;
         }
      }

      return result;
   }

   this.normalizeToken = function(token) {
      var c = token.charAt(0);
      var str = token;

      if(c == 'y') {
         if(token.length >= 4) {
            str = "yyyy";
         }
         else if(token.length != 1) {
            str = "yy";
         }
      }
      else if(c == 'M') {
         if(token.length >= 4) {
            str = "MMMM";
         }
      }
      else if(c == 'd') {
         if(token.length >= 2) {
            str = "dd";
         }
      }
      else if(c == 'E') {
         if(token.length >= 4) {
            str = "EEEE";
         }
         else {
            str = "EE";
         }
      }
      else if(c == 'H') {
         if(token.length >= 2) {
            str = "HH";
         }
      }
      else if(c == 'h') {
         if(token.length >= 2) {
            str = "hh";
         }
      }
      else if(c == 'K') {
         if(token.length >= 2) {
            str = "KK";
         }
      }
      else if(c == 'k') {
         if(token.length >= 2) {
            str = "kk";
         }
      }
      else if(c == 'a') {
         str = "a";
      }
      else if(c == 'm') {
         if(token.length >= 2) {
            str = "mm";
         }
      }
      else if(c == 's') {
         if(token.length >= 2) {
            str = "ss";
         }
      }

      return str;
   }

   /**
    *Parses text from a string to produce a Date
    */
   this.parse = function(val) {
      var i_val = 0;
      var i_format = 0;
      var c = "";
      var token = "";
      var token2 = "";
      var x,y;
      var now = new Date();
      var year = now.getYear();
      var month = now.getMonth()+1;
      var date = 1;
      var hh = now.getHours();
      var mm = now.getMinutes();
      var ss = now.getSeconds();
      var ampm = "";

      while (i_format < this.vformat.length) {

         c = this.vformat.charAt(i_format);
         token = "";

         while((this.vformat.charAt(i_format) == c) &&
               (i_format < this.vformat.length)) {
            token  +=  this.vformat.charAt(i_format++);
         }

         token = this.normalizeToken(token);
         // Extract contents of value based on format token
         if(token == "yyyy" || token == "yy" || token == "y") {
            if(token == "yyyy") {
               x = 4;y = 4;
            }

            if(token == "yy") {
               x = 2;y = 2;
            }

            if(token == "y") {
               x = 2;y = 4;
            }

            year = _getInt(val, i_val, x , y);

            if(year == null) {
               return null;
            }

            i_val  +=  year.length;

            if(year.length == 2) {
               if(year > 70) {
                  year = 1900 + (year-0);
               }
               else {
                  year = 2000 + (year-0);
               }
            }
         }
         else if(token == "MMMM"||token == "MMM"){
            month = 0;

            for(var i = 0; i < MONTH_NAMES.length; i++) {
               var month_name = MONTH_NAMES[i];

               if(val.substring(i_val, i_val + month_name.length).toLowerCase()==
                  month_name.toLowerCase()) {
                  if(token == "MMMM"||(token == "MMM" && i > 11)) {
                     month = i+1;

                     if(month > 12) {
                        month -=  12;
                     }

                     i_val  +=  month_name.length;

                     break;
                  }
               }
            }

            if((month < 1)||(month > 12)){
               return null;
            }
         }
         else if(token == "EEEE"||token == "EE"){
            for(var i = 0; i<DAY_NAMES.length; i++) {
               var day_name = DAY_NAMES[i];

               if(val.substring(i_val,i_val+day_name.length).toLowerCase() ==
                  day_name.toLowerCase()) {
                  i_val  +=  day_name.length;
                  break;
               }
            }
         }
         else if(token == "MM"||token == "M") {
            month = _getInt(val, i_val, token.length, 2);

            if(month == null || (month < 1) || (month > 12)){
               return null;
            }

            i_val += month.length;
         }
         else if(token == "dd" || token == "d") {
            date = _getInt(val, i_val, token.length, 2);

            if(date == null || (date < 1) || (date > 31)){
               return null;
            }

            i_val += date.length;
         }
         else if(token == "hh"||token == "h") {
            hh = _getInt(val, i_val, token.length, 2);

            if(hh == null||(hh < 1)||(hh > 12)) {
               return null;
            }

            i_val += hh.length;
         }
         else if(token == "HH"||token == "H") {
            hh = _getInt(val, i_val, token.length, 2);

            if(hh == null || (hh < 0) || (hh > 23)) {
               return null;
            }

            i_val += hh.length;
         }
         else if(token == "KK" || token == "K") {
            hh = _getInt(val, i_val, token.length, 2);

            if(hh == null || (hh < 0) || (hh > 11)){
               return null;
            }

            i_val += hh.length;
         }
         else if(token == "kk" || token == "k") {
            hh = _getInt(val, i_val, token.length, 2);

            if(hh == null || (hh < 1) || (hh > 24)) {
               return null;
            }

            i_val += hh.length;hh--;
         }
         else if(token == "mm" || token == "m") {
            mm = _getInt(val, i_val, token.length, 2);

            if(mm == null || (mm < 0) || (mm > 59)) {
               return null;
            }

            i_val += mm.length;
         }
         else if(token == "ss" || token == "s") {
            ss = _getInt(val, i_val, token.length, 2);

            if(ss == null || (ss < 0) || (ss > 59)) {
               return null;
            }

            i_val += ss.length;
         }
         else if(token == "a") {
            if(val.substring(i_val, i_val + 2).toLowerCase() == "am") {
               ampm = "AM";
            }
            else if(val.substring(i_val, i_val + 2).toLowerCase() == "pm") {
               ampm = "PM";
            }
            else {
               return null;
            }

            i_val += 2;
         }
         else {
            if(val.substring(i_val, i_val + token.length) != token) {
               return null;
            }
            else {
               i_val += token.length;
            }
         }
      }

      if(i_val !=  val.length) {
         return null;
      }

      if(month == 2) {
         if(((year % 4 == 0) && (year % 100 !=  0)) || (year % 400 == 0)) {
            if(date > 29){
               return null;
            }
         }
         else {
            if(date > 28) {
               return null;
            }
         }
      }

      if((month == 4)||(month == 6)||(month == 9)||(month == 11)) {
         if(date > 30) {
            return null;
         }
      }

      // Correct hours value
      if(hh < 12 && ampm == "PM") {
         hh = hh - 0 + 12;
      }
      else if(hh > 11 && ampm == "AM") {
         hh -= 12;
      }

      var newdate = new Date(year, month - 1, date, hh, mm, ss);

      return newdate;
   }

   /**
    * Set localed week.
    */
   this.setLocaledWeek = function(val) {
      localedWeek = val;
   }

   /**
    * Set localed month.
    */
   this.setLocaledMonth = function(val) {
      localedMonth = val;
   }

   function _isInteger(val) {
      var digits = "1234567890";

      for(var i = 0; i < val.length; i++) {
         if(digits.indexOf(val.charAt(i)) == -1) {
            return false;
         }
      }

      return true;
   }

   function _getInt(str, i, minlength, maxlength) {
      for(var x = maxlength; x >= minlength; x--) {
         var token=str.substring(i,i+x);

         if(token.length < minlength) {
            return null;
         }

         if(_isInteger(token)) {
            return token;
         }
      }

      return null;
   }

   function LZ(x) {
      return(x < 0 || x > 9 ? "" : "0" ) + x;
   }

   var MONTH_NAMES = new Array('January','February','March','April','May','June','July','August','September','October','November','December','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec');
   var DAY_NAMES = new Array('Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sun','Mon','Tue','Wed','Thu','Fri','Sat');
}

Element.autoFix = function(id, doc, upper) {
   var textf = Element.get(id);

   Element.addEventListener(doc, "keyup", function(event) {

      var ievent = new IEvent(event);

      if(ievent.src == textf) {
         textf.value = upper ? textf.value.toUpperCase() : textf.value.toLowerCase() ;
      }
   });
}

/**
 * Get window's parent iframe element.
 */
Element.getFrameElement = function(win) {
   return win.frameElement;
}

/**
 * Get window's parent dialog.
 */
Element.getDialog = function(win) {
   return win.frameElement._dialog;
}

/**
 * Create an input text field first, and remove it after getting the focus,
 * in order to get the focus for all fields in ie.
 */
Element.setFocus = function(doc) {
   if(_root.is_ie) {
      return;
   }

   var ie78 = _root.is_ie7 || _root.is_ie8;

   try {
      try {
         if(doc.activeElement && (doc.activeElement.type == "text")) {
            return;
         }

         var inputs = doc.all.tags("input");

         for(var i = 0; i < inputs.length; i++) {
            var ele = inputs[i];

            // @IEBUG, not disabled, IE7, 8 bug, will cause page layout wrong
            // in viewer report tab, if search is not visible, set focus
            // to search text will cause preference layout wrong
            if((ele.id || ele.name) && ele.type == "text" &&
               (!ie78 || !elem.disabled))
            {
               ele.focus();

               return;
            }
         }
      }
      catch(ex) {
         // ignore, document is not loaded now
      }

      // if open embedded pdf in IE, we should force set
      // focus, or the embedded pdf print dialog can't show.
      var pdfObject = doc.getElementById("pdfObject");

      // see @IEBUG
      if(ie78 && !pdfObject) {
         return;
      }

      var tempElem = Element.create("input", "", false, doc);
      tempElem.type = "text";
      tempElem.style.width = "10px";

      try {
         if(doc.body.firstChild) {
            Element.insert(doc.body, tempElem, doc.body.firstChild);
         }
         else {
            Element.append(doc.body, tempElem);
         }

         tempElem.focus();
      }
      catch(ex) {
         // ignore it
      }

      Element.remove(doc.body, tempElem);
   }
   catch(ex) {
      // ignore
   }
}

function findPosX(obj) {
   var curleft = 0;

   if(obj.offsetParent) {
      while(1) {
         curleft += obj.offsetLeft;

         if(!obj.offsetParent) {
            break;
         }

         obj = obj.offsetParent;
      }
   }
   else if(obj.x) {
      curleft += obj.x;
   }

   return curleft;
}

function findPosY(obj) {
   var curtop = 0;

   if(obj.offsetParent) {
      while(1) {
         curtop += obj.offsetTop;

         if(!obj.offsetParent) {
            break;
         }

         obj = obj.offsetParent;
      }
   }
   else if(obj.y) {
      curtop += obj.y;
   }

   return curtop;
}


/**
 * Get the operator to use when concatenating an op parameter to a servlet
 * name.
 */
function getConcat(servletName) {
   return servletName.indexOf("?") >= 0 ? "&" : "?";
}

function checkFocus() {
   if(!_root.is_ie) {
      return;
   }

   try {
      if(document.body) {
         if(document.readyState && document.readyState != "complete") {
            window.setTimeout("checkFocus()", 500);
         }
         else {
            Element.setFocus(document);
         }
      }
      else {
         window.setTimeout("checkFocus()", 500);
      }
   }
   catch(ex) {
   }
}

// it is not sure why we need check body's focus here for IE.
// actually if we set focus here, it may conflict with client HTML setFocus
// code for other input element.
// which will cause the behavior of backspace incorrect in IE.
try {
   //if(window.inDashboard || !showPageMenu) {
      checkFocus();
   //}
}
catch(ex) {
   // ignore
}

/**
 * Wrap the specified string.
 */
function wrapString(bigString, subLength) {
   var arr = bigString.split("\n");
   var subLength = subLength == null ? 20 : subLength;

   if(subLength > 0) {
      for(var i = 0; i < arr.length; i++) {
         var str = arr[i].length < 40 + subLength ? "" : arr[i];
         var idx;
         arr[i] = arr[i].length < 40 + subLength ? arr[i] : "";

         while(str.length > 0) {
            idx = str.indexOf(" ", 40);

            if(idx < 0) {
               var isLonger = str.length > 40 + subLength;
               idx = isLonger ? 40 + subLength : str.length;
               arr[i] += str.substring(0, idx) + (isLonger ? "<br/>" : "");
               str = str.substring(idx + 1);
            }
            else {
               idx = idx > 40 + subLength ? 40 + subLength : idx;
               arr[i] += str.substring(0, idx) + "<br/>";
               str = str.substring(idx + 1);
            }
         }
      }
   }

   return arr.join("<br>");
}

function getHTMLString(text) {
   if(text == null) {
      return text;
   }

   var text2 = "";

   for(var i = 0; i < text.length; i++) {
      var c = text.charAt(i);

      if(c == ' ') {
         text2 += "&nbsp;";
      }
      else if(c == '<') {
         text2 += "&lt;"
      }
      else if(c == '>') {
         text2 += "&gt;"
      }
      else if(c == '&') {
         text2 += "&amp;"
      }
      else {
         text2 += c;
      }
   }

   return text2;
}

function trace(msg) {
   try {
      console.log(msg);
   }
   catch(ex) {
      // ignore
   }
}

function isEmptyString(value) {
   return value == null || value == "";
}

/**
 * Check date format. If the action is "removeLocale", then transform the value
 * which format with locale to the one without locale. If the action is
 * "setLocale", then transform the value which format without locale to the one
 * with locale.
 * @return the target value in string.
 */
function checkDateFormat(action, value, fmt, defaultFmt) {
   if(isEmptyString(action) || isEmptyString(value) || isEmptyString(fmt)) {
      return value;
   }

   var container = new isii_XMLContainer();
   container.put("value", value);
   container.put("format", fmt);
   container.put("defaultFmt", defaultFmt);
   var url;

   try {
      url = repositoryServlet;
   }
   catch(ex) {
      url = servlet;
   }

   var ajax = new isii_AJAX(null, "checkDateFormat", action, url, false);
   ajax.send(container.writeXML());

   if(ajax.getError() != null) {
      alert(ajax.getError());
      return false;
   }

   var acontainer = new isii_XMLContainer();
   acontainer.parseXML(ajax.element);
   var result = acontainer.get("result");
   return isEmptyString(result) ? value : result;
}

function getOffset(evt) {
   if(evt.offsetY) {
      return evt;
   }

   var target = evt.target;

   if(target && target.offsetLeft) {
      target = target.parentNode;
   }

   var pageCoord = getPageCoord(target);

   var eventCoord = {x: window.pageXOffset + evt.clientX,
                     y: window.pageYOffset + evt.clientY};

   var offset = {offsetX: eventCoord.x - pageCoord.x,
                 offsetY: eventCoord.y - pageCoord.y};

   return offset;
}

function getPageCoord(element) {
   var coord = {x: 0, y: 0};

   while (element) {
      coord.x += element.offsetLeft;
      coord.y += element.offsetTop;
      element = element.offsetParent;
   }

   return coord;
}

function closeReplet(report, servlet, pageid, url) {
   if(parent && parent.closeReport) {
      parent.closeReport(report, servlet, pageid);
   }
   else {
      window.location = url;
   }
}

// cache css background image.
if(_root.is_ie) {
   try {
      document.execCommand("BackgroundImageCache", true, true);
   }
   catch(ex) {
      // ignore
   }
}

/**
 * Set the document cookie pair.
 */
function setCookie(name, value) {
   var days = 1;
   var exp  = new Date();
   exp.setTime(exp.getTime() + days * 24 * 60 * 60 * 1000);
   getRoot(self).document.cookie = name + "=" + escape(value) + ";expires=" +
      exp.toGMTString();
}

/**
 * Get the document cookie by name.
 */
function getCookie(name) {
   var arr = getRoot(self).document.cookie.match(
      new RegExp("(^| )" + name + "=([^;]*)(;|$)"));

   if(arr != null) {
      return unescape(arr[2]);
   }

   return null;
}

/**
 * Get format date value.
 */
function getDataView(data, servlet) {
   var ajax = new isii_AJAX(null, "getDateFormatView", null,
      servlet+"?oldValue=" + data, false);
   ajax.send(null);

   if(ajax.getError() != null) {
      alert(ajax.getError());
      return data;
   }

   var element = ajax.element;
   var container = new isii_XMLContainer();
   container.parseXML(element);

   return container.get("newValue");
}

/**
 * Destory replet by close browser.
 */
function unloadDestory(servlet, reportid) {
   //now it does not work well, so rollback it.
   /*
   var alt = null;
   var ctrl = null;
   var key = null;

   Element.addEventListener(window.document, "keydown", function(event) {
      var event0 = window.event ? window.event : event;
      alt = event0.altKey;
      ctrl = event0.ctrlKey;

      if(event0) {
         key = event0.keyCode;
      }
      else {
         key = event0.which;
      }
   });

   Element.addEventListener(getRoot(window), "beforeunload", function(event) {
      var event0 = window.event ? window.event : event;

      if(!window.istoc && ((event0.clientX <= 0 || event0.clientY < 0) ||
         alt && key == 115 || ctrl && key == 87))
      {
         setTimeout(function() {
            var ajax = new isii_AJAX(null, "Destroy", null,
               servlet + getConcat(servlet) + "&ID=" + reportid, false);
            ajax.notXML = true;
            ajax.send();
         }, 100);
      }
   });
   */
}

/**
 * Add html link for favicon.
 */
function Favicon() {
   var docHead = document.getElementsByTagName("head")[0];

   this.add = function addFavicon() {
       var url = '$(SERVLET)/favicon.ico';
       addLink(url, "icon");
       addLink(url, "shortcut icon");
   }

   function addLink(iconURL, relValue) {
      var link = document.createElement("link");
      link.type = "image/x-icon";
      link.rel = relValue;
      link.href = iconURL;
      docHead.appendChild(link);
   }
}

/**
 * Clear values of parameters.
 */
function clearValues(form) {
   for(var i = 0; i < form.length; i++) {
      var formObj = form.elements[i];
      var objTag = formObj.tagName.toLowerCase();

      if(objTag == "input") {
         var objType = formObj.type.toLowerCase();

         if(objType == "text" || objType == "password") {
            formObj.value = "";
         }
         else if(objType == "radio" || objType == "checkbox") {
            formObj.checked = false;
         }
      }
      else if(objTag == "textarea") {
         formObj.value = "";
      }
      else if(objTag == "select") {
         formObj.selectedIndex = -1;
      }
   }
}

/**
 * Compare a date grid comlumn.
 */
function compareDateColumn(obj1, obj2) {
   var idx = obj1.lastIndexOf("#time:");
   obj1 = idx < 0 ? obj1 : parseInt(obj1.substring(idx + 6));
   idx = obj2.lastIndexOf("#time:");
   obj2 = idx < 0 ? obj2 : parseInt(obj2.substring(idx + 6));
   return obj1 > obj2 ? 1 : (obj1 < obj2 ? -1 : 0);
}

/**
 * Compare a address column.
 */
function compareAddress(obj1, obj2) {
   var ips1 = obj1.split(".");

   if(ips1.length != 4) {
      return -1;
   }

   var ips2 = obj2.split(".");

   if(ips2.length != 4) {
      return 1;
   }

   for(var i = 0; i < 4; i++) {
      if(parseInt(ips1[i]) > parseInt(ips2[i])) {
         return 1;
      }
      else if(parseInt(ips1[i]) < parseInt(ips2[i])) {
         return -1;
      }
   }

   return 0;
}

/**
 * Get the timestamp of the file.
 */
function getTimestamp(name) {
   if(name == "ExploreView") {
      return $(ExploreView_Timestamp);
   }
   else if(name == "ExploratoryAnalyzer") {
      return $(ExploratoryAnalyzer_Timestamp);
   }

   return 0;
}

function isAcrobatPluginInstall() {
   var installed = false;

   if(navigator.plugins && navigator.plugins.length) {
      for(x = 0; x < navigator.plugins.length; x++) {
         if(navigator.plugins[x].name.indexOf("Adobe Reader") == 0 ||
            navigator.plugins[x].name.indexOf("Adobe Acrobat") == 0) {
            installed = true;
            break;
         }
      }
   }
   if(!installed) {
      for(x = 2; x < 10; x++) {
         try {
            oAcro = eval("new ActiveXObject('PDF.PdfCtrl." + x + "');");

            if(oAcro) {
               installed = true;
               break;
            }
         }
         catch(e) {
            installed = false;
         }
      }
   }
   if(!installed) {
      try {
         oAcro4 = new ActiveXObject('PDF.PdfCtrl.1');

         if(oAcro4) {
            installed = true;
         }
      }
      catch(e) {
         installed = false;
      }
   }
   if(!installed) {
      try {
         oAcro7 = new ActiveXObject('AcroPDF.PDF.1');

         if(oAcro7) {
            installed = true;
         }
      }
      catch(e) {
         installed = false;
      }
   }

   return installed;
}

function getBrowserType() {
   if(_root.is_ie || _root.is_mac_ie) {
      return "IE";
   }
   else if(_root.is_edge) {
      return "Edge";
   }
   else if(_root.is_chrome) {
      return "Chrome";
   }
   else if(_root.is_khtml) {
      return "Safari";
   }
   else if(_root.is_opera) {
      return "Opera";
   }
   else if(_root.is_firefox) {
      return "Firefox";
   }
   else {
      return "Others";
   }
}

function showLoading() {
   var loadingDiv = Element.get("loadingdiv");
   var loadingImg = Element.get("loadingimg");

   if(!loadingDiv) {
      loadingDiv = Element.create("div", "loading", false);
      loadingDiv.id = "loadingdiv";
      loadingDiv.style.position = "absolute";
      loadingDiv.style.top =  Math.min(Tool.getWindowHeight(self),
                                       Tool.getWindowHeight(getRoot(self)))/2;
      loadingDiv.style.left = Math.min(Tool.getWindowWidth(self),
                                       Tool.getWindowWidth(getRoot(self))) / 2;
      var loadingTable = Element.create("table", "loadingTable", false);
      Element.append(loadingDiv, loadingTable);
      var temprow = loadingTable.insertRow(0);
      var tempcell = temprow.insertCell(0);
      loadingImg = Element.create("img", "loadingImg");
      loadingImg.setAttribute("alt", "Loading");
      loadingImg.id = "loadingimg";
      loadingImg.src = "$(SERVLET)?" +
         "op=resource&style=true&theme=true&type=portal&name=ajax_loading%2egif";
      Element.append(tempcell, loadingImg);
      Element.append(document.body,  loadingDiv);
   }

   loadingDiv.style.display = "";
   loadingImg.style.display = "";
}

function hideLoading() {
   var loadingDiv = Element.get("loadingdiv");

   if(loadingDiv) {
      loadingDiv.style.display = "none";
   }
}

/**
 * When drag element don't select the element or others.
 */
function notDragSelect(elem) {
   var clickElem = function(event) {
      if(_root.is_ie10 || _root.is_ie8 || _root.is_ie11 ||
         _root.is_chrome || _root.is_khtml)
      {
         if(event) {
            // ie8 does not support preventDefault
            (event.preventDefault) ? event.preventDefault()
               : event.returnValue = false;
         }
      }
   };

   Element.addEventListener(elem, "mousedown", clickElem);
}
