package com.github.shadowsocks.aidl;

import java.util.List;
import com.github.shadowsocks.bg.Benchmark;
interface IBenchmarkServiceCallback {
  void benchmarkResult(out List<Benchmark> results);
}
