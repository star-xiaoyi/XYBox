package com.github.catvod.utils;

import com.github.catvod.utils.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Shell {

    private static final String TAG = Shell.class.getSimpleName();

    public static String exec(String command) {
        try {
            StringBuilder sb = new StringBuilder();
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            Logger.d("Shell command '" + command + "' with exit code '" + p.waitFor() + "'");
            return Util.substring(sb.toString());
        } catch (Exception e) {
            Logger.e("Error", e);
            return "";
        }
    }
}