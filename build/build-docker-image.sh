#!/usr/bin/env bash

if [ -d "tmp" ]; then
  rm -rf tmp
fi

name=oystr-presto
version=$1
echo "Building '$name' ($version)"

mkdir tmp
unzip -q ../target/universal/package.zip -d tmp
cp Dockerfile tmp
cat << EOF > tmp/run
#!/usr/bin/env bash
/opt/oystr/service/bin/run -Dconfig.file=/opt/oystr/service/shared/conf/local.conf
EOF

chmod +x tmp/run

cd tmp
docker build -t $name:$version .
