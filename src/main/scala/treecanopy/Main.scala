package treecanopy

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.etl.Etl
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.render._
import geotrellis.spark.tiling._
import geotrellis.spark.util.SparkUtils
import geotrellis.vector._
import geotrellis.vector.io._

import com.amazonaws.services.s3.model._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.Job
import org.apache.spark._
import org.apache.spark.rdd.RDD

object Ingest {
  val targetLayoutScheme =
    ZoomedLayoutScheme(WebMercator, 512)

  val colorMap =
    ColorMap(
      Map(
        0 -> 0x00000000,
        1 -> 0x139A68FF
      )
    )

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("Tree Canopy ETL", new SparkConf(true))

    try {

      println(args.toSeq)
      val etl = Etl(args)

      val sourceTiles =
        etl.load[ProjectedExtent, Tile]

      // Reproject and tile the layer.
      val (zoom, canopy) =
        etl.tile(sourceTiles)

      val conf = etl.conf
      val path = "s3://com.azavea.datahub.tms/{name}/{z}/{x}/{y}.png"

      Pyramid.levelStream(canopy, targetLayoutScheme, zoom, NearestNeighbor)
        .foreach { case (z, layer) =>
          val layerId = LayerId(conf.layerName(), z)
          val keyToPath = SaveToS3.spatialKeyToPath(layerId, path)

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
}
