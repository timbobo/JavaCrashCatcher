### Android的两种崩溃
Android 崩溃分为 Java 崩溃和 Native崩溃两种。

### 应用退出的情形
在讨论什么是异常退出之前，我们先看看都有哪些应用退出的情形。
1. 主动自杀。Process.killProcess()、exit()等
2. 崩溃。出现了Java或者Native崩溃
3. 系统重启;系统出现异常、断电、用户主动重启等，我们可以通过比较应用开机运行时间是否比之前记录的值更小
4. 被系统杀死。被low memory killer杀掉、从系统的任务管理器中划掉等。
5. ANR

我们可以在应用启动的时候设定一个表示，在主动自杀或者崩溃后更新它，这样下次启动时通过检测这边标志就能确认运行期间是否发生过异常退出。

### Java崩溃的知识点
![image](C:/Users/%E7%8E%8B%E5%9F%8E%E6%B3%A2/Desktop/%E7%AC%94%E8%AE%B0/Java%E5%B4%A9%E6%BA%83.png)

### Java崩溃的原因
简单来说，Java崩溃就是在Java代码中，出现了未被捕获的异常，导致应用程序异常退出。

#### Java异常的归类
Java的异常可分为分为**可查的异常（checkedexceptions）**和**不可查的异常（unchecked exceptions）**
常见的异常可归类为如下图：
![image](C:/Users/%E7%8E%8B%E5%9F%8E%E6%B3%A2/Desktop/%E7%AC%94%E8%AE%B0/Throwable.png)


#### 崩溃的捕捉
