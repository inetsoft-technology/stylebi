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
package inetsoft.graph.treeviz.tree.sunburst;

import inetsoft.graph.treeviz.ProgressObserver;
import inetsoft.graph.treeviz.ProgressTracker;
import inetsoft.graph.treeviz.tree.TreePath2;
import inetsoft.graph.treeviz.tree.TreeView;
import inetsoft.graph.treeviz.util.SequentialDispatcher;
import inetsoft.graph.treeviz.util.Worker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * SunburstView provides an interactive user interface for a
 * {@link SunburstTree}. <p> Supports rotation of the tree, and zooming into a
 * subtree.
 *
 * @author Werner Randelshofer
 * @version 1.0 September 18, 2007 Created.
 */
public class SunburstView extends JPanel implements SunburstViewer, TreeView {

   private SunburstDraw draw;
   private SunburstDraw subDraw;
   private BufferedImage img;
   private boolean isInvalid;
   private ProgressObserver workerProgress;
   private boolean drawHandles;
   private boolean isAdjusting;
   private boolean needsSimplify;
   private boolean needsProgressive = true;
   private boolean isToolTipVisible = false;
   /**
    * The selected node of the sunburst tree. Can be null.
    */
   private SunburstNode selectedNode;
   /**
    * The node under the mouse cursor. Can be null.
    */
   private SunburstNode hoverNode;
   private SunburstTree model;
   private SequentialDispatcher dispatcher = new SequentialDispatcher();

   /**
    * Creates new instance.
    */
   public SunburstView() {
      init();
   }

   public SunburstView(SunburstTree model) {
      this.draw = new SunburstDraw(model);
      this.model = model;
      init();
   }

   private void init() {
      initComponents();
      MouseHandler handler = new MouseHandler();
      addMouseListener(handler);
      addMouseMotionListener(handler);
      ToolTipManager.sharedInstance().registerComponent(this);
      setFont(new Font("Dialog", Font.PLAIN, 9));
   }

   @Override
   public int getMaxDepth() {
      return Integer.MAX_VALUE;
      // throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public void setMaxDepth(int newValue) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   private void setRotation(double rotation) {
      draw.setRotation(rotation);
      if(subDraw != null) {
         subDraw.setRotation(rotation);
      }
   }

   private void setCenter(double cx, double cy) {
      draw.setCX(cx);
      draw.setCY(cy);
      if(subDraw != null) {
         subDraw.setCX(cx);
         subDraw.setCY(cy);
      }
   }

   private Point2D.Double getCenter() {
      return new Point2D.Double(draw.getCX(), draw.getCY());
   }

   private double getOuterRadius() {
      if(subDraw != null) {
         return subDraw.getOuterRadius();
      }
      else {
         return draw.getOuterRadius();
      }
   }

   private void setOuterRadius(double r) {
      if(subDraw != null) {
         draw.setOuterRadius(r / 2 - (r / 2) / draw.getTotalDepth());
         subDraw.setOuterRadius(r);
         if(subDraw.getRoot().children().isEmpty()) {
            subDraw.setInnerRadius(r / 2);
         }
         else {
            subDraw.setInnerRadius(r / 2 - (r / 2) / subDraw.getTotalDepth());
         }
      }
      else {
         draw.setOuterRadius(r);
      }
   }

   @Override
   public void repaintView() {
      isInvalid = true;
      repaint();
   }

