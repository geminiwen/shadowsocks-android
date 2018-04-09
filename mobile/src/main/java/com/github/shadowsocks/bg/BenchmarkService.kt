package com.github.shadowsocks.bg

import android.annotation.TargetApi
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.SystemClock
import android.support.v4.os.UserManagerCompat
import com.github.shadowsocks.App
import com.github.shadowsocks.acl.Acl
import com.github.shadowsocks.aidl.IBenchmarkService
import com.github.shadowsocks.aidl.IBenchmarkServiceCallback
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.ProfileManager
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.utils.Commandline
import com.github.shadowsocks.utils.TcpFastOpen
import com.github.shadowsocks.utils.responseLength
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.nio.charset.Charset
import java.util.*


class BenchmarkService: Service() {

    private val callbacks = RemoteCallbackList<IBenchmarkServiceCallback>()

    private val binder = object: IBenchmarkService.Stub() {
        override fun registerCallback(cb: IBenchmarkServiceCallback) {
            callbacks.register(cb)
        }

        override fun unregisterCallback(cb: IBenchmarkServiceCallback) {
            callbacks.unregister(cb)
        }

        override fun startBenchmark() {
            this@BenchmarkService.startBenchmark()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }


    fun startBenchmark() {
        val profiles = ProfileManager.getAllProfiles() ?: return
        val promises = profiles.map { serveBenchmarkPromise(it) }
        Observable.merge(promises)
                .toList()
                .subscribe(Consumer {
                    val count = callbacks.beginBroadcast()
                    for(i in 0 until count) {
                        callbacks.getBroadcastItem(i)
                                .benchmarkResult(it)
                    }
                    callbacks.finishBroadcast()
                })
    }

    private fun serveBenchmarkPromise(profile: Profile): Observable<Benchmark> {
        return Observable.fromCallable {
            val port =  Random().nextInt(65536 - 10000) + 10000
            val config = buildShadowsocksConfig(profile, port)

            val cmd = arrayListOf(
                    File((this as Context).applicationInfo.nativeLibraryDir, Executable.SS_LOCAL).absolutePath,
                    "-u",
                    "-b", "127.0.0.1",
                    "-l", "$port",
                    "-t", "600",
                    "-c", config.absolutePath)

            if (profile.udpdns) cmd += "-D"

            if (TcpFastOpen.sendEnabled) cmd += "--fast-open"

            val sslocalProcess = GuardedProcess(cmd)
            try {
                sslocalProcess.start()
                val delay = testConnection(port)
                Benchmark(profile.id, delay)
            } catch (e: IOException) {
                Benchmark(profile.id, -1L)
            } finally {
                sslocalProcess.destroy()
                config.delete()
            }
        }.subscribeOn(Schedulers.io())
    }

    val TIME_OUT = 5000

    private fun testConnection(port: Int): Long {
        val url = URL("http", "ip.cn", "/")
        val conn = url.openConnection(Proxy(Proxy.Type.SOCKS,
                    InetSocketAddress("127.0.0.1", port)))
                as HttpURLConnection
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("User-Agent", "curl/1")
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.connectTimeout = TIME_OUT
        conn.readTimeout = TIME_OUT
        try {
            val start = SystemClock.elapsedRealtime()
            val code = conn.responseCode
//            val responseLength = conn.responseLength
//            val input = conn.inputStream
//                            .bufferedReader(Charset.forName("utf-8"))
//                            .readLine()
            if (code == 204 || code == 200
//                    && responseLength == 0L
            )
                return SystemClock.elapsedRealtime() - start
            return -1L
        } catch (e: IOException) {
            return -1L
        } finally {
            conn.disconnect()
        }

    }

    private fun buildShadowsocksConfig(profile: Profile, port: Int): File {
        val config = JSONObject()
                .put("server", profile.host)
                .put("server_port", profile.remotePort)
                .put("password", profile.password)
                .put("method", profile.method)
        val plugin = PluginConfiguration(profile.plugin ?: "").selectedOptions
        val pluginPath =  PluginManager.init(plugin)
        if (pluginPath != null) {
            val pluginCmd = arrayListOf(pluginPath)
            if (TcpFastOpen.sendEnabled) pluginCmd.add("--fast-open")
            config
                    .put("plugin", Commandline.toString(pluginCmd))
                    .put("plugin_opts", plugin.toString())
        }
        // sensitive Shadowsocks config is stored in
        val file = File(if (UserManagerCompat.isUserUnlocked(App.app)) App.app.filesDir else @TargetApi(24) {
            App.app.deviceContext.noBackupFilesDir  // only API 24+ will be in locked state
        }, BaseService.CONFIG_FILE + "." + port)
        file.writeText(config.toString())
        return file
    }
}