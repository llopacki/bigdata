/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.hbase.helloworld;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.fs.*;

import java.io.IOException;

/**
 * A minimal application that connects to Cloud Bigtable using the native HBase API
 * and performs some basic operations.
 */
public class HelloWorld2 {

    // Refer to table metadata names by byte array in the HBase API
    private static final byte[] TABLE_NAME = Bytes.toBytes("Hello-Bigtable");
    private static final byte[] COLUMN_FAMILY_NAME1 = Bytes.toBytes("cf1");
    private static final byte[] COLUMN_FAMILY_NAME2 = Bytes.toBytes("cf2");
    private static final byte[] COLUMN_NAME = Bytes.toBytes("greeting");

    private static final String ZOOKEEPER_IP = "10.0.0.10";

    // Write some friendly greetings to Cloud Bigtable
    private static final String[] GREETINGS =
            { "Hello World!", "Hello Cloud Bigtable!", "Hello HBase!", "Hi there", "Cz!" };

    /**
     * Connects to Cloud Bigtable, runs some basic operations and prints the results.
     */
    private static void doHelloWorld() {
	print("ZOOKEEPER IP:"+ZOOKEEPER_IP);
        Configuration config = HBaseConfiguration.create();
        config.set("java.com.example.hbase.zookeeper.quorum",ZOOKEEPER_IP);
        config.set("java.com.example.hbase.zookeeper.property.clientPort","2181");
        config.set("hbase.client.retries.number","3");
        config.set("hbase.client.pause","1000");
        config.set("zookeeper.znode.parent","/hbase-unsecure");

        // [START connecting_to_bigtable]
        // Create the Bigtable connection, use try-with-resources to make sure it gets closed
        try (Connection connection = ConnectionFactory.createConnection(config)) {

            // The admin API lets us create, manage and delete tables
            Admin admin = connection.getAdmin();
            // [END connecting_to_bigtable]

            // [START creating_a_table]
            // Create a table with a single column family
            HTableDescriptor descriptor = new HTableDescriptor(TableName.valueOf(TABLE_NAME));
            descriptor.addFamily(new HColumnDescriptor(COLUMN_FAMILY_NAME1));
            descriptor.addFamily(new HColumnDescriptor(COLUMN_FAMILY_NAME2));

            print("Create table " + descriptor.getNameAsString());
            admin.createTable(descriptor);
            // [END creating_a_table]

            print("Get table " + descriptor.getNameAsString());

            // [START writing_rows]
            // Retrieve the table we just created so we can do some reads and writes
            Table table = connection.getTable(TableName.valueOf(TABLE_NAME));

            // Write some rows to the table
            print("Write some greetings to the table");
            for (int i = 0; i < GREETINGS.length; i++) {
                // Each row has a unique row key.
                //
                // Note: This java.com.example uses sequential numeric IDs for simplicity, but
                // this can result in poor performance in a production application.
                // Since rows are stored in sorted order by key, sequential keys can
                // result in poor distribution of operations across nodes.
                //
                // For more information about how to design a Bigtable schema for the
                // best performance, see the documentation:
                //
                //     https://cloud.google.com/bigtable/docs/schema-design
                String rowKey = "greeting" + i;

                // Put a single row into the table. We could also pass a list of Puts to write a batch.
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(COLUMN_FAMILY_NAME1, COLUMN_NAME, Bytes.toBytes(GREETINGS[i]));
                table.put(put);
                Put put2 = new Put(Bytes.toBytes(rowKey));
                put2.addColumn(COLUMN_FAMILY_NAME2, COLUMN_NAME, Bytes.toBytes(GREETINGS[i]));
                table.put(put2);
            }
            // [END writing_rows]

            // [START getting_a_row]
            // Get the first greeting by row key
            String rowKey = "greeting0";
            Result getResult = table.get(new Get(Bytes.toBytes(rowKey)));
            String greeting = Bytes.toString(getResult.getValue(COLUMN_FAMILY_NAME1, COLUMN_NAME));
            String greeting2 = Bytes.toString(getResult.getValue(COLUMN_FAMILY_NAME2, COLUMN_NAME));
            System.out.println("Get a single greeting by row key");
            System.out.printf("\t%s = %s, %s\n", rowKey, greeting, greeting2);
            // [END getting_a_row]

            // [START scanning_all_rows]
            // Now scan across all rows.
            Scan scan = new Scan();

            print("Scan for all greetings:");
            ResultScanner scanner = table.getScanner(scan);
            for (Result row : scanner) {
                byte[] valueBytes = row.getValue(COLUMN_FAMILY_NAME1, COLUMN_NAME);
                byte[] valueBytes2 = row.getValue(COLUMN_FAMILY_NAME2, COLUMN_NAME);
                System.out.println('\t' + Bytes.toString(valueBytes));
                System.out.println('\t' + Bytes.toString(valueBytes2));
            }
            // [END scanning_all_rows]

            // [START deleting_a_table]
            // Clean up by disabling and then deleting the table
            // print("Delete the table");
            //admin.disableTable(table.getName());
            //admin.deleteTable(table.getName());
            // [END deleting_a_table]

        } catch (IOException e) {
            System.err.println("Exception while running HelloWorld2: " + e.getMessage());
            e.printStackTrace();
            try {
                Connection connection = ConnectionFactory.createConnection(config);
                Admin admin = connection.getAdmin();
                Table table = connection.getTable(TableName.valueOf(TABLE_NAME));
                admin.disableTable(table.getName());
                admin.deleteTable(table.getName());
            } catch (IOException e2) {};

            System.exit(1);
        }

        System.exit(0);
    }

    private static void print(String msg) {
        System.out.println("HelloWorld2: " + msg);
    }

    public static void main(String[] args) {

        doHelloWorld();
    }

}
