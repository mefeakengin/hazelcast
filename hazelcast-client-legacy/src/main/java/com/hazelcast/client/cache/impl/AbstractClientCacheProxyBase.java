/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.client.cache.impl;

import com.hazelcast.cache.impl.client.CacheDestroyRequest;
import com.hazelcast.cache.impl.client.CacheLoadAllRequest;
import com.hazelcast.client.impl.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.client.ClientRequest;
import com.hazelcast.client.spi.ClientContext;
import com.hazelcast.client.spi.impl.ClientInvocation;
import com.hazelcast.client.spi.impl.ClientInvocationFuture;
import com.hazelcast.config.CacheConfig;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.impl.SerializableList;
import com.hazelcast.util.ExceptionUtil;

import javax.cache.CacheException;
import javax.cache.integration.CompletionListener;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract class providing cache open/close operations and {@link ClientContext} accessor which will be used
 * by implementation of {@link com.hazelcast.cache.ICache} for client.
 *
 * @param <K> the type of key
 * @param <V> the type of value
 */
abstract class AbstractClientCacheProxyBase<K, V> {

    static final int TIMEOUT = 10;

    private static final CompletionListener NULL_COMPLETION_LISTENER = new CompletionListener() {
        @Override
        public void onCompletion() {
        }

        @Override
        public void onException(Exception e) {
        }
    };

    protected final ILogger logger = Logger.getLogger(getClass());

    protected final ClientContext clientContext;
    protected final CacheConfig<K, V> cacheConfig;
    protected final String name;
    protected final String nameWithPrefix;

    private final ConcurrentMap<Future, CompletionListener> loadAllCalls
            = new ConcurrentHashMap<Future, CompletionListener>();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

    protected AbstractClientCacheProxyBase(CacheConfig cacheConfig, ClientContext clientContext) {
        this.name = cacheConfig.getName();
        this.nameWithPrefix = cacheConfig.getNameWithPrefix();
        this.cacheConfig = cacheConfig;
        this.clientContext = clientContext;
    }

    protected void ensureOpen() {
        if (isClosed()) {
            throw new IllegalStateException("Cache operations can not be performed. The cache closed");
        }
    }

    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        waitOnGoingLoadAllCallsToFinish();
        closeListeners();
    }

    private void waitOnGoingLoadAllCallsToFinish() {
        Iterator<Map.Entry<Future, CompletionListener>> iterator = loadAllCalls.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Future, CompletionListener> entry = iterator.next();
            Future f = entry.getKey();
            CompletionListener completionListener = entry.getValue();
            try {
                f.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (Throwable t) {
                logger.finest("Error occurred at loadAll operation execution while waiting it to finish on cache close!", t);
                handleFailureOnCompletionListener(completionListener, t);
            }
            iterator.remove();
        }
    }

    public void destroy() {
        close();
        if (!isDestroyed.compareAndSet(false, true)) {
            return;
        }
        isClosed.set(true);
        try {
            int partitionId = clientContext.getPartitionService().getPartitionId(nameWithPrefix);
            CacheDestroyRequest request = new CacheDestroyRequest(nameWithPrefix, partitionId);
            final ClientInvocation clientInvocation =
                    new ClientInvocation((HazelcastClientInstanceImpl) clientContext.getHazelcastInstance(),
                            request, partitionId);
            final Future<SerializableList> future = clientInvocation.invoke();
            future.get();
        } catch (Exception e) {
            throw ExceptionUtil.rethrow(e);
        }
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public boolean isDestroyed() {
        return isDestroyed.get();
    }

    public void open() {
        if (isDestroyed.get()) {
            throw new IllegalStateException("Cache is already destroyed! Cannot be reopened");
        }
        if (!isClosed.compareAndSet(true, false)) {
            return;
        }
    }

    protected abstract void closeListeners();

    public String getNameWithPrefix() {
        return nameWithPrefix;
    }

    protected <T> T toObject(Object data) {
        return clientContext.getSerializationService().toObject(data);
    }

    protected Data toData(Object o) {
        return clientContext.getSerializationService().toData(o);
    }

    protected <T> T invoke(ClientRequest req) {
        try {
            final ClientInvocation clientInvocation =
                    new ClientInvocation((HazelcastClientInstanceImpl) clientContext.getHazelcastInstance(), req);
            final Future<SerializableList> future = clientInvocation.invoke();
            Object result = future.get();
            return toObject(result);
        } catch (Exception e) {
            throw ExceptionUtil.rethrow(e);
        }
    }

    protected void submitLoadAllTask(final CacheLoadAllRequest request, final CompletionListener completionListener) {
        final CompletionListener compListener = completionListener != null ? completionListener : NULL_COMPLETION_LISTENER;
        ClientInvocationFuture invocationFuture = null;
        try {
            final long start = System.nanoTime();
            invocationFuture = new ClientInvocation(
                    (HazelcastClientInstanceImpl) clientContext.getHazelcastInstance(), request).invoke();
            final Future invFuture = invocationFuture;
            loadAllCalls.put(invFuture, compListener);
            invocationFuture.andThen(new ExecutionCallback<V>() {
                @Override
                public void onResponse(V response) {
                    loadAllCalls.remove(invFuture);
                    onLoadAll(request.getKeys(), response, start, System.nanoTime());
                    compListener.onCompletion();
                }

                @Override
                public void onFailure(Throwable t) {
                    loadAllCalls.remove(invFuture);
                    handleFailureOnCompletionListener(compListener, t);
                }
            });

        } catch (Throwable t) {
            if (invocationFuture != null) {
                loadAllCalls.remove(invocationFuture);
            }
            handleFailureOnCompletionListener(compListener, t);
        }
    }

    private void handleFailureOnCompletionListener(CompletionListener completionListener,
                                                   Throwable t) {
        if (t instanceof Exception) {
            Throwable cause = t.getCause();
            if (t instanceof ExecutionException && cause instanceof CacheException) {
                completionListener.onException((CacheException) cause);
            } else {
                completionListener.onException((Exception) t);
            }
        } else {
            if (t instanceof OutOfMemoryError) {
                ExceptionUtil.rethrow(t);
            } else {
                completionListener.onException(new CacheException(t));
            }
        }
    }

    protected void onLoadAll(Set<Data> keys, Object response, long start, long end) {

    }

}
