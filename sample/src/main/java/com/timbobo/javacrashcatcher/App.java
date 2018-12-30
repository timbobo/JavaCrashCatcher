package com.timbobo.javacrashcatcher;

import android.app.Application;

import com.timbobo.lib.crashcatcher.CrashHandler;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(this);
    }
}
