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
package inetsoft.graph.treeviz.tree.circlemap;

import inetsoft.graph.treeviz.ProgressObserver;
import inetsoft.graph.treeviz.ProgressTracker;
import inetsoft.graph.treeviz.tree.TreePath2;
import inetsoft.graph.treeviz.tree.TreeView;
import inetsoft.graph.treeviz.util.SequentialDispatcher;
import inetsoft.graph.treeviz.util.Worker;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * CirclemapView provides an interactive user interface for a
 * {@link CirclemapTree}. <p> Supports zooming into a subtree.
 *
 * @author Werner Randelshofer
 *
 * @version 1.4 2012-08-27 Adds printComponent method. <br>1.3 2011-01-16 Adds
 * popup menus. <br>1.2 2008-10-22 Turn ToolTips off by default. <br>1.1
 * 2008-07-05 Draw asynchronously. <br>1.0 2008-01-16 CirclemapView Created.
 */
public class CirclemapView extends javax.swing.JPanel implements TreeView {

   private CirclemapDraw draw;
   private BufferedImage img;
   private boolean isInvalid;
   private ProgressObserver workerProgress;
   private boolean drawHandles;
   private boolean isAdjusting;
   private boolean needsSimplify;
   private boolean needsProgressive = true;
   private CirclemapNode hoverNode;
   private SequentialDispatcher dispatcher = new SequentialDispatcher();
   private boolean isToolTipVisible = false;
   private CirclemapTree model;

   /**
    * Creates new form.
    */
   public CirclemapView() {
   }

