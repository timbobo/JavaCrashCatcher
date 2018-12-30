### Android的两种崩溃
Android 崩溃分为 Java 崩溃和 Native崩溃两种。

### Java崩溃的知识点
![Java崩溃](https://note.youdao.com/yws/public/resource/e41e224bd4b2a444fba146392244d8ea/3DB6860231FC4C09A93261A30B3C9360/5D31BB62EF2A46D9BA8C6FB1E739D202?ynotemdtimestamp=1546194914617)

### Java崩溃的原因
简单来说，Java崩溃就是在Java代码中，出现了未被捕获的异常，导致应用程序异常退出。

#### Java异常的归类
Java的异常可分为分为**可查的异常（checkedexceptions）**和**不可查的异常（unchecked exceptions）**<br>
**常见的异常**可归类为如下图：
![Throwable](https://note.youdao.com/yws/public/resource/e41e224bd4b2a444fba146392244d8ea/3DB6860231FC4C09A93261A30B3C9360/D16594DAAED94251ADE3B6BE76B14F8F?ynotemdtimestamp=1546194914617)
其中Error和RuntimeException是unchecked exceptions，编译器默认无法通过对其处理。其余checkedexceptions，需要我们在代码中try-catch。

#### 崩溃的捕捉

##### UncaughtExceptionHandler
先来看一下这个接口的作用

```
   /**
     * Interface for handlers invoked when a <tt>Thread</tt> abruptly
     * terminates due to an uncaught exception.
     * <p>When a thread is about to terminate due to an uncaught exception
     * the Java Virtual Machine will query the thread for its
     * <tt>UncaughtExceptionHandler</tt> using
     * {@link #getUncaughtExceptionHandler} and will invoke the handler's
     * <tt>uncaughtException</tt> method, passing the thread and the
     * exception as arguments.
     * If a thread has not had its <tt>UncaughtExceptionHandler</tt>
     * explicitly set, then its <tt>ThreadGroup</tt> object acts as its
     * <tt>UncaughtExceptionHandler</tt>. If the <tt>ThreadGroup</tt> object
     * has no
     * special requirements for dealing with the exception, it can forward
     * the invocation to the {@linkplain #getDefaultUncaughtExceptionHandler
     * default uncaught exception handler}.
     *
     * @see #setDefaultUncaughtExceptionHandler
     * @see #setUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        /**
         * Method invoked when the given thread terminates due to the
         * given uncaught exception.
         * <p>Any exception thrown by this method will be ignored by the
         * Java Virtual Machine.
         * @param t the thread
         * @param e the exception
         */
        void uncaughtException(Thread t, Throwable e);
    }
```
大致意思就是如果线程发生未处理的异常，会调用UncaughtExceptionHandler的uncaugthException方法去处理异常，如果该线程没有设置UncaughtExceptionHandler，则会去调用ThreadGroup的UncaughtExceptionHandler，若还是没有，则最终getDefaultUncaughtExceptionHandler来处理异常。

##### 系统默认的UncaughtExceptionHandler
日常当我们应用崩溃时，会有一个默认的系统弹窗，告知我们应用崩溃，那系统的崩溃是如何定义的呢？源码如下，注释已经比较完整。
```
 /**
     * Handle application death from an uncaught exception.  The framework
     * catches these for the main threads, so this should only matter for
     * threads created by applications. Before this method runs, the given
     * instance of {@link LoggingHandler} should already have logged details
     * (and if not it is run first).
     */
    private static class KillApplicationHandler implements Thread.UncaughtExceptionHandler {
        private final LoggingHandler mLoggingHandler;

        /**
         * Create a new KillApplicationHandler that follows the given LoggingHandler.
         * If {@link #uncaughtException(Thread, Throwable) uncaughtException} is called
         * on the created instance without {@code loggingHandler} having been triggered,
         * {@link LoggingHandler#uncaughtException(Thread, Throwable)
         * loggingHandler.uncaughtException} will be called first.
         *
         * @param loggingHandler the {@link LoggingHandler} expected to have run before
         *     this instance's {@link #uncaughtException(Thread, Throwable) uncaughtException}
         *     is being called.
         */
        public KillApplicationHandler(LoggingHandler loggingHandler) {
            this.mLoggingHandler = Objects.requireNonNull(loggingHandler);
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            try {
                ensureLogging(t, e);

                // Don't re-enter -- avoid infinite loops if crash-reporting crashes.
                if (mCrashing) return;
                mCrashing = true;

                // Try to end profiling. If a profiler is running at this point, and we kill the
                // process (below), the in-memory buffer will be lost. So try to stop, which will
                // flush the buffer. (This makes method trace profiling useful to debug crashes.)
                if (ActivityThread.currentActivityThread() != null) {
                    ActivityThread.currentActivityThread().stopProfiling();
                }

                // Bring up crash dialog, wait for it to be dismissed
                ActivityManager.getService().handleApplicationCrash(
                        mApplicationObject, new ApplicationErrorReport.ParcelableCrashInfo(e));
            } catch (Throwable t2) {
                if (t2 instanceof DeadObjectException) {
                    // System process is dead; ignore
                } else {
                    try {
                        Clog_e(TAG, "Error reporting crash", t2);
                    } catch (Throwable t3) {
                        // Even Clog_e() fails!  Oh well.
                    }
                }
            } finally {
                // Try everything to make sure this process goes away.
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
```
该接口实现在RuntimeInit类中，并在Runtime初始化时写入设置成我们默认的异常处理类
```
RuntimeInit.class

protected static final void commonInit() {
    if (DEBUG) Slog.d(TAG, "Entered RuntimeInit!");

    /*
     * set handlers; these apply to all threads in the VM. Apps can replace
     * the default handler, but not the pre handler.
     */
    LoggingHandler loggingHandler = new LoggingHandler();
    Thread.setUncaughtExceptionPreHandler(loggingHandler);
    Thread.setDefaultUncaughtExceptionHandler(new KillApplicationHandler(loggingHandler));
    ......
    }
```

##### 自定义崩溃捕捉
到这里思路已经很清晰了，我们要做的就是自己实现一个对崩溃处理的UncaughtExceptionHandler，那么我们应该设置在哪，初始化的时机在何时。我们先来看看系统初始化用到的方法，即Thread.setDefaultUncaughtExceptionHandler()。

```
/**
     * Set the default handler invoked when a thread abruptly terminates
     * due to an uncaught exception, and no other handler has been defined
     * for that thread.
     *
     * <p>Uncaught exception handling is controlled first by the thread, then
     * by the thread's {@link ThreadGroup} object and finally by the default
     * uncaught exception handler. If the thread does not have an explicit
     * uncaught exception handler set, and the thread's thread group
     * (including parent thread groups)  does not specialize its
     * <tt>uncaughtException</tt> method, then the default handler's
     * <tt>uncaughtException</tt> method will be invoked.
     * <p>By setting the default uncaught exception handler, an application
     * can change the way in which uncaught exceptions are handled (such as
     * logging to a specific device, or file) for those threads that would
     * already accept whatever &quot;default&quot; behavior the system
     * provided.
     *
     * <p>Note that the default uncaught exception handler should not usually
     * defer to the thread's <tt>ThreadGroup</tt> object, as that could cause
     * infinite recursion.
     *
     * @param eh the object to use as the default uncaught exception handler.
     * If <tt>null</tt> then there is no default handler.
     *
     * @throws SecurityException if a security manager is present and it
     *         denies <tt>{@link RuntimePermission}
     *         (&quot;setDefaultUncaughtExceptionHandler&quot;)</tt>
     *
     * @see #setUncaughtExceptionHandler
     * @see #getUncaughtExceptionHandler
     * @see ThreadGroup#uncaughtException
     * @since 1.5
     */
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
         defaultUncaughtExceptionHandler = eh;
     }
```
这个defaultUncaughtHandler是Thread类中一个静态的成员，所以，按道理，我们为任意一个线程设置异常处理，所有的线程都应该能共用这个异常处理器。为了在ui线程中添加异常处理Handler，我推荐大家在**Application中添加**而不是在Activity中添加。Application标识着整个应用，在Android声明周期中是第一个启动的，早于任何的Activity、Service等。

**有了以上的知识，我们就可以自己来实现一个崩溃捕捉和处理的lib啦**<br>
其实实现方法网上都大同小异，主要是对异常捕获后的处理机制不一致。一般会通过储存崩溃日志并上报这种方案去解决。这里先基础实现崩溃日志文件的存储。

一个崩溃日志应该包括的基本信息有：
- 崩溃原因和栈记录
- 日期和APP版本信息
- 机型信息

所以我们定义基础的异常处理器如下：

```
 @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            //将崩溃信息记录到文件
            dumpToFile(thread, throwable);
        } catch (IOException e) {
            e.printStackTrace();
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
```
记录文件具体如下：

```
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
```
好了，Java崩溃捕获大致就这样。

#### Demo地址：
https://github.com/timbobo/JavaCrashCatcher 


