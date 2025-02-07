group "default" {
  targets = ["stylebi"]
}

target "stylebi" {
  dockerfile = "Dockerfile"
  tags = [${community.docker.tags}]
  platforms = ["linux/amd64"]
  output = ["type=image,push=false"]
}
