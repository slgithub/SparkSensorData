	/*
	 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Below are the libraries required for this project.
 * In this example all of the dependencies are included with the DSE 4.6 distribution.
 * We need to account for that fact in the build.sbt file in order to make sure we don't introduce
 * library collisions upon deployment to the runtime.
 */

import org.apache.log4j.Level
import org.apache.log4j.Logger

import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.{SparkContext, SparkConf}

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.streaming._
import org.apache.spark.streaming.StreamingContext._

import com.datastax.spark.connector._
import com.datastax.spark.connector.streaming._
import com.datastax.spark.connector.cql.CassandraConnector

import org.apache.hadoop.io._

import java.util.Calendar
import java.util.Date
import javax.xml.bind.DatatypeConverter
import java.io.{DataInputStream, DataOutputStream, IOException}
import java.lang.{Thread, SecurityException}
import java.net.{InetAddress, InetSocketAddress, ServerSocket, Socket, SocketTimeoutException, UnknownHostException}

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.utils.UUIDs


object SparkIngest {

  def main(args: Array[String]) {

    // Check how many arguments were passed in - none required
    if (args.length >0) {
      System.out.println("No parameters required")
      // System.exit(0);
    }

    /*
     * This next line sets the logger level. If you are having trouble getting this program to work you can change the
     * value from Level.ERROR to LEVEL.WARN or more verbose yet, LEVEL.INFO
     */

    Logger.getRootLogger.setLevel(Level.ERROR)

    /* Set up the context for configuration for the Spark instance being used.
     * Configuration reflects running DSE/Spark on a local system. In a production system you
     * would want to modify the host and Master to reflect your installation.
     */
    val sparkMasterHost = "127.0.0.1"
    val cassandraHost = "127.0.0.1"
    val cassandraKeyspace = "demo"
    val cassandraTable = "sensor_data"

    // Tell Spark the address of one Cassandra node:
    val conf = new SparkConf(true)
      .set("spark.cassandra.connection.host", cassandraHost)
      .set("spark.cleaner.ttl", "3600")
      .setMaster("local[2]")
      .setAppName(getClass.getSimpleName)

    // Connect to the Spark cluster:
    lazy val sc = new SparkContext(conf)
    lazy val ssc = new StreamingContext(sc, Seconds(1))

    lazy val cc = CassandraConnector(sc.getConf)
    createSchema(cc, cassandraKeyspace, cassandraTable)
//    try {
      val input = ssc.socketTextStream("localhost", 9999)

      val parsedRecords = input.map(parseMessage)
      parsedRecords.saveToCassandra(cassandraKeyspace, cassandraTable)

    ssc.start()
    ssc.awaitTermination()

//     } catch {
//         case ce: java.net.ConnectException => {
//            println("No server on socket 9999... retrying")
//         }
//      }
  }

  def createSchema(cc:CassandraConnector, keySpaceName:String, tableName:String) = {
    cc.withSessionDo { session =>
      session.execute(s"CREATE KEYSPACE IF NOT EXISTS ${keySpaceName} WITH REPLICATION = { 'class':'SimpleStrategy', 'replication_factor':1}")

      session.execute(s"DROP TABLE IF EXISTS ${keySpaceName}.${tableName};")

      session.execute("CREATE TABLE IF NOT EXISTS " +
                      s"${keySpaceName}.${tableName} (name text, time timestamp, value decimal, " +
                      s"PRIMARY KEY(name, time));")
    }
  }

  def parseMessage(msg:String) : Record = {
    val arr = msg.split(",")
    val time = new Date
    return Record(arr(0), time, arr(1).toFloat)    
  }


  /* This is the entry point for the application */

  case class Record(name:String, time:Date, value:BigDecimal)

}


