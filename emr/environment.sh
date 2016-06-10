EMR_TARGET=s3://azavea-datahub/emr/treecanopy-ingest
CODE_TARGET=s3://azavea-datahub/jars/treecanopy-ingest

AWS_REGION="us-east-1"
SUBNET_ID="subnet-c5fefdb1"

KEY_NAME=azavea-data

# If you want to have zeppelin, uncomment
# ZEPPELIN="Name=Zeppelin-Sandbox"

MASTER_INSTANCE=m3.xlarge
MASTER_PRICE=0.15
DRIVER_MEMORY=10g

# 40 m3.2xlarges, using 5 cores each,
WORKER_INSTANCE=m3.xlarge
WORKER_PRICE=0.15
WORKER_COUNT=20

# M3.2XLARGE
#    8 cores
#   30 GB
#  160 GB SSD

NUM_EXECUTORS=80
EXECUTOR_MEMORY=2812m
EXECUTOR_CORES=1
EXECUTOR_YARN_OVERHEAD=520

# NUM_EXECUTORS=50
# EXECUTOR_MEMORY=20000m
# EXECUTOR_CORES=8
# EXECUTOR_YARN_OVERHEAD=5200


CLUSTER_ID=j-3BCI7AFMC27YP
