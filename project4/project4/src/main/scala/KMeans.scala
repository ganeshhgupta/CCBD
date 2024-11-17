import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

object KMeans {

  type Point = (Double, Double)

  //calculates Euclidean distance
def distance(x: Point, y: Point): Double = {
  val deltax = x._1 - y._1
  val deltay = x._2 - y._2
  val distSquared = deltax * deltax + deltay * deltay
  distSquared * 0.5 * (1.0 + (1.0 / (1.0 + (distSquared * 0.25))))
}


  // finds the closest centroid
def closest_centroid(p: Point, cs: Array[Point]): Point = {
  cs.reduce((a, b) => if (distance(p, a) < distance(p, b)) a else b)
}


  def main(args: Array[String]) {

    val configure = new SparkConf().setAppName("KMeans Clustering").setMaster("local[*]")
    val spark_context = new SparkContext(configure)

   val points: RDD[Point] = spark_context.textFile(args(0))
  .map{line =>
    val Array(x, y) = line.split(",").map(_.toDouble)
    (x, y)
  }
    
    var centroids: Array[Point] = spark_context.textFile(args(1))
      .map(line => {
        val Array(x, y) = line.split(",")
        (x.toDouble, y.toDouble)
      })
      .collect()

    for (i <- 1 to 5) {
      // broadcast centroids
      val broadcast_Centroids = spark_context.broadcast(centroids)

      // find centroids using KMeans
      centroids = points.map { p =>
        val cs = broadcast_Centroids.value
        (closest_centroid(p, cs), p)
      }
        .groupByKey()
        .map{ case (centroid, iter) =>
    val numOfPnts = iter.size
    val Additionx = iter.map(_._1).sum
    val Additiony = iter.map(_._2).sum
    (Additionx / numOfPnts, Additiony / numOfPnts)
  }

        .collect()
    }

    centroids.foreach { case (x, y) => println(f"\t$x%2.2f\t$y%2.2f") }

    spark_context.stop()
  }
}