   @Override
   public void paintComponent(Graphics gr) {
      int w = getWidth();
      int h = getHeight();

      if(img == null
         || img.getWidth() != w
         || img.getHeight() != h) {
         setCenter((double) w / 2, (double) h / 2);
         setOuterRadius(Math.min(w, h) / 2 - 4);
         img = null;
         img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
         isInvalid = true;
      }
      if(isInvalid && (workerProgress == null || workerProgress.isClosed())) {
         isInvalid = false;
         final BufferedImage workingImage = img;
         workerProgress = new ProgressTracker("Sunburst Tree", "Drawing...");
         workerProgress.setIndeterminate(true);
         final ProgressObserver p = workerProgress;
         final Timer timer = new Timer(33, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               repaint();
            }
         });
         timer.isRepeats();
         timer.start();
         final Worker worker = new Worker() {
            @Override
            public Object construct() {
               Graphics2D g = workingImage.createGraphics();
               g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
               g.setBackground(Color.WHITE);
               g.setFont(getFont());
               g.clearRect(0, 0, img.getWidth(), img.getHeight());
               if(isAdjusting && needsSimplify) {
                  draw.drawContours(g, draw.getRoot(), Color.gray);
                  if(subDraw != null) {
                     subDraw.drawContours(g, subDraw.getRoot(), Color.gray);
                  }
               }
               else {
                  long start = System.currentTimeMillis();
                  draw.drawTree(g, p);
                  if(subDraw != null) {
                     if(subDraw.getRoot().children().isEmpty()) {
                        subDraw.drawTree(g, p);
                     }
                     else {
                        subDraw.drawDescendants(g, subDraw.getRoot(), p);
                     }
                  }
                  long end = System.currentTimeMillis();
                  needsSimplify = (end - start) > 99;
                  needsProgressive = (end - start) > 33;
                  isInvalid = false;
               }

               g.dispose();
               return null;
            }

            @Override
            public void done(Object value) {
               workerProgress.close();
               workerProgress = null;
               repaint();
               timer.stop();
            }
         };
         if(!isAdjusting && !needsSimplify && needsProgressive) {
            dispatcher.dispatch(worker);
         }
         else {
            worker.run();
         }
      }


      gr.drawImage(img, 0, 0, this);

      if(selectedNode != null) {
         Graphics2D g = (Graphics2D) gr;
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         if(selectedNode.children().isEmpty()) {
            draw.drawSubtreeBounds(g, selectedNode, Color.red);
         }
         else {
            draw.drawDescendantSubtreeBounds(g, selectedNode, Color.red);
         }
      }
      if(hoverNode != null) {
         Graphics2D g = (Graphics2D) gr;
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         draw.drawNodeBounds(g, hoverNode, Color.black);
         if(subDraw != null && subDraw.getRoot().isDescendant(hoverNode)) {
            if(hoverNode != subDraw.getRoot() || subDraw.getRoot().children().isEmpty()) {
               subDraw.drawNodeBounds(g, hoverNode, Color.BLACK);
            }
         }
      }

      if(drawHandles) {
         Graphics2D g = (Graphics2D) gr;
         double cx = draw.getCX();
         double cy = draw.getCY();
         g.setColor(Color.BLACK);
         AffineTransform t = new AffineTransform();
         t.translate(cx, cy);
         t.rotate(draw.getRotation() * Math.PI / -180d);
         AffineTransform oldT = (AffineTransform) g.getTransform().clone();
         g.setTransform(t);
         g.draw(new Line2D.Double(-5, 0, 5, 0));
         g.draw(new Line2D.Double(0, -5, 0, 5));
         g.setTransform(oldT);
      }

   }

   @Override
   public void printComponent(Graphics gr) {
      int w = getWidth();
      int h = getHeight();

      Point2D.Double savedCenter = getCenter();
      double savedRadius = getOuterRadius();
      setCenter(w / 2, h / 2);
      setOuterRadius(Math.min(w, h) / 2 - 4);


      Graphics2D g = (Graphics2D) gr;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      workerProgress = new ProgressTracker("Sunburst Tree", "Drawing...");
      workerProgress.setIndeterminate(true);
      final ProgressObserver p = workerProgress;

      draw.drawTree(g, p);
      if(subDraw != null) {
         if(subDraw.getRoot().children().isEmpty()) {
            subDraw.drawTree(g, p);
         }
         else {
            subDraw.drawDescendants(g, subDraw.getRoot(), p);
         }
      }

      if(selectedNode != null) {
         if(selectedNode.children().isEmpty()) {
            draw.drawSubtreeBounds(g, selectedNode, Color.red);
         }
         else {
            draw.drawDescendantSubtreeBounds(g, selectedNode, Color.red);
         }
      }

      setCenter(savedCenter.x, savedCenter.y);
      setOuterRadius(savedRadius);
      workerProgress = null;
   }

   @Override
   public boolean isToolTipEnabled() {
      return isToolTipVisible;
   }

   @Override
   public void setToolTipEnabled(boolean newValue) {
      isToolTipVisible = newValue;
   }

   /**
    * Returns the tooltip to be displayed.
    *
    * @param event the event triggering the tooltip
    *
    * @return the String to be displayed
    */
   @Override
   public String getToolTipText(MouseEvent event) {
      if(isToolTipVisible) {
         return getInfoText(event);
      }
      else {
         return null;
      }
   }

   /**
    * Returns the tooltip to be displayed.
    *
    * @param event the event triggering the tooltip
    *
    * @return the String to be displayed
    */
   @Override
   public String getInfoText(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();

      SunburstNode node = draw.getNodeAt(x, y);
      if(node == null && subDraw != null) {
         node = subDraw.getNodeAt(x, y);
         if(node == subDraw.getRoot() && !subDraw.getRoot().children().isEmpty()) {
            node = null;
         }
      }
      return (node == null) ? null : model.getInfo().getTooltip(node.getDataNodePath());
   }

   public void setSelectedNode(SunburstNode newValue) {
      selectedNode = newValue;
      if(selectedNode == null) {
         if(subDraw != null) {
            draw.setOuterRadius(subDraw.getOuterRadius());
            subDraw = null;
         }
      }
      else {
         double outerRadius = (subDraw == null) ? draw.getOuterRadius() : subDraw.getOuterRadius();

         if(selectedNode.children().isEmpty()) {
            subDraw = new SunburstDraw(selectedNode, draw.getInfo());
         }
         else {
            subDraw = new SunburstDraw(selectedNode, draw.getInfo());
         }
         setRotation(draw.getRotation());
         setOuterRadius(outerRadius);
         setCenter(draw.getCX(), draw.getCY());
      }
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
   private void initComponents() {

      setLayout(new java.awt.BorderLayout());

   }// </editor-fold>//GEN-END:initComponents

   private class MouseHandler implements MouseListener, MouseMotionListener {

      private double alphaStart;
      private boolean isMove;
      private Point moveStart;

      @Override
      public void mouseClicked(MouseEvent evt) {
         if(evt.getButton() == MouseEvent.BUTTON1) {
            SunburstNode node = draw.getNodeAt(evt.getX(), evt.getY());
            if(node == null && subDraw != null) {
               node = subDraw.getNodeAt(evt.getX(), evt.getY());
               if(node == subDraw.getRoot() && !subDraw.getRoot().children().isEmpty()) {
                  node = null;
               }
            }
            if(node == draw.getRoot()) {
               setSelectedNode(null);
               if(evt.getClickCount() == 2) {
                  setCenter(getWidth() / 2, getHeight() / 2);
                  setSelectedNode(null);
               }
            }
            else {
               setSelectedNode(node);
            }
            isInvalid = true;
            repaint();
         }
      }

      @Override
      public void mousePressed(MouseEvent e) {
         if(e.isPopupTrigger()) {
            showPopup(e);
         }
         else {
            isMove = draw.getNodeAt(e.getX(), e.getY()) == draw.getRoot();
            moveStart = e.getPoint();
            alphaStart = draw.getTheta(e.getX(), e.getY());
         }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
         if(e.isPopupTrigger()) {
            showPopup(e);
         }
         else {
            if(drawHandles || isAdjusting) {
               drawHandles = false;
               if(isAdjusting) {
                  isAdjusting = false;
                  isInvalid = true;
               }
               repaint();
            }
         }
      }

      @Override
      public void mouseEntered(MouseEvent e) {
      }

      @Override
      public void mouseExited(MouseEvent e) {
         if(drawHandles) {
            drawHandles = false;
            repaint();
         }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
         isAdjusting = true;
         if(isMove) {
            Point moveNow = e.getPoint();
            int cx = (int) draw.getCX();
            int cy = (int) draw.getCY();
            setCenter(cx + moveNow.x - moveStart.x,
                      cy + moveNow.y - moveStart.y);
            moveStart = moveNow;
            isInvalid = true;
            repaint();
         }
         else {
            double alphaNow = draw.getTheta(e.getX(), e.getY());
            setRotation(draw.getRotation() - alphaNow + alphaStart);
            isInvalid = true;
            repaint();
         }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
         hoverNode = draw.getNodeAt(e.getX(), e.getY());
         if(hoverNode == null && subDraw != null) {
            hoverNode = subDraw.getNodeAt(e.getX(), e.getY());
            if(hoverNode == subDraw.getRoot()
               && !subDraw.getRoot().children().isEmpty()) {
               hoverNode = null;
            }
         }
         //isInvalid = true;
         repaint();
            /*
             boolean b = draw.getNodeAt(e.getX(), e.getY()) == draw.getRoot();
             if (b != drawHandles) {
             drawHandles = b;
             repaint();
             }*/
      }

      private void showPopup(MouseEvent evt) {
         SunburstNode popupNode = draw.getNodeAt(evt.getX(), evt.getY());
         if(popupNode != null) {
            TreePath2 treePath = popupNode.getDataNodePath();
            Action[] a = model.getInfo().getActions(treePath);
            if(a.length > 0) {
               JPopupMenu m = new JPopupMenu();
               for(int i = 0; i < a.length; i++) {
                  m.add(a[i]);
               }
               m.show(SunburstView.this, evt.getX(), evt.getY());
            }
         }
         evt.consume();
      }
   }
   // Variables declaration - do not modify//GEN-BEGIN:variables
   // End of variables declaration//GEN-END:variables
}
