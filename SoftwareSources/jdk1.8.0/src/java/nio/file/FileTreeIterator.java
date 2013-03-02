/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.nio.file;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.nio.file.attribute.BasicFileAttributes;

class FileTreeIterator implements Iterator<FileTreeIterator.Entry>, Closeable {

    /**
     * A pair of {@code Path} and its {@code BasicFileAttributes}.
     */
    static class Entry {
        private final Path file;
        private final BasicFileAttributes attrs;
        // Latched exception thrown when tried to read the BasicFileAttributes
        private RuntimeException latched_ex;

        Entry(Path file, BasicFileAttributes attrs) {
            this.file = Objects.requireNonNull(file);
            this.attrs = attrs;
            latched_ex = null;
        }

        Entry(Path file, RuntimeException ex) {
            this.file = Objects.requireNonNull(file);
            this.latched_ex = Objects.requireNonNull(ex);
            attrs = null;
        }

        static Entry make(Path file, boolean followLinks) {
            Objects.requireNonNull(file);
            BasicFileAttributes attrs;
            try {
                if (followLinks) {
                    try {
                        attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        return new Entry(file, attrs);
                    } catch (IOException notcare) {
                        // ignore, try not to follow link
                    }
                }
                attrs = Files.readAttributes(file, BasicFileAttributes.class,
                                             LinkOption.NOFOLLOW_LINKS);
                return new Entry(file, attrs);
            } catch (IOException ioe) {
                return new Entry(file, new UncheckedIOException(ioe));
            } catch (RuntimeException ex) {
                return new Entry(file, ex);
            }
        }

        public Entry ignoreException() {
            latched_ex = null;
            return this;
        }

        public Path getPath() {
            return file;
        }

        /**
         * Could return null if ignoreException
         */
        public BasicFileAttributes getFileAttributes() {
            if (latched_ex != null) {
                throw latched_ex;
            }
            return attrs;
        }

        public void checkException() throws IOException {
            if (latched_ex != null) {
                if (latched_ex instanceof UncheckedIOException) {
                    throw ((UncheckedIOException) latched_ex).getCause();
                } else {
                    throw latched_ex;
                }
            }
        }
    }

    private static class Context {
        final Path file;
        final BasicFileAttributes attrs;
        final DirectoryStream<Path> ds;
        final Iterator<Path> itor;

        Context(Path file, BasicFileAttributes attrs, DirectoryStream<Path> ds, Iterator<Path> itor) {
            this.file = file;
            this.attrs = attrs;
            this.ds = ds;
            this.itor = itor;
        }
    }

    private static class VisitorException extends RuntimeException {
        VisitorException(IOException ioe) {
            super(ioe);
        }

        @Override
        public IOException getCause() {
            return (IOException) super.getCause();
        }
    }

    private final boolean followLinks;
    private final int maxDepth;
    private final ArrayDeque<Context> stack = new ArrayDeque<>();

    private FileVisitor<Path> visitorProxy;
    private Entry next;

    private FileTreeIterator(int maxDepth,
                             FileVisitOption... options) {
        this.maxDepth = maxDepth;

        boolean follow = false;
        for (FileVisitOption opt : options) {
            switch(opt) {
                case FOLLOW_LINKS:
                    follow = true;
                    break;
                default:
                    // nothing should be here
                    break;
            }
        }
        this.followLinks = follow;
    }

