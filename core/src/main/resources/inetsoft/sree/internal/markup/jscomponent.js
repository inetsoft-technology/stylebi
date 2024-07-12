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
/**
 * The base class for all javascript gui components.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */

/**
 * Constructor
 *
 * @param doc document.
 */
isii_JSComponent = function(doc) {
   this._class = "isii_JSComponent";
   this._extend("isii_Object");
   var _this = this._this;

   this.rootDiv = Element.create("div", _this._class, true, doc);

   /**
    * Set the position of the component.
    * @public.
    */
   this.setPosition = setPositionFunction;
   this.setPosition._class = this._class;

   function setPositionFunction(left, top) {
      _this.rootDiv.style.position = "absolute";
      _this.rootDiv.style.left = Element.toPixel(left) + "px";
      _this.rootDiv.style.top = Element.toPixel(top) + "px";
   }

   /**
    * Set the size of the component.
    * @public.
    */
   this.setSize = setSizeFunction;
   this.setSize._class = this._class;

   function setSizeFunction(width, height) {
      _this._width = width;
      _this._height = height;

      // IE doesn't like NaN
      if(!isNaN(width)) {
         // IE doesn't like negative value
         try {
            _this.rootDiv.style.width = Element.toPixel(width) + "px";
         }
         catch(error) {
            // ignore it
         }
      }

      if(!isNaN(height)) {
         try {
            _this.rootDiv.style.height = Element.toPixel(height) + "px";
         }
         catch(error) {
            // ignore it
         }
      }
   }

   /**
    * Get the size of the component.
    * @public.
    */
   this.getSize = getSizeFunction;
   this.getSize._class = this._class;

   function getSizeFunction() {
      var size = {};
      size.width = calculateLength(this._this.rootDiv.style.width, "+", 0);
      size.height = calculateLength(this._this.rootDiv.style.height, "+", 0);
      return size;
   }
}

/**
 * Used for generating javascript popup window in parenet window.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */

/**
 * Constructor
 *
 * @param owner the parent window.
 * @param title the title string.
 * @param icon the url of the title icon.
 * @param vparent the virtal parent.
 * @param callback function to be executed when the component has finished
 *                 loading.
 * @param callbackOwner the owner of the callback function.
 */
