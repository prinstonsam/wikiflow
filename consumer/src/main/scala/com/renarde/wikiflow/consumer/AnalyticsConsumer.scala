package com.renarde.wikiflow.consumer

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions

object AnalyticsConsumer extends App with LazyLogging {

  val appName: String = "analytics-consumer-example"

  val spark: SparkSession = SparkSession.builder()
    .appName(appName)
    .config("spark.driver.memory", "5g")
    .master("local[2]")
    .getOrCreate()

  import spark.implicits._

  spark.sparkContext.setLogLevel("WARN")

  logger.info("Initializing Structured consumer")

  val inputStream = spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "d7c7f5682d73:9092")
    .option("subscribe", "wikiflow-topic")
    .option("startingOffsets", "earliest")
    .load()

  // please edit the code below
  val preparedDS = inputStream.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)").as[(String, String)]

  val rawData = preparedDS.filter($"value".isNotNull)

  val expectedSchema: StructType = getSchema


  // ...

  private def getSchema = {
    val expectedSchema = new StructType()
      .add(StructField("bot", BooleanType))
      .add(StructField("comment", StringType))
      .add(StructField("id", LongType))
      .add("length", new StructType()
        .add(StructField("new", LongType))
        .add(StructField("old", LongType))
      )
      .add("meta", new StructType()
        .add(StructField("domain", StringType))
        .add(StructField("dt", StringType))
        .add(StructField("id", StringType))
        .add(StructField("offset", LongType))
        .add(StructField("partition", LongType))
        .add(StructField("request_id", StringType))
        .add(StructField("stream", StringType))
        .add(StructField("topic", StringType))
        .add(StructField("uri", StringType))
      )
      .add("minor", BooleanType)
      .add("namespace", LongType)
      .add("parsedcomment", StringType)
      .add("patrolled", BooleanType)
      .add("revision", new StructType()
        .add("new", LongType)
        .add("old", LongType)
      )
      .add("server_name", StringType)
      .add("server_script_path", StringType)
      .add("server_url", StringType)
      .add("timestamp", LongType)
      .add("title", StringType)
      .add("type", StringType)
      .add("user", StringType)
      .add("wiki", StringType)
    expectedSchema
  }

  val transformedStream = rawData.select(from_json($"value", expectedSchema).as("data")).select("data.*")
    .filter($"bot" =!= true)
    .groupBy($"type")
    .count
    .withColumn("timestamp", functions.current_timestamp())

  transformedStream.writeStream
    .outputMode("complete")
    .format("delta")
    .option("checkpointLocation", "/storage/analytics-consumer/checkpoints")
    .start("/storage/analytics-consumer/output")

  spark.streams.awaitAnyTermination()
}

