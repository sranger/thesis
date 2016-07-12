package com.stephenwranger.thesis.geospatial;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.stephenwranger.graphics.utils.TimeUtils;

public class DemDownloader extends Thread {
   public static final String    DEM_URL       = "http://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm";
   private static final String   USAGE         = "USAGE: java -jar com.stephenwranger.thesis.jar DemDownloader <outputDirectory> <download archives (TRUE|FALSE)> <rebuild catalog (TRUE|FALSE)>";
   private static final String   OPTIONS_TITLE = "File exists options";
   private static final String   OVERWRITE     = "Overwrite";
   private static final String   SKIP          = "Skip";
   private static final String[] OPTIONS       = new String[] { DemDownloader.OVERWRITE, DemDownloader.SKIP };
   private static final String 	 ZIP_EXTENSION = ".ZIP";

   private final File            outputFolder;

   public DemDownloader(final File outputFolder) throws IOException {
      if (!outputFolder.exists()) {
         outputFolder.mkdirs();
      }

      if (!outputFolder.isDirectory()) {
         throw new IOException("Output folder must not exist or be a directory.");
      }

      this.outputFolder = outputFolder;
   }

   @Override
   public void run() {
      final String title = "DEM Downloader";
      final JFrame frame = new JFrame(title);
      final JLabel messageLabel = new JLabel("DEM Downloader initializing...");
      final JProgressBar progressBar = new JProgressBar(0, 1);
      progressBar.setPreferredSize(new Dimension(400, 100));

      frame.getContentPane().setLayout(new BorderLayout());
      frame.getContentPane().add(messageLabel, BorderLayout.SOUTH);
      frame.getContentPane().add(progressBar, BorderLayout.CENTER);

      try {
         SwingUtilities.invokeAndWait(() -> {
            frame.setLocation(300, 300);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setVisible(true);
         });
      } catch (final InvocationTargetException | InterruptedException e1) {
         e1.printStackTrace();
      }

      final Set<URL> urls = new HashSet<>();
      final Pattern pattern = Pattern.compile("href=\"(.*?)\\.zip\"");
      try {
         final URL demUrl = new URL(DemDownloader.DEM_URL);
         try (final BufferedReader reader = new BufferedReader(new InputStreamReader(demUrl.openStream()))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
               final Matcher matcher = pattern.matcher(line);
               if (matcher.find()) {
                  final String group = matcher.group(1);
                  urls.add(new URL(group + ".zip"));
               }
            }
         }

         int index = 1;

         for (final URL url : urls) {
            frame.setTitle(title + ": " + index + " of " + urls.size());
            index++;

            final File outputFile = new File(this.outputFolder, url.getFile());
            final long existingLength = outputFile.exists() ? outputFile.length() : -1;
            final ByteBuffer buffer = DemDownloader.readURL(url, messageLabel, progressBar, existingLength);

            if (buffer != null) {
               DemDownloader.writeFile(outputFile, buffer, messageLabel, progressBar);
            }
         }
      } catch (final IOException e) {
         e.printStackTrace();
      }
   }

   public static void main(final String[] args) throws InterruptedException, IOException {
      if (args.length != 3) {
         throw new RuntimeException(DemDownloader.USAGE);
      }

      final File file = new File(args[0]);

      if (Boolean.valueOf(args[1])) {
         final DemDownloader downloader = new DemDownloader(file);
         final long start = System.nanoTime();
         downloader.start();
         downloader.join();
         final long end = System.nanoTime();
         System.out.println("Complete! duration = " + TimeUtils.formatNanoseconds(end - start));
      }

      if (Boolean.valueOf(args[2])) {
         final long start = System.nanoTime();
         System.out.println("Building catalog...");
         DigitalElevationUtils.buildCatalog();
         final long end = System.nanoTime();
         System.out.println("Catalog complete! duration = " + TimeUtils.formatNanoseconds(end - start));
      }
   }

   private static ByteBuffer readURL(final URL url, final JLabel messageLabel, final JProgressBar progressBar, final long existingLength) throws IOException {
      ByteBuffer buffer = null;
      int error = 0;
      
      while(error < 5) {
         try {
            messageLabel.setText("Reading: " + url);
            final URLConnection connection = url.openConnection();
            final int length = Integer.parseInt(connection.getHeaderField("content-length"));
      
            if (existingLength != length) {
               progressBar.setValue(0);
               progressBar.setMaximum(length);
               buffer = ByteBuffer.allocate(length);
               final byte[] temp = new byte[2000];
      
               try (final InputStream is = connection.getInputStream()) {
                  int bytesRead = -1;
      
                  while ((bytesRead = is.read(temp)) != -1) {
                     buffer.put(temp, 0, bytesRead);
                     progressBar.setValue(buffer.position());
                  }
               }
      
               if (buffer.remaining() != 0) {
                  System.err.println("Buffer not filled: received = " + buffer.position() + ", expected = " + length);
                  error++;
               } else {
                  buffer.flip();
                  break;
               }
            } else {
               buffer = null;
               break;
            }
         } catch(final Exception e) {
            error++;
         }
      }
      
      if(error == 5) {
         System.out.println("Error downloading " + url + "; exiting");
         System.exit(1);
      }
      
      return buffer;
   }

   private static void writeFile(final File outputFile, final ByteBuffer buffer, final JLabel messageLabel, final JProgressBar progressBar) throws FileNotFoundException, IOException {
      messageLabel.setText("Writing: " + outputFile.getAbsolutePath());
      progressBar.setValue(0);
      progressBar.setMaximum(buffer.capacity());

      if (outputFile.exists()) {
         if (outputFile.length() == buffer.capacity()) {
            return;
         } else {
            final String message = "File exists (" + outputFile.getAbsolutePath() + ")\n existing size = " + outputFile.length() + "\npending size = " + buffer.capacity();
            final String choice = (String) JOptionPane.showInputDialog(null, message, DemDownloader.OPTIONS_TITLE, JOptionPane.QUESTION_MESSAGE, null, DemDownloader.OPTIONS, DemDownloader.OVERWRITE);

            if ((choice == null) || choice.equals(DemDownloader.SKIP)) {
               return;
            } else {
               outputFile.delete();
            }
         }
      } else {
         outputFile.getParentFile().mkdirs();
      }

      try (final OutputStream os = new FileOutputStream(outputFile, false)) {
         final byte[] temp = new byte[2000];
         int bytesRead = -1;

         while ((bytesRead = Math.min(2000, buffer.remaining())) > 0) {
            buffer.get(temp, 0, bytesRead);
            os.write(temp, 0, bytesRead);
            progressBar.setValue(buffer.position());
         }
      }
      
      if(outputFile.getAbsolutePath().toUpperCase().endsWith(ZIP_EXTENSION)) {
    	  try (final ZipInputStream zis = new ZipInputStream(new FileInputStream(outputFile))) {
    		  final byte[] temp = new byte[2000];
    		  ZipEntry entry = null;
    		  
    		  while((entry = zis.getNextEntry()) != null) {
    			  final String name = entry.getName();
    			  final File path = new File(outputFile.getParentFile() + File.separator + name);
    			  
    			  if(entry.isDirectory()) {
    				  path.mkdirs();
    			  } else {
    			     if(!path.getParentFile().exists()) {
    			        path.getParentFile().mkdirs();
    			     }
    			     
					  try(final FileOutputStream fout = new FileOutputStream(path)) {
						  int length = -1;
						  while((length = zis.read(temp)) != -1) {
							  fout.write(temp, 0, length);
						  }
					  }
    			  }
    		  }
    	  }
      }
   }
}
