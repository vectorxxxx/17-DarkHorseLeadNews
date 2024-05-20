## 1、环境搭建

### 1.1、安装 linux

```bash
# 初始化一个centos7系统
vagrant init centos7 https://mirrors.ustc.edu.cn/centos-cloud/centos/7/vagrant/x86_64/images/CentOS-7.box

# 启动虚拟机
vagrant up

# 连接虚拟机
vagrant ssh

# 使用 root 账号登录
su root
vagrant

# 允许账号密码登录
vi /etc/ssh/sshd_config
# PasswordAuthentication yes
# PermitRootLogin no
service sshd restart

# 查看端口
yum install net-tools
# t：显示TCP协议的连接信息。
# n：以数字形式显示地址和端口号，而不是以域名和服务名显示。
# p：显示进程标识符和进程名称，即显示与每个网络连接相关联的进程信息。
# l：仅显示监听状态的连接。
netstat -tnpl | grep sshd
```

### 1.2、安装 Docker

```bash
# 卸载旧版本
yum remove docker \
docker-client \
docker-client-latest \
docker-common \
docker-latest \
docker-latest-logrotate \
docker-logrotate \
docker-engine

# 更新缓存
yum makecache fast

# 设置阿里 docker 镜像仓库地址
yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 安装必要的依赖
yum install -y yum-utils device-mapper-persistent-data lvm2

# 安装 docker 引擎
# 安装 Docker-CE（Community Edition，社区版）
yum -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 查看 docker 版本
docker -v

# 启动 docker
systemctl start docker
# -e 表示显示所有进程，包括其他用户的进程。
# -f 表示显示完整的进程信息，包括进程的 UID、PID、PPID、C、STIME、TTY、TIME、CMD 等字段。
ps -ef | grep docker

# 自启动 docker
systemctl enable docker
systemctl is-enabled docker

# 配置 docker 镜像加速
mkdir -p /etc/docker
# 将JSON内容写入到 /etc/docker/daemon.json 文件中
tee /etc/docker/daemon.json <<-'EOF'
{
    "registry-mirrors": [
        "https://registry.hub.docker.com",
        "http://hub-mirror.c.163.com",
        "https://docker.mirrors.ustc.edu.cn",
        "https://registry.docker-cn.com"
    ]
}
EOF
# 重新加载systemd守护进程的配置文件
systemctl daemon-reload
# 重启 docker
systemctl restart docker
# 查看镜像源是否生效
docker info
```

### 1.3、安装 Nacos

```bash
docker pull nacos/nacos-server:1.2.0

docker run \
--env MODE=standalone \
--name nacos \
--restart=always  \
-d \
-p 8848:8848 \
nacos/nacos-server:1.2.0
```

