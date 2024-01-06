package com.rts;

import org.jetbrains.annotations.Contract;

public class MyApp extends android.app.Application {

    private static MyApp instance;

    public MyApp() {
        instance = this;
    }


    public static MyApp getInstance() {
        return instance;
    }
}
