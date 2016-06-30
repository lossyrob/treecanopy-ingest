package treecanopy

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.render._
import geotrellis.spark.tiling._
import geotrellis.spark.util.SparkUtils
import geotrellis.vector._

import com.amazonaws.services.s3.model._
import org.apache.hadoop.mapreduce.Job
import org.apache.spark._
import org.apache.spark.rdd.RDD


object Ingest {

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("Tree Canopy ETL", new SparkConf(true))

    val sourceBucket = "azavea-datahub"
    val statewideLayerName = "raw/treecanopy-2006-2008-pennsylvania"
    val localLayerNames = List(
      "raw/treecanopy-pennsylvania-local/abingtons",
      "raw/treecanopy-pennsylvania-local/alleghenyCounty",
      "raw/treecanopy-pennsylvania-local/harrisburg",
      "raw/treecanopy-pennsylvania-local/lancasterCity",
      "raw/treecanopy-pennsylvania-local/montgomeryCounty",
      "raw/treecanopy-pennsylvania-local/stateCollege"
    )
    val outputPath = "s3://com.azavea.datahub.tms/{name}/{z}/{x}/{y}.png"
    val outputName = "treecanopy-2006-2008-pa"

    val tileSize = 256
    val targetLayoutScheme =
      ZoomedLayoutScheme(WebMercator, tileSize)

    val colorMap =
      ColorMap(
        Map(
          0 -> 0x00000000,
          1 -> 0x139A68FF
        )
      )

    val maxLocalZoom = 18
    val partitioner = new HashPartitioner(5000)

    try {
      val statewide = loadAndTile(sourceBucket, statewideLayerName, partitioner, maxLocalZoom, tileSize)

      val locals =
        localLayerNames.map(localLayerName => {
          loadAndTile(sourceBucket, localLayerName, partitioner, maxLocalZoom, tileSize)
            .convert(ByteConstantNoDataCellType)
            .withContext {
              _.localMap { v =>
                // Map non-canopy land use types to 0
                if (isNoData(v)) NODATA
                else if (v == 1) 1
                else 0
              }
            }
        })

      // merge() will add statewide values only where the local value is absent or NODATA
      val allMerged =
        locals.foldLeft(statewide) { (acc, local) =>
          // left outer join so statewide tiles not overlapping local tiles will pass through as-is
          val merged = acc.leftOuterJoin(local)
            .updateValues((statewideTile, localTile) => localTile.merge(statewideTile))
          ContextRDD(merged, acc.metadata)
        }

      Pyramid.levelStream(allMerged, targetLayoutScheme, maxLocalZoom, NearestNeighbor)
        .foreach { case (z, layer) =>

          val layerId = LayerId(outputName, z)
          val keyToPath = SaveToS3.spatialKeyToPath(layerId, outputPath)

          // Color the tiles by the color map and save to S3
          layer
            .renderPng(colorMap)
            .mapValues(_.bytes)
            .saveToS3(keyToPath, { putObject =>
              putObject.withCannedAcl(CannedAccessControlList.PublicRead)
            })
        }

    } finally {
      sc.stop()
    }
  }

  // Apply a function to statewide and local data for each level of a pyramid stream
  def foreach[A, B](statewide: Stream[A], locals: List[Stream[A]], f: (A, List[A]) => B) {
    if (statewide.nonEmpty) {
      f(statewide.head, locals.map(_.head))
      foreach(statewide.tail, locals.map(_.tail), f)
    }
  }

  def loadAndTile(sourceBucket: String, rawName: String, partitioner: Partitioner, maxZoom: Int, tileSize: Int)
                 (implicit sc: SparkContext): TileLayerRDD[SpatialKey] = {
    val raw = loadFromS3(sourceBucket, rawName, partitioner.numPartitions)
    val (myMaxZoom, tiled) = tile(raw, tileSize, NearestNeighbor, partitioner)
    if (myMaxZoom >= maxZoom) {
      tiled
    } else {
      tiled.resampleToZoom(myMaxZoom, maxZoom, NearestNeighbor)
    }
  }

  // These functions borrowed from https://github.com/lossyrob/geotrellis-ned-example/blob/master/src/main/scala/elevation/Main.scala

  def loadFromS3(bucket: String, prefix: String, partitionCount: Int)(implicit sc: SparkContext): RDD[(ProjectedExtent, Tile)] = {
    val conf = {
      val job = Job.getInstance(sc.hadoopConfiguration)
      S3InputFormat.setBucket(job, bucket)
      S3InputFormat.setPrefix(job, prefix)
      S3InputFormat.setPartitionCount(job, partitionCount)
      job.getConfiguration
    }

    sc.newAPIHadoopRDD(conf, classOf[GeoTiffS3InputFormat], classOf[ProjectedExtent], classOf[Tile])
  }

  def tile(rdd: RDD[(ProjectedExtent, Tile)], tileSize: Int, method: ResampleMethod, partitioner: Partitioner): (Int, TileLayerRDD[SpatialKey]) = {
    val (_, md) = TileLayerMetadata.fromRdd(rdd, FloatingLayoutScheme(tileSize))
    val tilerOptions = Tiler.Options(resampleMethod = method, partitioner = partitioner)
    val tiled = ContextRDD(rdd.tileToLayout[SpatialKey](md, tilerOptions), md)
    tiled.reproject(WebMercator, ZoomedLayoutScheme(WebMercator, tileSize), method)
  }

}
