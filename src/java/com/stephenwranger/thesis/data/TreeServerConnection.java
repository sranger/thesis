package com.stephenwranger.thesis.data;

import java.util.ArrayList;
import java.util.List;

import com.stephenwranger.thesis.data.TreeServerProcessor.ConnectionType;

public class TreeServerConnection {
   private final List<TreeServerProcessor> processors    = new ArrayList<>();
   private int                             nextProcessor = 0;

   public TreeServerConnection(final TreeStructure tree, final String basePath, final ConnectionType connectionType) {
      final int numProcessors = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);

      for (int i = 0; i < numProcessors; i++) {
         this.processors.add(new TreeServerProcessor(tree, basePath, connectionType));
      }

      for (final TreeServerProcessor processor : this.processors) {
         processor.start();
      }
   }

   public synchronized void close() {
      for (final TreeServerProcessor processor : this.processors) {
         processor.close();
      }

      for (final TreeServerProcessor processor : this.processors) {
         try {
            processor.join();
         } catch (final InterruptedException e) {
            e.printStackTrace();
         }
      }
   }

   public synchronized void request(final TreeCell treeCell) {
      if (treeCell.isEmpty()) {
         treeCell.setPending();

         final int index = this.nextProcessor;
         this.nextProcessor = (this.nextProcessor + 1) % this.processors.size();

         new Thread(() -> {
            this.processors.get(index).addRequestedCell(treeCell);
         }).start();
      }
   }
}
