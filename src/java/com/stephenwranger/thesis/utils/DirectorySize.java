package com.stephenwranger.thesis.utils;
import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DirectorySize {
   private static final long BASE_FACTOR = 1024L;
   private static final String[] SUFFIXES = new String[] { " TiB", " GiB", " MiB", " KiB", " bytes" };
   private static final long[] FACTORS = new long[] { (long) Math.pow(BASE_FACTOR, 4), // 1 TiB
                                                      (long) Math.pow(BASE_FACTOR, 3), // 1 GiB
                                                      (long) Math.pow(BASE_FACTOR, 2), // 1 MiB
                                                      (long) Math.pow(BASE_FACTOR, 1), // 1 KiB
                                                      1                                // 1 byte
   };
   
   private static final Queue<Thread> THREADS = new ConcurrentLinkedQueue<>();

   public static void main(String[] args) {
      final File file = new File(args[0]);
      
      if(file.isDirectory()) {
         final AtomicInteger maxDepth = new AtomicInteger(0);
         final AtomicInteger dirCount = new AtomicInteger(0);
         final AtomicInteger fileCount = new AtomicInteger(0);
         final AtomicLong filesize = new AtomicLong(0L);
         getMaxDepthAndFileCountThreaded(file, 0, maxDepth, dirCount, fileCount, filesize);
         
         while(!THREADS.isEmpty()) {
            final Thread thread = THREADS.peek();
            try {
               thread.join();
               THREADS.remove();
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
         }
   
         System.out.println("depth:      " + maxDepth.get());
         System.out.println("dir count:  " + dirCount.get());
         System.out.println("file count: " + fileCount.get());
         System.out.println("filesize:   " + bytesString(filesize.get()));
      } else {
         System.err.println("USAGE: DirectorySize <directory>");
      }
   }

   public static void getMaxDepthAndFileCountThreaded(final File file, final int currentDepth, final AtomicInteger maxDepth, final AtomicInteger dirCount, final AtomicInteger fileCount, final AtomicLong filesize) {
      if(currentDepth <= 1) {
         final Thread thread = new Thread() {
            @Override
            public void run() {
               getMaxDepthAndFileCount(file, currentDepth, maxDepth, dirCount, fileCount, filesize);
            }
         };

         thread.start();
         THREADS.add(thread);
      } else {
         getMaxDepthAndFileCount(file, currentDepth, maxDepth, dirCount, fileCount, filesize);
      }
   }
   
   public static void getMaxDepthAndFileCount(final File file, final int currentDepth, final AtomicInteger maxDepth, final AtomicInteger dirCount, final AtomicInteger fileCount, final AtomicLong filesize) {
      if(file.isDirectory()) {
         dirCount.incrementAndGet();
         maxDepth.getAndUpdate((oldValue) -> {
            return Math.max(oldValue, currentDepth);
         });
         
         for(final File child : file.listFiles()) {
            getMaxDepthAndFileCountThreaded(child, currentDepth+1, maxDepth, dirCount, fileCount, filesize);
         }
      } else {
         fileCount.incrementAndGet();
         filesize.addAndGet(file.length());
      }
   }
   
   public static String bytesString(final long byteCount) {
      for(int i = 0; i < FACTORS.length - 1; i++) {
         if(byteCount >= FACTORS[i]) {
            final double value = byteCount / (double) FACTORS[i];
            final int large = (int) Math.floor(value);
            final int small = (int) Math.floor((value - large) * 1024);
            
            return large + SUFFIXES[i] + ", " + small + SUFFIXES[i+1];
         }
      }
      
      return byteCount + SUFFIXES[SUFFIXES.length - 1];
   }
}
