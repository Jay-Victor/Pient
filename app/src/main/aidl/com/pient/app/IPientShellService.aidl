// Pient 用户服务接口（Shizuku「用户服务」通道，2026-09-13 选型 B）。
//
// 机制（照 Shizuku 官方 demo）：本接口的 Stub 实现类（com.pient.app.runtime.PientShellService）
// **不是** manifest 里的 Service 组件——它由 **Shizuku 守护进程（uid = shell / 2000）在自己的进程里
// 反射实例化**（`createPackageContextAsUser` + 类名），所以我们只声明接口 + 实现类，
// 客户端用 `Shizuku.bindUserService(UserServiceArgs(ComponentName(包名, 类名)), connection)` 绑定。
//
// `destroy()` 的 16777114 是 **Shizuku 服务端保留的事务号**（官方 aidl 同款），不要改。
package com.pient.app;

interface IPientShellService {

    /** Shizuku 保留：请求销毁该用户服务（实现里 System.exit(0)） */
    void destroy() = 16777114;

    /**
     * 执行一条 Android 系统命令（**在守护进程里跑，uid = shell**）。
     * 返回 JSON：{"channel":"shizuku","uid":2000,"code":0,"output":"…"}；失败返回 {"error":…}。
     */
    String exec(String command, String cwd, long timeoutMs) = 1;
}
