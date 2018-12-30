/*
 * MIT License
 *
 * Copyright (c) 2018 WangChengbo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.timbobo.lib.crashcatcher;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements UncaughtExceptionHandler {

    /**
     * 时间转换
     */
    private static final SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy年MM月dd日HH时mm分ss秒", Locale.CHINA);

    private static final String TAG = "crash_handler>>>";

    private static final String FILE_NAME_SUFFIX = ".txt";

    /**
     * 单例
     */
    private volatile static CrashHandler sInstance;


    /**
     * 系统默认的UncaughtExceptionHandler
     */
    private UncaughtExceptionHandler mDefaultUncaughtExceptionHandler;

    private Context mContext;

    private CrashHandler() {

    }

    public static CrashHandler getInstance() {
        if (sInstance == null) {
            synchronized (CrashHandler.class) {
                if (sInstance == null) {
                    sInstance = new CrashHandler();
                }
            }
        }
        return sInstance;
    }

    public void init(Application application) {
        mDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mContext = application.getApplicationContext();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            //将崩溃信息记录到文件
            dumpToFile(thread, throwable);
        } catch (Exception e) {
            Log.e(TAG, "文件创建失败 " + e.getMessage());
        }

        throwable.printStackTrace();

        //如果系统仍有设置默认的处理器，则调用系统默认的
        if (mDefaultUncaughtExceptionHandler != null) {
            mDefaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
        } else {
            //结束进程并退出
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private void dumpToFile(Thread thread, Throwable ex) throws IOException {
        File file = null;
        PrintWriter printWriter = null;

        String crashTime = dataFormat.format(new Date(System.currentTimeMillis()));

        String dirPath = Utils.getCrashLogPath(mContext);

        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                return;
            }
        }

        //Log文件的名字
        String fileName = "Crash" + "_" + crashTime + FILE_NAME_SUFFIX;
        file = new File(dir, fileName);
        if (!file.exists()) {
            boolean createNewFileOk = file.createNewFile();
            if (!createNewFileOk) {
                return;
            }
        }

        try {
            //开始写日志
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file)));

            //崩溃时间
            printWriter.println(crashTime);

            //导出APP信息
            dumpAppInfo(printWriter);

            //导出手机信息
            dumpPhoneInfo(printWriter);

            //导出异常的调用栈信息
            ex.printStackTrace(printWriter);

            Log.e(TAG, "崩溃日志输入完成");
        } catch (Exception e) {
            Log.e(TAG, "导出信息失败");
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }

    /**
     * 获取APP信息
     *
     * @param printWriter
     */
    private void dumpAppInfo(PrintWriter printWriter) throws PackageManager.NameNotFoundException {
        //应用的版本名称和版本号
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        printWriter.print("App Version: ");
        printWriter.println(pi.versionName);
        printWriter.print("App VersionCode: ");
        printWriter.println(pi.versionCode);
    }

    /**
     * 获取手机信息
     *
     * @param printWriter
     */
    private void dumpPhoneInfo(PrintWriter printWriter) {
        //android版本号
        printWriter.print("OS Version: ");
        printWriter.print(Build.VERSION.RELEASE);
        printWriter.print("_");
        printWriter.println(Build.VERSION.SDK_INT);

        //手机制造商
        printWriter.print("Vendor: ");
        printWriter.println(Build.MANUFACTURER);

        //手机型号
        printWriter.print("Model: ");
        printWriter.println(Build.MODEL);

        //cpu架构
        printWriter.print("CPU ABI: ");
        printWriter.println(Build.CPU_ABI);
    }
}
