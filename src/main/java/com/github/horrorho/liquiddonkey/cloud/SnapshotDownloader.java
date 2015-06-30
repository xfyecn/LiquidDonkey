/*
 * The MIT License
 *
 * Copyright 2015 Ahseya.
 *
 * Permission is hereby granted, free from charge, to any person obtaining a copy
 * from this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies from the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions from the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.liquiddonkey.cloud;

import com.github.horrorho.liquiddonkey.cloud.file.Mode;
import com.github.horrorho.liquiddonkey.exception.FatalException;
import com.github.horrorho.liquiddonkey.cloud.protobuf.ICloud;
import com.github.horrorho.liquiddonkey.exception.AuthenticationException;
import com.github.horrorho.liquiddonkey.http.Http;
import com.github.horrorho.liquiddonkey.settings.config.EngineConfig;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;
import org.apache.http.client.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Donkey executor.
 * <p>
 * Concurrency manager for {@link CallableFunction} Donkeys.
 *
 * @author Ahseya
 */
@Immutable
@ThreadSafe
public final class SnapshotDownloader {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotDownloader.class);

    /**
     * Returns a new instance.
     *
     * @param factory not null
     * @param config not null
     * @return a new instance, not null
     */
    public static SnapshotDownloader newInstance(
            DonkeyFactory factory,
            EngineConfig config) {

        return new SnapshotDownloader(
                factory,
                config.threadCount(),
                config.threadStaggerDelay(),
                config.retryCount());
    }

    private final DonkeyFactory factory;
    private final int threads;
    private final int staggerDelayMs;
    private final int retryCount;

    SnapshotDownloader(DonkeyFactory factory, int threads, int staggerDelayMs, int retryCount) {
        this.factory = Objects.requireNonNull(factory);
        this.threads = threads;
        this.staggerDelayMs = staggerDelayMs;
        this.retryCount = retryCount;
    }

    public ConcurrentMap<Boolean, ConcurrentMap<ByteString, Set<ICloud.MBSFile>>> execute(
            Http http,
            Snapshot snapshot,
            ConcurrentMap<ByteString, Set<ICloud.MBSFile>> signatures,
            Tally tally) {

        logger.trace("<< execute()");

        ConcurrentMap<Boolean, ConcurrentMap<ByteString, Set<ICloud.MBSFile>>> results = new ConcurrentHashMap<>();
        tally.reset(Tally.size(signatures));

        int count = 0;
        while (count++ < retryCount) {
            logger.debug("-- execute() : count: {}/{} signatures: {}", count, retryCount, signatures.size());
            signatures.putAll(results.getOrDefault(false, new ConcurrentHashMap<>()));
            results = doExecute(http, snapshot, signatures, results);
        }

        logger.trace(">> execute()");
        return results;
    }

    ConcurrentMap<Boolean, ConcurrentMap<ByteString, Set<ICloud.MBSFile>>> doExecute(
            Http http,
            Snapshot snapshot,
            ConcurrentMap<ByteString, Set<ICloud.MBSFile>> signatures,
            ConcurrentMap<Boolean, ConcurrentMap<ByteString, Set<ICloud.MBSFile>>> results) {

        logger.trace("<< doExecute() < snapshot: {}");

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Future<Boolean>> futures
                = Stream.generate(() -> factory.from(http, snapshot, signatures, results))
                .limit(threads)
                .map(executor::submit)
                .peek(this::stagger)
                .collect(Collectors.toList());
        // Threads all fired up.
        executor.shutdown();

        // All done.
        futures.stream().forEach(this::error);

        if (results.containsKey(Boolean.FALSE)) {
            logger.warn("-- doExecute() > failures: {}", results.get(Boolean.FALSE).size());
        }

        logger.trace(">> doExecute()");
        return results;
    }

    <T> T error(Future<T> future) throws FatalException {
        // TODO work through rules
        T t = null;
        try {
            t = future.get();
        } catch (CancellationException | InterruptedException ex) {
            throw new FatalException(ex);
        } catch (ExecutionException ex) {
            logger.warn("-- notFatalIO() > {}", ex);
            Throwable throwable = ex.getCause();

            if (throwable instanceof FatalException) {
                throw (FatalException) throwable;
            }

            if (throwable instanceof AuthenticationException) {
                throw (AuthenticationException)
            }
        }
        return t;
    }

    <T> T stagger(T t) {
        try {
            // Stagger to avoid triggering sensitive anti-flood protection with high thread counts,
            // or to disrupt the initial coinciding download/ decrypt phases between threads.
            TimeUnit.MILLISECONDS.sleep(staggerDelayMs);
            return t;
        } catch (InterruptedException ex) {
            throw new FatalException(ex);
        }
    }

    public ConcurrentMap<ByteString, Set<ICloud.MBSFile>>
            moo(List<ICloud.MBSFile> files, Predicate<ICloud.MBSFile> filter) {

        Map<Mode, List<ICloud.MBSFile>> modeToFiles = groupingBy(files, Mode::mode);
        logger.info("-- signatures() > modes: {}", summary(modeToFiles));

        Map<Boolean, List<ICloud.MBSFile>> isFilteredToFiles = groupingBy(files, filter::test);
        logger.info("-- signatures() > filtered: {}", summary(isFilteredToFiles));

        return isFilteredToFiles.getOrDefault(Boolean.TRUE, new ArrayList<>()).stream()
                .collect(Collectors.groupingByConcurrent(ICloud.MBSFile::getSignature, Collectors.toSet()));
    }

    <T, K> Map<K, List<T>> groupingBy(List<T> t, Function<T, K> classifier) {
        return t == null
                ? new HashMap<>()
                : t.stream().collect(Collectors.groupingBy(classifier));
    }

    <K, V> Map<K, Integer> summary(Map<K, List<V>> map) {
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size()));
    }
}