访问地址：[http://192.168.56.17:8848/nacos](http://192.168.56.17:8848/nacos ) 

### 1.4、安装 MySQL

准备工作

```bash
mkdir -p /usr/local/src/mysql/log
mkdir -p /usr/local/src/mysql/data
mkdir -p /usr/local/src/mysql/conf.d

# 配置 MySQL
vi /usr/local/src/mysql/my.cnf
```

`/usr/local/src/mysql/my.cnf`

```bash
[mysqld]
user=mysql
character-set-server=utf8
default_authentication_plugin=mysql_native_password
secure_file_priv=/var/lib/mysql
expire_logs_days=7
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
max_connections=1000

[client]
default-character-set=utf8

[mysql]
default-character-set=utf8
```

创建 MySQL 实例

```bash
# 创建 MySQL 实例
docker run \
-p 3306:3306 \
--name mysql \
--restart=always \
--privileged=true \
--restart unless-stopped \
-v /usr/local/src/mysql/log:/var/log/mysql \
-v /usr/local/src/mysql/data:/var/lib/mysql \
-v /usr/local/src/mysql/my.cnf:/etc/mysql/my.cnf \
-v /usr/local/src/mysql/conf.d:/etc/mysql/conf.d \
-e MYSQL_ROOT_PASSWORD=root \
-d mysql:8.0.26
```

设置远程访问

```bash
docker exec -it mysql mysql -uroot -proot
use mysql;

# 查看授权情况
select user,host from user;

# 授权任意主机使用root账户登录
ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY 'root';
FLUSH PRIVILEGES;
grant all on *.* to 'root'@'%';

# 查看授权情况
select user,host from user;
```

### 1.5、安装 Nginx

```bash
mkdir -p /usr/local/src/nginx

# 主要解决报错问题：docker: Error response from daemon: failed to create task for container: failed to create shim task: OCI runtime create failed: runc create failed: unable to start container process: error during container init: error mounting "/usr/local/src/nginx/conf/nginx.conf" to rootfs at "/etc/nginx/nginx.conf": mount /usr/local/src/nginx/conf/nginx.conf:/etc/nginx/nginx.conf (via /proc/self/fd/6), flags: 0x5000: not a directory: unknown: Are you trying to mount a directory onto a file (or vice-versa)? Check if the specified host path exists and is the expected type.
# 根因：不支持直接挂载文件，只能挂载文件夹
# 随便启动一个 nginx 实例，这一步只是为了复制出配置，后面会删掉重装
docker run -p 80:80 --name nginx -d nginx:1.23.1
docker container cp nginx:/etc/nginx /usr/local/src/nginx/conf/
docker stop nginx
docker rm nginx


# 运行容器
docker run \
--name nginx \
--restart=always \
-p 80:80 \
-p 443:443 \
-v /usr/local/src/nginx/conf/conf.d:/etc/nginx/conf.d \
-v /usr/local/src/nginx/conf/nginx.conf:/etc/nginx/nginx.conf \
-v /usr/local/src/nginx/html:/usr/share/nginx/html \
-v /usr/local/src/nginx/logs:/var/log/nginx \
-d nginx:1.23.1


# 重新加载配置文件
docker exec nginx  nginx -s reload
```

### 1.6、安装 MinIO

```bash
# 拉取镜像
docker pull quay.io/minio/minio

# 创建数据存储目录
mkdir -p /usr/local/src/minio/data1
mkdir -p /usr/local/src/minio/data2
mkdir -p /usr/local/src/minio/data3
mkdir -p /usr/local/src/minio/data4

# 创建minio
docker run -d \
--name minio \
-p 9000:9000 \
-p 9001:9001 \
-v /usr/local/src/minio/data1:/data1 \
-v /usr/local/src/minio/data2:/data2 \
-v /usr/local/src/minio/data3:/data3 \
-v /usr/local/src/minio/data4:/data4 \
-e "MINIO_ROOT_USER=minioadmin" \
-e "MINIO_ROOT_PASSWORD=minioadmin" \
--restart=always \
quay.io/minio/minio \
server /data1 /data2 /data3 /data4 \
--console-address ":9000" \
--address ":9001"
```

访问: [http://192.168.56.17:9001/](http://192.168.56.17:9001/)

- 账号：minioadmin
- 密码：minioadmin

### 1.7、安装 Redis

```bash
# 下载镜像文件
docker pull redis

# 创建配置文件
mkdir -p /usr/local/src/redis/conf
touch /usr/local/src/redis/conf/redis.conf

# 创建实例并启动
docker run \
--name redis \
--restart=always \
-p 6379:6379 \
-v /usr/local/src/redis/data:/data \
-v /usr/local/src/redis/conf/redis.conf:/etc/redis/redis.conf \
-d redis \
redis-server /etc/redis/redis.conf

# 查看 redis 版本
docker exec -it redis redis-server -v

# 使用 redis 镜像执行 redis-cli 命令连接
docker exec -it redis redis-cli

# 默认存储在内存中，需要修改为持久化方式
vi /usr/local/src/redis/conf/redis.conf
appendonly yes
requirepass leadnews
```

### 1.8、安装 Zookeeper

```bash
docker pull zookeeper:3.4.14

docker run -d --name zookeeper \
--restart=always \
-p 2181:2181 \
zookeeper:3.4.14
```

### 1.9、安装 Kafka

```bash
docker pull wurstmeister/kafka:2.12-2.3.1

docker run -d --name kafka \
-p 9092:9092 \
--env KAFKA_ADVERTISED_HOST_NAME=192.168.56.17 \
--env KAFKA_ZOOKEEPER_CONNECT=192.168.56.17:2181 \
--env KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://192.168.56.17:9092 \
--env KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092 \
--env KAFKA_HEAP_OPTS="-Xmx256M -Xms256M" \
wurstmeister/kafka:2.12-2.3.1

wurstmeister/kafka
```

### 1.10、安装 ElasticSearch

```bash
# 下载镜像文件
docker pull elasticsearch:7.4.2

# 初始化配置
mkdir -p /usr/local/src/elasticsearch/config
mkdir -p /usr/local/src/elasticsearch/data
# 允许被所有IP来源的机器访问
echo "http.host: 0.0.0.0" >> /usr/local/src/elasticsearch/config/elasticsearch.yml
# 递归更改权限
chmod -R 777 /usr/local/src/elasticsearch/

# 运行 elasticsearch 镜像实例
# 测试环境下，必须设置ES的初始内存和最大内存，否则默认占用内存过大会启动不了ES
docker run \
--name elasticsearch \
--restart=always \
-p 9200:9200 -p 9300:9300 \
-e "discovery.type=single-node" \
-e ES_JAVA_OPTS="-Xms64m -Xmx512m" \
-v /usr/local/src/elasticsearch/config/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml \
-v /usr/local/src/elasticsearch/data:/usr/share/elasticsearch/data \
-v /usr/local/src/elasticsearch/plugins:/usr/share/elasticsearch/plugins \
-d elasticsearch:7.4.2

# 查看启动日志
docker logs elasticsearch
```

访问验证：[http://192.168.56.17:9200](http://192.168.56.17:9200)

### 1.11、安装 Kibana

```bash
# 下载镜像文件
docker pull kibana:7.4.2

# 运行 kibana 镜像实例
docker run \
--name kibana \
--restart=always \
-p 5601:5601 \
-e ELASTICSEARCH_HOSTS=http://192.168.56.17:9200 \
-d kibana:7.4.2

# 查看启动日志
docker logs kibana
```

访问验证：[http://192.168.56.17:5601](http://192.168.56.17:5601)

### 1.12、安装 ik 分词器

```bash
# 进入 elasticsearch 插件目录
cd /usr/local/src/elasticsearch/plugins/

# 下载对应版本的 ik 分词器压缩包
yum install wget -y
wget https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.4.2/elasticsearch-analysis-ik-7.4.2.zip

# 解压
yum install unzip -y
unzip elasticsearch-analysis-ik-7.4.2.zip
rm -rf *.zip

# 移至 ik 目录下并赋权限
mkdir ik
mv * ik/
chmod -R 777 ik/

# 以交互模式进入 elasticsearch 容器的命令行中
docker exec -it elasticsearch /bin/bash

# 运行 elasticsearch-plugin
cd /bin
elasticsearch-plugin

# 查看插件是否已安装
elasticsearch-plugin list

# 重启 elasticsearch 容器
exit;
docker restart elasticsearch
```

测试

```bash
POST _analyze
{
  "analyzer": "ik_smart", 
  "text": "我是中国人"
}
```

### 1.13、安装 MongoDB

```bash
docker pull mongo

docker run -di \
--name mongo-service \
--restart=always \
-p 27017:27017 \
-v ~/data/mongodata:/data \
mongo
```



## 2、ES

### 2.1、创建索引和映射

```bash
PUT app_info_article
{
    "mappings":{
        "properties":{
            "id":{
                "type":"long"
            },
            "publishTime":{
                "type":"date"
            },
            "layout":{
                "type":"integer"
            },
            "images":{
                "type":"keyword",
                "index": false
            },
            "staticUrl":{
                "type":"keyword",
                "index": false
            },
            "authorId": {
                "type": "long"
            },
            "authorName": {
                "type": "text"
            },
            "title":{
                "type":"text",
                "analyzer":"ik_smart"
            },
            "content":{
                "type":"text",
                "analyzer":"ik_smart"
            }
        }
    }
}
```

### 2.2、查询映射

```bash
GET app_info_article
```

### 2.3、删除映射

```bash
DELETE app_info_article
```

### 2.4、查询文档

```
GET app_info_article/_search
```

