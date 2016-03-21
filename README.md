Running a cluster via _docker-compose_:

```bash
sbt clean docker:stage

docker-compose pull

docker-compose up

docker-compose scale web=2

docker-compose up --force-recreate -d
```

Running a single container via _docker_:

```bash
docker run -p <public_http_port>:<http_port> <image_id> -Dapi.GitHub.clientSecret=<github_secret_id> -Dapi.GitHub.clientId=<github_client_id> -Dslick.dbs.default.db.user=<db_user> -Dslick.dbs.default.db.password=<db_password> -Dplay.evolutions.autoApply=true -DETCDCTL_PEERS=<etcd_peers> -Dakka.remote.netty.tcp.hostname=<seed_host> -Dakka.remote.netty.tcp.port=<seed_port> -Dhttp.port=<http_port>
```

Running MySQL and etcd via Docker:

```bash
sbt clean docker:stage

eval "$(docker-machine env default)"

docker-compose pull

docker-compose up mysql

docker-compose up etcd
```