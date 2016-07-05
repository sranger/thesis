package com.stephenwranger.thesis.data;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TreeServerProcessor extends Thread {
   public enum ConnectionType {
      FILESYSTEM, HTTP
   }
   
   private final BlockingQueue<TreeCell> requests = new LinkedBlockingQueue<>();
   private final String basePath;
   private final ConnectionType connectionType;
   private final int stride;
   
   private boolean isRunning = true;
   
   public TreeServerProcessor(final TreeStructure tree, final String basePath, final ConnectionType connectionType) {
      this.basePath = basePath;
      this.connectionType = connectionType;
      
      this.stride = tree.getAttributes().stride;
   }
   
   @Override
   public void run() {
      while(this.isRunning) {
         synchronized(this) {
            try {
               this.wait();
            } catch (final InterruptedException e) {
               e.printStackTrace();
            }
         }
         
         if(isRunning) {
            final List<TreeCell> toProcess = new LinkedList<>();
            this.requests.drainTo(toProcess);
            
            for(final TreeCell treeCell : toProcess) {
               this.fetchRequest(treeCell);
            }
         }
      }
   }
   
   public void close() {
      this.isRunning = false;
      this.notify();
   }
   
   public void addRequestedCell(final TreeCell treeCell) {
      synchronized(this) {
         this.requests.add(treeCell);
         this.notify();
      }
   }
   
   private void fetchRequest(final TreeCell treeCell) {
      final String filename = treeCell.path.isEmpty() ? "root" : Character.toString(treeCell.path.charAt(treeCell.path.length() - 1));
      final String dat = String.join("/", treeCell.path.split("")) + "/" + filename + ".dat";
      final String txt = String.join("/", treeCell.path.split("")) + "/" + filename + ".txt";
      byte[] buffer = null;
      String[] children = null;
      
      switch(this.connectionType) {
         case FILESYSTEM:
            final File datFile = new File(this.basePath, dat);
            final File txtFile = new File(this.basePath, txt);
            
            buffer = TreeServerProcessor.getData(treeCell, datFile, this.stride);
            children = TreeServerProcessor.getChildren(txtFile);
            break;
         case HTTP:
            try {
               final URL datUrl = new URL(this.basePath + "/" + dat);
               final URL txtUrl = new URL(this.basePath + "/" + txt);
               
               buffer = TreeServerProcessor.getData(treeCell, datUrl, this.stride);
               children = TreeServerProcessor.getChildren(txtUrl);
            } catch(final MalformedURLException e) {
               e.printStackTrace();
            }
            
            break;
      }

      treeCell.setData(buffer, children);
   }
   
   public static byte[] getData(final TreeCell treeCell, final File file, final int stride) {
      final byte[] buffer = new byte[(int)file.length()];
      
      try(final DataInputStream is = new DataInputStream(new FileInputStream(file))) {
         is.readFully(buffer, 0, buffer.length);
      } catch (final IOException e) {
         e.printStackTrace();
      }
      
      return buffer;
   }
   
   public static String[] getChildren(final File file) {
      String[] children = new String[0];
      
      try(final BufferedReader reader = new BufferedReader(new FileReader(file))) {
         final String line = reader.readLine();
         
         if(line != null && !line.isEmpty()) {
            children = line.split(",");
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }
      
      return children;
   }
   
   public static DataAttributes getAttributes(final File file) {
      final List<Attribute> attributes = new ArrayList<>();
      
      try(final BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line = null;
         
         while((line = reader.readLine()) != null) {
            // ignore the line with the column header names
            if(!line.isEmpty() && Character.isDigit(line.charAt(0))) {
               attributes.add(new Attribute(line));
            }
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }
      
      return new DataAttributes(attributes);
   }
   
   public static byte[] getData(final TreeCell treeCell, final URL url, final int stride) {
      try {
         final URLConnection connection = url.openConnection();
         final byte[] buffer = new byte[connection.getContentLength() * stride];
         
         try(final DataInputStream is = new DataInputStream(url.openStream())) {
            is.readFully(buffer, 0, buffer.length);
         }
         
         return buffer;
      } catch(final IOException e) {
         e.printStackTrace();
      }
      
      return new byte[0];
   }
   
   public static String[] getChildren(final URL url) {
      String[] children = new String[0];
      
      try(final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
         final String line = reader.readLine();

         if(line != null && !line.isEmpty()) {
            children = line.split(",");
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }
      
      return children;
   }
   
   public static DataAttributes getAttributes(final URL url) {
      final List<Attribute> attributes = new ArrayList<>();
      
      try(final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
         String line = null;
         
         while((line = reader.readLine()) != null) {
            // ignore the line with the column header names
            if(!line.isEmpty() && Character.isDigit(line.charAt(0))) {
               attributes.add(new Attribute(line));
            }
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }
      
      return new DataAttributes(attributes);
   }
}
