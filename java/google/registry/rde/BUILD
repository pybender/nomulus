package(
    default_visibility = ["//visibility:public"],
)

licenses(["notice"])  # Apache 2.0


java_library(
    name = "rde",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/annotations",
        "//java/com/google/common/base",
        "//java/com/google/common/collect",
        "//java/com/google/common/html",
        "//java/com/google/common/io",
        "//java/com/google/common/math",
        "//java/com/google/common/net",
        "//third_party/java/appengine:appengine-api",
        "//third_party/java/appengine_gcs_client",
        "//third_party/java/appengine_mapreduce2:appengine_mapreduce",
        "//third_party/java/auto:auto_factory",
        "//third_party/java/auto:auto_value",
        "//third_party/java/bouncycastle",
        "//third_party/java/bouncycastle_bcpg",
        "//third_party/java/dagger",
        "//third_party/java/joda_time",
        "//third_party/java/jsch",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/objectify:objectify-v4_1",
        "//third_party/java/re2j",
        "//third_party/java/servlet/servlet_api",
        "//java/google/registry/config",
        "//java/google/registry/gcs",
        "//java/google/registry/keyring/api",
        "//java/google/registry/mapreduce",
        "//java/google/registry/mapreduce/inputs",
        "//java/google/registry/model",
        "//java/google/registry/request",
        "//java/google/registry/tldconfig/idn",
        "//java/google/registry/util",
        "//java/google/registry/xjc",
        "//java/google/registry/xml",
    ],
)
