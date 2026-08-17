package com.fongmi.quickjs.method;

import com.github.catvod.utils.Logger;
import com.whl.quickjs.wrapper.QuickJSContext;

public class Console implements QuickJSContext.Console {

    private static final String TAG = "quickjs";

    @Override
    public void log(String info) {
        Logger.d(info);
    }

    @Override
    public void info(String info) {
        Logger.i(info);
    }

    @Override
    public void warn(String info) {
        Logger.w(info);
    }

    @Override
    public void error(String info) {
        Logger.e(info);
    }
}