isii_JSDialog = function(owner, title, icon, vparent, callback, callbackOwner) {
   if(vparent && !vparent.runScript) {
      vparent = null;
   }

   this._class = "isii_JSDialog";
   var _this = this._this;
   this.owner = owner;
   this.vparent = vparent;
   this.title = title;

   Element.addEventListener(this.owner, "unload", closeFunction);

   var doc = vparent ? vparent.document : owner.document;
   var body = doc.body;

   this._extend("isii_JSComponent", doc);

   var container = null;
   var zidx = 0;

   var positionSet = false;
   var titleheight = 22;
   var moveObj = {};

   var iframeBG = null;
   var SERVLET_PREF = "$(SERVLET)";
   var mask = null;
   var titlebg = null;
   var titleBar = null;
   var closeBtn = null;
   var titleInner = null;
   var titleImage = null;
   var content = null;

   container = this.rootDiv;
   Element.append(body, container);
   container.className = "dialog_container";
   zidx = 100 + getParentLevels() * 5;
   container.style.zIndex = zidx;
   container.style.display = "none";

   iframeBG = Element.create("iframe", "dialog_IframBG", false, doc);
   iframeBG.setAttribute("title", "Dialog background");
   Element.addEventListener(iframeBG, "load", initializeMask.bind(this));
   iframeBG.style.zIndex = zidx;
   iframeBG.frameBorder = 0;
   iframeBG.scrolling = "no";

   SERVLET_PREF = SERVLET_PREF.indexOf("?") != -1 ?
   SERVLET_PREF + "&" : SERVLET_PREF + "?";
   iframeBG.src = SERVLET_PREF +
      "op=Resource&name=%2finetsoft%2fsree%2fweb%2fssl_blank.html";
   iframeBG.style.position = "absolute";
   Element.append(container, iframeBG);

   function initializeMask() {
      Element.removeEventListener(iframeBG, "load", initializeMask);
      mask = Element.create("iframe", "dialog_mask", false, doc);
      mask.setAttribute("title", "Dialog mask");
      Element.addEventListener(mask, "load", initializeContent.bind(this));
      mask.style.display = "";
      mask.style.zIndex = zidx - 1;
      mask.allowTransparency = "true";
      mask.src = SERVLET_PREF +
         "op=Resource&name=%2finetsoft%2fsree%2finternal%2fmarkup" +
         "%2fmask_blank%2ehtml";
      Element.append(body, mask);
   }

   this.setMaskTransparency = function(transparency) {
      mask.allowTransparency = transparency;
   }

   function initializeContent() {
      Element.removeEventListener(mask, "load", initializeContent);
      titlebg = Element.create("div", "dialog_titleImage_middle", false, doc);
      Element.append(container, titlebg);
      titlebg.style.zIndex = zidx + 1;
      titlebg.style.height = Element.toPixel(titleheight);

      titleBar = Element.create("div", "dialog_titleBar", false, doc);
      titleBar.style.zIndex = zidx + 3;
      titleBar.style.height = Element.toPixel(titleheight);
      Element.append(container, titleBar);

      closeBtn = Element.create("div", "dialog_closeBtn", false, doc);
      Element.append(titleBar, closeBtn);

      // In firefox, the title bar corners won't be displayed over a viewsheet
      // because the contain transparent pixels. The only way to fix this would
      // be to set the wmode parameter of the flash plugin to transparent, but
      // that breaks the mouse wheel support. The corner images could be masked
      // with white, but it creates a visible white corner and doesn't look
      // good in most situations. For the time being, we are keeping the current
      // behavior because it can only be visible when the portal preferences
      // dialog is opened when a viewsheet is open.
      titleInner = Element.create("div", "dialog_titleInner", false, doc);
      titleInner.style.zIndex = zidx + 3;
      Element.append(titleBar, titleInner);

      titleImage = Element.create("div", "dialog_titleImage_left", false, doc);
      Element.append(titleBar, titleImage);
      titleImage.style.height = Element.toPixel(titleheight);

      if(icon) {
         titleInner.innerHTML = "<img src='" + icon + "'/>&nbsp;&nbsp;" + title;
      }
      else {
         titleInner.innerHTML = title;
      }

      content = Element.create("div", "dialog_content", false, doc);
      content.style.zIndex = zidx + 1;
      Element.append(container, content);

      titleInner.style.cursor = "move";

      Element.addEventListener(titleInner, "mousedown", mousedown);

      Element.addEventListener(doc, "mousemove", mousemove);
      Element.addEventListener(doc, "mouseup", mouseup);
      Element.addEventListener(doc, "keydown", keydown);

      Element.addEventListener(mask.contentWindow.document,
                               "mousemove", mousemove);
      Element.addEventListener(mask.contentWindow.document, "mouseup", mouseup);

      Element.addEventListener(closeBtn, "click", this.close.bind(this));

      if(callback) {
         callback.call(callbackOwner ? callbackOwner : _this, _this);
      }
   }

   /**
    * Show pupup window.
    *
    * @param modal if <code>true</code>, the mask div will show.
    * @open.
    */
   this.show = showFunction;
   this.show._class = this._class;

   function showFunction(modal) {
      _this.modal = modal;

      if(modal) {
         mask.style.display = "";
      }
      else {
         mask.style.display = "none";
      }

      container.style.display = "";

      this.fixSize();
   }

   /**
    * Adjust cotent width in Vista where content's width is
    * greater than container's
    *
    * @param modal if <code>true</code>, the mask div will show.
    * @open.
    */
   this.fixSize = fixSizeFunction;
   this.fixSize._class = this._class;

   function fixSizeFunction() {
      if(container.offsetWidth < content.offsetWidth) {
         var diff = content.offsetWidth - container.offsetWidth;
         var newWidth = calculateLength(content.style.width, "-", diff);
         content.style.width = Element.toPixel(newWidth);

         try{
            var iframe = content.getElementsByTagName("iframe")[0];
            iframe.style.width = Element.toPixel(newWidth - 2);
         }
         catch(e) {
         }
      }
   }

   /**
    * Get the content div element.
    *
    * @return the content div.
    * @open.
    */
   this.getContentPane = getContentPaneFunction;
   this.getContentPane._class = this._class;

   function getContentPaneFunction() {
      return content;
   }

   /**
    * Set the position of the popup window relative to the owner window.
    *
    * @param left the value of the left attribute.
    * @param top the value of the top attribute.
    * @open.
    */
   this.setPosition = setPositionFunction;
   this.setPosition._class = this._class;

   function setPositionFunction(left, top) {
      this._super.setPosition(left, top);
      positionSet = true;
   }

   /**
    * Set the size of the popup window.
    *
    * @param width the width of the popup window.
    * @param height the height of the popup window's content pane.
    * @open.
    */
   this.setSize = setSizeFunction;
   this.setSize._class = this._class;

   function setSizeFunction(width, height) {
      this._super.setSize(width, height);
      iframeBG.style.width = Element.toPixel(width);
      iframeBG.style.height = Element.toPixel(height);
      titleBar.style.width = Element.toPixel(width);

      if(_root.is_ie) {
         content.style.width = Element.toPixel(width);
      }
      else {
         content.style.width = Element.toPixel(width - 2);
      }

      content.style.height = Element.toPixel(height - titleheight);
      titlebg.style.width = Element.toPixel(width - 13);
      _this.fixSize();

      // center this dialog if not set position before
      if(!positionSet) {
         var win = this.vparent ? this.vparent : this.owner;
         var left = (Tool.getWindowWidth(win) - width) / 2;
         var top = (Tool.getWindowHeight(win) - height) / 2;
         _this.setPosition(left, top);
         positionSet = false;
      }
   }

   /**
    * Add close popup window listener.
    *
    * @param vFunction the callback function.
    * @param vObj the specified object.
    * @open.
    */
   this.addCloseListener = addCloseListenerFunction;
   this.addCloseListener._class = this._class;

   function addCloseListenerFunction(vFunction, vObj) {
      _this.addEventListener("close", vFunction, vObj);
   }

   /**
    * Add finish listener.
    *
    * @param vFunction the callback function.
    * @param vObj the specified object.
    * @open.
    */
   this.addFinishListener = addFinishListenerFunction;
   this.addFinishListener._class = this._class;

   function addFinishListenerFunction(vFunction, vObj) {
      _this.addEventListener("finish", vFunction, vObj);
   }

   /**
    * Remove the close listener.
    *
    * @param vFunction the callback function.
    * @param vObj the specified object.
    * @open.
    */
   this.removeCloseListener = removeCloseListenerFunction;
   this.removeCloseListener._class = this._class;

   function removeCloseListenerFunction(vFunction, vObj) {
      _this.removeEventListener("close", vFunction, vObj);
   }

   /**
    * Remove the finish listener.
    *
    * @param vFunction the callback function.
    * @param vObj the specified object.
    * @open.
    */
   this.removeFinishListener = removeFinishListenerFunction;
   this.removeFinishListener._class = this._class;

   function removeFinishListenerFunction(vFunction, vObj) {
      _this.removeEventListener("finish", vFunction, vObj);
   }

   function mousedown(evt) {
      var ievent = new IEvent(evt);
      var src = ievent.src;

      moveObj.src = src;

      moveObj._x = ievent.pageX - parseInt(container.style.left);
      moveObj._y = ievent.pageY - parseInt(container.style.top);

      if(!_this.modal) {
         mask.style.display = "";
      }
   };

   function mousemove(ev) {
      var ievent = new IEvent(ev);

      if(moveObj.src) {
         container.style.left = ievent.pageX - moveObj._x;
         container.style.top = ievent.pageY - moveObj._y;
      }
   };

   function mouseup(evt) {

      delete moveObj.src;

      if(!_this.modal) {
         mask.style.display = "none";
      }
   };

   function keydown(e) {
      var ievent = new IEvent(e);

      if(ievent.keyCode == 27) {
         _this.close();
      }
   };

   this.toString = function() {
      return "ISII_DIALOG[" + _this.url + "]";
   }

   this.equals = function(dialog) {
      return _this.url == dialog.url;
   }

   this.getUrl = function() {
      return _this.url;
   }

   this.hide = function() {
      container.style.display = "none";
      mask.style.display = "none";
   }

   /**
    * Close popup window.
    * @open.
    */
   this.close = closeFunction;
   this.close._class = this._class;

   function closeFunction(delay, time) {
      if(delay == true) {
         Element.setTimeout(window, time? time : 50, _this, doClose);
      }
      else {
         doClose();
      }
   }

   function doClose() {
      try {
         Element.removeEventListener(_this.owner, "unload", closeFunction);
         _this.owner.document.body.focus();
      }
      catch(e) {
      }

      try {
         Element.removeEventListener(titleInner, "mousedown", mousedown);
      }
      catch(e) {
      }

      Element.removeEventListener(doc, "keydown", keydown);
      Element.removeEventListener(doc, "mousemove", mousemove);
      Element.removeEventListener(doc, "mouseup", mouseup);
      container.style.display = "none";
      mask.style.display = "none";
      Element.remove(body, container);
      Element.remove(body, mask);
      _this.dispatchEvent("close", {"src" : this});
   }

   /**
    * Get the levels of the parent.
    */
   function getParentLevels() {
      var tempElement = owner;
      var levels = 0;

      while(true) {
         try {
            if(Element.getFrameElement(tempElement)) {
               if(Element.getDialog(tempElement)) {
                  levels++;
               }

               if(tempElement == tempElement.parent) {
                  break;
               }

               tempElement = tempElement.parent;
            }
            else {
               break;
            }
         }
         catch(ex) {
            break;
         }
      }

      return levels;
   }
}


