package eu.siacs.conversations.utils;


import android.os.FileObserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Copyright (C) 2012 Bartek Przybylski
 * Copyright (C) 2015 ownCloud Inc.
 * Copyright (C) 2016 Daniel Gultsch
 */

public abstract class ConversationsFileObserver {

    private final String path;
    private final List<SingleFileObserver> mObservers = new ArrayList<>();

    public ConversationsFileObserver(String path) {
        this.path = path;
    }

    public synchronized void startWatching() {
        Stack<String> stack = new Stack<>();
        stack.push(path);

        while (!stack.empty()) {
            String parent = stack.pop();
            mObservers.add(new SingleFileObserver(parent, FileObserver.DELETE));
            final File path = new File(parent);
            final File[] files = path.listFiles();
            if (files == null) {
                continue;
            }
            for(File file : files) {
                if (file.isDirectory() && !file.getName().equals(".") && !file.getName().equals("..")) {
                    stack.push(file.getPath());
                }
            }
        }
        for(FileObserver observer : mObservers) {
            observer.startWatching();
        }
    }

    public synchronized void stopWatching() {
        for(FileObserver observer : mObservers) {
            observer.stopWatching();
        }
        mObservers.clear();
    }

    abstract public void onEvent(int event, String path);

    private class SingleFileObserver extends FileObserver {
        private final String path;

        public SingleFileObserver(String path, int mask) {
            super(path, mask);
            this.path = path;
        }

        @Override
        public void onEvent(int event, String filename) {
            ConversationsFileObserver.this.onEvent(event, path+'/'+filename);
        }

    }
}
