/*
 * Copyright 2012-2018 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.test.sync.basic;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.test.sync.TestSync;

public class TestOperateMap extends TestSync {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	private static final String binName = "opmapbin";
	
	@Test
	public void operateMapPut() {
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey1");		
		client.delete(null, key);
		
		MapPolicy putMode = MapPolicy.Default;
		MapPolicy addMode = new MapPolicy(MapOrder.UNORDERED, MapWriteMode.CREATE_ONLY);
		MapPolicy updateMode = new MapPolicy(MapOrder.UNORDERED, MapWriteMode.UPDATE_ONLY);
		MapPolicy orderedUpdateMode = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE_ONLY);
		
		// Calling put() multiple times performs poorly because the server makes
		// a copy of the map for each call, but we still need to test it.
		// putItems() should be used instead for best performance.
		Record record = client.operate(null, key,
				MapOperation.put(putMode, binName, Value.get(11), Value.get(789)),
				MapOperation.put(putMode, binName, Value.get(10), Value.get(999)),
				MapOperation.put(addMode, binName, Value.get(12), Value.get(500)),
				MapOperation.put(addMode, binName, Value.get(15), Value.get(1000)),
				// Ordered type should be ignored since map has already been created in first put().
				MapOperation.put(orderedUpdateMode, binName, Value.get(10), Value.get(1)),
				MapOperation.put(updateMode, binName, Value.get(15), Value.get(5))
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
				
		List<?> results = record.getList(binName);
		int i = 0;

		long size = (Long)results.get(i++);	
		assertEquals(1, size);
		
		size = (Long)results.get(i++);	
		assertEquals(2, size);
		
		size = (Long)results.get(i++);	
		assertEquals(3, size);
		
		size = (Long)results.get(i++);	
		assertEquals(4, size);
		
		size = (Long)results.get(i++);	
		assertEquals(4, size);
		
		size = (Long)results.get(i++);	
		assertEquals(4, size);

		record = client.get(null, key, binName);
		//System.out.println("Record: " + record);
		
		Map<?,?> map = record.getMap(binName);	
		assertEquals(4, map.size());
		assertEquals(1L, map.get(10L));
	}

	@Test
	public void operateMapPutItems() {
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey2");	
		client.delete(null, key);
		
		Map<Value,Value> addMap = new HashMap<Value,Value>();
		addMap.put(Value.get(12), Value.get("myval"));
		addMap.put(Value.get(-8734), Value.get("str2"));
		addMap.put(Value.get(1), Value.get("my default"));
		
		Map<Value,Value> putMap = new HashMap<Value,Value>();
		putMap.put(Value.get(12), Value.get("myval12222"));
		putMap.put(Value.get(13), Value.get("str13"));

		Map<Value,Value> updateMap = new HashMap<Value,Value>();
		updateMap.put(Value.get(13), Value.get("myval2"));

		Map<Value,Value> replaceMap = new HashMap<Value,Value>();
		replaceMap.put(Value.get(12), Value.get(23));
		replaceMap.put(Value.get(-8734), Value.get("changed"));
	
		MapPolicy putMode = MapPolicy.Default;
		MapPolicy addMode = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.CREATE_ONLY);
		MapPolicy updateMode = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE_ONLY);

		Record record = client.operate(null, key,
			MapOperation.putItems(addMode, binName, addMap),
			MapOperation.putItems(putMode, binName, putMap),
			MapOperation.putItems(updateMode, binName, updateMap),
			MapOperation.putItems(updateMode, binName, replaceMap),
			MapOperation.getByKey(binName, Value.get(1), MapReturnType.VALUE),
			MapOperation.getByKey(binName, Value.get(-8734), MapReturnType.VALUE),
			MapOperation.getByKeyRange(binName, Value.get(12), Value.get(15), MapReturnType.KEY_VALUE)
			);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		List<?> results = record.getList(binName);
		int i = 0;
		
		long size = (Long)results.get(i++);	
		assertEquals(3, size);

		size = (Long)results.get(i++);	
		assertEquals(4, size);

		size = (Long)results.get(i++);	
		assertEquals(4, size);

		size = (Long)results.get(i++);	
		assertEquals(4, size);

		String str = (String)results.get(i++);	
		assertEquals("my default", str);
		
		str = (String)results.get(i++);	
		assertEquals("changed", str);

		List<?> list = (List<?>)results.get(i++);	
		assertEquals(2, list.size());
	}

	@Test
	public void operateMapMixed() {
		// Test normal operations with map operations.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey2");		
		client.delete(null, key);
		
		Map<Value,Value> itemMap = new HashMap<Value,Value>();
		itemMap.put(Value.get(12), Value.get("myval"));
		itemMap.put(Value.get(-8734), Value.get("str2"));
		itemMap.put(Value.get(1), Value.get("my default"));
		itemMap.put(Value.get(7), Value.get(1));
		
		Record record = client.operate(null, key,
				MapOperation.putItems(new MapPolicy(MapOrder.KEY_VALUE_ORDERED, MapWriteMode.UPDATE), binName, itemMap),
				Operation.put(new Bin("otherbin", "hello"))
				);

		assertRecordFound(key, record);

		long size = record.getLong(binName);
		assertEquals(4, size);

		record = client.operate(null, key,
				MapOperation.getByKey(binName, Value.get(12), MapReturnType.INDEX),
				Operation.append(new Bin("otherbin", Value.get("goodbye"))),
				Operation.get("otherbin")
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		long index = record.getLong(binName);
		assertEquals(3, index);

		List<?> results = record.getList("otherbin");		
		String val = (String)results.get(1);
		assertEquals("hellogoodbye", val);
	}

	@Test
	public void operateMapSwitch() {
		// Switch from unordered map to a key ordered map.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey4");		
		client.delete(null, key);
		
		Record record = client.operate(null, key,
				MapOperation.put(MapPolicy.Default, binName, Value.get(4), Value.get(4)),
				MapOperation.put(MapPolicy.Default, binName, Value.get(3), Value.get(3)),
				MapOperation.put(MapPolicy.Default, binName, Value.get(2), Value.get(2)),
				MapOperation.put(MapPolicy.Default, binName, Value.get(1), Value.get(1)),
				MapOperation.getByIndex(binName, 2, MapReturnType.KEY_VALUE),
				MapOperation.getByIndexRange(binName, 0, 10, MapReturnType.KEY_VALUE)
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		List<?> results = record.getList(binName);
		int i = 3;
		
		long size = (Long)results.get(i++);	
		assertEquals(4, size);
		
		List<?> list = (List<?>)results.get(i++);
		assertEquals(1, list.size());

		list = (List<?>)results.get(i++);
		assertEquals(4, list.size());
		
		record = client.operate(null, key,
				MapOperation.setMapPolicy(new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE), binName),
				MapOperation.getByKeyRange(binName, Value.get(3), Value.get(5), MapReturnType.COUNT),
				MapOperation.getByKeyRange(binName, Value.get(-5), Value.get(2), MapReturnType.KEY_VALUE),
				MapOperation.getByIndexRange(binName, 0, 10, MapReturnType.KEY_VALUE)
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		results = record.getList(binName);
		i = 0;
		
		Object obj = results.get(i++);
		assertNull(obj);
		
		long val = (Long)results.get(i++);	
		assertEquals(2, val);
		
		list = (List<?>)results.get(i++);
		assertEquals(1, list.size());
		Entry<?,?> entry = (Entry<?,?>)list.get(0);
		assertEquals(1L, entry.getValue());

		list = (List<?>)results.get(i++);
		entry = (Entry<?,?>)list.get(3);
		assertEquals(4L, entry.getKey());
	}

	@Test
	public void operateMapRank() {
		// Test rank.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey6");
		client.delete(null, key);
		
		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		inputMap.put(Value.get("John"), Value.get(76));
		inputMap.put(Value.get("Harry"), Value.get(82));
		
		// Write values to empty map.
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap)
				);
					
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		// Increment some user scores.
		record = client.operate(null, key, 
				MapOperation.increment(MapPolicy.Default, binName, Value.get("John"), Value.get(5)),
				MapOperation.decrement(MapPolicy.Default, binName, Value.get("Jim"), Value.get(4))
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		// Get scores.
		record = client.operate(null, key,
				MapOperation.getByRankRange(binName, -2, 2, MapReturnType.KEY),
				MapOperation.getByRankRange(binName, 0, 2, MapReturnType.KEY_VALUE),
				MapOperation.getByRank(binName, 0, MapReturnType.VALUE),
				MapOperation.getByRank(binName, 2, MapReturnType.KEY),
				MapOperation.getByValueRange(binName, Value.get(90), Value.get(95), MapReturnType.RANK),
				MapOperation.getByValueRange(binName, Value.get(90), Value.get(95), MapReturnType.COUNT),
				MapOperation.getByValueRange(binName, Value.get(90), Value.get(95), MapReturnType.KEY_VALUE),
				MapOperation.getByValueRange(binName, Value.get(81), Value.get(82), MapReturnType.KEY),
				MapOperation.getByValue(binName, Value.get(77), MapReturnType.KEY),
				MapOperation.getByValue(binName, Value.get(81), MapReturnType.RANK),
				MapOperation.getByKey(binName, Value.get("Charlie"), MapReturnType.RANK),
				MapOperation.getByKey(binName, Value.get("Charlie"), MapReturnType.REVERSE_RANK)
				);

		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		List<?> results = record.getList(binName);
		int i = 0;
		
		List<?> list = (List<?>)results.get(i++);
		String str;
		long val;
		
		str = (String)list.get(0);	
		assertEquals("Harry", str);
		str = (String)list.get(1);	
		assertEquals("Jim", str);
		
		list = (List<?>)results.get(i++);
		Entry<?,?> entry = (Entry<?,?>)list.get(0);
		assertEquals("Charlie", entry.getKey());
		assertEquals(55L, entry.getValue());
		entry = (Entry<?,?>)list.get(1);
		assertEquals("John", entry.getKey());
		assertEquals(81L, entry.getValue());

		val = (Long)results.get(i++);	
		assertEquals(55, val);
		
		str = (String)results.get(i++);	
		assertEquals("Harry", str);
		
		list = (List<?>)results.get(i++);
		val = (Long)list.get(0);	
		assertEquals(3, val);
		
		val = (Long)results.get(i++);	
		assertEquals(1, val);
		
		list = (List<?>)results.get(i++);
		entry = (Entry<?,?>)list.get(0);
		assertEquals("Jim", entry.getKey());
		assertEquals(94L, entry.getValue());
		
		list = (List<?>)results.get(i++);
		str = (String)list.get(0);	
		assertEquals("John", str);
		
		list = (List<?>)results.get(i++);
		assertEquals(0, list.size());
		
		list = (List<?>)results.get(i++);
		val = (Long)list.get(0);	
		assertEquals(1, val);
		
		val = (Long)results.get(i++);	
		assertEquals(0, val);
		
		val = (Long)results.get(i++);	
		assertEquals(3, val);
	}

	@Test
	public void operateMapRemove() {
		// Test remove.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey7");
		client.delete(null, key);

		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		inputMap.put(Value.get("John"), Value.get(76));
		inputMap.put(Value.get("Harry"), Value.get(82));
		inputMap.put(Value.get("Sally"), Value.get(79));
		inputMap.put(Value.get("Lenny"), Value.get(84));
		inputMap.put(Value.get("Abe"), Value.get(88));
		
		List<Value> removeItems = new ArrayList<Value>();
		removeItems.add(Value.get("Sally"));
		removeItems.add(Value.get("UNKNOWN"));
		removeItems.add(Value.get("Lenny"));
		
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap),
				MapOperation.removeByKey(binName, Value.get("NOTFOUND"), MapReturnType.VALUE),
				MapOperation.removeByKey(binName, Value.get("Jim"), MapReturnType.VALUE),
				MapOperation.removeByKeyList(binName, removeItems, MapReturnType.COUNT),
				MapOperation.removeByValue(binName, Value.get(55), MapReturnType.KEY),
				MapOperation.size(binName)
				);

		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		List<?> results = record.getList(binName);
		int i = 0;
		
		long val = (Long)results.get(i++);	
		assertEquals(7, val);
		
		Object obj = results.get(i++);
		assertNull(obj);
		
		val = (Long)results.get(i++);
		assertEquals(98, val);

		val = (Long)results.get(i++);	
		assertEquals(2, val);
		
		List<?> list = (List<?>)results.get(i++);	
		assertEquals(1, list.size());
		assertEquals("Charlie", (String)list.get(0));
		
		val = (Long)results.get(i++);	
		assertEquals(3, val);
	}

	@Test
	public void operateMapRemoveRange() {
		// Test remove ranges.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey8");
		client.delete(null, key);
		
		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		inputMap.put(Value.get("John"), Value.get(76));
		inputMap.put(Value.get("Harry"), Value.get(82));
		inputMap.put(Value.get("Sally"), Value.get(79));
		inputMap.put(Value.get("Lenny"), Value.get(84));
		inputMap.put(Value.get("Abe"), Value.get(88));
				
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap),
				MapOperation.removeByKeyRange(binName, Value.get("J"), Value.get("K"), MapReturnType.COUNT),
				MapOperation.removeByValueRange(binName, Value.get(80), Value.get(85), MapReturnType.COUNT),
				MapOperation.removeByIndexRange(binName, 0, 2, MapReturnType.COUNT),
				MapOperation.removeByRankRange(binName, 0, 2, MapReturnType.COUNT)			
				);

		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		List<?> results = record.getList(binName);
		int i = 0;
		
		long val = (Long)results.get(i++);	
		assertEquals(7, val);
		
		val = (Long)results.get(i++);
		assertEquals(2, val);
		
		val = (Long)results.get(i++);
		assertEquals(2, val);

		val = (Long)results.get(i++);
		assertEquals(2, val);

		val = (Long)results.get(i++);
		assertEquals(1, val);
	}

	@Test
	public void operateMapClear() {
		// Test clear.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey9");
		client.delete(null, key);
		
		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap)
				);

		assertRecordFound(key, record);

		long size = record.getLong(binName);
		assertEquals(2, size);
		
		record = client.operate(null, key, 
				MapOperation.clear(binName),
				MapOperation.size(binName)
				);
		
		List<?> results = record.getList(binName);
		size = (Long)results.get(1);
		assertEquals(0, size);
	}
	
	@Test
	public void operateMapScore() {
		// Test score.
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey10");
		client.delete(null, key);
		
		MapPolicy mapPolicy = new MapPolicy(MapOrder.KEY_VALUE_ORDERED, MapWriteMode.UPDATE);

		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("weiling"), Value.get(0));
		inputMap.put(Value.get("briann"), Value.get(0));
		inputMap.put(Value.get("brianb"), Value.get(0));
		inputMap.put(Value.get("meher"), Value.get(0));
		
		// Create map.
		Record record = client.operate(null, key, 
				MapOperation.putItems(mapPolicy, binName, inputMap)
				);

		assertRecordFound(key, record);
						
		// Change scores
		record = client.operate(null, key, 
				MapOperation.increment(mapPolicy, binName, Value.get("weiling"), Value.get(10)),
				MapOperation.increment(mapPolicy, binName, Value.get("briann"), Value.get(20)),
				MapOperation.increment(mapPolicy, binName, Value.get("brianb"), Value.get(1)),
				MapOperation.increment(mapPolicy, binName, Value.get("meher"), Value.get(20))
				);
		
		assertRecordFound(key, record);		
		//System.out.println("Record: " + record);

		// Query top 3 scores
		record = client.operate(null, key, 
				MapOperation.getByRankRange(binName, -3, 3, MapReturnType.KEY)
				);

		assertRecordFound(key, record);		
		//System.out.println("Record: " + record);
		
		// Remove people with score 10 and display top 3 again
		record = client.operate(null, key, 
				MapOperation.removeByValue(binName, Value.get(10), MapReturnType.KEY),
				MapOperation.getByRankRange(binName, -3, 3, MapReturnType.KEY)
				);
		
		assertRecordFound(key, record);		
		//System.out.println("Record: " + record);
		
		List<?> results = record.getList(binName);
		int i = 0;
		List<?> list = (List<?>)results.get(i++);
		String s = (String)list.get(0);
		assertEquals("weiling", s);
		
		list = (List<?>)results.get(i++);
		s = (String)list.get(0);
		assertEquals("brianb", s);
		s = (String)list.get(1);
		assertEquals("briann", s);
		s = (String)list.get(2);
		assertEquals("meher", s);
	}

	@Test
	public void operateMapGetByList() {
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey11");
		client.delete(null, key);
		
		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		inputMap.put(Value.get("John"), Value.get(76));
		inputMap.put(Value.get("Harry"), Value.get(82));
		
		// Write values to empty map.
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap)
				);
					
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		List<Value> keyList = new ArrayList<Value>();
		keyList.add(Value.get("Harry"));
		keyList.add(Value.get("Jim"));
		
		List<Value> valueList = new ArrayList<Value>();
		valueList.add(Value.get(76));
		valueList.add(Value.get(50));

		record = client.operate(null, key,
				MapOperation.getByKeyList(binName, keyList, MapReturnType.KEY_VALUE),
				MapOperation.getByValueList(binName, valueList, MapReturnType.KEY_VALUE)
				);

		assertRecordFound(key, record);
		//System.out.println("Record: " + record);

		List<?> results = record.getList(binName);
		List<?> list = (List<?>)results.get(0);
		assertEquals(2L, list.size());
		Entry<?,?> entry = (Entry<?,?>)list.get(0);
		assertEquals("Harry", entry.getKey());
		assertEquals(82L, entry.getValue());
		entry = (Entry<?,?>)list.get(1);
		assertEquals("Jim", entry.getKey());
		assertEquals(98L, entry.getValue());

		list = (List<?>)results.get(1);
		assertEquals(1L, list.size());
		entry = (Entry<?,?>)list.get(0);
		assertEquals("John", entry.getKey());
		assertEquals(76L, entry.getValue());
	}

	@Test
	public void operateMapInverted() {
		if (! args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey12");
		client.delete(null, key);
		
		Map<Value,Value> inputMap = new HashMap<Value,Value>();
		inputMap.put(Value.get("Charlie"), Value.get(55));
		inputMap.put(Value.get("Jim"), Value.get(98));
		inputMap.put(Value.get("John"), Value.get(76));
		inputMap.put(Value.get("Harry"), Value.get(82));
		
		// Write values to empty map.
		Record record = client.operate(null, key, 
				MapOperation.putItems(MapPolicy.Default, binName, inputMap)
				);
					
		assertRecordFound(key, record);

		List<Value> valueList = new ArrayList<Value>();
		valueList.add(Value.get(76));
		valueList.add(Value.get(55));
		valueList.add(Value.get(98));
		valueList.add(Value.get(50));

		record = client.operate(null, key,
				MapOperation.getByValue(binName, Value.get(81), MapReturnType.RANK | MapReturnType.INVERTED),
				MapOperation.getByValue(binName, Value.get(82), MapReturnType.RANK | MapReturnType.INVERTED),
				MapOperation.getByValueRange(binName, Value.get(90), Value.get(95), MapReturnType.RANK | MapReturnType.INVERTED),
				MapOperation.getByValueRange(binName, Value.get(90), Value.get(100), MapReturnType.RANK | MapReturnType.INVERTED),
				MapOperation.getByValueList(binName, valueList, MapReturnType.KEY_VALUE | MapReturnType.INVERTED),
				MapOperation.getByRankRange(binName, -2, 2, MapReturnType.KEY | MapReturnType.INVERTED ),
				MapOperation.getByRankRange(binName, 0, 3, MapReturnType.KEY_VALUE | MapReturnType.INVERTED)
				);
		
		assertRecordFound(key, record);
		//System.out.println("Record: " + record);
		
		List<?> results = record.getList(binName);
		int i = 0;
		
		List<?> list = (List<?>)results.get(i++);
		assertEquals(4L, list.size());
		
		list = (List<?>)results.get(i++);
		assertEquals(3L, list.size());
		
		list = (List<?>)results.get(i++);
		assertEquals(4L, list.size());
		
		list = (List<?>)results.get(i++);
		assertEquals(3L, list.size());
		assertEquals(0L, list.get(0));
		assertEquals(1L, list.get(1));
		assertEquals(2L, list.get(2));

		list = (List<?>)results.get(i++);
		assertEquals(1L, list.size());
		Entry<?,?> entry = (Entry<?,?>)list.get(0);
		assertEquals("Harry", entry.getKey());
		assertEquals(82L, entry.getValue());
		
		list = (List<?>)results.get(i++);
		assertEquals(2L, list.size());
		assertEquals("Charlie", list.get(0));
		assertEquals("John", list.get(1));
		
		list = (List<?>)results.get(i++);
		assertEquals(1L, list.size());
		entry = (Entry<?,?>)list.get(0);
		assertEquals("Jim", entry.getKey());
		assertEquals(98L, entry.getValue());
	}

	@Test
	public void operateMapRemoveByKeyListForNonExistingKey() throws Exception {
		if (!args.validateMap()) {
			return;
		}

		Key key = new Key(args.namespace, args.set, "opmkey13");

		expectedException.expect(AerospikeException.class);
		
		// Server versions < 3.16.0.1 receive a bin type error.
		//expectedException.expectMessage("Error Code 12: Bin type error");
		
		// Server versions >= 3.16.0.1 receive a key not found error.
		expectedException.expectMessage("Error Code 2: Key not found");

		client.operate(null, key,
				MapOperation.removeByKeyList(binName, singletonList(Value.get("key-1")), MapReturnType.VALUE));
	}
}