/**
 * Used for generating javascript popup window which contain an iframe in
 * content pane. The user can set the url of the iframe.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */

/**
 * Constructor
 *
 * @param owner the parent window.
 * @param title the title string.
 * @param icon the url of the title icon.
 * @param vparent the virtal parent.
 * @param url the url of the iframe content.
 * @param callback function to be executed when the component has finished
 *                 loading.
 * @param callbackOwner the owner of the callback function.
 */
isii_JSIFrameDialog = function(owner, title, icon, vparent, url,
                               callback, callbackOwner, config)
{
   this._class = "isii_JSIFrameDialog";
   var _this = null;
   var doc = null;
   var body = null;
   var iFrame = null;
   var titleheight = 22;

   var ocallback = callback;
   var ocbowner = callbackOwner;
   config = config == null ? new Object() : config;
   var sel = config.selectable ? config.selectable : false;
   var ignoreEscape = config.ignoreEscape ? config.ignoreEscape : false;

   callback = function() {
      _this = this._this;
      doc = _this.vparent ? _this.vparent.document : _this.owner.document;
      body = doc.body;

      if(url) {
         this.setURL(url);
      }

      if(ocallback) {
         ocallback.call(ocbowner ? ocbowner : _this, _this);
      }
   };

   callbackOwner = this;

   this._extend("isii_JSDialog", owner, title, icon, vparent,
               callback, callbackOwner);

   /**
    * Set the url of the iframe.
    *
    * @param url the url of the iframe.
    * @open.
    */
   this.setURL = setURLFunction;
   this.setURL._class = this._class;

   function setURLFunction(url) {
      _this.url = url;

      if(iFrame == null) {
         var content = this._super.getContentPane();
         iFrame = Element.create("iframe", "dialog_iFrame", sel, doc);
         iFrame.setAttribute("title", _this.title);
         Element.append(content, iFrame);
         iFrame.src = url;
         iFrame.name = "iframeName";
         iFrame.id = "iframeId";
         iFrame.frameBorder = "0";
         iFrame._dialog = _this;
         Element.addEventListener(iFrame, "load", function(e) {
            Element.addEventListener(iFrame.contentWindow.document, "keydown",
                                     function(e) {
               var ievent = new IEvent(e);

               if(ievent.keyCode == 27 && !ignoreEscape) {
                  _this.close();
               }
            });
         });
      }
      else {
         iFrame.src = url;
      }
   }

   /**
    * Get the content pane.
    *
    * @ return the iframe content window.
    * @ open.
    */
   this.getContentPane = getContentPaneFunction;
   this.getContentPane._class = this._class;

   function getContentPaneFunction() {
      if(iFrame != null) {
         if(iFrame.contentWindow) {
            return iFrame.contentWindow;
         }
         else {
            return iFrame;
         }
      }

      return null;
   }

   /**
    * Set the size of the popup window.
    *
    * @param width the width of the popup window.
    * @param height the height of the popup window's iframe.
    * @open.
    */
   this.setSize = setSizeFunction;
   this.setSize._class = this._class;

   function setSizeFunction(width, height) {
      this._super.setSize(width, height);
      iFrame.style.width = Element.toPixel(width - 2);
      iFrame.style.height = Element.toPixel(height - titleheight);
      this.fixSize();
   }
}

