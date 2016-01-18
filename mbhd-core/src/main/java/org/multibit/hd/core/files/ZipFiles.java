package org.multibit.hd.core.files;

import org.multibit.hd.core.managers.BackupManager;
import org.multibit.hd.core.managers.InstallationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.multibit.commons.files.SecureFiles.verifyOrCreateDirectory;

/**
 * <p>Utilties to provide the following to applications:</p>
 * <ul>
 * <li>Access to common ZIP operations</li>
 * </ul>
 * <p>Uses Java new I/O and Guava Files where possible</p>
 *
 * @since 0.0.1
 *
 */
public class ZipFiles {

  private static final Logger log = LoggerFactory.getLogger(ZipFiles.class);

  /**
   * Utilities have private constructor
   */
  private ZipFiles() {
  }

  /**
   * Copy the files in the specified srcFolder to the destZipFile
   * The zip-backups are not stored in the backup (as they are zip-backups themselves) but the rolling backups
   * are to increase backup coverage
   *
   * @param srcFolder         The directory holding the files to zip
   * @param destZipFile       The zip file to copy files into
   * @param includeBlockStore if true then include the blockstore, if false then don't
   *
   * @throws java.io.IOException
   */
  public static void zipFolder(String srcFolder, String destZipFile, boolean includeBlockStore) throws IOException {
    ZipOutputStream zip;
    FileOutputStream fileWriter;

    fileWriter = new FileOutputStream(destZipFile);
    zip = new ZipOutputStream(fileWriter);

    try {
      // Add the contents of the srcFolder to the zip - note the top folder (with the wallet id) is not added as it is coded in the name of the zip
      String[] srcFolderList = new File(srcFolder).list();

      if (srcFolderList != null) {
        for (String fileName : srcFolderList) {
          if (!includeBlockStore && fileName.endsWith(InstallationManager.MBHD_PREFIX + InstallationManager.SPV_BLOCKCHAIN_SUFFIX)) {
            // Do not include the block chain (to save space)
            continue;
          }
          addFileToZip(srcFolder, fileName, zip, includeBlockStore);
        }
      }
    } finally {
      zip.flush();
      zip.close();
    }
  }

  public static void unzip(String zipFileName, String directoryToExtractTo) throws IOException {

    Enumeration entriesEnum;
    ZipFile zipFile = null;
    try {
      zipFile = new ZipFile(zipFileName);

      entriesEnum = zipFile.entries();

      File directory = new File(directoryToExtractTo);
      verifyOrCreateDirectory(directory);

      while (entriesEnum.hasMoreElements()) {

        ZipEntry entry = (ZipEntry) entriesEnum.nextElement();

        if (entry.isDirectory()) {
          verifyOrCreateDirectory(new File(directoryToExtractTo + File.separator + entry.getName()));
        } else {

          // Ignore certain files
          if (entry.getName().contains(".DS_Store")) {
            continue;
          }

          log.debug("Extracting file: " + entry.getName());

          // This part is necessary because file entry can come before
          // directory entry where is file located
          // i.e.:
          //   /foo/foo.txt
          //   /foo/
          String dir = directoryPart(entry.getName());
          if (dir != null) {
            verifyOrCreateDirectory(new File(directoryToExtractTo + File.separator + dir));
          }
          String name = entry.getName();

          writeFile(zipFile.getInputStream(entry),
            new BufferedOutputStream(new FileOutputStream(
              directoryToExtractTo + File.separator + name))
          );
        }
      }
    } finally {
      if (zipFile != null) {
        zipFile.close();
      }
    }
  }

  private static void addFileToZip(String path, String srcFile, ZipOutputStream zip, boolean includeBlockStore)
    throws IOException {

    File srcFileOnDisk = new File(path + File.separator + srcFile);
    if (srcFileOnDisk.isDirectory()) {
      addFolderToZip(path, srcFile, zip, includeBlockStore);
    } else {
      byte[] buf = new byte[1024];
      int len;
      if (srcFileOnDisk.exists()) {
        try (FileInputStream in = new FileInputStream(srcFileOnDisk)) {
          zip.putNextEntry(new ZipEntry(srcFile));

          while ((len = in.read(buf)) > 0) {
            zip.write(buf, 0, len);
          }
        } catch (IOException ioe) {
          log.error("Failed to save file to cloud backup. Error was {}", ioe);
        }
      } else {
        log.debug("Not adding file {} to backup as it does not exist.", srcFileOnDisk);
      }
    }
  }

  private static void addFolderToZip(String path, String srcFolder, ZipOutputStream zip, boolean includeBlockStore)
    throws IOException {

    File folder = new File(srcFolder);
    File folderOnDisk = new File(path + File.separator + srcFolder);

    // Don't include the zip-backups folder in the backups
    if (folder.getAbsolutePath().contains(BackupManager.LOCAL_ZIP_BACKUP_DIRECTORY_NAME)) {
      return;
    }

    String[] folderOnDiskList = folderOnDisk.list();
    if (folderOnDiskList != null) {
      for (String fileName : folderOnDiskList) {
        if (!includeBlockStore && fileName.endsWith(InstallationManager.MBHD_PREFIX + InstallationManager.SPV_BLOCKCHAIN_SUFFIX)) {
          // Do not include the block writeContacts (to save space)
          continue;
        }
        addFileToZip(path, srcFolder + File.separator + fileName, zip, includeBlockStore);
      }
    }
  }

  /**
   * Work out the directory part of a filename
   *
   * @param name of file
   *
   * @return directory part of filename
   * TODO (GR) Replace with Guava or NIO equivalent
   */
  private static String directoryPart(String name) {
    int s = name.lastIndexOf(File.separatorChar);
    return s == -1 ? null : name.substring(0, s);
  }

  /**
   * Write a file from the inputstream to the outputstream
   * TODO (GR) Replace with Guava or NIO equivalent
   */
  private static void writeFile(InputStream in, OutputStream out)
    throws IOException {
    byte[] buffer = new byte[1024];
    int len;

    while ((len = in.read(buffer)) >= 0)
      out.write(buffer, 0, len);

    in.close();
    out.close();
  }

}
