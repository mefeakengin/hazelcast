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

package com.hazelcast.cache.impl.operation;

import com.hazelcast.cache.impl.CacheDataSerializerHook;
import com.hazelcast.cache.impl.ICacheService;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.spi.impl.AbstractNamedOperation;
import com.hazelcast.spi.impl.MutatingOperation;

import java.io.IOException;

/**
 * <p>Destroys the cache on the cluster or on a single node by calling
 * {@link CacheService#deleteCache(String, boolean, String, boolean)}.
 * </p>
 * @see CacheService#deleteCache(String, boolean, String, boolean)
 */
public class CacheDestroyOperation
        extends AbstractNamedOperation
        implements IdentifiedDataSerializable, MutatingOperation {

    boolean isLocal;

    public CacheDestroyOperation() {
    }

    public CacheDestroyOperation(String name) {
        this(name, false);
    }

    public CacheDestroyOperation(String name, boolean isLocal) {
        super(name);
        this.isLocal = isLocal;
    }

    @Override
    public void run()
            throws Exception {
        final ICacheService service = getService();
        service.deleteCache(name, isLocal, getCallerUuid(), true);
    }

    @Override
    public int getId() {
        return CacheDataSerializerHook.DESTROY_CACHE;
    }

    @Override
    public int getFactoryId() {
        return CacheDataSerializerHook.F_ID;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out)
            throws IOException {
        super.writeInternal(out);
        out.writeBoolean(isLocal);
    }

    @Override
    protected void readInternal(ObjectDataInput in)
            throws IOException {
        super.readInternal(in);
        isLocal = in.readBoolean();
    }

}