/**
 * Manages all the ToolTips in the window.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */

/**
 * Constructor.
 * @param owner the parent window.
 */
isii_TooltipManager = function(owner) {
   this._class = "isii_TooltipManager";

   this.owner = owner;
   var doc = this.owner.document;

   this.init = function() {
      if(!this.tipdiv) {
         this.tipdiv = Element.create("div", "isii_tooltip_div", false, doc);
         this.tipdiv.style.position = "absolute";
         this.tipdiv.style.zIndex = 1000;
         this.tipdiv.style.display = "none";
         Element.append(doc.body, this.tipdiv);

         // add a transparent iframe background to fix the problem which the
         // tooltip div will be hidden under the flash or a list element
         if(!_root.is_ie) {
            var iframeBG =
               Element.create("iframe", "isii_tooltip_IframBG", false, doc);
            iframeBG.setAttribute("title", "Dialog tooltip background");
            iframeBG.frameBorder = 0;
            iframeBG.scrolling = "no";
            var SERVLET_PREF = "$(SERVLET)";
            SERVLET_PREF = SERVLET_PREF.indexOf("?") != -1 ?
            SERVLET_PREF + "&" : SERVLET_PREF + "?";
            iframeBG.src = SERVLET_PREF +
               "op=Resource&name=%2finetsoft%2fsree%2fweb%2fssl_blank.html";
            iframeBG.style.position = "absolute";
            iframeBG.style.width = "100%";
            iframeBG.style.height = "100%";
            iframeBG.style.zIndex = this.tipdiv.style.zIndex + 1;
            Element.append(this.tipdiv, iframeBG);
         }

         var table = Element.create("table", "isii_tooltip_table", false, doc);
         table.style.zIndex = this.tipdiv.style.zIndex + 2;
         Element.append(this.tipdiv, table);

         var tbody = Element.create("tbody", null, false, doc);
         Element.append(table, tbody);
         var tr = Element.create("tr", null, false, doc);
         Element.append(tbody, tr);
         var td = Element.create("td", null, false, doc);
         Element.append(tr, td);
         this.tiptd = td;
      }
   }

   Element.initComponent(doc, this);

   /**
    * Registers a element for tip manager.
    * @param elem the html element.
    * @param tips the text that is shown when the tool tip is displayed.
    * @public.
    */
   this.registerTip = registerTipFunction;
   this.registerTip._class = this._class;

   function registerTipFunction(elem, tips) {
      deregisterTipFunction(elem);
      elem._showtipfun = showTooltip.bind(this, tips);
      elem._hidetipfun = hideTooltip.bind(this);
      Element.addEventListener(elem, "mouseover", elem._showtipfun);
      Element.addEventListener(elem, "mouseout", elem._hidetipfun);
   }

   /**
    * Removes a element from tooltip control.
    * @param elem the html element.
    * @public.
    */
   this.deregisterTip = deregisterTipFunction;
   this.deregisterTip._class = this._class;

   function deregisterTipFunction(elem) {
      if(elem._showtipfun) {
         Element.removeEventListener(elem, "mouseover", elem._showtipfun);
      }

      if(elem._hidetipfun) {
         Element.removeEventListener(elem, "mouseout", elem._hidetipfun);
      }
   }

   /**
    * Show tooltip when mouse over.
    */
   function showTooltip(tips, evt) {
      if(!this.tipdiv) {
         this.init();
      }

      var win =  this.owner;
      var ievent = new IEvent(evt, win);
      this.tipdiv.status = 'over';
      win._tipdiv = this.tipdiv;
      var showtip = 'if(_tipdiv.status == "over") {_tipdiv.style.display = ""}';
      win.tip_delay = win.setTimeout(showtip, 800);

      //this.tiptd.innerHTML = getHTMLString(tips);
      this.tiptd.innerHTML = tips;
      var tabPanel = Element.get("tabbedPanel");
      var offsetleft = 10;
      var offsettop = 10;

      if(tabPanel) {
         var frame = tabPanel.contentWindow.frames[0];
         var vLen = Tool.getWindowHeight(tabPanel.contentWindow) -
            calculateLength(Element.getFrameElement(frame).style.height, "-",0);
         var hLen = Tool.getWindowWidth(tabPanel.contentWindow) -
            calculateLength(Element.getFrameElement(frame).style.width, "-", 0);
         offsetleft += tabPanel.offsetLeft + hLen;
         offsettop += tabPanel.offsetTop + vLen;
      }

      this.tipdiv.style.left = ievent.pageX + offsetleft;
      this.tipdiv.style.top = ievent.pageY + offsettop;
   }

   /**
    * Hide tooltip when mouse out.
    */
   this.hideTooltip = hideTooltip;

   function hideTooltip() {
      if(!this.tipdiv) {
         this.init();
      }
      var win =  this.owner;

      if(win.tip_delay) {
         win.clearTimeout(win.tip_delay);
      }

      this.tipdiv.style.display = "none";
      this.tipdiv.status = 'out';
   }
}

