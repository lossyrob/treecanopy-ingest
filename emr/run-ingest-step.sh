DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

JAR=$CODE_TARGET/treecanopy-ingest-assembly-0.1.0.jar

DATA_NAME=ned-19
SOURCE_BUCKET=azavea-datahub
SOURCE_PREFIX=raw/treecanopy-2006-2008-pennsylvania-albers

TARGET=

PARTITION_ARG='partitionCount=5000,'

ON_FAILURE=CONTINUE

LAYER_NAME=treecanopy-2006-2008-pa-revised
CRS=EPSG:3857
TILE_SIZE=512
INGEST_CLASS=treecanopy.Ingest

TILE_ARGS="--deploy-mode,cluster"
TILE_ARGS=${TILE_ARGS},--class,$INGEST_CLASS,--driver-memory,$DRIVER_MEMORY,--executor-memory,$EXECUTOR_MEMORY,--num-executors,$NUM_EXECUTORS,--executor-cores,$EXECUTOR_CORES
TILE_ARGS=${TILE_ARGS},--conf,spark.yarn.executor.memoryOverhead=$EXECUTOR_YARN_OVERHEAD
TILE_ARGS=${TILE_ARGS},$JAR
TILE_ARGS=${TILE_ARGS},--input,s3,--format,geotiff,-I,"$PARTITION_ARG"bucket=$SOURCE_BUCKET,key=$SOURCE_PREFIX
TILE_ARGS=${TILE_ARGS},--output,render,-O,path=$TARGET,encoding=png
TILE_ARGS=${TILE_ARGS},--layer,$LAYER_NAME,--tileSize,$TILE_SIZE,--crs,$CRS
TILE_ARGS=${TILE_ARGS},--layoutScheme,tms,--cache,NONE,--pyramid
TILE_ARGS=${TILE_ARGS},--reproject,per-tile  # avoid "Multiple CRS tags found" errors

echo "TILE: $TILE_ARGS"

# http://com.azavea.datahub.tms.s3.amazonaws.com/treecanopy-2006-2008-pa-revised/{z}/{x}/{y}.png

aws emr add-steps --cluster-id $CLUSTER_ID --steps \
  Name=Ingest-$LAYER_NAME,ActionOnFailure=$ON_FAILURE,Type=Spark,Jar=$JAR,Args=[$TILE_ARGS]
