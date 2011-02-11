/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.server.tx;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.orientechnologies.orient.core.db.record.ODatabaseRecordTx;
import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.exception.OTransactionException;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.tx.OTransactionEntry;
import com.orientechnologies.orient.core.tx.OTransactionOptimistic;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinary;

public class OTransactionOptimisticProxy extends OTransactionOptimistic {
	private final Map<ORecordId, ORecord<?>>	updatedRecords	= new HashMap<ORecordId, ORecord<?>>();

	public OTransactionOptimisticProxy(final ODatabaseRecordTx iDatabase, final OChannelBinary iChannel) throws IOException {
		super(iDatabase, iChannel.readInt());

		while (iChannel.readByte() == 1) {
			try {
				OTransactionEntryProxy entry = new OTransactionEntryProxy();

				final ORecordId rid = (ORecordId) entry.getRecord().getIdentity();

				entry.status = iChannel.readByte();
				rid.clusterId = iChannel.readShort();
				rid.clusterPosition = iChannel.readLong();
				((OTransactionRecordProxy) entry.getRecord()).setRecordType(iChannel.readByte());

				switch (entry.status) {
				case OTransactionEntry.CREATED:
					entry.clusterName = iChannel.readString();
					entry.getRecord().fromStream(iChannel.readBytes());
					break;

				case OTransactionEntry.UPDATED:
					entry.getRecord().setVersion(iChannel.readInt());
					entry.getRecord().fromStream(iChannel.readBytes());

					// SAVE THE RECORD TO RETRIEVE THEM FOR THE NEW VERSIONS TO SEND BACK TO THE REQUESTER
					updatedRecords.put(rid, entry.getRecord());
					break;

				case OTransactionEntry.DELETED:
					entry.getRecord().setVersion(iChannel.readInt());
					break;

				default:
					throw new OTransactionException("Unrecognized tx command: " + entry.status);
				}

				// PUT IN TEMPORARY LIST TO GET FETCHED AFTER ALL FOR CACHE
				entries.put((ORecordId) entry.getRecord().getIdentity(), entry);

			} catch (IOException e) {
				throw new OSerializationException("Can't read transaction record from the network", e);
			}
		}
	}

	public Map<ORecordId, ORecord<?>> getUpdatedRecords() {
		return updatedRecords;
	}
}
