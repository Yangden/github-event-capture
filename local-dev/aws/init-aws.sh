#!/bin/bash

set -x

awslocal sqs create-queue --queue-name local-demo-queue

set +x