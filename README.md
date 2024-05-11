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