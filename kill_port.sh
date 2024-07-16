#!/bin/bash

# 定义要关闭的端口范围
START_PORT=8090
END_PORT=8102

# 循环遍历端口范围
for ((port=$START_PORT; port<=$END_PORT; port++))
do
  # 查找占用指定端口的进程ID
  PID=$(lsof -t -i:$port)

  # 如果找到进程ID，则杀死进程
  if [ ! -z "$PID" ]; then
    echo "Killing process $PID using port $port"
    kill -9 $PID
  fi
done

PID=$(lsof -t -i:10010)

# 如果找到进程ID，则杀死进程
if [ ! -z "$PID" ]; then
  echo "Killing process $PID using port $port"
  kill -9 $PID
fi

echo "All processes using ports $START_PORT to $END_PORT have been terminated."