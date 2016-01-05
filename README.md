Running a Docker container locally:

```bash
sbt clean

sbt docker:publishLocal

docker pull mysql

docker pull

docker run -d --name mysql -p 3306:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql

docker run -d --link mysql:mysql --name jetchat -p 80:9000 jetchat:0.1-SNAPSHOT -Dapi.GitHub.clientId=875908da66afd5c86463 -Dapi.GitHub.clientSecret=38a287506600b0c6b59f2777e6ce04b487636549 -Dslick.dbs.default.db.url=jdbc:mysql://mysql:3306/jetchat -Dslick.dbs.default.db.user=root -Dslick.dbs.default.db.password= -Dplay.evolutions.autoApply=true -Dhttp.port=9000 -Dapplication.secret=docker
```

Running a Docker container by DevOps:

```bash
docker run -p <public_http_port>:<http_port> <image_id> -Dapi.GitHub.clientSecret=<github_secret_id> -Dapi.GitHub.clientId=<github_client_id> -Dslick.dbs.default.db.user=<db_user> -Dslick.dbs.default.db.password=<db_password> -Dplay.evolutions.autoApply=true -DETCDCTL_PEERS=<etcd_peers> -Dakka.remote.netty.tcp.hostname=<seed_host> -Dakka.remote.netty.tcp.port=<seed_port> -Dhttp.port=<http_port>
```