    private FileTreeIterator init(Path start, FileVisitor<? super Path> visitor) throws IOException {
        next = Entry.make(start, followLinks);
        try {
            next.checkException();
        } catch (SecurityException se) {
            // First level, re-throw it.
            throw se;
        } catch (IOException ioe) {
            if (visitor != null) {
                visitor.visitFileFailed(start, ioe);
            } else {
                throw ioe;
            }
        }

        // Wrap IOException in VisitorException so we can throw from hasNext()
        // and distinguish them for re-throw later.
        // For non-proxy mode, exception come in should be re-thrown so the caller know
        // it is not processed and deal with it accordingly.
        visitorProxy = new FileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                if (visitor != null) {
                    try {
                        return visitor.preVisitDirectory(path, attrs);
                    } catch (IOException ex) {
                        throw new VisitorException(ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult postVisitDirectory(Path path, IOException exc) throws IOException {
                if (visitor != null) {
                    try {
                        return visitor.postVisitDirectory(path, exc);
                    } catch (IOException ex) {
                        throw new VisitorException(ex);
                    }
                } else if (exc != null) {
                    throw exc;
                }
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (visitor != null) {
                    try {
                        return visitor.visitFile(path, attrs);
                    } catch (IOException ex) {
                        throw new VisitorException(ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFileFailed(Path path, IOException exc) throws IOException {
                if (visitor != null) {
                    try {
                        return visitor.visitFileFailed(path, exc);
                    } catch (IOException ex) {
                        throw new VisitorException(ex);
                    }
                } else if (exc != null) {
                    throw exc;
                }
                return FileVisitResult.CONTINUE;
            }
        };

        // Setup first visit for directory
        visitNext();

        return this;
    }

    public static FileTreeIterator iterator(Path start, int maxDepth,
                                            FileVisitOption... options) throws IOException {
        return new FileTreeIterator(maxDepth, options).init(start, null);
    }

    public static void walkThrough(Path start, int maxDepth,
                                   FileVisitor visitor,
                                   FileVisitOption... options) throws IOException {
        Objects.requireNonNull(visitor);
        FileTreeIterator itor = new FileTreeIterator(maxDepth, options).init(start, visitor);
        try {
            while (itor.hasNext()) {
                itor.next();
            }
        } catch (VisitorException ex) {
            // Only VisitorException is processed here as others should be
            // handled by FileVisitor already.
            throw ex.getCause();
        }
    }

    private boolean detectLoop(Path dir, BasicFileAttributes attrs) {
        Object key = attrs.fileKey();
        for (Context ctx : stack) {
            Object ancestorKey = ctx.attrs.fileKey();
            if (key != null && ancestorKey != null) {
                if (key.equals(ancestorKey)) {
                    return true;
                }
            } else {
                boolean isSameFile = false;
                try {
                    isSameFile = Files.isSameFile(dir, ctx.file);
                } catch (IOException x) {
                    // ignore
                } catch (SecurityException x) {
                    // ignore
                }
                if (isSameFile) {
                    return true;
                }
            }
        }

        return false;
    }

    private void evalVisitorResult(FileVisitResult result) {
        Objects.requireNonNull(result);
        switch (result) {
            case TERMINATE:
                try {
                    close();
                } catch (IOException ioe) {
                    // ignore
                }
                break;
            case SKIP_SIBLINGS:
            case SKIP_SUBTREE:
                // stop iterate in the containing folder
                if (! stack.isEmpty()) {
                    exitDirectory(null);
                }
                break;
            case CONTINUE:
                break;
        }
    }

    private void enteringDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        // Detect loop when follow links
        if (followLinks && detectLoop(dir, attrs)) {
            // Loop detected
            throw new FileSystemLoopException(dir.toString());
            // ?? skip is better ??
            // return;
        }

        DirectoryStream<Path> ds = Files.newDirectoryStream(dir);
        stack.push(new Context(dir, attrs, ds, ds.iterator()));
    }

    private void exitDirectory(DirectoryIteratorException die) {
        Context ctx = stack.pop();
        IOException failure = (die == null) ? null : die.getCause();

        try {
            ctx.ds.close();
        } catch (IOException ioe) {
            if (failure != null) {
                failure = ioe;
            }
        }

        try {
            evalVisitorResult(visitorProxy.postVisitDirectory(ctx.file, failure));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
            // retain DirectoryIteratorException information ?
            // throw (die == null) ? new UncheckedIOException(ex) : die;
        }
    }

    private void visitNext() {
        Path p = next.file;
        try {
            BasicFileAttributes attrs;
            try {
                attrs = next.getFileAttributes();
            } catch (UncheckedIOException uioe) {
                throw uioe.getCause();
            }
            if (attrs.isDirectory() && stack.size() < maxDepth) {
                enteringDirectory(p, attrs);
                FileVisitResult result = visitorProxy.preVisitDirectory(p, attrs);
                // Simply undo enter, not calling postVisitDirectory
                if (FileVisitResult.CONTINUE != result) {
                    Context ctx = stack.pop();
                    try {
                        ctx.ds.close();
                    } catch (IOException ioe) {
                        // ignore
                    }
                }
                // deal result from containing folder
                evalVisitorResult(result);
            } else {
                evalVisitorResult(visitorProxy.visitFile(p, attrs));
            }
        } catch (IOException ioe) {
            try {
                evalVisitorResult(visitorProxy.visitFileFailed(p, ioe));
            } catch (IOException ioe2) {
                throw new UncheckedIOException(ioe2);
            }
        }
    }

    /**
     * When there is an exception occurred, we will try to resume the iteration
     * to next element. So the exception is thrown, and next call to hasNext()
     * will continue the iteration.
     */
    public boolean hasNext() {
        // next was read-ahead, not yet fetched.
        if (next != null) {
            return true;
        }

        // Check if iterator had been closed.
        if (stack.isEmpty()) {
            return false;
        }

        Iterator<Path> itor = stack.peek().itor;
        try {
            Path p = itor.next();
            next = Entry.make(p, followLinks);
            visitNext();
        } catch (SecurityException se) {
            // ignore and skip this file
            next = null;
            return hasNext();
        } catch (DirectoryIteratorException die) {
            // try to resume from level above
            exitDirectory(die);
        } catch (NoSuchElementException nsee) {
            // nothing left at this level
            exitDirectory(null);
        }
        return stack.isEmpty() ? false : hasNext();
    }

    public Entry next() {
        if (next != null || hasNext()) {
            try {
                return next;
            } finally {
                next = null;
            }
        } else {
            throw new NoSuchElementException();
        }
    }

    public void close() throws IOException {
        IOException ioe = null;

        for (Context ctx : stack) {
            try {
                ctx.ds.close();
            } catch (IOException ex) {
                // ignore so we try to close all DirectoryStream
                // keep the last exception to throw later
                ioe = ex;
            }
        }

        next = null;
        stack.clear();

        if (ioe != null) {
            // Throw at least one if there is any
            throw ioe;
        }
    }
}