/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.r.engine.shell;

import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.DEFAULT_PACKAGES;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.EXPR;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.FILE;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.HELP;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.NO_RESTORE;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.SLAVE;
import static com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption.VERSION;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import com.oracle.truffle.r.runtime.RCmdOptions;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RVersionNumber;
import com.oracle.truffle.r.runtime.context.ContextInfo;

/**
 * Emulates the (Gnu)Rscript command as precisely as possible. in GnuR, Rscript is a genuine wrapper
 * to R, as evidenced by the script {@code print(commandArgs())}. We don't implement it quite that
 * way but the effect is similar.
 *
 */
public class RscriptCommand {
    // CheckStyle: stop system..print check

    private static void preprocessRScriptOptions(RCmdOptions options) {
        String[] arguments = options.getArguments();
        int resultArgsLength = arguments.length;
        int firstNonOptionArgIndex = options.getFirstNonOptionArgIndex();
        if (options.getBoolean(HELP)) {
            RCmdOptions.printHelpAndExit(RCmdOptions.Client.RSCRIPT);
        } else if (options.getBoolean(VERSION)) {
            printVersionAndExit();
        }
        // Now reformat the args, setting --slave and --no-restore as per the spec
        ArrayList<String> adjArgs = new ArrayList<>(resultArgsLength + 1);
        adjArgs.add(arguments[0]);
        adjArgs.add("--slave");
        options.setValue(SLAVE, true);
        adjArgs.add("--no-restore");
        options.setValue(NO_RESTORE, true);
        // Either -e options are set or first non-option arg is a file
        if (options.getStringList(EXPR) == null) {
            if (firstNonOptionArgIndex == resultArgsLength) {
                // does not return
                RCmdOptions.printHelpAndExit(RCmdOptions.Client.RSCRIPT);
            } else {
                if (arguments[firstNonOptionArgIndex].startsWith("-")) {
                    System.out.println("file name is missing");
                    System.exit(1);
                }
                options.setValue(FILE, arguments[firstNonOptionArgIndex]);
            }
        }
        String defaultPackagesArg = options.getString(DEFAULT_PACKAGES);
        String defaultPackagesEnv = System.getenv("R_DEFAULT_PACKAGES");
        if (defaultPackagesArg == null && defaultPackagesEnv == null) {
            defaultPackagesArg = "datasets,utils,grDevices,graphics,stats";
        }
        if (defaultPackagesEnv == null) {
            options.setValue(DEFAULT_PACKAGES, defaultPackagesArg);
        }
        // copy up to non-option args
        int rx = 1;
        while (rx < firstNonOptionArgIndex) {
            adjArgs.add(arguments[rx]);
            rx++;
        }
        if (options.getString(FILE) != null) {
            adjArgs.add("--file=" + options.getString(FILE));
            rx++; // skip over file arg
            firstNonOptionArgIndex++;
        }

        if (firstNonOptionArgIndex < resultArgsLength) {
            adjArgs.add("--args");
            while (rx < resultArgsLength) {
                adjArgs.add(arguments[rx++]);
            }
        }
        options.setArguments(adjArgs.toArray(new String[adjArgs.size()]));
    }

    public static void main(String[] args) {
        doMain(args, null, true, System.in, System.out);
        // never returns
        throw RInternalError.shouldNotReachHere();
    }

    public static int doMain(String[] args, String[] env, boolean initial, InputStream inStream, OutputStream outStream) {
        // Since many of the options are shared parse them from an RSCRIPT perspective.
        // Handle --help and --version specially, as they exit.
        RCmdOptions options = RCmdOptions.parseArguments(RCmdOptions.Client.RSCRIPT, args, false);
        preprocessRScriptOptions(options);
        ContextInfo info = RCommand.createContextInfoFromCommandLine(options, false, initial, inStream, outStream, env);
        return RCommand.readEvalPrint(info.createVM(), info);

    }

    private static void printVersionAndExit() {
        // TODO Not ok in nested context
        System.out.print("FastR scripting front-end version ");
        System.out.println(RVersionNumber.FULL);
        System.exit(0);
    }
}
