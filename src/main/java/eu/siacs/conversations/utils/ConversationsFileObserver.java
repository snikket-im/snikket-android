package eu.siacs.conversations.utils;


import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;

/**
 * Copyright (C) 2012 Bartek Przybylski
 * Copyright (C) 2015 ownCloud Inc.
 * Copyright (C) 2016 Daniel Gultsch
 */

public abstract class ConversationsFileObserver {

    private final String path;
    private final List<SingleFileObserver> mObservers = new ArrayList<>();
    private final AtomicBoolean shouldStop = new AtomicBoolean(true);

    protected ConversationsFileObserver(String path) {
        this.path = path;
    }

    public void startWatching() {
        shouldStop.set(false);
        startWatchingInternal();
    }

    private synchronized void startWatchingInternal() {
        Stack<String> stack = new Stack<>();
        stack.push(path);

        while (!stack.empty()) {
            if (shouldStop.get()) {
                Log.d(Config.LOGTAG,"file observer received command to stop");
                return;
            }
            String parent = stack.pop();
            mObservers.add(new SingleFileObserver(parent, FileObserver.DELETE| FileObserver.MOVED_FROM));
            final File path = new File(parent);
            final File[] files = path.listFiles();
            if (files == null) {
                continue;
            }
            for(File file : files) {
                if (shouldStop.get()) {
                    Log.d(Config.LOGTAG,"file observer received command to stop");
                    return;
                }
                if (file.isDirectory() && file.getName().charAt(0) != '.') {
                    final String currentPath = file.getAbsolutePath();
                    if (depth(file) <= 8 && !stack.contains(currentPath) && !observing(currentPath)) {
                        stack.push(currentPath);
                    }
                }
            }
        }
        for(FileObserver observer : mObservers) {
            observer.startWatching();
        }
    }

    private static int depth(File file) {
        int depth = 0;
        while((file = file.getParentFile()) != null) {
            depth++;
        }
        return depth;
    }

    private boolean observing(String path) {
        for(SingleFileObserver observer : mObservers) {
            if(path.equals(observer.path)) {
                return true;
            }
        }
        return false;
    }

    public void stopWatching() {
        shouldStop.set(true);
        stopWatchingInternal();
    }

    private synchronized void stopWatchingInternal() {
        for(FileObserver observer : mObservers) {
            observer.stopWatching();
        }
        mObservers.clear();
    }

    abstract public void onEvent(int event, String path);

    public void restartWatching() {
        stopWatching();
        startWatching();
    }

    private class SingleFileObserver extends FileObserver {
        private final String path;

        SingleFileObserver(String path, int mask) {
            super(path, mask);
            this.path = path;
        }

        @Override
        public void onEvent(int event, String filename) {
            if (filename == null) {
                Log.d(Config.LOGTAG,"ignored file event with NULL filename (event="+event+")");
                return;
            }
            ConversationsFileObserver.this.onEvent(event, path+'/'+filename);
        }

    }
}
