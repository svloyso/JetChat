Running a two-node cluster via Docker:

```bash
sbt clean

sbt docker:publishLocal

docker pull mysql

docker run -d --name mysql -p 3306:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=jetchat mysql

docker pull quay.io/coreos/etcd

export HostIP="192.168.99.100"

docker run -d -p 4001:4001 -p 2380:2380 -p 2379:2379 --name etcd quay.io/coreos/etcd \
 -name etcd0 \
 -advertise-client-urls http://${HostIP}:2379,http://${HostIP}:4001 \
 -listen-client-urls http://0.0.0.0:2379,http://0.0.0.0:4001 \
 -initial-advertise-peer-urls http://${HostIP}:2380 \
 -listen-peer-urls http://0.0.0.0:2380 \
 -initial-cluster-token etcd-cluster-1 \
 -initial-cluster etcd0=http://${HostIP}:2380 \
 -initial-cluster-state new

docker run -d --link mysql:mysql --link etcd:etcd --name jetchat0 -p 80:9000 jetchat:0.1-SNAPSHOT -Dapi.GitHub.clientId=875908da66afd5c86463 -Dapi.GitHub.clientSecret=38a287506600b0c6b59f2777e6ce04b487636549 -Dslick.dbs.default.db.url=jdbc:mysql://mysql:3306/jetchat -Dslick.dbs.default.db.user=root -Dslick.dbs.default.db.password= -Dplay.evolutions.autoApply=true -Dhttp.port=9000 -Dakka.remote.netty.tcp.port=2500 -Dapplication.secret=docker -DETCDCTL_PEERS=http://etcd:2379,http://etcd:4001

docker run -d --link mysql:mysql --link etcd:etcd --name jetchat1 -p 81:9001 jetchat:0.1-SNAPSHOT -Dapi.GitHub.clientId=805391a9464341c0243d -Dapi.GitHub.clientSecret=eaaf62a85c576c8b7802409d9c1e2db67260e4e3 -Dslick.dbs.default.db.url=jdbc:mysql://mysql:3306/jetchat -Dslick.dbs.default.db.user=root -Dslick.dbs.default.db.password= -Dplay.evolutions.autoApply=true -Dhttp.port=9001 -Dakka.remote.netty.tcp.port=2501 -Dapplication.secret=docker -DETCDCTL_PEERS=http://etcd:2379,http://etcd:4001
```

Running a single Docker container:

```bash
docker run -p <public_http_port>:<http_port> <image_id> -Dapi.GitHub.clientSecret=<github_secret_id> -Dapi.GitHub.clientId=<github_client_id> -Dslick.dbs.default.db.user=<db_user> -Dslick.dbs.default.db.password=<db_password> -Dplay.evolutions.autoApply=true -DETCDCTL_PEERS=<etcd_peers> -Dakka.remote.netty.tcp.hostname=<seed_host> -Dakka.remote.netty.tcp.port=<seed_port> -Dhttp.port=<http_port>
```
