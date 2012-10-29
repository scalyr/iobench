#!/bin/bash
# collect output from parallel runs into a single directory
# usage  : ./collect.sh <directory> <count> <output-directory>
# example: ./collect.sh ../trial 30 ../trial/output
# (adapted from runFromCopy.sh)

if [ -e $3 ]; then
  echo "$3 already exists. will not overwrite it." 1>&2
  exit 1
fi
mkdir $3
for i in `seq 1 $2`; do
  for f in `ls $1/$i/*.{out,err}`; do
    f1=`basename $f`
    p=$1/$i/$f1
    #only non-empty files
    [ -s $p ] && cp $p $3/$f1.$i
  done
  grep '{' $1/$i/run.out > $3/json.$i
done
