/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.builder;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.skcraft.launcher.model.modpack.FileInstall;
import com.skcraft.launcher.model.modpack.Manifest;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import java.util.List;

/**
 * Walks a path and adds hashed path versions to the given
 * {@link com.skcraft.launcher.model.modpack.Manifest}.
 */
@Log
public class ClientFileCollector extends DirectoryWalker {

    public static final String URL_FILE_SUFFIX = ".url.txt";
    public static final String URL_FILE_SUFFIX2 = ".url";
    public static final String BACKUP_FILE_SUFFIX = ".bak";

    private final Manifest manifest;
    private final PropertiesApplicator applicator;
    private final File destDir;
    private HashFunction hf = Hashing.sha1();

    /**
     * Create a new collector.
     *
     * @param manifest the manifest
     * @param applicator applies properties to manifest entries
     * @param destDir the destination directory to copy the hashed objects
     */
    public ClientFileCollector(@NonNull Manifest manifest, @NonNull PropertiesApplicator applicator,
                               @NonNull File destDir) {
        this.manifest = manifest;
        this.applicator = applicator;
        this.destDir = destDir;
    }

    @Override
    protected DirectoryBehavior getBehavior(@NonNull String name) {
        return getDirectoryBehavior(name);
    }

    @Override
    protected void onFile(File file, String relPath) throws IOException {
        if (file.getName().endsWith(FileInfoScanner.FILE_SUFFIX) || file.getName().endsWith(URL_FILE_SUFFIX) || file.getName().endsWith(URL_FILE_SUFFIX2) || file.getName().endsWith(BACKUP_FILE_SUFFIX)) {
            return;
        }

        FileInstall entry = new FileInstall();
        String hash = Files.hash(file, hf).toString();
        String to = FilenameUtils.separatorsToUnix(FilenameUtils.normalize(relPath));
        
        // url.txt override file
        File urlFile = new File(file.getAbsoluteFile().getParentFile(), file.getName() + URL_FILE_SUFFIX);
        File urlFile2 = new File(file.getAbsoluteFile().getParentFile(), file.getName() + URL_FILE_SUFFIX2);
        String location;
        boolean copy = true;
        if (urlFile.exists() && !System.getProperty("com.skcraft.builder.ignoreURLOverrides", "false").equalsIgnoreCase("true")) {
            location = Files.readFirstLine(urlFile, Charset.defaultCharset());
            copy = false;
        } else if (urlFile2.exists() && !System.getProperty("com.skcraft.builder.ignoreURLOverrides", "false").equalsIgnoreCase("true")) {
        	  List<String> lines = Files.readLines(urlFile2, Charset.defaultCharset());
            location = lines.get(1).replace("URL=", "");
            copy = false;
        } else {
            location = hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash;
        }
        
        File destPath = new File(destDir, location);
        entry.setHash(hash);
        entry.setLocation(location);
        entry.setTo(to);
        entry.setSize(file.length());
        applicator.apply(entry);
        destPath.getParentFile().mkdirs();
        ClientFileCollector.log.info(String.format("Adding %s from %s...", relPath, file.getAbsolutePath()));
        if (copy) {
            Files.copy(file, destPath);
        }
        manifest.getTasks().add(entry);
    }

    public static DirectoryBehavior getDirectoryBehavior(@NonNull String name) {
        if (name.startsWith(".")) {
            return DirectoryBehavior.SKIP;
        } else if (name.equals("_OPTIONAL")) {
            return DirectoryBehavior.IGNORE;
        } else if (name.equals("_SERVER")) {
            return DirectoryBehavior.SKIP;
        } else if (name.equals("_CLIENT")) {
            return DirectoryBehavior.IGNORE;
        } else {
            return DirectoryBehavior.CONTINUE;
        }
    }

}