   public CirclemapView(CirclemapTree model) {
      this.model = model;
      this.draw = new CirclemapDraw(model.getRoot(), model.getInfo());
      model.getInfo().addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            isInvalid = true;
            repaint();
         }
      });
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
      CirclemapNode node = draw.getNodeAt(x, y);
      return (node == null) ? null : draw.getInfo().getTooltip(node.getDataNodePath());
   }

   private void setCenter(double cx, double cy) {
      draw.setCX(cx);
      draw.setCY(cy);
   }

   private Point2D.Double getCenter() {
      return new Point2D.Double(draw.getCX(), draw.getCY());
   }

   private void setOuterRadius(double r) {
      draw.setRadius(r);
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
         if(img == null) {
            setCenter((double) w / 2, (double) h / 2);
            setOuterRadius(Math.min(w, h) / 2 - 4);
         }
         else {
            setCenter(draw.getCX() / img.getWidth() * w,
                      draw.getCY() / img.getHeight() * h);
            setOuterRadius(Math.min(w, h) / 2 - 4);
         }
         img = null;
         img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = img.createGraphics();
         g.setBackground(Color.WHITE);
         g.clearRect(0, 0, img.getWidth(), img.getHeight());
         g.dispose();
         isInvalid = true;
      }
      if(isInvalid) {
         if(workerProgress != null) {
            workerProgress.cancel();
         }
         else {
            isInvalid = false;
            final BufferedImage workingImage = img;
            workerProgress = new ProgressTracker("Circular Treemap", "Drawing...");
            workerProgress.setIndeterminate(true);
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
                  g.clearRect(0, 0, workingImage.getWidth(), workingImage.getHeight());
                  g.setClip(new Rectangle(0, 0, workingImage.getWidth(), workingImage.getHeight()));
                  if(isAdjusting && needsSimplify) {
                     //  draw.drawContours(g, draw.getRoot(), Color.gray);
                     long start = System.currentTimeMillis();
                     draw.drawTree(g, workerProgress);
                     long end = System.currentTimeMillis();
                     needsSimplify |= (end - start) > 99;
                     needsProgressive |= (end - start) > 33;
                  }
                  else {
                     long start = System.currentTimeMillis();
                     draw.drawTree(g, workerProgress);
                     long end = System.currentTimeMillis();
                     needsSimplify = (end - start) > 99;
                     needsProgressive = (end - start) > 33;
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
            if(/*!isAdjusting && !needsSimplify && */needsProgressive) {
               dispatcher.dispatch(worker);
            }
            else {
               worker.run();
            }
         }
      }


      gr.drawImage(img, 0, 0, this);
      CirclemapNode selectedNode = draw.getDrawRoot();
      if(selectedNode != null) {
         Graphics2D g = (Graphics2D) gr;
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         if(selectedNode.children().isEmpty()) {
            draw.drawSubtreeBounds(g, selectedNode, Color.blue);
         }
         else {
            draw.drawDescendantSubtreeBounds(g, selectedNode, Color.blue);
         }
      }
      if(hoverNode != null) {
         Graphics2D g = (Graphics2D) gr;
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         draw.drawNodeBounds(g, hoverNode, Color.red);
      }

      if(drawHandles) {
         Graphics2D g = (Graphics2D) gr;
         double cx = draw.getCX();
         double cy = draw.getCY();
         g.setColor(Color.BLACK);
         AffineTransform t = new AffineTransform();
         t.translate(cx, cy);
         //  t.rotate(draw.getRotation() * Math.PI / -180d);
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

      setCenter((double) w / 2, (double) h / 2);
      setOuterRadius(Math.min(w, h) / 2 - 4);

      Graphics2D g = (Graphics2D) gr;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setFont(getFont());
      try {
         draw.drawTree(g, new ProgressTracker("Circular Treemap", "Printing..."));
      }
      catch(Throwable t) {
         //t.printStackTrace();
      }
      CirclemapNode selectedNode = draw.getDrawRoot();
      if(selectedNode != null) {
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
         if(selectedNode.children().isEmpty()) {
            draw.drawSubtreeBounds(g, selectedNode, Color.blue);
         }
         else {
            draw.drawDescendantSubtreeBounds(g, selectedNode, Color.blue);
         }
      }

      setCenter(savedCenter.x, savedCenter.y);
   }

   @Override
   public int getMaxDepth() {
      return draw.getMaxDepth();
   }

   @Override
   public void setMaxDepth(int newValue) {
      if(newValue != draw.getMaxDepth()) {
         draw.setMaxDepth(newValue);
         isInvalid = true;
         if(newValue == Integer.MAX_VALUE) {
            needsProgressive = true;
         }
         repaint();
      }
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      setLayout(new java.awt.BorderLayout());
   }// </editor-fold>//GEN-END:initComponents

   private class MouseHandler implements MouseListener, MouseMotionListener {

      @Override
      public void mouseClicked(MouseEvent evt) {
         if(evt.getButton() == MouseEvent.BUTTON1) {
            CirclemapNode node = draw.getNodeAt(evt.getX(), evt.getY());
            if(node == null) {
               node = draw.getRoot();
            }
            if(node != draw.getDrawRoot()) {
               draw.setDrawRoot(node);

               isInvalid = true;
               repaint();
            }
            else {
               // go back to root
               draw.setDrawRoot(draw.getRoot());
               isInvalid = true;
               repaint();
            }
         }
      }

      @Override
      public void mousePressed(MouseEvent e) {
         if(e.isPopupTrigger()) {
            showPopup(e);
         }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
         if(e.isPopupTrigger()) {
            showPopup(e);
         }
      }

      @Override
      public void mouseEntered(MouseEvent evt) {
         hoverNode = draw.getNodeAt(evt.getX(), evt.getY());
         repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
         hoverNode = null;
         repaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
      }

      @Override
      public void mouseMoved(MouseEvent evt) {
         hoverNode = draw.getNodeAt(evt.getX(), evt.getY());
         repaint();
      }

      private void showPopup(MouseEvent evt) {
         CirclemapNode popupNode = draw.getNodeAt(evt.getX(), evt.getY());
         if(popupNode != null) {
            TreePath2 treePath = popupNode.getDataNodePath();
            Action[] a = model.getInfo().getActions(treePath);
            if(a.length > 0) {
               JPopupMenu m = new JPopupMenu();
               for(int i = 0; i < a.length; i++) {
                  m.add(a[i]);
               }
               m.show(CirclemapView.this, evt.getX(), evt.getY());
            }
         }
         evt.consume();
      }
   }
   // Variables declaration - do not modify//GEN-BEGIN:variables
   // End of variables declaration//GEN-END:variables
}