window.createTipManager = function() {
   if(!window._tooltipManager) {
      var manager = new isii_TooltipManager(window);
      window._tooltipManager = manager;
   }
}

/**
 * Get a tooltip manager.
 * @param owner the parent window.
 * @static.
 * @public.
 */
isii_TooltipManager.getManager = function(owner) {
   var win = owner ? owner : window;
   win.runScript("window.createTipManager()");

   return win._tooltipManager;
}

/**
 * Used for getting DnD manager.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 * @depend util.js
 */

/**
 * Constructor.
 * @param root the root window.
 */
isii_DndManager = function(root) {
   this._class = "isii_DndManager";
   this._extend("isii_Object");
   var _this = this._this;
   var readyDrag = false;
   var inDraging = false;
   this.root = root;
   var dragSource = null;
   var dragObject = null;
   var dragDiv = null;
   var vdocument = _this.root.document;
   var cssRef = document.createElement('link');
   cssRef.setAttribute("type", "text/css");
   cssRef.setAttribute("rel", "stylesheet");
   cssRef.setAttribute("href",
     "$(SERVLET)?op=resource&type=portal&combine=true&name=tree-portal.css");
   Element.append(document.getElementsByTagName("head")[0], cssRef);
   /**
    * Accept the drop to target action.
    * @public.
    */
   this.accept = acceptFunction;
   this.accept._class = this._class;

   function acceptFunction() {
      if(dragSource != null && dragSource.endDrag) {
         dragSource.endDrag(true);
      }
   }

   /**
    * Reject the drop to target action.
    * @public.
    */
   this.reject = rejectFunction;
   this.reject._class = this._class;

   function rejectFunction() {
      if(dragSource != null && dragSource.endDrag) {
         dragSource.endDrag(false);
      }
   }

   /**
    * Register a drag source element.
    * @param element the specified html element as dragging source.
    * @param object the specified object for dragging.
    * @public.
    */
   this.registerDragSource = registerDragSourceFunction;
   this.registerDragSource._class = this._class;

   function registerDragSourceFunction(element, object) {
      Element.setSelectable(element, false);
      element._dragSource = object;
      Element.addEventListener(element, "mousedown", mouse_down);
      Element.addEventListener(element, "mousemove", mouse_move);
      Element.addEventListener(element, "mouseup", mouse_up);
   }

   /**
    * De-register a drag source element.
    * @param element the specified html element as dragging source.
    * @param object the specified object for dragging.
    * @public.
    */
   this.deregisterDragSource = deregisterDragSourceFunction;
   this.deregisterDragSource._class = this._class;

   function deregisterDragSourceFunction(element, object) {
      Element.removeEventListener(element, "mousedown", mouse_down);
      Element.removeEventListener(element, "mousemove", mouse_move);
      Element.removeEventListener(element, "mouseup", mouse_up);
   }

   function mouse_down(event){
      isii_DndManager.getManager().readyDrag = true;
   }

   function mouse_move(event){
      if(isii_DndManager.getManager().readyDrag &&
         !isii_DndManager.getManager().inDraging)
      {
         isii_DndManager.getManager().inDraging = true;
         initDraggable(event);
      }
   }

   function mouse_up(event){
      isii_DndManager.getManager().readyDrag = false;
      isii_DndManager.getManager().inDraging = false;
   }

   /**
    * Register a drop target element.
    * @param element the specified html element as dropping target.
    * @param object the specified object for dropping.
    * @public.
    */
   this.registerDropTarget = registerDropTargetFunction;
   this.registerDropTarget._class = this._class;

   function registerDropTargetFunction(element, object) {
      Element.setSelectable(element, false);
      element._dragTarget = object;

      Element.addEventListener(element, "mousemove", dragOver);
      Element.addEventListener(element, "mouseout", dragExit);
      Element.addEventListener(element, "mouseup", dragRelease);
   }

   /**
    * De-register a drop target element.
    * @param element the specified html element as dropping target.
    * @param object the specified object for dropping.
    * @public.
    */
   this.deregisterDropTarget = deregisterDropTargetFunction;
   this.deregisterDropTarget._class = this._class;

   function deregisterDropTargetFunction(element, object) {
      Element.removeEventListener(element, "mousemove", dragOver);
      Element.removeEventListener(element, "mouseout", dragExit);
      Element.removeEventListener(element, "mouseup", dragRelease);
   }

   /**
    * Register window for dragging action.
    * @param window the specified window that will response for dragging action.
    * @public.
    */
   this.registerWindow = registerWindowFunction;
   this.registerWindow._class = this._class;

   function registerWindowFunction(window) {
      if(!window._dragListener) {
         window._drag_mousemove = mouseMove.bind(this, window);
         Element.addEventListener(window.document, "mousemove", window._drag_mousemove);
         Element.addEventListener(window.document, "mouseup", removeDragObj);
         window._dragListener = true;
      }
   }

   /**
    * De-register window for dragging action.
    * @param window the specified window that will response for dragging action.
    * @public.
    */
   this.deregisterWindow = deregisterWindowFunction;
   this.deregisterWindow._class = this._class;

   function deregisterWindowFunction(window) {
      if(window._dragListener) {
         Element.removeEventListener(window.document, "mousemove",
            mouseMove.bind(this, window));
         Element.removeEventListener(window.document, "mouseup", removeDragObj);
         window._dragListener = false;
      }
   }

   /**
    * Mouse move.
    * @private.
    */
   function mouseMove(window, event) {
      if(dragSource != null && dragDiv != null) {
         var root = getRoot(window);
         dragDiv.style.display = "";

         var xpos = event.screenX;
         var ypos = event.screenY;
         var safari = navigator.userAgent.toLowerCase().indexOf("safari") >= 0;

         if(root.screenTop && !safari) {
            xpos -= root.screenLeft;
            ypos -= root.screenTop;
         }
         else if(dragSource._class == "isii_JSTree" && _root.is_chrome) {
            xpos = xpos - root.screenX - root.outerWidth + root.innerWidth;
            ypos = ypos - root.screenY - root.outerHeight + root.innerHeight;
         }
         else {
            xpos = xpos - root.screenX - root.outerWidth + root.innerWidth;
            ypos = ypos - root.screenY - root.outerHeight +
               root.innerHeight + 30;
         }

         var position = {x: xpos, y: ypos};
         dragDiv.style.left = Element.toPixel(position.x + 10);
         dragDiv.style.top = Element.toPixel(position.y);
      }
   }

   /**
    * Remove drag object.
    * @private.
    */
   function removeDragObj() {
      if(dragDiv != null) {
         dragDiv.style.display = "none";
      }

      dragSource = null;
      dragObject = null;
   }

   /**
    * Init draggable object.
    * @private.
    */
   function initDraggable(event) {
      var ievent = new IEvent(event);
      var element = (new IEvent(event)).src;
      var object = element._dragSource;
      var vdocument = _this.root.document;

      if(object && object.startDrag) {
         dragSource = object;
         dragObject = object.startDrag(element, event);

         if(!dragObject) {
            if(dragDiv) {
               Element.remove(vdocument.body, dragDiv);
               dragDiv = null;
            }

            return;
         }

         if(dragDiv == null) {
            dragDiv = Element.create("div", "inetsoft_drag_label",
               false, vdocument);
            Element.append(vdocument.body, dragDiv);
            dragDiv.style.position = "absolute";
            dragDiv.style.zIndex  = 1111;
            dragDiv.style.display = "none";
         }

         if(dragObject.img) {
            dragDiv.innerHTML = "<img src=\"" + dragObject.img + "\">";
         }
         else if(dragObject.label) {
            var ihtml = "<table>";

            if(dragObject.objects) {
               for(var i = 0; i < dragObject.objects.length; i++) {

                  ihtml += "<tr><td class=\"" + dragObject.objects[i].icon +
                        "\"></td><td> " + dragObject.objects[i].label +
                        "</td></tr>";

                  if(i == 9) {
                     ihtml += "<tr><td colspan='2'>&nbsp;&nbsp;&nbsp;&nbsp;...</td></tr>";
                     break;
                  }
               }

               ihtml += "</table>";
            }
            else {
               var iconClsName = element.getAttribute("iconClass") ?
                                 element.getAttribute("iconClass") :
                                 (element.className ? element.className : "");
               // when selected element is <a></a>, use the parent iconClass.
               var pelement = element.parentNode;
               iconClsName = iconClsName == "" ?
                  pelement && pelement.getAttribute("iconClass") ?
                  pelement.getAttribute("iconClass") : "" : iconClsName;
               ihtml = "<table><tr><td " +
                       (iconClsName != "" ? "class=\"" + iconClsName + "\">" : ">") +
                       "</td><td> " + dragObject.label + "</td></tr></table>";
            }

            dragDiv.innerHTML = ihtml;

            // for tree nodes.
            if(object.fixCurDrags) {
               object.fixCurDrags(element);
            }
         }
         else {
            dragDiv.innerHTML = dragObject.toString();
         }
      }
   }

   /**
    * Exit a drop target area.
    * @private.
    */
   function dragExit(event) {
      var ievent = new IEvent(event);
      var element = (new IEvent(event)).src;
      var object = element._dragTarget;

      if(object && object.dragExit && dragObject) {
         dragObject.event = event;
         object.dragExit(dragObject);
      }
   }

   /**
    * Mouse move over a drop target area.
    * @private.
    */
   function dragOver(event) {
      var ievent = new IEvent(event);
      var element = (new IEvent(event)).src;
      var object = element._dragTarget;

      if(object && object.autoScroll && dragObject) {
         dragObject.event = event;
         object.autoScroll(dragObject);
      }

      if(object && object.dragOver && dragObject) {
         dragObject.event = event;
         object.dragOver(dragObject);
      }
   }

   /**
    * Mouse released on the drop target.
    * @private.
    */
   function dragRelease(event) {
      var ievent = new IEvent(event);
      var element = (new IEvent(event)).src;
      var object = element._dragTarget;

      if(object && object.dragRelease && dragObject) {
         dragObject.event = event;
         object.dragRelease(dragObject);
      }
      if(dragDiv != null) {
         dragDiv.style.display = "none";
      }

      isii_DndManager.getManager().readyDrag = false;
      isii_DndManager.getManager().inDraging = false;

      dragSource = null;
      dragObject = null;
   }
}

window.createDndManager = function() {
   if(!window.dndManager) {
      var dndManager = new isii_DndManager(window);
      dndManager.registerWindow(window);
      window.dndManager = dndManager;
   }
}

/**
 * Get a DnD manager.
 * @static.
 * @public.
 */
isii_DndManager.getManager = function() {
   var _root = getRoot(self, true);

   if(_root) {
      _root.runScript("window.createDndManager()");

      return _root.dndManager;
   }

   return null;
}

/**
 * Used for data communication between drag source and drop target.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */

/**
 * Constructor.
 * @param dragAction the drag action.
 * @param img the image for dragging object if possible.
 * @param label the label for dragging object if possible.
 * @param dragData the data for dragging object if possible.
 * @param event the mouse event of drop action.
 */
isii_DragObject = function(dragAction, img, label, dragData, event, objects) {
   this._class = "isii_DragObject";
   this._extend("isii_Object");
   var _this = this._this;

   this.dragAction = dragAction;
   this.img = img;
   this.label = label;
   this.dragData = dragData;
   this.event = event;
   this.objects = objects; //for drag multiple elements.
}

isii_DndManager.getManager().registerWindow(this);
