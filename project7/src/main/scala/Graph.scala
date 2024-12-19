import org.apache.spark.sql.functions.count
import org.apache.spark.sql.SparkSession
import org.apache.spark._

object Graph {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Graph").setMaster("local[*]")
    val spark = SparkSession.builder().config(conf).getOrCreate()
    import spark.implicits._
    val data_frame = spark.read.format("csv")
      .option("header", "false")
      .load(args(0))
      .toDF("column1", "column2") 
    val final_data_frame = data_frame.groupBy("column1")
      .agg(count("column1").alias("end_users"))
      .groupBy("end_users")
      .agg(count("end_users").alias("clients"))
      .orderBy("end_users")
    val output_data_frame = final_data_frame.collect()
    var i = 0
    while (i < output_data_frame.length) {
      val row = output_data_frame(i)
      val count_of_users = row.getAs[Long]("end_users")
      val count_of_count_of_users = row.getAs[Long]("clients")
      println(s"${count_of_users}\t${count_of_count_of_users}")
      i += 1
    }
  }
}
