/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.collection.multimap.tx;

import com.hazelcast.collection.CollectionDataSerializerHook;
import com.hazelcast.collection.CollectionProxyId;
import com.hazelcast.collection.operations.CollectionKeyBasedOperation;
import com.hazelcast.nio.serialization.Data;

/**
 * @ali 4/12/13
 */
public class TxnGenerateRecordIdOperation extends CollectionKeyBasedOperation implements TxnMultiMapOperation{

    public TxnGenerateRecordIdOperation() {
    }

    public TxnGenerateRecordIdOperation(CollectionProxyId proxyId, Data dataKey) {
        super(proxyId, dataKey);
    }

    public void run() throws Exception {
        response = getOrCreateContainer().nextId();
    }

    public int getId() {
        return CollectionDataSerializerHook.TXN_GENERATE_RECORD_ID;
    }
}
