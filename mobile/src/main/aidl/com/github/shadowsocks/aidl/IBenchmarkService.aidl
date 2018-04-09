// IBenchmarkService.aidl
package com.github.shadowsocks.aidl;
import com.github.shadowsocks.aidl.IBenchmarkServiceCallback;
// Declare any non-default types here with import statements

interface IBenchmarkService {
    oneway void registerCallback(IBenchmarkServiceCallback cb);

    oneway void unregisterCallback(IBenchmarkServiceCallback cb);

    oneway void startBenchmark();

}
