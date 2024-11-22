import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

object GraphPartitioning {

  val depth = 6 // Number of BFS iterations

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("GraphPartitioning").setMaster("local[2]")
    val sc = new SparkContext(conf)

    // Read the graph and initialize nodes with clusters
    var graph: RDD[(Long, (Long, List[Long]))] = sc.textFile(args(0)).mapPartitionsWithIndex {
      case (partitionIndex, iterator) =>
        val lines = iterator.toList

        // Assign cluster IDs to the first 5 nodes in each partition
        val firstFiveNodes = lines.take(5).map { line =>
          val parts = line.split(",").map(_.toLong)
          (parts(0), (parts(0), parts.tail.toList)) // Set node ID as cluster
        }

        // Remaining nodes are initialized with cluster ID -1
        val remainingNodes = lines.drop(5).map { line =>
          val parts = line.split(",").map(_.toLong)
          (parts(0), (-1L, parts.tail.toList))
        }

        (firstFiveNodes ++ remainingNodes).iterator
    }

    // Perform BFS for the given depth
    for (_ <- 1 to depth) {
      val expanded = graph.flatMap { case (nodeId, (clusterId, neighbors)) =>
        // Emit both the current node and its neighbors with their cluster IDs
        val neighborClusters = neighbors.map(neighbor => (neighbor, clusterId))
        Seq((nodeId, clusterId)) ++ neighborClusters
      }

      // Group by node ID and pick the maximum cluster ID encountered
      val maxClusters = expanded.groupBy(_._1).mapValues { entries =>
        entries.map(_._2).max
      }

      // Join updated cluster information back to the original graph structure
      graph = graph.map { case (id, (_, neighbors)) => (id, neighbors) }
        .join(maxClusters)
        .map { case (id, (neighbors, newCluster)) =>
          (id, (newCluster, neighbors))
        }
    }

    // Calculate and print the sizes of the partitions (clusters)
    val partitionSizes = graph.map { case (_, (clusterId, _)) => (clusterId, 1) }
      .filter(_._1 != -1) // Exclude unassigned nodes
      .groupBy(_._1)
      .mapValues(_.size)

    partitionSizes.collect().foreach { case (clusterId, size) =>
      println(s"($clusterId,$size)")
    }

    sc.stop()
  }
}
