/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.MemPoolProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class HashBench {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        volatile byte[] FILE_BYTES;
        volatile HexFormat HEX_FORMAT;

        public BenchmarkState() {
            try {
                FILE_BYTES = Files.readAllBytes(Paths.get(
                        "/home/work/Documents/output.file"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            HEX_FORMAT = HexFormat.of();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        volatile MessageDigest DIGEST;

        public ThreadState() {
            try {
                DIGEST = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Benchmark
    public long readAndWrite(BenchmarkState state) throws IOException {
        try (
                var is = new ByteArrayInputStream(state.FILE_BYTES);
                var os = OutputStream.nullOutputStream()
        ) {
            return transfer(is, os);
        }
    }

    @Benchmark
    public String readAndWriteWithHashCalculationOnFly(BenchmarkState benchmarkState, ThreadState threadState,
                                                       Blackhole blackhole) throws IOException {
        try (
                var is = new DigestInputStream(new ByteArrayInputStream(benchmarkState.FILE_BYTES), threadState.DIGEST);
                var os = OutputStream.nullOutputStream()
        ) {
            blackhole.consume(transfer(is, os));
            return benchmarkState.HEX_FORMAT.formatHex(is.getMessageDigest().digest());
        }
    }

    private long transfer(InputStream is, OutputStream os) throws IOException {
        int bufferSize = 16384;
        long transferred = 0;
        byte[] buffer = new byte[bufferSize];
        int length;
        while ((length = is.read(buffer, 0, bufferSize)) != -1) {
            os.write(buffer, 0, length);
            if (transferred < Long.MAX_VALUE) {
                try {
                    transferred = Math.addExact(transferred, length);
                } catch (ArithmeticException ignore) {
                    transferred = Long.MAX_VALUE;
                }
            }
        }
        return transferred;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashBench.class.getSimpleName())
                .addProfiler(MemPoolProfiler.class)
                .addProfiler(GCProfiler.class)
                .forks(1)
                .warmupIterations(1)
                .measurementIterations(1)
                .resultFormat(ResultFormatType.JSON)
                .build();

        new Runner(opt).run();
    }

}
