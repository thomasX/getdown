//
// $Id: Resource.java,v 1.10 2004/07/14 13:44:49 mdb Exp $

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;

import java.util.Comparator;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.samskivert.io.StreamUtil;
import com.samskivert.util.CollectionUtil;
import com.samskivert.util.SortableArrayList;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;
import com.threerings.getdown.util.ProgressObserver;

/**
 * Models a single file resource used by an {@link Application}.
 */
public class Resource
{
    /**
     * Creates a resource with the supplied remote URL and local path.
     */
    public Resource (String path, URL remote, File local)
    {
        _path = path;
        _remote = remote;
        _local = local;
        _marker = new File(_local.getPath() + "v");
    }

    /**
     * Returns the path associated with this resource.
     */
    public String getPath ()
    {
        return _path;
    }

    /**
     * Returns the local location of this resource.
     */
    public File getLocal ()
    {
        return _local;
    }

    /**
     * Returns the remote location of this resource.
     */
    public URL getRemote ()
    {
        return _remote;
    }

    /**
     * Computes the MD5 hash of this resource's underlying file.
     * <em>Note:</em> This is both CPU and I/O intensive.
     */
    public String computeDigest (MessageDigest md, ProgressObserver obs)
        throws IOException
    {
        md.reset();
        byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        int read;

        // if this is a jar file, we need to compute the digest in a
        // timestamp and file order agnostic manner to properly correlate
        // jardiff patched jars with their unpatched originals
        if (_local.getPath().endsWith(".jar")) {
            JarFile jar = new JarFile(_local);
            try {
                SortableArrayList entries = new SortableArrayList();
                CollectionUtil.addAll(entries, jar.entries());
                entries.sort(ENTRY_COMP);

                int eidx = 0;
                for (Iterator iter = entries.iterator(); iter.hasNext(); ) {
                    JarEntry entry = (JarEntry)iter.next();

                    // skip metadata; we just want the goods
                    if (entry.getName().startsWith("META-INF")) {
                        updateProgress(obs, eidx, entries.size());
                        continue;
                    }

                    // add this file's data to the MD5 hash
                    InputStream in = null;
                    try {
                        in = jar.getInputStream(entry);
                        while ((read = in.read(buffer)) != -1) {
                            md.update(buffer, 0, read);
                        }
                    } finally {
                        StreamUtil.close(in);
                    }
                    updateProgress(obs, eidx, entries.size());
                }

            } finally {
                try {
                    jar.close();
                } catch (IOException ioe) {
                    Log.warning("Error closing jar [path=" + _local +
                                ", error=" + ioe + "].");
                }
            }

        } else {
            long totalSize = _local.length(), position = 0L;
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(_local);
                while ((read = fin.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                    position += read;
                    updateProgress(obs, position, totalSize);
                }
            } finally {
                StreamUtil.close(fin);
            }
        }
        return StringUtil.hexlate(md.digest());
    }

    /**
     * Returns true if this resource has an associated "validated" marker
     * file.
     */
    public boolean isMarkedValid ()
    {
        if (!_local.exists()) {
            clearMarker();
            return false;
        }
        return _marker.exists();
    }

    /**
     * Creates a "validated" marker file for this resource to indicate
     * that its MD5 hash has been computed and compared with the value in
     * the digest file.
     *
     * @throws IOException if we fail to create the marker file.
     */
    public void markAsValid ()
        throws IOException
    {
        _marker.createNewFile();
    }

    /**
     * Removes any "validated" marker file associated with this resource.
     */
    public void clearMarker ()
    {
        if (_marker.exists()) {
            if (!_marker.delete()) {
                Log.warning("Failed to erase marker file '" + _marker + "'.");
            }
        }
    }

    /**
     * Wipes this resource file along with any "validated" marker file
     * that may be associated with it.
     */
    public void erase ()
    {
        clearMarker();
        if (_local.exists()) {
            if (!_local.delete()) {
                Log.warning("Failed to erase resource '" + _local + "'.");
            }
        }
    }

    /**
     * If our path is equal, we are equal.
     */
    public boolean equals (Object other)
    {
        if (other instanceof Resource) {
            return _path.equals(((Resource)other)._path);
        } else {
            return false;
        }
    }

    /**
     * We hash on our path.
     */
    public int hashCode ()
    {
        return _path.hashCode();
    }

    /**
     * Returns a string representation of this instance.
     */
    public String toString ()
    {
        return _path;
    }

    /** Helper function to simplify the process of reporting progress. */
    protected void updateProgress (ProgressObserver obs, long pos, long total)
    {
        if (obs != null) {
            obs.progress((int)(100 * pos / total));
        }
    }

    protected String _path;
    protected URL _remote;
    protected File _local, _marker;

    /** Used to sort the entries in a jar file. */
    protected static final Comparator ENTRY_COMP = new Comparator() {
        public int compare (Object o1, Object o2) {
            JarEntry e1 = (JarEntry)o1;
            JarEntry e2 = (JarEntry)o2;
            return e1.getName().compareTo(e2.getName());
        }
    };

    protected static final int DIGEST_BUFFER_SIZE = 5 * 1025;
}