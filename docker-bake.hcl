variable "DOCKER_REGISTRY" {
  default = "ghcr.io"
}
variable "DOCKER_ORG" {
  default = "darpa-askem"
}
variable "VERSION" {
  default = "local"
}

# ---------------------------------
function "tag" {
  params = [image_name, prefix, suffix]
  result = [ "${DOCKER_REGISTRY}/${DOCKER_ORG}/${image_name}:${check_prefix(prefix)}${VERSION}${check_suffix(suffix)}" ]
}

function "check_prefix" {
  params = [tag]
  result = notequal("",tag) ? "${tag}-": ""
}

function "check_suffix" {
  params = [tag]
  result = notequal("",tag) ? "-${tag}": ""
}

# ---------------------------------
group "prod" {
  targets = ["hmi-client", "hmi-server", "tds-migration"]
}

group "staging" {
  targets = ["hmi-client", "hmi-server", "tds-migration"]
}

group "default" {
  targets = ["hmi-client-base", "hmi-server-base", "tds-migration-base"]
}

# ---------------------------------
target "_platforms" {
  platforms = ["linux/amd64", "linux/arm64"]
}

target "hmi-client-base" {
	context = "packages/client/hmi-client/docker"
	tags = tag("hmi-client", "", "")
	dockerfile = "Dockerfile"
}

target "hmi-client" {
  inherits = ["_platforms", "hmi-client-base"]
}

target "hmi-server-base" {
	context = "." # root of the repo
	dockerfile = "./packages/server/docker/Dockerfile"
	tags = tag("hmi-server", "", "")
}

target "hmi-server" {
  inherits = ["_platforms", "hmi-server-base"]
}

target "tds-migration-base" {
	context = "." # root of the repo
	dockerfile = "./packages/tds-migration/docker/Dockerfile"
	tags = tag("tds-migration", "", "")
}

target "tds-migration" {
  inherits = ["_platforms", "tds-migration-base"]
}
